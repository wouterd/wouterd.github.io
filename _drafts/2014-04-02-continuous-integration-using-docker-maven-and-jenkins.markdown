---
layout: post
title:  "Continuous Integration Using Docker, Maven and Jenkins"
date:   2014-04-02
categories:
  - continuous integration
  - docker
  - jenkins
  - java
summary: "With the push to feature branches and the increased use of git, continuous integration of every single branch
          can become an infrastructure nightmare. Docker can be used to eliminate the need to deploy to remote servers
          and run your integration tests on the same server as your build. Scaling can then be done using Jenkins slaves
          that run one or more jobs concurrently."
---
### The Problem
Git has made merging almost a non event. A common way to handle development is to have a separate branch per feature and
merge that feature back to the development/master branches when the feature is done and all tests pass. For unit tests,
there is already viable tooling that can help you auto-test feature branches before you merge them into develop or
master. [Travis-ci][travis] already offers this for Github projects, you should try it out if you haven't already!

When it comes to integration tests there is a problem. When you have complex integration tests that need a running
environment, it can become hard to manage all these branches and environments. You could spin up VMs for each feature
branch that you create or try to share one environment for all feature branches. Or the worst alternative: you can leave
integration tests for the integration branch. The problem with the last option is that it breaks the principle of
"do no harm": you want to merge when you know it won't break things and now you have a branch that's meant to break?
Sounds like bad design to me.

### The Old Way of Deploying to Integration
Deploying to a test environment normally means deploying the new version of your app to the server. Anything else is
already in place. As an example I'll take a java based CMS: Hippo. Hippo consists of two WAR files: cms.war and
site.war. These two WARs are deployed to a tomcat instance together with some shared & common jars. If you base your
project on the standard maven archetype, the build will create a gzipped tar archive for you that you can extract into
your tomcat base/home and you're good to go. In pseudo-code, this is what my deploy.sh script that runs on jenkins looks
like this for each server:

* SCP the tar.gz from the project to the server(s)
* ssh into the server
* stop tomcat
* remove the "work", "webapps", "shared" and "common" folders
* extract the tar.gz into the tomcat folder
* start tomcat

After this, we wait for tomcat to start up and deploy the webapps. This involves an expect script that needs to be
copied to the server and run remotely to check for the "server startup in xxx" in the catalina.out. A few of these steps
also require root access rights, which requires password less sudo for some commands. While this is all possible and
done a lot, it does add a lot of complexity and "boilerplate" to a simple goal: integration testing.

### Docker
[Docker][docker] is a tool allows you to run multiple processes in their own containers on a machine without the
overhead of virtual machines. It does give process separation and to the processes it looks like they're running in
their own linux environment. Docker was created in Ruby and is basically an interface to [Linux Containers][lxc] (LXC),
a feature of the linux kernel that was introduced in the 3.8 release.

Docker works well under Ubuntu and [Red Hat is working to make it enterprise ready for RHEL][docker-rhel].

Docker allows you to specify containers using [Dockerfiles][docker-builder]. These can be compared to Vagrantfiles that
are used to provision virtual machines. These docker files can be composed of other docker files, creating a sort of
inheritance / composition of containers.

### Creating an Integration Server Using Docker
I created a [sample project][hippo-docker] on Github that shows how you can run integration tests on a sample Hippo project
using docker. If you have [Vagrant][vagrant], you can see the Jenkins server in action with a simple `vagrant up` from the
root of the project. Using the composition command in the Dockerfiles (FROM), I've built an oracle jdk 7 image that is used
as the base of a tomcat image. I then use that tomcat image as the base for my integration environments. The Dockerfile for
the integration image resides in the root of the project and is as follows:

    FROM wouterd/tomcat
    MAINTAINER Wouter Danes "https://github.com/wouterd"    
    ADD myhippoproject/target/myhippoproject-distribution.tar.gz /var/lib/tomcat6/

The wouterd/tomcat image is built as follows:

    FROM wouterd/oracle-jre7
    MAINTAINER Wouter Danes "https://github.com/wouterd"
    RUN apt-get install -y tomcat6
    RUN mkdir -p /usr/share/tomcat6/logs
    RUN mkdir -p /usr/share/tomcat6/temp
    CMD export JAVA_HOME=/usr/lib/jvm/java-7-oracle && export CATALINA_BASE=/var/lib/tomcat6 && /usr/share/tomcat6/bin/catalina.sh run
    EXPOSE 8080
    
The wouterd/oracle-jre7 has some magic to install jdk 7 from oracle on a clean ubuntu VM. The code for this container and the two above
is in the docker-images directory in the Github project. The wouterd/tomcat image does the following:

* `FROM wouterd/oracle-jre7` takes the container wouterd/oracle-jre7 (a container with ubunty + oracle java7 installed)
* `RUN apt-get install -y tomcat6` installs tomcat6 using the ubuntu repository
* `RUN mkdir -p ..` creates the temp and logs directories
* `CMD [some bash]` sets the command that gets executed when this container is run, setting two environment variables and starting catalina.sh
* `EXPOSE 8080` tells docker to expose port 8080 in the container to the host system.

The docker container that gets built during the integration test simply does two things:

* Takes the wouterd/tomcat container 
* `ADD [file] [destination]` copies the tar.gz with the distribution from the maven project to the tomcat base folder and extracts it if it's a tar.
* It inherits the `CMD` directive from the wouterd/tomcat container, so if you "run" the integration container using `docker run`, it will start up 
tomcat and start deploying the distribution.

### Using the container to run continuous integration
Now that we have everything in place to build the integration server, we can start putting everything together. I will use Jenkins as the build server,
but this solution is easily ported to your own build server, like Go, Hudson or Bamboo. I implemented these steps on the Jenkins server:

* Build the project using maven and run tests using `mvn clean test`
* Build the project distribution using `mvn clean package -Pdist`
* Build the docker container with the distribution
* Start the docker container
* Run integration tests on the container
* Stop & destroy the container and delete the created image



[travis]:https://travis-ci.org/
[docker]:https://www.docker.io/
[lxc]:https://linuxcontainers.org/
[docker-rhel]:http://www.infoworld.com/t/application-virtualization/red-hat-fast-tracks-docker-apps-enterprise-linux-238122
[docker-builder]:http://docs.docker.io/en/latest/reference/builder/
[hippo-docker]:https://github.com/wouterd/hippo-docker
[vagrant]:http://www.vagrantup.com
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
I created a [sample project][hippo-docker] on Github that shows how you can run integration tests on a sample Hippo
project using docker. If you have [Vagrant][vagrant], you can see the Jenkins server in action with a simple
`vagrant up` from the root of the project. Using the composition command in the Dockerfiles (FROM), I've built an oracle
jdk 7 image that is used as the base of a tomcat image. I then use that tomcat image as the base for my integration
environments. The Dockerfile for the integration image resides in the root of the project and is as follows:

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

The wouterd/oracle-jre7 has some magic to install jdk 7 from oracle on a clean ubuntu VM. The code for this container
and the two above is in the docker-images directory in the Github project. The wouterd/tomcat image does the following:

* `FROM wouterd/oracle-jre7` takes the container wouterd/oracle-jre7 (a container with ubunty + oracle java7 installed)
* `RUN apt-get install -y tomcat6` installs tomcat6 using the ubuntu repository
* `RUN mkdir -p ..` creates the temp and logs directories
* `CMD [some bash]` sets the command that gets executed when this container is run, setting two environment variables
  and starting catalina.sh
* `EXPOSE 8080` tells docker to expose port 8080 in the container to the host system.

The docker container that gets built during the integration test simply does two things:

* Takes the wouterd/tomcat container
* `ADD [file] [destination]` copies the tar.gz with the distribution from the maven project to the tomcat base folder
  and extracts it if it's a tar.
* It inherits the `CMD` directive from the wouterd/tomcat container, so if you "run" the integration container using
  `docker run`, it will start up tomcat and start deploying the distribution.

### Using the container to run continuous integration
Now that we have everything in place to build the integration server, we can start putting it all together. I will
use Jenkins as the build server, but this solution is easily ported to your own build server, like Go, Hudson or Bamboo.
The integrationtest can also be run using `mvn verify`, if you run linux as an OS with a minimum kernel version 3.8 or
MacOS X with boot2docker and public-key-authentication setup for `boot2docker ssh`. Refer to the __Project Requirements__
section below for the required steps and software to be able to run `mvn verify` on your own machine.

__OS X Only__: Here's a two liner to add your key
to the boot2docker vm (if you use an rsa key, mine is called id_dsa.pub):

{% highlight bash %}
  boot2docker ssh mkdir -p ~/.ssh
  cat ~/.ssh/id_rsa.pub | boot2docker ssh 'cat >> ~/.ssh/authorized_keys'
{% endhighlight %}

You can create the Jenkins server using [Vagrant][vagrant] by running `vagrant up` in the root of the project. After
initialization and booting, jenkins can be found by opening a browser and going to [http://localhost:8080](http://localhost:8080).

The maven project does the following during the build phases:

* `compile` simply compiles the project
* `test` runs the unit tests
* `package` builds the tar.gz distribution
* `pre-integration-test` creates the docker image and starts it as a new container, then waits for tomcat to finish
  starting up.
* `integration-test` runs the integration tests on the container using junit, webdriver and phantomjs
* `post-integration-test` stops the docker container, removes the container and the image that was created for the test

All the interesting tidbits are in the ["integrationtests" module][integrationtests] of the "myhippoproject" project:

The `pre-integration-test` and `post-integration-test` phases are implemented using shell scripts that are executed
  with the exec-maven-plugin. There's an excellent [java based docker api client][docker-java] and a
  [docker maven plugin][docker-maven-plugin] that can probably be used instead of these scripts, but I hadn't found out
  about them yet when I wrote these scripts. Below is the start script. This script can probably be reduced to a maximum
  of 10 lines of java code. :-)

{% highlight bash %}
#!/bin/bash
set -eu
workdir="${WORK_DIR}"
dockerfile="${DOCKER_FILE_LOCATION}"
distributionfile="${DISTRIBUTION_FILE_LOCATION}"
logs="${workdir}/logs"
dockerbuilddir="${workdir}/docker-build"
mkdir -p ${workdir}
mkdir -p ${dockerbuilddir}
cp ${dockerfile} ${distributionfile} ${dockerbuilddir}/
image_id=$(docker build --rm -q=false ${dockerbuilddir} | grep "Successfully built" | cut -d " " -f 3)
echo ${image_id} > ${workdir}/docker_image.id
rm -rf ${dockerbuilddir}
catalina_out="catalina.$(date +%Y-%m-%d).log"
if [[ "$(uname)" == "Darwin" ]] ; then
    echo 'Detected MacOS X, trying to use boot2docker ssh to check catalina logs..'
    logs='/tmp/docker-logs'
    grepcommand="boot2docker ssh grep -q 'INFO: Server startup' ${logs}/${catalina_out}"
else
    mkdir -p ${logs}
    grepcommand="grep -q 'INFO: Server startup' ${logs}/${catalina_out}"
fi
container_id=$(docker run -d -v ${logs}:/var/log/tomcat6 ${image_id})
echo ${container_id} > ${workdir}/docker_container.id
echo -n "Waiting for tomcat to finish startup..."
# Give Tomcat some time to wake up...
while ! ${grepcommand} ; do
    sleep 5
    echo -n "."
done
echo -n "done"
{% endhighlight %}  

This script is run with the following snippet in the integrationtests pom:
{% highlight xml %}
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <version>1.2.1</version>
  <executions>
    <execution>
      <id>build-and-start-integration-server</id>
      <goals>
        <goal>exec</goal>
      </goals>
      <phase>pre-integration-test</phase>
      <configuration>
        <environmentVariables>
          <WORK_DIR>${project.build.directory}/docker-work</WORK_DIR>
          <DOCKER_FILE_LOCATION>${project.basedir}/src/main/assembly/Dockerfile</DOCKER_FILE_LOCATION>
          <DISTRIBUTION_FILE_LOCATION>${project.build.directory}/myhippoproject.tar.gz</DISTRIBUTION_FILE_LOCATION>
        </environmentVariables>
        <executable>${project.basedir}/src/test/script/start-integration-server.sh</executable>
      </configuration>
    </execution>
  </executions>
</plugin>
{% endhighlight %}

The `if [[ "$(uname)" == "Darwin" ]]` hack is there to be able to run this script with boot2docker and detect if tomcat
has started up yet. It's not easily possible to bind host folders to docker container folders when using boot2docker, but
there is definitely room for improvement there. For Cygwin a similar hack will be needed. :( Using docker-java would fix
this, because they have a better way to stream logs than the docker CLI.

The `package` goal uses the maven-assembly-plugin to create the tar.gz archive that needs to be unpacked into the
  CATALINA_HOME folder on the tomcat docker container.

The `integration-test` phase is implemented using selenium webdriver tests with the phantomjs driver. But you can use
  any test-fixture here for integration tests that you'd like. An example would be using a generated apache CXF REST
  proxy to do validation of a rest interface that you created.

### Project requirements

* Docker installed and a daemon running, either on the host machine or in a boot2docker VM, OS X and linux will work,
  Windows might work when using cygwin. If you are running boot2docker make sure you have the DOCKER_HOST environment
  variable set. Here's a oneliner that'll work with standard settings: `export DOCKER_HOST=tcp://localhost:4243`
* PhantomJS needs to be installed and on the path
* Git isn't required but it useful to be able to get the project. ;-)
* Maven 3.x
* Java 7+ (Oracle JDK preferred)

### What is Next?

* Given that you now build containers and test those containers, you could use those containers in production instead of
  bare metal or VMs. Docker is currently working hard to get to version 1.0, and they don't recommend using Docker in
  production yet. But when it is production ready, you could deploy these containers straight into production instead of
  having to execute complex deploy scripts and do tomcat restarts. There are already some companies that use docker in
  production, one of which is docker themselves (of course..), so if you dare..
* I've only shown you how to spawn one container running one process. But you can link docker containers and as such can
  create a whole infrastructure of web frontends, load balancers, database servers, app servers, cache servers, etc. A
  first thing to show would be adding MySQL storage to the app server for the Hippo Jackrabbit repository instead of the
  file based storage that is never used in production. Have a look at `docker run --link`.
* Docker allows you to create snapshot images of running containers. If you have an application that takes a while to
  get into a deployed state, but you want every test to be run on a "fresh instance", it can really slow down the test
  when you have to initialize the container over and over again. What you can do is wait for tomcat to start up and deploy
  and then run `docker commit`. It will create a new docker image that you can start which will then move on from when
  you called `docker commit`. This is a very nice feature to speed up autonomous integration tests.

Let me know if you have any questions or if anything is unclear. Or if you know better ways to do things I did in this
project!

### Useful links

* [Travis-ci][travis], a continuous integration service for your public github projects
* [Docker][docker], an implementation for managing linux containers
* [Vagrant][vagrant], a tool to specify and create VirtualBox VMs
* [Hippo-docker][hippo-docker], my reference Hippo project using docker to do integration testing
* [Phantomjs][phantomjs], A headless WebKit based and scriptable browser that is very useful for headless selenium testing
* [Selenium Webdriver][webdriver], If you want to create regression and integration tests that can be run during your build

[travis]:https://travis-ci.org/
[lxc]:https://linuxcontainers.org/

[docker]:https://www.docker.io/
[docker-rhel]:http://www.infoworld.com/t/application-virtualization/red-hat-fast-tracks-docker-apps-enterprise-linux-238122
[docker-builder]:http://docs.docker.io/en/latest/reference/builder/
[docker-java]:https://github.com/kpelykh/docker-java
[docker-maven-plugin]:https://github.com/etux/docker-maven-plugin

[hippo-docker]:https://github.com/wouterd/hippo-docker
[integrationtests]:https://github.com/wouterd/hippo-docker/tree/master/myhippoproject/integrationtests
[vagrant]:http://www.vagrantup.com
[phantomjs]:http://phantomjs.org/
[webdriver]:http://docs.seleniumhq.org/projects/webdriver/

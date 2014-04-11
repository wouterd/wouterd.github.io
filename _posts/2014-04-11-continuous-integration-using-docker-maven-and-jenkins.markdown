---
layout: post
title:  "Continuous Integration Using Docker, Maven and Jenkins"
date:   2014-04-11
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

    FROM        wouterd/oracle-jre7
    VOLUME      ["/var/log/tomcat6"]
    MAINTAINER  Wouter Danes "https://github.com/wouterd"
    RUN         apt-get install -y tomcat6
    CMD         JAVA_HOME=/usr/lib/jvm/java-7-oracle CATALINA_BASE=/var/lib/tomcat6 CATALINA_HOME=/usr/share/tomcat6 /usr/share/tomcat6/bin/catalina.sh run
    EXPOSE      8080

The wouterd/oracle-jre7 has some magic to install jdk 7 from oracle on a clean ubuntu VM. The code for this container
and the two above is in the docker-images directory in the Github project. The wouterd/tomcat image does the following:

* `FROM wouterd/oracle-jre7` takes the container wouterd/oracle-jre7 (a container with Ubuntu + oracle java7 installed)
* `VOLUME ["/var/log/tomcat6"]` tells the container to expose that path to the outside world. Docker actually
  "physically" places this path outside the container so that other containers can also reach it. Further down I will
  show why this is great. (Sneak peak: syslog deprecated?)
* `RUN apt-get install -y tomcat6` installs tomcat6 using the ubuntu repository
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
MacOS X with boot2docker-cli. Refer to the __Project Requirements__ section below for the required steps and software to
be able to run `mvn verify` on your own machine.

#### Running tests with boot2docker
To be able to run the integration-tests with boot2docker, you need to
specify -Dboot2docker=[IP_of_boot2docker-vm] on the commandline, like this: `mvn verify -Dboot2docker=192.168.59.103`.
Below you can see how to figure out the IP of your boot2docker VM, the IP you need to specify is the IP of the eth1
interface:

    Wouters-MacBook-Pro-2:hippo-docker wouter$ boot2docker-cli ssh
    Warning: Permanently added '[localhost]:2022' (RSA) to the list of known hosts.
    docker@localhost's password:
                            ##        .
                      ## ## ##       ==
                   ## ## ## ##      ===
               /""""""""""""""""\___/ ===
          ~~~ {~~ ~~~~ ~~~ ~~~~ ~~ ~ /  ===- ~~~
               \______ o          __/
                 \    \        __/
                  \____\______/
     _                 _   ____     _            _
    | |__   ___   ___ | |_|___ \ __| | ___   ___| | _____ _ __
    | '_ \ / _ \ / _ \| __| __) / _` |/ _ \ / __| |/ / _ \ '__|
    | |_) | (_) | (_) | |_ / __/ (_| | (_) | (__|   <  __/ |
    |_.__/ \___/ \___/ \__|_____\__,_|\___/ \___|_|\_\___|_|
    boot2docker: 0.8.0
    docker@boot2docker:~$ ifconfig
    docker0   Link encap:Ethernet  HWaddr 56:84:7A:FE:97:99  
              inet addr:172.17.42.1  Bcast:0.0.0.0  Mask:255.255.0.0
              inet6 addr: fe80::5484:7aff:fefe:9799/64 Scope:Link
              UP BROADCAST MULTICAST  MTU:1500  Metric:1
              RX packets:71349 errors:0 dropped:0 overruns:0 frame:0
              TX packets:119482 errors:0 dropped:0 overruns:0 carrier:0
              collisions:0 txqueuelen:0
              RX bytes:3000501 (2.8 MiB)  TX bytes:169514559 (161.6 MiB)

    eth0      Link encap:Ethernet  HWaddr 08:00:27:F6:4F:CB  
              inet addr:10.0.2.15  Bcast:10.0.2.255  Mask:255.255.255.0
              inet6 addr: fe80::a00:27ff:fef6:4fcb/64 Scope:Link
              UP BROADCAST RUNNING MULTICAST  MTU:1500  Metric:1
              RX packets:415492 errors:0 dropped:0 overruns:0 frame:0
              TX packets:125189 errors:0 dropped:0 overruns:0 carrier:0
              collisions:0 txqueuelen:1000
              RX bytes:603256397 (575.3 MiB)  TX bytes:7233415 (6.8 MiB)

    eth1      Link encap:Ethernet  HWaddr 08:00:27:35:F0:76  
              inet addr:192.168.59.103  Bcast:192.168.59.255  Mask:255.255.255.0
              inet6 addr: fe80::a00:27ff:fe35:f076/64 Scope:Link
              UP BROADCAST RUNNING MULTICAST  MTU:1500  Metric:1
              RX packets:591 errors:0 dropped:0 overruns:0 frame:0
              TX packets:83 errors:0 dropped:0 overruns:0 carrier:0
              collisions:0 txqueuelen:1000
              RX bytes:94986 (92.7 KiB)  TX bytes:118562 (115.7 KiB)

    lo        Link encap:Local Loopback  
              inet addr:127.0.0.1  Mask:255.0.0.0
              inet6 addr: ::1/128 Scope:Host
              UP LOOPBACK RUNNING  MTU:65536  Metric:1
              RX packets:40 errors:0 dropped:0 overruns:0 frame:0
              TX packets:40 errors:0 dropped:0 overruns:0 carrier:0
              collisions:0 txqueuelen:0
              RX bytes:8930 (8.7 KiB)  TX bytes:8930 (8.7 KiB)

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
{% raw %}
#!/bin/bash

if [[ ${BOOT_2_DOCKER_HOST_IP} ]] ; then
    echo "Boot2Docker specified, this will work if you use the new boot2docker-cli VM.."
    boot2docker='yes'
    docker_run_args='-p 8080'
else
    boot2docker=''
    docker_run_args=''
fi

set -eu

work_dir="${WORK_DIR}"
docker_file="${DOCKER_FILE_LOCATION}"
distribution_file="${DISTRIBUTION_FILE_LOCATION}"
docker_build_dir="${work_dir}/docker-build"

mkdir -p ${work_dir}

mkdir -p ${docker_build_dir}

cp ${docker_file} ${distribution_file} ${docker_build_dir}/

image_id=$(docker build --rm -q=false ${docker_build_dir} | grep "Successfully built" | cut -d " " -f 3)
echo ${image_id} > ${work_dir}/docker_image.id

rm -rf ${docker_build_dir}

catalina_out="/var/log/tomcat6/catalina.$(date +%Y-%m-%d).log"

container_id=$(docker run ${docker_run_args} -d ${image_id})
echo ${container_id} > ${work_dir}/docker_container.id

container_ip=$(docker inspect --format '{{.NetworkSettings.IPAddress}}' ${container_id})

echo -n "Waiting for tomcat to finish startup..."

# Give Tomcat some time to wake up...
while ! docker run --rm --volumes-from ${container_id} busybox grep -i -q 'INFO: Server startup in' ${catalina_out} ; do
    sleep 5
    echo -n "."
done

echo -n "done"

if [[ ${boot2docker} ]] ; then
    # This Go template will break if we end up exposing more than one port, but by then this should be ported to Java
    # code already (famous last words...)
    tomcat_port=$(docker inspect --format '{{ range .NetworkSettings.Ports }}{{ range . }}{{ .HostPort }}{{end}}{{end}}' ${container_id}) 
    tomcat_host_port="${BOOT_2_DOCKER_HOST_IP}:${tomcat_port}"
else
    tomcat_host_port="${container_ip}:8080"
fi

echo ${tomcat_host_port} > ${work_dir}/docker_container.ip
{% endraw %}
{% endhighlight %}  

The script has some magic to resolve the port when it comes to running docker remotely, but it's mostly just running a
bunch of docker commands to build an image and start a container. I use a little bit of docker magic to monitor the log
files on the tomcat container. With the VOLUME directive in the tomcat container, I specified that the /var/log/tomcat6
folder is exposed as a volume. I can see this folder from other containers if I specify the running tomcat container with
the --volumes-from command. The following command executes grep in a small container to see if the server has already
started: `docker run --rm --volumes-from ${container_id} busybox grep -i -q 'INFO: Server startup in' ${catalina_out}`
The `--rm` option tells docker to throw away the container immediately after it exits, which is nice, because we're
repeating this quite a lot before the server has started up.

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

The `package` goal uses the maven-assembly-plugin to create the tar.gz archive that needs to be unpacked into the
  CATALINA_HOME folder on the tomcat docker container.

The `integration-test` phase is implemented using selenium webdriver tests with the phantomjs driver. But you can use
  any test-fixture here for integration tests that you'd like. An example would be using a generated apache CXF REST
  proxy to do validation of a rest interface that you created.

### Project requirements

* Docker installed and a daemon running, either on the host machine or in a [boot2docker-cli][boot2docker-cli] VM, OS X
  and linux will work, Windows will not work until it gets a docker CLI or I migrate all bash scripts to java code. If
  you are running boot2docker make sure you have the DOCKER_HOST environment variable set. Here's a oneliner that'll
  work with standard settings: `export DOCKER_HOST=tcp://localhost:4243`. Also, you need to figure out the IP of your
  boot2docker-vm on the host-only network. (eth1, by default the IP is `192.168.59.103`)
* PhantomJS needs to be installed and on the path
* Git isn't required but it useful to be able to get the project. ;-)
* Maven 3.x
* Java 7+ (Oracle JDK preferred)
* You need to run ./build-docker-images.sh once to build the oracle jdk7 and tomcat images before you can run the
  integration tests.

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
* At the moment, this project can't run on Windows, because it relies on the docker CLI, I should migrate the scripts
  to the [docker client java api][docker-java], so it can run seamless on Windows hosts as well (with boot2docker-cli).

### Useful links

* [Travis-ci][travis], a continuous integration service for your public github projects
* [Docker][docker], an implementation for managing linux containers
* [Docker-cli][docker-cli], a way to run docker on a Windows or MacOS host using a very minimal memory footprint
* [Vagrant][vagrant], a tool to specify and create VirtualBox VMs
* [Hippo-docker][hippo-docker], my reference Hippo project using docker to do integration testing
* [Phantomjs][phantomjs], A headless WebKit based and scriptable browser that is very useful for headless selenium testing
* [Selenium Webdriver][webdriver], If you want to create regression and integration tests that can be run during your build

Please leave a comment in the comments section below, let me know if you have any questions or if anything is unclear.
Or if you know better ways to do things I did in this project! Or even better: to share how you used this in your
projects!

[travis]:https://travis-ci.org/
[lxc]:https://linuxcontainers.org/

[docker]:https://www.docker.io/
[boot2docker-cli]:https://github.com/boot2docker/boot2docker-cli
[docker-rhel]:http://www.infoworld.com/t/application-virtualization/red-hat-fast-tracks-docker-apps-enterprise-linux-238122
[docker-builder]:http://docs.docker.io/en/latest/reference/builder/
[docker-java]:https://github.com/kpelykh/docker-java
[docker-maven-plugin]:https://github.com/etux/docker-maven-plugin

[hippo-docker]:https://github.com/wouterd/hippo-docker
[integrationtests]:https://github.com/wouterd/hippo-docker/tree/master/myhippoproject/integrationtests
[vagrant]:http://www.vagrantup.com
[phantomjs]:http://phantomjs.org/
[webdriver]:http://docs.seleniumhq.org/projects/webdriver/

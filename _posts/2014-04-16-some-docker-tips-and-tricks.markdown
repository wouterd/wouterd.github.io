---
layout: post
title:  "Some Docker Tips and Tricks"
date:   2014-04-16
categories:
 - docker
 - infra
summary: "Docker is a great tool, which can be daunting at first. Shells can be annoying to work with and have their
  gotchas. This post has some quick tips, tricks and shell one liners to help you use Docker"
---
### Removing All Containers and Images (Spring Cleaning)
Spring cleaning one liner:

    {% raw %} docker kill $(docker ps -q) ; docker rm $(docker ps -a -q) ; docker rmi $(docker images -q -a) {% endraw %}

This might give a warning when there are no running containers or containers at all, but it's a nice one liner when
you're trying things out. If you just want to remove all containers, you can run:

    {% raw %} docker kill $(docker ps -q) ; docker rm $(docker ps -a -q) {% endraw %}

### Remove Containers On Exit
If you only want to quickly run a command in a container and exit and aren't worried about the end state, add `--rm`
to the docker `run` command, this will really end up saving you a lot of containers to clean up!

Example: `docker run --rm -i -t busybox /bin/bash`

### Boot2Docker and Laptops on the Move Means DNS Woes
I have about three locations where I use my laptop and they're all on different ISPs. [Boot2docker][boot2docker-cli]
tends to hold on to DNS servers for a bit too long and because of that, you might get weird errors when trying to build
images. If you see

    cannot lookup archive.ubuntu.com

on Ubuntu or something similar on CentOS, it might be wise to stop and start your
boot2docker just to be sure:

    boot2docker-cli down && boot2docker-cli up

Things should work again after that.

### Volumes Beat `docker logs` and `docker copy`
If you want to monitor log files or use files from one container on another, have a look at
[volumes](http://docs.docker.io/use/working_with_volumes/#volume-def). For example, to check if Tomcat has started up:

{% highlight bash %}
{% raw %}
tomcat_id=$(docker run -d -v /var/log/tomcat6 wouterd/tomcat6)
# Give Tomcat some time to wake up...
sleep 5
while ! docker run --rm --volumes-from ${tomcat_id} busybox /bin/sh -c "grep -i -q 'INFO: Server startup in' /var/log/tomcat6/catalina*.log" ; do
    echo -n "."
    sleep 5
done
{% endraw %}
{% endhighlight %}

You can also specify the volumes to share inside a Dockerfile, which I did in my previous blog post on continuous
integration with docker.

### Docker Inspect Plus Go Templates Equals Profit
The command `docker inspect` allows the use of [Go templates][gotemplates] to format the output of the inspect command.
If you're good at that, you can get a lot of great information out of docker containers from the commandline in shell
scripts. Here's one to get the IP of a running container:

    {% raw %}container_ip=$(docker inspect --format '{{.NetworkSettings.IPAddress}}' ${container_id}) {% endraw %}

Here's a very silly one to get the host:port mapping of all exposed ports and put them in a java properties file:

{% highlight bash %}
{% raw %}
sut_ip=${BOOT_2_DOCKER_HOST_IP}
template='{{ range $key, $value := .NetworkSettings.Ports }}{{ $key }}='"${BOOT_2_DOCKER_HOST_IP}:"'{{ (index $value 0).HostPort }} {{ end }}'
tomcat_host_port=$(docker inspect --format="${template}" ${container_id})
for line in ${tomcat_host_port} ; do
    echo "${line}" >> ${work_dir}/docker_container_hosts.properties
done
{% endraw %}
{% endhighlight %}

### See also
[My post on continuous integration using docker and maven]({% post_url 2014-04-11-continuous-integration-using-docker-maven-and-jenkins %})

[gotemplates]:http://golang.org/pkg/text/template/
[docker]:https://www.docker.io/
[boot2docker-cli]:https://github.com/boot2docker/boot2docker-cli

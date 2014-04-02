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

### Docker
[Docker][docker] is a tool allows you to run multiple processes in their own containers on a machine without the
overhead of virtual machines. It does give process separation and to the processes it looks like they're running in
their own linux environment. Docker was created in Ruby and is basically an interface to [Linux Containers][lxc] (LXC),
a feature of the linux kernel that was introduced in the 3.8 release.

Docker works well under Ubuntu and [Red Hat is working to make it enterprise ready for RHEL][docker-rhel].

Docker allows you to specify containers using [Dockerfiles][docker-builder]. These can be compared to Vagrantfiles that
are used to provision virtual machines.





[travis]:https://travis-ci.org/
[docker]:https://www.docker.io/
[lxc]:https://linuxcontainers.org/
[docker-rhel]:http://www.infoworld.com/t/application-virtualization/red-hat-fast-tracks-docker-apps-enterprise-linux-238122
[docker-builder]:http://docs.docker.io/en/latest/reference/builder/

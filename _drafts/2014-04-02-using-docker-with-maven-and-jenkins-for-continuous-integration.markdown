---
layout: post
title:  "Using Docker, Maven and Jenkins for Continuous Integration"
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



[travis]:https://travis-ci.org/

---
layout: post
title:  "Three great development tools"
date:   2014-06-27
categories:
 - development
 - tooling
 - ack
 - httpie
 - Fira Sans
summary: "I use many tools for day to day productivity. Of course there is IDEs, like IntelliJ, web storm and to an
extent Atom. But there's many little nifty gems that get shown to me by peers which I end up adopting. In this post I
will cover httpie: a curl on steroids, ack: a replacement for grep and my new favourite font: Fira Sans Mono"
---
### Font as a tool? Well yes!
The new Firefox typeface has a great fixed width variant. I use it in my code editors, terminal and on this blog. The
great thing about this font is that it distinguishes really well between characters that can be mistaken for each other.

    As I'll happily show you there is 0 collisions between l and I, 0 and O, etc etc.

The name of this mono spaced font is Fira Mono. You can download the font [here][firamono]

### Beyond grep: ack
Ack is grep, but then made for developers. I was already happy with `git grep`, until [Ernst Naezer][ernst] told me
about ack. The nice thing about ack is that searches by developers tend to take less keystrokes. For example, when I'm
looking for usages of the class `JsonSlurper` in .java files, I'll simply type `ack JsonSlurper --java`. If I want to
show more than one line per file, I can use the -C option: `ack JsonSlurper --java -C`. The tool is blazingly fast,
`git grep` level. I've used it for a week now and I'm already hooked on it! You can get Ack [here][ack]. Or on macs,
simply install it with homebrew: `brew install ack`

### Fiddle with RESTful APIs: httpie
When developing or exploring RESTful APIs, it's useful to be able to fiddle with them on the command line. Httpie is
the single best tool I've seen to date that doesn't require a UI. One of the great things about httpie is that it's
JSON aware, which allows you to do really cool things! Have a look at the web site [here][httpie]. Easily installed on
macs with `brew install httpie` or on most linux distros: `yum install httpie` or `apt-get install httpie`.

Here's some examples, the actual command for httpie is called `http`:

  - POST a file to a web service running on localhost: `http POST :8080/import < path_to_file`
  - httpie posts a json by default: `http :8080/people first=Wouter last=Danes` will post a JSON to
  http://localhost:8080/people. The JSON will contain: `{ "first" : "Wouter", "last":"Danes" }`.

Read the output of `http --help`, you'll be surprised at what it can do!

[firamono]:http://www.carrois.com/fira-3-1/
[ack]:http://beyondgrep.com/
[ernst]:https://twitter.com/ernstnaezer
[httpie]:https://github.com/jakubroztocil/httpie

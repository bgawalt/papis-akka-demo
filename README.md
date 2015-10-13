# papis-akka-demo

Hello! This repository is a mild demonstration of deploying a predictive model, accessible through
an HTTP API, can be built using the actor model. It makes use of Scala's Akka and Spray libraries
to let you train and utilize a simple, two-class text classifier.

You shouldn't need more than a Java Runtime Environment to make use of this toolset -- if calling
 `java -version` from your command prompt tells you have version 1.7 or higher, I bet you'll be
fine.

# Starting up the service

This codebase runs through [SBT](http://www.scala-sbt.org). To launch the service, we'll pass SBT
an argument telling it to run the `main()` routine in the `ClassifierServer` object. This will
then kick off compilation of the code, then execute it:

```
$ sbt/sbt 'runMain com.gawalt.papis_akka_demo.ClassifierServer'
[info] Set current project to papis-akka-demo (in build file:/Users/bgawalt/papis-akka-demo/)
[info] Updating {file:/Users/bgawalt/papis-akka-demo/}papis-akka-demo...
[info] Resolving org.fusesource.jansi#jansi;1.4 ...
[info] Done updating.
[info] Compiling 6 Scala sources to /Users/bgawalt/papis-akka-demo/target/scala-2.10/classes...
[info] Running com.gawalt.papis_akka_demo.ClassifierServer 
[INFO] [10/12/2015 10:50:29.749] [papis-akka-demo-akka.actor.default-dispatcher-2] [akka://papis-akka-demo/user/IO-HTTP/listener-0] Bound to localhost/127.0.0.1:12345
```

That final `[INFO]` line, once it appears, tells us that the program is listening on socket 
127.0.0.1:8080. (You can switch up the exact port by modifying line 25 in 
`com/gawalt/papis_akka_demo/ClassifierServer.scala` -- the field named `SERVICE_PORT`.) 
You now have a service ready to construct a text classifier from example documents you provide it,
and to apply that learned model to new documents you ask it to score.
 
# Using the service

Once it's up and running, you can start interacting with the service via HTTP requests to five 
paths: `hello`, `observe`, `predict`, `status`, and `reset`.

## http://localhost:12345/hello/some_text/to_echo

This path provides some nice "hello, world" functionality to test the responsiveness of the
system. Whatever you pass as a path after `hello/` will be split by slashes and returned, one line
each. You can try it in your browser:

![hello/ path response](/doc/fig/hello_browser_example.png)

Funnily enough, though, your browser might ask for an additional resource: mine also wanted a
`favicon.ico` file to populate the browser tab. The service has no idea what that could mean, and
in the terminal where the service is running has its complaints registered via `println`:
 
![favicon error](/doc/fig/favicon_requests.png)
 
One of these errors will be "logged" every time a request comes in that deviates from the five paths
the service is prepared to handle. Here's an example of me deliberately pointing my browser at an
unsupported path, along with the terminal running the service application:

![unsupported path](/doc/fig/unmatched_path.png)

## http://localhost:12345/observe/[label]/text_of_known_document

For the classifier to be of any use, you're going to have to provide it at least a few examples
from each of the two classes. This path lets you pass documents with known class labels for the
service's classifier to observe:

1. Populate the `[label]` field with either `true` or `false`, depending on whether or not
the document you're observing. Case sensitive!
2. Pass in the document itself as a string containing letters and numbers 
(i.e., scrub out any punctuation) while using an underscore to separate word tokens.
  
Consider a scenario where we want to try and identify spam content. Imagine a positive example
that reads "Great 1nvestment, buy NOW!!" -- actual spam -- and a negative example reading
"Sounds good to me; let's hash it out more Monday" -- a non-spam document. We could register each
of these in turns with the following request paths, respectively:

* `http://localhost:12345/observe/true/great_1nvestment_buy_now`
* `http://localhost:12345/observe/false/sounds_good_to_me_lets_hash_it_out_more_monday`

Couple million more such observations, and we're on our way to a state-of-the-2003 spam filter!

Breaking this format will return an error message to the requesting client:

![bad observe request](/doc/fig/bad_observe_request.png)

## http://localhost:12345/predict/words_from_a_doc_of_unknown_class

To now apply the knowledge accumulated in the classifier with all those `observe` calls, there's
a request path by which you can score new, unlabelled documents. Format the document similar to
how you've been preparing the documents passed in the `observe` path, and include it as the last
element of the request path.

What comes back is a real-valued number basically representing the posterior log-odds that a 
document containing those tokens would be a member of the positive class, given all the observations
made about token-appearance rates in positive and negative documents. A highly-positive number means
greater faith that the document is in the positive class. Here's an example prediction on a trained
up classifier that returns a positive prediction:

![positive doc predict](/doc/fig/positive_doc_predict.png)

and similarly for a doc the classifer thinks is a negative document:

![negative doc predict](/doc/fig/negative_doc_predict.png)

If the classifier has no previous recollection of any of the tokens in your document, it
falls back to an estimate based just on the ratio of positive examples to negative examples it's
seen. Here's what the trained-up classifier thinks of a bunch of nonsense:

![nonsense doc predict](/doc/fig/crazy_doc_predict.png)

Pretty close to zero! This classifier's been exposed to an equal number of positive and negative
examples, so it's not making a strong statement one way or the other.

As with `observe`, deviating from this request pattern just returns an error:

![bad predict request](/doc/fig/bad_predict_request.png)

## http://localhost:12345/status

Following this path will return information about how many documents the classifier has observed
up to this point, and the number of distinct word tokens it's learned about:

![status request](/doc/fig/status_request.png)

## http://localhost:12345/reset

This path allows to reset the classifier back to it's initial state: it forgets everything it's
ever learned. No positive examples, no negative examples, no language model. A successful request
returns the new zeroed-out status:

![reset request](/doc/fig/reset_request.png)

# PAPIs 2015

I gave a talk in August 2015, at the 2nd International  Conference on Predictive APIs. It was about
the appeal and convenience of using the actor framework to deploy machine learning applications.
You can find a copy of the slides on [Slideshare](http://www.slideshare.net/papisdotio/research-deploying-predictive-models-with-the-actor-framework-brian-gawalt).

If you're interested in attending a future PAPIs event, stay tuned to 
[papis.io](http://www.papis.io/) - the 2016 conference will be held in the United States.
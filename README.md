# papis-akka-demo

Hello! This repository is a mild demonstration of deploying a predictive model, accessible through
an HTTP API, can be built using the actor model. It makes use of Scala's Akka and Spray libraries
to let you train and utilize a simple, two-class text classifier.

You shouldn't need more than a Java Runtime Environment to make use of this toolset -- if calling
 `java -version` from your command prompt tells you have version 1.7 or higher, I bet you'll be
fine.

# Repository Highlights

Use this repo as a way to get familiar with building a web API using an actor framework. Actors
are great! They make it quite straight-forward to reason about concurrency without stumbling into
false-sharing and other common pitfalls. The major code components to look at are:

* [ClassifierServer.scala](src/main/scala/com/gawalt/papis_akka_demo/ClassifierServer.scala) -
this file describes what socket the web API should listen to (`ClassifierServer.main()`),
how requests should be parsed and handled (`ServiceActor.route`), where we should maintain state
information about the model itself (`class Librarian`), and that stateful actor should be sent
requests to process (the container classes `PredictMsg`, `ObserveMsg`, `StatusMsg`, `ResetMsg`).
* [ClassifierClient.scala](src/main/scala/com/gawalt/papis_akka_demo/ClassifierClient.scala) -
this is a simple routine to test out a particular text classification task. Movie review summaries,
for either five-star or one-star appraisals, are read in from the TSV files kept in `resources/`,
and sent off to the modelling service to be studied and classified. Note that it in generating
predictions for these makes no reference to any class outside of the standard Scala library --
it's only hitting the web API provided by a running instance of `ClassifierServer`.
* [TextClassifier.scala](src/main/scala/com/gawalt/papis_akka_demo/TextClassifier.scala) --
a simple Naive Bayes classifier for documents, treating each distinct word token as a
[Bernoulli random variable](https://en.wikipedia.org/wiki/Bernoulli_distribution) when conditioned
on the document class. A mildly-biased estimate of each word's Bernoulli parameter is made
by tracking the number of documents seen of each class, and the number of times each word token
has appeared in documents of each class.

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

# Evaluating the service

To let you try out the classifier on a large, real-world dataset, I've contrived a classification
problem: from the headline alone, can the model learn the difference between 1-star and 5-star
reviews?

The Stanford Network Analysis Project hosts a
[dataset of movie reviews](https://snap.stanford.edu/data/web-Movies.html) posted to Amazon,
curated by Julian McAuley and Jure Leskovec. I've stripped each review down to its `review/score`
and `review/summary` components, formatted to accord with the service API, and packaged a
subsample of them in this repo:

```
$ head -5 resources/*
==> resources/five_star_reviews_subset.tsv <==
5.0 this_movie_needed_to_be_made
5.0 a_rock_n_roll_history_lesson
5.0 a_musthave_video_if_you_grew_up_in_the_s_or_s
5.0 if_you_like_doowop_you_gotta_have_this_dvd
5.0 professional_excellence```

```==> resources/one_star_reviews_subset.tsv <==
1.0 this_is_junk_stay_away
1.0 truly_bad_but_not_the_worst
1.0 should_be_locked_in_chateau_dif
1.0 the_count_of_monte_cristo
1.0 a_disappointment_for_book_fans
```

The `main()` routine in `com.gawalt.papis_akka_demo.ClassifierClient` alternates sending a
five-star review, then a one-star review, bouncing back and forth and keeping basic parity.
Before observing and incorporating a new review into the model, though, we can first ask the
model to make a prediction of that review's label. `ClassifierClient` spins through 20,000
reviews, keeps track whether was incorrectly predicted, then bins that sequence of predictions
into buckets of size 500. The number of errors in each bucket is then printed to the screen.

Once we're sure that the service is running in another process, we can run the evaluation via SBT:

```
$ sbt/sbt 'runMain com.gawalt.papis_akka_demo.ClassifierClient resources/five_star_reviews_subset.tsv resources/one_star_reviews_subset.tsv'
[info] Set current project to papis-akka-demo (in build file:/Users/brian/papis-akka-demo/)
[info] Running com.gawalt.papis_akka_demo.ClassifierClient resources/five_star_reviews_subset.tsv resources/one_star_reviews_subset.tsv
One-star predict: ArrayIndexOutOfBoundsException:	1
   [... several more such exceptions are thrown ...]
One-star update: ArrayIndexOutOfBoundsException:	1
333, 235, 230, 214, 181, 231, 225, 221, 226, 220, 197, 219, 148, 125, 212, 198, 206, 207, 201, 143, 66, 184, 192, 219, 176, 182, 161, 188, 196, 200, 191, 206, 164, 185, 200, 191, 179, 183, 131, 187,
Not interrupting system thread Thread[Keep-Alive-Timer,8,system]
[success] Total time: 46 s, completed Oct 12, 2015 11:19:14 PM
```

Forty-six second runtime -- not bad! (There's a handful of `ArrayIndexOutOfBoundsException`s;
one's triggered whenever a review is missing and only the score is present.)

Plotting this error rate, we can see an achingly slow but undeniable improvement in the model's
accuracy as it accumulates more data:

QQQ PUT PLOT HERE

Checking the status of the model, we can see that it's learned about 15,000 word tokens
after viewing around 20,000 review summaries:

```$ curl http://localhost:12345/status/
   Num pos: 9993,
   Num neg: 9993,
   Num tokens: 15240```

So it's not surprising that it's slow to get a handle on what's good and what's bad: a giant
share of the documents are feeding it words it's never seen before. In fact, if we cheat and
re-run the `ClassifierClient.main()` routine again without resetting the mode -- by letting the
model make predictions on reviews that already knows are coming -- we can see that error rates
immediately drop below 1%:

QQQ PUT OTHER PLOT HERE

Woe betide thee, o over-fitter!

# Concurrency

On my MacBook Pro, we can see the `ClassifierClient` experiment issue 20,000 sequential predict and
observation calls in 46 seconds, putting a lower bound on throughput at around 430
predict-then-observe pairs per second, or about 2 milliseconds per predict-observe pair. That's
not so bad, given that anyone using a website that relies on this service is probably in for
75-100 milliseconds of render time anyway -- another 2 ms isn't breaking the bank.

But the whole appeal of the Actor model is to try and expose concurrency in an easy-to-reason-about
way, so that the throughput can be made not just "good enough" but genuinely optimal. The current
architecture isn't set up to truly do that.

It's true that a new Actor is spun up to handle each request sent to the service API.
So things like parsing the request paths, validating them, and preparing the arguments for the
model can happen in parallel if multiple users are issuing concurrent calls.

But unfortunately, the whole operation bottlenecks when it's time to actually use the model itself.
Interesting we want to do involves interacting with the `librarian` actor instantiated on line
31 of `ClassifierServer.scala`. This actor holds the text classifier as part of its private state.
Every prediction requested or update issued has to be fully completed before a `librarian` can
move onto the next one in line. That's a huge portion of our computational load that's
resistant to parallelization, setting an awful low
[Amdahl's Law](https://en.wikipedia.org/wiki/Amdahl%27s_law) performance ceiling.

Keep your eyes on this space, though -- I have a possible alternative architecture in mind that
could help address this.

# PAPIs 2015

I gave a talk in August 2015, at the 2nd International  Conference on Predictive APIs. It was about
the appeal and convenience of using the actor framework to deploy machine learning applications.
You can find a copy of the slides on [Slideshare](http://www.slideshare.net/papisdotio/research-deploying-predictive-models-with-the-actor-framework-brian-gawalt).

If you're interested in attending a future PAPIs event, stay tuned to 
[papis.io](http://www.papis.io/) - the 2016 conference will be held in the United States.
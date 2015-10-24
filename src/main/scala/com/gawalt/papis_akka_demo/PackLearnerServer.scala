package com.gawalt.papis_akka_demo

import akka.actor.{ActorRef, Props, ActorSystem, Actor}
import akka.io.IO
import akka.util.Timeout
import spray.can.Http
import spray.routing
import spray.routing.HttpServiceActor
import scala.concurrent.duration._
import akka.pattern.ask

import scala.util.{Failure, Success, Try}

/**
 * Authored by bgawalt on 10/23/15.
 */
class PackLearnerServer {

  /** The port on localhost that our HTTP API should listen to when fielding requests */
  val SERVICE_PORT = 12346

  def main(args: Array[String]) {

    // How long should a received client request be allowed to hang before it's auto-completed
    // by the execution context with a "TIMEOUT!" error message?
    implicit val timeout = Timeout(5.seconds)

    // This is the system that will manage our two actors, `noahWyle` and `service`.
    // It decides who gets to make use of what computing resources (CPU? RAM? Disk I/O?) at any
    // given moment. It also keeps track of the messages that are stacking up in our actor's queues,
    // and ensures that any message sent to an actor gets routed to the right place.
    implicit val system = ActorSystem("papis-akka-demo-pack-server")


    // An instance of our Librarian class, which houses the TextClassifier as its private state
    val learners = (0 until 10).map(_ => system.actorOf(Props[LoneLearner]))

    // An instance of our ServiceActor class, the Actor which will parse and pass along
    // client requests to noahWyle at his actorRef address
    val service = system.actorOf(Props(new PackParser(learners)), "PackLearnerService")

    // Route HTTP traffic on the specified socket to our service Actor -- any HTTP request
    // causes `system` to place a message object of type RequestContext to `service`'s queue
    IO(Http) ? Http.Bind(service, interface = "localhost", port = SERVICE_PORT)
  }

}

case class SyncUpdateMsg(update: TextClassifier)

class PackLearner(val comrades: Seq[ActorRef],
                  val maxUpdates: Int, val updateTrigger: Int) extends Actor {
  require(maxUpdates > 0, "maxUpdates must be postive")
  require(updateTrigger >= 0 && updateTrigger < maxUpdates,
    "updateTrigger must be between 0 and maxUpdates - 1")

  // Text classification model. See com.gawalt.papis_akka_demo.TextClassifier for more details.
  val mainCls = new TextClassifier()
  val updateCls = new TextClassifier()

  def predict(doc: String): Double = {
    val numMain = mainCls.numObservations
    val numUpdt = updateCls.numObservations
    val numTotal = numMain + numUpdt
    if (numTotal == 0) 0.0
    else (numMain * mainCls.predict(doc) + numUpdt * updateCls.predict(doc))/numTotal
  }

  def status: String = s"Main Classifier Status:\n${mainCls.status}\n" +
    s"Update Classifier Status:\n${updateCls.status}\n"

  /**
   * Any messages received have their constituent fields parsed out. Documents are passed along
   * to the appropriate TextClassifier methods, and the results of those method calls are used
   * to complete the RequestContext, returning information back to the client.
   *
   * As with all Actors, each message received is processed in full before the next message in
   * the queue is looked at. This helps ensure that there's no risk of two competing update requests
   * stomping out each other's modifications.
   */
  def receive = {
    case PredictMsg(ctx, doc) =>
      ctx.complete(predict(doc).toString)
    case ObserveMsg(ctx, doc) =>
      updateCls.observe(doc)
      val syncMsg = if (updateCls.numObservations % maxUpdates == updateTrigger) {
        comrades.foreach(c => c ! SyncUpdateMsg(updateCls))
        "Sync update transmitted"
      }
      else ""
      ctx.complete(status + s"\n$syncMsg\n")
    case StatusMsg(ctx) =>
      ctx.complete(status)
    case ResetMsg(ctx) =>
      mainCls.reset()
      updateCls.reset()
      ctx.complete("Reset classifier: \n" + status)
    case x: Any =>
      // If it's not one of the four main routines, log the error to the console
      println(s"ERROR: Unexpected message received, ${x.getClass.getSimpleName}")
  }
}


class PackParser(val learners: IndexedSeq[ActorRef]) extends HttpServiceActor {

  val numLearners = learners.length
  require(numLearners > 0, "Must supply a positive number of learners")
  var currentLearnerIndex = 0

  // What should we do with requests we receive? Pass them to `route`.
  def receive = runRoute(route)

  // A function that performs actions on a RequestContext object, named `ctx` in this definition
  val route: routing.Route = {ctx =>
    // Find the portion of the URL requested that comes after `http://localhost:port`
    val path = ctx.unmatchedPath.toString()
    val splitPath = path.split("/").tail  // `.tail()` drops the leading `/` character

    // Supported paths follow up `http://localhost:port/` with `predict/..`, `observe/..`,
    // `status/`, `reset/`, or `hello/`. Find out which of these paths is being requested, if any.
    splitPath.head match {
      case "predict" =>
        // Make sure the path requested has the right format: `predict/document_text_follows`
        // If it looks ok, forward the request and the document text to the librarian
        if (splitPath.length == 2) {
          learners(currentLearnerIndex) ! PredictMsg(ctx, splitPath(1))
          currentLearnerIndex = (currentLearnerIndex + 1) % numLearners
        }
        // If it doesn't look right, inform the client by completing the RequestContext
        else ctx.complete(s"Invalid prediction request: ${splitPath.mkString("/")}")

      case "observe" =>
        // Try to parse the requested path into a set of valid `observe` arguments
        Try({
          require(splitPath.length == 3,
            "Provided path doesn't match expected format of '[true, false]/doc_text_underscore'")
          val label = splitPath(1).toBoolean
          val text = splitPath(2)
          LabelledDocument(text, label)
        }) match {
          // If it worked, pass along to the librarian
          case Success(ld) => learners(currentLearnerIndex) ! ObserveMsg(ctx, ld)
            currentLearnerIndex = (currentLearnerIndex + 1) % numLearners
          case Failure(f) =>
            // If it failed, inform the client by completin the RequestContext
            ctx.complete(s"Invalid prediction request: ${splitPath.mkString("/")}" +
              s"${f.getClass.getSimpleName}, ${f.getMessage}")
        }

      // If the request is for the current model status, pass the context off to the Librarian
      case "status" => ctx.complete("I don't know what to put here right now")
      // and likewise for a request to reset the model back to the zero status
      case "reset" => learners.foreach(learner => learner ! ResetMsg(ctx))

      // Hey, here's just a fun one for joshing around. Just immediately complete the
      // RequestContext by echoing the resource requested, broken into new lines at the slashes
      case "hello" =>
        ctx.complete(s"Hello! You requested:\n${splitPath.tail.mkString("\n")}")

      // If the path doesn't match any of the above, complete the request and
      // log the error to console output
      case _ =>
        println(s"ERROR: Unrecognized resource request: /${splitPath.mkString("/")}")
        ctx.complete(s"ERROR: Unrecognized resource request: /${splitPath.mkString("/")}")
    }
  }
}
package com.gawalt.papis_spray_demo

import akka.actor.{ActorRef, Props, ActorSystem, Actor}
import akka.io.IO
import spray.can.Http
import akka.pattern.ask
import spray.routing.HttpService
import spray.http.MediaTypes
import MediaTypes._
import akka.util.Timeout
import scala.concurrent.duration._
import scala.collection.mutable
import spray.routing.RequestContext

/**
 * This source file created by Brian Gawalt, 9/9/15.
 * It is subject to the MIT license bundled with this package in the file LICENSE.txt.
 * Copyright (c) Brian Gawalt, 2015
 */
object ClassifierServer {

  def main(args: Array[String]) {
    // we need an ActorSystem to host our application in
    implicit val system = ActorSystem("on-spray-can")

    val noahWyle = system.actorOf(Props[Librarian])

    // create and start our service actor
    val service = system.actorOf(Props(new MyServiceActor(noahWyle.actorRef)), "demo-service")

    implicit val timeout = Timeout(5.seconds)
    // start a new HTTP server on port 8080 with our service actor as the handler
    IO(Http) ? Http.Bind(service, interface = "localhost", port = 8080)
  }

}

case class IncrementMsg(s: String)
case class CheckRequestMsg(s: String)
case class CheckResultMsg(s: String, c: Int)
case class CompleteMsg(ctx: RequestContext, out: String)
class Librarian extends Actor {

  val wordCounts = mutable.HashMap.empty[String, Int]

  def receive = {
    case CompleteMsg(ctx, out) =>
      println(s"Time to complete $out")
      ctx.complete(out)
    case IncrementMsg(s) =>
      val curr = wordCounts.getOrElse(s, 0)
      wordCounts(s) = curr + 1
    case CheckRequestMsg(s) =>
      sender ! CheckResultMsg(s, wordCounts.getOrElse(s, 0))
    case _ => println("NOOOO!!!")
  }

}

class MyServiceActor(val librarian: ActorRef) extends Actor with MyService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)
}


// this trait defines our service behavior independently from the service actor
trait MyService extends HttpService {

  val librarian: ActorRef

  val myRoute = {
    path("predict") { ctx =>
      librarian ! CompleteMsg(ctx, "predict")
    } ~
    path("update") { ctx => librarian ! CompleteMsg(ctx, "update") }
      //ctx.complete("IT ME")
      /*get {
        respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
          complete {
            <html>
              <body>
                <h1>Say hello to <i>spray-routing</i> on <i>spray-can</i>!</h1>
              </body>
            </html>
          }
        }
      }*/
    }

}
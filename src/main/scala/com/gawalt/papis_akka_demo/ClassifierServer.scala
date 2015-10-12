package com.gawalt.papis_akka_demo

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

import scala.util.{Failure, Success, Try}
import spray.routing

/**
 * This source file created by Brian Gawalt, 9/9/15.
 * It is subject to the MIT license bundled with this package in the file LICENSE.txt.
 * Copyright (c) Brian Gawalt, 2015
 */
object ClassifierServer {

  def main(args: Array[String]) {

    implicit val system = ActorSystem("papis-akka-demo")

    val noahWyle = system.actorOf(Props[Librarian])

    val service = system.actorOf(Props(new ServiceActor(noahWyle.actorRef)), "papis-akka-service")

    implicit val timeout = Timeout(5.seconds)

    IO(Http) ? Http.Bind(service, interface = "localhost", port = 8080)
  }

}

case class PredictMsg(ctx: RequestContext, doc: String)
case class ObserveMsg(ctx: RequestContext, doc: LabelledDocument)
case class StatusMsg(ctx: RequestContext)
case class ResetMsg(ctx: RequestContext)

class Librarian extends Actor {

  val cls = new TextClassifier()

  def receive = {
    case PredictMsg(ctx, str) =>
      ctx.complete(cls.predict(str).toString)
    case ObserveMsg(ctx, doc) =>
      cls.observe(doc)
      ctx.complete(cls.status)
    case StatusMsg(ctx) =>
      ctx.complete(cls.status)
    case ResetMsg(ctx) =>
      cls.reset()
      ctx.complete("Reset classifier: \n" + cls.status)
    case _ =>
      println("ERROR: Unexpected message received!!")
  }

}

class ServiceActor(val librarian: ActorRef) extends Actor with HttpService {

  def actorRefFactory = context
  def receive = runRoute(newRoute)

  val myRoute = {
    path("hello") {ctx => ctx.complete("Hello!")} ~
    path("predict") { ctx =>
      val text = ctx.unmatchedPath.toString()
      librarian ! PredictMsg(ctx, text)
    } ~
    path("observe") { ctx =>
        Try({
          val splitPath = ctx.unmatchedPath.toString().split("/")
          require(splitPath.lengthCompare(2) == 0,
            "Provided path doesn't match expected format of '[true, false]/doc_text_underscore")
          val label = splitPath(0).toBoolean
          val text = splitPath(1)
          LabelledDocument(text, label)
        }) match {
          case Success(ld) => librarian ! ObserveMsg(ctx, ld)
          case Failure(f) => ctx.complete(s"ERROR: ${f.getClass.getSimpleName}, ${f.getMessage}")
        }
    } ~
    path("status") { ctx => librarian ! StatusMsg(ctx) } ~
    path("reset") { ctx => librarian ! ResetMsg(ctx) }
  }

  val newRoute: routing.Route = {ctx =>
    val path = ctx.unmatchedPath.toString()
    val splitPath = path.split("/").tail
    splitPath.head match {
      case "predict" =>
        if (splitPath.length == 2) librarian ! PredictMsg(ctx, splitPath(1))
        else ctx.complete(s"Invalid prediction request: ${splitPath.mkString("/")}")
      case "observe" =>
        Try({
          require(splitPath.length == 3,
            "Provided path doesn't match expected format of '[true, false]/doc_text_underscore")
          val label = splitPath(1).toBoolean
          val text = splitPath(2)
          LabelledDocument(text, label)
        }) match {
          case Success(ld) => librarian ! ObserveMsg(ctx, ld)
          case Failure(f) => ctx.complete(s"ERROR: ${f.getClass.getSimpleName}, ${f.getMessage}")
        }
      case "status" => librarian ! StatusMsg(ctx)
      case "reset" => librarian ! ResetMsg(ctx)
      case "hello" =>
        ctx.complete(s"Hello! You requested:\n${splitPath.tail.mkString("\n")}")
      case _ =>
        println(s"Unrecognized resource request: /${splitPath.mkString("/")}")
    }
  }
}
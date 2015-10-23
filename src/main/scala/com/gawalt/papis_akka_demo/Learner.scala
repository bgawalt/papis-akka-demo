package com.gawalt.papis_akka_demo

import akka.actor.Actor

/**
 * This source file created by Brian Gawalt, 10/22/15.
 * It is subject to the MIT license bundled with this package in the file LICENSE.txt.
 * Copyright (c) Brian Gawalt, 2015
 */
trait Learner extends Actor {

  def predict(doc: String): Double
  def observe(doc: LabelledDocument): Unit
  def status: String
  def reset(): Unit
  def synchronize(): Unit

  def receive = {
    case PredictMsg(ctx, doc) =>
      ctx.complete(predict(doc).toString)
    case ObserveMsg(ctx, doc) =>
      observe(doc)
      ctx.complete(status)
    case StatusMsg(ctx) =>
      ctx.complete(status)
    case ResetMsg(ctx) =>
      reset()
      ctx.complete("Reset classifier: \n" + status)
    case x: Any =>
      // If it's not one of the four main routines, log the error to the console
      println(s"ERROR: Unexpected message received, ${x.getClass.getSimpleName}")
  }

}


class LoneLearner extends Learner {
  val cls = new TextClassifier()
  def predict(doc: String): Double = cls.predict(doc)
  def observe(doc: LabelledDocument): Unit = cls.observe(doc)
  def status: String = cls.status
  def reset(): Unit = cls.reset()
  def synchronize(): Unit = {} // No operation needed here
}

class PackLearner
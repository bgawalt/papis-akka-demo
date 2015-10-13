package com.gawalt.papis_akka_demo

import scala.collection.mutable
import scala.util.{Failure, Success, Try}


/**
 * This source file created by Brian Gawalt, 10/11/15.
 * It is subject to the MIT license bundled with this package in the file LICENSE.txt.
 * Copyright (c) Brian Gawalt, 2015
 */
object ClassifierClient {

  def getPrediction(review: String): Try[Boolean] = Try({
    val url = s"http://localhost:${ClassifierServer.SERVICE_PORT}/predict" +
      s"/${review.replaceAll("[^a-zA-Z]", "")}"
    io.Source.fromURL(url).getLines().mkString("\n").toDouble > 0
  })
  
  def issueUpdate(label: Boolean, review: String) : Try[String] = Try({
    val url = s"http://localhost:${ClassifierServer.SERVICE_PORT}/observe" +
      s"/$label/${review.replaceAll("[^a-zA-Z]", "")}"
    io.Source.fromURL(url).getLines().mkString("\n")
  })

  /**
   * This routine will attempt to use the predictive API listening on localhost:12345 to
   * predict whether an amazon movie summary is from a one-star or five-star review.
   * Produces a chart of accuracy over time.
   * @param args args(0): Five-star review-summary TSV filename
   *             args(1): One-star review-summary TSV filename
   */
  def main(args: Array[String]) {

    val fiveStarLines = io.Source.fromFile(args(0)).getLines()
    val oneStarLines = io.Source.fromFile(args(1)).getLines()

    val predictionErrors = mutable.Buffer.empty[Boolean]

    while (fiveStarLines.hasNext && oneStarLines.hasNext) {
      val fiveReview = Try(fiveStarLines.next().split("\t")(1).trim)
      fiveReview.flatMap(rev => getPrediction(rev)) match {
        case Success(pred) =>
          predictionErrors.append(!pred) // It's an error if model predicted "false"
        case Failure(f) =>
          println(s"Five-star predict: ${f.getClass.getSimpleName}:\t${f.getMessage}")
      }

      fiveReview.flatMap(rev => issueUpdate(true, rev)) match {
        case Success(status) => status
        case Failure(f) =>
          println(s"Five-star update: ${f.getClass.getSimpleName}:\t${f.getMessage}")
      }

      val oneReview = Try(oneStarLines.next().split("\t")(1).trim)
      oneReview.flatMap(line => getPrediction(line)) match {
        case Success(pred) =>
          predictionErrors.append(pred) // It's an error if model predicted "true"
        case Failure(f) =>
          println(s"One-star predict: ${f.getClass.getSimpleName}:\t${f.getMessage}")
      }

      oneReview.flatMap(line => issueUpdate(false, line)) match {
        case Success(status) => status
        case Failure(f) =>
          println(s"One-star update: ${f.getClass.getSimpleName}:\t${f.getMessage}")
      }
    }



    predictionErrors.grouped(500).map(_.count(b => b)).foreach(k => print(s"$k, "))
    println("")



  }

}

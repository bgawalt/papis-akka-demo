package com.gawalt.papis_akka_demo

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/**
 * Authored by bgawalt on 10/23/15.
 */
object PackLearnerClient {

  /**
   * Attempts to contact the text classification service running on localhost at the port
   * defined by the field ClassifierServer.SERVICE_PORT, and asks it to provide a score.
   * The more positive that score, the more likely it is that the document comes from the
   * positive class.
   * @param review The document whose positivity you'd like to predict, formatted in the
   *               underscores_for_spaces_and_only_letters style
   * @return If successful, a log-posterior ratio score indicating how probable it is the document
   *         belongs in the positive class.
   */
  def getPrediction(review: String): Try[Boolean] = Try({
    val url = s"http://localhost:${PackLearnerServer.SERVICE_PORT}/predict" +
      s"/${review.replaceAll("[^a-zA-Z]", "")}"
    io.Source.fromURL(url).getLines().mkString("\n").toDouble > 0
  })

  /**
   * Attempts to contact the text classification service running on localhost at the port
   * defined by the field ClassifierServer.SERVICE_PORT, and asks it to update its classification
   * model by taking the provided observation into account.
   * @param label true, if this movie review is a five-star review; else false
   * @param review The movie review text you're observing, formatted in the
   *               underscores_for_spaces_and_only_letters style
   * @return If successful, the classification model's status, post-update
   */
  def issueUpdate(label: Boolean, review: String) : Try[String] = Try({
    val url = s"http://localhost:${PackLearnerServer.SERVICE_PORT}/observe" +
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

    println("\nReview ID Bin\tAccuracy over Bin")
    predictionErrors.grouped(500) // Group errors into consecutive bins of 500
      .map(es => (es.count(b => b), es.length))  // Count num. of errors and total num. predictions
      .zipWithIndex          // Get the index of each bin
      .foreach({case ((numErrors, numTotal), binIdx) =>
      println(s"Reviews ${500*binIdx + 1} - ${500*binIdx + numTotal}:\t" + // Bin ranges
        s"${numErrors.toDouble/numTotal}")})  // Bin accuracy
    println("")

  }

}

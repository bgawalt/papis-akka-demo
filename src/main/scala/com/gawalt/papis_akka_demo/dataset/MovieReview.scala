package com.gawalt.papis_akka_demo.dataset

import scala.util.Try

/**
 * Authored by bgawalt on 10/11/15.
 */
case class MovieReview(score: Double, reviewer: String, movie: String, summary: String) {
  def cleanSummary: String = {
    summary.toLowerCase
      .replaceAll("[0-9]", "D")
      .replaceAll("""[!"#$%&'()*+,\-./:;<=>?@[\\\\\\]^_`{|}~]""", "P")
      .replaceAll("\\s+", "_")
  }
}

object MovieReview {

  def fromLines(lines: Iterator[String]): Try[MovieReview] = Try({
    val reviewLines = for (i <- 1 to 9) yield lines.next()
    require(reviewLines(0).startsWith("product/productId: "), "First line isn't product ID")
    require(reviewLines(1).startsWith("review/userId: "), "Second line isn't user ID")
    require(reviewLines(2).startsWith("review/profileName: "), "Third line isn't profile name")
    require(reviewLines(3).startsWith("review/helpfulness: "), "Fourth line isn't helpfulness")
    require(reviewLines(4).startsWith("review/score: "), "Fifth line isn't score")
    require(reviewLines(5).startsWith("review/time: "), "Sixth line isn't time")
    require(reviewLines(6).startsWith("review/summary: "), "Seventh line isn't summary")
    require(reviewLines(7).startsWith("review/text: "), "Eighth line isn't review")

    // 19 characters in "product/productId: "
    val movie = reviewLines(0).substring(19)
    // 15 characters in "review/userId: "
    val reviewer = reviewLines(1).substring(15)
    // 14 characters in "review/score: "
    val score = reviewLines(4).substring(14).toDouble
    // 16 characters in "review/summary: "
    val summary = reviewLines(6).substring(16)

    MovieReview(score = score, reviewer = reviewer, movie = movie, summary = summary)
  })

}

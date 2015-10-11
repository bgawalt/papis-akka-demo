package com.gawalt.papis_spray_demo.dataset

import java.io.PrintWriter
import java.nio.charset.CodingErrorAction

import scala.collection.mutable
import scala.util.{Random, Failure, Success}
import scala.io.Codec



/**
 * This source file created by Brian Gawalt, 10/11/15.
 * It is subject to the MIT license bundled with this package in the file LICENSE.txt.
 * Copyright (c) Brian Gawalt, 2015
 */
object MoviesDatasetCleaner {

  /**
   * Parse the SNAP Movie reivew dataset
   * https://snap.stanford.edu/data/web-Movies.html
   * Pull out the title and score of movies that got a 1.0 or 5.0 rating (the lowest and highest
   * possible score)
   * @param args args(0): Path to the movies.txt file
   *             args(1): Path to file where cleaned results should be written
   */
  def main(args: Array[String]) {
    val outfile = new PrintWriter(args(1))

    implicit val codec = Codec("UTF-8")
    codec.onMalformedInput(CodingErrorAction.REPLACE)
    codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

    val lines = io.Source.fromFile(args(0)).getLines()
    var loop = true
    val scoreCounter = new mutable.HashMap[Double, Int]()
    val failCounter = new mutable.HashMap[String, Int]()

    while (loop) {
      MovieReview.fromLines(lines) match {
        case Success(mr) =>
          val c = scoreCounter.getOrElse(mr.score, 0)
          scoreCounter(mr.score) = c + 1
          outfile.println(s"${mr.score}\t${mr.cleanSummary}")

        case Failure(f) =>
          val k = s"${f.getClass.getSimpleName}: ${f.getMessage}"
          val c = failCounter.getOrElse(k, 0)
          failCounter(k) = c + 1

          // If an error was encountered, iterate up to the next empty line in the review file
          var line = "dummy"
          while (lines.nonEmpty && line.trim.length > 0) {
            line = lines.next()
          }
      }

      loop = lines.nonEmpty
    }

    println("\nReview Score Histogram:")
    for (k <- scoreCounter.keys.toList.sorted) println(s"$k: ${scoreCounter(k)}")

    println("\nErrors encountered:")
    for (k <- failCounter.keys.toList.sorted) println(s"$k: ${failCounter(k)}")

    outfile.close()
  }
}

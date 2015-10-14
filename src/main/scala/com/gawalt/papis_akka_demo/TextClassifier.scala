package com.gawalt.papis_akka_demo

import scala.collection.mutable

/**
 * This source file created by Brian Gawalt, 8/20/15.
 * It is subject to the MIT license bundled with this package in the file LICENSE.txt.
 * Copyright (c) Brian Gawalt, 2015
 */

/**
 * Objects of this class are able to learn a Naive Bayes classification model (treating each
 * word token in a document as a Bernoulli random variable) by showing it examples of
 * document alongside a true/false label.  It can then perform predictions for new documents,
 * assigning a score reflecting how probable it is the document comes from the positive class.
 */
class TextClassifier {

  /** Number of positive documents seen so far */
  private var numPos: Int = 0
  /** Number of positive documents seen so far */
  private var numNeg: Int = 0

  /** Counts the number of times a word token appeared in any positive document seen */
  private var posCounts = mutable.HashMap.empty[String, Int]
  /** Counts the number of times a word token appeared in any negative document seen */
  private var negCounts = mutable.HashMap.empty[String, Int]
  /** Collection of all word tokens seen in any document */
  private var allTokens = mutable.HashSet.empty[String]

  /**
   * Update the model -- numPos/numNeg, posCounts/negCounts, and allTokens -- with the new
   * information included in the labelled document provided
   * @param doc A document and a true/false class label for that document. See the class
   *            com.gawalt.papis_akka_demo.LabelledDocument for more details.
   */
  def observe(doc: LabelledDocument) {
    if (doc.label) { // If it's a positive-class document...
      numPos += 1  // ... increment the number of positive documents seen,
      doc.tokens.foreach(token => { // ... increment the posCounts for every token in the document
        val count = posCounts.getOrElse(token, 0)
        posCounts(token) = count + 1
        allTokens.add(token) // ... and add every document token to the list of all tokens seen
      })
    } else { // ... or else take all those same steps on the negative class equivalents
      numNeg += 1
      TextClassifier.tokenize(doc.text).foreach(token => {
        val count = negCounts.getOrElse(token, 0)
        negCounts(token) = count + 1
        allTokens.add(token)
      })
    }
  }

  /**
   * Produce a log-posterior ratio for the provided document, given the current model values
   * and the document text
   */
  def predict(text: String): Double = {
    // log ratio of prior rates of positive and negative class documents
    val prior = math.log(numPos + 0.5) - math.log(numNeg + 0.5)

    val tokens = TextClassifier.tokenize(text).toSet

    val logNnMinusLogNp = math.log(numNeg + 1) - math.log(numPos + 1)
    // For every token we've seen, we're accumulating some amount of information --
    // what are the odds that we would see, or wouldn't see, a particular token given the
    // counts accumulated by the model so far?
    allTokens.foldLeft(0.0)({case (sum, t) =>
      // log P(token | pos class) - log P(token | neg class )
      // log [kp/np] - log [kn/nn] = log kp - log kn - log np + log nn
      val si = logNnMinusLogNp + (if (tokens.contains(t)) {
          // if the token does appear in this document, what are the general rates of appearances
          // in the documents seen so far?
          math.log(posCounts.getOrElse(t, 0) + 0.5) -
            math.log(negCounts.getOrElse(t, 0) + 0.5)
        } else {
          // if the token doesn't appear in this document, what are the general rates of *not*
          // appearing in the documents seen so far?
          math.log(numPos - posCounts.getOrElse(t, 0) + 0.5) -
            math.log(numNeg - negCounts.getOrElse(t, 0) + 0.5)
        })
      sum + si
    }) + prior
  }

  /**
   * Wipe out all the accumulated information held by the model to this point
   */
  def reset() {
    numPos = 0
    numNeg = 0
    posCounts = mutable.HashMap.empty[String, Int]
    negCounts = mutable.HashMap.empty[String, Int]
    allTokens = mutable.HashSet.empty[String]
  }

  def status: String = {
    s"""Num pos: $numPos,
       |Num neg: $numNeg,
       |Num tokens: ${allTokens.size}
     """.stripMargin
  }
}

object TextClassifier {
  /**
   * Turn any document into its constituent tokens. Split the text by any non-alphabetical character
   */
  def tokenize(text: String): List[String] =
    text.toLowerCase.replaceAll("[^a-zA-Z]", " ").split(" ").filter(_.length > 0).toList
}

/**
 * A container class for a document and its class label
 */
case class LabelledDocument(text: String, label: Boolean) {
  lazy val tokens = TextClassifier.tokenize(text).filter(_.length > 2).toSet
}
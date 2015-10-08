package com.gawalt.papis_spray_demo

import scala.collection.mutable

/**
 * This source file created by Brian Gawalt, 8/20/15.
 * It is subject to the MIT license bundled with this package in the file LICENSE.txt.
 * Copyright (c) Brian Gawalt, 2015
 */
class TwoCorpusTextCounter {

  private var numPos: Int = 0
  private var numNeg: Int = 0
  private var posCounts = mutable.HashMap.empty[String, Int]
  private var negCounts = mutable.HashMap.empty[String, Int]
  private var allTokens = mutable.HashSet.empty[String]
  private var posIntercept: Double = 0.0
  private var negIntercept: Double = 0.0

  def observe(doc: LabelledDocument) {
    if (doc.label) {
      numPos += 1
      TwoCorpusTextCounter.tokenize(doc.text).foreach(token => {
        val count = posCounts.getOrElse(token, 0)
        posCounts(token) = count + 1
        allTokens.add(token)
      })
    } else {
      numNeg += 1
      TwoCorpusTextCounter.tokenize(doc.text).foreach(token => {
        val count = negCounts.getOrElse(token, 0)
        negCounts(token) = count + 1
        allTokens.add(token)
      })
    }
  }

  def +(other: TwoCorpusTextCounter) {
    numPos += other.numPos
    numNeg += other.numNeg
    other.posCounts.iterator.foreach({case (k, v) => posCounts(k) = posCounts(k) + v})
    other.negCounts.iterator.foreach({case (k, v) => negCounts(k) = negCounts(k) + v})
    // TODO Recalculate intercepts
  }

  def predict(text: String): Double = {
    val prior = math.log(numPos) - math.log(numNeg)
    val tokens = TwoCorpusTextCounter.tokenize(text).toSet

    for (w <- allTokens) {
      if (tokens.contains(w)) {
        
      }
    }

    1.0
  }

  def reset() {
    numPos = 0
    numNeg = 0
    posCounts = mutable.HashMap.empty[String, Int]
    negCounts = mutable.HashMap.empty[String, Int]
    posIntercept = 0.0
    negIntercept = 0.0
  }
}

object TwoCorpusTextCounter {
  def tokenize(text: String): List[String] =
    text.toLowerCase.replaceAll("[^a-z]", " ").split(" ").filter(_.length > 0).toList
}

case class LabelledDocument(text: String, label: Boolean)
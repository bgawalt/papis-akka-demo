package com.gawalt.papis_akka_demo

import scala.collection.mutable

/**
 * This source file created by Brian Gawalt, 8/20/15.
 * It is subject to the MIT license bundled with this package in the file LICENSE.txt.
 * Copyright (c) Brian Gawalt, 2015
 */
class TextClassifier {

  private var numPos: Int = 0
  private var numNeg: Int = 0
  private var posCounts = mutable.HashMap.empty[String, Int]
  private var negCounts = mutable.HashMap.empty[String, Int]
  private var allTokens = mutable.HashSet.empty[String]

  def observe(doc: LabelledDocument) {
    if (doc.label) {
      numPos += 1
      doc.tokens.foreach(token => {
        val count = posCounts.getOrElse(token, 0)
        posCounts(token) = count + 1
        allTokens.add(token)
      })
    } else {
      numNeg += 1
      TextClassifier.tokenize(doc.text).foreach(token => {
        val count = negCounts.getOrElse(token, 0)
        negCounts(token) = count + 1
        allTokens.add(token)
      })
    }
  }

  def +(other: TextClassifier) {
    numPos += other.numPos
    numNeg += other.numNeg
    other.posCounts.iterator.foreach({case (k, v) => posCounts(k) = posCounts(k) + v})
    other.negCounts.iterator.foreach({case (k, v) => negCounts(k) = negCounts(k) + v})
  }

  def predict(text: String): Double = {
    if (numPos == 0 || numNeg == 0) {
      0.0
    }
    else {
      val prior = math.log(numPos) - math.log(numNeg)
      val tokens = TextClassifier.tokenize(text).toSet

      val logNnMinusLogNp = math.log(numNeg + 1) - math.log(numPos + 1)
      allTokens.foldLeft(0.0)({case (sum, t) =>
        // log P(word | class 1) - log P(word | class 0)
        // log [kp/np] - log [kn/nn] = log kp - log kn - log np + log nn
        val si = if (tokens.contains(t)) {
            logNnMinusLogNp + math.log(posCounts.getOrElse(t, 0) + 0.5) - 
              math.log(negCounts.getOrElse(t, 0) + 0.5)
        } else {
          logNnMinusLogNp + math.log(numPos - posCounts.getOrElse(t, 0) + 0.5) -
            math.log(numNeg - negCounts.getOrElse(t, 0) + 0.5)
        }
        sum + si
      }) + prior
    }
  }

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
  def tokenize(text: String): List[String] =
    text.toLowerCase.replaceAll("[^a-zA-Z]", " ").split(" ").filter(_.length > 0).toList
}

case class LabelledDocument(text: String, label: Boolean) {
  lazy val tokens = TextClassifier.tokenize(text).filter(_.length > 2).toSet
}
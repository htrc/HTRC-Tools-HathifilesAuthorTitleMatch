package org.hathitrust.htrc.tools.hathifilesauthortitlematch

import java.util.StringTokenizer
import org.slf4j.{Logger, LoggerFactory}

import java.text.Normalizer
import scala.util.matching.Regex

import org.hathitrust.htrc.tools.java.metrics.StringMetrics.levenshteinDistance

object Helper {
  @transient lazy val logger: Logger = LoggerFactory.getLogger(Main.appName)
  val cleanAuthorParensOrQuotesRegex: Regex = """“.+”|".+"|\(.+\)""".r
  val cleanAuthorTimePeriodSuffixRegex: Regex = """,?\s*(?:\b(?:b\.?|d\.?)\s+)?(?:\b(?:ca\.?|circa)\s+)?-?(?:\d{2,4}|active|approximately)[^,]*,?$""".r
  val cleanAuthorEndingRegex: Regex = """[,. ]+$""".r
  val normWhitespacesRegex: Regex = """\s{2,}""".r
  val squareBracketsRegex: Regex = """[\[\]]""".r
  val removePunctRegex: Regex = """\p{P}+""".r
  val accentsRegex: Regex = """\p{M}+""".r

  def genericSanitizeAuthorName(s: String): String = {
    var cleaned = cleanAuthorParensOrQuotesRegex.replaceAllIn(s, "")
    cleaned = normWhitespacesRegex.replaceAllIn(cleaned, " ")
    cleaned = cleaned.trim
    cleaned
  }

  def sanitizeHathiAuthorName(s: String): String = {
    var cleaned = cleanAuthorTimePeriodSuffixRegex.replaceFirstIn(s, "")
    cleaned = cleaned.replace("[from old catalog]", "")
    cleaned = cleanAuthorEndingRegex.replaceFirstIn(cleaned, "")
    cleaned = squareBracketsRegex.replaceAllIn(cleaned, "")
    cleaned = genericSanitizeAuthorName(cleaned)
    cleaned
  }

  def convertNameFirstLast(s: String): String = {
    val authorParts = tokenize(s, ",").take(2).map(_.trim).filterNot(_.isEmpty).toList
    val convertedName = authorParts match {
      case last :: first :: Nil if first.endsWith("'") => s"$first$last"
      case last :: first :: Nil => s"$first $last"
      case fullName :: Nil => fullName
      case _ => ""
    }

    convertedName
  }

  def tokenize(s: String, delims: String = " "): Iterator[String] = {
    val st = new StringTokenizer(s, delims)
    Iterator.continually(st).takeWhile(_.hasMoreTokens).map(_.nextToken())
  }

  def deaccent(s: String): String = accentsRegex.replaceAllIn(Normalizer.normalize(s, Normalizer.Form.NFKD), "")

  // method specific for SCWARED project
  def splitAuthors(s: String): Iterator[String] =
    s.split("""\band\b""").iterator.flatMap(tokenize(_, ";")).map(_.trim).filterNot(_.isEmpty)

  def normalizeAuthor(s: String): String = {
    var norm = s.replace("-", " ")
    norm = removePunctRegex.replaceAllIn(norm, "")
    norm = deaccent(norm)
    norm = normWhitespacesRegex.replaceAllIn(norm, " ")
    norm = norm.toLowerCase().trim
    norm
  }

  def isAuthorMatch(a1: String, a2: String, tryFuzzy: Boolean = false, minSimScore: Float = 0.7f): Boolean = {
    if (a1 equals a2)
      return true

    // tokenize and drop initials from names
    val a1Tokens = tokenize(a1).filter(_.length > 1).toList
    val a2Tokens = tokenize(a2).filter(_.length > 1).toList

    val a1TokenSet = a1Tokens.toSet
    val a2TokenSet = a2Tokens.toSet
    val intersect = a1TokenSet intersect a2TokenSet
    if (intersect.nonEmpty && intersect.size == Math.min(a1TokenSet.size, a2TokenSet.size))
      return true

    if (tryFuzzy) {
      val a1Fuzed = a1Tokens.mkString
      val a2Fuzed = a2Tokens.mkString
      val similarityScore = 1 - levenshteinDistance(a1Fuzed, a2Fuzed).toFloat / Math.max(a1Fuzed.length, a2Fuzed.length)
      if (similarityScore >= minSimScore)
        return true
    }

    false
  }

  def isTitleMatch(t1: String, t2: String, minSimScore: Float = 0.9f): Boolean = {
    val l1 = t1.length
    val l2 = t2.length

    val (shorter, longer) = if (l2 > l1) t1 -> t2 else t2 -> t1
    if (raw"""\b\Q$shorter\E\b""".r.findFirstIn(longer).isDefined)
      return true

    val (t1Norm, t2Norm) = if (l2 > l1) t1 -> t2.take(l1).stripSuffix(" ") else t1.take(l2).stripSuffix(" ") -> t2
    val similarityScore = 1 - levenshteinDistance(t1Norm, t2Norm).toFloat / Math.max(t1Norm.length, t2Norm.length)

    similarityScore >= minSimScore
  }

  def cleanTitle(title: String): Option[String] = {
    Option(title)
      .map(_.trim.toLowerCase)
      .collect {
        case t if t.nonEmpty =>
          deaccent(t).replace("-", " ").replaceAll("""\p{P}""", "").replaceAll("""\s{2,}""", " ").trim
      }
  }
}

package org.hathitrust.htrc.tools.hathifilesauthortitlematch

import java.io.File
import com.gilt.gfc.time.Timer
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.types.{IntegerType, StringType, StructField, StructType, TimestampType}
import org.apache.spark.sql.{Dataset, SparkSession}
import org.hathitrust.htrc.tools.hathifilesauthortitlematch.Helper._

/**
  * @author Boris Capitanu
  */

object Main {
  val appName = "hathifiles-authortitle-match"

  def stopSparkAndExit(sc: SparkContext, exitCode: Int = 0): Unit = {
    try {
      sc.stop()
    }
    finally {
      System.exit(exitCode)
    }
  }

  def main(args: Array[String]): Unit = {
    val conf = new Conf(args.to(Seq))
    val tryFuzzyAuthorMatch = conf.tryFuzzyAuthors()
    val minAuthorSim = conf.minAuthorSimilarity()
    val minTitleSim = conf.minTitleSimilarity()
    val numCores = conf.numCores.map(_.toString).getOrElse("*")
    val outputPath = conf.outputPath().toString

    // set up logging destination
    conf.sparkLog.toOption match {
      case Some(logFile) => System.setProperty("spark.logFile", logFile)
      case None =>
    }
    System.setProperty("logLevel", conf.logLevel().toUpperCase)

    val sparkConf = new SparkConf()
    sparkConf.setAppName(appName)
    sparkConf.setIfMissing("spark.master", s"local[$numCores]")
    val sparkMaster = sparkConf.get("spark.master")

    val spark = SparkSession.builder()
      .config(sparkConf)
      .getOrCreate()

    import spark.implicits._
    import org.apache.spark.sql.functions._

    val sc = spark.sparkContext

    def readHathifiles(file: File): Dataset[HathiVolume] = {
      val schema = StructType(Array(
        StructField("htid", StringType, nullable = false),
        StructField("access", StringType, nullable = false),
        StructField("rights", StringType, nullable = false),
        StructField("ht_bib_key", StringType, nullable = false),
        StructField("description", StringType, nullable = false),
        StructField("source", StringType, nullable = false),
        StructField("source_bib_num", StringType, nullable = false),
        StructField("oclc_num", StringType, nullable = false),
        StructField("isbn", StringType, nullable = false),
        StructField("issn", StringType, nullable = false),
        StructField("lccn", StringType, nullable = false),
        StructField("title", StringType, nullable = false),
        StructField("imprint", StringType, nullable = false),
        StructField("rights_reason_code", StringType, nullable = false),
        StructField("rights_timestamp", TimestampType, nullable = false),
        StructField("us_gov_doc_flag", IntegerType, nullable = false),
        StructField("rights_date_used", StringType, nullable = false),
        StructField("pub_place", StringType, nullable = false),
        StructField("lang", StringType, nullable = false),
        StructField("bib_fmt", StringType, nullable = false),
        StructField("collection_code", StringType, nullable = false),
        StructField("content_provider_code", StringType, nullable = false),
        StructField("responsible_entity_code", StringType, nullable = false),
        StructField("digitization_agent_code", StringType, nullable = false),
        StructField("access_profile_code", StringType, nullable = false),
        StructField("author", StringType, nullable = false)
      ))

      spark
        .read
        .option("header", "true")
        .option("charset", "UTF-8")
        .option("delimiter", "\t")
        .schema(schema)
        .option("timestampFormat", "yyyy-MM-dd HH:mm:ss")
        .csv(file.getCanonicalPath)
        .withColumn("access", when($"access" === "allow", true).otherwise(false))
        .withColumn("oclc_num", split($"oclc_num", ","))
        .withColumn("isbn", split($"isbn", ","))
        .withColumn("issn", split($"issn", ","))
        .withColumn("lccn", split($"lccn", ","))
        .as[HathiVolume]
    }

    def readInput(file: File): Array[(String, String)] = {
      spark
        .read
        .option("header", "true")
        .option("charset", "UTF-8")
        .option("quote", "\"")
        .option("escape", "\"")
        .csv(file.getCanonicalPath)
        .map(row => (row.getAs[String](1), row.getAs[String](0)))
        .collect()
    }

    try {
      logger.info("Starting...")
      logger.info(s"Spark master: $sparkMaster")

      val t0 = Timer.nanoClock()

      if (tryFuzzyAuthorMatch)
        logger.info("Enabling fuzzy matching for author names with threshold {} (disable with --no-fuzzy-author-match)", minAuthorSim)
      else
        logger.info("Disabling fuzzy matching for author names (enable with --fuzzy-author-match)")

      logger.info("Enabling fuzzy matching for volume titles with threshold {}", minTitleSim)

      val parseInputAuthors = splitAuthors(_: String)
        .map(genericSanitizeAuthorName _ andThen convertNameFirstLast andThen normalizeAuthor)
        .toList

      val parseHathiAuthor = sanitizeHathiAuthorName _ andThen convertNameFirstLast andThen normalizeAuthor

      val hathiFiles = readHathifiles(conf.hathiFiles())
      val queriesBcast = sc.broadcast {
        readInput(conf.inputPath())
          .map { case (t, a) =>
            (t, cleanTitle(t), a, Option(a).filterNot(_.isEmpty).map(parseInputAuthors).filterNot(_.isEmpty))
          }
      }

      val matches = hathiFiles.rdd
        .flatMap { vol =>
          val queries = queriesBcast.value
          val volTitleCleanOpt = cleanTitle(vol.title)
          val volAuthorCleanOpt = Option(vol.author).filterNot(_.isEmpty).map(parseHathiAuthor).filterNot(_.isEmpty)

          queries
            .iterator
            .map { case (title, titleCleanOpt, author, authorCleanOpt) =>
              val titleMatch = volTitleCleanOpt
                .zip(titleCleanOpt)
                .exists { case (vt, t) => isTitleMatch(vt, t, minSimScore = minTitleSim) }
              val authorMatch = volAuthorCleanOpt.zip(authorCleanOpt) match {
                case Some((a1, a2)) => a2.exists(isAuthorMatch(a1, _, tryFuzzyAuthorMatch, minSimScore = minAuthorSim))
                case None => (volAuthorCleanOpt.isEmpty && authorCleanOpt.isEmpty) || authorCleanOpt.isEmpty
              }

              // logger.debug(s"\nvolTitle: ${vol.title}  title: $title\nvolAuthor: ${vol.author}  author: $author\nvolAuthorParts: ${volAuthorPartsOpt.map(_.mkString(" | "))}  authorParts: ${authorPartsOpt.map(_.mkString(" | "))}\ntitleMatch: $titleMatch    authorMatch: $authorMatch\n")

              (vol, title, titleMatch, author, authorMatch)
            }
            .filter(t => t._3 && t._5)  // title and author match
        }
        .collect {
          case (vol, title, _, author, _) =>
            logger.info(s"\nvolTitle: ${vol.title}  title: $title\nvolAuthor: ${vol.author}  author: $author\n")
            (vol, title, author)
        }

      conf.outputPath().mkdirs()

      matches
        .map { case (vol, title, author) => (vol.htid, title, vol.title, author, vol.author) }
        .toDS()
        .write
        .option("header", "false")
        .option("sep", "\t")
        .option("encoding", "UTF-8")
        .option("nullValue", null)
        .csv(outputPath + "/matches")

      val t1 = Timer.nanoClock()
      val elapsed = t1 - t0
      logger.info(f"All done in ${Timer.pretty(elapsed)}")
    }
    catch {
      case e: Throwable =>
        logger.error(s"Uncaught exception", e)
        stopSparkAndExit(sc, exitCode = 500)
    }

    stopSparkAndExit(sc)
  }

}

package org.hathitrust.htrc.tools.hathifilesauthortitlematch

import java.io.File

import org.rogach.scallop.{Scallop, ScallopConf, ScallopHelpFormatter, ScallopOption, SimpleOption}

/**
  * Command line argument configuration
  *
  * @param arguments The cmd line args
  */
class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
  appendDefaultToDescription = true
  helpFormatter = new ScallopHelpFormatter {
    override def getOptionsHelp(s: Scallop): String = {
      super.getOptionsHelp(s.copy(opts = s.opts.map {
        case opt: SimpleOption if !opt.required =>
          opt.copy(descr = "(Optional) " + opt.descr)
        case other => other
      }))
    }
  }

  val (appTitle, appVersion, appVendor) = {
    val p = getClass.getPackage
    val nameOpt = Option(p).flatMap(p => Option(p.getImplementationTitle))
    val versionOpt = Option(p).flatMap(p => Option(p.getImplementationVersion))
    val vendorOpt = Option(p).flatMap(p => Option(p.getImplementationVendor))
    (nameOpt, versionOpt, vendorOpt)
  }

  version(appTitle.flatMap(
    name => appVersion.flatMap(
      version => appVendor.map(
        vendor => s"$name $version\n$vendor"))).getOrElse(Main.appName))

  val sparkLog: ScallopOption[String] = opt[String]("spark-log",
    descr = "Where to write logging output from Spark to",
    argName = "FILE",
    noshort = true
  )

  val logLevel: ScallopOption[String] = opt[String]("log-level",
    descr = "The application log level; one of INFO, DEBUG, OFF",
    argName = "LEVEL",
    default = Some("INFO"),
    validate = level => Set("INFO", "DEBUG", "OFF").contains(level.toUpperCase)
  )

  val numCores: ScallopOption[Int] = opt[Int]("num-cores",
    descr = "The number of CPU cores to use (if not specified, uses all available cores)",
    short = 'c',
    argName = "N",
    validate = 0 <
  )

  val tryFuzzyAuthors: ScallopOption[Boolean] = toggle("fuzzy-author-match",
    descrNo = "Perform subset matching only",
    descrYes = "Perform fuzzy matching if subset matching fails",
    noshort = true,
    default = Some(false),
    prefix = "no-"
  )

  val minAuthorSimilarity: ScallopOption[Float] = opt[Float]("min-author-sim",
    descr = "Minimum Levenshtein similarity score needed to determine that two author names match",
    noshort = true,
    default = Some(0.7f)
  )

  val minTitleSimilarity: ScallopOption[Float] = opt[Float]("min-title-sim",
    descr = "Minimum Levenshtein similarity score needed to determine that two volume titles match",
    noshort = true,
    default = Some(0.7f)
  )

  val outputPath: ScallopOption[File] = opt[File]("output",
    descr = "Write the output to DIR (should not exist, or be empty)",
    required = true,
    argName = "DIR"
  )

  val inputPath: ScallopOption[File] = opt[File]("input",
    descr = "The path to the CSV file containing the author/title information",
    required = true,
    argName = "FILE"
  )

  val hathiFiles: ScallopOption[File] = trailArg[File]("hathifile",
    descr = "The file containing the HathiFiles data",
    required = true
  )

  validateFileExists(inputPath)
  validateFileExists(hathiFiles)
  validateFileDoesNotExist(outputPath)
  verify()
}
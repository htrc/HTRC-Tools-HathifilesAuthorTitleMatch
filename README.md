[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/htrc/HTRC-Tools-HathifilesAuthorTitleMatch/ci.yml?branch=develop)](https://github.com/htrc/HTRC-Tools-HathifilesAuthorTitleMatch/actions/workflows/ci.yml)
[![codecov](https://codecov.io/github/htrc/HTRC-Tools-HathifilesAuthorTitleMatch/branch/develop/graph/badge.svg?token=5K1J2AIATG)](https://codecov.io/github/htrc/HTRC-Tools-HathifilesAuthorTitleMatch)
[![GitHub release (latest SemVer including pre-releases)](https://img.shields.io/github/v/release/htrc/HTRC-Tools-HathifilesAuthorTitleMatch?include_prereleases&sort=semver)](https://github.com/htrc/HTRC-Tools-HathifilesAuthorTitleMatch/releases/latest)

# HTRC-Tools-HathifilesAuthorTitleMatch
This tool can be used to find HathiTrust volumes with specific author(s) and title;
it takes as input a no-header CSV where the first column contains the author information
and the second column contains the title. Author names are supported both in the `<First> [<MI>|<Middle>] <Last>` and
`<Last>, <First> [<MI>|<Middle>]` formats. 
*Note:* The author information is not required and can be left blank, in which case the
searching is done by title only.

## Matching authors and titles
The tool does its best to find matches for authors and titles. For authors, the names are 
first normalized to `<First> <Last>` style, removing accents and other symbols and lowercasing 
the result. The same normalization steps are applied to the authors in the HathiFiles. 

After normalization, the actual matching is done by tokenizing the full name, removing initials, and
performing subset matching (the same processing is done for the author in the Hathifiles, before matching).
Optionally (via `--fuzzy-author-match`), if the above matching is unsuccessful,
fuzzy author matching is performed by computing the Levenshtein distance between the two full names and 
comparing the score with a user-configurable minimum threshold (via `--min-author-sim`).

Title matching is performed by first normalizing the two titles (normalizing whitespaces, removing punctuation, 
lowercasing), and then by comparing the computed Levenshtein distance between the
two normalized titles with the minimum threshold set by the user (via `--min-title-sim`) or its default if 
the user does not specify one.

## Output
The tool saves the output containing the match results in a folder specified by the user
(via `--output`). Multiple no-header CSV files are created (an artifact of the parallel/distributed) processing
performed by Apache Spark, where each CSV file contains a subset of the matches. To get all matches,
the user should concatenate all the `part-*.csv` files. Each CSV files conforms to the following header:
```text
htid,title,ht_title,author,ht_author
```
where `author` and `title` are the ones supplied by the user in the input CSV, and 
`ht_author` and `ht_title` are the matches identified in the Hathifiles. Finally, `htid` contains
the HathiTrust volume identifier associated with the matched volume.

# Build
`sbt clean stage` - generates the unpacked, runnable application in the `target/universal/stage/` folder.  
`sbt clean universal:packageBin` - generates an application ZIP file

# Usage
*Note:* Must use one of the supported JVMs for Apache Spark (at this time Java 8 through Java 11 are supported)
```text
hathifiles-authortitle-match <version>
HathiTrust Research Center
      --fuzzy-author-match      Perform fuzzy matching if substring matching
                                fails
      --no-fuzzy-author-match   Perform subset matching only
  -i, --input  <FILE>           The path to the CSV file containing the
                                author/title information
  -l, --log-level  <LEVEL>      (Optional) The application log level; one of
                                INFO, DEBUG, OFF (default = INFO)
      --min-author-sim  <arg>   (Optional) Minimum Levenshtein similarity score
                                needed to determine that two author names match
                                (default = 0.7)
      --min-title-sim  <arg>    (Optional) Minimum Levenshtein similarity score
                                needed to determine that two volume titles match
                                (default = 0.7)
  -c, --num-cores  <N>          (Optional) The number of CPU cores to use (if
                                not specified, uses all available cores)
  -o, --output  <DIR>           Write the output to DIR (should not exist, or be
                                empty)
      --spark-log  <FILE>       (Optional) Where to write logging output from
                                Spark to
  -h, --help                    Show help message
  -v, --version                 Show version of this program

 trailing arguments:
  hathifile (required)   The file containing the HathiFiles data
```
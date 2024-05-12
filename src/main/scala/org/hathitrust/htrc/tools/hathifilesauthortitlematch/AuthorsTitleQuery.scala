package org.hathitrust.htrc.tools.hathifilesauthortitlematch

case class AuthorsTitleQuery(uuid: String, title: Option[String], authors: Option[List[String]])

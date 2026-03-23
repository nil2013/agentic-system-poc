// Stage 1: 民法 XML から条文抽出するユーティリティ
// Run: scala-cli run stages/stage1/extract_articles.scala

//> using scala 3.6
//> using dep org.scala-lang.modules::scala-xml:2.3.0

import scala.xml.*

@main def extractArticles(): Unit = {
  val root = XML.loadFile("stages/stage1/civil_code.xml")

  for (num <- List("1", "5", "709")) {
    val article = (root \\ "Article").find(a => (a \ "@Num").text == num)
    article match {
      case Some(a) =>
        println(s"=== Article $num ===")
        println(a.toString.take(1500))
        println()
      case None =>
        println(s"Article $num not found")
    }
  }
}

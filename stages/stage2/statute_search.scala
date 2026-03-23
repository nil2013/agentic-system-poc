//> using scala 3.6
//> using dep com.softwaremill.sttp.client4::core:4.0.19
//> using dep org.scala-lang.modules::scala-xml:2.3.0

import sttp.client4.*
import scala.xml.*

object StatuteSearch {

  val EgovBase = "https://laws.e-gov.go.jp/api/1"

  val KnownLaws: Map[String, String] = Map(
    "民法" -> "129AC0000000089",
    "刑法" -> "140AC0000000045",
    "憲法" -> "321CONSTITUTION",
    "行政手続法" -> "405AC0000000088",
    "行政事件訴訟法" -> "337AC0000000139",
    "民事訴訟法" -> "408AC0000000109",
  )

  def searchStatute(lawName: String, articleNumber: String): String = {
    KnownLaws.get(lawName) match {
      case None =>
        s"エラー: '$lawName' は登録されていません。利用可能: ${KnownLaws.keys.mkString(", ")}"
      case Some(lawId) =>
        val backend = DefaultSyncBackend()
        val resp = basicRequest
          .get(uri"$EgovBase/lawdata/$lawId")
          .response(asString)
          .readTimeout(scala.concurrent.duration.Duration(30, "s"))
          .send(backend)
        backend.close()

        resp.body match {
          case Left(err) => s"エラー: API呼び出し失敗: $err"
          case Right(xmlStr) =>
            val root = XML.loadString(xmlStr)
            val articles = (root \\ "Article").filter(a => (a \ "@Num").text == articleNumber)
            articles.headOption match {
              case None => s"エラー: ${lawName}第${articleNumber}条が見つかりません。"
              case Some(article) =>
                val caption = (article \ "ArticleCaption").text.trim
                val title = (article \ "ArticleTitle").text.trim
                val sentences = (article \\ "Sentence").map(_.text.trim).filter(_.nonEmpty)
                (List(caption, title).filter(_.nonEmpty) ++ sentences).mkString("\n")
            }
        }
    }
  }
}

@main def testTool(): Unit = {
  println("=== 民法709条 ===")
  println(StatuteSearch.searchStatute("民法", "709"))
  println()
  println("=== 刑法199条 ===")
  println(StatuteSearch.searchStatute("刑法", "199"))
  println()
  println("=== エラーケース ===")
  println(StatuteSearch.searchStatute("不存在", "1"))
}

package tools

import sttp.client4.*
import scala.xml.*

object LawListSearch {

  private var cache: Option[Seq[(String, String, String)]] = None // (name, id, number)

  private def loadLawList(): Seq[(String, String, String)] = {
    cache.getOrElse {
      val backend = DefaultSyncBackend()
      try {
        val resp = basicRequest
          .get(uri"https://laws.e-gov.go.jp/api/1/lawlists/2")
          .response(asString)
          .readTimeout(scala.concurrent.duration.Duration(60, "s"))
          .send(backend)
        val root = XML.loadString(resp.body.getOrElse("<DataRoot/>"))
        val laws = (root \\ "LawNameListInfo").map { info =>
          ((info \ "LawName").text, (info \ "LawId").text, (info \ "LawNo").text)
        }
        cache = Some(laws)
        laws
      } finally {
        backend.close()
      }
    }
  }

  def findLawByKeyword(keyword: String): String = {
    val matches = loadLawList().filter(_._1.contains(keyword)).take(10)
    if (matches.isEmpty) {
      s"'$keyword' を含む法令は見つかりませんでした。"
    } else {
      val lines = matches.map { case (name, _, number) => s"- $name（$number）" }
      s"'$keyword' を含む法令 (${matches.size}件):\n${lines.mkString("\n")}"
    }
  }
}

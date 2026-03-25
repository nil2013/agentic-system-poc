package tools.egov.v1

import tools.egov.*
import sttp.client4.*
import sttp.model.Uri
import scala.xml.*
import java.net.URLEncoder

/** e-Gov 法令 API V1 クライアント。
  *
  * `/api/1` エンドポイントに対して HTTP リクエストを送信し、XML レスポンスをパースする。
  *
  * == 設計判断 ==
  *  - '''同期・ブロッキング設計''': LLM 推論がボトルネックであり、e-Gov API の
  *    1-2 秒のレイテンシは支配的でない。
  *  - '''リクエストごとに新規 `DefaultSyncBackend`''': PoC の使用頻度では問題にならない。
  *
  * == `/articles` の URL 形式 ==
  * セミコロン区切りの matrix parameter（JAX-RS 形式）を使用。
  * sttp の `uri""` 補間子はセミコロンを正しく扱えないため、`Uri.unsafeParse` で変換。
  *
  * @see `docs/egov-api/v1/hourei-api-v1-rest-spec.md` API 仕様の詳細
  */
class V1Client extends EGovLawApi {

  val BaseUrl = "https://laws.e-gov.go.jp/api/1"

  def fetchLawList(category: Int = 2): Either[String, Seq[LawInfo]] = {
    val backend = DefaultSyncBackend()
    try {
      val resp = basicRequest
        .get(uri"$BaseUrl/lawlists/$category")
        .response(asString)
        .readTimeout(scala.concurrent.duration.Duration(60, "s"))
        .send(backend)

      resp.body match {
        case Left(err) => Left(s"HTTP エラー: $err")
        case Right(xmlStr) =>
          parseResult(xmlStr).map { root =>
            val applData = root \ "ApplData"
            (applData \ "LawNameListInfo").map { info =>
              LawInfo(
                lawId = (info \ "LawId").text.trim,
                lawName = (info \ "LawName").text.trim,
                lawNo = (info \ "LawNo").text.trim
              )
            }
          }
      }
    } finally {
      backend.close()
    }
  }

  def fetchArticle(lawId: String, article: String): Either[String, Elem] = {
    val encodedArticle = URLEncoder.encode(article, "UTF-8")
    val rawUrl = s"$BaseUrl/articles;lawId=$lawId;article=$encodedArticle"
    fetchXml(rawUrl)
  }

  def fetchArticleWithParagraph(lawId: String, article: String, paragraph: String): Either[String, Elem] = {
    val encodedArticle = URLEncoder.encode(article, "UTF-8")
    val encodedParagraph = URLEncoder.encode(paragraph, "UTF-8")
    val rawUrl = s"$BaseUrl/articles;lawId=$lawId;article=$encodedArticle;paragraph=$encodedParagraph"
    fetchXml(rawUrl)
  }

  def fetchLawData(lawId: String): Either[String, Elem] = {
    fetchXml(s"$BaseUrl/lawdata/$lawId")
  }

  def capabilities: Set[Capability] = Set.empty

  // --- internal ---

  private def fetchXml(rawUrl: String): Either[String, Elem] = {
    val backend = DefaultSyncBackend()
    try {
      val uri = Uri.unsafeParse(rawUrl)
      val resp = basicRequest
        .get(uri)
        .response(asString)
        .readTimeout(scala.concurrent.duration.Duration(30, "s"))
        .send(backend)

      resp.body match {
        case Left(err) => Left(s"HTTP エラー: $err")
        case Right(xmlStr) => parseResult(xmlStr)
      }
    } finally {
      backend.close()
    }
  }

  private def parseResult(xmlStr: String): Either[String, Elem] = {
    try {
      val root = XML.loadString(xmlStr)
      val code = (root \ "Result" \ "Code").text.trim
      val message = (root \ "Result" \ "Message").text.trim

      code match {
        case "0" => Right(root)
        case "1" =>
          val msg = if (message.nonEmpty) message else "該当するデータが見つかりません"
          Left(s"APIエラー: $msg")
        case "2" => Left(s"複数候補: $message")
        case other => Left(s"不明なコード($other): $message")
      }
    } catch {
      case e: Exception => Left(s"XML パースエラー: ${e.getMessage}")
    }
  }
}

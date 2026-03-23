package tools.egov

import sttp.client4.*
import sttp.model.Uri
import scala.xml.*
import java.net.URLEncoder

/** e-Gov 法令 API V1 への HTTP アクセス層 */
object EGovApiClient {

  val BaseUrl = "https://laws.e-gov.go.jp/api/1"

  /** 法令一覧取得: GET /api/1/lawlists/{category} */
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

  /** 条文取得: GET /api/1/articles;lawId={lawId};article={article} */
  def fetchArticle(lawId: String, article: String): Either[String, Elem] = {
    val encodedArticle = URLEncoder.encode(article, "UTF-8")
    val rawUrl = s"$BaseUrl/articles;lawId=$lawId;article=$encodedArticle"
    fetchXml(rawUrl)
  }

  /** 条文+項取得: GET /api/1/articles;lawId={lawId};article={article};paragraph={paragraph} */
  def fetchArticleWithParagraph(lawId: String, article: String, paragraph: String): Either[String, Elem] = {
    val encodedArticle = URLEncoder.encode(article, "UTF-8")
    val encodedParagraph = URLEncoder.encode(paragraph, "UTF-8")
    val rawUrl = s"$BaseUrl/articles;lawId=$lawId;article=$encodedArticle;paragraph=$encodedParagraph"
    fetchXml(rawUrl)
  }

  /** 法令全文取得: GET /api/1/lawdata/{lawId} */
  def fetchLawData(lawId: String): Either[String, Elem] = {
    fetchXml(s"$BaseUrl/lawdata/$lawId")
  }

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

  /** 共通 XML パース: Result/Code チェック */
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

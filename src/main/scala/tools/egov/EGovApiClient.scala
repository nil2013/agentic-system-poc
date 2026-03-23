package tools.egov

import sttp.client4.*
import sttp.model.Uri
import scala.xml.*
import java.net.URLEncoder

/** e-Gov 法令 API V1 への HTTP アクセス層。
  *
  * 本プロジェクトで外部ネットワークアクセスを行う唯一のコンポーネント（LLM API を除く）。
  * すべてのレスポンスは XML 形式で返却される。
  *
  * == 設計判断 ==
  *  - '''同期・ブロッキング設計''': LLM 推論（500ms〜数秒）がボトルネックであり、
  *    e-Gov API の 1-2 秒のレイテンシは全体に対して支配的でない。非同期化の利点が薄い。
  *  - '''リクエストごとに新規 `DefaultSyncBackend`''': 接続プーリングなし。
  *    PoC の使用頻度では問題にならない。高頻度アクセスが必要な場合はバックエンドを共有に変更する。
  *
  * == `/articles` エンドポイントの URL 形式 ==
  * このエンドポイントは標準的なクエリパラメータ（`?key=value`）ではなく、
  * '''セミコロン区切りの matrix parameter''' を使用する（JAX-RS 形式）:
  * {{{
  * GET /api/1/articles;lawId=129AC0000000089;article=%E7%AC%AC%E4%B8%83%E7%99%BE%E4%B9%9D%E6%9D%A1
  * }}}
  * sttp の `uri""` 補間子はセミコロンをパス区切りとして解釈しないため、
  * 完全な URL 文字列を構築して `Uri.unsafeParse` で変換している。
  *
  * == e-Gov API レスポンスの共通構造 ==
  * {{{
  * <DataRoot>
  *   <Result>
  *     <Code>0</Code>        <!-- 0: 成功, 1: エラー/0件, 2: 複数候補 -->
  *     <Message>...</Message>
  *   </Result>
  *   <ApplData>...</ApplData> <!-- エンドポイント固有のデータ -->
  * </DataRoot>
  * }}}
  *
  * @see [[LawRepository]] 法令一覧のキャッシュと検索（`fetchLawList` を使用）
  * @see [[ArticleRepository]] 条文取得とパース（`fetchArticle` を使用）
  * @see `docs/egov-api/hourei-api-v1-rest-spec.md` API 仕様の詳細
  */
object EGovApiClient {

  /** e-Gov 法令 API V1 のベース URL */
  val BaseUrl = "https://laws.e-gov.go.jp/api/1"

  /** 法令一覧を取得する。
    *
    * @param category 法令種別コード。
    *   - 1: 全法令（約9000件）
    *   - 2: 憲法・法律（約2000件、'''デフォルト'''）
    *   - 3: 政令・勅令
    *   - 4: 府省令・規則
    *
    *   デフォルト 2 は意図的: 法令名検索のユースケースでは法律レベルで十分であり、
    *   政令以下を含めるとレスポンスが大きくなる。
    * @return 成功時は `Right(法令情報のリスト)`、失敗時は `Left(エラーメッセージ)`
    */
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

  /** 条文を取得する（`/articles` エンドポイント）。
    *
    * @param lawId   法令ID（半角英数）
    * @param article 条番号（漢数字全角、例: `"第七百九条"`）。
    *                [[ArticleNumberConverter.toKanjiArticle]] で変換済みの値を渡すこと。
    *                URL エンコードは本メソッド内で行う。
    * @return 成功時は `Right(XMLルート要素)`、失敗時は `Left(エラーメッセージ)`。
    *         `ApplData/LawContents` に条文 XML が含まれる。
    */
  def fetchArticle(lawId: String, article: String): Either[String, Elem] = {
    val encodedArticle = URLEncoder.encode(article, "UTF-8")
    val rawUrl = s"$BaseUrl/articles;lawId=$lawId;article=$encodedArticle"
    fetchXml(rawUrl)
  }

  /** 条文の特定の項を取得する（`/articles` エンドポイント）。
    *
    * @param lawId     法令ID
    * @param article   条番号（漢数字全角）
    * @param paragraph 項番号（漢数字全角、例: `"第一項"`）
    */
  def fetchArticleWithParagraph(lawId: String, article: String, paragraph: String): Either[String, Elem] = {
    val encodedArticle = URLEncoder.encode(article, "UTF-8")
    val encodedParagraph = URLEncoder.encode(paragraph, "UTF-8")
    val rawUrl = s"$BaseUrl/articles;lawId=$lawId;article=$encodedArticle;paragraph=$encodedParagraph"
    fetchXml(rawUrl)
  }

  /** 法令全文を取得する（`/lawdata` エンドポイント）。
    *
    * 現在は使用していない（条文単位の `/articles` で十分）。
    * 法令全体のテキスト分析が必要になった場合に使用する。
    * '''注意''': レスポンスが大きい（民法で約 1.7MB）。
    */
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

  /** e-Gov API レスポンスの共通 XML パース。`Result/Code` をチェックして成否を判定する。
    *
    * Result Code の意味:
    *  - `0`: 正常終了
    *  - `1`: エラー（0件ヒット、法令番号重複、パラメータ不正等）
    *  - `2`: 複数候補（`/articles` の附則テーブルのみ）
    */
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

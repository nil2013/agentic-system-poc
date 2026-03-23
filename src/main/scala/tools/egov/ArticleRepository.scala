package tools.egov

import scala.xml.*

/** 条文の取得と XML パースを担当する。
  *
  * == 役割 ==
  * LLM からのアラビア数字条番号（`"709"`）を受け取り、漢数字に変換（[[ArticleNumberConverter]]）、
  * e-Gov API を呼び出し（[[EGovApiClient]]）、XML レスポンスをパースして [[ArticleContent]] に変換する。
  *
  * '''ステートレス設計''': 内部状態を持たない。すべての状態は [[EGovApiClient]] の
  * HTTP 呼び出しに委譲される。キャッシュは行わない（条文リクエストは都度異なるため）。
  *
  * == エラーハンドリング規約 ==
  * すべてのメソッドは `Either[String, ArticleContent]` を返す:
  *  - `Left`: ユーザー向けエラーメッセージ（日本語、`"エラー:"` プレフィックス）
  *  - `Right`: 正常に取得・パースされた条文
  *
  * エラーメッセージは LLM の Tool Result としてそのまま返される。
  * LLM がエラーをユーザーに伝えるか内部知識で補完するかは LLM の判断に委ねられる
  * （「静かなフォールバック」問題。Stage 4 RESULTS.md §3.5 参照）。
  *
  * @see [[tools.ToolDispatch]] `get_article` ツールが本オブジェクトを使用
  */
object ArticleRepository {

  /** 指定法令の指定条文を取得してパースする。
    *
    * @param lawId         法令ID（半角英数。[[LawRepository.resolveLawId]] で事前に解決済み）
    * @param articleNumber 条番号（アラビア数字文字列、例: `"709"`）。
    *                      内部で [[ArticleNumberConverter.toKanjiArticle]] により漢数字に変換される。
    * @return 成功時は `Right(条文内容)`、失敗時は `Left(エラーメッセージ)`
    */
  def getArticle(lawId: String, articleNumber: String): Either[String, ArticleContent] = {
    val num = articleNumber.trim.toIntOption match {
      case Some(n) => n
      case None => return Left(s"エラー: 条番号は数字で指定してください: '$articleNumber'")
    }

    val kanjiArticle = ArticleNumberConverter.toKanjiArticle(num)

    EGovApiClient.fetchArticle(lawId, kanjiArticle).flatMap { root =>
      val lawContents = root \ "ApplData" \ "LawContents"
      parseArticleXml(lawId, articleNumber, lawContents)
    }
  }

  /** 指定法令の指定条文・指定項を取得してパースする。
    *
    * @param lawId           法令ID
    * @param articleNumber   条番号（アラビア数字文字列）
    * @param paragraphNumber 項番号（アラビア数字文字列、例: `"1"`）
    */
  def getArticleWithParagraph(lawId: String, articleNumber: String, paragraphNumber: String): Either[String, ArticleContent] = {
    val artNum = articleNumber.trim.toIntOption match {
      case Some(n) => n
      case None => return Left(s"エラー: 条番号は数字で指定してください: '$articleNumber'")
    }
    val paraNum = paragraphNumber.trim.toIntOption match {
      case Some(n) => n
      case None => return Left(s"エラー: 項番号は数字で指定してください: '$paragraphNumber'")
    }

    val kanjiArticle = ArticleNumberConverter.toKanjiArticle(artNum)
    val kanjiParagraph = ArticleNumberConverter.toKanjiParagraph(paraNum)

    EGovApiClient.fetchArticleWithParagraph(lawId, kanjiArticle, kanjiParagraph).flatMap { root =>
      val lawContents = root \ "ApplData" \ "LawContents"
      parseArticleXml(lawId, articleNumber, lawContents)
    }
  }

  /** `LawContents` の XML をパースして [[ArticleContent]] に変換する。
    *
    * `private[egov]` スコープ: テストからフィクスチャ XML で直接呼び出すために公開。
    *
    * 2つの XML 構造に対応:
    *  1. `<LawContents><Article>...</Article></LawContents>` — 条文全体を取得した場合
    *  2. `<LawContents><Paragraph>...</Paragraph></LawContents>` — 項のみを取得した場合
    *
    * @see `docs/egov-api/XMLSchemaForJapaneseLaw_v3.xsd` 法令 XML スキーマ
    */
  private[egov] def parseArticleXml(lawId: String, articleNum: String, lawContents: NodeSeq): Either[String, ArticleContent] = {
    val articles = lawContents \ "Article"
    if (articles.isEmpty) {
      val paragraphs = lawContents \\ "Paragraph"
      if (paragraphs.isEmpty) {
        return Left("エラー: 条文が見つかりません")
      }
      val parsedParas = parseParagraphs(paragraphs)
      return Right(ArticleContent(lawId, articleNum, "", "", parsedParas))
    }

    val article = articles.head
    val caption = (article \ "ArticleCaption").text.trim
    val title = (article \ "ArticleTitle").text.trim
    val paragraphs = parseParagraphs(article \ "Paragraph")

    Right(ArticleContent(lawId, articleNum, caption, title, paragraphs))
  }

  private def parseParagraphs(paragraphs: NodeSeq): List[ParagraphContent] = {
    paragraphs.map { para =>
      val num = (para \ "@Num").text.trim
      val sentences = (para \\ "Sentence").map(_.text.trim).filter(_.nonEmpty).toList
      ParagraphContent(num, sentences)
    }.toList
  }
}

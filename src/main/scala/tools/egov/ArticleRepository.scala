package tools.egov

import scala.xml.*

/** 条文取得 + XML パース */
object ArticleRepository {

  /** 条文を取得してパース */
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

  /** 条文+項を取得してパース */
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

  /** LawContents の XML をパースして ArticleContent に変換 */
  private[egov] def parseArticleXml(lawId: String, articleNum: String, lawContents: NodeSeq): Either[String, ArticleContent] = {
    val articles = lawContents \ "Article"
    if (articles.isEmpty) {
      // Article が直接含まれない場合（Paragraph のみ等）
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

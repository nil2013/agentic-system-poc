package tools.egov

import scala.xml.*

/** 法令全文の取得・キャッシュ・法令内キーワード検索を提供する。
  *
  * `/lawdata` エンドポイント（V1）または `/law_data` エンドポイント（V2）で取得した
  * 法令全文 XML をインメモリキャッシュし、`<Sentence>` 要素に対する部分一致検索を行う。
  *
  * == V1/V2 共通機能 ==
  * `EGovLawApi.fetchLawData` を使うため、V1/V2 どちらのバックエンドでも動作する。
  * V2 の `/keyword` エンドポイント（サーバ側インデックス検索）が利用可能な場合でも、
  * 特定法令内の検索には本クラスが有効（キャッシュヒット時はローカル走査のみ）。
  *
  * == キャッシュ戦略 ==
  * 最大 `MaxCacheSize` 法令分の全文 XML をキャッシュ。超過時は最も古いエントリを破棄（FIFO）。
  * 法令全文サイズ: 民法 ~1.7MB, 民事訴訟法 ~800KB, 行政手続法 ~80KB。
  * 5法令キャッシュで最大 ~5MB 程度のメモリ使用。
  *
  * @param api e-Gov API バックエンド
  */
class LawDataRepository(api: EGovLawApi) {

  private val MaxCacheSize = 5
  private var cache: Map[String, Elem] = Map.empty

  /** 法令全文 XML を取得する（キャッシュあり）。 */
  def getLawData(lawId: String): Either[String, Elem] = {
    cache.get(lawId) match {
      case Some(elem) => Right(elem)
      case None =>
        api.fetchLawData(lawId).map { root =>
          if (cache.size >= MaxCacheSize) {
            cache = cache.drop(1)
          }
          cache = cache + (lawId -> root)
          root
        }
    }
  }

  /** 法令内の条文をキーワードで検索する。
    *
    * 法令全文 XML を取得（またはキャッシュから取得）し、全 `<Article>` 内の
    * `<Sentence>` 要素テキストに対して部分一致検索を行う。
    * 本則（`<MainProvision>`）と附則（`<SupplProvision>`）の両方を検索対象とする。
    *
    * @param lawId      法令ID
    * @param keyword    検索キーワード
    * @param maxResults 最大結果数（デフォルト: 10）
    * @return マッチした条文のリスト。マッチしない場合は空リスト（エラーではない）。
    */
  def searchWithinLaw(lawId: String, keyword: String, maxResults: Int = 10): Either[String, List[SearchHit]] = {
    getLawData(lawId).map { root =>
      val law = (root \\ "Law").headOption.getOrElse(root)
      val lawBody = law \ "LawBody"

      // 本則の Article を走査
      val mainHits = (lawBody \ "MainProvision").flatMap { mp =>
        searchArticlesIn(mp \\ "Article", keyword, "本則")
      }

      // 附則の Article を走査（各 SupplProvision ごとに出典を付与）
      val supplHits = (lawBody \ "SupplProvision").flatMap { sp =>
        val amendNum = sp.attribute("AmendLawNum").map(_.text).getOrElse("")
        val source = if (amendNum.nonEmpty) s"附則（$amendNum）" else "附則"
        searchArticlesIn(sp \\ "Article", keyword, source)
      }

      (mainHits ++ supplHits).take(maxResults).toList
    }
  }

  /** Article のリストからキーワードにマッチする SearchHit を生成する。 */
  private def searchArticlesIn(articles: scala.xml.NodeSeq, keyword: String, source: String): Seq[SearchHit] = {
    articles.flatMap { article =>
      val title = (article \ "ArticleTitle").text.trim
      val caption = (article \ "ArticleCaption").text.trim
      val paragraphs = article \ "Paragraph"

      paragraphs.flatMap { para =>
        val paraNum = (para \ "@Num").text.trim
        val sentences = (para \\ "Sentence").map(_.text.trim).filter(_.nonEmpty)
        sentences.filter(_.contains(keyword)).map { s =>
          SearchHit(title, caption, paraNum, s, source)
        }
      }
    }
  }

  /** Article の Num 属性からベース条番号を抽出する。
    * "709" → Some(709), "709_2" → Some(709), "38_3_2" → Some(38)
    */
  private def baseArticleNum(numAttr: String): Option[Int] = {
    numAttr.takeWhile(_ != '_').toIntOption
  }

  /** 法令の条文範囲を取得する（MainProvision のみ、キャッシュあり）。
    *
    * ベース条番号が [from, to] の範囲にある条文をすべて取得する。
    * 枝番号付き条文（例: Num="709_2"）はベース番号（709）で判定。
    *
    * @param lawId 法令ID
    * @param from  開始条番号（inclusive）
    * @param to    終了条番号（inclusive）
    * @return 各条文のテキスト。範囲は50条以内。
    */
  def getArticleRange(lawId: String, from: Int, to: Int): Either[String, List[String]] = {
    if (from > to) {
      return Left(s"エラー: 開始条番号($from)が終了条番号($to)より大きいです。")
    }
    if (to - from > 50) {
      return Left(s"エラー: 範囲が広すぎます（${to - from + 1}条）。50条以内で指定してください。")
    }

    getLawData(lawId).map { root =>
      val mainProvision = (root \\ "MainProvision").headOption
      mainProvision.map { mp =>
        val articles = mp \\ "Article"
        val matched = articles.filter { article =>
          val numAttr = (article \ "@Num").text.trim
          baseArticleNum(numAttr).exists(n => n >= from && n <= to)
        }

        matched.map { article =>
          val caption = (article \ "ArticleCaption").text.trim
          val title = (article \ "ArticleTitle").text.trim
          val paragraphs = (article \ "Paragraph").map { para =>
            val paraNum = (para \ "@Num").text.trim
            val sentences = (para \\ "Sentence").map(_.text.trim).filter(_.nonEmpty)
            val prefix = if (paraNum.nonEmpty && paraNum != "1") s"${paraNum}　" else "　"
            prefix + sentences.mkString("")
          }
          val header = List(caption, title).filter(_.nonEmpty).mkString("\n")
          if (header.nonEmpty) s"$header\n${paragraphs.mkString("\n")}"
          else paragraphs.mkString("\n")
        }.toList
      }.getOrElse(Nil)
    }
  }

  /** 「term」という。）パターン: 括弧書き内の定義を検出する正規表現。 */
  private val DefinitionPattern = """「([^」]+)」という。）""".r

  /** 定義文のスコープ（適用範囲）を検出する。 */
  private def detectScope(sentenceText: String): String = {
    if (sentenceText.contains("この条において")) "この条において"
    else if (sentenceText.contains("この款において")) "この款において"
    else if (sentenceText.contains("この節において")) "この節において"
    else if (sentenceText.contains("この章において")) "この章において"
    else if (sentenceText.contains("この法律において")) "この法律において"
    else if (sentenceText.contains("以下同じ")) "以下同じ"
    else ""
  }

  /** 法令内の用語の定義を検索する（MainProvision のみ、キャッシュあり）。
    *
    * 2段階検索:
    *  1. 集中定義条文: ArticleCaption に "定義" を含む条文で term を検索（パターン1）
    *  2. 分散定義: 全条文の Sentence で `「term」という。）` パターンを検索（パターン2/4）
    *
    * @param lawId      法令ID
    * @param term       検索する用語
    * @param maxResults 最大結果数（デフォルト: 20）
    */
  def getDefinitions(lawId: String, term: String, maxResults: Int = 20): Either[String, List[DefinitionHit]] = {
    getLawData(lawId).map { root =>
      val mainProvision = (root \\ "MainProvision").headOption
      mainProvision.map { mp =>
        val articles = mp \\ "Article"
        var hits = List.empty[DefinitionHit]

        // Step 1: 集中定義条文（ArticleCaption に "定義" を含む）
        val defArticles = articles.filter { article =>
          (article \ "ArticleCaption").text.trim.contains("定義")
        }

        for (article <- defArticles) {
          val title = (article \ "ArticleTitle").text.trim
          val caption = (article \ "ArticleCaption").text.trim
          val sentences = (article \\ "Sentence").map(_.text.trim).filter(_.nonEmpty)

          for (s <- sentences if s.contains(term)) {
            val scope = detectScope(s)
            val extractedTerm = DefinitionPattern.findFirstMatchIn(s)
              .map(_.group(1))
              .filter(_ == term)
              .getOrElse(term)
            hits = hits :+ DefinitionHit(extractedTerm, title, caption, scope, s, 1)
          }
        }

        // Step 2: 分散定義（「term」という。）パターン）
        for (article <- articles) {
          val title = (article \ "ArticleTitle").text.trim
          val caption = (article \ "ArticleCaption").text.trim
          val sentences = (article \\ "Sentence").map(_.text.trim).filter(_.nonEmpty)

          for (s <- sentences if s.contains(s"「${term}」という。）")) {
            val scope = detectScope(s)
            val patternType = if (scope.nonEmpty) 2 else 4
            // Step 1 との重複排除
            if (!hits.exists(h => h.articleTitle == title && h.term == term && h.definitionText == s)) {
              hits = hits :+ DefinitionHit(term, title, caption, scope, s, patternType)
            }
          }
        }

        hits.take(maxResults)
      }.getOrElse(Nil)
    }
  }

  /** 法令のメタデータを取得する（キャッシュあり）。 */
  def getMetadata(lawId: String): Either[String, LawMetadata] = {
    getLawData(lawId).map { root =>
      val law = (root \\ "Law").headOption.getOrElse(root)
      val lawBody = law \ "LawBody"
      val mainProvision = lawBody \ "MainProvision"

      LawMetadata(
        lawName = (lawBody \ "LawTitle").text.trim,
        lawNum = (law \ "LawNum").text.trim,
        era = (law \ "@Era").text.trim,
        year = (law \ "@Year").text.trim,
        lawType = (law \ "@LawType").text.trim,
        promulgateMonth = Option((law \ "@PromulgateMonth").text.trim).filter(_.nonEmpty),
        promulgateDay = Option((law \ "@PromulgateDay").text.trim).filter(_.nonEmpty),
        partCount = (mainProvision \\ "Part").size,
        chapterCount = (mainProvision \\ "Chapter").size,
        articleCount = (mainProvision \\ "Article").size,
        supplProvisionCount = (lawBody \ "SupplProvision").size
      )
    }
  }

  /** 法令の構造（目次）を取得する（キャッシュあり）。
    *
    * TOC 要素がある場合はそれを使用（Strategy A）。
    * ない場合は MainProvision から構造を再構成（Strategy B）。
    */
  def getStructure(lawId: String): Either[String, String] = {
    getLawData(lawId).map { root =>
      val law = (root \\ "Law").headOption.getOrElse(root)
      val lawBody = law \ "LawBody"
      val lawName = (lawBody \ "LawTitle").text.trim
      val lawNum = (law \ "LawNum").text.trim

      val header = s"目次: $lawName（$lawNum）\n"

      val toc = (lawBody \ "TOC").headOption
      val body = toc match {
        case Some(tocElem) => formatToc(tocElem)
        case None => formatFromMainProvision(lawBody)
      }

      header + "\n" + body
    }
  }

  /** TOC 要素からインデント付き構造テキストを生成する（Strategy A） */
  private def formatToc(toc: Node): String = {
    val sb = new StringBuilder
    for (child <- toc.child) {
      child match {
        case elem: scala.xml.Elem => formatTocElement(elem, 0, sb)
        case _ =>
      }
    }
    sb.toString
  }

  private def formatTocElement(elem: scala.xml.Elem, depth: Int, sb: StringBuilder): Unit = {
    val indent = "  " * depth
    val prefix = if (depth == 0) "■ " else "├ "

    elem.label match {
      case "TOCPart" =>
        val title = (elem \ "PartTitle").text.trim
        sb.append(s"$indent$prefix$title\n")
        elem.child.foreach {
          case e: scala.xml.Elem if e.label.startsWith("TOC") => formatTocElement(e, depth + 1, sb)
          case _ =>
        }
      case "TOCChapter" =>
        val title = (elem \ "ChapterTitle").text.trim
        val range = (elem \ "ArticleRange").text.trim
        val rangeStr = if (range.nonEmpty) s" $range" else ""
        sb.append(s"$indent$prefix$title$rangeStr\n")
        elem.child.foreach {
          case e: scala.xml.Elem if e.label.startsWith("TOC") => formatTocElement(e, depth + 1, sb)
          case _ =>
        }
      case "TOCSection" =>
        val title = (elem \ "SectionTitle").text.trim
        val range = (elem \ "ArticleRange").text.trim
        val rangeStr = if (range.nonEmpty) s" $range" else ""
        sb.append(s"$indent$prefix$title$rangeStr\n")
        elem.child.foreach {
          case e: scala.xml.Elem if e.label.startsWith("TOC") => formatTocElement(e, depth + 1, sb)
          case _ =>
        }
      case "TOCSubsection" =>
        val title = (elem \ "SubsectionTitle").text.trim
        val range = (elem \ "ArticleRange").text.trim
        val rangeStr = if (range.nonEmpty) s" $range" else ""
        sb.append(s"$indent$prefix$title$rangeStr\n")
        elem.child.foreach {
          case e: scala.xml.Elem if e.label.startsWith("TOC") => formatTocElement(e, depth + 1, sb)
          case _ =>
        }
      case "TOCDivision" =>
        val title = (elem \ "DivisionTitle").text.trim
        val range = (elem \ "ArticleRange").text.trim
        val rangeStr = if (range.nonEmpty) s" $range" else ""
        sb.append(s"$indent$prefix$title$rangeStr\n")
      case "TOCSupplProvision" =>
        val label = (elem \ "SupplProvisionLabel").text.trim
        val range = (elem \ "ArticleRange").text.trim
        val rangeStr = if (range.nonEmpty) s" $range" else ""
        val displayLabel = if (label.nonEmpty) label else "附則"
        sb.append(s"$indent■ $displayLabel$rangeStr\n")
      case "TOCArticle" =>
        val title = (elem \ "ArticleTitle").text.trim
        val caption = (elem \ "ArticleCaption").text.trim
        val display = List(title, caption).filter(_.nonEmpty).mkString(" ")
        sb.append(s"$indent$prefix$display\n")
      case _ => // TOCLabel, TOCPreambleLabel, etc. — skip
    }
  }

  /** MainProvision から構造を再構成する（Strategy B: TOC なしのフォールバック） */
  private def formatFromMainProvision(lawBody: NodeSeq): String = {
    val sb = new StringBuilder
    val mainProvision = (lawBody \ "MainProvision").headOption

    mainProvision match {
      case Some(mp) =>
        formatStructureElement(mp, 0, sb)
      case None =>
        sb.append("（本則なし）\n")
    }

    // 附則のカウント
    val supplCount = (lawBody \ "SupplProvision").size
    if (supplCount > 0) {
      sb.append(s"■ 附則（${supplCount}本）\n")
    }

    sb.toString
  }

  private def formatStructureElement(elem: Node, depth: Int, sb: StringBuilder): Unit = {
    val indent = "  " * depth
    val prefix = if (depth == 0) "" else "├ "

    elem match {
      case e: scala.xml.Elem =>
        e.label match {
          case "Part" =>
            val title = (e \ "PartTitle").text.trim
            sb.append(s"$indent■ $title\n")
            e.child.foreach { c => formatStructureElement(c, depth + 1, sb) }
          case "Chapter" =>
            val title = (e \ "ChapterTitle").text.trim
            val articles = e \\ "Article"
            val range = if (articles.nonEmpty) {
              val first = (articles.head \ "ArticleTitle").text.trim
              val last = (articles.last \ "ArticleTitle").text.trim
              if (first == last) s"（$first）" else s"（$first〜$last）"
            } else ""
            sb.append(s"$indent$prefix$title $range\n")
            e.child.foreach { c => formatStructureElement(c, depth + 1, sb) }
          case "Section" =>
            val title = (e \ "SectionTitle").text.trim
            val articles = e \\ "Article"
            val range = if (articles.nonEmpty) {
              val first = (articles.head \ "ArticleTitle").text.trim
              val last = (articles.last \ "ArticleTitle").text.trim
              if (first == last) s"（$first）" else s"（$first〜$last）"
            } else ""
            sb.append(s"$indent$prefix$title $range\n")
          case "MainProvision" =>
            e.child.foreach { c => formatStructureElement(c, depth, sb) }
          case _ => // Article, Paragraph, etc. — skip at structure level
        }
      case _ =>
    }
  }

  /** テスト用: キャッシュをクリアする。 */
  private[egov] def clearCache(): Unit = {
    cache = Map.empty
  }
}

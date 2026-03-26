/** e-Gov 法令 API のドメインモデル（V1/V2 共通）。
  *
  * `tools.egov` パッケージは e-Gov 法令 API をラップし、LLM の Tool Calling から
  * 利用できるインターフェースを提供する。3層構成:
  *
  *  - '''API Client 層''' ([[EGovLawApi]] trait + V1/V2 実装): HTTP リクエスト + レスポンスパース
  *  - '''Domain 層''' ([[LawRepository]], [[ArticleRepository]]): キャッシュ・名前解決・条文取得
  *  - '''Tool Calling 層''' ([[tools.ToolDispatch]]): LLM ↔ ドメイン層のブリッジ
  *
  * 設計の詳細は `docs/egov-api/egov-law-client-design.md` を参照。
  */
package tools.egov

/** e-Gov 法令一覧 API (`/lawlists`) から取得される法令情報。
  *
  * @param lawId  法令ID。e-Gov が割り当てる一意の半角英数識別子（例: `"129AC0000000089"` = 民法）。
  *               法令番号と異なり重複しないため、条文取得 API の引数として安全に使える。
  * @param lawName 法令名（全角）。例: `"民法"`, `"個人情報の保護に関する法律"`。
  *               LLM が法令を指定する際のキーとなるが、表記ゆれがあるため
  *               [[LawRepository.resolveLawId]] で lawId に変換してから使用する。
  * @param lawNo  法令番号。例: `"明治二十九年法律第八十九号"`。人間向けの引用形式。
  */
case class LawInfo(lawId: String, lawName: String, lawNo: String)

/** 条文中の1つの項の内容。
  *
  * @param paragraphNum XML の `Paragraph/@Num` 属性値（例: `"1"`, `"2"`）。
  *                     第1項は空文字列の場合がある（XML 上 `<ParagraphNum/>` が空）。
  * @param sentences    `<Sentence>` 要素のテキストを順序通りに格納したリスト。
  *                     1つの項に複数の文が含まれる場合がある（本文 + 但書など）。
  */
case class ParagraphContent(paragraphNum: String, sentences: List[String])

/** 条文の構造化された内容。[[ArticleRepository.getArticle]] の返却型。
  *
  * @param lawId       取得元の法令ID
  * @param articleNum  リクエスト時の条番号（アラビア数字文字列、例: `"709"`）
  * @param caption     条文見出し。XML の `<ArticleCaption>` テキスト。
  *                    例: `"（不法行為による損害賠償）"`。括弧付きのまま格納される。
  * @param title       条文タイトル。XML の `<ArticleTitle>` テキスト。
  *                    例: `"第七百九条"`。漢数字表記。
  * @param paragraphs  項のリスト。順序は XML の出現順。
  */
case class ArticleContent(
    lawId: String,
    articleNum: String,
    caption: String,
    title: String,
    paragraphs: List[ParagraphContent]
) {

  /** LLM へのツール結果として返す、人間に読みやすいテキスト形式に変換する。
    *
    * フォーマット: 見出し + タイトル + 各項のテキスト。
    * 単一項の条文（第1項のみ）では項番号を省略する。
    */
  def toText: String = {
    val header = List(caption, title).filter(_.nonEmpty).mkString("\n")
    val body = paragraphs.map { p =>
      val prefix = if (p.paragraphNum.nonEmpty && p.paragraphNum != "1") s"${p.paragraphNum}　" else "　"
      prefix + p.sentences.mkString("")
    }.mkString("\n")
    if (header.nonEmpty) s"$header\n$body" else body
  }
}

/** 法令内キーワード検索のヒット結果。[[LawDataRepository.searchWithinLaw]] の返却型。
  *
  * @param articleTitle   条文タイトル（漢数字、例: `"第九十一条"`）
  * @param articleCaption 条文見出し（例: `"（訴訟記録の閲覧等）"`）。見出しがない条文では空文字列。
  * @param paragraphNum   項番号（例: `"1"`, `"2"`）
  * @param snippet        マッチした文のテキスト
  */
case class SearchHit(
    articleTitle: String,
    articleCaption: String,
    paragraphNum: String,
    snippet: String
) {
  /** LLM へのツール結果として返す1行テキスト。 */
  def toText: String = {
    val loc = List(articleTitle, articleCaption).filter(_.nonEmpty).mkString(" ")
    val para = if (paragraphNum.nonEmpty && paragraphNum != "1") s"第${paragraphNum}項" else ""
    val prefix = List(loc, para).filter(_.nonEmpty).mkString(" ")
    s"$prefix: ${snippet.take(120)}"
  }
}

/** 法令のメタデータ。[[LawDataRepository.getMetadata]] の返却型。 */
case class LawMetadata(
    lawName: String,
    lawNum: String,
    era: String,
    year: String,
    lawType: String,
    promulgateMonth: Option[String],
    promulgateDay: Option[String],
    partCount: Int,
    chapterCount: Int,
    articleCount: Int,
    supplProvisionCount: Int
) {
  def toText: String = {
    val lawTypeJa = lawType match {
      case "Constitution" => "Constitution（憲法）"
      case "Act" => "Act（法律）"
      case "CabinetOrder" => "CabinetOrder（政令）"
      case "ImperialOrder" => "ImperialOrder（勅令）"
      case "MinisterialOrdinance" => "MinisterialOrdinance（府省令）"
      case "Rule" => "Rule（規則）"
      case other => other
    }
    val eraJa = era match {
      case "Meiji" => "明治"
      case "Taisho" => "大正"
      case "Showa" => "昭和"
      case "Heisei" => "平成"
      case "Reiwa" => "令和"
      case other => other
    }
    val promulgation = (promulgateMonth, promulgateDay) match {
      case (Some(m), Some(d)) => s"${eraJa}${year}年${m}月${d}日"
      case _ => "不明"
    }
    val scale = List(
      if (partCount > 0) s"${partCount}編" else "",
      if (chapterCount > 0) s"${chapterCount}章" else "",
      s"${articleCount}条",
      if (supplProvisionCount > 0) s"附則${supplProvisionCount}本" else ""
    ).filter(_.nonEmpty).mkString(", ")

    s"""法令名: $lawName
       |法令番号: $lawNum
       |種別: $lawTypeJa
       |公布: $promulgation
       |規模（本則）: $scale""".stripMargin
  }
}

/** [[LawRepository.resolveLawId]] の返却型。法令名→lawId の解決結果を表す。
  *
  * 解決の優先順位:
  *  1. lawId フォーマット（半角英数のみ）→ そのまま [[Resolved]]
  *  2. 法令一覧キャッシュでの完全一致 → [[Resolved]]
  *  3. 前方一致（1件のみ）→ [[Resolved]]、複数件 → [[Ambiguous]]
  *  4. 部分一致（1件のみ）→ [[Resolved]]、複数件 → [[Ambiguous]]
  *  5. [[KnownLaws]] フォールバック → [[Resolved]] or [[NotFound]]
  */
sealed trait ResolveResult
object ResolveResult {
  /** lawId が正常に解決された */
  case class Resolved(lawId: String) extends ResolveResult
  /** 複数の候補が見つかり一意に特定できない。`candidates` は最大5件。 */
  case class Ambiguous(candidates: Seq[LawInfo]) extends ResolveResult
  /** 該当する法令が見つからない */
  case object NotFound extends ResolveResult
}

/** 主要法令の短縮名→lawId マッピング。
  *
  * [[LawRepository.resolveLawId]] の最終フォールバックとして使用される。
  * 法令一覧キャッシュが未ロードの場合や、短縮名（「民法」等）が部分一致で
  * 複数ヒットする場合でも、ここに登録された法令は確実に解決できる。
  *
  * '''拡張ポイントではない''': 新しい法令への対応は [[LawRepository]] の
  * 動的名前解決（法令一覧キャッシュ）で行う。ここに追加するのは
  * 頻繁に使われる短縮名のショートカットのみ。
  */
object KnownLaws {
  val mapping: Map[String, String] = Map(
    "民法" -> "129AC0000000089",
    "刑法" -> "140AC0000000045",
    "憲法" -> "321CONSTITUTION",
    "行政手続法" -> "405AC0000000088",
    "行政事件訴訟法" -> "337AC0000000139",
    "民事訴訟法" -> "408AC0000000109",
  )
}

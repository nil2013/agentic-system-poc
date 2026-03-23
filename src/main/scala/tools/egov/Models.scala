package tools.egov

/** 法令情報（法令一覧から取得） */
case class LawInfo(lawId: String, lawName: String, lawNo: String)

/** 項の内容 */
case class ParagraphContent(paragraphNum: String, sentences: List[String])

/** 条文の内容 */
case class ArticleContent(
    lawId: String,
    articleNum: String,
    caption: String,
    title: String,
    paragraphs: List[ParagraphContent]
) {
  /** 読みやすいテキスト形式に変換 */
  def toText: String = {
    val header = List(caption, title).filter(_.nonEmpty).mkString("\n")
    val body = paragraphs.map { p =>
      val prefix = if (p.paragraphNum.nonEmpty && p.paragraphNum != "1") s"${p.paragraphNum}　" else "　"
      prefix + p.sentences.mkString("")
    }.mkString("\n")
    if (header.nonEmpty) s"$header\n$body" else body
  }
}

/** 法令名→lawId 解決の結果 */
sealed trait ResolveResult
object ResolveResult {
  case class Resolved(lawId: String) extends ResolveResult
  case class Ambiguous(candidates: Seq[LawInfo]) extends ResolveResult
  case object NotFound extends ResolveResult
}

/** よく使う法令の短縮名→lawId マッピング（フォールバック用） */
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

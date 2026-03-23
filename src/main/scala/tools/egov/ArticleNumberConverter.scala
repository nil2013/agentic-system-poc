package tools.egov

/** アラビア数字 → 漢数字の条番号・項番号変換 */
object ArticleNumberConverter {

  private val kanjiDigits = Array("〇", "一", "二", "三", "四", "五", "六", "七", "八", "九")

  /** アラビア数字 → "第X条" 形式 */
  def toKanjiArticle(num: Int): String = {
    s"第${toKanjiNumber(num)}条"
  }

  /** アラビア数字 → "第X項" 形式 */
  def toKanjiParagraph(num: Int): String = {
    s"第${toKanjiNumber(num)}項"
  }

  /** 整数を漢数字に変換（1〜9999 対応） */
  def toKanjiNumber(num: Int): String = {
    require(num >= 1 && num <= 9999, s"条番号は1〜9999の範囲: $num")

    val sb = new StringBuilder
    var remaining = num

    // 千の位
    val thousands = remaining / 1000
    remaining %= 1000
    if (thousands > 0) {
      if (thousands > 1) { sb.append(kanjiDigits(thousands)) }
      sb.append("千")
    }

    // 百の位
    val hundreds = remaining / 100
    remaining %= 100
    if (hundreds > 0) {
      if (hundreds > 1) { sb.append(kanjiDigits(hundreds)) }
      sb.append("百")
    }

    // 十の位
    val tens = remaining / 10
    remaining %= 10
    if (tens > 0) {
      if (tens > 1) { sb.append(kanjiDigits(tens)) }
      sb.append("十")
    }

    // 一の位
    if (remaining > 0) {
      sb.append(kanjiDigits(remaining))
    }

    sb.toString
  }
}

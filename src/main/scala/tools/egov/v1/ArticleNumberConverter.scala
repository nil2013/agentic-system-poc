package tools.egov.v1

/** アラビア数字と漢数字の条番号・項番号を変換する（V1 API 固有）。
  *
  * e-Gov 法令 API V1 の `/articles` エンドポイントは条番号を漢数字全角で受け付ける
  * （例: `article=第七百九条`）。V2 では異なる形式を使用する見込みのため、
  * V1 パッケージに配置する。
  *
  * == 変換規則 ==
  * 日本の法令番号の慣例に従う位取り記数法:
  *  - 千/百/十の係数が1の場合は省略（百 = 100、二百 = 200）
  *  - 一の位はそのまま漢数字
  *  - 例: 709 → 七百九、1001 → 千一、11 → 十一
  *
  * == 既知の制限 ==
  *  - 対応範囲: 1〜9999
  *  - 枝番号（第709条の2）は未対応
  */
object ArticleNumberConverter {

  private val kanjiDigits = Array("〇", "一", "二", "三", "四", "五", "六", "七", "八", "九")

  /** アラビア数字を `"第X条"` 形式の漢数字条番号に変換する。 */
  def toKanjiArticle(num: Int): String = {
    s"第${toKanjiNumber(num)}条"
  }

  /** アラビア数字を `"第X項"` 形式の漢数字項番号に変換する。 */
  def toKanjiParagraph(num: Int): String = {
    s"第${toKanjiNumber(num)}項"
  }

  /** 整数を漢数字文字列に変換する（接頭辞・接尾辞なし）。 */
  def toKanjiNumber(num: Int): String = {
    require(num >= 1 && num <= 9999, s"条番号は1〜9999の範囲: $num")

    val sb = new StringBuilder
    var remaining = num

    val thousands = remaining / 1000
    remaining %= 1000
    if (thousands > 0) {
      if (thousands > 1) { sb.append(kanjiDigits(thousands)) }
      sb.append("千")
    }

    val hundreds = remaining / 100
    remaining %= 100
    if (hundreds > 0) {
      if (hundreds > 1) { sb.append(kanjiDigits(hundreds)) }
      sb.append("百")
    }

    val tens = remaining / 10
    remaining %= 10
    if (tens > 0) {
      if (tens > 1) { sb.append(kanjiDigits(tens)) }
      sb.append("十")
    }

    if (remaining > 0) {
      sb.append(kanjiDigits(remaining))
    }

    sb.toString
  }
}

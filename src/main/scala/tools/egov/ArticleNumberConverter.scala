package tools.egov

/** アラビア数字と漢数字の条番号・項番号を相互変換する。
  *
  * == なぜ変換が必要か ==
  * e-Gov 法令 API V1 の `/articles` エンドポイントは条番号を漢数字全角で受け付ける
  * （例: `article=第七百九条`）。一方、LLM は `"709"` のようなアラビア数字で条番号を
  * 指定する。この変換器が両者を橋渡しする。
  *
  * == 変換規則 ==
  * 日本の法令番号の慣例に従う位取り記数法:
  *  - 千/百/十の係数が1の場合は省略（百 = 100、二百 = 200）
  *  - 一の位はそのまま漢数字
  *  - 例: 709 → 七百九、1001 → 千一、11 → 十一
  *
  * == 既知の制限 ==
  *  - 対応範囲: 1〜9999（日本の法令条文番号として十分）
  *  - 枝番号（第709条の2）は未対応。将来 `/articles` で枝番号指定が必要になった場合に拡張
  *  - 附則の条番号は通常のアラビア数字と同じ形式なので対応済み
  *
  * @see [[ArticleRepository]] が本変換器を使用してAPI リクエストを構築する
  */
object ArticleNumberConverter {

  private val kanjiDigits = Array("〇", "一", "二", "三", "四", "五", "六", "七", "八", "九")

  /** アラビア数字を `"第X条"` 形式の漢数字条番号に変換する。
    *
    * @param num 条番号（1〜9999）
    * @return 漢数字条番号。例: `toKanjiArticle(709)` → `"第七百九条"`
    * @throws IllegalArgumentException num が範囲外の場合
    */
  def toKanjiArticle(num: Int): String = {
    s"第${toKanjiNumber(num)}条"
  }

  /** アラビア数字を `"第X項"` 形式の漢数字項番号に変換する。
    *
    * @param num 項番号（1〜9999）
    * @return 漢数字項番号。例: `toKanjiParagraph(1)` → `"第一項"`
    */
  def toKanjiParagraph(num: Int): String = {
    s"第${toKanjiNumber(num)}項"
  }

  /** 整数を漢数字文字列に変換する（接頭辞・接尾辞なし）。
    *
    * @param num 変換対象（1〜9999）
    * @return 漢数字文字列。例: `toKanjiNumber(709)` → `"七百九"`
    */
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

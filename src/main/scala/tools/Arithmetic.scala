package tools

/** 簡易再帰下降パーサによる四則演算ツール。
  *
  * == 経緯 ==
  * ガイドのサンプルコードは `javax.script.ScriptEngineManager().getEngineByName("js")` を
  * 使用していたが、JDK 17+ では Nashorn が削除されており `null` が返る。
  * GraalJS の依存追加は PoC には過剰なため、自前の再帰下降パーサで代替した。
  *
  * == 文法 ==
  * {{{
  * expr   = term (('+' | '-') term)*
  * term   = factor (('*' | '/') factor)*
  * factor = number | '(' expr ')' | '-' factor
  * }}}
  *
  * == 入力サニタイズ ==
  * `calculate` は入力を数字・小数点・四則演算子・括弧・スペース以外の文字を含まないことを検証する。
  * これにより任意コード実行を防止する（`javax.script` 時代の `eval` とは異なり、
  * パーサが認識しないトークンは構文エラーになる）。
  *
  * == 既知の制限 ==
  *  - 浮動小数点数（`Double`）のみ。整数モードや `BigDecimal` は未対応
  *  - `%`（剰余）、`^`（累乗）、関数（`sqrt` 等）は未対応
  *  - LLM が生成する計算式は通常四則演算で十分だが、将来的な拡張が必要なら
  *    文法にルールを追加する
  *
  * @see [[ToolDispatch]] `calculate` ツールが本オブジェクトを使用
  */
object Arithmetic {

  /** 四則演算式を評価して結果を文字列で返す。
    *
    * @param expression 計算式。半角数字・小数点・四則演算子・括弧・スペースのみ使用可能。
    *                   例: "1400000 * 0.3", "(100 + 200) * 3"
    * @return 計算結果の文字列（例: "420000.0"）。
    *         エラー時は "エラー: ..." または "計算エラー: ..." プレフィックス付きメッセージ。
    */
  def calculate(expression: String): String = {
    val sanitized = expression.replaceAll("[^0-9.+\\-*/() ]", "")
    if (sanitized != expression.trim) {
      return s"エラー: 許可されていない文字が含まれています"
    }
    try {
      val tokens = tokenize(sanitized)
      val (result, rest) = parseExpr(tokens)
      if (rest.nonEmpty) s"エラー: 解析できない部分: ${rest.mkString}"
      else result.toString
    } catch {
      case e: Exception => s"計算エラー: ${e.getMessage}"
    }
  }

  private sealed trait Token
  private case class Num(v: Double) extends Token
  private case class Op(c: Char) extends Token

  private def tokenize(s: String): List[Token] = {
    var tokens = List.empty[Token]
    var i = 0
    val cs = s.trim.toCharArray
    while (i < cs.length) {
      if (cs(i).isWhitespace) { i += 1 }
      else if (cs(i).isDigit || cs(i) == '.') {
        val start = i
        while (i < cs.length && (cs(i).isDigit || cs(i) == '.')) { i += 1 }
        tokens = tokens :+ Num(new String(cs, start, i - start).toDouble)
      } else {
        tokens = tokens :+ Op(cs(i))
        i += 1
      }
    }
    tokens
  }

  private def parseExpr(tokens: List[Token]): (Double, List[Token]) = {
    var (left, rest) = parseTerm(tokens)
    while (rest.headOption.exists { case Op('+') | Op('-') => true; case _ => false }) {
      val Op(op) = rest.head: @unchecked
      val (right, rest2) = parseTerm(rest.tail)
      left = if (op == '+') left + right else left - right
      rest = rest2
    }
    (left, rest)
  }

  private def parseTerm(tokens: List[Token]): (Double, List[Token]) = {
    var (left, rest) = parseFactor(tokens)
    while (rest.headOption.exists { case Op('*') | Op('/') => true; case _ => false }) {
      val Op(op) = rest.head: @unchecked
      val (right, rest2) = parseFactor(rest.tail)
      left = if (op == '*') left * right else left / right
      rest = rest2
    }
    (left, rest)
  }

  private def parseFactor(tokens: List[Token]): (Double, List[Token]) = {
    tokens match {
      case Num(v) :: rest => (v, rest)
      case Op('(') :: rest =>
        val (v, rest2) = parseExpr(rest)
        rest2 match {
          case Op(')') :: rest3 => (v, rest3)
          case _ => throw new Exception("閉じ括弧がありません")
        }
      case Op('-') :: rest =>
        val (v, rest2) = parseFactor(rest)
        (-v, rest2)
      case _ => throw new Exception(s"予期しないトークン: ${tokens.headOption}")
    }
  }
}

package tools

/** 簡易再帰下降パーサによる四則演算。javax.script が利用不可のため自前実装。 */
object Arithmetic {

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

//> using scala 3.6
//> using dep com.softwaremill.sttp.client4::core:4.0.19
//> using dep com.softwaremill.sttp.client4::circe:4.0.19
//> using dep io.circe::circe-generic:0.14.15
//> using dep io.circe::circe-parser:0.14.15
//> using dep org.scala-lang.modules::scala-xml:2.3.0

import sttp.client4.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.parser.{decode, parse}
import scala.xml.*

val BaseUrl = sys.env.getOrElse("LLM_BASE_URL", "http://localhost:8080/v1")
val Model = sys.env.getOrElse("LLM_MODEL", "local")
val MaxToolRounds = 3

// ============================================================
// ツール実装
// ============================================================

object StatuteSearch {
  val EgovBase = "https://laws.e-gov.go.jp/api/1"
  val KnownLaws: Map[String, String] = Map(
    "民法" -> "129AC0000000089",
    "刑法" -> "140AC0000000045",
    "憲法" -> "321CONSTITUTION",
    "行政手続法" -> "405AC0000000088",
    "行政事件訴訟法" -> "337AC0000000139",
    "民事訴訟法" -> "408AC0000000109",
  )

  def searchStatute(lawName: String, articleNumber: String): String = {
    KnownLaws.get(lawName) match {
      case None =>
        s"エラー: '$lawName' は登録されていません。利用可能: ${KnownLaws.keys.mkString(", ")}"
      case Some(lawId) =>
        val backend = DefaultSyncBackend()
        val resp = basicRequest
          .get(uri"$EgovBase/lawdata/$lawId")
          .response(asString)
          .readTimeout(scala.concurrent.duration.Duration(30, "s"))
          .send(backend)
        backend.close()
        resp.body match {
          case Left(err) => s"エラー: API呼び出し失敗: $err"
          case Right(xmlStr) =>
            val root = XML.loadString(xmlStr)
            val articles = (root \\ "Article").filter(a => (a \ "@Num").text == articleNumber)
            articles.headOption match {
              case None => s"エラー: ${lawName}第${articleNumber}条が見つかりません。"
              case Some(article) =>
                val caption = (article \ "ArticleCaption").text.trim
                val title = (article \ "ArticleTitle").text.trim
                val sentences = (article \\ "Sentence").map(_.text.trim).filter(_.nonEmpty)
                (List(caption, title).filter(_.nonEmpty) ++ sentences).mkString("\n")
            }
        }
    }
  }
}

object LawListSearch {
  private var cache: Option[Seq[(String, String, String)]] = None

  private def loadLawList(): Seq[(String, String, String)] = {
    cache.getOrElse {
      val backend = DefaultSyncBackend()
      val resp = basicRequest
        .get(uri"https://laws.e-gov.go.jp/api/1/lawlists/2")
        .response(asString)
        .readTimeout(scala.concurrent.duration.Duration(60, "s"))
        .send(backend)
      backend.close()
      val root = XML.loadString(resp.body.getOrElse("<DataRoot/>"))
      val laws = (root \\ "LawNameListInfo").map { info =>
        ((info \ "LawName").text, (info \ "LawId").text, (info \ "LawNo").text)
      }
      cache = Some(laws)
      laws
    }
  }

  def findLawByKeyword(keyword: String): String = {
    val matches = loadLawList().filter(_._1.contains(keyword)).take(10)
    if (matches.isEmpty) {
      s"'$keyword' を含む法令は見つかりませんでした。"
    } else {
      val lines = matches.map { case (name, _, number) => s"- $name（$number）" }
      s"'$keyword' を含む法令 (${matches.size}件):\n${lines.mkString("\n")}"
    }
  }
}

object Arithmetic {
  // 簡易再帰下降パーサ（javax.script が利用不可のため自前実装）
  // expr   = term (('+' | '-') term)*
  // term   = factor (('*' | '/') factor)*
  // factor = number | '(' expr ')'
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

// ============================================================
// ツールディスパッチ
// ============================================================

def dispatchTool(name: String, args: JsonObject): String = {
  name match {
    case "search_statute" =>
      val lawName = args("law_name").flatMap(_.asString).getOrElse("")
      val articleNum = args("article_number").flatMap(_.asString).getOrElse("")
      StatuteSearch.searchStatute(lawName, articleNum)
    case "find_law_by_keyword" =>
      val keyword = args("keyword").flatMap(_.asString).getOrElse("")
      LawListSearch.findLawByKeyword(keyword)
    case "calculate" =>
      val expr = args("expression").flatMap(_.asString).getOrElse("")
      Arithmetic.calculate(expr)
    case other =>
      s"エラー: 未知のツール '$other'"
  }
}

// ============================================================
// ツール定義（2パターン）
// ============================================================

def makeToolDefs(detailed: Boolean): Json = {
  if (detailed) {
    Json.arr(
      Json.obj("type" -> Json.fromString("function"), "function" -> Json.obj(
        "name" -> Json.fromString("search_statute"),
        "description" -> Json.fromString("日本の法令の条文を検索して取得する。法令名と条番号を指定する。"),
        "parameters" -> Json.obj(
          "type" -> Json.fromString("object"),
          "properties" -> Json.obj(
            "law_name" -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("法令名。例: 民法, 刑法, 憲法")),
            "article_number" -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("条番号。数字のみ。例: 709, 199, 9"))
          ),
          "required" -> Json.arr(Json.fromString("law_name"), Json.fromString("article_number"))
        )
      )),
      Json.obj("type" -> Json.fromString("function"), "function" -> Json.obj(
        "name" -> Json.fromString("find_law_by_keyword"),
        "description" -> Json.fromString("法令名にキーワードを含む法令を検索する。法令の正式名称や法令番号を調べたいときに使う。"),
        "parameters" -> Json.obj(
          "type" -> Json.fromString("object"),
          "properties" -> Json.obj(
            "keyword" -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("検索キーワード。例: 個人情報, 消費者, 行政"))
          ),
          "required" -> Json.arr(Json.fromString("keyword"))
        )
      )),
      Json.obj("type" -> Json.fromString("function"), "function" -> Json.obj(
        "name" -> Json.fromString("calculate"),
        "description" -> Json.fromString("数式を計算して結果を返す。四則演算のみ対応。金額計算や割合計算に使う。"),
        "parameters" -> Json.obj(
          "type" -> Json.fromString("object"),
          "properties" -> Json.obj(
            "expression" -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("計算式。例: 100 * 0.3, 1500 + 2000"))
          ),
          "required" -> Json.arr(Json.fromString("expression"))
        )
      ))
    )
  } else {
    // パターン1: 簡潔
    Json.arr(
      Json.obj("type" -> Json.fromString("function"), "function" -> Json.obj(
        "name" -> Json.fromString("search_statute"),
        "description" -> Json.fromString("日本の法令の条文を検索する"),
        "parameters" -> Json.obj(
          "type" -> Json.fromString("object"),
          "properties" -> Json.obj(
            "law_name" -> Json.obj("type" -> Json.fromString("string")),
            "article_number" -> Json.obj("type" -> Json.fromString("string"))
          ),
          "required" -> Json.arr(Json.fromString("law_name"), Json.fromString("article_number"))
        )
      )),
      Json.obj("type" -> Json.fromString("function"), "function" -> Json.obj(
        "name" -> Json.fromString("find_law_by_keyword"),
        "description" -> Json.fromString("法令名を検索する"),
        "parameters" -> Json.obj(
          "type" -> Json.fromString("object"),
          "properties" -> Json.obj(
            "keyword" -> Json.obj("type" -> Json.fromString("string"))
          ),
          "required" -> Json.arr(Json.fromString("keyword"))
        )
      )),
      Json.obj("type" -> Json.fromString("function"), "function" -> Json.obj(
        "name" -> Json.fromString("calculate"),
        "description" -> Json.fromString("計算する"),
        "parameters" -> Json.obj(
          "type" -> Json.fromString("object"),
          "properties" -> Json.obj(
            "expression" -> Json.obj("type" -> Json.fromString("string"))
          ),
          "required" -> Json.arr(Json.fromString("expression"))
        )
      ))
    )
  }
}

// ============================================================
// エージェントループ
// ============================================================

case class AgentResult(
    toolsCalled: List[String],  // "toolName(args)" のログ
    answer: String,
    rounds: Int
)

def runAgent(userQuery: String, toolDefs: Json): AgentResult = {
  var messages = List(
    Json.obj("role" -> Json.fromString("system"), "content" -> Json.fromString(
      "あなたは日本法に関するアシスタントです。" +
      "ユーザの質問に答えるために、必要に応じてツールを使ってください。" +
      "ツールが不要な質問にはツールを使わずに直接回答してください。"
    )),
    Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(userQuery))
  )
  var toolCallLog = List.empty[String]

  for (round <- 0 until MaxToolRounds) {
    val body = Json.obj(
      "model" -> Json.fromString(Model),
      "messages" -> Json.arr(messages*),
      "tools" -> toolDefs,
      "max_tokens" -> Json.fromInt(4096),
      "temperature" -> Json.fromDoubleOrNull(0.0)
    )

    val backend = DefaultSyncBackend()
    val resp = basicRequest
      .post(uri"$BaseUrl/chat/completions")
      .contentType("application/json")
      .body(body.noSpaces)
      .response(asString)
      .readTimeout(scala.concurrent.duration.Duration(120, "s"))
      .send(backend)
    backend.close()

    val respJson = parse(resp.body.getOrElse("{}")).getOrElse(Json.Null)
    val choiceMsg = respJson.hcursor
      .downField("choices").downArray.downField("message").focus.getOrElse(Json.Null)

    val toolCalls = choiceMsg.hcursor.downField("tool_calls").as[List[Json]].getOrElse(Nil)

    if (toolCalls.isEmpty) {
      val content = choiceMsg.hcursor.downField("content").as[String].getOrElse("(empty)")
      return AgentResult(toolCallLog, content, round + 1)
    }

    messages = messages :+ choiceMsg
    for (tc <- toolCalls) {
      val c = tc.hcursor
      val fnName = c.downField("function").downField("name").as[String].getOrElse("")
      val fnArgs = c.downField("function").downField("arguments").as[String].getOrElse("{}")
      val argsObj = parse(fnArgs).flatMap(_.as[JsonObject]).getOrElse(JsonObject.empty)
      val tcId = c.downField("id").as[String].getOrElse("")

      val logEntry = s"$fnName($fnArgs)"
      toolCallLog = toolCallLog :+ logEntry

      val result = dispatchTool(fnName, argsObj)
      messages = messages :+ Json.obj(
        "role" -> Json.fromString("tool"),
        "tool_call_id" -> Json.fromString(tcId),
        "content" -> Json.fromString(result)
      )
    }
  }
  AgentResult(toolCallLog, "(MAX_TOOL_ROUNDS exceeded)", MaxToolRounds)
}

// ============================================================
// テストケース
// ============================================================

case class TestCase(id: Int, query: String, expectedTool: String)

val testCases = List(
  TestCase(1, "個人情報に関する法律にはどんなものがありますか？", "find_law_by_keyword"),
  TestCase(2, "民法709条の条文を見せてください", "search_statute"),
  TestCase(3, "研究費140万円の30%はいくらですか？", "calculate"),
  TestCase(4, "消費者契約法の正式名称は？", "find_law_by_keyword"),
  TestCase(5, "刑法199条を教えてください", "search_statute"),
  TestCase(6, "50000 + 150000 + 400000 の合計は？", "calculate"),
)

def runExperiment(patternName: String, toolDefs: Json): List[(Int, Boolean, String)] = {
  println(s"\n=== $patternName ===")
  var results = List.empty[(Int, Boolean, String)]

  for (tc <- testCases) {
    print(s"  Test ${tc.id}: ${tc.query.take(30)}... ")
    val result = runAgent(tc.query, toolDefs)

    val calledTool = result.toolsCalled.headOption.map(_.takeWhile(_ != '(')).getOrElse("(none)")
    val correct = calledTool == tc.expectedTool
    results = results :+ (tc.id, correct, calledTool)

    println(s"${if (correct) "OK" else "MISS"} (expected: ${tc.expectedTool}, got: $calledTool)")
    if (!correct) {
      println(s"    calls: ${result.toolsCalled.mkString(", ")}")
      println(s"    answer: ${result.answer.take(100).replace("\n", " ")}")
    }
  }

  val score = results.count(_._2)
  println(s"  Score: $score/${results.size}")
  results
}

@main def run(): Unit = {
  println(s"LLM_BASE_URL = $BaseUrl")

  // ツール単体テスト
  println("\n--- Tool unit tests ---")
  println(s"  calculate: ${Arithmetic.calculate("1400000 * 0.3")}")
  println(s"  calculate: ${Arithmetic.calculate("50000 + 150000 + 400000")}")
  println(s"  findLawByKeyword: ${LawListSearch.findLawByKeyword("個人情報").split("\n").head}")

  // パターン1（簡潔）
  val results1 = runExperiment("Pattern 1: Concise descriptions", makeToolDefs(detailed = false))

  // パターン2（詳細）
  val results2 = runExperiment("Pattern 2: Detailed descriptions", makeToolDefs(detailed = true))

  // 比較サマリー
  println("\n" + "=" * 60)
  println("COMPARISON SUMMARY")
  println("=" * 60)
  println(f"  Pattern 1 (concise):  ${results1.count(_._2)}/${results1.size}")
  println(f"  Pattern 2 (detailed): ${results2.count(_._2)}/${results2.size}")

  println("\n  Per-test comparison:")
  for (i <- testCases.indices) {
    val (id, ok1, tool1) = results1(i)
    val (_, ok2, tool2) = results2(i)
    val expected = testCases(i).expectedTool
    val mark1 = if (ok1) "✓" else s"✗($tool1)"
    val mark2 = if (ok2) "✓" else s"✗($tool2)"
    println(f"    Test $id: P1=$mark1%-20s P2=$mark2%-20s expected=$expected")
  }
}

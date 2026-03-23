// Stage 2: パスA — OpenAI 互換 tool calling によるエージェント
// Superseded by: src/main/scala/agent/AgentLoop.scala
// Run: scala-cli run stages/stage2/agent_path_a.scala --main-class runTests

//> using scala 3.6
//> using dep com.softwaremill.sttp.client4::core:4.0.19
//> using dep com.softwaremill.sttp.client4::circe:4.0.19
//> using dep io.circe::circe-generic:0.14.15
//> using dep io.circe::circe-parser:0.14.15
//> using dep org.scala-lang.modules::scala-xml:2.3.0
//> using file statute_search.scala

import sttp.client4.*
import sttp.client4.circe.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.parser.{decode, parse}

val BaseUrl = sys.env.getOrElse("LLM_BASE_URL", "http://localhost:8080/v1")
val Model = sys.env.getOrElse("LLM_MODEL", "local")
val MaxToolRounds = 5

// --- ツール定義（OpenAI 形式） ---
val ToolDefs: Json = Json.arr(
  Json.obj(
    "type" -> Json.fromString("function"),
    "function" -> Json.obj(
      "name" -> Json.fromString("search_statute"),
      "description" -> Json.fromString("日本の法令の条文を検索して取得する。法令名と条番号を指定する。"),
      "parameters" -> Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "law_name" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("法令名。例: 民法, 刑法, 憲法")
          ),
          "article_number" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("条番号。数字のみ。例: 709, 199, 9")
          )
        ),
        "required" -> Json.arr(Json.fromString("law_name"), Json.fromString("article_number"))
      )
    )
  )
)

// --- ツールディスパッチ ---
def dispatchTool(name: String, args: JsonObject): String = {
  name match {
    case "search_statute" =>
      val lawName = args("law_name").flatMap(_.asString).getOrElse("")
      val articleNum = args("article_number").flatMap(_.asString).getOrElse("")
      StatuteSearch.searchStatute(lawName, articleNum)
    case other =>
      s"エラー: 未知のツール '$other'"
  }
}

// --- エージェントループ ---
def runAgent(userQuery: String): (String, List[String]) = {
  var messages = List(
    Json.obj("role" -> Json.fromString("system"), "content" -> Json.fromString(
      "あなたは日本法の条文検索ができるアシスタントです。" +
      "ユーザの質問に答えるために、必要に応じてsearch_statuteツールを使って条文を検索してください。" +
      "ツールが不要な質問にはツールを使わずに直接回答してください。"
    )),
    Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(userQuery))
  )

  var toolCallLog = List.empty[String]

  for (round <- 0 until MaxToolRounds) {
    val body = Json.obj(
      "model" -> Json.fromString(Model),
      "messages" -> Json.arr(messages*),
      "tools" -> ToolDefs,
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
    val finishReason = respJson.hcursor
      .downField("choices").downArray.downField("finish_reason").as[String].getOrElse("")

    val toolCalls = choiceMsg.hcursor.downField("tool_calls").as[List[Json]].getOrElse(Nil)

    if (toolCalls.isEmpty) {
      val content = choiceMsg.hcursor.downField("content").as[String].getOrElse("(empty)")
      return (content, toolCallLog)
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
      println(s"  [Tool call #$round] $logEntry")

      val result = dispatchTool(fnName, argsObj)

      messages = messages :+ Json.obj(
        "role" -> Json.fromString("tool"),
        "tool_call_id" -> Json.fromString(tcId),
        "content" -> Json.fromString(result)
      )
    }
  }
  ("(MAX_TOOL_ROUNDS exceeded)", toolCallLog)
}

// --- テストケース ---
case class TestCase(id: Int, query: String, expectTool: Boolean, expectArgs: Option[(String, String)])

val testCases = List(
  TestCase(1, "民法709条の条文を教えてください。", true, Some(("民法", "709"))),
  TestCase(2, "今日の天気はどうですか？", false, None),
  TestCase(3, "不法行為の損害賠償請求の根拠条文は？条文も示してください。", true, Some(("民法", "709"))),
  TestCase(4, "刑法の殺人罪は何条ですか？条文も示してください。", true, Some(("刑法", "199"))),
  TestCase(5, "民法1条と709条の両方を見せてください。", true, None),  // 2回の呼び出し
)

@main def runTests(): Unit = {
  println(s"=== Path A: OpenAI-compatible tool calling ===")
  println(s"LLM_BASE_URL = $BaseUrl")
  println()

  for (tc <- testCases) {
    println(s"--- Test ${tc.id}: ${tc.query} ---")
    val (answer, toolCalls) = runAgent(tc.query)

    val calledTool = toolCalls.nonEmpty
    val callCorrect = tc.expectTool == calledTool
    println(s"  Expected tool: ${tc.expectTool}, Called: $calledTool → ${if (callCorrect) "OK" else "MISMATCH"}")

    tc.expectArgs.foreach { case (law, art) =>
      val q = "\""
      val argsMatch = toolCalls.exists(_.contains(s"${q}${law}${q}")) && toolCalls.exists(_.contains(s"${q}${art}${q}"))
      println(s"  Expected args: ($law, $art) → ${if (argsMatch) "OK" else "MISMATCH"}")
    }

    if (tc.id == 5) {
      println(s"  Tool calls count: ${toolCalls.size} (expected: 2)")
    }

    println(s"  Answer: ${answer.take(200).replace("\n", " ")}")
    println()
  }
}

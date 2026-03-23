// Stage 2: パスB — プロンプトベース tool calling（フォールバック）
// Superseded by: src/main/scala/agent/AgentLoop.scala（パスA採用のため）
// Run: scala-cli run stages/stage2/agent_path_b.scala --main-class runTestsB

//> using scala 3.6
//> using dep com.softwaremill.sttp.client4::core:4.0.19
//> using dep com.softwaremill.sttp.client4::circe:4.0.19
//> using dep io.circe::circe-generic:0.14.15
//> using dep io.circe::circe-parser:0.14.15
//> using dep org.scala-lang.modules::scala-xml:2.3.0
//> using file statute_search.scala

import sttp.client4.*
import io.circe.*
import io.circe.syntax.*
import io.circe.parser.{decode, parse}

val BaseUrl = sys.env.getOrElse("LLM_BASE_URL", "http://localhost:8080/v1")
val Model = sys.env.getOrElse("LLM_MODEL", "local")
val MaxToolRounds = 5

val PromptSystemMsg = """|あなたは日本法の条文検索ができるアシスタントです。
  |
  |## 利用可能なツール
  |### search_statute
  |日本の法令の条文を検索して取得する。
  |パラメータ:
  |- law_name (string, 必須): 法令名。例: 民法, 刑法, 憲法
  |- article_number (string, 必須): 条番号。数字のみ。例: 709, 199, 9
  |
  |## ツールの呼び出し方
  |ツールを使いたい場合は、以下のJSON形式のみを出力してください:
  |{"tool_call":{"name":"search_statute","arguments":{"law_name":"...","article_number":"..."}}}
  |
  |ツールが不要な場合は、通常のテキストで回答してください。
  |重要: ツールを呼ぶ場合はJSON以外のテキストを含めないでください。
  |""".stripMargin

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

def parseToolCall(content: String): Option[(String, JsonObject)] = {
  // まず全体を JSON としてパース
  parse(content.trim).toOption
    .flatMap(_.hcursor.downField("tool_call").focus)
    .flatMap { tc =>
      for {
        name <- tc.hcursor.downField("name").as[String].toOption
        args <- tc.hcursor.downField("arguments").as[JsonObject].toOption
      } yield (name, args)
    }
}

def runAgentPromptBased(userQuery: String): (String, List[String]) = {
  var messages = List(
    Json.obj("role" -> Json.fromString("system"), "content" -> Json.fromString(PromptSystemMsg)),
    Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(userQuery))
  )

  var toolCallLog = List.empty[String]

  for (round <- 0 until MaxToolRounds) {
    val body = Json.obj(
      "model" -> Json.fromString(Model),
      "messages" -> Json.arr(messages*),
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
    val content = respJson.hcursor
      .downField("choices").downArray.downField("message").downField("content")
      .as[String].getOrElse("")

    parseToolCall(content) match {
      case None =>
        // ツール呼び出しではない → 最終回答
        return (content, toolCallLog)
      case Some((fnName, args)) =>
        val logEntry = s"$fnName(${args.asJson.noSpaces})"
        toolCallLog = toolCallLog :+ logEntry
        println(s"  [Tool call #$round] $logEntry")
        val result = dispatchTool(fnName, args)
        messages = messages ++ List(
          Json.obj("role" -> Json.fromString("assistant"), "content" -> Json.fromString(content)),
          Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(
            s"ツールの実行結果:\n$result\n\nこの結果を踏まえて回答してください。"
          ))
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
  TestCase(5, "民法1条と709条の両方を見せてください。", true, None),
)

@main def runTestsB(): Unit = {
  println(s"=== Path B: Prompt-based tool calling ===")
  println(s"LLM_BASE_URL = $BaseUrl")
  println()

  for (tc <- testCases) {
    println(s"--- Test ${tc.id}: ${tc.query} ---")
    val (answer, toolCalls) = runAgentPromptBased(tc.query)

    val calledTool = toolCalls.nonEmpty
    val callCorrect = tc.expectTool == calledTool
    println(s"  Expected tool: ${tc.expectTool}, Called: $calledTool → ${if (callCorrect) "OK" else "MISMATCH"}")

    tc.expectArgs.foreach { case (law, art) =>
      val argsMatch = toolCalls.exists(_.contains(law)) && toolCalls.exists(_.contains(art))
      println(s"  Expected args: ($law, $art) → ${if (argsMatch) "OK" else "MISMATCH"}")
    }

    if (tc.id == 5) {
      println(s"  Tool calls count: ${toolCalls.size} (expected: 2)")
    }

    println(s"  Answer: ${answer.take(200).replace("\n", " ")}")
    println()
  }
}

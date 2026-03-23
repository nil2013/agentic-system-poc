package agent

import messages.*
import tools.ToolDispatch
import io.circe.*
import io.circe.syntax.*
import io.circe.parser.parse
import sttp.client4.*

case class AgentConfig(
    baseUrl: String = sys.env.getOrElse("LLM_BASE_URL", "http://localhost:8080/v1"),
    model: String = sys.env.getOrElse("LLM_MODEL", "local"),
    maxTokens: Int = 4096,
    maxToolRounds: Int = 5,
    systemPrompt: String = "あなたは日本法に関するアシスタントです。" +
      "ユーザの質問に答えるために、必要に応じてツールを使ってください。" +
      "ツールが不要な質問にはツールを使わずに直接回答してください。"
)

case class TurnResult(
    response: ChatMessage.Assistant,
    toolCalls: List[String],   // ログ用: "toolName(args)" 形式
    totalTokens: Int
)

object AgentLoop {

  def runTurn(
      messages: List[ChatMessage],
      config: AgentConfig
  ): (TurnResult, List[ChatMessage]) = {
    var currentMessages = messages
    var toolCallLog = List.empty[String]
    var totalTokens = 0

    for (round <- 0 until config.maxToolRounds) {
      val body = Json.obj(
        "model" -> Json.fromString(config.model),
        "messages" -> Json.arr(currentMessages.map(ChatMessage.toJson)*),
        "tools" -> ToolDispatch.toolDefs,
        "max_tokens" -> Json.fromInt(config.maxTokens),
        "temperature" -> Json.fromDoubleOrNull(0.0)
      )

      val backend = DefaultSyncBackend()
      val resp = basicRequest
        .post(uri"${config.baseUrl}/chat/completions")
        .contentType("application/json")
        .body(body.noSpaces)
        .response(asString)
        .readTimeout(scala.concurrent.duration.Duration(120, "s"))
        .send(backend)
      backend.close()

      val respJson = parse(resp.body.getOrElse("{}")).getOrElse(Json.Null)
      val choiceMsg = respJson.hcursor
        .downField("choices").downArray.downField("message").focus.getOrElse(Json.Null)
      val usage = respJson.hcursor.downField("usage").downField("total_tokens").as[Int].getOrElse(0)
      totalTokens += usage

      // レスポンスを ChatMessage.Assistant に変換
      val assistantMsg: ChatMessage.Assistant = ChatMessage.fromJson(choiceMsg) match {
        case Some(a: ChatMessage.Assistant) => a
        case _ => ChatMessage.Assistant(
          choiceMsg.hcursor.downField("content").as[String].toOption,
          Nil
        )
      }

      if (assistantMsg.toolCalls.isEmpty) {
        // ツール呼び出しなし → 最終回答
        currentMessages = currentMessages :+ assistantMsg
        val result = TurnResult(assistantMsg, toolCallLog, totalTokens)
        return (result, currentMessages)
      }

      // ツール呼び出し実行
      currentMessages = currentMessages :+ assistantMsg
      for (tc <- assistantMsg.toolCalls) {
        val logEntry = s"${tc.name}(${tc.arguments.asJson.noSpaces})"
        toolCallLog = toolCallLog :+ logEntry
        println(s"  [Tool call #$round] $logEntry")

        val result = ToolDispatch.dispatch(tc.name, tc.arguments)
        currentMessages = currentMessages :+ ChatMessage.ToolResult(tc.id, result)
      }
    }

    val fallback: ChatMessage.Assistant = ChatMessage.Assistant(Some("(MAX_TOOL_ROUNDS exceeded)"), Nil)
    currentMessages = currentMessages :+ fallback
    (TurnResult(fallback, toolCallLog, totalTokens), currentMessages)
  }
}

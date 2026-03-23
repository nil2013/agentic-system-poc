package stages

import agent.*
import messages.*
import java.nio.file.Paths

/** Stage 4: 複数ターン対話 + コンテキスト管理の体験 */
object Stage4Main {

  val config = AgentConfig()

  val queries = List(
    "民法709条の条文を教えてください。",
    "この条文の要件を簡潔に整理してください。",
    "刑法199条の条文も見せてください。",
    "民法709条と刑法199条の違いを説明してください。",
    "個人情報に関する法律にはどんなものがありますか？",
    "個人情報保護法の第1条を見せてください。",
    "研究費100万円の消費税10%込みの金額はいくらですか？",
    "ここまでの会話を要約してください。",
    "民法1条の条文を教えてください。",
    "民法1条と709条を比較して、それぞれの役割の違いを述べてください。",
  )

  def main(args: Array[String]): Unit = {
    println(s"=== Stage 4: Multi-turn Conversation ===")
    println(s"LLM_BASE_URL = ${config.baseUrl}")
    println(s"Queries: ${queries.size} turns")
    println()

    val state = new ConversationState("stage4-test")
    state.add(ChatMessage.System(config.systemPrompt))

    val logFile = sys.env.getOrElse("STAGE4_LOG", "stages/stage4/conversation-log.md")
    val logger = new ConversationLogger(Paths.get(logFile))
    logger.header("Stage 4: Multi-turn Conversation Log", config)

    for ((query, i) <- queries.zipWithIndex) {
      val turnNum = i + 1
      println(s"--- Turn $turnNum: ${query.take(40)} ---")

      // ユーザーメッセージ追加
      state.add(ChatMessage.User(query))
      logger.turnStart(turnNum, query)

      // エージェント実行
      val (result, updatedMessages) = AgentLoop.runTurn(state.messages, config)

      // ツール呼び出しログを記録
      val newMessages = updatedMessages.drop(state.messageCount)
      // ToolResult と対応する tool call を紐付けて記録
      var toolCallIdx = 0
      for (msg <- newMessages) {
        msg match {
          case ChatMessage.ToolResult(_, content) =>
            val callLog = result.toolCalls.lift(toolCallIdx).getOrElse("unknown")
            val name = callLog.takeWhile(_ != '(')
            val args = callLog.drop(name.length)
            logger.toolCall(toolCallIdx, name, args, content)
            toolCallIdx += 1
          case _ => ()
        }
      }

      state.addAll(newMessages)

      // ログ記録
      val answer = result.response.content.getOrElse("(empty)")
      logger.assistantResponse(answer, result.totalTokens, state.estimateTokens, state.messageCount)

      // stdout
      println(s"  Answer: ${answer.take(150).replace("\n", " ")}")
      println(s"  Tool calls: ${result.toolCalls.mkString(", ").take(80)}")
      println(s"  API tokens this turn: ${result.totalTokens}")
      println(s"  State: ${state.messageCount} messages, ~${state.estimateTokens} est. chars")
      println()

      state.save()
    }

    // サマリー
    logger.summary(state.messageCount, state.estimateTokens)
    logger.save()

    println("=" * 60)
    println("CONTEXT GROWTH SUMMARY")
    println("=" * 60)
    println(s"  Total messages: ${state.messageCount}")
    println(s"  Estimated chars: ${state.estimateTokens}")
    println(s"  Session saved to: sessions/stage4-test.json")
    println(s"  Conversation log: stages/stage4/conversation-log.md")

    // truncation テスト
    println()
    println("--- Truncation test (limit: 3000 chars) ---")
    val beforeCount = state.messageCount
    val beforeTokens = state.estimateTokens
    val removed = state.truncateIfNeeded(maxTokens = 3000)
    val afterCount = state.messageCount
    val afterTokens = state.estimateTokens
    println(s"  Before: $beforeCount messages, ~$beforeTokens chars")
    println(s"  After:  $afterCount messages, ~$afterTokens chars")
    println(s"  Removed: ${removed.size} messages")
  }
}

package stages

import agent.*
import messages.*

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

    for ((query, i) <- queries.zipWithIndex) {
      val turnNum = i + 1
      println(s"--- Turn $turnNum: ${query.take(40)} ---")

      // ユーザーメッセージ追加
      state.add(ChatMessage.User(query))

      // エージェント実行
      val (result, updatedMessages) = AgentLoop.runTurn(state.messages, config)

      // 状態を更新（AgentLoop が返した全メッセージで上書き）
      // ただし ConversationState は差分追加の方が効率的なので、
      // エージェントが追加した分（assistant + tool results）だけ追加
      val newMessages = updatedMessages.drop(state.messageCount)
      state.addAll(newMessages)

      // 推移ログ
      val estTokens = state.estimateTokens
      val msgCount = state.messageCount
      println(s"  Answer: ${result.response.content.getOrElse("(empty)").take(150).replace("\n", " ")}")
      println(s"  Tool calls: ${result.toolCalls.mkString(", ").take(80)}")
      println(s"  API tokens this turn: ${result.totalTokens}")
      println(s"  State: $msgCount messages, ~$estTokens est. chars")
      println()

      // セッション保存
      state.save()
    }

    // サマリー
    println("=" * 60)
    println("CONTEXT GROWTH SUMMARY")
    println("=" * 60)
    println(s"  Total messages: ${state.messageCount}")
    println(s"  Estimated chars: ${state.estimateTokens}")
    println(s"  Session saved to: sessions/stage4-test.json")

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

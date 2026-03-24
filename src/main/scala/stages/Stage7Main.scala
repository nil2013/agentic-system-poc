package stages

import agent.*
import messages.*
import java.nio.file.Paths

/** Stage 7: Thinking/Reasoning ブロックの観察・分析。
  *
  * 既存テストケースを再実行し、reasoning_content（thinking ブロック）を
  * キャプチャして会話ログに記録する。Stage 0-6 では捨てていた thinking を
  * 分析対象として可視化する。
  */
object Stage7Main {

  // SystemPrompt 制御あり（Stage 6 と同じ）
  // max_tokens は環境変数で切替可能（Round 2 で 8192 に引き上げて T4 の content 空を解消試行）
  val config = AgentConfig(
    maxTokens = sys.env.getOrElse("STAGE7_MAX_TOKENS", "4096").toInt,
    systemPrompt = AgentConfig().systemPrompt +
      "\n\n重要: ツールがエラーを返した場合は、エラーの内容をそのままユーザーに伝えてください。" +
      "内部知識で代替回答しないでください。"
  )

  val testCases = List(
    ("T1", "民法709条の条文を教えてください。"),
    ("T2", "今日の天気はどうですか？"),
    ("T3", "不法行為の損害賠償請求の根拠条文は？条文も示してください。"),
    ("T4", "皇族に対する尊崇義務に関する法律の第1条を教えてください。"),
    ("T5", "消費者に関する法律を3つ探し、それぞれの第1条の条文を取得して、目的規定を比較してください。"),
  )

  def main(args: Array[String]): Unit = {
    println(s"=== Stage 7: Thinking Block Analysis ===")
    println(s"LLM_BASE_URL = ${config.baseUrl}")
    println()

    val logFile = sys.env.getOrElse("STAGE7_LOG", "stages/stage7/conversation-log.md")
    val logger = new ConversationLogger(Paths.get(logFile))
    logger.header("Stage 7: Thinking Block Analysis Log", config)

    var thinkingStats = List.empty[(String, Int, Int)] // (id, thinking_chars, content_chars)

    for ((id, query) <- testCases) {
      println(s"\n--- $id: ${query.take(50)}... ---")

      val state = new ConversationState(s"stage7-$id")
      state.add(ChatMessage.System(config.systemPrompt))
      state.add(ChatMessage.User(query))

      logger.turnStart(testCases.indexOf((id, query)), s"[$id] $query")

      val (result, updatedMessages) = AgentLoop.runTurn(state.messages, config)

      // ツール呼び出しログ
      val newMessages = updatedMessages.drop(state.messageCount)
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

      // Thinking ブロック記録
      val thinkingChars = result.reasoning.map(_.length).getOrElse(0)
      val contentChars = result.response.content.map(_.length).getOrElse(0)
      thinkingStats = thinkingStats :+ (id, thinkingChars, contentChars)

      result.reasoning.foreach { r =>
        logger.thinkingBlock(r)
        println(s"  Thinking: ${r.take(120).replace("\n", " ")}...")
        println(s"  Thinking chars: $thinkingChars")
      }

      val answer = result.response.content.getOrElse("(empty)")
      logger.assistantResponse(answer, result.totalTokens, 0, updatedMessages.size)

      println(s"  Content chars: $contentChars")
      println(s"  Tool calls: ${result.toolCalls.mkString(", ").take(80)}")
      println(s"  Tokens: ${result.totalTokens}")
      println(s"  Answer: ${answer.take(150).replace("\n", " ")}")
    }

    // サマリー
    println("\n" + "=" * 60)
    println("THINKING BLOCK STATISTICS")
    println("=" * 60)
    println(f"${"ID"}%-5s ${"Thinking"}%10s ${"Content"}%10s ${"Ratio"}%8s")
    for ((id, tc, cc) <- thinkingStats) {
      val ratio = if (tc + cc > 0) f"${tc.toDouble / (tc + cc) * 100}%.0f%%" else "N/A"
      println(f"$id%-5s $tc%10d $cc%10d $ratio%8s")
    }

    val totalThinking = thinkingStats.map(_._2).sum
    val totalContent = thinkingStats.map(_._3).sum
    val avgRatio = if (totalThinking + totalContent > 0) {
      f"${totalThinking.toDouble / (totalThinking + totalContent) * 100}%.0f%%"
    } else "N/A"
    println(f"${"TOTAL"}%-5s $totalThinking%10d $totalContent%10d $avgRatio%8s")

    logger.summary(0, 0)
    logger.save()
    println(s"\nLog: $logFile")
  }
}

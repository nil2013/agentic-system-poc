package agent

import messages.*
import java.nio.file.{Files, Path}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 会話ログを markdown 形式で記録する。 */
class ConversationLogger(outputPath: Path) {

  private val sb = new StringBuilder
  private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

  def header(title: String, config: AgentConfig): Unit = {
    sb.append(s"# $title\n\n")
    sb.append(s"> Generated: ${LocalDateTime.now()}\n")
    sb.append(s"> Model: ${config.model}\n")
    sb.append(s"> Base URL: ${config.baseUrl}\n")
    sb.append(s"> max_tokens: ${config.maxTokens}\n\n")
    sb.append("---\n\n")
  }

  def turnStart(turnNum: Int, query: String): Unit = {
    val time = LocalDateTime.now().format(timeFormatter)
    sb.append(s"## Turn $turnNum — $time\n\n")
    sb.append(s"**User**: $query\n\n")
  }

  def toolCall(index: Int, name: String, args: String, resultSummary: String): Unit = {
    sb.append(s"  - **Tool**: `$name$args`\n")
    sb.append(s"    - Result: ${resultSummary.take(150).replace("\n", " ")}\n")
  }

  def assistantResponse(content: String, totalTokens: Int, estChars: Int, msgCount: Int): Unit = {
    val truncated = content.take(300).replace("\n", " ")
    sb.append(s"\n**Assistant**: $truncated\n\n")
    sb.append(s"*[tokens: $totalTokens, messages: $msgCount, est. chars: $estChars]*\n\n")
    sb.append("---\n\n")
  }

  def summary(totalMessages: Int, estChars: Int): Unit = {
    sb.append(s"## Summary\n\n")
    sb.append(s"- Total messages: $totalMessages\n")
    sb.append(s"- Estimated chars: $estChars\n")
  }

  def save(): Unit = {
    Files.createDirectories(outputPath.getParent)
    Files.writeString(outputPath, sb.toString)
  }
}

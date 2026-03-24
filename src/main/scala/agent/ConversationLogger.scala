package agent

import messages.*
import java.nio.file.{Files, Path}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 会話ログを Markdown 形式で記録する。
  *
  * [[agent.AgentLoop]] の実行結果を人間が読める形式で保存する。
  * `stages/PROTOCOL.md` §4.1.1 に従い、RESULTS.md の Discussion セクションで
  * 会話品質の定性的分析に使用される。
  *
  * == 設計 ==
  *  - '''Append-only''': 内部の `StringBuilder` に追記していき、`save()` で一度だけ書き出す
  *  - '''[[ConversationState]] とは独立''': 永続化は ConversationState が JSON で行い、
  *    本クラスは分析用の Markdown ログのみを担当する
  *
  * == 出力フォーマット ==
  * {{{
  * # タイトル
  * > Generated: 2026-03-23T16:18:39
  * > Model: local
  * ---
  * ## Turn 1 — 16:18:39
  * **User**: 質問
  *   - **Tool**: `tool_name(args)`
  *     - Result: ツール結果の要約
  * **Assistant**: 回答（300文字まで）
  * *[tokens: 1434, messages: 5, est. chars: 646]*
  * ---
  * }}}
  *
  * @param outputPath ログファイルの出力先パス
  */
class ConversationLogger(outputPath: Path) {

  private val sb = new StringBuilder
  private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

  /** ログファイルのヘッダーを書き込む。最初に1回だけ呼ぶ。 */
  def header(title: String, config: AgentConfig): Unit = {
    sb.append(s"# $title\n\n")
    sb.append(s"> Generated: ${LocalDateTime.now()}\n")
    sb.append(s"> Model: ${config.model}\n")
    sb.append(s"> Base URL: ${config.baseUrl}\n")
    sb.append(s"> max_tokens: ${config.maxTokens}\n\n")
    sb.append("---\n\n")
  }

  /** ターンの開始を記録する（ユーザーの質問）。 */
  def turnStart(turnNum: Int, query: String): Unit = {
    val time = LocalDateTime.now().format(timeFormatter)
    sb.append(s"## Turn $turnNum — $time\n\n")
    sb.append(s"**User**: $query\n\n")
  }

  /** ツール呼び出しとその結果を記録する。
    *
    * @param index         ツール呼び出しのインデックス（0始まり）
    * @param name          ツール名
    * @param args          引数（JSON 文字列フォーマット）
    * @param resultSummary ツール結果の先頭150文字
    */
  def toolCall(index: Int, name: String, args: String, resultSummary: String): Unit = {
    sb.append(s"  - **Tool**: `$name$args`\n")
    sb.append(s"    - Result: ${resultSummary.take(150).replace("\n", " ")}\n")
  }

  /** アシスタントの回答とメトリクスを記録する。 */
  def assistantResponse(content: String, totalTokens: Int, estChars: Int, msgCount: Int): Unit = {
    val truncated = content.take(300).replace("\n", " ")
    sb.append(s"\n**Assistant**: $truncated\n\n")
    sb.append(s"*[tokens: $totalTokens, messages: $msgCount, est. chars: $estChars]*\n\n")
    sb.append("---\n\n")
  }

  /** thinking ブロックを記録する（Stage 7 の分析用）。
    *
    * GitHub markdown の details/summary で折りたたみ表示にする。
    */
  def thinkingBlock(reasoning: String): Unit = {
    val charCount = reasoning.length
    sb.append(s"\n<details><summary>Thinking ($charCount chars)</summary>\n\n")
    sb.append(s"${reasoning.take(2000)}\n")
    if (reasoning.length > 2000) {
      sb.append(s"\n*... (truncated, total $charCount chars)*\n")
    }
    sb.append(s"\n</details>\n\n")
  }

  /** セッション全体のサマリーを記録する。 */
  def summary(totalMessages: Int, estChars: Int): Unit = {
    sb.append(s"## Summary\n\n")
    sb.append(s"- Total messages: $totalMessages\n")
    sb.append(s"- Estimated chars: $estChars\n")
  }

  /** 蓄積したログをファイルに書き出す。ディレクトリがなければ作成する。 */
  def save(): Unit = {
    Files.createDirectories(outputPath.getParent)
    Files.writeString(outputPath, sb.toString)
  }
}

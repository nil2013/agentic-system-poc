package stages

import agent.*
import messages.*
import sttp.client4.*
import java.nio.file.Paths
import org.jline.reader.{LineReaderBuilder, EndOfFileException, UserInterruptException}
import org.jline.terminal.TerminalBuilder

/** Stage 8: 対話的 REPL。Stage 0-7 の全コンポーネントを統合。
  *
  * == 実行方法 ==
  * {{{
  * sbt "runMain stages.Stage8Main"              // デフォルトセッション
  * sbt "runMain stages.Stage8Main my-session"   // セッション指定
  * }}}
  *
  * == コマンド ==
  * `/quit`, `/exit` — 終了 / `/think` — thinking 表示切替
  * `/session [id]` — セッション切替 / `/history` — 履歴サマリー
  * `/save [path]` — 会話ログ保存 / `/help` — ヘルプ
  */
object Stage8Main {

  var showThinking = false
  var turnNum = 0

  /** 簡易引数パーサー。`--key value` 形式と位置引数（セッションID）を受け付ける。 */
  def parseArgs(args: Array[String]): Map[String, String] = {
    var result = Map.empty[String, String]
    var i = 0
    while (i < args.length) {
      if (args(i).startsWith("--") && i + 1 < args.length) {
        result = result + (args(i).stripPrefix("--") -> args(i + 1))
        i += 2
      } else {
        result = result + ("session" -> args(i))
        i += 1
      }
    }
    result
  }

  def main(args: Array[String]): Unit = {
    val opts = parseArgs(args)
    val sessionId = opts.getOrElse("session", "repl-default")

    val egovVersion = opts.getOrElse("egov-api", sys.env.getOrElse("EGOV_API_VERSION", "v1"))
    val backend = tools.egov.EGovBackendFactory.create(egovVersion)
    val toolDispatch = tools.ToolDispatch.forBackend(backend)
    val capPrompt = Prompts.capabilityNotice(backend.capabilities)

    val config = AgentConfig(
      baseUrl = opts.getOrElse("url", sys.env.getOrElse("LLM_BASE_URL", "http://localhost:8080/v1")),
      model = opts.getOrElse("model", sys.env.getOrElse("LLM_MODEL", "local")),
      promptSections = List(Prompts.Role, Prompts.FallbackControl, capPrompt, Prompts.TagHandling),
      toolDispatch = toolDispatch
    )

    println("=== Agentic System REPL ===")
    println(s"Model: ${config.model}")
    println(s"Base URL: ${config.baseUrl}")
    println(s"e-Gov API: $egovVersion")
    println(s"Max tool rounds: ${config.maxToolRounds}")
    println(s"Session: $sessionId")
    println()

    // ヘルスチェック
    if (!healthCheck(config.baseUrl)) {
      println("Error: LLM サーバーに接続できません。LLM_BASE_URL を確認してください。")
      return
    }
    println("LLM サーバー接続OK")
    println()

    // セッション初期化
    var state = new ConversationState(sessionId)
    if (state.messageCount == 0) {
      state.add(ChatMessage.System(config.systemPrompt))
      println(s"新規セッション: $sessionId")
    } else {
      println(s"セッション復元: $sessionId (${state.messageCount} messages, ~${state.estimateTokens} chars)")
    }

    println("'/help' でコマンド一覧を表示。'/quit' で終了。")
    println()

    // JLine ターミナル + LineReader（マルチバイト対応の行編集）
    val terminal = TerminalBuilder.builder().system(true).build()
    val reader = LineReaderBuilder.builder().terminal(terminal).build()

    // REPL ループ
    var running = true
    while (running) {
      val line = try {
        reader.readLine("You> ")
      } catch {
        case _: EndOfFileException => null          // Ctrl+D
        case _: UserInterruptException => null      // Ctrl+C
      }

      if (line == null) {
        println()
        saveAndExit(state)
        terminal.close()
        running = false
      } else if (line.trim.isEmpty) {
        // 空行 → スキップ
      } else if (line.trim.startsWith("/")) {
        // コマンド処理
        val result = handleCommand(line.trim, state, config)
        result match {
          case CommandResult.Continue(newState) =>
            state = newState
          case CommandResult.Exit =>
            saveAndExit(state)
            running = false
        }
      } else {
        // 通常クエリ → AgentLoop
        turnNum += 1
        state.add(ChatMessage.User(line))

        val (result, updatedMessages) = AgentLoop.runTurn(state.messages, config)

        // 新メッセージを state に追加
        val newMessages = updatedMessages.drop(state.messageCount)
        state.addAll(newMessages)

        // Thinking 表示
        if (showThinking) {
          result.reasoning.foreach { r =>
            println(s"  [Thinking] ${r.take(300).replace("\n", " ")}")
          }
        }

        // 回答表示
        val answer = result.response.content.getOrElse("(empty)")
        println(s"\nAI> $answer\n")

        // 保存
        state.save()
      }
    }
  }

  // --- コマンド処理 ---

  sealed trait CommandResult
  object CommandResult {
    case class Continue(state: ConversationState) extends CommandResult
    case object Exit extends CommandResult
  }

  def handleCommand(cmd: String, state: ConversationState, config: AgentConfig): CommandResult = {
    val parts = cmd.split("\\s+", 2)
    val command = parts(0).toLowerCase
    val arg = parts.lift(1).getOrElse("")

    command match {
      case "/quit" | "/exit" =>
        CommandResult.Exit

      case "/think" =>
        showThinking = !showThinking
        println(s"  Thinking 表示: ${if (showThinking) "ON" else "OFF"}")
        CommandResult.Continue(state)

      case "/session" =>
        if (arg.isEmpty) {
          println(s"  現在のセッション: messages=${state.messageCount}, ~${state.estimateTokens} chars")
          CommandResult.Continue(state)
        } else {
          state.save()
          println(s"  セッション保存: ${state}")
          val newState = new ConversationState(arg)
          if (newState.messageCount == 0) {
            newState.add(ChatMessage.System(config.systemPrompt))
            println(s"  新規セッション: $arg")
          } else {
            println(s"  セッション復元: $arg (${newState.messageCount} messages)")
          }
          turnNum = 0
          CommandResult.Continue(newState)
        }

      case "/history" =>
        println(s"  Messages: ${state.messageCount}")
        println(s"  Est. chars: ${state.estimateTokens}")
        println(s"  Turns: $turnNum")
        CommandResult.Continue(state)

      case "/save" =>
        val path = if (arg.nonEmpty) arg else s"stages/stage8/conversation-log-${System.currentTimeMillis / 1000}.md"
        val logger = new ConversationLogger(Paths.get(path))
        logger.header("REPL Session Log", config)

        // 現在の会話履歴をログに変換
        var logTurn = 0
        var i = 0
        while (i < state.messages.size) {
          state.messages(i) match {
            case ChatMessage.User(content) =>
              logTurn += 1
              logger.turnStart(logTurn, content)
            case ChatMessage.Assistant(content, _, reasoning) =>
              reasoning.foreach(r => logger.thinkingBlock(r))
              logger.assistantResponse(content.getOrElse(""), 0, state.estimateTokens, state.messageCount)
            case ChatMessage.ToolResult(_, content) =>
              logger.toolCall(0, "tool", "", content)
            case _ => ()
          }
          i += 1
        }

        logger.summary(state.messageCount, state.estimateTokens)
        logger.save()
        println(s"  会話ログ保存: $path")
        CommandResult.Continue(state)

      case "/help" =>
        println("  /quit, /exit  — セッション保存して終了")
        println("  /think        — Thinking ブロック表示の ON/OFF")
        println("  /session [id] — セッション情報表示 / 切替")
        println("  /history      — 履歴サマリー")
        println("  /save [path]  — 会話ログを Markdown 保存")
        println("  /help         — このヘルプ")
        CommandResult.Continue(state)

      case _ =>
        println(s"  不明なコマンド: $command ('/help' でヘルプ)")
        CommandResult.Continue(state)
    }
  }

  // --- ヘルスチェック ---

  def healthCheck(baseUrl: String): Boolean = {
    try {
      val healthUrl = baseUrl.stripSuffix("/v1") + "/health"
      val backend = DefaultSyncBackend()
      val resp = basicRequest
        .get(sttp.model.Uri.unsafeParse(healthUrl))
        .response(asString)
        .readTimeout(scala.concurrent.duration.Duration(5, "s"))
        .send(backend)
      backend.close()
      resp.code.isSuccess
    } catch {
      case _: Exception => false
    }
  }

  def saveAndExit(state: ConversationState): Unit = {
    state.save()
    println(s"セッション保存完了 (${state.messageCount} messages)")
    println("Bye!")
  }
}

package agent

import messages.*
import tools.{ToolDispatch, Arithmetic}
import io.circe.*
import io.circe.syntax.*

/** エージェントの動作パラメータ。
  *
  * @param baseUrl        LLM API のベース URL。環境変数 `LLM_BASE_URL` から取得。
  * @param model          モデル名。環境変数 `LLM_MODEL` から取得。
  * @param maxTokens      1回の API 呼び出しの最大トークン数。4096 以上を推奨（thinking mode 対策）。
  * @param maxToolRounds  ツール呼び出しラウンドの上限。無限ループ防止ガード。
  * @param temperature    生成時の temperature。0.0 で確定的出力。
  * @param timeoutSeconds API 呼び出しのタイムアウト秒数。
  * @param promptSections SystemPrompt のセクションリスト。[[Prompts]] の定数を組み合わせる。
  *                       `def systemPrompt` で `"\n\n"` 区切りの文字列に結合される。
  */
case class AgentConfig(
    baseUrl: String = sys.env.getOrElse("LLM_BASE_URL", "http://localhost:8080/v1"),
    model: String = sys.env.getOrElse("LLM_MODEL", "local"),
    maxTokens: Int = 16384,
    maxToolRounds: Int = 15,
    temperature: Double = 0.0,
    timeoutSeconds: Int = 120,
    promptSections: List[String] = List(Prompts.Role),
    toolDispatch: ToolDispatch = ToolDispatch.defaultV1
) {
  /** プロンプトセクションを結合したシステムプロンプト文字列。 */
  def systemPrompt: String = promptSections.mkString("\n\n")
}

/** [[AgentLoop.runTurn]] の実行結果。
  *
  * @param response    LLM の最終回答（`toolCalls` が空の `Assistant` メッセージ）
  * @param toolCalls   このターンで実行されたツール呼び出しのログ。
  *                    フォーマット: `"toolName({json args})"` の文字列リスト。
  *                    実行可能なデータではなく、記録・表示用。
  * @param totalTokens このターンの全 API 呼び出しで消費されたトークン合計
  *                    （`usage.total_tokens` の累計）。ツール呼び出しが複数ラウンドに
  *                    わたる場合、各ラウンドの消費を合算。
  */
case class TurnResult(
    response: ChatMessage.Assistant,
    toolCalls: List[String],
    totalTokens: Int,
    reasoning: Option[String] = None
)

/** ReAct パターンのエージェントループ。
  *
  * == ループ構造 ==
  * {{{
  * 1. メッセージ履歴 + ツール定義を LLM に送信
  * 2. レスポンスに tool_calls が含まれるか？
  *    - No  → 最終回答として返す（ループ終了）
  *    - Yes → 各ツールを ToolDispatch で実行し、結果を ToolResult として履歴に追加 → 1. に戻る
  * 3. maxToolRounds に達したらフォールバック回答を返す
  * }}}
  *
  * == 契約 ==
  *  - '''入力''': メッセージ履歴（`System` + `User` + 過去の `Assistant`/`ToolResult`）+ 設定
  *  - '''出力''': `(TurnResult, 更新されたメッセージ履歴)`。
  *    返されるメッセージ履歴には、このターンの全中間メッセージ（ツール呼び出し + 結果）+
  *    最終回答が追加されている。
  *
  * == 既知の制限 ==
  *  - '''ストリーミング非対応''': レスポンス全体を待ってから処理
  *  - '''ツール並行実行非対応''': LLM が parallel tool_calls を返しても逐次実行
  *  - '''エラー回復なし''': ツール実行の例外は伝播する（catch しない）
  *  - '''リクエストごとに新規 HTTP バックエンド''': 接続プーリングなし
  *  - '''`reasoning_content` の破棄''': Qwen3.5 の thinking ブロックは現在捨てている
  *    （Stage 7 でキャプチャ予定、`stages/stage7/PLAN.md` 参照）
  *
  * @see [[ConversationState]] 複数ターンにわたるメッセージ履歴の管理
  * @see [[tools.ToolDispatch]] ツール定義とディスパッチ
  */
object AgentLoop {

  /** 1ターン分のエージェントループを実行する。
    *
    * @param messages 現在のメッセージ履歴（`System` + `User` + 過去のやり取り）
    * @param config   エージェント設定
    * @return `(ターン結果, 更新されたメッセージ履歴)`
    */
  def runTurn(
      messages: List[ChatMessage],
      config: AgentConfig
  ): (TurnResult, List[ChatMessage]) = {
    var currentMessages = messages
    var toolCallLog = List.empty[String]
    var allReasoning = List.empty[String]  // 全ラウンドの reasoning を収集
    var totalTokens = 0

    for (round <- 0 until config.maxToolRounds) {
      val spinner = new Spinner()
      spinner.start()
      val llmResp = try {
        LlmClient.chatCompletion(currentMessages, config, tools = Some(config.toolDispatch.toolDefs))
      } finally {
        spinner.stop()
      }
      totalTokens += llmResp.totalTokens

      val assistantMsg: ChatMessage.Assistant = ChatMessage.fromJson(llmResp.message) match {
        case Some(a: ChatMessage.Assistant) => a
        case _ => ChatMessage.Assistant(
          llmResp.message.hcursor.downField("content").as[String].toOption,
          Nil
        )
      }

      // 全ラウンドの reasoning を収集（ツール選択の推論過程を含む）
      assistantMsg.reasoning.foreach(r => allReasoning = allReasoning :+ r)

      if (assistantMsg.toolCalls.isEmpty) {
        currentMessages = currentMessages :+ assistantMsg
        // 全ラウンドの reasoning を結合して TurnResult に含める
        val combinedReasoning = if (allReasoning.nonEmpty) Some(allReasoning.mkString("\n---\n")) else None
        val result = TurnResult(assistantMsg, toolCallLog, totalTokens, combinedReasoning)
        return (result, currentMessages)
      }

      currentMessages = currentMessages :+ assistantMsg
      for (tc <- assistantMsg.toolCalls) {
        val logEntry = s"${tc.name}(${tc.arguments.asJson.noSpaces})"
        toolCallLog = toolCallLog :+ logEntry
        println(s"  [Tool call #$round] $logEntry")

        val result = config.toolDispatch.dispatch(tc.name, tc.arguments)
        currentMessages = currentMessages :+ ChatMessage.ToolResult(tc.id, result)
      }
    }

    val fallback: ChatMessage.Assistant = ChatMessage.Assistant(
      Some(s"(ツール呼び出し上限 ${config.maxToolRounds} 回に達しました。質問を分割して再試行してください。)"),
      Nil
    )
    currentMessages = currentMessages :+ fallback
    val combinedReasoning = if (allReasoning.nonEmpty) Some(allReasoning.mkString("\n---\n")) else None
    (TurnResult(fallback, toolCallLog, totalTokens, combinedReasoning), currentMessages)
  }
}

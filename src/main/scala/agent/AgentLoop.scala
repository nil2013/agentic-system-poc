package agent

import messages.*
import tools.ToolDispatch
import io.circe.*
import io.circe.syntax.*
import io.circe.parser.parse
import sttp.client4.*

/** エージェントの動作パラメータ。
  *
  * @param baseUrl       LLM API のベース URL。環境変数 `LLM_BASE_URL` から取得。
  *                      OpenAI 互換 API（llama-server, mlx-lm, OpenAI API）を想定。
  * @param model         モデル名。環境変数 `LLM_MODEL` から取得。
  * @param maxTokens     1回の API 呼び出しの最大トークン数。Qwen3.5 の thinking mode は
  *                      ~1500-2000 tokens を消費するため、4096 以上を推奨。
  * @param maxToolRounds ツール呼び出しラウンドの上限。LLM がツールを繰り返し呼び続ける
  *                      無限ループを防ぐガード。通常の対話では 2-3 ラウンドで収束する。
  * @param systemPrompt  システムプロンプト。LLM の役割とツール利用の指針を定義する。
  */
case class AgentConfig(
    baseUrl: String = sys.env.getOrElse("LLM_BASE_URL", "http://localhost:8080/v1"),
    model: String = sys.env.getOrElse("LLM_MODEL", "local"),
    maxTokens: Int = 4096,
    maxToolRounds: Int = 5,
    systemPrompt: String = "あなたは日本法に関するアシスタントです。" +
      "ユーザの質問に答えるために、必要に応じてツールを使ってください。" +
      "ツールが不要な質問にはツールを使わずに直接回答してください。"
)

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

      val assistantMsg: ChatMessage.Assistant = ChatMessage.fromJson(choiceMsg) match {
        case Some(a: ChatMessage.Assistant) => a
        case _ => ChatMessage.Assistant(
          choiceMsg.hcursor.downField("content").as[String].toOption,
          Nil
        )
      }

      if (assistantMsg.toolCalls.isEmpty) {
        currentMessages = currentMessages :+ assistantMsg
        val result = TurnResult(assistantMsg, toolCallLog, totalTokens, assistantMsg.reasoning)
        return (result, currentMessages)
      }

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

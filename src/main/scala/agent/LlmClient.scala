package agent

import messages.*
import io.circe.*
import io.circe.syntax.*
import io.circe.parser.parse
import sttp.client4.*

/** LLM API レスポンスの軽量ラッパー。 */
case class LlmResponse(
    /** `choices[0].message` の JSON（ChatMessage.fromJson で変換可能） */
    message: Json,
    /** `usage.total_tokens` */
    totalTokens: Int
)

/** OpenAI Chat Completions API 互換の共有 HTTP クライアント。
  *
  * AgentLoop、Stage5 の計画生成、Stage6 の evaluator など、
  * LLM API を呼び出す全箇所がこのクライアントを経由する。
  * HTTP リクエスト構築・レスポンスパースの重複を排除する。
  *
  * == 設計判断 ==
  *  - '''同期・ブロッキング''': LLM 推論がボトルネックのため非同期化の利点が薄い
  *  - '''リクエストごとに新規バックエンド''': 接続プーリングなし（PoC では十分）
  *  - '''tools パラメータは Optional''': AgentLoop は tools あり、Stage5/6 は tools なし
  *
  * @see [[AgentLoop]] `chatCompletion` を使用
  * @see [[AgentConfig]] リクエストパラメータの設定
  */
object LlmClient {

  /** Chat Completions API を呼び出し、フルレスポンスを返す。
    *
    * @param messages メッセージ履歴
    * @param config   API 設定（URL, model, maxTokens, temperature, timeout）
    * @param tools    ツール定義 JSON（Optional。AgentLoop では `Some(ToolDispatch.toolDefs)`）
    * @return `LlmResponse`（message JSON + token 消費量）
    */
  def chatCompletion(
      messages: List[ChatMessage],
      config: AgentConfig,
      tools: Option[Json] = None
  ): LlmResponse = {
    val baseFields = List(
      "model" -> Json.fromString(config.model),
      "messages" -> Json.arr(messages.map(ChatMessage.toJson)*),
      "max_tokens" -> Json.fromInt(config.maxTokens),
      "temperature" -> Json.fromDoubleOrNull(config.temperature)
    )
    val fields = baseFields ++ tools.map(t => "tools" -> t).toList
    val body = Json.obj(fields*)

    val backend = DefaultSyncBackend()
    val resp = basicRequest
      .post(uri"${config.baseUrl}/chat/completions")
      .contentType("application/json")
      .body(body.noSpaces)
      .response(asString)
      .readTimeout(scala.concurrent.duration.Duration(config.timeoutSeconds.toLong, "s"))
      .send(backend)
    backend.close()

    val respJson = parse(resp.body.getOrElse("{}")).getOrElse(Json.Null)
    val choiceMsg = respJson.hcursor
      .downField("choices").downArray.downField("message").focus.getOrElse(Json.Null)
    val usage = respJson.hcursor
      .downField("usage").downField("total_tokens").as[Int].getOrElse(0)

    LlmResponse(choiceMsg, usage)
  }

  /** Chat Completions API を呼び出し、content 文字列のみを返す便利メソッド。
    *
    * ツールなし・単発呼び出し用（計画生成、evaluator 等）。
    *
    * @return `(content文字列, token消費量)`。content が空の場合は空文字列。
    */
  def contentOnly(
      messages: List[ChatMessage],
      config: AgentConfig
  ): (String, Int) = {
    val resp = chatCompletion(messages, config, tools = None)
    val content = resp.message.hcursor.downField("content").as[String].getOrElse("")
    (content, resp.totalTokens)
  }
}

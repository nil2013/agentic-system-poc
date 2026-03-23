//> using scala 3.6
//> using dep com.softwaremill.sttp.client4::core:4.0.19
//> using dep com.softwaremill.sttp.client4::circe:4.0.19
//> using dep io.circe::circe-generic:0.14.15
//> using dep io.circe::circe-parser:0.14.15

import sttp.client4.*
import sttp.client4.circe.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.parser.{decode, parse}

// --- API 型定義 ---
// リクエスト用: content は必須 String（llama-server が null を拒否するため）
case class ReqMessage(role: String, content: String)
case class ChatRequest(
    model: String,
    messages: List[ReqMessage],
    max_tokens: Int,
    stream: Boolean = false
)
// レスポンス用: content は Optional（thinking mode で空になる場合がある）
case class RespMessage(role: String, content: Option[String] = None, reasoning_content: Option[String] = None)
case class Usage(prompt_tokens: Int, completion_tokens: Int, total_tokens: Int)
case class Timings(
    prompt_n: Int,
    prompt_ms: Double,
    prompt_per_second: Double,
    predicted_n: Int,
    predicted_ms: Double,
    predicted_per_second: Double
)
case class Choice(message: RespMessage, finish_reason: String)
case class ChatResponse(
    model: String,
    choices: List[Choice],
    usage: Option[Usage],
    timings: Option[Timings]
)

val BaseUrl = sys.env.getOrElse("LLM_BASE_URL", "http://localhost:8080/v1")

def healthCheck(): Unit = {
  val backend = DefaultSyncBackend()
  val resp = basicRequest.get(uri"${BaseUrl.stripSuffix("/v1")}/health").response(asString).send(backend)
  backend.close()
  println(s"Health: ${resp.body.merge}")
}

def measureLatency(label: String, prompt: String, maxTokens: Int = 128): Unit = {
  val backend = DefaultSyncBackend()
  val req = ChatRequest(
    model = sys.env.getOrElse("LLM_MODEL", "local"),
    messages = List(ReqMessage("user", prompt)),
    max_tokens = maxTokens
  )

  val t0 = System.nanoTime()
  val resp = basicRequest
    .post(uri"$BaseUrl/chat/completions")
    .body(req.asJson.noSpaces)
    .contentType("application/json")
    .response(asJson[ChatResponse])
    .readTimeout(scala.concurrent.duration.Duration(120, "s"))
    .send(backend)
  val elapsed = (System.nanoTime() - t0) / 1e9
  backend.close()

  resp.body match {
    case Right(data) =>
      val u = data.usage.getOrElse(Usage(0, 0, 0))
      val t = data.timings

      println(s"\n--- $label ---")
      println(s"  model:             ${data.model}")
      println(s"  finish_reason:     ${data.choices.headOption.map(_.finish_reason).getOrElse("?")}")
      println(s"  prompt_tokens:     ${u.prompt_tokens}")
      println(s"  completion_tokens: ${u.completion_tokens}")
      println(f"  wall_time:         ${elapsed}%.3f s")

      t.foreach { tm =>
        println(f"  server prompt:     ${tm.prompt_per_second}%.1f tok/s (${tm.prompt_n} tokens, ${tm.prompt_ms}%.1f ms)")
        println(f"  server predicted:  ${tm.predicted_per_second}%.1f tok/s (${tm.predicted_n} tokens, ${tm.predicted_ms}%.1f ms)")
      }

      val content = data.choices.headOption.flatMap(_.message.content).getOrElse("")
      val reasoning = data.choices.headOption.flatMap(_.message.reasoning_content).getOrElse("")
      if (reasoning.nonEmpty) {
        println(s"  reasoning:         ${reasoning.take(120).replace("\n", " ")}...")
      }
      if (content.nonEmpty) {
        println(s"  response:          ${content.take(200).replace("\n", " ")}")
      } else {
        println(s"  response:          (empty — thinking consumed all tokens?)")
      }

    case Left(err) =>
      println(s"\n--- $label ---")
      println(s"  ERROR: $err")
  }
}

@main def run(): Unit = {
  println(s"LLM_BASE_URL = $BaseUrl")
  println()

  // ヘルスチェック
  healthCheck()

  // 短い応答
  measureLatency("Short response", "1+1は？", maxTokens = 256)

  // 中程度の応答
  measureLatency("Medium response", "日本国憲法の三大原則を簡潔に説明してください。", maxTokens = 512)

  // 長い入力（プロンプト処理速度の計測）
  val longInput = "以下の文章を要約してください。\n" + ("これはテスト文です。" * 200)
  measureLatency("Long input", longInput, maxTokens = 256)
}

// Stage EX: 法令要約実験 — 共通ユーティリティ
// 各条件スクリプトから `//> using file common.scala` で参照される

//> using scala 3.6
//> using dep com.softwaremill.sttp.client4::core:4.0.19
//> using dep com.softwaremill.sttp.client4::circe:4.0.19
//> using dep io.circe::circe-generic:0.14.15
//> using dep io.circe::circe-parser:0.14.15
//> using dep org.scala-lang.modules::scala-xml:2.3.0

import sttp.client4.*
import sttp.client4.circe.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.parser.{decode, parse}
import scala.xml.*
import java.io.{File, PrintWriter}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ============================================================
// Config
// ============================================================

object Config {
  val BaseUrl: String = sys.env.getOrElse("LLM_BASE_URL", "http://localhost:8080/v1")
  val Model: String = sys.env.getOrElse("LLM_MODEL", "local")
  val MaxTokens: Int = 8192
  val Temperature: Double = 0.0
  val TimeoutSeconds: Int = 600
  val CivilCodeLawId: String = "129AC0000000089"
  val ResultsDir: String = "stages/stage-ex/summarization/results"
}

// ============================================================
// API 型定義（stage0/latency.scala 準拠）
// ============================================================

case class ReqMessage(role: String, content: String)
case class ChatRequest(
    model: String,
    messages: List[ReqMessage],
    max_tokens: Int,
    temperature: Double = 0.0
)
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

// ============================================================
// 結果型
// ============================================================

case class LlmResult(
    content: String,
    reasoningContent: Option[String],
    promptTokens: Int,
    completionTokens: Int,
    elapsedMs: Long,
    finishReason: String,
    serverPpTokPerSec: Option[Double],
    serverTgTokPerSec: Option[Double]
)

case class PartResult(
    partIndex: Int,
    partName: String,
    inputChars: Int,
    result: LlmResult
)

// ============================================================
// System Prompt
// ============================================================

object Prompts {
  val FullTextSummary: String =
    "あなたは日本法の専門家です。以下の法令全文を読み、法令調査に有益な構造化された要約を作成してください。\n\n" +
    "要約には以下を含めること:\n" +
    "1. 法令の目的と規律対象\n" +
    "2. 全体構造（編・章構成の概観）\n" +
    "3. 重要な定義規定\n" +
    "4. 中核的な規律内容（主要な条文番号を正確に引用）\n" +
    "5. 罰則・制裁規定\n" +
    "6. 附則・経過措置の概要\n\n" +
    "条文番号は正確に記載すること。推測で番号を補完しないこと。"

  val PartSummary: String =
    "あなたは日本法の専門家です。以下の法令テキスト（特定の編または附則のみ）を読み、構造化された要約を作成してください。\n\n" +
    "要約には以下を含めること:\n" +
    "1. この部分の規律対象と位置づけ\n" +
    "2. 章・節の構成\n" +
    "3. 重要な定義規定（条番号付き）\n" +
    "4. 中核的な規律内容（主要な条文番号を正確に引用）\n" +
    "5. 罰則・制裁規定（ある場合）\n\n" +
    "条文番号は正確に記載すること。推測で番号を補完しないこと。"

  val OverallSummary: String =
    "あなたは日本法の専門家です。以下は民法の各部分の要約です。これらを統合して、法令全体の要約を作成してください。\n\n" +
    "要約には以下を含めること:\n" +
    "1. 法令の目的と規律対象\n" +
    "2. 全体構造（編・章構成の概観）\n" +
    "3. 重要な定義規定\n" +
    "4. 中核的な規律内容（主要な条文番号を正確に引用）\n" +
    "5. 罰則・制裁規定\n" +
    "6. 附則・経過措置の概要\n\n" +
    "条文番号は正確に記載すること。各編の要約に含まれる情報を忠実に統合すること。"
}

// ============================================================
// e-Gov API
// ============================================================

def fetchLawData(lawId: String): Elem = {
  println(s"  Fetching law data: $lawId ...")
  val backend = DefaultSyncBackend()
  try {
    val resp = basicRequest
      .get(uri"https://laws.e-gov.go.jp/api/1/lawdata/$lawId")
      .response(asString)
      .readTimeout(scala.concurrent.duration.Duration(120, "s"))
      .send(backend)

    resp.body match {
      case Right(xmlStr) =>
        val root = XML.loadString(xmlStr)
        val code = (root \ "Result" \ "Code").text.trim
        if (code != "0") {
          val msg = (root \ "Result" \ "Message").text.trim
          System.err.println(s"  e-Gov API error: code=$code, message=$msg")
          sys.exit(1)
        }
        println(s"  OK (${xmlStr.length} bytes)")
        root
      case Left(err) =>
        System.err.println(s"  HTTP error: $err")
        sys.exit(1)
    }
  } finally {
    backend.close()
  }
}

// ============================================================
// テキスト抽出
// ============================================================

/** XML ノードからテキストを再帰抽出（構造マークアップ付き） */
private def extractTextFromNode(node: Node, depth: Int): String = {
  val sb = new StringBuilder
  node match {
    case elem: Elem =>
      elem.label match {
        case "PartTitle" | "ChapterTitle" | "SectionTitle" | "SubsectionTitle" | "DivisionTitle" =>
          val prefix = "#" * (depth.min(4) + 1)
          sb.append(s"\n$prefix ${elem.text.trim}\n")
        case "ArticleCaption" =>
          sb.append(s"${elem.text.trim}\n")
        case "ArticleTitle" =>
          sb.append(s"${elem.text.trim}\n")
        case "Sentence" =>
          val text = elem.text.trim
          if (text.nonEmpty) { sb.append(s"$text\n") }
        case "Part" =>
          elem.child.foreach { c => sb.append(extractTextFromNode(c, depth)) }
        case "Chapter" =>
          elem.child.foreach { c => sb.append(extractTextFromNode(c, depth + 1)) }
        case "Section" =>
          elem.child.foreach { c => sb.append(extractTextFromNode(c, depth + 2)) }
        case "Subsection" =>
          elem.child.foreach { c => sb.append(extractTextFromNode(c, depth + 3)) }
        case "Division" =>
          elem.child.foreach { c => sb.append(extractTextFromNode(c, depth + 3)) }
        case _ =>
          elem.child.foreach { c => sb.append(extractTextFromNode(c, depth)) }
      }
    case _ => // text nodes, skip
  }
  sb.toString
}

/** 法令全文のテキスト抽出（C1 用） */
def extractFullText(root: Elem): String = {
  val lawFullText = root \ "ApplData" \ "LawFullText"
  val lawElem = if (lawFullText.nonEmpty) (lawFullText \ "Law").headOption.getOrElse(root) else root

  val sb = new StringBuilder
  // MainProvision
  (lawElem \\ "MainProvision").foreach { mp =>
    sb.append(extractTextFromNode(mp, 0))
  }
  // SupplProvision
  (lawElem \\ "SupplProvision").foreach { sp =>
    val amendNum = sp.attribute("AmendLawNum").map(_.text).getOrElse("")
    val label = if (amendNum.nonEmpty) s"附則（$amendNum）" else "附則"
    sb.append(s"\n# $label\n")
    sb.append(extractTextFromNode(sp, 1))
  }
  sb.toString
}

/** 編単位のテキスト抽出（C2-C5 用）。(partName, text) のリストを返す。 */
def extractPartTexts(root: Elem): List[(String, String)] = {
  val lawFullText = root \ "ApplData" \ "LawFullText"
  val lawElem = if (lawFullText.nonEmpty) (lawFullText \ "Law").headOption.getOrElse(root) else root
  val mainProv = (lawElem \\ "MainProvision").head

  // 各 Part を抽出
  val parts = (mainProv \ "Part").toList.map { part =>
    val title = (part \ "PartTitle").text.trim
    val text = extractTextFromNode(part, 0)
    (title, text)
  }

  // 附則をまとめて1パート
  val supplSb = new StringBuilder
  (lawElem \\ "SupplProvision").foreach { sp =>
    val amendNum = sp.attribute("AmendLawNum").map(_.text).getOrElse("")
    val label = if (amendNum.nonEmpty) s"附則（$amendNum）" else "附則"
    supplSb.append(s"\n## $label\n")
    supplSb.append(extractTextFromNode(sp, 1))
  }

  parts :+ ("附則", supplSb.toString)
}

// ============================================================
// LLM 呼び出し
// ============================================================

def callLlm(
    systemPrompt: String,
    userPrompt: String,
    maxTokens: Int = Config.MaxTokens
): LlmResult = {
  val backend = DefaultSyncBackend()
  val req = ChatRequest(
    model = Config.Model,
    messages = List(
      ReqMessage("system", systemPrompt),
      ReqMessage("user", userPrompt)
    ),
    max_tokens = maxTokens,
    temperature = Config.Temperature
  )

  val t0 = System.nanoTime()
  try {
    val resp = basicRequest
      .post(uri"${Config.BaseUrl}/chat/completions")
      .body(req.asJson.noSpaces)
      .contentType("application/json")
      .response(asJson[ChatResponse])
      .readTimeout(scala.concurrent.duration.Duration(Config.TimeoutSeconds, "s"))
      .send(backend)

    val elapsedMs = (System.nanoTime() - t0) / 1_000_000

    resp.body match {
      case Right(data) =>
        val u = data.usage.getOrElse(Usage(0, 0, 0))
        val t = data.timings
        val choice = data.choices.headOption
        val content = choice.flatMap(_.message.content).getOrElse("")
        val reasoning = choice.flatMap(_.message.reasoning_content)
        val finishReason = choice.map(_.finish_reason).getOrElse("unknown")

        LlmResult(
          content = content,
          reasoningContent = reasoning,
          promptTokens = u.prompt_tokens,
          completionTokens = u.completion_tokens,
          elapsedMs = elapsedMs,
          finishReason = finishReason,
          serverPpTokPerSec = t.map(_.prompt_per_second),
          serverTgTokPerSec = t.map(_.predicted_per_second)
        )
      case Left(err) =>
        System.err.println(s"  LLM API error: $err")
        LlmResult("", None, 0, 0, elapsedMs, "error", None, None)
    }
  } finally {
    backend.close()
  }
}

// ============================================================
// Phase 2: 統合要約（C2-C5 共通）
// ============================================================

def runPhase2Combine(partSummaries: List[(String, String)]): LlmResult = {
  val combined = partSummaries.map { case (name, summary) =>
    val text = if (summary.isEmpty) "[empty - thinking consumed all tokens]" else summary
    s"## $name\n$text"
  }.mkString("\n\n---\n\n")

  val userPrompt = s"以下は民法の各部分の要約です。統合要約を作成してください。\n\n$combined"
  callLlm(Prompts.OverallSummary, userPrompt)
}

// ============================================================
// 結果出力
// ============================================================

def ensureResultsDir(): Unit = {
  val dir = new File(Config.ResultsDir)
  if (!dir.exists()) { dir.mkdirs() }
}

def saveText(filename: String, content: String): Unit = {
  ensureResultsDir()
  val pw = new PrintWriter(new File(s"${Config.ResultsDir}/$filename"))
  try { pw.write(content) } finally { pw.close() }
}

def saveMetricsJson(conditionId: String, metricsJson: Json): Unit = {
  saveText(s"${conditionId}_metrics.json", metricsJson.spaces2)
}

def saveSummary(conditionId: String, summary: String): Unit = {
  saveText(s"${conditionId}_summary.md", summary)
}

def saveThinking(conditionId: String, thinking: String): Unit = {
  saveText(s"${conditionId}_thinking.md", thinking)
}

def savePartSummary(conditionId: String, partIndex: Int, partName: String, summary: String): Unit = {
  saveText(s"${conditionId}_phase1_part${partIndex}.md", s"# $partName\n\n$summary")
}

/** メトリクス JSON を構築する（C1 用） */
def buildMetricsC1(result: LlmResult, inputChars: Int): Json = {
  Json.obj(
    "condition" -> Json.fromString("C1"),
    "strategy" -> Json.fromString("full-text"),
    "parallelism" -> Json.fromString("N/A"),
    "timestamp" -> Json.fromString(LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
    "config" -> Json.obj(
      "model" -> Json.fromString(Config.Model),
      "max_tokens" -> Json.fromInt(Config.MaxTokens),
      "base_url" -> Json.fromString(Config.BaseUrl)
    ),
    "phase1" -> buildLlmResultJson("full_text", inputChars, result),
    "total" -> Json.obj(
      "wall_time_ms" -> Json.fromLong(result.elapsedMs),
      "prompt_tokens" -> Json.fromInt(result.promptTokens),
      "completion_tokens" -> Json.fromInt(result.completionTokens)
    )
  )
}

/** メトリクス JSON を構築する（C2-C5 用） */
def buildMetricsMapReduce(
    conditionId: String,
    parallelism: String,
    partResults: List[PartResult],
    phase1WallMs: Long,
    phase2Result: LlmResult,
    phase2InputChars: Int
): Json = {
  val totalPrompt = partResults.map(_.result.promptTokens).sum + phase2Result.promptTokens
  val totalCompletion = partResults.map(_.result.completionTokens).sum + phase2Result.completionTokens
  val totalWall = phase1WallMs + phase2Result.elapsedMs

  Json.obj(
    "condition" -> Json.fromString(conditionId),
    "strategy" -> Json.fromString("map-reduce"),
    "parallelism" -> Json.fromString(parallelism),
    "timestamp" -> Json.fromString(LocalDateTime.now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
    "config" -> Json.obj(
      "model" -> Json.fromString(Config.Model),
      "max_tokens" -> Json.fromInt(Config.MaxTokens),
      "base_url" -> Json.fromString(Config.BaseUrl)
    ),
    "phase1" -> Json.obj(
      "wall_time_ms" -> Json.fromLong(phase1WallMs),
      "parts" -> Json.arr(partResults.map { pr =>
        buildLlmResultJson(pr.partName, pr.inputChars, pr.result)
      }*)
    ),
    "phase2" -> buildLlmResultJson("combine", phase2InputChars, phase2Result),
    "total" -> Json.obj(
      "wall_time_ms" -> Json.fromLong(totalWall),
      "prompt_tokens" -> Json.fromInt(totalPrompt),
      "completion_tokens" -> Json.fromInt(totalCompletion)
    )
  )
}

private def buildLlmResultJson(name: String, inputChars: Int, r: LlmResult): Json = {
  Json.obj(
    "name" -> Json.fromString(name),
    "input_chars" -> Json.fromInt(inputChars),
    "prompt_tokens" -> Json.fromInt(r.promptTokens),
    "completion_tokens" -> Json.fromInt(r.completionTokens),
    "wall_time_ms" -> Json.fromLong(r.elapsedMs),
    "finish_reason" -> Json.fromString(r.finishReason),
    "has_content" -> Json.fromBoolean(r.content.nonEmpty),
    "output_chars" -> Json.fromInt(r.content.length),
    "server_pp_tok_per_sec" -> r.serverPpTokPerSec.map(Json.fromDouble(_).getOrElse(Json.Null)).getOrElse(Json.Null),
    "server_tg_tok_per_sec" -> r.serverTgTokPerSec.map(Json.fromDouble(_).getOrElse(Json.Null)).getOrElse(Json.Null)
  )
}

// ============================================================
// 表示ユーティリティ
// ============================================================

def printResult(label: String, r: LlmResult, inputChars: Int): Unit = {
  println(s"  $label:")
  println(f"    input:       $inputChars%,d chars")
  println(f"    prompt:      ${r.promptTokens}%,d tokens")
  println(f"    completion:  ${r.completionTokens}%,d tokens")
  println(f"    wall time:   ${r.elapsedMs / 1000.0}%.1f s")
  println(f"    finish:      ${r.finishReason}")
  r.serverPpTokPerSec.foreach { pp => println(f"    PP:          $pp%.1f tok/s") }
  r.serverTgTokPerSec.foreach { tg => println(f"    TG:          $tg%.1f tok/s") }
  if (r.content.isEmpty) {
    println("    WARNING: content is empty (thinking consumed all tokens?)")
  } else {
    println(f"    output:      ${r.content.length}%,d chars")
  }
}

// ============================================================
// 並列 Map-Reduce ヘルパ
// ============================================================

import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration.Duration
import java.util.concurrent.Executors

/** バッチ単位で並列要約を実行する（C2, C3, C5 共通）。
  *
  * @param parts       (partName, text) のリスト
  * @param batches     バッチ定義。各バッチは parts のインデックスのリスト
  * @param parallelism スレッドプールサイズ
  * @return (List[PartResult], phase1WallMs)
  */
def runPhase1Batched(
    parts: List[(String, String)],
    batches: List[List[Int]],
    parallelism: Int
): (List[PartResult], Long) = {
  val executor = Executors.newFixedThreadPool(parallelism)
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(executor)

  val phase1Start = System.nanoTime()
  var allResults = List.empty[PartResult]

  try {
    for ((batch, batchIdx) <- batches.zipWithIndex) {
      println(s"  Batch $batchIdx: indices=${batch.mkString(",")}")
      val futures = batch.map { idx =>
        val (name, text) = parts(idx)
        Future {
          println(s"    [$idx] $name ...")
          val userPrompt = s"以下は民法の「$name」の条文です。要約してください。\n\n$text"
          val result = callLlm(Prompts.PartSummary, userPrompt)
          PartResult(idx, name, text.length, result)
        }
      }

      val batchResults = Await.result(Future.sequence(futures), Duration(Config.TimeoutSeconds, "s"))
      batchResults.foreach { pr =>
        printResult(s"    [${pr.partIndex}] ${pr.partName}", pr.result, pr.inputChars)
      }
      allResults = allResults ++ batchResults
      println()
    }
  } finally {
    executor.shutdown()
  }

  val phase1WallMs = (System.nanoTime() - phase1Start) / 1_000_000
  // PartResult をインデックス順にソート
  (allResults.sortBy(_.partIndex), phase1WallMs)
}

def printFinalSummary(conditionId: String, totalWallMs: Long, totalPrompt: Int, totalCompletion: Int): Unit = {
  println()
  println(s"=== $conditionId Final Summary ===")
  println(f"  Total wall time:       ${totalWallMs / 1000.0}%.1f s")
  println(f"  Total prompt tokens:   $totalPrompt%,d")
  println(f"  Total completion:      $totalCompletion%,d")
  println(f"  Total tokens:          ${totalPrompt + totalCompletion}%,d")
}

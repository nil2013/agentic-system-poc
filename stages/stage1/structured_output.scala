// Stage 1: 構造化出力の3手法比較（プロンプト / JSON mode / JSON Schema）
// Run: scala-cli run stages/stage1/structured_output.scala

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

// ============================================================
// API 型定義
// ============================================================
case class ReqMessage(role: String, content: String)
case class RespMessage(role: String, content: Option[String] = None, reasoning_content: Option[String] = None)
case class Choice(message: RespMessage, finish_reason: String)
case class Timings(predicted_n: Int, predicted_ms: Double, predicted_per_second: Double)
case class ChatResponse(choices: List[Choice], timings: Option[Timings])

// 抽出結果型
case class ParagraphInfo(paragraph_number: String, text: String)
case class ArticleInfo(
    article_number: String,
    caption: String,
    title: String,
    paragraphs: List[ParagraphInfo]
)

// ============================================================
// LLM クライアント
// ============================================================
val BaseUrl = sys.env.getOrElse("LLM_BASE_URL", "http://localhost:8080/v1")
val Model = sys.env.getOrElse("LLM_MODEL", "local")

def chat(
    messages: List[ReqMessage],
    maxTokens: Int = 1024,
    responseFormat: Option[Json] = None
): Either[String, (String, Option[Timings])] = {
  val backend = DefaultSyncBackend()

  var body = Json.obj(
    "model" -> Json.fromString(Model),
    "messages" -> messages.asJson,
    "max_tokens" -> Json.fromInt(maxTokens),
    "temperature" -> Json.fromDoubleOrNull(0.0)
  )
  responseFormat.foreach { rf =>
    body = body.deepMerge(Json.obj("response_format" -> rf))
  }

  val resp = basicRequest
    .post(uri"$BaseUrl/chat/completions")
    .body(body.noSpaces)
    .contentType("application/json")
    .response(asJson[ChatResponse])
    .readTimeout(scala.concurrent.duration.Duration(120, "s"))
    .send(backend)
  backend.close()

  resp.body match {
    case Right(data) =>
      val content = data.choices.headOption.flatMap(_.message.content).getOrElse("")
      Right((content, data.timings))
    case Left(err) =>
      Left(err.toString)
  }
}

// ============================================================
// 条文 XML データ
// ============================================================
val Article709Xml = """<Article Num="709">
  <ArticleCaption>（不法行為による損害賠償）</ArticleCaption>
  <ArticleTitle>第七百九条</ArticleTitle>
  <Paragraph Num="1">
    <ParagraphNum/>
    <ParagraphSentence>
      <Sentence>故意又は過失によって他人の権利又は法律上保護される利益を侵害した者は、これによって生じた損害を賠償する責任を負う。</Sentence>
    </ParagraphSentence>
  </Paragraph>
</Article>"""

val Article1Xml = """<Article Num="1">
  <ArticleCaption>（基本原則）</ArticleCaption>
  <ArticleTitle>第一条</ArticleTitle>
  <Paragraph Num="1">
    <ParagraphNum/>
    <ParagraphSentence>
      <Sentence>私権は、公共の福祉に適合しなければならない。</Sentence>
    </ParagraphSentence>
  </Paragraph>
  <Paragraph Num="2">
    <ParagraphNum>２</ParagraphNum>
    <ParagraphSentence>
      <Sentence>権利の行使及び義務の履行は、信義に従い誠実に行わなければならない。</Sentence>
    </ParagraphSentence>
  </Paragraph>
  <Paragraph Num="3">
    <ParagraphNum>３</ParagraphNum>
    <ParagraphSentence>
      <Sentence>権利の濫用は、これを許さない。</Sentence>
    </ParagraphSentence>
  </Paragraph>
</Article>"""

val Article5Xml = """<Article Num="5">
  <ArticleCaption>（未成年者の法律行為）</ArticleCaption>
  <ArticleTitle>第五条</ArticleTitle>
  <Paragraph Num="1">
    <ParagraphNum/>
    <ParagraphSentence>
      <Sentence>未成年者が法律行為をするには、その法定代理人の同意を得なければならない。ただし、単に権利を得、又は義務を免れる法律行為については、この限りでない。</Sentence>
    </ParagraphSentence>
  </Paragraph>
  <Paragraph Num="2">
    <ParagraphNum>２</ParagraphNum>
    <ParagraphSentence>
      <Sentence>前項の規定に反する法律行為は、取り消すことができる。</Sentence>
    </ParagraphSentence>
  </Paragraph>
  <Paragraph Num="3">
    <ParagraphNum>３</ParagraphNum>
    <ParagraphSentence>
      <Sentence>第一項の規定にかかわらず、法定代理人が目的を定めて処分を許した財産は、その目的の範囲内において、未成年者が自由に処分することができる。目的を定めないで処分を許した財産を処分するときも、同様とする。</Sentence>
    </ParagraphSentence>
  </Paragraph>
</Article>"""

// ============================================================
// Ground Truth
// ============================================================
// Ground Truth: XML の ArticleCaption テキストをそのまま使用（括弧付き）
val GroundTruths: Map[String, ArticleInfo] = Map(
  "709" -> ArticleInfo("709", "（不法行為による損害賠償）", "第七百九条", List(
    ParagraphInfo("1", "故意又は過失によって他人の権利又は法律上保護される利益を侵害した者は、これによって生じた損害を賠償する責任を負う。")
  )),
  "1" -> ArticleInfo("1", "（基本原則）", "第一条", List(
    ParagraphInfo("1", "私権は、公共の福祉に適合しなければならない。"),
    ParagraphInfo("2", "権利の行使及び義務の履行は、信義に従い誠実に行わなければならない。"),
    ParagraphInfo("3", "権利の濫用は、これを許さない。")
  )),
  "5" -> ArticleInfo("5", "（未成年者の法律行為）", "第五条", List(
    ParagraphInfo("1", "未成年者が法律行為をするには、その法定代理人の同意を得なければならない。ただし、単に権利を得、又は義務を免れる法律行為については、この限りでない。"),
    ParagraphInfo("2", "前項の規定に反する法律行為は、取り消すことができる。"),
    ParagraphInfo("3", "第一項の規定にかかわらず、法定代理人が目的を定めて処分を許した財産は、その目的の範囲内において、未成年者が自由に処分することができる。目的を定めないで処分を許した財産を処分するときも、同様とする。")
  ))
)

// ============================================================
// System Prompt
// ============================================================
val SystemPrompt = """|あなたは法令XMLから情報を抽出するアシスタントです。
  |与えられたXML断片から以下の情報をJSON形式で抽出してください。
  |JSONのみを出力し、それ以外のテキストは一切含めないでください。
  |
  |出力フォーマット:
  |{"article_number":"条番号","caption":"見出し","title":"条文タイトル",
  | "paragraphs":[{"paragraph_number":"項番号","text":"条文本文"}]}""".stripMargin

// ============================================================
// JSON Schema (実験C用)
// ============================================================
val ArticleSchema: Json = Json.obj(
  "type" -> Json.fromString("json_schema"),
  "json_schema" -> Json.obj(
    "name" -> Json.fromString("article_extraction"),
    "strict" -> Json.fromBoolean(true),
    "schema" -> Json.obj(
      "type" -> Json.fromString("object"),
      "properties" -> Json.obj(
        "article_number" -> Json.obj("type" -> Json.fromString("string")),
        "caption" -> Json.obj("type" -> Json.fromString("string")),
        "title" -> Json.obj("type" -> Json.fromString("string")),
        "paragraphs" -> Json.obj(
          "type" -> Json.fromString("array"),
          "items" -> Json.obj(
            "type" -> Json.fromString("object"),
            "properties" -> Json.obj(
              "paragraph_number" -> Json.obj("type" -> Json.fromString("string")),
              "text" -> Json.obj("type" -> Json.fromString("string"))
            ),
            "required" -> Json.arr(Json.fromString("paragraph_number"), Json.fromString("text"))
          )
        )
      ),
      "required" -> Json.arr(
        Json.fromString("article_number"), Json.fromString("caption"),
        Json.fromString("title"), Json.fromString("paragraphs")
      )
    )
  )
)

// ============================================================
// 評価
// ============================================================
def evaluate(result: ArticleInfo, truth: ArticleInfo): Map[String, Boolean] = {
  Map(
    "article_number" -> (result.article_number.trim == truth.article_number),
    "caption" -> (result.caption.trim == truth.caption),
    "title" -> (result.title.trim == truth.title),
    "paragraph_count" -> (result.paragraphs.size == truth.paragraphs.size),
    "all_para_text" -> result.paragraphs.zip(truth.paragraphs).forall {
      case (r, t) => r.text.trim == t.text.trim
    }
  )
}

// ============================================================
// 実験ランナー
// ============================================================
case class TrialResult(
    experiment: String,
    articleNum: String,
    trial: Int,
    jsonParsed: Boolean,
    scores: Map[String, Boolean],
    tokPerSec: Double,
    rawSnippet: String
)

def runTrial(
    experiment: String,
    articleNum: String,
    xmlText: String,
    trial: Int,
    responseFormat: Option[Json] = None
): TrialResult = {
  val messages = List(
    ReqMessage("system", SystemPrompt),
    ReqMessage("user", s"以下のXMLから情報を抽出してください:\n\n$xmlText")
  )

  // Qwen3.5 の thinking mode がデフォルト有効で ~1500-2000 tokens を消費するため、
  // content に到達するには max_tokens を十分大きくする必要がある
  chat(messages, maxTokens = 4096, responseFormat = responseFormat) match {
    case Right((text, timings)) =>
      val tps = timings.map(_.predicted_per_second).getOrElse(0.0)
      decode[ArticleInfo](text) match {
        case Right(info) =>
          val scores = evaluate(info, GroundTruths(articleNum))
          TrialResult(experiment, articleNum, trial, true, scores, tps, text.take(80))
        case Left(err) =>
          // JSON パースできたが ArticleInfo にデコードできない場合も試す
          parse(text) match {
            case Right(_) =>
              TrialResult(experiment, articleNum, trial, true, Map("decode_fail" -> false), tps, s"Valid JSON but wrong shape: ${err.getMessage.take(60)}")
            case Left(_) =>
              TrialResult(experiment, articleNum, trial, false, Map.empty, tps, text.take(80))
          }
      }
    case Left(err) =>
      TrialResult(experiment, articleNum, trial, false, Map.empty, 0.0, s"API ERROR: ${err.take(80)}")
  }
}

// ============================================================
// メイン
// ============================================================
@main def run(): Unit = {
  println(s"LLM_BASE_URL = $BaseUrl")
  println(s"Model = $Model")
  println()

  val articles = List(
    ("709", Article709Xml),
    ("1", Article1Xml),
    ("5", Article5Xml)
  )

  val experiments: List[(String, Option[Json])] = List(
    ("A_prompt_only", None),
    ("B_json_mode", Some(Json.obj("type" -> Json.fromString("json_object")))),
    ("C_json_schema", Some(ArticleSchema))
  )

  var allResults = List.empty[TrialResult]

  for {
    (expName, respFmt) <- experiments
    (artNum, artXml) <- articles
    trial <- 1 to 5
  } {
    print(s"  $expName / Art.$artNum / Trial $trial ... ")
    val result = runTrial(expName, artNum, artXml, trial, respFmt)
    allResults = allResults :+ result

    val status = if (result.jsonParsed) {
      val allPass = result.scores.values.forall(identity)
      if (allPass) "OK" else s"PARTIAL(${result.scores.filter(!_._2).keys.mkString(",")})"
    } else {
      "FAIL"
    }
    println(s"$status [${result.tokPerSec.round} tok/s]")
  }

  // サマリー出力
  println("\n" + "=" * 70)
  println("SUMMARY")
  println("=" * 70)

  for ((expName, _) <- experiments) {
    println(s"\n--- $expName ---")
    for ((artNum, _) <- articles) {
      val trials = allResults.filter(r => r.experiment == expName && r.articleNum == artNum)
      val jsonOk = trials.count(_.jsonParsed)
      val allFieldsOk = trials.count(r => r.jsonParsed && r.scores.values.forall(identity))
      val avgTps = trials.map(_.tokPerSec).sum / trials.size
      println(f"  Art.$artNum: JSON parse $jsonOk/5, all fields correct $allFieldsOk/5, avg ${avgTps}%.1f tok/s")
    }
  }

  // 全体集計
  println(s"\n--- Overall ---")
  for ((expName, _) <- experiments) {
    val trials = allResults.filter(_.experiment == expName)
    val jsonRate = trials.count(_.jsonParsed).toDouble / trials.size * 100
    val correctRate = trials.count(r => r.jsonParsed && r.scores.values.forall(identity)).toDouble / trials.size * 100
    println(f"  $expName: JSON $jsonRate%.0f%%, all correct $correctRate%.0f%%")
  }
}

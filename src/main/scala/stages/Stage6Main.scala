package stages

import agent.*
import messages.*
import tools.ToolDispatch
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.parser.{decode, parse}
import sttp.client4.*
import java.nio.file.Paths

/** Stage 6: 自己評価と修正ループの実験。
  *
  * generator（AgentLoop）の出力を evaluator（別の LLM 呼び出し）で評価し、
  * 不合格なら問題点を添えて再生成する。tool_result_consistent による
  * 「静かなフォールバック」の検出を含む。
  */
object Stage6Main {

  // SystemPrompt にフォールバック制御を組み込んだ generator config
  val generatorConfig = AgentConfig(
    systemPrompt = AgentConfig().systemPrompt +
      "\n\n重要: ツールがエラーを返した場合は、エラーの内容をそのままユーザーに伝えてください。" +
      "内部知識で代替回答しないでください。"
  )

  // Evaluator 用の定義
  case class Evaluation(
      source_cited: Boolean,
      internally_consistent: Boolean,
      answers_question: Boolean,
      tool_result_consistent: Boolean,
      issues: List[String]
  )

  val EvaluatorPrompt = """|あなたは法律情報の品質検証者です。
    |以下の質問と回答のペアを検証し、JSONで評価結果を返してください。
    |
    |評価基準:
    |1. source_cited: 回答中に具体的な条文番号が引用されているか (true/false)
    |2. internally_consistent: 回答内で矛盾する記述がないか (true/false)
    |3. answers_question: 元の質問に対する直接的な回答が含まれているか (true/false)
    |4. tool_result_consistent: ツールが否定結果（エラー）を返した場合、
    |   回答がその否定結果と整合しているか。ツールがエラーを返したのに
    |   具体的な条文内容が回答に含まれている場合は false。
    |   ツールがエラーを返していない場合は true とする (true/false)
    |5. issues: 問題点があれば列挙 (配列。なければ空配列)
    |
    |JSONのみを出力してください:
    |{"source_cited":true,"internally_consistent":true,"answers_question":true,"tool_result_consistent":true,"issues":[]}
    |""".stripMargin

  // --- 機械検証 ---

  /** 条文番号パターンの存在確認 */
  def hasCitation(text: String): Boolean = {
    "第[一二三四五六七八九十百千\\d]+条".r.findFirstIn(text).isDefined
  }

  /** ツール結果の整合性チェック: ツールがエラーを返したのに条文テキストが含まれていたら不整合 */
  def checkToolResultConsistency(toolResults: List[String], answer: String): Boolean = {
    val hasToolError = toolResults.exists(_.startsWith("エラー:")) || toolResults.exists(_.startsWith("APIエラー:"))
    if (!hasToolError) {
      return true  // ツールエラーなし → 整合
    }
    // ツールがエラーを返しているのに、条文テキストパターンが含まれていたら不整合
    val hasArticleContent = hasCitation(answer) &&
      (answer.contains("条文は以下") || answer.contains("条文:") || answer.contains("第一条") || answer.contains("第七百"))
    !hasArticleContent
  }

  // --- LLM Evaluator ---

  def evaluateWithLLM(query: String, answer: String, toolResults: List[String]): Either[String, Evaluation] = {
    val toolContext = if (toolResults.nonEmpty) {
      s"\n\nツール実行結果:\n${toolResults.mkString("\n")}"
    } else {
      ""
    }

    val body = Json.obj(
      "model" -> Json.fromString(generatorConfig.model),
      "messages" -> Json.arr(
        Json.obj("role" -> Json.fromString("system"), "content" -> Json.fromString(EvaluatorPrompt)),
        Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(
          s"質問: $query\n\n回答: $answer$toolContext"
        ))
      ),
      "max_tokens" -> Json.fromInt(4096),
      "temperature" -> Json.fromDoubleOrNull(0.0)
    )

    val backend = DefaultSyncBackend()
    val resp = basicRequest
      .post(uri"${generatorConfig.baseUrl}/chat/completions")
      .contentType("application/json")
      .body(body.noSpaces)
      .response(asString)
      .readTimeout(scala.concurrent.duration.Duration(120, "s"))
      .send(backend)
    backend.close()

    val respJson = parse(resp.body.getOrElse("{}")).getOrElse(Json.Null)
    val content = respJson.hcursor
      .downField("choices").downArray.downField("message").downField("content")
      .as[String].getOrElse("")

    if (content.isEmpty) {
      return Left("evaluator content が空（thinking 消費）")
    }

    // JSON を抽出
    val jsonStr = if (content.trim.startsWith("{")) {
      content.trim
    } else {
      "\\{[\\s\\S]*\\}".r.findFirstIn(content).getOrElse(content.trim)
    }

    decode[Evaluation](jsonStr).left.map(e => s"Evaluation パースエラー: $e\nRaw: ${jsonStr.take(200)}")
  }

  // --- 修正ループ ---

  case class EvalRound(
      attempt: Int,
      answer: String,
      toolCalls: List[String],
      toolResults: List[String],
      machineCitation: Boolean,
      machineToolConsistent: Boolean,
      llmEval: Option[Evaluation],
      passed: Boolean
  )

  def runWithSelfEval(query: String, logger: ConversationLogger, turnNum: Int): List[EvalRound] = {
    var rounds = List.empty[EvalRound]
    var currentQuery = query

    for (attempt <- 0 until 3) {
      println(s"  [Attempt $attempt] Running agent...")

      // Generator 実行
      val state = new ConversationState(s"stage6-eval-$turnNum-$attempt")
      state.add(ChatMessage.System(generatorConfig.systemPrompt))
      state.add(ChatMessage.User(currentQuery))

      val (result, updatedMessages) = AgentLoop.runTurn(state.messages, generatorConfig)
      val answer = result.response.content.getOrElse("(empty)")

      // ツール結果を収集
      val toolResults = updatedMessages.collect {
        case ChatMessage.ToolResult(_, content) => content
      }

      // 機械検証
      val machineCitation = hasCitation(answer)
      val machineToolConsistent = checkToolResultConsistency(toolResults, answer)

      println(s"  [Attempt $attempt] Machine: citation=$machineCitation, toolConsistent=$machineToolConsistent")

      // LLM 評価
      println(s"  [Attempt $attempt] Running evaluator...")
      val llmEval = evaluateWithLLM(query, answer, toolResults)

      val passed = llmEval match {
        case Right(eval) =>
          val llmPass = eval.source_cited && eval.answers_question && eval.tool_result_consistent
          println(s"  [Attempt $attempt] LLM eval: $eval")
          println(s"  [Attempt $attempt] LLM pass=$llmPass, machine toolConsistent=$machineToolConsistent")
          // 二重防御: LLM と機械検証の両方がパスした場合のみ合格
          llmPass && machineToolConsistent
        case Left(err) =>
          println(s"  [Attempt $attempt] LLM eval failed: $err")
          // evaluator が失敗した場合は機械検証のみで判断
          machineToolConsistent
      }

      val round = EvalRound(attempt, answer, result.toolCalls, toolResults, machineCitation, machineToolConsistent, llmEval.toOption, passed)
      rounds = rounds :+ round

      // ログ記録
      logger.turnStart(turnNum * 10 + attempt, s"[Eval attempt $attempt] $query")
      for ((tc, i) <- result.toolCalls.zipWithIndex) {
        val name = tc.takeWhile(_ != '(')
        val args = tc.drop(name.length)
        val toolResult = toolResults.lift(i).getOrElse("")
        logger.toolCall(i, name, args, toolResult)
      }
      logger.assistantResponse(
        s"[pass=$passed, citation=$machineCitation, toolConsistent=$machineToolConsistent] $answer",
        result.totalTokens, 0, 0
      )

      if (passed) {
        println(s"  [Attempt $attempt] PASSED")
        return rounds
      }

      // 不合格 → 再生成用のフィードバック
      val issues = llmEval.toOption.map(_.issues).getOrElse(Nil)
      val feedback = if (issues.nonEmpty) {
        issues.mkString("; ")
      } else if (!machineToolConsistent) {
        "ツールがエラーを返した項目について、内部知識で代替回答しないでください。エラーをそのままユーザーに伝えてください。"
      } else {
        "回答の品質が不十分です。改善してください。"
      }
      currentQuery = s"$query\n\n前回の回答の問題点: $feedback\n修正して再回答してください。"
      println(s"  [Attempt $attempt] FAILED — retrying with feedback: ${feedback.take(80)}")
    }

    rounds
  }

  // --- テストケース ---

  val testCases = List(
    ("Q1", "民法709条の条文を教えてください。"),
    ("Q2", "皇族に対する尊崇義務に関する法律の第1条を教えてください。"),
    ("Q3", "民法709条と、皇族に対する尊崇義務に関する法律の第1条を比較してください。"),
  )

  def main(args: Array[String]): Unit = {
    println(s"=== Stage 6: Self-Evaluation Loop ===")
    println(s"LLM_BASE_URL = ${generatorConfig.baseUrl}")
    println()

    val logFile = sys.env.getOrElse("STAGE6_LOG", "stages/stage6/conversation-log.md")
    val logger = new ConversationLogger(Paths.get(logFile))
    logger.header("Stage 6: Self-Evaluation Loop Log", generatorConfig)

    var allRounds = Map.empty[String, List[EvalRound]]

    for ((id, query) <- testCases) {
      println(s"\n{'=' * 60}")
      println(s"--- $id: ${query.take(50)}... ---")
      val rounds = runWithSelfEval(query, logger, testCases.indexOf((id, query)))
      allRounds = allRounds + (id -> rounds)
      println()
    }

    // サマリー
    println("\n" + "=" * 60)
    println("SUMMARY")
    println("=" * 60)

    for ((id, _) <- testCases) {
      val rounds = allRounds(id)
      val finalRound = rounds.last
      val attempts = rounds.size
      println(s"\n  $id: $attempts attempt(s)")
      println(s"    Final pass: ${finalRound.passed}")
      println(s"    Machine citation: ${finalRound.machineCitation}")
      println(s"    Machine toolConsistent: ${finalRound.machineToolConsistent}")
      finalRound.llmEval.foreach { eval =>
        println(s"    LLM eval: source_cited=${eval.source_cited}, answers_question=${eval.answers_question}, tool_result_consistent=${eval.tool_result_consistent}")
        if (eval.issues.nonEmpty) {
          println(s"    Issues: ${eval.issues.mkString("; ")}")
        }
      }
      println(s"    Answer: ${finalRound.answer.take(150).replace("\n", " ")}")
    }

    // 機械検証 vs LLM 判定の一致率
    println(s"\n  --- Machine vs LLM agreement ---")
    val allRoundsList = allRounds.values.flatten.toList
    val withLlmEval = allRoundsList.filter(_.llmEval.isDefined)
    if (withLlmEval.nonEmpty) {
      val toolConsistentAgreement = withLlmEval.count { r =>
        r.machineToolConsistent == r.llmEval.get.tool_result_consistent
      }
      println(s"  tool_result_consistent agreement: $toolConsistentAgreement/${withLlmEval.size}")

      val citationAgreement = withLlmEval.count { r =>
        r.machineCitation == r.llmEval.get.source_cited
      }
      println(s"  source_cited agreement: $citationAgreement/${withLlmEval.size}")
    }

    logger.summary(0, 0)
    logger.save()
    println(s"\nLog: stages/stage6/conversation-log.md")
  }
}

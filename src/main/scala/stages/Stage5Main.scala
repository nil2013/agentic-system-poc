package stages

import agent.*
import messages.*
import tools.ToolDispatch
import tools.egov.*
import io.circe.*
import io.circe.syntax.*
import io.circe.parser.{decode, parse}
import sttp.client4.*
import java.nio.file.Paths

/** Stage 5: 計画と分解（Planning）の比較実験。
  *
  * Plan-then-Execute と Adaptive Planning を比較し、
  * 「計画の静的確定の限界」を体験する。
  * 加えて、ネガティブタスクと SystemPrompt 制御の試行を含む。
  */
object Stage5Main {

  val baseConfig = AgentConfig()

  // SystemPrompt にフォールバック制御を追加した版
  val controlledConfig = baseConfig.copy(
    systemPrompt = baseConfig.systemPrompt +
      "\n\n重要: ツールがエラーを返した場合は、エラーの内容をそのままユーザーに伝えてください。" +
      "内部知識で代替回答しないでください。「見つかりませんでした」と正直に伝えてください。"
  )

  val PlanningPrompt = """|与えられたタスクを、利用可能なツールだけで遂行できるステップに分解してください。
    |
    |利用可能なツール:
    |1. find_laws(keyword) — キーワードで法令名・法令IDを検索
    |2. get_article(law_id_or_name, article_number) — 法令の条文を取得
    |3. calculate(expression) — 四則演算
    |
    |以下のJSON形式で計画を出力してください。JSONのみ出力し、それ以外のテキストは含めないでください。
    |{"steps":[{"id":1,"tool":"ツール名","args":{"引数名":"値"},"purpose":"このステップの目的"}]}
    |
    |注意:
    |- 利用可能なツールだけを使ってください
    |- 前のステップの結果に依存する引数は "depends_on_step_N_result" と記載してください
    |""".stripMargin

  case class PlanStep(id: Int, tool: String, args: Map[String, String], purpose: String)
  case class Plan(steps: List[PlanStep])

  // --- Plan-then-Execute ---

  def generatePlan(task: String, config: AgentConfig): Either[String, Plan] = {
    val body = Json.obj(
      "model" -> Json.fromString(config.model),
      "messages" -> Json.arr(
        Json.obj("role" -> Json.fromString("system"), "content" -> Json.fromString(PlanningPrompt)),
        Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(task))
      ),
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
    val content = respJson.hcursor
      .downField("choices").downArray.downField("message").downField("content")
      .as[String].getOrElse("")

    if (content.isEmpty) {
      return Left("計画生成: content が空（thinking で消費された可能性）")
    }

    // JSON を抽出（コードフェンスがある場合も対応）
    val jsonStr = if (content.trim.startsWith("{")) {
      content.trim
    } else {
      val pattern = "\\{[\\s\\S]*\\}".r
      pattern.findFirstIn(content).getOrElse(content.trim)
    }

    parse(jsonStr).flatMap { json =>
      val steps = json.hcursor.downField("steps").as[List[Json]].getOrElse(Nil).flatMap { s =>
        val c = s.hcursor
        for {
          id <- c.downField("id").as[Int].toOption
          tool <- c.downField("tool").as[String].toOption
          args <- c.downField("args").as[Map[String, String]].toOption
          purpose <- c.downField("purpose").as[String].toOption
        } yield PlanStep(id, tool, args, purpose)
      }
      if (steps.nonEmpty) Right(Plan(steps))
      else Left(s"計画のパースに失敗: $jsonStr")
    }.left.map(e => s"JSON パースエラー: $e")
  }

  def executePlan(plan: Plan, logger: ConversationLogger, turnBase: Int): Unit = {
    var stepResults = Map.empty[Int, String]

    for ((step, idx) <- plan.steps.zipWithIndex) {
      val turnNum = turnBase + idx
      println(s"  [Plan step ${step.id}] ${step.tool}(${step.args}) — ${step.purpose}")

      // 依存する引数を解決
      val resolvedArgs = step.args.map { case (k, v) =>
        val resolved = if (v.startsWith("depends_on_step_")) {
          val depId = v.stripPrefix("depends_on_step_").stripSuffix("_result").toIntOption.getOrElse(0)
          stepResults.getOrElse(depId, v) // 解決できなければ元の値を使う
        } else {
          v
        }
        (k, resolved)
      }

      val argsObj = JsonObject.fromMap(resolvedArgs.map { case (k, v) => k -> Json.fromString(v) })
      val result = ToolDispatch.dispatch(step.tool, argsObj)
      stepResults = stepResults + (step.id -> result)

      logger.toolCall(idx, step.tool, resolvedArgs.toString, result)
      println(s"    Result: ${result.take(100).replace("\n", " ")}")
    }
  }

  // --- Adaptive Planning (= AgentLoop そのもの) ---

  def runAdaptive(task: String, config: AgentConfig, logger: ConversationLogger, turnNum: Int): TurnResult = {
    val state = new ConversationState(s"stage5-adaptive-$turnNum")
    state.add(ChatMessage.System(config.systemPrompt))
    state.add(ChatMessage.User(task))
    logger.turnStart(turnNum, s"[Adaptive] $task")

    val (result, updatedMessages) = AgentLoop.runTurn(state.messages, config)

    // ツール呼び出しログを記録
    val newMessages = updatedMessages.drop(state.messageCount)
    var toolCallIdx = 0
    for (msg <- newMessages) {
      msg match {
        case ChatMessage.ToolResult(_, content) =>
          val callLog = result.toolCalls.lift(toolCallIdx).getOrElse("unknown")
          val name = callLog.takeWhile(_ != '(')
          val args = callLog.drop(name.length)
          logger.toolCall(toolCallIdx, name, args, content)
          toolCallIdx += 1
        case _ => ()
      }
    }

    val answer = result.response.content.getOrElse("(empty)")
    logger.assistantResponse(answer, result.totalTokens, 0, updatedMessages.size)

    println(s"  Answer: ${answer.take(200).replace("\n", " ")}")
    println(s"  Tool calls: ${result.toolCalls.mkString(", ").take(100)}")
    println(s"  Tokens: ${result.totalTokens}")
    result
  }

  // --- テストタスク ---

  val tasks = List(
    ("T1", "個人情報に関する法律を探し、その法律の第1条の条文を取得してください。"),
    ("T2", "消費者に関する法律を3つ探し、それぞれの第1条の条文を取得して、目的規定を比較してください。"),
    ("T3", "皇族に対する尊崇義務に関する法律の第1条と、民法第1条を比較してください。"),
  )

  def main(args: Array[String]): Unit = {
    println(s"=== Stage 5: Planning Comparison ===")
    println(s"LLM_BASE_URL = ${baseConfig.baseUrl}")
    println()

    val logger = new ConversationLogger(Paths.get("stages/stage5/conversation-log.md"))
    logger.header("Stage 5: Planning Comparison Log", baseConfig)

    // === Part 1: Plan-then-Execute ===
    println("=" * 60)
    println("Part 1: Plan-then-Execute")
    println("=" * 60)

    for ((id, task) <- tasks) {
      println(s"\n--- $id (Plan-then-Execute): ${task.take(40)}... ---")
      logger.turnStart(0, s"[Plan-then-Execute] $id: $task")

      generatePlan(task, baseConfig) match {
        case Right(plan) =>
          println(s"  Plan: ${plan.steps.size} steps")
          for (s <- plan.steps) {
            println(s"    ${s.id}. ${s.tool}(${s.args}) — ${s.purpose}")
          }
          executePlan(plan, logger, 1)
          logger.assistantResponse(s"Plan executed: ${plan.steps.size} steps", 0, 0, 0)
        case Left(err) =>
          println(s"  Plan generation failed: $err")
          logger.assistantResponse(s"Plan failed: $err", 0, 0, 0)
      }
      println()
    }

    // === Part 2: Adaptive Planning ===
    println("=" * 60)
    println("Part 2: Adaptive Planning")
    println("=" * 60)

    for ((id, task) <- tasks.take(2)) {  // T1, T2 のみ（T3 は Part 3 で制御実験）
      println(s"\n--- $id (Adaptive): ${task.take(40)}... ---")
      runAdaptive(task, baseConfig, logger, tasks.indexOf((id, task)) + 10)
      println()
    }

    // === Part 3: ネガティブタスク + SystemPrompt 制御 ===
    println("=" * 60)
    println("Part 3: Negative task + SystemPrompt control")
    println("=" * 60)

    val (negId, negTask) = tasks(2)  // T3

    println(s"\n--- $negId (Adaptive, 制御なし): ---")
    runAdaptive(negTask, baseConfig, logger, 20)

    println(s"\n--- $negId (Adaptive, フォールバック制御あり): ---")
    runAdaptive(negTask, controlledConfig, logger, 21)

    logger.summary(0, 0)
    logger.save()

    println("\n" + "=" * 60)
    println("Experiment complete. Log: stages/stage5/conversation-log.md")
  }
}

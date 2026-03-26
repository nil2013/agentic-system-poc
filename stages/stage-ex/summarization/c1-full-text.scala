// Stage EX: C1 — 全文一発要約
// Run: scala-cli run stages/stage-ex/summarization/c1-full-text.scala

//> using file common.scala

@main def run(): Unit = {
  val conditionId = "c1"
  println(s"=== Condition C1: Full Text Single Shot ===")
  println(s"  LLM: ${Config.BaseUrl} (model: ${Config.Model})")
  println(s"  max_tokens: ${Config.MaxTokens}")
  println()

  // Step 1: 法令データ取得 + 全文抽出
  val root = fetchLawData(Config.CivilCodeLawId)
  val fullText = extractFullText(root)
  println(f"  Full text: ${fullText.length}%,d chars (~${fullText.length / 3}%,d est. tokens)")
  println()

  // Step 2: LLM 呼び出し
  println("--- Summarization ---")
  val userPrompt = s"以下は民法（明治二十九年法律第八十九号）の全文です。法令調査のための要約を作成してください。\n\n$fullText"
  val result = callLlm(Prompts.FullTextSummary, userPrompt)
  printResult("Full text", result, fullText.length)

  // Step 3: 結果保存
  val thinkingLog = new StringBuilder("# C1 Thinking Log\n\n")
  result.reasoningContent.foreach { r =>
    thinkingLog.append(s"## Full Text Summarization\n\n$r\n\n")
  }

  val metrics = buildMetricsC1(result, fullText.length)
  saveMetricsJson(conditionId, metrics)
  saveSummary(conditionId, result.content)
  saveThinking(conditionId, thinkingLog.toString)

  printFinalSummary("C1", result.elapsedMs, result.promptTokens, result.completionTokens)
}

// Stage EX: C4 — Map-Reduce 逐次処理（1並列）
// Run: scala-cli run stages/stage-ex/summarization/c4-map-reduce-seq.scala

//> using file common.scala

@main def run(): Unit = {
  val conditionId = "c4"
  println(s"=== Condition C4: Map-Reduce Sequential ===")
  println(s"  LLM: ${Config.BaseUrl} (model: ${Config.Model})")
  println(s"  max_tokens: ${Config.MaxTokens}")
  println(s"  parallelism: sequential (1)")
  println()

  // Step 1: 法令データ取得
  val root = fetchLawData(Config.CivilCodeLawId)
  val parts = extractPartTexts(root)
  println(s"  Parts: ${parts.size}")
  parts.zipWithIndex.foreach { case ((name, text), i) =>
    println(f"    [$i] $name: ${text.length}%,d chars")
  }
  println()

  // Step 2: Phase 1 — 各編を逐次要約
  println("--- Phase 1: Sequential summarization ---")
  val phase1Start = System.nanoTime()
  var partResults = List.empty[PartResult]
  var thinkingLog = new StringBuilder("# C4 Thinking Log\n\n")

  parts.zipWithIndex.foreach { case ((name, text), i) =>
    println(s"  [$i] $name ...")
    val userPrompt = s"以下は民法の「$name」の条文です。要約してください。\n\n$text"
    val result = callLlm(Prompts.PartSummary, userPrompt)

    partResults = partResults :+ PartResult(i, name, text.length, result)
    printResult(s"[$i] $name", result, text.length)

    // Thinking 記録
    result.reasoningContent.foreach { r =>
      thinkingLog.append(s"## Phase 1: $name\n\n$r\n\n---\n\n")
    }
    // パート要約保存
    savePartSummary(conditionId, i, name, result.content)

    println()
  }

  val phase1WallMs = (System.nanoTime() - phase1Start) / 1_000_000
  println(f"  Phase 1 total wall time: ${phase1WallMs / 1000.0}%.1f s")
  println()

  // Step 3: Phase 2 — 統合要約
  println("--- Phase 2: Combine ---")
  val partSummaries = partResults.map(pr => (pr.partName, pr.result.content))
  val phase2InputChars = partSummaries.map(_._2.length).sum
  val phase2Result = runPhase2Combine(partSummaries)
  printResult("Phase 2 (combine)", phase2Result, phase2InputChars)

  phase2Result.reasoningContent.foreach { r =>
    thinkingLog.append(s"## Phase 2: Combine\n\n$r\n\n")
  }

  // Step 4: 結果保存
  val totalWallMs = phase1WallMs + phase2Result.elapsedMs
  val totalPrompt = partResults.map(_.result.promptTokens).sum + phase2Result.promptTokens
  val totalCompletion = partResults.map(_.result.completionTokens).sum + phase2Result.completionTokens

  val metrics = buildMetricsMapReduce(
    conditionId = "C4",
    parallelism = "sequential",
    partResults = partResults,
    phase1WallMs = phase1WallMs,
    phase2Result = phase2Result,
    phase2InputChars = phase2InputChars
  )
  saveMetricsJson(conditionId, metrics)
  saveSummary(conditionId, phase2Result.content)
  saveThinking(conditionId, thinkingLog.toString)

  printFinalSummary("C4", totalWallMs, totalPrompt, totalCompletion)
}

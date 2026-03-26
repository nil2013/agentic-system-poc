// Stage EX: C3 — Map-Reduce 3並列×2バッチ
// Run: scala-cli run stages/stage-ex/summarization/c3-map-reduce-3p.scala

//> using file common.scala

@main def run(): Unit = {
  val conditionId = "c3"
  val parallelism = 3
  val batches = List(List(0, 1, 2), List(3, 4, 5))

  println(s"=== Condition C3: Map-Reduce 3-parallel × 2 batches ===")
  println(s"  LLM: ${Config.BaseUrl} (model: ${Config.Model})")
  println(s"  max_tokens: ${Config.MaxTokens}")
  println(s"  parallelism: $parallelism, batches: ${batches.map(_.mkString("[",",","]")).mkString(" ")}")
  println()

  // Step 1: 法令データ取得
  val root = fetchLawData(Config.CivilCodeLawId)
  val parts = extractPartTexts(root)
  println(s"  Parts: ${parts.size}")
  parts.zipWithIndex.foreach { case ((name, text), i) =>
    println(f"    [$i] $name: ${text.length}%,d chars")
  }
  println()

  // Step 2: Phase 1 — バッチ並列要約
  println("--- Phase 1: Batched parallel summarization ---")
  val (partResults, phase1WallMs) = runPhase1Batched(parts, batches, parallelism)
  println(f"  Phase 1 total wall time: ${phase1WallMs / 1000.0}%.1f s")
  println()

  // Thinking 記録 + パート保存
  val thinkingLog = new StringBuilder("# C3 Thinking Log\n\n")
  partResults.foreach { pr =>
    pr.result.reasoningContent.foreach { r =>
      thinkingLog.append(s"## Phase 1: ${pr.partName}\n\n$r\n\n---\n\n")
    }
    savePartSummary(conditionId, pr.partIndex, pr.partName, pr.result.content)
  }

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

  val metrics = buildMetricsMapReduce("C3", s"${parallelism}-parallel x ${batches.size}-batches",
    partResults, phase1WallMs, phase2Result, phase2InputChars)
  saveMetricsJson(conditionId, metrics)
  saveSummary(conditionId, phase2Result.content)
  saveThinking(conditionId, thinkingLog.toString)

  printFinalSummary("C3", totalWallMs, totalPrompt, totalCompletion)
}

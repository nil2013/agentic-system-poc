// Stage EX: C5 — Map-Reduce 完全並列（6リクエスト同時投入、サーバ n_parallel=4）
// Run: scala-cli run stages/stage-ex/summarization/c5-map-reduce-full.scala

//> using file common.scala

@main def run(): Unit = {
  val conditionId = "c5"
  val parallelism = 6
  // 全パートを1バッチに（サーバ側で4スロットに振り分け、2つはキュー待ち）
  val batches = List(List(0, 1, 2, 3, 4, 5))

  println(s"=== Condition C5: Map-Reduce Full Parallel ===")
  println(s"  LLM: ${Config.BaseUrl} (model: ${Config.Model})")
  println(s"  max_tokens: ${Config.MaxTokens}")
  println(s"  parallelism: $parallelism (server n_parallel=4, 2 will queue)")
  println()

  // Step 1: 法令データ取得
  val root = fetchLawData(Config.CivilCodeLawId)
  val parts = extractPartTexts(root)
  println(s"  Parts: ${parts.size}")
  parts.zipWithIndex.foreach { case ((name, text), i) =>
    println(f"    [$i] $name: ${text.length}%,d chars")
  }
  println()

  // Step 2: Phase 1 — 全パート同時投入
  println("--- Phase 1: Full parallel summarization ---")
  val (partResults, phase1WallMs) = runPhase1Batched(parts, batches, parallelism)
  println(f"  Phase 1 total wall time: ${phase1WallMs / 1000.0}%.1f s")
  println()

  // Thinking 記録 + パート保存
  val thinkingLog = new StringBuilder("# C5 Thinking Log\n\n")
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

  val metrics = buildMetricsMapReduce("C5", s"full-parallel ($parallelism threads, server 4 slots)",
    partResults, phase1WallMs, phase2Result, phase2InputChars)
  saveMetricsJson(conditionId, metrics)
  saveSummary(conditionId, phase2Result.content)
  saveThinking(conditionId, thinkingLog.toString)

  printFinalSummary("C5", totalWallMs, totalPrompt, totalCompletion)
}

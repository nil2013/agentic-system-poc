# LLM エージェントの Planning アーキテクチャ調査

> 調査日: 2026-03-24
> 契機: Stage 5 実験で Plan-then-Execute が全滅、Adaptive が完璧に動作した結果を受けて

---

## 背景

Stage 5 で Plan-then-Execute と Adaptive Planning を比較した結果、Plan-then-Execute は全タスクで失敗した（前ステップの結果から情報を抽出する処理が静的計画に含められない）。この結果が既存研究でどう整理されているか調査した。

## 分類軸: 計画粒度 × 再計画頻度

文献上の整理は二項対立ではなく、2軸で分類される:

| アプローチ | 計画粒度 | 再計画 | 代表手法 | 論文 |
|-----------|---------|--------|---------|------|
| 純粋 ReAct | なし（都度判断） | 毎ステップ | ReAct | Yao et al. 2022 |
| Plan-then-Execute | 詳細 | なし | ReWOO | Xu et al. 2023 |
| Plan-and-Replan | 詳細 | 各ステップ後 | Plan-and-Act | Sun et al. 2025 |
| As-needed decomposition | なし → 失敗時のみ生成 | 失敗時 | ADaPT | Prasad et al. 2024 |
| 階層的計画 | 高レベル + 低レベル | サブゴール失敗時 | HiPlan | 2025 |

## 各アプローチの詳細

### ReAct (Yao et al. 2022)

思考（Thought）と行動（Action）を交互に生成。明示的な計画を持たず、各ステップで判断。

- **強み**: 短期タスクで高い柔軟性。ツール結果に即座に対応
- **弱み**: 長期タスクでのドリフト（目標を見失う）、グローバルな一貫性の欠如
- **批判**: Stechly et al. (2024) — ReAct の有効性は few-shot 例との類似度に依存し、「近似検索」として機能している可能性。例がずれると成功率が一桁に崩壊

### ReWOO (Xu et al. 2023)

Planner / Worker / Solver の3モジュール。Planner が全計画を生成し、Worker が逐次実行、Solver が統合。

- **強み**: トークン効率（ReAct 比 5x）。Planner は1回だけ呼ばれる
- **弱み**: 動的コンテンツに対応不能（Stage 5 T1, T2 の失敗と同じ構造）
- **本プロジェクトとの対応**: Stage 5 の Plan-then-Execute は ReWOO 型の簡易版

### Plan-and-Act (Sun et al. 2025)

Planner と Executor を分離。各 Executor ステップ後に Planner が残りの計画を再生成。

- **成績**: WebArena-Lite で 57.58%（静的計画比 +10.3pt）
- **本質**: 「計画を立てるが、柔軟に修正する」。計画のグローバルな構造と実行の局所的な適応を両立
- **本プロジェクトへの示唆**: Stage 5 の Plan-then-Execute の失敗を解消しうる最も有望な中間アプローチ

### ADaPT (Prasad et al. 2024)

まず実行を試み、失敗した場合のみサブタスクに分解。分解は再帰的（AND/OR 論理演算子）。

- **成績**: ALFWorld でベースライン比 +28.3%
- **設計哲学**: 最も保守的 — 計画は必要なときだけ

### HiPlan (2025)

高レベルのサブゴールと低レベルの行動生成を分離。事後条件チェックでサブゴール失敗を検知し再計画。

## 静的計画の既知の失敗モード

| 失敗モード | 説明 | Stage 5 との対応 |
|-----------|------|----------------|
| 動的コンテンツ盲目 | ツール出力に応じた後続ステップの変更ができない | T1, T2 の `depends_on_step_N_result` 問題 |
| カスケードエラー | ツール失敗時のルート変更ができない | T3 の計画生成失敗 |
| 過剰指定 | 計画ステップの 29.5% が不要に細かい (Jang 2026) | — |
| ツール合成の脆弱性 | GPT-4o でもネストされた API 呼び出しチェーンの完全成功率は 28% (NESTFUL) | — |

## 重要な発見: 実行の失敗 > 計画の失敗

Jang et al. (2026) の分析が示唆的:
- LLM エージェントの失敗原因は**計画の質ではなく実行時のエラー**が支配的
- グラウンディングエラー（ハルシネーション）、状態追跡の喪失、ツール結果の誤解釈
- **「静かなフォールバック」はまさにこの実行時エラーの一形態**

## 本プロジェクトへの示唆

1. **Stage 5 の結果は文献と完全に整合**: Plan-then-Execute の失敗は ReWOO 型の既知の限界
2. **現在の Adaptive（ReAct 型）は短期タスクに適切**: 法令検索 + 条文取得のような 2-4 ステップのタスクでは十分
3. **長期タスクへの拡張には Plan-and-Replan が有望**: ただし PoC の範囲では不要かもしれない
4. **失敗の主因はツール実行時の挙動（静かなフォールバック等）**: 計画の改善より実行の信頼性向上（Stage 6 の `tool_result_consistent`）が優先度高い

## 参考文献

| # | 論文 | URL |
|---|------|-----|
| 1 | Huang et al., "Understanding the planning of LLM agents: A survey" (2024) | https://arxiv.org/abs/2402.02716 |
| 2 | Masterman et al., "The Landscape of Emerging AI Agent Architectures" (2024) | https://arxiv.org/abs/2404.11584 |
| 3 | Yao et al., "ReAct: Synergizing Reasoning and Acting" (ICLR 2023) | https://arxiv.org/abs/2210.03629 |
| 4 | Stechly et al., "On the Brittle Foundations of ReAct Prompting" (2024) | https://arxiv.org/abs/2405.13966 |
| 5 | Xu et al., "ReWOO: Decoupling Reasoning from Observations" (2023) | https://arxiv.org/abs/2305.18323 |
| 6 | Sun et al., "Plan-and-Act: Improving Planning for Long-Horizon Tasks" (2025) | https://arxiv.org/abs/2503.09572 |
| 7 | Rawat et al., "Pre-Act: Multi-Step Planning and Reasoning" (2025) | https://arxiv.org/abs/2505.09970 |
| 8 | Prasad et al., "ADaPT: As-Needed Decomposition and Planning" (NAACL 2024) | https://aclanthology.org/2024.findings-naacl.264/ |
| 9 | HiPlan: Hierarchical Planning for LLM Agents (2025) | https://arxiv.org/abs/2508.19076 |
| 10 | Jang et al., "Why Do LLM-based Web Agents Fail?" (2026) | https://arxiv.org/abs/2603.14248 |
| 11 | "A Modern Survey of LLM Planning Capabilities" (ACL 2025) | https://aclanthology.org/2025.acl-long.958.pdf |
| 12 | Basu et al., "NESTFUL: Nested API Call Benchmark" (2024) | — |

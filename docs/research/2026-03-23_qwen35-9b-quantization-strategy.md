# Qwen3.5-9B 量子化戦略調査

> 調査日: 2026-03-23
> 目的: 自宅環境（Apple Silicon Mac, 24GB）で mlx-lm 経由の tool calling に使う Qwen3.5-9B の量子化方式を選定する

## 調査体制

4つのエージェント（Claude Code 内部 ×2、Gemini 3.1 Pro、ChatGPT 5.4-Thinking）で同一プロンプトを並列調査し、結果を統合した。Claude (Web) にも同プロンプトで調査を依頼。

---

## 結論: 8-bit を採用

**全エージェントが 8-bit 推奨で一致。** BF16 は 24GB で運用不可、4-bit は tool calling の精度劣化リスクが未検証。

| 量子化 | 判定 | 理由 |
|--------|------|------|
| BF16 (~18GB) | **不可** | モデル+OS で 23GB。KV キャッシュの余地なし |
| 8-bit (~10GB) | **採用** | near-lossless。~9GB の KV キャッシュ余力 |
| 4-bit (~5.6GB) | **フォールバック** | メモリ余裕大だが tool calling 精度に未知のリスク |

使用モデル: `mlx-community/Qwen3.5-9B-MLX-8bit`（~10.4GB, group_size=64, ~8.86 bpw）

---

## 調査結果の詳細

### 1. MLX 量子化手法の特性

- MLX は **groupwise affine 量子化**（group_size=64, scale+bias per group）
- GGUF K-quant（Q4_K_M 等）は階層的 double quantization + mixed-precision per layer で、同一 bit 幅では GGUF の方が精度が高い傾向（ソース: コミュニティ報告、実機確認）
- **8-bit ではこの差は無視できる**。差が顕著になるのは 4-bit 以下
- MLX は MXFP4/MXFP8 もサポートするが、mlx-community の Qwen3.5-9B は標準 affine で変換済み

**Source:** MLX 公式ドキュメント (ml-explore.github.io), llama.cpp discussions

### 2. 量子化別ベンチマーク

#### Qwen3.5-9B 固有の量子化比較データは存在しない

Qwen3 8B（先代アーキテクチャ）での実測値（arXiv:2505.02214）:

| 精度 | C4 Perplexity | MMLU (5-shot) |
|------|--------------|---------------|
| FP16 | 13.3 | 74.7% |
| 8-bit AWQ | 13.3 | 74.7% |
| 4-bit AWQ | 14.2 | 71.9% |
| 4-bit GPTQ | 13.8 | 71.6% |

- 8-bit は劣化なし
- 4-bit は MMLU で -2.8〜-3.1pt の劣化
- 論文は「4-bit が noticeable degradation の閾値」と明記

#### Tool calling × 量子化の直接測定データは存在しない

- Red Hat 50万件評価: Coding/STEM タスクで量子化劣化が最大（tool calling と特性が類似）
- GGUF ガイドでは code generation / agent 用途に Q5_K_M 以上を推奨
- Unsloth は Qwen3.5 の QLoRA (4-bit) トレーニングを「非推奨」としている（量子化感度が高い）

**Source:** arXiv:2505.02214, Red Hat evaluation, Unsloth docs

### 3. 24GB Apple Silicon でのメモリ予算

#### モデルサイズと KV キャッシュ

Qwen3.5-9B は **ハイブリッドアーキテクチャ**（32層中8層が full attention、24層が Gated DeltaNet）。KV キャッシュは full attention 層のみで発生し、通常の Transformer の **約 1/4**。

KV キャッシュ（BF16）: ~32KB/token

| コンテキスト長 | KV キャッシュ |
|---------------|-------------|
| 8K | 256 MB |
| 16K | 512 MB |
| 32K | 1.0 GB |

#### メモリバジェット（OS 5GB 想定、保守的）

| 量子化 | モデル | OS | KV (8K) | 合計 | 余力 |
|--------|-------|-----|---------|------|------|
| BF16 | ~18.0 GB | 5 GB | 0.26 GB | 23.3 GB | **0.7 GB** ⚠️ |
| 8-bit | ~10.0 GB | 5 GB | 0.26 GB | 15.3 GB | **8.7 GB** ✅ |
| 4-bit | ~5.6 GB | 5 GB | 0.26 GB | 10.9 GB | **13.1 GB** ✅ |

#### MLX のメモリ管理に関する注意

- MLX に「モデルサイズの2倍のメモリを消費する」バグ報告あり（Issue #2254）
- KV キャッシュ未制限時に kernel panic に至った事例あり（Issue #883）
- **`--max-kv-size` の設定を推奨**

**Source:** mlx-community model cards, Qwen3.5-9B config.json, MLX GitHub issues

### 4. Qwen3.5-9B の Tool Calling 能力

#### ベンチマーク（BF16 ベースライン）

| モデル | BFCL-V4 | TAU2-Bench | Active Params |
|--------|---------|------------|---------------|
| Qwen3.5-9B | **66.1** | **79.1** | 9B (dense) |
| Qwen3.5-35B-A3B | 67.3 | 81.2 | ~3B (MoE) |
| Qwen3.5-4B | 50.3 | 79.9 | 4B (dense) |

- 9B と 35B-A3B の差はたった **1.2pt**
- 9B → 4B では **15.8pt** 落ちる。9B は tool calling の実用ラインを十分に超えている
- Together AI も「production-ready function calling」と評価

#### 既知の failure mode

1. **パーサー起因**: `qwen3_coder` フォーマットを使う必要がある（Ollama v0.17.6 で修正済み）
2. **テキストへのフォールバック**: tool call を構造化出力ではなくテキストとして出力する
3. **Parallel calling の弱さ**: single-tool は安定だが multi-tool / parallel は脆弱
4. **Chat template の正確性が量子化レベルと同等以上に重要**（Unsloth の修正で改善）

**Source:** Qwen3.5-9B/35B-A3B 公式 model card, Ollama issues, Together AI

---

## mlx-community 上の利用可能バリアント

| Model ID | BPW | Disk Size |
|----------|-----|-----------|
| `Qwen3.5-9B-MLX-bf16` | 16 | ~18.0 GB |
| `Qwen3.5-9B-MLX-8bit` | ~8.86 | ~10.4 GB |
| `Qwen3.5-9B-6bit` | ~6 | ~8.19 GB |
| `Qwen3.5-9B-5bit` | ~5 | ~7.07 GB |
| `Qwen3.5-9B-OptiQ-4bit` | ~4.5 | ~6.04 GB |
| `Qwen3.5-9B-MLX-4bit` | ~5.06 | ~5.95 GB |

5-bit / 6-bit も中間選択肢として存在する（ChatGPT が発見）。

---

## GPU WS での 9B 使用について

GPU WS (32GB VRAM) では 9B-BF16 もロード可能だが、35B-A3B を使い続ける方が合理的:

- BFCL-V4: 35B-A3B (67.3) > 9B (66.1)
- **推論速度**: MoE 3B active は毎トークンで 3B 分の重みしか読まない。dense 9B は 9B 分。memory bandwidth bound な LLM inference では MoE が ~3x 速い
- VRAM: 両方 32GB に収まるが、同時にはロードできない

→ GPU WS では 35B-A3B 一択。9B は Mac ローカル専用。

---

## 次のアクション

1. `mlx-community/Qwen3.5-9B-MLX-8bit` で mlx-lm サーバーを起動し tool calling の基本動作を検証
2. `--max-kv-size` を設定してメモリ安全性を確保
3. 実際の tool calling ワークフローでのピークメモリを Activity Monitor で確認
4. 大学 WS (35B-A3B) との同一テストケース比較で品質差を定量化
5. 8-bit で問題があれば OptiQ-4bit → uniform 4-bit の順でフォールバック

---

## 参考文献

| # | Source | URL |
|---|--------|-----|
| 1 | MLX 公式ドキュメント (mlx.core.quantize) | https://ml-explore.github.io/mlx/build/html/python/_autosummary/mlx.core.quantize.html |
| 2 | An Empirical Study of Qwen3 Quantization | https://arxiv.org/html/2505.02214v1 |
| 3 | Qwen3.5-9B 公式 model card | https://huggingface.co/Qwen/Qwen3.5-9B |
| 4 | Qwen3.5-35B-A3B 公式 model card | https://huggingface.co/Qwen/Qwen3.5-35B-A3B |
| 5 | Red Hat 量子化評価 (50万件+) | https://developers.redhat.com/articles/2024/10/17/we-ran-over-half-million-evaluations-quantized-llms |
| 6 | Unsloth Qwen3.5 GGUF Benchmarks | https://unsloth.ai/docs/models/qwen3.5/gguf-benchmarks |
| 7 | mlx-community/Qwen3.5-9B-MLX-8bit | https://huggingface.co/mlx-community/Qwen3.5-9B-MLX-8bit |
| 8 | mlx-community/Qwen3.5-9B-OptiQ-4bit | https://huggingface.co/mlx-community/Qwen3.5-9B-OptiQ-4bit |
| 9 | MLX Issue #2254 (double memory) | https://github.com/ml-explore/mlx/issues/2254 |
| 10 | MLX-LM Issue #883 (kernel panic) | https://github.com/ml-explore/mlx-lm/issues/883 |
| 11 | Apple ML Research – M5 + MLX | https://machinelearning.apple.com/research/exploring-llms-mlx-m5 |
| 12 | Ollama Issue #14745 (tool call bug) | https://github.com/ollama/ollama/issues/14745 |
| 13 | Berkeley BFCL Leaderboard | https://gorilla.cs.berkeley.edu/leaderboard.html |
| 14 | Comprehensive Evaluation of Quantization | https://arxiv.org/html/2402.16775v1 |
| 15 | vllm-mlx (Apple Silicon inference) | https://arxiv.org/html/2601.19139 |

# Stage EX: 法令要約戦略の比較実験

## 目的

法令全文の LLM 要約における最適な処理戦略を特定する。具体的には、全文一発投入と階層分割（Map-Reduce）の比較、および並列度が処理速度・品質に与える影響を定量的に計測する。

## 背景

- KV キャッシュ分析（`docs/research/2026-03-26_qwen35-35b-kv-cache-analysis.md`）で 262K tokens のコンテキスト制約消失を確認
- 予備実験（`docs/research/2026-03-26_law-summarization-experiment.md`）で全文一発（81s, 品質良好）と階層分割（254s, 品質より高い）の初期比較を実施
- 予備実験で判明した課題: max_tokens=4096 で Thinking が content を圧迫（content=0 問題）、並列度の最適値が未確定

## 対象法令

民法（明治二十九年法律第八十九号、lawId: `129AC0000000089`）

五編構成 + 附則。全文 ~213K chars（Sentence+Caption+Title 抽出）、~122K prompt tokens。

## 実験条件

全条件共通: `max_tokens=8192`, `temperature=0.0`, 同一 system prompt

| 条件 | 戦略 | 並列度 | 説明 |
|------|------|--------|------|
| **C1** | 全文一発 | — | 法令全文を1リクエストで要約 |
| **C2** | Map-Reduce | 2並列×3バッチ | 6パートを2ずつ3回に分けて処理→統合 |
| **C3** | Map-Reduce | 3並列×2バッチ | 6パートを3ずつ2回に分けて処理→統合 |
| **C4** | Map-Reduce | 逐次（1並列） | 6パートを1つずつ処理→統合。競合ゼロの理想値 |
| **C5** | Map-Reduce | 完全並列（4スロット） | 6パートを一斉投入（n_parallel=4、2パートはキュー待ち）→統合 |

C1, C5 は予備実験で実施済み。今回は max_tokens=8192 での再実験 + C2, C3, C4 を新規実施。

## 計測項目

### 各パート（Phase 1）
- Wall time (s)
- Prompt tokens
- Completion tokens（うち Thinking tokens / Content tokens）
- PP 速度 (tok/s)
- TG 速度 (tok/s)
- 出力文字数

### 統合要約（Phase 2）
- 同上

### 全体
- 総 wall time（Phase 1 + Phase 2）
- 総 prompt tokens / completion tokens
- GPU ピーク温度

### 品質（定性）
- 条番号の正確性
- 附則・経過措置の記述の具体性
- 構造の解像度（編/章/節レベル）
- 要約6要件（目的・構造・定義・中核規律・罰則・附則）の充足度

## 記録するデータ

各実行ごとに `results/` に保存:
- `cN_result.json`: API レスポンス（usage, content, reasoning_content）
- `cN_summary.md`: 生成された要約テキスト
- `cN_thinking.md`: Thinking ブロックの内容（分析用）
- `cN_metrics.json`: 計測結果の構造化データ

## 実装方針

- scala-cli スクリプトで実装。各条件ごとに独立したスクリプト
- 共通ユーティリティ（API 呼び出し、XML 抽出、計測）は `common.scala` に集約
- 環境変数 `LLM_BASE_URL` で接続先を指定（sbt プロジェクトと同一の慣例）
- e-Gov API V1 の `/lawdata` エンドポイントで法令全文を取得

## サーバ構成

```bash
llama-server \
  -m Qwen3.5-35B-A3B-UD-Q4_K_XL.gguf \
  --host 0.0.0.0 --port 8080 \
  -ngl 99 -c 262144 --jinja -fa on
# n_parallel=4（デフォルト auto）
```

## 成果物

- `RESULTS.md`: 全条件の比較結果、分析、設計指針
- `NOTES.md`: 実験中の観察メモ
- `results/`: 生データ
- scala-cli スクリプト群: 再現可能な実験プロトコル（code as documents）

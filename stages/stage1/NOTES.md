# Stage 1 実験ノート

実験中の観察・気付きを時系列で記録。最終的に RESULTS.md の Results/Discussion に統合する。

---

## 2026-03-23 12:50 — max_tokens=1024 で全 FAIL

- 45/45 試行が FAIL（JSON パース成功率 0%）
- 原因: Qwen3.5 の thinking mode が ~1500-2000 tokens を消費し、`content` に到達する前に `max_tokens` に達する
- `content` は空文字列（`""`）、`reasoning_content` が ~3700文字
- `finish_reason: length` で打ち切り
- **教訓**: Qwen3.5 で構造化出力を得るには thinking のトークン消費を見込んだ余裕が必須

## 2026-03-23 12:55 — /no_think が効かない

- SystemPrompt に `/no_think` を含めても thinking は停止しなかった
- llama-server 経由では Qwen3.5 の thinking 制御トークンが正しく解釈されていない可能性
- → `max_tokens=4096` で力技で解決

## 2026-03-23 13:00 — max_tokens=4096 で再実行、全試行 PARTIAL(caption)

- JSON パース成功率: 全手法で 100%（45/45）
- 不一致フィールドは `caption` のみ
- モデル出力: `（不法行為による損害賠償）`（括弧付き）
- Ground truth: `不法行為による損害賠償`（括弧なし）
- **XML の `ArticleCaption` テキストは括弧付き**なので、モデルの抽出は正しい。Ground truth 側のバグ
- → ground truth を括弧付きに修正して再実行予定

## 2026-03-23 13:00 — 実験A でコードフェンスが付く可能性

- curl での手動テスト時、プロンプトのみ（response_format なし）で ` ```json ... ``` ` が付いた
- しかしスクリプトの実験A では JSON パースが成功している → スクリプト側でコードフェンスを剥がしてる？いや、そんなコードはない
- **仮説**: SystemPrompt に「JSONのみを出力し、それ以外のテキストは一切含めないでください」と強く指示しているため、temperature=0.0 では遵守されている。curl テストでは SystemPrompt が異なっていた
- → 再実行後にサンプル出力を目視確認する必要あり

## 2026-03-23 13:05 — 全手法で結果が均一すぎる

- A/B/C 全手法で PARTIAL(caption) のみ、レイテンシも ~144 tok/s で均一
- **ガイドの予想（A: 80-90%, B: 100%, C: 100%+スキーマ準拠）との乖離が大きい**
- 仮説: Qwen3.5-35B-A3B の SystemPrompt スキーマ遵守率が高すぎて、JSON mode/Schema の恩恵が見えない
- これは「良い結果」だが、手法間の差が出ないと比較実験として情報量が少ない
- → Discussion で「モデル能力が高い場合、制約手法の差が見えにくい」という知見として記録
- → UserPrompt にスキーマを移した追加実験で差が出る可能性（ユーザーの Qwen3.5 知見を検証）

## 2026-03-23 13:10 — Ground truth 修正後の full retry

- ground truth の caption を括弧付きに修正し、45 回全試行を再実行（キャッシュではなく full retry）
- これにより、caption 以外のフィールドと JSON パース成功率は **90 サンプル**（修正前 45 + 修正後 45）で報告可能
- caption フィールドは修正後の 45 サンプルのみ有効

## 2026-03-23 13:15 — Ground truth 修正後の再実行結果

- **全手法・全条文・全試行で 100% 正解** (45/45)
- 修正前の 45 サンプル（caption 除く）と合算して JSON パース成功率 90/90 = 100%
- 手法間の差がゼロ。ガイドの予想（A: 80-90%）とは大きく乖離
- → Discussion に「モデル能力の天井効果」「temperature=0.0 の影響」として記録

## 2026-03-23 13:05 — 生成速度について

- 全試行で ~144 tok/s。Stage 0 の ~70 tok/s より高い
- 差の原因: Stage 0 は max_tokens=256 で短く、Stage 1 は max_tokens=4096 で長い
- **thinking 部分の生成が速い**（構造化出力部分より thinking の方がトークン生成が速い可能性。要確認）

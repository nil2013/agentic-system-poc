# Stage 6: 自己評価と修正ループ — 実験レポート

> 実施日: 2026-03-24
> 環境: Mac Studio (M4 Pro) → GPU WS (RTX PRO 4500 Blackwell)
> モデル: Qwen3.5-35B-A3B-Q4_K_M (llama-server, --jinja -fa on)
> ガイド参照: `docs/guide/agentic-system-learning-guide-scala.md` §6.1–§6.4
> 実験ノート: [`NOTES.md`](NOTES.md)

---

## 1. Introduction

### 1.1 目的

LLM 出力の品質をプログラム的に評価し、不十分な場合に再生成するパターンを実装する。特に「静かなフォールバック」のプログラム的検出（`tool_result_consistent`）と、SystemPrompt 制御との二重防御を検証する。

### 1.2 予定していた作業

| 作業 | ガイド参照 | 積み残し |
|------|-----------|---------|
| Evaluation case class + EvaluatorPrompt 実装 | §6.2 | — |
| 機械検証（条文番号パターン + ツール結果整合性） | §6.2 | — |
| 修正ループ（最大3回再生成） | §6.3 | — |
| SystemPrompt 制御のデフォルト組み込み | — | Stage 5 |
| tool_result_consistent の二重防御検証 | — | R-07 |

### 1.3 仮説（ガイドの予想）

- 同一モデルの self-consistency bias により、evaluator が generator の出力を「正しい」と判定しがち
- 機械検証可能な項目（source_cited, tool_result_consistent）は LLM 判定より信頼性が高い
- 修正ループで品質は向上するが、再生成コスト（レイテンシ）とのトレードオフがある

---

## 2. Methods

### 2.1 テストケース

| # | クエリ | 期待される動作 | 評価の焦点 |
|---|--------|-------------|-----------|
| Q1 | 「民法709条の条文を教えてください」 | 正常にツール取得 → 回答 | source_cited, answers_question |
| Q2 | 「皇族に対する尊崇義務に関する法律の第1条を教えてください」 | ツールエラー → エラー報告 | **tool_result_consistent** |
| Q3 | 「民法709条と、皇族に対する尊崇義務に関する法律の第1条を比較してください」 | 一方成功・一方失敗 | 複合的な整合性 |

### 2.2 評価基準（5項目）

1. `source_cited`: 条文番号が引用されているか（機械検証: 正規表現）
2. `internally_consistent`: 回答内に矛盾がないか（LLM 判定のみ）
3. `answers_question`: 質問に対する直接的回答が含まれるか（LLM 判定のみ）
4. `tool_result_consistent`: ツール否定結果と回答内容の整合性（**機械検証 + LLM 判定**）
5. `issues`: 問題点のリスト

### 2.3 二重防御

- **予防（SystemPrompt）**: 「ツールがエラーを返した場合はエラーを伝えよ」
- **検出（プログラム）**: ツール結果に "エラー:" があるのに回答に条文パターンが含まれる → 不整合
- **検出（LLM evaluator）**: tool_result_consistent を JSON で評価

### 2.4 パラメータ

- generator: max_tokens=4096, temperature=0.0
- evaluator: max_tokens=2048, temperature=0.0
- 修正ループ: 最大3回

---

## 3. Results

### 3.1 評価結果サマリー

| クエリ | 試行数 | 最終判定 | 機械 citation | 機械 toolConsistent | LLM evaluator |
|--------|-------|---------|-------------|-------------------|---------------|
| Q1（民法709条） | 1 | PASS | ✅ | ✅ | 動作（issues あり） |
| Q2（非実在法令） | 1 | PASS | ✅ | ✅ | content 空 |
| Q3（複合） | 1 | PASS | ✅ | ✅ | content 空 |

全クエリが初回でパス。**修正ループは発動しなかった。**

### 3.2 機械検証 vs LLM 判定の一致率

LLM evaluator が動作した Q1 のみ:
- `tool_result_consistent`: 機械=true, LLM=true → **一致**
- `source_cited`: 機械=true, LLM=true → **一致**

サンプルサイズ 1/3（Q2, Q3 で evaluator 失敗）のため統計的意味は限定的。

### 3.3 修正ループの効果

**修正ループは発動しなかった。** 全クエリが初回パス。SystemPrompt 制御 + Adaptive ツール使用の組み合わせが十分に機能し、評価基準を満たす回答が初回で生成された。

### 3.4 tool_result_consistent の検出

- Q1: ツールエラーなし → toolConsistent=true（正しい）
- Q2: `find_laws("皇族 尊崇義務")` は 0件だが、LLM が追加で `find_laws("皇室")` → `get_article("皇室典範", "1")` でツール経由取得に成功 → toolConsistent=true（ツール結果と回答が整合）
- Q3: 同様に非実在法令部分は「見つかりません」と報告、民法709条はツール取得成功 → toolConsistent=true

**「静かなフォールバック」は発生しなかった。** SystemPrompt 制御が効いており、ツールエラー時に内部知識で補完する動作が抑制されている。

### 3.5 会話品質の評価

**Q1**: 正常な条文取得。evaluator が「709条 vs 第七百九条は矛盾」と指摘したが、これは **evaluator の false positive**（漢数字とアラビア数字の対応を理解できていない）。

**Q2**: 非実在法令に対して adaptive に追加検索を実行し、皇室典範をツール経由で発見・提示。「皇族に対する尊崇義務という文言の法律は存在しない」と正直に報告しつつ、関連法令をツールで取得。Stage 5 からの段階的改善:
- Stage 5 制御なし: 内部知識で皇室典範第1条を生成（出典偽装）
- Stage 5 制御あり: 「存在しません」と報告のみ
- **Stage 6**: 「存在しない」と報告 + 関連法令をツールで発見・提示

**Q3**: 複合タスクで「見つかりませんでした、比較できません」と正直に回答。

### 3.6 遭遇した問題

1. **Evaluator の thinking 消費**: Round 1 では max_tokens=2048 で全て失敗。Round 2 で 4096 に引き上げたが、Q2, Q3 では依然として失敗。複雑なクエリほど thinking が長くなる構造的問題
2. **Evaluator の false positive**: Q1 で「709条 vs 第七百九条は矛盾」と誤判定。法学ドメインの評価には条番号表記の正規化が必要
3. **修正ループの未発動**: 全クエリ初回パスのため、修正ループの効果を検証できなかった

---

## 4. Discussion

### 4.1 ガイドの予想との比較

ガイドの予想「self-consistency bias により evaluator が generator の出力を正しいと判定しがち」は、**観察できなかった**（evaluator がそもそも動作したのが Q1 のみ）。Q1 では evaluator が issues を指摘しており（漢数字の誤解だが）、少なくとも「何でも OK」にはなっていない。

ガイドの予想「機械検証可能な項目は LLM 判定より信頼性が高い」は、Q1 の例で一部確認:
- 機械検証 `hasCitation` は「第七百九条」を正しく検出
- LLM evaluator は「709 ≠ 第七百九条」と誤判定
- → 機械検証の方が正確

### 4.2 会話ログに基づく定性的分析

Q2 の挙動が最も示唆的。LLM が:
1. 最初の検索で0件 → **諦めずに別のキーワードで再検索**
2. 関連法令を発見 → **ツール経由で取得**
3. 「元の質問の法律は存在しない」と**正直に報告**

これは SystemPrompt 制御 + Adaptive の組み合わせが生む「誠実かつ能動的」な動作。Stage 5 T3 の「出典偽装」とは質的に異なる。

### 4.3 二重防御の評価

**SystemPrompt 制御（予防）**: 全クエリで静かなフォールバックを抑制。Q2, Q3 で非実在法令に対して正直に報告。

**機械検証（検出）**: 全クエリで正しく判定。ただし「静かなフォールバック」自体が発生しなかったため、**検出能力の検証は不十分**。

**LLM evaluator（検出）**: 3クエリ中 1クエリでのみ動作。thinking 消費により構造的に不安定。

→ 現時点では **SystemPrompt 制御 + 機械検証** の組み合わせが最も実用的。LLM evaluator は thinking 消費問題の解決が先。

### 4.4 evaluator の thinking 消費問題

evaluator が複雑なクエリで動作しない根本原因は、Qwen3.5 の thinking mode が evaluator 呼び出しでも発動し、max_tokens を消費すること。対策候補:
- `response_format: {"type": "json_object"}` で thinking を抑制できるか（llama-server 依存）
- evaluator 専用の thinking-off 設定（llama-server の API レベルで制御可能か）
- evaluator を 9B (thinking-off) で実行し、generator を 35B (thinking-on) で実行する分離構成

### 4.5 Stage 7 への示唆

- Q2 の「追加検索」判断が thinking ブロック内でどう行われているかが分析対象
- evaluator の thinking を観察すれば、「709 ≠ 第七百九条」の誤判定がどういう推論過程で生まれるか見えるはず
- thinking 消費問題自体が Stage 7 の分析テーマ（thinking の有用性 vs 消費のトレードオフ）

---

## 5. 成果物

| ファイル | 内容 |
|---------|------|
| `src/main/scala/stages/Stage6Main.scala` | 自己評価ループ + 機械検証 + LLM evaluator |
| `stages/stage6/RESULTS.md` | 本レポート |
| `stages/stage6/NOTES.md` | 実験ノート |
| `stages/stage6/conversation-log.md` | 会話ログ |

## 6. 次のステップ

- Stage 7（Thinking/Reasoning ブロック分析）に進む
- evaluator の thinking 消費問題の対策を検討（response_format / thinking-off / モデル分離）
- 修正ループが発動する条件を意図的に作り出すテスト（temperature > 0 等）は発展的課題（A6-1, A6-2）として記録

# Stage 1: 構造化出力の制御 — 実験レポート

> 実施日: 2026-03-23
> 環境: Mac Studio (M4 Pro) → GPU WS (RTX PRO 4500 Blackwell)
> モデル: Qwen3.5-35B-A3B-Q4_K_M (llama-server, --jinja -fa on)
> ガイド参照: `docs/agentic-system-learning-guide-scala.md` §1.1–§1.9
> 実験ノート: [`NOTES.md`](NOTES.md) — 実験中の観察・気付きを時系列で記録

---

## 1. Introduction

### 1.1 目的

LLM の出力をプログラムが解釈可能な JSON 形式に制約する3つの手法を比較し、精度（JSON パース成功率・フィールド正答率）とレイテンシの特性を把握する。この結果は Stage 2 以降の tool calling 設計の基盤となる。

### 1.2 予定していた作業

ガイド §1.3–§1.7 に基づき、以下の実験を実施する:

| 実験 | 手法 | ガイド参照 |
|------|------|-----------|
| A | プロンプトのみ（「JSON で出力せよ」） | §1.4 |
| B | `response_format: {"type": "json_object"}` | §1.5 |
| C | `response_format` に JSON Schema 指定 | §1.6 |

各手法 × 3条文（民法709条・1条・5条）× 5試行 = 45回の実験。

評価方法（§1.7）: 入力 XML から機械的に導出した ground truth との完全一致で、JSON パース成功率・フィールド正答率を計測。

### 1.3 ガイドの予想

| 手法 | 予想 JSON パース率 | 予想スキーマ準拠率 |
|------|-------------------|-------------------|
| A（プロンプトのみ） | 80-90% | — |
| B（JSON mode） | 100% | スキーマ不一致あり |
| C（JSON Schema） | 100% | 100%（レイテンシ増の可能性） |

---

## 2. Methods

### 2.1 題材

e-Gov 法令 API V1 から民法 XML を取得し、3条文を抽出:

| 条文 | 構造的特徴 |
|------|-----------|
| 709条 | 単一項。最もシンプル |
| 1条 | 3項構成。複数項の抽出を検証 |
| 5条 | 3項構成、1項・3項に複数 Sentence。テキスト結合の処理を検証 |

### 2.2 実験設計

- **System Prompt**: スキーマ（出力フォーマット指示）を System Prompt 側に集約。Qwen3.5 は System Prompt でのスキーマ遵守率が高い傾向がある（ユーザー報告による経験的知見。→ NOTES.md 参照）
- **max_tokens**: 4096。Qwen3.5 の thinking mode がデフォルト有効で ~1500-2000 tokens を消費するため、content に到達するには十分な余裕が必要（Stage 0 および初回実験で確認。→ NOTES.md 12:50 エントリ）
- **temperature**: 0.0（再現性確保）
- **試行回数**: 各条件5回 × 2ラウンド（ground truth 修正前後で full retry）

### 2.3 Ground Truth

入力 XML から機械的に導出。フィールド:
- `article_number`: `@Num` 属性値
- `caption`: `ArticleCaption` テキスト（括弧付きのまま。→ NOTES.md 13:00 エントリ）
- `title`: `ArticleTitle` テキスト
- `paragraphs`: 各 `Paragraph` の `@Num` と全 `Sentence` テキストの結合

**修正点**: 初回実行時の ground truth では `caption` から括弧を除去していたが、XML の `ArticleCaption` テキスト自体が括弧付きであるため、モデル出力が正しく ground truth が誤っていた。修正後に full retry を実施。

### 2.4 評価指標

1. **JSON パース成功率**: レスポンスの `content` が有効な JSON としてパースでき、`ArticleInfo` case class にデコードできるか
2. **フィールド正答率**: ground truth との完全一致（5フィールド: article_number, caption, title, paragraph_count, all_para_text）

### 2.5 サンプルサイズ

Ground truth 修正前後で full retry したため:
- **JSON パース成功率・caption 以外のフィールド**: 90 サンプル（45 × 2ラウンド）
- **caption フィールド**: 45 サンプル（修正後のみ有効）

### 2.6 実装

`stages/stage1/structured_output.scala` — scala-cli スクリプト。

---

## 3. Results

### 3.1 全体サマリー

| 手法 | JSON パース率 | 全フィールド正答率 | 平均生成速度 |
|------|-------------|-------------------|-------------|
| A（プロンプトのみ） | **100%** (15/15) | **100%** (15/15) | 144.2 tok/s |
| B（JSON mode） | **100%** (15/15) | **100%** (15/15) | 144.2 tok/s |
| C（JSON Schema） | **100%** (15/15) | **100%** (15/15) | 144.1 tok/s |

修正前ラウンド（caption 除くフィールドのみ）: 全手法で JSON パース 100% (45/45)、caption 以外の全フィールド正答率 100% (45/45)。

### 3.2 条文別結果

| 条文 | A: JSON / correct | B: JSON / correct | C: JSON / correct |
|------|-------------------|-------------------|-------------------|
| 709条（単一項） | 5/5 / 5/5 | 5/5 / 5/5 | 5/5 / 5/5 |
| 1条（3項） | 5/5 / 5/5 | 5/5 / 5/5 | 5/5 / 5/5 |
| 5条（3項+複数文） | 5/5 / 5/5 | 5/5 / 5/5 | 5/5 / 5/5 |

条文の構造的複雑さ（項数、Sentence 数）による差は観察されなかった。

### 3.3 レイテンシ

| 手法 | 平均 tok/s | 最小 | 最大 |
|------|-----------|------|------|
| A | 144.2 | 144.1 | 144.6 |
| B | 144.2 | 143.9 | 144.2 |
| C | 144.1 | 142.3 | 144.1 |

手法間のレイテンシ差はほぼなし。JSON Schema 制約（C）によるレイテンシ増は観察されなかった。

### 3.4 遭遇した問題

1. **Thinking mode による content 枯渇**（→ NOTES.md 12:50）: `max_tokens=1024` では thinking だけで全トークンを消費。`max_tokens=4096` で解決。Qwen3.5 の thinking は ~1500-2000 tokens を使う
2. **`/no_think` が効かない**（→ NOTES.md 12:55）: llama-server 経由では thinking mode を無効化できなかった
3. **Ground truth の caption 括弧問題**（→ NOTES.md 13:00）: XML の `ArticleCaption` テキストは括弧付きであり、モデル出力が正しかった

---

## 4. Discussion

### 4.1 ガイドの予想との比較

| 手法 | 予想 JSON パース率 | 実測 | 予想スキーマ準拠率 | 実測 |
|------|-------------------|------|-------------------|------|
| A | 80-90% | **100%** | — | **100%** |
| B | 100% | **100%** | 「スキーマ不一致あり」 | **100%** |
| C | 100% | **100%** | 100% | **100%** |

**ガイドの予想は実験 A について過度に悲観的だった。** Qwen3.5-35B-A3B は System Prompt にスキーマを記述し temperature=0.0 で運用すれば、`response_format` なしでも JSON 遵守率が極めて高い。

### 4.2 結果が均一すぎる問題

3手法間で差が出なかったため、「どの手法が最も信頼できるか」という本来の問いに対する回答が得られていない。考えられる原因:

1. **モデル能力が高すぎる**: Qwen3.5-35B-A3B は BFCL-V4 で 67.3 を記録する tool calling 特化モデル。構造化出力はその前提スキルであり、この程度のタスクでは天井効果が出る
2. **System Prompt の効果**: スキーマを System Prompt に集約する設計が Qwen3.5 の特性と噛み合い、プロンプトのみでも制約として十分に機能した
3. **temperature=0.0**: 確定的な出力を生成するため、5回の試行が実質的に同一出力の繰り返しになっている可能性。バリエーションが出ない

### 4.3 Stage 2 への示唆

- **JSON mode / Schema なしでも tool calling は成立する可能性**: 実験 A の結果から、System Prompt でのスキーマ指定だけで十分な精度が出る。パス B（プロンプトベースの tool calling）が想定以上に実用的かもしれない
- **Thinking mode の制御が Stage 2 の課題**: tool calling では `content` にツール呼び出し JSON が入る必要があるが、thinking がトークンを消費する。`max_tokens` の適切な設定またはthinkinkg 無効化が必須
- **Ground truth 設計の重要性**: 評価基準のバグ（括弧問題）が実験結果を歪めた。Stage 2 以降でも ground truth の妥当性検証を初期に行うべき

### 4.4 追加検証の候補（未実施）

1. **SystemPrompt vs UserPrompt でのスキーマ遵守率比較**: ユーザーの経験的知見（Qwen3.5 は SystemPrompt でのスキーマ遵守率が高い）を定量化する。差が出れば、手法 A の高い成功率の説明になる
2. **temperature > 0 での試行**: 確定的出力から外れた場合の robustness を検証
3. **Q6_K vs Q4_K_M での精度比較**: 量子化戦略検証の一環（`docs/research/2026-03-23_qwen35-9b-quantization-strategy.md` 参照）
4. **より複雑な条文での検証**: 号構成、別表参照、附則など構造的に複雑な条文

---

## 5. 成果物

| ファイル | 内容 |
|---------|------|
| `structured_output.scala` | 3手法比較実験スクリプト |
| `extract_articles.scala` | XML 条文抽出ユーティリティ |
| `civil_code.xml` | e-Gov API から取得した民法 XML |
| `NOTES.md` | 実験ノート（時系列の観察記録） |
| `RESULTS.md` | 本レポート |

## 6. 次のステップ

- Stage 2（単一ツール呼び出し）に進む
- Thinking mode の制御方法を引き続き調査（llama-server の `--reasoning-budget` 等）
- 追加検証は必要に応じて実施（§4.4 参照）

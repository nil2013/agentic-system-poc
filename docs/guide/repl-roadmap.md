# REPL 実用化ロードマップ

> ステータス: Stage EX（カリキュラム完了後の自由拡張フェーズ）
> 前提: Stage 0-8 完了。Stage 8 REPL が PoC レベルで動作中。
> 目標: 法令の調査・検索に実用的なレベルの REPL に成長させる（v0.1 → v1.0）
> 最終更新: 2026-03-26

---

## 現状（2026-03-26 時点）

### 動作するもの

- 対話的 REPL（`run-repl.sh` で直接 JVM 起動）
- 4ツール: `find_laws`, `get_article`, `search_within_law`, `calculate`
- V1/V2 バックエンド切り替え基盤（`--egov-api v1|v2`）
- セッション永続化（新規/復元/切替）
- `/think`, `/save`, `/history`, `/session` コマンド
- Prompts の capabilities ベース動的生成
- 全ラウンド reasoning キャプチャ

### 本セッションで解決した課題

| 課題 | 対応 | 状態 |
|------|------|------|
| 条文内容のキーワード検索ができない | `search_within_law` 実装 | ✅ 解消 |
| V1/V2 切り替えができない | `EGovLawApi` trait + 切り替え基盤 | ✅ 解消 |
| コンテキスト制約 | 262K context 確認（Hybrid Attention-SSM） | ✅ 制約消失を確認 |

### 残存課題

| 課題 | 重要度 | 根拠 |
|------|--------|------|
| 法令構造（章・節）の表示ができない | **高** | 民訴構造探索に11ターン要した |
| MAX_TOOL_ROUNDS=5 が探索的タスクで不足 | **高** | 複数回のオーバーフロー |
| LLM 推論中の進捗表示がない | **中** | REPL が無反応に見える |
| 定義規定の体系的検索ができない | **中** | 法令調査の基本動作 |
| 条文範囲の一括取得ができない | **中** | 1条ずつ get_article の連打が必要 |
| content 空問題（境界パース） | **中** | 散発的に発生、再試行で復帰 |
| sbt fork で JLine dumb terminal | **低** | run-repl.sh で回避中 |

---

## 次期実装タスク（T1-T7）

### 依存関係図

```
T1 (MAX_TOOL_ROUNDS) ─── 独立
T2 (ドットアニメ) ─── 独立 ──→ T7 (ストリーミング, T2 を拡張)
T3 (get_article_range) ─── LawDataRepository (既存)
T4 (get_law_metadata) ─── LawDataRepository (既存)
T5 (get_law_structure) ─── LawDataRepository (既存)
T6 (get_definitions) ─── T5 の知見 + ナッジタグシステム

T3, T4, T5 は相互に独立（並行実装可能）
T6 は T5 の後（XML パースパターンを再利用）
T7 は T2 の後（スレッド制御パターンを拡張）
```

### Phase 1: インフラ + 即効性の高いツール

#### T1: MAX_TOOL_ROUNDS 引き上げ

- **変更**: `AgentConfig.maxToolRounds` のデフォルト値を 5 → 15 に変更
- **理由**: 7ツール + ナッジフォールバックで最大 8 ラウンド消費。余裕を持たせる
- **難易度**: 極低（1行変更）
- **変更ファイル**: `agent/AgentLoop.scala`

#### T2: ドットアニメーション（LLM 待機中の進捗表示）

- **変更**: `AgentLoop.runTurn` 内の LLM 呼び出し中にバックグラウンドスレッドで `Thinking.....` を表示
- **方式**: `\r` で同一行を上書き、ドット数 1-5 でループ、500ms 間隔
- **難易度**: 低（10行程度の追加）
- **変更ファイル**: `agent/AgentLoop.scala`

#### T3: `get_article_range` — 条文範囲一括取得

- **機能**: 「第709条から第724条まで」のように条番号範囲を指定して複数条文を一括取得
- **実装**: `LawDataRepository` に `getArticleRange(lawId, from, to)` 追加。`<Article>` の `Num` 属性でフィルタ。枝番号（`Num="709_2"`）対応
- **ToolDispatch**: `get_article_range` ツール定義 + dispatch
- **難易度**: 低
- **変更ファイル**: `LawDataRepository.scala`, `ToolDispatch.scala`

#### T4: `get_law_metadata` — 法令メタデータ取得

- **機能**: 法令の基本情報（法令名、法令番号、公布日、法令種別、総条数、章数、附則数）
- **実装**: `LawDataRepository` に `getMetadata(lawId)` 追加。XML の `<Law>` 属性 + 要素カウント
- **ToolDispatch**: `get_law_metadata` ツール定義 + dispatch
- **難易度**: 極低
- **変更ファイル**: `LawDataRepository.scala`, `ToolDispatch.scala`, `Models.scala`（LawMetadata case class）

### Phase 2: 構造系ツール + ナッジシステム

#### T5: `get_law_structure` — 法令構造表示

- **機能**: 法令の章・節・款の構造（目次）を返却。条文範囲付き
- **実装**: `LawDataRepository` に `getStructure(lawId)` 追加。`<Part>`, `<Chapter>`, `<Section>` 等を再帰抽出
- **設計ステータス**: OPEN → 実装（XSD 精査は実装中に行う）
- **難易度**: 低
- **変更ファイル**: `LawDataRepository.scala`, `ToolDispatch.scala`

#### T6: `get_definitions` — 定義条文検索 + ナッジタグシステム

- **機能**: 法令の定義規定を検索。集中定義（第2条型）+ 「いう。）」終端パターンによる本文中定義抽出
- **フォールバック**: 定義条文 → 「いう。）」検索 → [NUDGE] で search_within_law へ誘導
- **ナッジタグ**: `[RESULT]`/`[NUDGE]`/`[ERROR]` のセミ構造化応答を導入
- **SP 条件分岐**: `Prompts` にタグ解釈指示を追加（shall 強度、§6 実験で検証済み）
- **難易度**: 中（「いう。）」パターンの正規表現 + ナッジタグの ToolDispatch 統合）
- **変更ファイル**: `LawDataRepository.scala`, `ToolDispatch.scala`, `Prompts.scala`, `Models.scala`（Definition case class）
- **参照**: `docs/research/2026-03-26_agentic-law-retrieval-architecture.md` §3, §5, §6

### Phase 3: ストリーミング

#### T7: Thinking tail ストリーミング表示

- **機能**: LLM 推論中に Thinking ブロックの末尾 20-30 文字をリアルタイム表示
- **実装**: `LlmClient` に SSE ストリーミングメソッド追加。`reasoning_content` delta を読み取り、`\r` で末尾表示
- **T2 との関係**: T2 のドットアニメスレッドを SSE 読み取りスレッドに拡張
- **難易度**: 中（SSE パーサ + LlmClient の二重パス化）
- **変更ファイル**: `agent/LlmClient.scala`, `agent/AgentLoop.scala`

---

## 実装状況トラッカー

| ID | タスク | Phase | 状態 | 備考 |
|----|--------|-------|------|------|
| EX-1a | search_within_law | 1 | ✅ **完了** | `LawDataRepository` + `SearchHit` |
| T1 | MAX_TOOL_ROUNDS 引き上げ | 1 | 📋 予定 | 5→15 |
| T2 | ドットアニメーション | 1 | 📋 予定 | |
| T3 | get_article_range | 1 | 📋 予定 | |
| T4 | get_law_metadata | 1 | 📋 予定 | |
| T5 | get_law_structure | 2 | 📋 予定 | OPEN→実装 |
| T6 | get_definitions + ナッジタグ | 2 | 📋 予定 | 「いう。）」パターン + SP 条件分岐 |
| T7 | Thinking tail ストリーミング | 3 | 📋 予定 | SSE 対応 |
| — | V1/V2 切り替え基盤 | — | ✅ **完了** | EGovLawApi trait + Capability |
| — | Stage EX 要約実験 | — | ✅ **完了・クローズ** | C2 パレート最適 |

### Deferred（明示的にスコープ外）

| 項目 | 理由 | 参照 |
|------|------|------|
| V2Client 実装 | PoC は V1 ベースで十分。V2 は将来フェーズ | tool-capability-analysis.md §7 |
| バルク DB 構築 | プロダクション設計の範疇 | agentic-law-retrieval-architecture.md §4 |
| KV キャッシュ量子化 | 実運用で性能問題が顕在化してから | law-summarization-experiment.md §7 |
| 分割戦略の最適化 | 編単位で実用品質確認済み | RESULTS.md §4.6 |
| find_laws カテゴリ拡張 | T3-T6 より優先度低 | tool-capability-analysis.md §3 |
| get_appendix_table | 同上 | tool-capability-analysis.md §3 |
| egov-law-client-design.md 更新 | 実装完了後にまとめて | |

---

## 設計リファレンス

| 文書 | 内容 |
|------|------|
| `docs/egov-api/tool-capability-analysis.md` | ツール候補の網羅的分析、優先順位、description 設計原則 |
| `docs/research/2026-03-26_agentic-law-retrieval-architecture.md` | バルク DB 構想、定義規定の4パターン、ナッジパターン、構造化応答実験 |
| `docs/research/2026-03-26_qwen35-35b-kv-cache-analysis.md` | KV キャッシュ特性、推奨起動パラメータ |
| `docs/research/2026-03-26_law-summarization-experiment.md` | 要約実験結果、並列度の影響、Thinking 消費特性 |
| `stages/stage-ex/summarization/RESULTS.md` | Stage EX 実験の最終結果、Deferred 課題 |

# REPL 実用化ロードマップ

> ステータス: Stage EX（カリキュラム完了後の自由拡張フェーズ）
> 前提: Stage 0-8 完了。Stage 8 REPL が PoC レベルで動作中。
> 目標: 法令の調査・検索に実用的なレベルの REPL に成長させる（v0.1 → v1.0）

---

## 現状（Stage 8 完了時点）

### 動作するもの

- 対話的 REPL（`run-repl.sh` で直接 JVM 起動）
- 3ツール: `find_laws`（法令名検索）, `get_article`（条文取得）, `calculate`（計算）
- セッション永続化（新規/復元/切替）
- `/think`, `/save`, `/history`, `/session` コマンド
- Prompts オブジェクトによる SystemPrompt のセクション管理
- 全ラウンド reasoning キャプチャ

### 課題（Stage 8 テストで判明）

| 課題 | 重要度 | 根拠 |
|------|--------|------|
| 条文内容のキーワード検索ができない | **最高** | 民訴196条ハルシネーション事件 |
| 法令構造（章・節）の表示ができない | **高** | 民訴構造探索に11ターン要した |
| MAX_TOOL_ROUNDS=5 が探索的タスクで不足 | **高** | 複数回のオーバーフロー |
| content 空問題（境界パース） | **中** | 散発的に発生、再試行で復帰 |
| sbt fork で JLine dumb terminal | **中** | run-repl.sh で回避中 |
| `/save` のメタデータ不正確 | **低** | tokens=0, timestamps 同一 |

---

## Phase 1: ツール拡充（最優先）

> 詳細な API 分析: [`docs/egov-api/tool-capability-analysis.md`](../egov-api/tool-capability-analysis.md)

### EX-1a: `search_articles` — 法令内条文キーワード検索

**最優先。ハルシネーションの構造的原因を解消する。**

- `/lawdata/{lawId}` で法令全文を取得 → XML 内の `<Sentence>` をキーワード検索
- プロセスライフタイムキャッシュ（LRU, 最大 3-5 法令）
- ツール description で `get_article` との使い分けを明示

**実装見積もり**: 中（キャッシュ基盤の設計が核）

### EX-1b: `get_law_structure` — 法令構造表示

- `/lawdata/{lawId}` から章・節・款の構造を抽出（`search_articles` とキャッシュ共有）
- 条文範囲付きの目次を返却

**実装見積もり**: 小（XML 構造抽出のみ、キャッシュは EX-1a と共有）

### EX-1c: `find_laws` カテゴリ拡張 + `get_appendix_table`

- `find_laws` に category パラメータ追加（政令・省令対応）
- `/articles;appdxTable={}` を新ツールとして公開

**実装見積もり**: 小

---

## Phase 2: SystemPrompt チューニング

### EX-2a: ツール使い分けガイドの SystemPrompt 組み込み

Phase 1 でツールが増えたら、ルーティング精度を維持するために description 設計と SystemPrompt ガイドを整備。

- `Prompts.ToolGuidelines` セクション追加
- 「条文番号が分かっている → get_article」「分からない → search_articles」の使い分けルール

### EX-2b: Thinking 分析に基づくチューニング（AX-3 の実践）

Stage 7 の thinking 分析結果を踏まえて description を改善。

---

## Phase 3: 回答品質の改善

### EX-3a: 空回答のリカバリ

content 空問題への対策。検出して自動再試行、またはユーザーへの通知。

### EX-3b: 出典明示

ツール経由で取得した情報と内部知識を回答内で明示的に区別。

### EX-3c: 「中間報告」の自動発動

Stage 8 テストで発見された「中間報告パターン」（implicit self-evaluation）を N ターンごとに自動発動。

---

## Phase 4: UI / UX の改善

### EX-4a: MAX_TOOL_ROUNDS の動的調整

REPL コマンド `/max-tools N` で設定変更。探索的タスク時に引き上げ。

### EX-4b: sbt 依存からの脱却

assembly jar or GraalVM native-image で直接実行可能に。JLine dumb terminal 問題の根本解消。

### EX-4c: `/save` のメタデータ改善

リアルタイムでログに書き込み、tokens と timestamps を正確に記録。

---

## 実装状況トラッカー

| ID | タスク | Phase | 状態 | 備考 |
|----|--------|-------|------|------|
| EX-1a | search_articles | 1 | 未着手 | API 分析完了 |
| EX-1b | get_law_structure | 1 | 未着手 | EX-1a とキャッシュ共有 |
| EX-1c | find_laws 拡張 + appendix | 1 | 未着手 | |
| EX-2a | ToolGuidelines SystemPrompt | 2 | 未着手 | Phase 1 完了後 |
| EX-2b | Thinking ベース description 改善 | 2 | 未着手 | |
| EX-3a | 空回答リカバリ | 3 | 未着手 | |
| EX-3b | 出典明示 | 3 | 未着手 | |
| EX-3c | 中間報告自動発動 | 3 | 未着手 | |
| EX-4a | MAX_TOOL_ROUNDS 動的調整 | 4 | 未着手 | |
| EX-4b | sbt 脱却 | 4 | 未着手 | |
| EX-4c | /save メタデータ | 4 | 未着手 | |

# e-Gov 法令 API ツール機能分析

> 作成日: 2026-03-25（初版: V1 分析）、2026-03-25（V2 セクション追加）
> 目的: Stage EX（REPL 実用化）のツール拡充にあたり、V1/V2 の全機能を分析しツール化候補を網羅的に整理する
> API 仕様（V1）: `v1/houreiapi_shiyosyo.pdf`, `v1/hourei-api-v1-rest-spec.md`
> API 仕様（V2）: `v2/lawapi-v2.yaml`, `v2/domain-reference.md`
> 現行実装: `src/main/scala/tools/egov/`（`EGovLawApi` trait + V1/V2 バックエンド）

---

## 1. V1 API エンドポイント全体マップ

| # | エンドポイント | メソッド | 実装状況 | ツール化候補 |
|---|--------------|---------|---------|------------|
| 1 | `/api/1/lawlists/{category}` | GET | ✅ `find_laws` | カテゴリ拡張 |
| 2 | `/api/1/lawdata/{lawNum or lawId}` | GET | ❌ 未使用 | **条文内容検索, 法令構造表示** |
| 3 | `/api/1/articles;lawId={};article={}` | GET | ✅ `get_article` | — |
| 4 | `/api/1/articles;lawId={};paragraph={}` | GET | ⚠️ API 対応、ツール未公開 | 項単独取得 |
| 5 | `/api/1/articles;lawId={};article={};paragraph={}` | GET | ✅ `get_article`(paragraph_number) | — |
| 6 | `/api/1/articles;lawId={};appdxTable={}` | GET | ⚠️ API 対応、ツール未公開 | 別表取得 |
| 7 | `/api/1/updatelawlists/{yyyyMMdd}` | GET | ❌ 未使用 | 改正履歴検索 |

**使用率**: 7エンドポイント中 3つ実装済み（~43%）。残り ~57% が未活用。

---

## 2. 現行ツール一覧

| ツール | API エンドポイント | 機能 | 制約 |
|--------|------------------|------|------|
| `find_laws(keyword)` | `/lawlists/2` | 法令名キーワード検索 → lawId 付き結果 | category=2 固定（憲法・法律のみ） |
| `get_article(law_id_or_name, article_number, paragraph_number?)` | `/articles;lawId;article;paragraph` | 条文取得（lawId or 法令名 + 条番号） | 条文内容の検索不可、別表非対応 |
| `calculate(expression)` | — | 四則演算 | 法律計算機能なし |

---

## 3. ツール化候補の優先順位

### 最高優先: `search_articles` — 法令内条文キーワード検索

**解決する問題**: ハルシネーション（条番号推測の強制）

**根拠（Stage 8 テスト）**: 「訴訟記録の閲覧に関する条文は？」→ LLM が 195, 196, 197, 198, 247, 248 を推測 → 全て不正解。正解は 91条。**ツールがないため推測を強いられ、ハルシネーションが構造的に誘発された。**

**実装方針**:
- `/lawdata/{lawId}` で法令全文 XML を取得
- XML 内の `<Sentence>` 要素をキーワード検索
- マッチした条文の条番号 + スニペットを返却
- **キャッシュ必須**: 法令全文は大きい（民法 1.7MB）。プロセスライフタイムキャッシュ + LRU eviction

**ツール定義案**:
```json
{
  "name": "search_articles",
  "description": "法令の条文内容をキーワードで検索する。条文番号が分からない場合に使う。find_laws で法令IDを取得してから使用する。",
  "parameters": {
    "law_id": { "type": "string", "description": "法令ID（find_laws で取得）" },
    "keyword": { "type": "string", "description": "検索キーワード。例: 閲覧, 損害賠償, 婚姻" },
    "max_results": { "type": "integer", "description": "最大結果数（デフォルト: 5）" }
  }
}
```

---

### 高優先: `get_law_structure` — 法令構造表示

**解決する問題**: 法令全体像の非効率な探索

**根拠（Stage 8 テスト）**: 民事訴訟法の全体構造を把握するのに 11ターンの二分探索が必要だった。法令の章・節・款の目次があれば 1-2 ターンで完了。

**実装方針**:
- `/lawdata/{lawId}` で法令全文 XML を取得（`search_articles` とキャッシュ共有）
- XML の構造要素（`<Part>`, `<Chapter>`, `<Section>`, `<Subsection>`, `<Division>`）を抽出
- 各構造要素のタイトルと条文範囲を返却

**ツール定義案**:
```json
{
  "name": "get_law_structure",
  "description": "法令の章・節・款の構造（目次）を表示する。法令全体の構成を把握したいときに使う。",
  "parameters": {
    "law_id": { "type": "string", "description": "法令ID（find_laws で取得）" }
  }
}
```

---

### 中優先: `find_laws` のカテゴリ拡張

**解決する問題**: 政令・省令が検索できない

**現状**: `LawRepository.loadLawList(category = 2)` で憲法・法律のみ。政令（category=3）や府省令（category=4）が検索対象外。

**実装方針**: `find_laws` の `category` パラメータを追加（デフォルト: 2）。または全カテゴリ統合キャッシュ。

---

### 中優先: `get_appendix_table` — 別表取得

**解決する問題**: 別表（附属表）にアクセスできない

**根拠**: 税法、会社法等は別表が重要。現在の `get_article` では別表を取得できない。

**実装方針**: `/articles;lawId={};appdxTable={}` を新ツールとして公開。API は前方一致検索のため、複数候補返却（HTTP 300, Code=2）のハンドリングが必要。

---

### 低優先: `get_law_updates` — 改正履歴検索

**解決する問題**: 法令改正の追跡

**実装方針**: `/updatelawlists/{yyyyMMdd}` をラップ。日付範囲指定で複数日分を取得。

---

## 4. アーキテクチャ考慮事項

### 4.1 キャッシュ戦略（`/lawdata` 対応）

`search_articles` と `get_law_structure` は `/lawdata` の全文 XML を必要とする。

| データ | サイズ（例） | キャッシュ方式 |
|--------|------------|-------------|
| 民法全文 | 1.7 MB | プロセスライフタイム |
| 刑法全文 | ~500 KB | 同上 |
| 民事訴訟法全文 | ~800 KB | 同上 |

**方針**: LRU eviction（最大 3-5 法令をキャッシュ）。`LawRepository` の法令一覧キャッシュと同じパターン。

### 4.2 MAX_TOOL_ROUNDS の制約

現在の MAX_TOOL_ROUNDS=5 は探索的タスクで不足（Stage 8 T5, T7 で複数回オーバー）。

**対策候補**:
- AgentConfig で設定可能にする（既に `maxToolRounds` フィールドがある）
- REPL コマンド `/max-tools N` で動的変更
- デフォルトを 10 に引き上げ

### 4.3 ツール数増加とルーティング精度

Stage 3 で 3ツールのルーティングは 100% だったが、ツールが 5-6 に増えると精度が下がる可能性。

**対策**:
- description の差別化を徹底（「条文番号が分かっている場合」vs「条文番号が分からない場合」）
- SystemPrompt にツール使い分けガイドを追加（AX-3）
- Stage 3 と同じ description 粒度比較実験を拡張ツールで再実施

---

## 5. LLM 視点での設計考慮

### 5.1 ツール間の境界設計

| 質問パターン | 使うべきツール | 間違いやすいツール |
|------------|-------------|----------------|
| 「民法709条を見せて」 | `get_article` | — |
| 「訴訟記録の閲覧に関する条文は？」 | `search_articles` | `get_article`（条番号推測） |
| 「民事訴訟法の全体構造は？」 | `get_law_structure` | `get_article`（二分探索） |
| 「個人情報に関する法律は？」 | `find_laws` | — |
| 「民法の別表を見せて」 | `get_appendix_table` | `get_article` |

### 5.2 description 設計の原則

- **「いつ使うか」を明示**: 「条文番号が分からない場合に使う」「法令全体の構成を把握したいときに使う」
- **他ツールとの関係を明示**: 「find_laws で法令IDを取得してから使用する」
- **使うべきでない場面も明示**: 「条文番号が分かっている場合は get_article を使うこと」

---

## 6. 実装ロードマップ（V1 ベース）

| フェーズ | ツール | 依存 | 見積もり |
|---------|--------|------|---------|
| **Phase 1** | `search_articles` | `/lawdata` キャッシュ基盤 | 中（キャッシュ設計が核） |
| **Phase 1** | `get_law_structure` | 同上（キャッシュ共有） | 小（XML 構造抽出のみ） |
| **Phase 2** | `find_laws` カテゴリ拡張 | なし | 小 |
| **Phase 2** | `get_appendix_table` | なし | 小（API 対応済み） |
| **Phase 3** | `get_law_updates` | なし | 小 |
| **Phase 3** | MAX_TOOL_ROUNDS 動的調整 | REPL コマンド追加 | 小 |

---

## 7. V2 API による能力拡張

V2 API（`/api/2`）は V1 の構造的限界を解消する機能を提供する。V1/V2 切り替え基盤（`EGovLawApi` trait + `Capability` enum）は Phase 1 で実装済み。

### 7.1 V2 で追加される主要エンドポイント

| エンドポイント | 機能 | V1 の課題との対応 |
|---|---|---|
| `GET /keyword` | **条文内容の全文キーワード検索** | 196条事件の構造的解消。V1 では条番号推測が必要 → ハルシネーション |
| `GET /laws` | 法令一覧の高度なフィルタリング（名前、種別、日付、カテゴリ） | V1 の `find_laws` は category=2 固定 |
| `GET /law_data/{id}?elm=...` | 要素レベルの部分取得（特定条文だけ JSON で取得） | V1 は全文取得 or `/articles` のみ |
| `GET /law_data/{id}?asof=...` | 時点指定クエリ（「X年Y月時点の法令」） | V1 に対応機能なし |
| `GET /law_revisions/{id}` | 法令改正履歴の取得 | V1 に対応機能なし |
| `GET /law_file/{type}/{id}` | 法令ファイル DL（html/rtf/docx） | V1 に対応機能なし |

### 7.2 V1 ツール候補 vs V2 ネイティブ対応

| V1 での候補（§3） | V1 実装方式 | V2 でのアプローチ |
|---|---|---|
| `search_articles`（最高優先） | `/lawdata` 全文取得 + クライアント側キーワード検索 + LRU キャッシュ | **`/keyword` エンドポイントで直接解決。キャッシュ不要** |
| `get_law_structure`（高優先） | `/lawdata` 全文取得 + XML 構造要素抽出 | `/law_data?elm=TOC` or JSON 形式で構造取得 |
| `find_laws` カテゴリ拡張 | 複数カテゴリの法令一覧キャッシュ | `/laws?law_type=Act,CabinetOrder` で一発 |
| `get_appendix_table` | `/articles;appdxTable=...`（HTTP 300 対応必要） | `/law_data?elm=AppdxTable_1` |

**結論**: V2 を使うと、V1 で「キャッシュ基盤が核」だった `search_articles` が API 一発で解決する。実装コストが大幅に削減される。

### 7.3 V2 実装ロードマップ

| フェーズ | ツール | V2 エンドポイント | 備考 |
|---|---|---|---|
| **V2 Phase 1**（最優先） | `search_keyword` | `GET /keyword` | 196条問題の構造的解消。ツール定義は Phase 1 で作成済み |
| **V2 Phase 2** | `find_laws` 拡張 | `GET /laws` | 名前検索、複数種別、日付範囲 |
| **V2 Phase 2** | `get_article` JSON 化 | `GET /law_data?elm=...&json_format=light` | XML パーサ不要に |
| **V2 Phase 3** | `get_law_structure` | `GET /law_data?elm=TOC` | 目次取得 |
| **V2 Phase 3** | `get_law_revisions` | `GET /law_revisions/{id}` | 改正履歴 |

### 7.4 V1/V2 切り替え基盤の現状

- `EGovLawApi` trait + `Capability` enum: 実装済み
- `ToolDispatch`: capabilities ベースの動的ツールリスト生成: 実装済み
- `--egov-api v1|v2` / `EGOV_API_VERSION`: 実装済み
- `Prompts.capabilityNotice`: V1/V2 で SystemPrompt を動的切り替え: 実装済み
- `V2Client`: スケルトン（Phase 2 以降で順次実装）

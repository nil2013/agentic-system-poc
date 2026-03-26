# 法令探索 Agentic System: アーキテクチャ設計ノート

> 作成日: 2026-03-26
> 位置づけ: PoC（本リポジトリ）で得られた設計知見を、将来のプロダクション実装に向けて整理した文書。
> 前提知識: `docs/egov-api/tool-capability-analysis.md`（ツール機能分析）、`stages/stage-ex/summarization/RESULTS.md`（要約実験結果）

---

## 1. PoC → プロダクション設計のパイプライン

本 PoC は「最終形」ではなく「最終形の設計材料を生成するための実験」として位置づけられる。

```
Phase A (現在: API 直叩き PoC)
  → 知見: 頻出検索パターン、必要なメタデータ、事前計算可能なデータ
  → 知見: deterministic 抽出の限界、LLM が補完できる範囲

Phase B (将来: バルク DB + メタデータ整備)
  → Phase A の知見をスキーマ設計に反映
  → LLM が Phase A で「発見」したメタデータを DB に永続化

Phase C (将来: ハイブリッド DB + API)
  → DB をメイン、API は差分同期 + リアルタイム更新用
```

Phase A で「何がうまくいき何が落ちるか」を記録することが、Phase B-C の設計品質を決定する。

---

## 2. Deterministic 抽出 vs LLM 発見のスペクトラム

法令データからの情報抽出は、完全に deterministic なものから LLM 依存のものまでスペクトラムを形成する。

| 抽出対象 | 手法 | 確実性 | 事前計算可能 |
|---------|------|--------|------------|
| 法令構造（編・章・節） | XML タグ走査 | 確実 | ✅ |
| 条文見出し | `<ArticleCaption>` 取得 | 確実 | ✅ |
| 集中定義規定（第2条型） | 見出し「定義」検索 | 高い | ✅ |
| 本文中定義（パターン2-4） | 「いう。）」終端検索 | 高い（後述） | ✅ |
| 罰則章 | `<ChapterTitle>` に「罰則」検索 | 高い | ✅ |
| 条文間の参照関係 | 正規表現（「○○法第X条」） | 中程度 | ✅ だが精度に課題 |
| 条文の要約・解釈 | LLM 要約 | 依存性高 | ❌ |
| 法令全体の位置づけ | LLM 分析 | 依存性高 | ❌ |

**設計原則**: deterministic に抽出可能なものは事前計算して DB に持つ。LLM はdeterministic 手法の「穴」を埋める補完的役割。

---

## 3. 法令の定義規定: 4つのパターンと抽出戦略

### 3.1 パターン分類

| パターン | 構造 | 例 | deterministic 抽出 |
|---------|------|-----|-------------------|
| 1. 集中定義条文 | `<ArticleCaption>（定義）</ArticleCaption>` + 号列記 | 個人情報保護法第2条 | `ArticleCaption` 検索で確実 |
| 2. スコープ限定定義 | 本文中「以下この章において「○○」という。）」 | 民法各所 | **「いう。）」検索で抽出可能** |
| 3. 号列記定義 | 柱書「次の各号に掲げる用語の意義は…」 | 行政手続法第2条 | パターン1 の変種。柱書テキストで検出 |
| 4. 暗黙的括弧定義 | 「○○（以下「△△」という。）」 | ほぼ全ての法令 | **「いう。）」検索で抽出可能** |

### 3.2 「いう。）」終端パターンによる定義抽出

日本の法令における定義は、ほぼ例外なく以下の形式で終端する:

```
「[定義語]」という。）
[用語]をいう。）
```

したがって、法令全文に対して `「([^」]+)」という。）` の正規表現検索を行うことで、パターン 2, 4 を含む大多数の定義語を deterministic に抽出できる。

**抽出結果のスキーマ**:

```
定義語: "個人情報"
出現条文: 第2条第1項
スコープ: "この法律において"（全体）or "この章において"（限定）
定義テキスト: "生存する個人に関する情報であって…をいう。）"
```

**既知の限界**:
- 定義語の「スコープ」判定（「この法律」vs「この章」vs「この条」）は周辺テキストのパースが必要
- 「前条に規定する○○」のような参照型定義は終端パターンに一致しない
- 条例・規則等では慣例が異なる場合がある

### 3.3 フォールバック戦略（PoC 実装向け）

```
get_definitions(lawId, term)
  Step 1: ArticleCaption に「定義」を含む条文を検索（パターン 1, 3）
  Step 2: 「いう。）」パターンで全文検索し、term に一致する定義を抽出（パターン 2, 4）
  Step 3: 見つからなければ:
    → エラーメッセージで search_within_law へナッジ
    「定義条文に該当する定義は見つかりませんでした。
     この用語は条文本文中で別の形式で定義されている可能性があります。
     search_within_law で用法を確認してください。」
```

**ツールのエラーメッセージによるナッジ**: Tool のエラー応答に「次に使うべきツール」の示唆を含めるパターン。既に `get_article` → `find_laws` 誘導で実績があり、agentic system のツール設計として汎用性が高い。

---

## 4. 将来アーキテクチャ: バルク DB + Agent

### 4.1 なぜ API 直叩きから DB に移行すべきか

| 観点 | API 直叩き（現在） | バルク DB（将来） |
|------|-----------------|----------------|
| レイテンシ | 1-3秒/リクエスト | ~1ms（ローカル DB） |
| キャッシュ | クライアント側 LRU（メモリ消費、FIFO eviction） | 不要（全データ DB 上） |
| メタデータ | なし（都度 XML パース） | 事前計算済み（定義 map, 構造 index, 参照関係） |
| 検索能力 | 全文走査（`search_within_law`） | 全文検索 index + メタデータクエリ |
| 法令横断検索 | 不可能（API は1法令ずつ） | 全法令対象の横断検索 |
| データ鮮度 | リアルタイム | 同期遅延あり（日次バッチで実用可） |

### 4.2 DB スキーマの設計材料（PoC から得られた知見）

**法令構造テーブル** — `get_law_structure` の知見から:

```sql
law_structure(
  law_id,
  element_type,    -- 'Part' / 'Chapter' / 'Section' / 'Article'
  element_num,     -- "1" / "709" / "709_2"（枝番号）
  title,           -- PartTitle / ChapterTitle / ArticleCaption
  parent_path,     -- "Part_1/Chapter_3/Section_2"
  sentence_count,
  char_count
)
```

**定義規定テーブル** — `get_definitions` + 「いう。）」パターンの知見から:

```sql
definitions(
  law_id,
  term,              -- 定義される用語
  scope,             -- 'law' / 'part' / 'chapter' / 'article'
  scope_detail,      -- "第3章" 等
  source_article,    -- 定義が置かれている条番号
  pattern_type,      -- 1:集中 / 2:スコープ限定 / 3:号列記 / 4:暗黙的
  definition_text,   -- 定義のテキスト
  extraction_method  -- 'deterministic' / 'llm_discovered'
)
```

`extraction_method` フィールドが Phase A → Phase B のブリッジ:
- `deterministic`: 「いう。）」パターン等で自動抽出
- `llm_discovered`: LLM が `search_within_law` で発見し、human-in-the-loop で DB に追加

「LLM 発見率が高いパターン」を分析し、deterministic 抽出ルールを改善するフィードバックループ。

**法令間参照テーブル** — `get_related_provisions` の知見から:

```sql
law_references(
  source_law_id,
  source_article,
  target_law_name,    -- 参照先法令名（正規化前）
  target_article,     -- 参照先条番号（あれば）
  target_law_id,      -- 名寄せ後の法令ID（nullable, 自動解決 or 手動）
  context_text        -- 参照文脈のスニペット
)
```

### 4.3 データ同期戦略

e-Gov API V1 は `/updatelawlists/{yyyyMMdd}` で日次の更新法令一覧を提供する。

```
初期ロード: /lawlists/1（全法令）→ 各法令の /lawdata → DB 投入
日次同期:   /updatelawlists/{yesterday} → 更新法令のみ /lawdata → DB 更新
```

法令の更新頻度（国会会期中でも月数十件程度）を考えると、日次バッチで実用上十分。V2 API ではさらに `updated_from` / `updated_to` パラメータで細かい差分取得が可能。

### 4.4 PoC が生成すべき「設計材料」

| PoC の実験 | 生成される設計材料 |
|-----------|----------------|
| `search_within_law` の検索精度 | 全文検索 index のスキーマ要件 |
| `get_definitions` の false negative | 定義抽出ルールの改善候補 |
| `get_law_structure` の XML パース | 構造テーブルのスキーマ |
| `get_article_range` の使用パターン | クエリ最適化の優先度 |
| 要約実験の並列度特性 | バッチ処理アーキテクチャの設計 |
| LLM が発見する定義・参照関係 | human-in-the-loop メタデータ整備のワークフロー |

---

## 5. ツール設計パターン: エラーメッセージによるナッジ

PoC で確立したパターン。ツールが不完全な結果を返す場合、エラーメッセージに「次に使うべきツール」の示唆を含めることで、LLM の行動を明示的にガイドする。

| ツール | エラー条件 | ナッジ先 |
|--------|----------|---------|
| `get_article` | 法令が見つからない | 「find_laws で法令IDを確認してください」 |
| `get_definitions` | 定義条文に該当なし | 「search_within_law で用法を確認してください」 |
| `search_within_law` | 0件ヒット | 「キーワードを変えるか、find_laws で別の法令を検索してください」 |
| `get_law_structure` | 構造要素なし（附則のみ等） | 「get_article で直接条文を参照してください」 |

このパターンは System prompt の指示（「条番号を推測するな」等）と補完関係にある。System prompt がグローバルな行動原則を設定し、ツールのエラーメッセージがローカルな行動指示を提供する。

---

## 6. Qwen3.5 の System/User 遵守特性（ユーザー報告）

Qwen3.5 は System prompt に対する遵守率が高く、User message はデータとして処理する傾向がある。

**設計への影響**:
- 制約・ルール（「条番号を推測しない」等）→ **System prompt に配置**
- メタデータ（法令名、構造情報、担当パート範囲）→ **System prompt に配置**
- 処理対象データ（法令テキスト）→ **User message に配置**

Stage 0-2 の実験で System prompt の指示が高い遵守率を示したこと、および要約実験の Thinking ログで System prompt の指示がモデル内部で反復参照されていることと整合する。

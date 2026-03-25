# e-Gov 法令 API クライアントライブラリ設計書

> 作成日: 2026-03-23（初版）、2026-03-25（V1/V2 切り替え基盤対応で改訂）
> 対象: `src/main/scala/tools/` パッケージ
> API 仕様（V1）: `docs/egov-api/v1/houreiapi_shiyosyo.pdf`, `docs/egov-api/v1/hourei-api-v1-rest-spec.md`
> API 仕様（V2）: `docs/egov-api/v2/lawapi-v2.yaml`, `docs/egov-api/v2/domain-reference.md`
> **実装状況**: V1 実装完了。V2 はスケルトン（Phase 1: 切り替え基盤のみ、Phase 2 以降で実装予定）。

---

## 1. 設計目標

### 1.1 経緯

当初の課題（Stage 4 時点）: `KnownLaws`（6法ハードコード）→ `/articles` パイプラインが断絶。Stage 8 テストでさらに、**キーワード検索がないことによるハルシネーション**（196条事件: LLM に条番号の推測を強いる構造的欠陥）が判明。

### 1.2 設計方針

- **lawId ベースの設計**: 法令名のあいまいさを排除し、lawId で一意に法令を特定する
- **V1/V2 切り替え可能**: `EGovLawApi` trait による抽象化。環境変数・CLI 引数でバックエンド選択
- **動的ツールリスト**: バックエンドの `Capability` に基づき、LLM に提示するツール定義と SystemPrompt を動的生成
- **Tool Calling との統合**: LLM が自然に `find_laws` → `get_article`（V1）、`find_laws` → `search_keyword`（V2）のパイプラインを使えるツール description 設計

---

## 2. API エンドポイントの利用計画

### V1 エンドポイント

| エンドポイント | 利用方針 | ツール名 |
|---|---|---|
| `GET /api/1/lawlists/{cat}` | 法令名検索。法令一覧をキャッシュし、キーワードフィルタ | `find_laws` |
| `GET /api/1/articles;lawId={};article={}` | 条文単位の取得。lawId + 条番号で検索 | `get_article` |
| `GET /api/1/lawdata/{lawId}` | 法令全文取得。将来の拡張用 | （予備） |
| `GET /api/1/updatelawlists/{date}` | 更新法令リスト。現時点では使わない | （予備） |

### V2 エンドポイント（Phase 2 以降で実装予定）

| エンドポイント | 利用方針 | ツール名 |
|---|---|---|
| `GET /laws` | 法令一覧検索（名前・種別・日付フィルタ、ページネーション） | `find_laws`（拡張版） |
| `GET /keyword` | **条文内容の全文検索**。196条問題を構造的に解消 | `search_keyword` |
| `GET /law_data/{id}` | 法令本文取得（JSON/XML 選択、`elm` パラメータで部分取得） | `get_article`（拡張版） |
| `GET /law_revisions/{id}` | 法令改正履歴 | 将来検討 |
| `GET /law_file/{type}/{id}` | 法令ファイル DL（html/rtf/docx） | 将来検討 |

---

## 3. 内部アーキテクチャ

### 3.1 レイヤー構成

```
┌─────────────────────────────────────────────────────┐
│ Tool Calling Layer (LLM ↔ ToolDispatch)              │
│  find_laws / get_article / calculate / search_keyword │
│  ※ 提示されるツールは Capability に基づき動的に決定     │
├─────────────────────────────────────────────────────┤
│ Domain Layer                                          │
│  LawRepository (法令一覧キャッシュ + 検索)              │
│  ArticleRepository (条文取得 + XML パース)              │
├─────────────────────────────────────────────────────┤
│ API Client Layer                                      │
│  EGovLawApi (trait: 共通インターフェース)               │
│  ├─ v1.V1Client (HTTP + XML, /api/1 エンドポイント)    │
│  └─ v2.V2Client (HTTP + JSON, /api/2 エンドポイント)   │
└─────────────────────────────────────────────────────┘
```

### 3.2 EGovLawApi trait

V1/V2 の共通インターフェース。呼び出し側（LawRepository, ArticleRepository）はこの trait に依存する。

```scala
package tools.egov

trait EGovLawApi {
  def fetchLawList(category: Int = 2): Either[String, Seq[LawInfo]]
  def fetchArticle(lawId: String, article: String): Either[String, Elem]
  def fetchArticleWithParagraph(lawId: String, article: String, paragraph: String): Either[String, Elem]
  def fetchLawData(lawId: String): Either[String, Elem]
  def capabilities: Set[Capability]
}
```

**V1 実装** (`v1.V1Client`):
- ベース URL: `https://laws.e-gov.go.jp/api/1`
- レスポンス形式: XML（`<DataRoot><Result><Code>...</Code></Result><ApplData>...</ApplData></DataRoot>`）
- `/articles` エンドポイントはセミコロン区切りの matrix parameter（JAX-RS 形式）。sttp の URI 補間子が非対応のため `Uri.unsafeParse` で変換
- `capabilities`: `Set.empty`（V1 固有の追加能力なし）

**V2 実装** (`v2.V2Client`):
- ベース URL: `https://laws.e-gov.go.jp/api/2`
- レスポンス形式: JSON（`json_format=full|light`）または XML
- `capabilities`: `Set(Capability.KeywordSearch)`
- Phase 1 ではスケルトン（全メソッドが `Left("not yet implemented")`）

### 3.3 Capability enum と動的ツールリスト

```scala
enum Capability {
  case KeywordSearch  // V2 /keyword エンドポイント
}
```

`ToolDispatch` はバックエンドの `capabilities` を見てツール定義を動的生成:
- V1 モード: `find_laws`, `get_article`, `calculate`（3ツール）
- V2 モード: 上記 + `search_keyword`（4ツール）

LLM に存在しないツールを見せない → ハルシネーション呼び出しの防止。

### 3.4 EGovBackendFactory

```scala
object EGovBackendFactory {
  def create(version: String): EGovLawApi = version match {
    case "v2" => new v2.V2Client()
    case _    => new v1.V1Client()  // デフォルト V1
  }
}
```

選択は `--egov-api v1|v2` CLI 引数または `EGOV_API_VERSION` 環境変数で行う（引数が優先）。

### 3.5 LawRepository

法令一覧のキャッシュと検索を担当する。コンストラクタで `EGovLawApi` を受け取る。

```scala
class LawRepository(api: EGovLawApi) {
  private var cache: Option[Seq[LawInfo]] = None

  def loadLawList(category: Int = 2): Seq[LawInfo]
  def findByKeyword(keyword: String): Seq[LawInfo]
  def resolveLawId(lawNameOrId: String): ResolveResult
}
```

**`resolveLawId` の名前解決戦略:**

戻り値は `ResolveResult` ADT:

```scala
sealed trait ResolveResult
object ResolveResult {
  case class Resolved(lawId: String) extends ResolveResult
  case class Ambiguous(candidates: Seq[LawInfo]) extends ResolveResult
  case object NotFound extends ResolveResult
}
```

解決の4段階チェーン:

| 段階 | 条件 | 結果 |
|------|------|------|
| 1. lawId 検出 | 半角英数のみ（`^[0-9A-Za-z]+$`） | `Resolved(そのまま)` |
| 2. 完全一致 | 法令一覧の `lawName` と完全一致 | `Resolved(lawId)` |
| 3. 前方一致 / 部分一致 | 1件 → `Resolved`、複数件 → `Ambiguous(最大5件)` | — |
| 4. KnownLaws | 静的マッピング（6法短縮名） | `Resolved` or `NotFound` |

### 3.6 ArticleRepository

条文取得と XML パースを担当する。

```scala
class ArticleRepository(api: EGovLawApi) {
  def getArticle(lawId: String, articleNumber: String): Either[String, ArticleContent]
  def getArticleWithParagraph(lawId: String, articleNumber: String, paragraphNumber: String): Either[String, ArticleContent]
}
```

**V1 固有の依存**: 漢数字変換（`v1.ArticleNumberConverter`）を直接 import。V2 は `/law_data` + `elm` パラメータを使う見込みで、V2 実装時に解消予定。

---

## 4. 条番号のフォーマット変換（V1 固有）

`v1.ArticleNumberConverter`: e-Gov V1 の `/articles` エンドポイントが漢数字全角を要求するため。

```scala
package tools.egov.v1

object ArticleNumberConverter {
  def toKanjiArticle(num: Int): String   // 709 → "第七百九条"
  def toKanjiParagraph(num: Int): String // 1 → "第一項"
}
```

対応範囲は 1〜9999。枝番号（第709条の2）は未対応。

---

## 5. Tool Calling インターフェース

### 5.1 ツール定義

V1 モードで提供される3ツール + V2 モードで追加される1ツール。

| ツール | 提供条件 | 機能 |
|--------|---------|------|
| `find_laws` | 常時 | 法令名キーワード検索 → lawId 付き結果 |
| `get_article` | 常時 | 条文取得（lawId or 法令名 + 条番号） |
| `calculate` | 常時 | 四則演算 |
| `search_keyword` | `Capability.KeywordSearch` | 条文内容の全文検索（V2） |

### 5.2 ToolDispatch

```scala
class ToolDispatch(
    lawRepo: LawRepository,
    articleRepo: ArticleRepository,
    capabilities: Set[Capability]
) {
  /** capabilities に基づいて動的に生成されるツール定義 JSON */
  def toolDefs: Json = {
    val base = List(findLawsDef, getArticleDef, calculateDef)
    val extra = if (capabilities.contains(Capability.KeywordSearch))
      List(searchKeywordDef) else Nil
    Json.arr((base ++ extra)*)
  }

  def dispatch(name: String, args: JsonObject): String = name match {
    case "find_laws"      => /* lawRepo.findByKeyword ... */
    case "get_article"    => /* lawRepo.resolveLawId + articleRepo.getArticle ... */
    case "calculate"      => Arithmetic.calculate(expr)
    case "search_keyword" => /* V2 stub / 実装 */
    case other            => s"エラー: 未知のツール '$other'"
  }
}

object ToolDispatch {
  def forBackend(api: EGovLawApi): ToolDispatch = {
    val lawRepo = new LawRepository(api)
    val articleRepo = new ArticleRepository(api)
    new ToolDispatch(lawRepo, articleRepo, api.capabilities)
  }
  lazy val defaultV1: ToolDispatch = forBackend(new v1.V1Client())
}
```

`AgentConfig.toolDispatch` のデフォルト値として `ToolDispatch.defaultV1` を使用。Stage 4-7 はデフォルトで動作。

### 5.3 SystemPrompt 連動

`Prompts.capabilityNotice(capabilities)` がバックエンド能力に応じた指示文を生成:
- V1 モード: 「キーワード検索は利用不可。条番号が不明な場合は推測せずユーザーに確認」
- V2 モード: 「`search_keyword` ツールで条文内容を検索可能」

---

## 6. エラーハンドリング

### 6.1 API レベルのエラー

| 状況 | API レスポンス | ツールの返却 |
|------|-------------|------------|
| 法令が見つからない | Code=1, 0件 | `"エラー: 条件に該当する法令が見つかりません。"` |
| 法令番号が重複 | Code=1 or HTTP 406 | `"エラー: 法令番号が重複しています。lawId を使用してください。"` |
| 条文が見つからない | Code=1, 0件 | `"エラー: 指定された条文が見つかりません。条番号を確認してください。"` |
| API 通信エラー | HTTP エラー | `"エラー: API 通信エラー: {details}"` |

### 6.2 名前解決エラー

`ResolveResult` ADT に基づいて分岐:

| `ResolveResult` | ツールの返却 |
|---|---|
| `Resolved(lawId)` | 処理続行 |
| `Ambiguous(candidates)` | `"エラー: '{name}' に該当する法令が複数あります: {候補リスト}。法令IDを指定してください。find_laws で法令IDを確認できます。"` |
| `NotFound` | `"エラー: '{name}' に該当する法令が見つかりません。find_laws で法令IDを確認してください。"` |

**エラーメッセージには `find_laws` への誘導を含める。** LLM が「まず法令を検索して lawId を確認する」フローを学習しやすくなる。

---

## 7. キャッシュ戦略

| データ | キャッシュ | 理由 |
|--------|---------|------|
| 法令一覧（lawlists/2） | **する**（プロセスライフタイム） | 約2000件、変更頻度が低い |
| 条文（articles） | **しない** | リクエストごとに条文が異なる |
| 法令全文（lawdata） | **しない** | サイズが大きい（民法で 1.7MB） |

---

## 8. パッケージ構成

```
src/main/scala/tools/
├── egov/
│   ├── EGovLawApi.scala            # trait: 共通インターフェース
│   ├── Capability.scala            # enum: バックエンド能力宣言
│   ├── EGovBackendFactory.scala    # "v1"/"v2" → インスタンス生成
│   ├── Models.scala                # LawInfo, ArticleContent, ResolveResult 等
│   ├── LawRepository.scala         # class(api): キャッシュ・名前解決
│   ├── ArticleRepository.scala     # class(api): 条文取得・パース
│   ├── v1/
│   │   ├── V1Client.scala          # /api/1 エンドポイント実装
│   │   └── ArticleNumberConverter.scala  # 漢数字変換（V1 固有）
│   └── v2/
│       └── V2Client.scala          # /api/2 エンドポイント（Phase 1: スケルトン）
├── Arithmetic.scala                # 四則演算
└── ToolDispatch.scala              # class + companion: 動的ツール定義 + ディスパッチ
```

---

## 9. テスト計画

### 9.1 ユニットテスト

| テスト | 内容 |
|--------|------|
| `v1.ArticleNumberConverterTest` | 1, 5, 10, 11, 100, 199, 709, 1000 の変換 |
| `LawRepositoryTest` | キーワード検索、名前解決（完全一致/前方一致/部分一致/Ambiguous/NotFound/KnownLaws） |

### 9.2 統合テスト（API 呼び出し）

`ToolDispatch.forBackend(new v1.V1Client())` で V1 バックエンドを使用:

| テスト | 内容 |
|--------|------|
| `find_laws("個人情報")` | 法令一覧からフィルタ、lawId 付きで返却 |
| `get_article("129AC0000000089", "709")` | 民法709条を取得（lawId 指定） |
| `get_article("民法", "709")` | 民法709条を取得（法令名→lawId 解決） |
| `get_article("不存在法", "1")` | エラーメッセージに find_laws 誘導が含まれるか |
| `pipeline` | find_laws → lawId 抽出 → get_article の E2E |

### 9.3 V1/V2 切り替え検証

| 検証項目 | 方法 |
|----------|------|
| V1 モードで既存動作と同一 | Stage8Main デフォルト起動 |
| V2 モードで search_keyword がツールリストに出現 | `--egov-api v2` 起動 |
| 環境変数での切り替え | `EGOV_API_VERSION=v2` |

---

## 10. 配線（Wiring）

Stage8Main での典型的な配線:

```scala
val egovVersion = opts.getOrElse("egov-api", sys.env.getOrElse("EGOV_API_VERSION", "v1"))
val backend = EGovBackendFactory.create(egovVersion)
val toolDispatch = ToolDispatch.forBackend(backend)
val capPrompt = Prompts.capabilityNotice(backend.capabilities)

val config = AgentConfig(
  baseUrl = ...,
  model = ...,
  promptSections = List(Prompts.Role, Prompts.FallbackControl, capPrompt),
  toolDispatch = toolDispatch
)
```

Stage 4-7 は `AgentConfig()` のデフォルト（`ToolDispatch.defaultV1`）で動作し、変更不要。

# e-Gov 法令 API V1 クライアントライブラリ設計書

> 作成日: 2026-03-23
> 対象: `src/main/scala/tools/` パッケージ
> API 仕様: `docs/egov-api/houreiapi_shiyosyo.pdf`, `docs/egov-api/hourei-api-v1-rest-spec.md`
> **実装状況**: 本設計に基づき実装済み。旧 `StatuteSearch.scala`, `LawListSearch.scala` は削除済み。また、HTTP 呼び出しは `agent/LlmClient.scala` に統合、SystemPrompt は `agent/Prompts.scala` に集約された。

---

## 1. 設計目標

### 1.1 解決すべき問題

現在の `StatuteSearch.searchStatute` は `KnownLaws`（6法ハードコード）でのみ動作し、`findLawByKeyword` → `searchStatute` のパイプラインが断絶している。Stage 5 のタスク設計（`findLawByKeyword` の結果で条文を取得）が動かない。

### 1.2 設計方針

- **lawId ベースの設計**: 法令名のあいまいさを排除し、lawId で一意に法令を特定する
- **`/articles` エンドポイントの活用**: 全文取得（`/lawdata`）ではなく条文単位でピンポイント取得。レスポンスサイズとレイテンシを削減
- **法令一覧キャッシュの共有**: `findLaws` と `getArticle` が同一のキャッシュを参照し、法令名→lawId の動的解決を実現
- **Tool Calling との統合**: LLM が自然に `findLaws` → `getArticle` のパイプラインを使えるツール description を設計

---

## 2. API エンドポイントの利用計画

| エンドポイント | 利用方針 | ツール名 |
|---------------|---------|---------|
| `GET /api/1/lawlists/{cat}` | 法令名検索。法令一覧をキャッシュし、キーワードフィルタ | `findLaws` |
| `GET /api/1/articles;lawId={};article={}` | 条文単位の取得。lawId + 条番号で検索 | `getArticle` |
| `GET /api/1/lawdata/{lawId}` | 法令全文取得。将来の拡張用（現時点では使わない） | （予備） |
| `GET /api/1/updatelawlists/{date}` | 更新法令リスト。現時点では使わない | （予備） |

---

## 3. 内部アーキテクチャ

### 3.1 レイヤー構成

```
┌─────────────────────────────────────────────┐
│ Tool Calling Layer (LLM ↔ ToolDispatch)      │
│  findLaws / getArticle / calculate           │
├─────────────────────────────────────────────┤
│ Domain Layer                                  │
│  LawRepository (法令一覧キャッシュ + 検索)      │
│  ArticleRepository (条文取得 + XML パース)      │
├─────────────────────────────────────────────┤
│ API Client Layer                              │
│  EGovApiClient (HTTP リクエスト + XML 解析)     │
└─────────────────────────────────────────────┘
```

### 3.2 EGovApiClient

e-Gov API V1 への HTTP アクセスを抽象化する。

```scala
package tools.egov

case class LawInfo(lawId: String, lawName: String, lawNo: String)

object EGovApiClient {
  val BaseUrl = "https://laws.e-gov.go.jp/api/1"

  /** 法令一覧取得: GET /api/1/lawlists/{category} */
  def fetchLawList(category: Int = 2): Either[String, Seq[LawInfo]]

  /** 条文取得: GET /api/1/articles;lawId={lawId};article={article} */
  def fetchArticle(lawId: String, article: String): Either[String, String]

  /** 条文+項取得: GET /api/1/articles;lawId={lawId};article={article};paragraph={paragraph} */
  def fetchArticleWithParagraph(lawId: String, article: String, paragraph: String): Either[String, String]

  /** 法令全文取得: GET /api/1/lawdata/{lawId} */
  def fetchLawData(lawId: String): Either[String, String]
}
```

**XML レスポンスの共通パース:**

```xml
<DataRoot>
  <Result>
    <Code>0</Code>      <!-- 0: 成功, 1: エラー, 2: 複数候補 -->
    <Message></Message>
  </Result>
  <ApplData>
    <!-- エンドポイント固有のデータ -->
  </ApplData>
</DataRoot>
```

- `Code != 0` の場合は `Left(message)` を返す
- `ApplData` の内容はエンドポイントに応じてパースする

### 3.3 LawRepository

法令一覧のキャッシュと検索を担当する。

```scala
package tools.egov

object LawRepository {
  private var cache: Option[Seq[LawInfo]] = None

  /** 法令一覧をロード（初回のみ API 呼び出し、以降はキャッシュ） */
  def loadLawList(category: Int = 2): Seq[LawInfo]

  /** キーワードで法令名を検索（最大10件） */
  def findByKeyword(keyword: String): Seq[LawInfo]

  /** 法令名から lawId を解決（完全一致 → 前方一致 → 部分一致の順） */
  def resolveLawId(lawName: String): Option[String]
}
```

**`resolveLawId` の名前解決戦略:**

LLM は `findLaws` の結果から法令名を取得して `getArticle` に渡す。その際、法令名の表記ゆれが発生しうる（「個人情報保護法」vs「個人情報の保護に関する法律」）。以下の順で解決を試みる:

1. **完全一致**: `lawName == info.lawName`
2. **前方一致**: `info.lawName.startsWith(lawName)`
3. **部分一致**: `info.lawName.contains(lawName)`
4. **KnownLaws フォールバック**: 従来のハードコード（「民法」→ ID）。短縮名で呼ばれた場合のショートカット

### 3.4 ArticleRepository

条文取得と XML パースを担当する。

```scala
package tools.egov

case class ArticleContent(
    lawId: String,
    articleNum: String,   // リクエストした条番号
    caption: String,      // 見出し（ArticleCaption）
    title: String,        // 条文タイトル（ArticleTitle）
    paragraphs: List[ParagraphContent]
)

case class ParagraphContent(
    paragraphNum: String,  // 項番号
    sentences: List[String] // 文のリスト
)

object ArticleRepository {
  /** 条文を取得してパース */
  def getArticle(lawId: String, articleNumber: String): Either[String, ArticleContent]
}
```

---

## 4. 条番号のフォーマット変換

### 4.1 問題

`/articles` エンドポイントの `article` パラメータは `第十一条` のような漢数字全角フォーマットを期待する。LLM は `"709"` のようなアラビア数字で条番号を指定する可能性が高い。

### 4.2 変換ロジック

```
アラビア数字 → 漢数字変換:
  709 → "第七百九条"
  1   → "第一条"
  199 → "第百九十九条"
  5   → "第五条"
```

**変換規則:**
- 百の位: N百（N=1 の場合「百」、N>1 の場合「N百」）
- 十の位: N十（N=1 の場合「十」、N>1 の場合「N十」）
- 一の位: そのまま漢数字
- 千の位: N千（同様のルール）

```scala
object ArticleNumberConverter {
  /** アラビア数字 → "第X条" 形式に変換 */
  def toKanjiArticle(num: Int): String
  /** アラビア数字 → "第X項" 形式に変換 */
  def toKanjiParagraph(num: Int): String
}
```

---

## 5. Tool Calling インターフェース

### 5.1 ツール定義

LLM に提供するツールを3つに再構成する。

#### findLaws

```json
{
  "name": "find_laws",
  "description": "法令名にキーワードを含む法令を検索する。法令の正式名称、法令ID、法令番号を返す。法令IDは getArticle で条文を取得する際に必要。",
  "parameters": {
    "type": "object",
    "properties": {
      "keyword": {
        "type": "string",
        "description": "検索キーワード。例: 個人情報, 消費者, 行政"
      }
    },
    "required": ["keyword"]
  }
}
```

**返却フォーマット:**
```
'個人情報' を含む法令 (2件):
- 個人情報の保護に関する法律 [ID: 415AC0000000057]（平成十五年法律第五十七号）
- 情報公開・個人情報保護審査会設置法 [ID: 415AC0000000060]（平成十五年法律第六十号）
```

lawId を明示的に返却することで、LLM が `getArticle` に渡せるようにする。

#### getArticle

```json
{
  "name": "get_article",
  "description": "法令の条文を取得する。法令ID（findLawsで取得）または法令名と、条番号を指定する。",
  "parameters": {
    "type": "object",
    "properties": {
      "law_id_or_name": {
        "type": "string",
        "description": "法令IDまたは法令名。例: 415AC0000000057, 民法, 個人情報の保護に関する法律"
      },
      "article_number": {
        "type": "string",
        "description": "条番号（アラビア数字）。例: 709, 1, 199"
      },
      "paragraph_number": {
        "type": "string",
        "description": "項番号（省略可）。例: 1, 2, 3"
      }
    },
    "required": ["law_id_or_name", "article_number"]
  }
}
```

**内部処理フロー:**
1. `law_id_or_name` が lawId フォーマット（半角英数）なら直接使用
2. そうでなければ `LawRepository.resolveLawId` で法令名→lawId 変換
3. `article_number` を漢数字に変換（`"709"` → `"第七百九条"`）
4. `/articles;lawId={};article={}` で条文取得
5. XML をパースして読みやすいテキストに整形

#### calculate

変更なし。

### 5.2 ToolDispatch の更新

```scala
object ToolDispatch {
  val toolDefs: Json = Json.arr(findLawsDef, getArticleDef, calculateDef)

  def dispatch(name: String, args: JsonObject): String = name match {
    case "find_laws" => ...
    case "get_article" => ...
    case "calculate" => ...
    case other => s"エラー: 未知のツール '$other'"
  }
}
```

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

| 状況 | ツールの返却 |
|------|------------|
| 法令名が解決できない | `"エラー: '{name}' に該当する法令が見つかりません。find_laws で法令IDを確認してください。"` |
| 法令名が複数候補にマッチ | `"エラー: '{name}' に該当する法令が複数あります: {候補リスト}。法令IDを指定してください。"` |

**エラーメッセージには `find_laws` への誘導を含める。** これにより LLM が「まず法令を検索して lawId を確認する」というフローを学習する。

---

## 7. キャッシュ戦略

| データ | キャッシュ | 理由 |
|--------|---------|------|
| 法令一覧（lawlists/2） | **する**（プロセスライフタイム） | 約2000件、変更頻度が低い。毎回取得するとレイテンシが増大 |
| 条文（articles） | **しない** | リクエストごとに条文が異なる。キャッシュの複雑さに見合わない |
| 法令全文（lawdata） | **しない** | サイズが大きく（民法で 1.7MB）、メモリ圧迫のリスク |

---

## 8. パッケージ構成

```
src/main/scala/tools/
├── egov/
│   ├── EGovApiClient.scala       # HTTP + XML パース
│   ├── LawRepository.scala       # 法令一覧キャッシュ + 検索
│   ├── ArticleRepository.scala   # 条文取得 + パース
│   ├── ArticleNumberConverter.scala  # アラビア数字 → 漢数字
│   └── Models.scala              # LawInfo, ArticleContent 等
├── Arithmetic.scala              # 四則演算（変更なし）
└── ToolDispatch.scala            # ツール定義 + ディスパッチ（更新）
```

旧ファイル（`StatuteSearch.scala`, `LawListSearch.scala`）は削除。

---

## 9. テスト計画

### 9.1 ユニットテスト

| テスト | 内容 |
|--------|------|
| `ArticleNumberConverterTest` | 1, 5, 10, 11, 100, 199, 709, 1000 の変換 |
| `LawRepositoryTest` | キーワード検索、名前解決（完全一致/前方一致/部分一致） |

### 9.2 統合テスト（API 呼び出し）

| テスト | 内容 |
|--------|------|
| `findLaws("個人情報")` | 法令一覧からフィルタ、lawId 付きで返却 |
| `getArticle("129AC0000000089", "709")` | 民法709条を取得（lawId 指定） |
| `getArticle("民法", "709")` | 民法709条を取得（法令名→lawId 解決） |
| `getArticle("415AC0000000057", "1")` | 個人情報保護法第1条（Stage 4 Turn 6 の修正確認） |
| `getArticle("不存在法", "1")` | エラーメッセージに find_laws 誘導が含まれるか |

### 9.3 パイプラインテスト

`findLaws("消費者")` → 結果から lawId を取得 → `getArticle(lawId, "1")` のフローが動くことを確認。Stage 5 のタスクの前提条件。

---

## 10. 移行の影響範囲

| コンポーネント | 変更 |
|--------------|------|
| `ToolDispatch` | ツール定義を3ツール（find_laws, get_article, calculate）に更新 |
| `AgentLoop` | 変更なし（ToolDispatch 経由で呼ぶだけ） |
| `Stage4Main` | `AgentConfig.systemPrompt` のツール説明を更新（任意） |
| `stages/stage4/` | Round 2 実験を実施 |
| ガイド | §3.1 の注記に従い実装が完了したことを記録 |

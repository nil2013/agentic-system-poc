package tools

import tools.egov.*
import io.circe.{Json, JsonObject}

/** LLM の Tool Calling と実際のツール実装を橋渡しする中央ディスパッチャ。
  *
  * 2つの責務を持つ:
  *  1. '''`toolDefs`''': OpenAI Chat Completions API の `tools` パラメータとして送信される
  *     ツールスキーマ JSON。LLM はこの定義を見てツールを選択する。
  *     '''`description` は事実上プロンプトの一部'''であり、変更は LLM の挙動に直接影響する。
  *  2. '''`dispatch`''': LLM が返した `tool_calls` を受け取り、対応する実装に振り分ける。
  *     ツール名文字列がスキーマ定義とディスパッチの結合点。
  *
  * == ツール一覧 ==
  *  - `find_laws`: 法令名キーワード検索 → [[egov.LawRepository.findByKeyword]]
  *  - `get_article`: 条文取得 → [[egov.LawRepository.resolveLawId]] + [[egov.ArticleRepository.getArticle]]
  *  - `calculate`: 四則演算 → [[Arithmetic.calculate]]
  *
  * == 拡張手順 ==
  * 新しいツールを追加するには:
  *  1. `toolDefs` の `Json.arr` にツール定義を追加
  *  2. `dispatch` の `match` に `case` を追加
  *  3. ツールの実装を作成（`tools/` または `tools/egov/` パッケージ）
  *
  * == 既知の制限 ==
  *  - `toolDefs` はコンパイル時定数。ランタイムでのツール動的登録は未対応
  *  - スキーマ JSON は手書き（case class からの自動導出ではない）。PoC では十分
  *
  * @see [[agent.AgentLoop]] 本オブジェクトの `toolDefs` と `dispatch` を使用するエージェントループ
  */
object ToolDispatch {

  /** OpenAI Chat Completions API の `tools` パラメータとして送信されるツール定義。
    *
    * この JSON はリクエストごとに LLM に渡される。LLM はこの定義の `description` と
    * `parameters` を読んでツールを選択するため、description の書き方はルーティング精度に影響する
    * （Stage 3 の実験では Qwen3.5-35B-A3B の能力により差は顕在化しなかった）。
    */
  val toolDefs: Json = Json.arr(
    Json.obj("type" -> Json.fromString("function"), "function" -> Json.obj(
      "name" -> Json.fromString("find_laws"),
      "description" -> Json.fromString(
        "法令名にキーワードを含む法令を検索する。法令の正式名称、法令ID、法令番号を返す。" +
        "法令IDは get_article で条文を取得する際に必要。"
      ),
      "parameters" -> Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "keyword" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("検索キーワード。例: 個人情報, 消費者, 行政")
          )
        ),
        "required" -> Json.arr(Json.fromString("keyword"))
      )
    )),
    Json.obj("type" -> Json.fromString("function"), "function" -> Json.obj(
      "name" -> Json.fromString("get_article"),
      "description" -> Json.fromString(
        "法令の条文を取得する。法令ID（find_lawsで取得）または法令名と、条番号を指定する。"
      ),
      "parameters" -> Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "law_id_or_name" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("法令IDまたは法令名。例: 415AC0000000057, 民法, 個人情報の保護に関する法律")
          ),
          "article_number" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("条番号（アラビア数字）。例: 709, 1, 199")
          ),
          "paragraph_number" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("項番号（省略可）。例: 1, 2, 3")
          )
        ),
        "required" -> Json.arr(Json.fromString("law_id_or_name"), Json.fromString("article_number"))
      )
    )),
    Json.obj("type" -> Json.fromString("function"), "function" -> Json.obj(
      "name" -> Json.fromString("calculate"),
      "description" -> Json.fromString("数式を計算して結果を返す。四則演算のみ対応。金額計算や割合計算に使う。"),
      "parameters" -> Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "expression" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("計算式。例: 100 * 0.3, 1500 + 2000")
          )
        ),
        "required" -> Json.arr(Json.fromString("expression"))
      )
    ))
  )

  /** ツール呼び出しを実行する。
    *
    * @param name ツール名（`toolDefs` の `function.name` と一致する必要がある）
    * @param args LLM が生成した引数（パース済み `JsonObject`）
    * @return ツールの実行結果（人間に読みやすいテキスト）。
    *         '''エラー規約''': エラーメッセージは `"エラー: "` プレフィックスで返される。
    *         `get_article` のエラーには `find_laws` への誘導メッセージが含まれる
    *         （LLM が「まず法令を検索して」というフローを学習しやすくするため）。
    */
  def dispatch(name: String, args: JsonObject): String = {
    name match {
      case "find_laws" =>
        val keyword = args("keyword").flatMap(_.asString).getOrElse("")
        val results = LawRepository.findByKeyword(keyword)
        if (results.isEmpty) {
          s"'$keyword' を含む法令は見つかりませんでした。"
        } else {
          val lines = results.map { law =>
            s"- ${law.lawName} [ID: ${law.lawId}]（${law.lawNo}）"
          }
          s"'$keyword' を含む法令 (${results.size}件):\n${lines.mkString("\n")}"
        }

      case "get_article" =>
        val lawIdOrName = args("law_id_or_name").flatMap(_.asString).getOrElse("")
        val articleNum = args("article_number").flatMap(_.asString).getOrElse("")
        val paragraphNum = args("paragraph_number").flatMap(_.asString)

        val resolvedLawId = LawRepository.resolveLawId(lawIdOrName) match {
          case ResolveResult.Resolved(id) => Right(id)
          case ResolveResult.Ambiguous(candidates) =>
            val names = candidates.map(c => s"${c.lawName} [ID: ${c.lawId}]").mkString(", ")
            Left(s"エラー: '$lawIdOrName' に該当する法令が複数あります: $names。法令IDを指定してください。find_laws で法令IDを確認できます。")
          case ResolveResult.NotFound =>
            Left(s"エラー: '$lawIdOrName' に該当する法令が見つかりません。find_laws で法令IDを確認してください。")
        }

        val result = resolvedLawId.flatMap { lawId =>
          paragraphNum match {
            case Some(pn) if pn.nonEmpty =>
              ArticleRepository.getArticleWithParagraph(lawId, articleNum, pn)
            case _ =>
              ArticleRepository.getArticle(lawId, articleNum)
          }
        }

        result match {
          case Right(content) => content.toText
          case Left(err) => err
        }

      case "calculate" =>
        val expr = args("expression").flatMap(_.asString).getOrElse("")
        Arithmetic.calculate(expr)

      case other =>
        s"エラー: 未知のツール '$other'"
    }
  }
}

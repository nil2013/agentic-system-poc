package tools

import tools.egov.*
import io.circe.{Json, JsonObject}

/** LLM の Tool Calling と実際のツール実装を橋渡しする中央ディスパッチャ。
  *
  * 2つの責務を持つ:
  *  1. '''`toolDefs`''': OpenAI Chat Completions API の `tools` パラメータに送信する
  *     ツールスキーマ JSON。バックエンドの [[Capability]] に基づいて動的に生成される。
  *  2. '''`dispatch`''': LLM が返した `tool_calls` を受け取り、対応する実装に振り分ける。
  *
  * == V1/V2 対応 ==
  * `capabilities` に [[Capability.KeywordSearch]] が含まれる場合（V2）、
  * `search_keyword` ツールが `toolDefs` に追加される。
  *
  * @param lawRepo       法令一覧キャッシュ・名前解決
  * @param articleRepo   条文取得・パース
  * @param capabilities  バックエンドが提供する能力（ツール定義の動的生成に使用）
  *
  * @see [[agent.AgentLoop]] `config.toolDispatch` 経由で使用
  * @see [[EGovBackendFactory]] バックエンド生成
  */
class ToolDispatch(
    lawRepo: LawRepository,
    articleRepo: ArticleRepository,
    lawDataRepo: LawDataRepository,
    capabilities: Set[Capability]
) {

  /** バックエンドの能力に基づいて動的に生成されるツール定義 JSON。 */
  def toolDefs: Json = {
    val base = List(findLawsDef, getArticleDef, getArticleRangeDef, searchWithinLawDef, getDefinitionsDef, getStructureDef, getMetadataDef, calculateDef)
    val extra = if (capabilities.contains(Capability.KeywordSearch)) {
      List(searchKeywordDef)
    } else {
      Nil
    }
    Json.arr((base ++ extra)*)
  }

  /** ツール呼び出しを実行する。 */
  def dispatch(name: String, args: JsonObject): String = {
    name match {
      case "find_laws" =>
        val keyword = args("keyword").flatMap(_.asString).getOrElse("")
        val results = lawRepo.findByKeyword(keyword)
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

        val resolvedLawId = lawRepo.resolveLawId(lawIdOrName) match {
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
              articleRepo.getArticleWithParagraph(lawId, articleNum, pn)
            case _ =>
              articleRepo.getArticle(lawId, articleNum)
          }
        }

        result match {
          case Right(content) => content.toText
          case Left(err) => err
        }

      case "get_article_range" =>
        val lawIdOrName = args("law_id").flatMap(_.asString).getOrElse("")
        val fromStr = args("from_article").flatMap(_.asString)
          .orElse(args("from_article").flatMap(_.asNumber).map(_.toString))
          .getOrElse("")
        val toStr = args("to_article").flatMap(_.asString)
          .orElse(args("to_article").flatMap(_.asNumber).map(_.toString))
          .getOrElse("")

        val resolvedLawId = lawRepo.resolveLawId(lawIdOrName) match {
          case ResolveResult.Resolved(id) => Right(id)
          case ResolveResult.Ambiguous(candidates) =>
            val names = candidates.map(c => s"${c.lawName} [ID: ${c.lawId}]").mkString(", ")
            Left(s"エラー: '$lawIdOrName' に該当する法令が複数あります: $names。法令IDを指定してください。")
          case ResolveResult.NotFound =>
            Left(s"エラー: '$lawIdOrName' に該当する法令が見つかりません。find_laws で法令IDを確認してください。")
        }

        val parsed = for {
          lawId <- resolvedLawId
          from <- fromStr.toIntOption.toRight(s"エラー: from_article は数字で指定してください: '$fromStr'")
          to <- toStr.toIntOption.toRight(s"エラー: to_article は数字で指定してください: '$toStr'")
        } yield (lawId, from, to)

        parsed.flatMap { case (lawId, from, to) =>
          lawDataRepo.getArticleRange(lawId, from, to)
        } match {
          case Right(texts) if texts.isEmpty =>
            s"指定範囲（第${fromStr}条〜第${toStr}条）に該当する条文は見つかりませんでした。"
          case Right(texts) =>
            s"${texts.size}条を取得:\n\n${texts.mkString("\n\n")}"
          case Left(err) => err
        }

      case "search_within_law" =>
        val lawIdOrName = args("law_id").flatMap(_.asString).getOrElse("")
        val keyword = args("keyword").flatMap(_.asString).getOrElse("")
        val maxResults = args("max_results").flatMap(_.asNumber).flatMap(_.toInt).getOrElse(10)

        val resolvedLawId = lawRepo.resolveLawId(lawIdOrName) match {
          case ResolveResult.Resolved(id) => Right(id)
          case ResolveResult.Ambiguous(candidates) =>
            val names = candidates.map(c => s"${c.lawName} [ID: ${c.lawId}]").mkString(", ")
            Left(s"エラー: '$lawIdOrName' に該当する法令が複数あります: $names。法令IDを指定してください。")
          case ResolveResult.NotFound =>
            Left(s"エラー: '$lawIdOrName' に該当する法令が見つかりません。find_laws で法令IDを確認してください。")
        }

        resolvedLawId.flatMap { lawId =>
          lawDataRepo.searchWithinLaw(lawId, keyword, maxResults)
        } match {
          case Right(hits) if hits.isEmpty =>
            s"'$keyword' を含む条文は見つかりませんでした。"
          case Right(hits) =>
            val lines = hits.map(h => s"- ${h.toText}")
            s"'$keyword' を含む条文 (${hits.size}件):\n${lines.mkString("\n")}"
          case Left(err) => err
        }

      case "get_definitions" =>
        val lawIdOrName = args("law_id").flatMap(_.asString).getOrElse("")
        val term = args("term").flatMap(_.asString).getOrElse("")

        if (term.isEmpty) {
          "[ERROR]\n検索する用語を指定してください。\n\n[NUDGE]\nterm パラメータに検索したい用語を指定してください。例: 個人情報, 消費者, 届出"
        } else {
          val resolvedLawId = lawRepo.resolveLawId(lawIdOrName) match {
            case ResolveResult.Resolved(id) => Right(id)
            case ResolveResult.Ambiguous(candidates) =>
              val names = candidates.map(c => s"${c.lawName} [ID: ${c.lawId}]").mkString(", ")
              Left(s"エラー: '$lawIdOrName' に該当する法令が複数あります: $names。法令IDを指定してください。")
            case ResolveResult.NotFound =>
              Left(s"エラー: '$lawIdOrName' に該当する法令が見つかりません。find_laws で法令IDを確認してください。")
          }

          resolvedLawId.flatMap { lawId =>
            lawDataRepo.getDefinitions(lawId, term)
          } match {
            case Right(hits) if hits.nonEmpty =>
              val resultText = hits.map(_.toText).mkString("\n\n")
              val nudge = if (hits.forall(_.patternType == 1)) ""
                else s"\n\n[NUDGE]\nこの結果は条文テキストのパターンマッチによる検出です。search_within_law で「$term」を検索すると、追加の用法が見つかる場合があります。"
              s"[RESULT]\n$resultText$nudge"

            case Right(_) =>
              s"[ERROR]\n「$term」の定義条文は見つかりませんでした。\n\n[NUDGE]\nこの用語は条文本文中で別の形式で定義されている可能性があります。search_within_law で「$term」を検索して用法を確認してください。"

            case Left(err) => err
          }
        }

      case "get_law_structure" =>
        val lawIdOrName = args("law_id").flatMap(_.asString).getOrElse("")

        val resolvedLawId = lawRepo.resolveLawId(lawIdOrName) match {
          case ResolveResult.Resolved(id) => Right(id)
          case ResolveResult.Ambiguous(candidates) =>
            val names = candidates.map(c => s"${c.lawName} [ID: ${c.lawId}]").mkString(", ")
            Left(s"エラー: '$lawIdOrName' に該当する法令が複数あります: $names。法令IDを指定してください。")
          case ResolveResult.NotFound =>
            Left(s"エラー: '$lawIdOrName' に該当する法令が見つかりません。find_laws で法令IDを確認してください。")
        }

        resolvedLawId.flatMap { lawId =>
          lawDataRepo.getStructure(lawId)
        } match {
          case Right(structure) => structure
          case Left(err) => err
        }

      case "get_law_metadata" =>
        val lawIdOrName = args("law_id").flatMap(_.asString).getOrElse("")

        val resolvedLawId = lawRepo.resolveLawId(lawIdOrName) match {
          case ResolveResult.Resolved(id) => Right(id)
          case ResolveResult.Ambiguous(candidates) =>
            val names = candidates.map(c => s"${c.lawName} [ID: ${c.lawId}]").mkString(", ")
            Left(s"エラー: '$lawIdOrName' に該当する法令が複数あります: $names。法令IDを指定してください。")
          case ResolveResult.NotFound =>
            Left(s"エラー: '$lawIdOrName' に該当する法令が見つかりません。find_laws で法令IDを確認してください。")
        }

        resolvedLawId.flatMap { lawId =>
          lawDataRepo.getMetadata(lawId)
        } match {
          case Right(meta) => meta.toText
          case Left(err) => err
        }

      case "calculate" =>
        val expr = args("expression").flatMap(_.asString).getOrElse("")
        Arithmetic.calculate(expr)

      case "search_keyword" =>
        // V2 stub — Phase 2 で実装予定
        "search_keyword: V2 バックエンドの実装は次フェーズで行います。"

      case other =>
        s"エラー: 未知のツール '$other'"
    }
  }

  // --- ツール定義 JSON ---

  private val findLawsDef: Json = Json.obj(
    "type" -> Json.fromString("function"), "function" -> Json.obj(
      "name" -> Json.fromString("find_laws"),
      "description" -> Json.fromString(
        "法令名にキーワードを含む法令を検索する。法令の正式名称、法令ID、法令番号を返す。" +
        "法令名が不明な場合や、キーワードで法令を探したい場合に使う。" +
        "法令名が分かっている場合は他のツールに法令名を直接指定できるため、find_laws は不要。"
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
    ))

  private val getArticleDef: Json = Json.obj(
    "type" -> Json.fromString("function"), "function" -> Json.obj(
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
    ))

  private val getArticleRangeDef: Json = Json.obj(
    "type" -> Json.fromString("function"), "function" -> Json.obj(
      "name" -> Json.fromString("get_article_range"),
      "description" -> Json.fromString(
        "法令の連続する条文を範囲指定で一括取得する。" +
        "開始条番号と終了条番号を指定すると、その範囲内の全条文（枝番号含む）をまとめて返す。" +
        "周辺条文の確認や比較に適している。範囲は50条以内で指定すること。" +
        "条文番号が分かっている場合に使う。分からない場合は search_within_law を使うこと。"
      ),
      "parameters" -> Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "law_id" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("法令IDまたは法令名。例: 129AC0000000089, 民法")
          ),
          "from_article" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("開始条番号（アラビア数字）。例: 709")
          ),
          "to_article" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("終了条番号（アラビア数字）。例: 724")
          )
        ),
        "required" -> Json.arr(
          Json.fromString("law_id"),
          Json.fromString("from_article"),
          Json.fromString("to_article")
        )
      )
    ))

  private val searchWithinLawDef: Json = Json.obj(
    "type" -> Json.fromString("function"), "function" -> Json.obj(
      "name" -> Json.fromString("search_within_law"),
      "description" -> Json.fromString(
        "特定の法令の条文内容をキーワードで検索する。条番号が不明な場合に使う。" +
        "法令名を直接指定可能（find_laws は不要）。" +
        "条番号が分かっている場合は get_article を使うこと。"
      ),
      "parameters" -> Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "law_id" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("検索対象の法令IDまたは法令名。例: 408AC0000000109, 民事訴訟法")
          ),
          "keyword" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("検索キーワード。例: 訴訟記録の閲覧, 損害賠償, 婚姻")
          ),
          "max_results" -> Json.obj(
            "type" -> Json.fromString("integer"),
            "description" -> Json.fromString("最大結果数（省略時: 10）")
          )
        ),
        "required" -> Json.arr(Json.fromString("law_id"), Json.fromString("keyword"))
      )
    ))

  private val getDefinitionsDef: Json = Json.obj(
    "type" -> Json.fromString("function"), "function" -> Json.obj(
      "name" -> Json.fromString("get_definitions"),
      "description" -> Json.fromString(
        "法令内の用語の定義を検索する。定義条文（第2条型）と本文中の定義（「○○」という。）パターンを検索する。" +
        "用語の正式な定義を確認したいときに使う。法令名を直接指定可能（find_laws は不要）。"
      ),
      "parameters" -> Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "law_id" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("法令IDまたは法令名。例: 415AC0000000057, 個人情報の保護に関する法律")
          ),
          "term" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("定義を検索する用語。例: 個人情報, 消費者, 届出")
          )
        ),
        "required" -> Json.arr(Json.fromString("law_id"), Json.fromString("term"))
      )
    ))

  private val getStructureDef: Json = Json.obj(
    "type" -> Json.fromString("function"), "function" -> Json.obj(
      "name" -> Json.fromString("get_law_structure"),
      "description" -> Json.fromString(
        "法令の章・節・款の構造（目次）を表示する。法令全体の構成を把握したいときに使う。" +
        "法令名を直接指定可能（find_laws は不要）。" +
        "条文の内容は含まない。構造を確認してから get_article_range で条文を取得する流れが効果的。"
      ),
      "parameters" -> Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "law_id" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("法令IDまたは法令名。例: 129AC0000000089, 民法")
          )
        ),
        "required" -> Json.arr(Json.fromString("law_id"))
      )
    ))

  private val getMetadataDef: Json = Json.obj(
    "type" -> Json.fromString("function"), "function" -> Json.obj(
      "name" -> Json.fromString("get_law_metadata"),
      "description" -> Json.fromString(
        "法令のメタデータ（法令名、法令番号、種別、公布日、規模）を取得する。" +
        "法令の全体像を把握するために使う。法令名を直接指定可能（find_laws は不要）。条文の内容は含まない。"
      ),
      "parameters" -> Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "law_id" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("法令IDまたは法令名。例: 129AC0000000089, 民法")
          )
        ),
        "required" -> Json.arr(Json.fromString("law_id"))
      )
    ))

  private val calculateDef: Json = Json.obj(
    "type" -> Json.fromString("function"), "function" -> Json.obj(
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

  private val searchKeywordDef: Json = Json.obj(
    "type" -> Json.fromString("function"), "function" -> Json.obj(
      "name" -> Json.fromString("search_keyword"),
      "description" -> Json.fromString(
        "法令の条文内容をキーワードで全文検索する。" +
        "条番号が不明な場合に、キーワードを含む条文を発見するために使う。" +
        "検索結果には条文の位置（条番号等）とテキストスニペットが含まれる。"
      ),
      "parameters" -> Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "keyword" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("検索キーワード。例: 訴訟記録の閲覧, 損害賠償")
          ),
          "law_id" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("検索対象の法令ID（省略可）。指定すると特定法令内のみを検索する。")
          )
        ),
        "required" -> Json.arr(Json.fromString("keyword"))
      )
    ))
}

/** [[ToolDispatch]] のファクトリ。 */
object ToolDispatch {

  /** 指定バックエンドに対応する [[ToolDispatch]] を生成する。
    * [[LawRepository]] と [[ArticleRepository]] も内部で生成される。
    */
  def forBackend(api: EGovLawApi): ToolDispatch = {
    val lawRepo = new LawRepository(api)
    val articleRepo = new ArticleRepository(api)
    val lawDataRepo = new LawDataRepository(api)
    new ToolDispatch(lawRepo, articleRepo, lawDataRepo, api.capabilities)
  }

  /** V1 バックエンドのデフォルトインスタンス。
    * [[agent.AgentConfig]] のデフォルト値として使用される。
    */
  lazy val defaultV1: ToolDispatch = forBackend(new egov.v1.V1Client())
}

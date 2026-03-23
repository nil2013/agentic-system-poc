package tools

import io.circe.{Json, JsonObject}

object ToolDispatch {

  /** OpenAI 互換の tools 定義 JSON */
  val toolDefs: Json = Json.arr(
    Json.obj("type" -> Json.fromString("function"), "function" -> Json.obj(
      "name" -> Json.fromString("search_statute"),
      "description" -> Json.fromString("日本の法令の条文を検索して取得する。法令名と条番号を指定する。"),
      "parameters" -> Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "law_name" -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("法令名。例: 民法, 刑法, 憲法")),
          "article_number" -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("条番号。数字のみ。例: 709, 199, 9"))
        ),
        "required" -> Json.arr(Json.fromString("law_name"), Json.fromString("article_number"))
      )
    )),
    Json.obj("type" -> Json.fromString("function"), "function" -> Json.obj(
      "name" -> Json.fromString("find_law_by_keyword"),
      "description" -> Json.fromString("法令名にキーワードを含む法令を検索する。法令の正式名称や法令番号を調べたいときに使う。"),
      "parameters" -> Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "keyword" -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("検索キーワード。例: 個人情報, 消費者"))
        ),
        "required" -> Json.arr(Json.fromString("keyword"))
      )
    )),
    Json.obj("type" -> Json.fromString("function"), "function" -> Json.obj(
      "name" -> Json.fromString("calculate"),
      "description" -> Json.fromString("数式を計算して結果を返す。四則演算のみ対応。金額計算や割合計算に使う。"),
      "parameters" -> Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "expression" -> Json.obj("type" -> Json.fromString("string"), "description" -> Json.fromString("計算式。例: 100 * 0.3, 1500 + 2000"))
        ),
        "required" -> Json.arr(Json.fromString("expression"))
      )
    ))
  )

  def dispatch(name: String, args: JsonObject): String = {
    name match {
      case "search_statute" =>
        val lawName = args("law_name").flatMap(_.asString).getOrElse("")
        val articleNum = args("article_number").flatMap(_.asString).getOrElse("")
        StatuteSearch.searchStatute(lawName, articleNum)
      case "find_law_by_keyword" =>
        val keyword = args("keyword").flatMap(_.asString).getOrElse("")
        LawListSearch.findLawByKeyword(keyword)
      case "calculate" =>
        val expr = args("expression").flatMap(_.asString).getOrElse("")
        Arithmetic.calculate(expr)
      case other =>
        s"エラー: 未知のツール '$other'"
    }
  }
}

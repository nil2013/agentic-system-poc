package tools.egov

import tools.ToolDispatch
import io.circe.JsonObject
import io.circe.Json

/** 統合テスト（e-Gov API 呼び出しあり、ネットワーク必要） */
class IntegrationTest extends munit.FunSuite {

  // V1 バックエンドでテスト
  val dispatch: ToolDispatch = ToolDispatch.forBackend(new v1.V1Client())

  // ネットワークアクセスが必要なテストにタグ付け
  override def munitTimeout = scala.concurrent.duration.Duration(60, "s")

  test("find_laws: 個人情報 → lawId 付き結果") {
    val result = dispatch.dispatch("find_laws", JsonObject("keyword" -> Json.fromString("個人情報")))
    assert(result.contains("個人情報の保護に関する法律"), s"Result: $result")
    assert(result.contains("[ID:"), s"lawId not found in: $result")
  }

  test("get_article: 民法709条 by lawId") {
    val args = JsonObject(
      "law_id_or_name" -> Json.fromString("129AC0000000089"),
      "article_number" -> Json.fromString("709")
    )
    val result = dispatch.dispatch("get_article", args)
    assert(result.contains("不法行為"), s"Result: $result")
    assert(result.contains("第七百九条"), s"Result: $result")
  }

  test("get_article: 民法709条 by name") {
    val args = JsonObject(
      "law_id_or_name" -> Json.fromString("民法"),
      "article_number" -> Json.fromString("709")
    )
    val result = dispatch.dispatch("get_article", args)
    assert(result.contains("不法行為"), s"Result: $result")
  }

  test("get_article: 個人情報保護法第1条 by lawId") {
    val args = JsonObject(
      "law_id_or_name" -> Json.fromString("415AC0000000057"),
      "article_number" -> Json.fromString("1")
    )
    val result = dispatch.dispatch("get_article", args)
    assert(result.contains("個人情報"), s"Result: $result")
    assert(!result.contains("エラー"), s"Got error: $result")
  }

  test("get_article: 存在しない法令 → エラーに find_laws 誘導") {
    val args = JsonObject(
      "law_id_or_name" -> Json.fromString("不存在法"),
      "article_number" -> Json.fromString("1")
    )
    val result = dispatch.dispatch("get_article", args)
    assert(result.contains("find_laws"), s"No find_laws guidance in: $result")
  }

  test("pipeline: find_laws → get_article") {
    // Step 1: 法令検索
    val findResult = dispatch.dispatch("find_laws", JsonObject("keyword" -> Json.fromString("消費者契約")))
    assert(findResult.contains("[ID:"), s"No lawId in find result: $findResult")

    // Step 2: lawId を抽出
    val idPattern = """\[ID:\s*([A-Za-z0-9]+)\]""".r
    val lawId = idPattern.findFirstMatchIn(findResult).map(_.group(1)).getOrElse(fail("lawId not found"))

    // Step 3: 条文取得
    val args = JsonObject(
      "law_id_or_name" -> Json.fromString(lawId),
      "article_number" -> Json.fromString("1")
    )
    val articleResult = dispatch.dispatch("get_article", args)
    assert(!articleResult.contains("エラー"), s"Got error: $articleResult")
    assert(articleResult.nonEmpty, "Empty article result")
    println(s"  Pipeline result (${lawId}): ${articleResult.take(150)}")
  }
}

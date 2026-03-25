package tools.egov.v2

import tools.egov.*
import scala.xml.Elem

/** e-Gov 法令 API V2 クライアント（スケルトン）。
  *
  * Phase 1 では全メソッドが stub。`capabilities` のみ V2 の能力を宣言し、
  * ツールリスト動的生成のパスを検証できるようにする。
  *
  * V2 API の仕様は `docs/egov-api/v2/lawapi-v2.yaml` を参照。
  * YAML を補完するドメイン知識は `docs/egov-api/v2/domain-reference.md` を参照。
  */
class V2Client extends EGovLawApi {

  val BaseUrl = "https://laws.e-gov.go.jp/api/2"

  def fetchLawList(category: Int = 2): Either[String, Seq[LawInfo]] =
    Left("V2 fetchLawList: not yet implemented")

  def fetchArticle(lawId: String, article: String): Either[String, Elem] =
    Left("V2 fetchArticle: not yet implemented")

  def fetchArticleWithParagraph(lawId: String, article: String, paragraph: String): Either[String, Elem] =
    Left("V2 fetchArticleWithParagraph: not yet implemented")

  def fetchLawData(lawId: String): Either[String, Elem] =
    Left("V2 fetchLawData: not yet implemented")

  def capabilities: Set[Capability] = Set(Capability.KeywordSearch)
}

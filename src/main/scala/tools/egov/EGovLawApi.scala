package tools.egov

import scala.xml.Elem

/** e-Gov 法令 API の共通インターフェース。
  *
  * V1 (`v1.V1Client`) と V2 (`v2.V2Client`) の共通操作を抽象化する。
  * 呼び出し側（[[LawRepository]], [[ArticleRepository]]）はこの trait に依存し、
  * 具体的な API バージョンの詳細を意識しない。
  *
  * == 設計判断 ==
  *  - シグネチャは既存 `EGovApiClient` と同一。移行時の呼び出し側変更を最小化するため。
  *  - `fetchArticle` の `article` パラメータは V1 では漢数字（`"第七百九条"`）。
  *    変換責任は [[ArticleRepository]] 側に置き、trait は文字列をそのまま受け取る。
  *
  * @see [[EGovBackendFactory]] バージョン文字列からインスタンスを生成する
  */
trait EGovLawApi {

  /** 法令一覧を取得する。
    * @param category 法令種別コード（1=全法令, 2=憲法・法律, 3=政令・勅令, 4=府省令・規則）
    */
  def fetchLawList(category: Int = 2): Either[String, Seq[LawInfo]]

  /** 条文を取得する。
    * @param lawId   法令ID
    * @param article 条番号（フォーマットはバックエンド依存）
    */
  def fetchArticle(lawId: String, article: String): Either[String, Elem]

  /** 条文の特定の項を取得する。
    * @param lawId     法令ID
    * @param article   条番号
    * @param paragraph 項番号
    */
  def fetchArticleWithParagraph(lawId: String, article: String, paragraph: String): Either[String, Elem]

  /** 法令全文を取得する。
    * @param lawId 法令ID
    */
  def fetchLawData(lawId: String): Either[String, Elem]

  /** このバックエンドが提供する能力の集合。 */
  def capabilities: Set[Capability]
}

package tools.egov

/** API バージョン文字列から [[EGovLawApi]] インスタンスを生成するファクトリ。
  *
  * == 使用例 ==
  * {{{
  * val backend = EGovBackendFactory.create("v2")
  * val lawRepo = new LawRepository(backend)
  * }}}
  *
  * @see [[v1.V1Client]] V1 バックエンド
  * @see [[v2.V2Client]] V2 バックエンド（Phase 1 ではスケルトン）
  */
object EGovBackendFactory {

  /** @param version `"v1"` または `"v2"`。不明な値は V1 にフォールバック。 */
  def create(version: String): EGovLawApi = version match {
    case "v2" => new v2.V2Client()
    case _    => new v1.V1Client()
  }
}

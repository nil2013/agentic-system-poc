package tools.egov

class LawRepositoryTest extends munit.FunSuite {

  val testData = Seq(
    LawInfo("129AC0000000089", "民法", "明治二十九年法律第八十九号"),
    LawInfo("140AC0000000045", "刑法", "明治四十年法律第四十五号"),
    LawInfo("415AC0000000057", "個人情報の保護に関する法律", "平成十五年法律第五十七号"),
    LawInfo("415AC0000000060", "情報公開・個人情報保護審査会設置法", "平成十五年法律第六十号"),
    LawInfo("336AC0000000120", "消費者基本法", "昭和四十三年法律第七十八号"),
    LawInfo("412AC0000000061", "消費者契約法", "平成十二年法律第六十一号"),
  )

  /** テスト用の stub バックエンド。fetchLawList はキャッシュ経由で回避されるため呼ばれない。 */
  val stubApi: EGovLawApi = new EGovLawApi {
    def fetchLawList(category: Int): Either[String, Seq[LawInfo]] = Left("stub: not called")
    def fetchArticle(lawId: String, article: String): Either[String, scala.xml.Elem] = Left("stub")
    def fetchArticleWithParagraph(lawId: String, article: String, paragraph: String): Either[String, scala.xml.Elem] = Left("stub")
    def fetchLawData(lawId: String): Either[String, scala.xml.Elem] = Left("stub")
    def capabilities: Set[Capability] = Set.empty
  }

  val repo = new LawRepository(stubApi)

  override def beforeEach(context: BeforeEach): Unit = {
    repo.setCache(testData)
  }

  override def afterEach(context: AfterEach): Unit = {
    repo.clearCache()
  }

  test("findByKeyword: 個人情報 → 2件") {
    val results = repo.findByKeyword("個人情報")
    assertEquals(results.size, 2)
    assert(results.exists(_.lawName == "個人情報の保護に関する法律"))
  }

  test("findByKeyword: 消費者 → 2件") {
    val results = repo.findByKeyword("消費者")
    assertEquals(results.size, 2)
  }

  test("findByKeyword: 存在しないキーワード → 0件") {
    val results = repo.findByKeyword("zzz_nonexistent")
    assertEquals(results.size, 0)
  }

  test("resolveLawId: lawId フォーマットはそのまま返す") {
    val result = repo.resolveLawId("129AC0000000089")
    assertEquals(result, ResolveResult.Resolved("129AC0000000089"))
  }

  test("resolveLawId: 完全一致") {
    val result = repo.resolveLawId("民法")
    assertEquals(result, ResolveResult.Resolved("129AC0000000089"))
  }

  test("resolveLawId: 完全一致（長い名前）") {
    val result = repo.resolveLawId("個人情報の保護に関する法律")
    assertEquals(result, ResolveResult.Resolved("415AC0000000057"))
  }

  test("resolveLawId: 部分一致で複数 → Ambiguous") {
    val result = repo.resolveLawId("消費者")
    result match {
      case ResolveResult.Ambiguous(candidates) =>
        assertEquals(candidates.size, 2)
      case other =>
        fail(s"Expected Ambiguous, got $other")
    }
  }

  test("resolveLawId: 存在しない → NotFound") {
    val result = repo.resolveLawId("存在しない法律")
    assertEquals(result, ResolveResult.NotFound)
  }

  test("resolveLawId: KnownLaws フォールバック") {
    // キャッシュから民法を除外して、KnownLaws のみで解決
    repo.setCache(testData.filterNot(_.lawName == "民法"))
    val result = repo.resolveLawId("民法")
    assertEquals(result, ResolveResult.Resolved("129AC0000000089"))
  }
}

package tools.egov

/** 法令一覧のキャッシュ・検索・名前解決 */
object LawRepository {

  private var cache: Option[Seq[LawInfo]] = None

  /** 法令一覧をロード（初回のみ API 呼び出し） */
  def loadLawList(category: Int = 2): Seq[LawInfo] = {
    cache.getOrElse {
      EGovApiClient.fetchLawList(category) match {
        case Right(laws) =>
          cache = Some(laws)
          laws
        case Left(err) =>
          println(s"  [LawRepository] 法令一覧取得失敗: $err")
          Seq.empty
      }
    }
  }

  /** キーワードで法令名を検索（最大10件） */
  def findByKeyword(keyword: String): Seq[LawInfo] = {
    loadLawList().filter(_.lawName.contains(keyword)).take(10)
  }

  /** 法令名または法令ID → lawId 解決 */
  def resolveLawId(lawNameOrId: String): ResolveResult = {
    // lawId フォーマット（半角英数のみ）ならそのまま返す
    if (lawNameOrId.matches("^[0-9A-Za-z]+$")) {
      return ResolveResult.Resolved(lawNameOrId)
    }

    val laws = loadLawList()

    // 1. 完全一致
    laws.find(_.lawName == lawNameOrId) match {
      case Some(law) => return ResolveResult.Resolved(law.lawId)
      case None => ()
    }

    // 2. 前方一致
    val prefixMatches = laws.filter(_.lawName.startsWith(lawNameOrId))
    if (prefixMatches.size == 1) {
      return ResolveResult.Resolved(prefixMatches.head.lawId)
    } else if (prefixMatches.size > 1) {
      return ResolveResult.Ambiguous(prefixMatches.take(5))
    }

    // 3. 部分一致
    val containsMatches = laws.filter(_.lawName.contains(lawNameOrId))
    if (containsMatches.size == 1) {
      return ResolveResult.Resolved(containsMatches.head.lawId)
    } else if (containsMatches.size > 1) {
      return ResolveResult.Ambiguous(containsMatches.take(5))
    }

    // 4. KnownLaws フォールバック（短縮名）
    KnownLaws.mapping.get(lawNameOrId) match {
      case Some(lawId) => ResolveResult.Resolved(lawId)
      case None => ResolveResult.NotFound
    }
  }

  /** テスト用: キャッシュを直接設定 */
  private[egov] def setCache(laws: Seq[LawInfo]): Unit = {
    cache = Some(laws)
  }

  /** テスト用: キャッシュをクリア */
  private[egov] def clearCache(): Unit = {
    cache = None
  }
}

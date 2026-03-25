package tools.egov

/** 法令一覧のインメモリキャッシュ、キーワード検索、および法令名→lawId の名前解決を提供する。
  *
  * == キャッシュ戦略 ==
  * 法令一覧（`/lawlists/2`、約2000件の憲法・法律）は初回アクセス時に API から取得し、
  * プロセスライフタイムの間キャッシュする。法令一覧は変更頻度が低く（新法制定・改廃時のみ）、
  * リアルタイム性は不要。
  *
  * '''スレッドセーフティ''': `var cache` に同期機構がないため、マルチスレッド環境では
  * 安全でない。単一スレッドのエージェントループ（[[agent.AgentLoop]]）からの使用を前提とする。
  *
  * == 名前解決の仕組み ==
  * LLM は法令を「民法」「個人情報保護法」のような自然言語名で指定する。
  * [[resolveLawId]] はこれを e-Gov API で使える lawId に変換する。
  * 解決の優先順位は [[ResolveResult]] を参照。
  *
  * @param api e-Gov API バックエンド
  * @see [[EGovLawApi]] バックエンドの共通インターフェース
  * @see [[tools.ToolDispatch]] `find_laws` ツールが本クラスの `findByKeyword` を使用
  */
class LawRepository(api: EGovLawApi) {

  private var cache: Option[Seq[LawInfo]] = None

  /** 法令一覧をロードする。初回呼び出し時のみ API アクセスが発生し、以降はキャッシュを返す。
    *
    * API エラー時は空の `Seq` を返す（例外を投げない）。
    * エラーは標準出力にログ出力される。
    *
    * @param category 法令種別コード（デフォルト: 2 = 憲法・法律）
    * @return 法令情報のリスト。API エラー時は空。
    */
  def loadLawList(category: Int = 2): Seq[LawInfo] = {
    cache.getOrElse {
      api.fetchLawList(category) match {
        case Right(laws) =>
          cache = Some(laws)
          laws
        case Left(err) =>
          println(s"  [LawRepository] 法令一覧取得失敗: $err")
          Seq.empty
      }
    }
  }

  /** キーワードで法令名を部分一致検索する。
    *
    * `.take(10)` で結果を制限している理由: LLM の Tool Result として返されるため、
    * 大量の結果はコンテキストウィンドウを圧迫する。10件あれば LLM が適切な法令を
    * 選択するには十分。
    *
    * @param keyword 検索キーワード（法令名に対する部分一致）
    * @return マッチした法令情報（最大10件）
    */
  def findByKeyword(keyword: String): Seq[LawInfo] = {
    loadLawList().filter(_.lawName.contains(keyword)).take(10)
  }

  /** 法令名または法令ID を lawId に解決する。
    *
    * 解決の4段階チェーン:
    *  1. '''lawId フォーマットチェック''': 半角英数のみ（`^[0-9A-Za-z]+$`）なら、
    *     すでに lawId として渡されたと判断しそのまま返す。
    *     LLM が `find_laws` の結果から lawId をコピーして渡すケースに対応。
    *  2. '''完全一致''': 法令一覧の `lawName` と完全一致する法令を探す。
    *  3. '''前方一致 / 部分一致''': 一意に特定できれば [[ResolveResult.Resolved]]、
    *     複数候補があれば [[ResolveResult.Ambiguous]]（最大5件）。
    *  4. '''[[KnownLaws]] フォールバック''': 「民法」「刑法」等の短縮名を静的マッピングで解決。
    *
    * @param lawNameOrId 法令名（全角）または法令ID（半角英数）
    * @return 解決結果。[[ResolveResult]] を参照。
    */
  def resolveLawId(lawNameOrId: String): ResolveResult = {
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

    // 4. KnownLaws フォールバック
    KnownLaws.mapping.get(lawNameOrId) match {
      case Some(lawId) => ResolveResult.Resolved(lawId)
      case None => ResolveResult.NotFound
    }
  }

  /** テスト用: キャッシュを直接設定する。`private[egov]` スコープ。 */
  private[egov] def setCache(laws: Seq[LawInfo]): Unit = {
    cache = Some(laws)
  }

  /** テスト用: キャッシュをクリアする。`private[egov]` スコープ。 */
  private[egov] def clearCache(): Unit = {
    cache = None
  }
}

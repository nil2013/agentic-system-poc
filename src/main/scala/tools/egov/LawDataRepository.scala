package tools.egov

import scala.xml.*

/** 法令全文の取得・キャッシュ・法令内キーワード検索を提供する。
  *
  * `/lawdata` エンドポイント（V1）または `/law_data` エンドポイント（V2）で取得した
  * 法令全文 XML をインメモリキャッシュし、`<Sentence>` 要素に対する部分一致検索を行う。
  *
  * == V1/V2 共通機能 ==
  * `EGovLawApi.fetchLawData` を使うため、V1/V2 どちらのバックエンドでも動作する。
  * V2 の `/keyword` エンドポイント（サーバ側インデックス検索）が利用可能な場合でも、
  * 特定法令内の検索には本クラスが有効（キャッシュヒット時はローカル走査のみ）。
  *
  * == キャッシュ戦略 ==
  * 最大 `MaxCacheSize` 法令分の全文 XML をキャッシュ。超過時は最も古いエントリを破棄（FIFO）。
  * 法令全文サイズ: 民法 ~1.7MB, 民事訴訟法 ~800KB, 行政手続法 ~80KB。
  * 5法令キャッシュで最大 ~5MB 程度のメモリ使用。
  *
  * @param api e-Gov API バックエンド
  */
class LawDataRepository(api: EGovLawApi) {

  private val MaxCacheSize = 5
  private var cache: Map[String, Elem] = Map.empty

  /** 法令全文 XML を取得する（キャッシュあり）。 */
  def getLawData(lawId: String): Either[String, Elem] = {
    cache.get(lawId) match {
      case Some(elem) => Right(elem)
      case None =>
        api.fetchLawData(lawId).map { root =>
          if (cache.size >= MaxCacheSize) {
            cache = cache.drop(1)
          }
          cache = cache + (lawId -> root)
          root
        }
    }
  }

  /** 法令内の条文をキーワードで検索する。
    *
    * 法令全文 XML を取得（またはキャッシュから取得）し、全 `<Article>` 内の
    * `<Sentence>` 要素テキストに対して部分一致検索を行う。
    * 本則（`<MainProvision>`）と附則（`<SupplProvision>`）の両方を検索対象とする。
    *
    * @param lawId      法令ID
    * @param keyword    検索キーワード
    * @param maxResults 最大結果数（デフォルト: 10）
    * @return マッチした条文のリスト。マッチしない場合は空リスト（エラーではない）。
    */
  def searchWithinLaw(lawId: String, keyword: String, maxResults: Int = 10): Either[String, List[SearchHit]] = {
    getLawData(lawId).map { root =>
      val articles = root \\ "Article"

      val hits = articles.flatMap { article =>
        val title = (article \ "ArticleTitle").text.trim
        val caption = (article \ "ArticleCaption").text.trim
        val paragraphs = article \ "Paragraph"

        paragraphs.flatMap { para =>
          val paraNum = (para \ "@Num").text.trim
          val sentences = (para \\ "Sentence").map(_.text.trim).filter(_.nonEmpty)
          sentences.filter(_.contains(keyword)).map { s =>
            SearchHit(title, caption, paraNum, s)
          }
        }
      }

      hits.take(maxResults).toList
    }
  }

  /** テスト用: キャッシュをクリアする。 */
  private[egov] def clearCache(): Unit = {
    cache = Map.empty
  }
}

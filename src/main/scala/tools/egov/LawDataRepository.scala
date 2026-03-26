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

  /** Article の Num 属性からベース条番号を抽出する。
    * "709" → Some(709), "709_2" → Some(709), "38_3_2" → Some(38)
    */
  private def baseArticleNum(numAttr: String): Option[Int] = {
    numAttr.takeWhile(_ != '_').toIntOption
  }

  /** 法令の条文範囲を取得する（MainProvision のみ、キャッシュあり）。
    *
    * ベース条番号が [from, to] の範囲にある条文をすべて取得する。
    * 枝番号付き条文（例: Num="709_2"）はベース番号（709）で判定。
    *
    * @param lawId 法令ID
    * @param from  開始条番号（inclusive）
    * @param to    終了条番号（inclusive）
    * @return 各条文のテキスト。範囲は50条以内。
    */
  def getArticleRange(lawId: String, from: Int, to: Int): Either[String, List[String]] = {
    if (from > to) {
      return Left(s"エラー: 開始条番号($from)が終了条番号($to)より大きいです。")
    }
    if (to - from > 50) {
      return Left(s"エラー: 範囲が広すぎます（${to - from + 1}条）。50条以内で指定してください。")
    }

    getLawData(lawId).map { root =>
      val mainProvision = (root \\ "MainProvision").headOption
      mainProvision.map { mp =>
        val articles = mp \\ "Article"
        val matched = articles.filter { article =>
          val numAttr = (article \ "@Num").text.trim
          baseArticleNum(numAttr).exists(n => n >= from && n <= to)
        }

        matched.map { article =>
          val caption = (article \ "ArticleCaption").text.trim
          val title = (article \ "ArticleTitle").text.trim
          val paragraphs = (article \ "Paragraph").map { para =>
            val paraNum = (para \ "@Num").text.trim
            val sentences = (para \\ "Sentence").map(_.text.trim).filter(_.nonEmpty)
            val prefix = if (paraNum.nonEmpty && paraNum != "1") s"${paraNum}　" else "　"
            prefix + sentences.mkString("")
          }
          val header = List(caption, title).filter(_.nonEmpty).mkString("\n")
          if (header.nonEmpty) s"$header\n${paragraphs.mkString("\n")}"
          else paragraphs.mkString("\n")
        }.toList
      }.getOrElse(Nil)
    }
  }

  /** 法令のメタデータを取得する（キャッシュあり）。 */
  def getMetadata(lawId: String): Either[String, LawMetadata] = {
    getLawData(lawId).map { root =>
      val law = (root \\ "Law").headOption.getOrElse(root)
      val lawBody = law \ "LawBody"
      val mainProvision = lawBody \ "MainProvision"

      LawMetadata(
        lawName = (lawBody \ "LawTitle").text.trim,
        lawNum = (law \ "LawNum").text.trim,
        era = (law \ "@Era").text.trim,
        year = (law \ "@Year").text.trim,
        lawType = (law \ "@LawType").text.trim,
        promulgateMonth = Option((law \ "@PromulgateMonth").text.trim).filter(_.nonEmpty),
        promulgateDay = Option((law \ "@PromulgateDay").text.trim).filter(_.nonEmpty),
        partCount = (mainProvision \\ "Part").size,
        chapterCount = (mainProvision \\ "Chapter").size,
        articleCount = (mainProvision \\ "Article").size,
        supplProvisionCount = (lawBody \ "SupplProvision").size
      )
    }
  }

  /** テスト用: キャッシュをクリアする。 */
  private[egov] def clearCache(): Unit = {
    cache = Map.empty
  }
}

package tools.egov

class ArticleNumberConverterTest extends munit.FunSuite {

  test("single digit articles") {
    assertEquals(v1.ArticleNumberConverter.toKanjiArticle(1), "第一条")
    assertEquals(v1.ArticleNumberConverter.toKanjiArticle(5), "第五条")
    assertEquals(v1.ArticleNumberConverter.toKanjiArticle(9), "第九条")
  }

  test("tens") {
    assertEquals(v1.ArticleNumberConverter.toKanjiArticle(10), "第十条")
    assertEquals(v1.ArticleNumberConverter.toKanjiArticle(11), "第十一条")
    assertEquals(v1.ArticleNumberConverter.toKanjiArticle(20), "第二十条")
    assertEquals(v1.ArticleNumberConverter.toKanjiArticle(99), "第九十九条")
  }

  test("hundreds") {
    assertEquals(v1.ArticleNumberConverter.toKanjiArticle(100), "第百条")
    assertEquals(v1.ArticleNumberConverter.toKanjiArticle(199), "第百九十九条")
    assertEquals(v1.ArticleNumberConverter.toKanjiArticle(300), "第三百条")
    assertEquals(v1.ArticleNumberConverter.toKanjiArticle(709), "第七百九条")
  }

  test("thousands") {
    assertEquals(v1.ArticleNumberConverter.toKanjiArticle(1000), "第千条")
    assertEquals(v1.ArticleNumberConverter.toKanjiArticle(1001), "第千一条")
  }

  test("paragraph format") {
    assertEquals(v1.ArticleNumberConverter.toKanjiParagraph(1), "第一項")
    assertEquals(v1.ArticleNumberConverter.toKanjiParagraph(3), "第三項")
  }

  test("toKanjiNumber internal") {
    assertEquals(v1.ArticleNumberConverter.toKanjiNumber(1), "一")
    assertEquals(v1.ArticleNumberConverter.toKanjiNumber(10), "十")
    assertEquals(v1.ArticleNumberConverter.toKanjiNumber(11), "十一")
    assertEquals(v1.ArticleNumberConverter.toKanjiNumber(709), "七百九")
  }
}

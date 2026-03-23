package tools.egov

class ArticleNumberConverterTest extends munit.FunSuite {

  test("single digit articles") {
    assertEquals(ArticleNumberConverter.toKanjiArticle(1), "第一条")
    assertEquals(ArticleNumberConverter.toKanjiArticle(5), "第五条")
    assertEquals(ArticleNumberConverter.toKanjiArticle(9), "第九条")
  }

  test("tens") {
    assertEquals(ArticleNumberConverter.toKanjiArticle(10), "第十条")
    assertEquals(ArticleNumberConverter.toKanjiArticle(11), "第十一条")
    assertEquals(ArticleNumberConverter.toKanjiArticle(20), "第二十条")
    assertEquals(ArticleNumberConverter.toKanjiArticle(99), "第九十九条")
  }

  test("hundreds") {
    assertEquals(ArticleNumberConverter.toKanjiArticle(100), "第百条")
    assertEquals(ArticleNumberConverter.toKanjiArticle(199), "第百九十九条")
    assertEquals(ArticleNumberConverter.toKanjiArticle(300), "第三百条")
    assertEquals(ArticleNumberConverter.toKanjiArticle(709), "第七百九条")
  }

  test("thousands") {
    assertEquals(ArticleNumberConverter.toKanjiArticle(1000), "第千条")
    assertEquals(ArticleNumberConverter.toKanjiArticle(1001), "第千一条")
  }

  test("paragraph format") {
    assertEquals(ArticleNumberConverter.toKanjiParagraph(1), "第一項")
    assertEquals(ArticleNumberConverter.toKanjiParagraph(3), "第三項")
  }

  test("toKanjiNumber internal") {
    assertEquals(ArticleNumberConverter.toKanjiNumber(1), "一")
    assertEquals(ArticleNumberConverter.toKanjiNumber(10), "十")
    assertEquals(ArticleNumberConverter.toKanjiNumber(11), "十一")
    assertEquals(ArticleNumberConverter.toKanjiNumber(709), "七百九")
  }
}

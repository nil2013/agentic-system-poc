package tools.egov

/** e-Gov API バックエンドが提供する能力を表す。
  *
  * [[tools.ToolDispatch]] がこの集合に基づいて、LLM に提示するツール定義と
  * SystemPrompt を動的に生成する。
  *
  * @see [[EGovLawApi.capabilities]] 各バックエンドが自身の能力を申告する
  */
enum Capability {
  /** 条文内容のキーワード全文検索（V2 `/keyword` エンドポイント） */
  case KeywordSearch
}

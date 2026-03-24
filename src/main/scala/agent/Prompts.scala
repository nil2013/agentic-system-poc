package agent

/** SystemPrompt のセクション定数。
  *
  * 各 StageMain は `AgentConfig.promptSections` に必要なセクションを列挙する。
  * A/B テスト時はセクションの包含/除外で制御。
  *
  * == 拡張 ==
  * 新しいプロンプトセクションを追加するには、ここに `val` を追加し、
  * 該当する StageMain の `promptSections` リストに含める。
  *
  * @see [[AgentConfig.promptSections]]
  * @see `docs/guide/advanced-topics.md` AX-3（SystemPrompt チューニング）
  */
object Prompts {

  /** 基本ロール定義。全ステージで使用。 */
  val Role: String =
    "あなたは日本法に関するアシスタントです。" +
    "ユーザの質問に答えるために、必要に応じてツールを使ってください。" +
    "ツールが不要な質問にはツールを使わずに直接回答してください。"

  /** 静かなフォールバック制御。ツールエラー時に内部知識で補完しないよう指示。
    * Stage 5 で有効性を確認済み。Stage 5+ のデフォルト。
    */
  val FallbackControl: String =
    "重要: ツールがエラーを返した場合は、エラーの内容をそのままユーザーに伝えてください。" +
    "内部知識で代替回答しないでください。「見つかりませんでした」と正直に伝えてください。"
}

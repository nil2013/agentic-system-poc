package agent

import tools.egov.Capability

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

  /** バックエンド能力に応じた案内文を生成する。
    *
    * V1 モード（KeywordSearch なし）: 条番号推測を抑制する警告
    * V2 モード（KeywordSearch あり）: search_keyword ツールの案内
    */
  def capabilityNotice(capabilities: Set[Capability]): String = {
    val base = "条番号が不明な場合は search_within_law で特定法令内の条文をキーワード検索できます。" +
      "条番号を推測せず、まずツールで検索してください。"
    if (capabilities.contains(Capability.KeywordSearch)) {
      base + "\n" +
      "法令横断のキーワード検索も利用可能です（search_keyword ツール）。"
    } else {
      base
    }
  }

  /** 事実主張の根拠明示制約（Provenance Control）。
    *
    * LLM が「〜がない」「〜は存在しない」のような不存在の断言を行う際に、
    * ツールで確認した範囲と根拠を明示させる。FallbackControl が「ある」の嘘を防ぐのに対し、
    * ProvenanceControl は「ない」の嘘を防ぐ。
    *
    * == 強度バリエーション（実験結果: `docs/research/2026-03-26_sp-provenance-experiment.md`）==
    *  - '''Light'''（推奨）: 断言抑制 + 根拠明示。確認手順の記述を誘発。過度なツール依存なし
    *  - '''Moderate''': 根拠セクションを生成。やや冗長
    *  - '''Strong''': 全主張にツール確認を要求。慎重すぎてツール過剰依存を誘発
    *
    * 切り替えて A/B テストする場合は以下のコメントアウトを入れ替える。
    */
  val ProvenanceControl: String =
    // --- Light（推奨）---
    "ツールで確認できていない事実について断言しないこと。" +
    "確認した範囲と根拠を明示すること。"
    // --- Moderate ---
    // "事実的な主張にはツール結果による根拠を示すこと。" +
    // "ツールで確認していない事実は推測であることを明示すること。"
    // --- Strong ---
    // "すべての事実的主張はツールで確認した結果に基づくこと。" +
    // "ツールで確認していない事実を述べないこと。" +
    // "内部知識による補足は、ツール結果を提示した上で付記すること。"

  /** ツール結果のタグ処理指示（PLACEHOLDER: curl 実験で文言を最終決定）。 */
  val TagHandling: String =
    "一部のツールの結果にはタグが含まれる場合があります。\n" +
    "[RESULT] のみ: 結果をユーザーに提示してください。\n" +
    "[RESULT] + [NUDGE]: 結果を提示し、[NUDGE] の指示に従って追加のツール呼び出しを行ってください。\n" +
    "[ERROR] + [NUDGE]: [NUDGE] の指示に従ってリカバリのツール呼び出しを行ってください。"
}

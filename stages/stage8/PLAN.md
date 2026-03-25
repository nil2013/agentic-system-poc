# Stage 8: REPL 実装と統合 — 作業計画

> ステータス: 未着手
> ガイド参照: `docs/guide/agentic-system-learning-guide-scala.md` §8.1–§8.3

## 目的

Stage 0-7 で構築した全コンポーネントを統合し、対話的に利用できる REPL を実装する。統合そのものが学習目標。

## 既存コンポーネント（全て完成済み）

| コンポーネント | 場所 | 役割 |
|--------------|------|------|
| AgentLoop | `agent/AgentLoop.scala` | ReAct ループ（全ラウンド reasoning キャプチャ済み） |
| ConversationState | `agent/ConversationState.scala` | セッション永続化 + truncation |
| ConversationLogger | `agent/ConversationLogger.scala` | Markdown 会話ログ（thinking セクション対応） |
| LlmClient | `agent/LlmClient.scala` | 共有 HTTP クライアント |
| Prompts | `agent/Prompts.scala` | SystemPrompt セクション定数 |
| ToolDispatch | `tools/ToolDispatch.scala` | 3ツール（find_laws, get_article, calculate） |
| e-Gov API クライアント | `tools/egov/` | 法令一覧キャッシュ + 条文取得 |

## 設計判断（実験結果に基づく）

| 判断ポイント（ガイド §8.2） | 方針 | 根拠 |
|---------------------------|------|------|
| 計画層の呼び出し | Adaptive 一択（AgentLoop そのもの） | Stage 5: PtE 全滅、Adaptive 完璧 |
| 自己評価の頻度 | ツールエラー発生時のみ | Stage 6: 全クエリ初回パス（天井効果） |
| thinking ブロック表示 | `/think` コマンドで切替 | Stage 7: thinking キャプチャ動作確認済み |
| セッション管理 | 新規/継続の切替 | ConversationState が sessionId ベースで永続化済み |

## 作業の流れ

### Phase 1: 最小限の REPL

- stdin からクエリを読み、AgentLoop に渡し、結果を表示するループ
- セッション管理（新規 / 継続）
- SystemPrompt は `Prompts.Role` + `Prompts.FallbackControl` をデフォルト
- 環境変数 `LLM_BASE_URL` 等は AgentConfig のデフォルトを利用

### Phase 2: REPL コマンド

- `/quit` or `/exit`: 終了
- `/session [id]`: セッション切替
- `/think`: thinking ブロック表示の ON/OFF 切替
- `/history`: 会話履歴の要約表示
- `/save`: 会話ログを Markdown ファイルに保存
- ツール呼び出しのプログレス表示（AgentLoop の stdout 出力を活用）

### Phase 3: 統合設計の記録

- RESULTS.md（IMRaD 形式）
- NOTES.md（実験ノート）
- 会話ログ

## 規模見積もり

- Phase 1: `Stage8Main.scala` 50-80行
- Phase 2: 同ファイルに +50-80行
- Phase 3: ドキュメントのみ
- 合計: ~150行 + ドキュメント（これまでのステージと同程度）

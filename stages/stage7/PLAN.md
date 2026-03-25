# Stage 7: Thinking/Reasoning ブロックの観察・分析 — 予定メモ

> ステータス: 実施済み + 修正適用済み（中間ラウンド reasoning キャプチャ修正を含む）
> 方針変更: ガイドの Stage 7（研究タスク応用）は本 PoC のスコープ外。研究タスク本体は別プロジェクトで本格開発する。

## 目的

Qwen3.5-35B-A3B の `reasoning_content`（thinking ブロック）を収集・分析し、モデルの内部推論パターンを観察する。

## 背景

Stage 0-6 を通じて以下が判明している:
- Thinking mode はデフォルト有効で ~1500-2000 tokens を消費する
- thinking tokens は蓄積しない（次のリクエストの messages に含まれない）
- ~~現在の実装では `reasoning_content` を捨てている~~ → **修正済み**: ChatMessage.Assistant に reasoning フィールド追加、AgentLoop で全ラウンド収集

> **設計要件（実験中に判明）**: Stage 7 を正しく実施するには、AgentLoop が全ラウンド（tool_calls あり含む）の reasoning_content を収集している必要がある。初回実行時にこの前提が欠けており、「ツール選択の推論が見えない」と誤報告した。curl で API レスポンスを直接確認し、llama-server が reasoning_content を返していることを確認した上で AgentLoop を修正。

Thinking ブロックの中身を分析すれば:
- ツール選択の推論過程が見える
- 「静かなフォールバック」（ツールエラー時の内部知識補完）の判断ロジックが見える
- thinking が有用な推論をしているのか、冗長な反芻をしているのか判別できる

## 実装見通し

### Step 1: reasoning_content のキャプチャ
- `ChatMessage.Assistant` に `reasoning: Option[String]` フィールドを追加
- `ChatMessage.fromJson` で API レスポンスの `reasoning_content` を読み取り
- `ChatMessage.toJson` では reasoning を messages に含めない（llama-server に送り返さない）

### Step 2: ConversationLogger の拡張
- ターンごとに thinking セクションを記録
- 全文 or 要約（トークン数が多いので要約の方が実用的かもしれない）

### Step 3: データ収集
- Stage 0-6 の既存テストケースを再実行し、thinking ブロックを収集
- 特に注目すべきターン:
  - ツール選択の判断場面
  - ツールエラー時の動作（「静かなフォールバック」の再現実験）
  - 複数ツール呼び出しの判断過程

### Step 4: 分析
- ツール選択の推論過程: 正しい判断のパターン vs 誤った判断のパターン
- エラー時の判断ロジック: モデルが「エラーをユーザーに伝える」vs「内部知識で補完する」を選ぶ基準
- thinking の冗長性: 有用な推論 vs 無駄な反芻の比率
- thinking tokens の消費パターン: タスク難易度と thinking 量の関係

# Stage 7: Thinking/Reasoning ブロックの観察・分析 — 実験レポート

> 実施日: 2026-03-24
> 環境: Mac Studio (M4 Pro) → GPU WS (RTX PRO 4500 Blackwell)
> モデル: Qwen3.5-35B-A3B-Q4_K_M (llama-server, --jinja -fa on)
> ガイド参照: `docs/guide/agentic-system-learning-guide-scala.md` §7.1–§7.4
> 計画: [`PLAN.md`](PLAN.md)
> 実験ノート: [`NOTES.md`](NOTES.md)

---

## 1. Introduction

### 1.1 目的

Qwen3.5-35B-A3B の `reasoning_content`（thinking ブロック）をキャプチャし、モデルの内部推論過程を観察・分析する。Stage 0-6 では「LLM が何をするか」を観察してきたが、Stage 7 では「LLM が何を考えているか」を分析する。

### 1.2 予定していた作業

| 作業 | PLAN.md 参照 |
|------|-------------|
| ChatMessage.Assistant に reasoning 追加 | Step 1 |
| AgentLoop.TurnResult に reasoning 追加 | Step 2 |
| ConversationLogger に thinking セクション追加 | Step 3 |
| 既存テストケースの再実行 + thinking 収集 | Step 4 |

### 1.3 分析観点

- ツール選択の推論過程（どのような思考経路でツールを選ぶか）
- ネガティブ結果時の判断ロジック（エラー → 追加検索 or 内部知識補完の分岐点）
- thinking tokens の消費パターン（ターン・タスクタイプ別）

---

## 2. Methods

### 2.1 テストケース

Stage 2, 5, 6 から代表的なケースを選定:

| # | クエリ | 出典 | 分析の焦点 |
|---|--------|------|-----------|
| T1 | 民法709条の条文を教えてください | Stage 2 #1 | 基本的なツール選択推論 |
| T2 | 今日の天気は？ | Stage 2 #2 | ツール不使用の判断過程 |
| T3 | 不法行為の損害賠償請求の根拠条文は？ | Stage 2 #3 | 間接的推論（709条を推定する過程） |
| T4 | 皇族に対する尊崇義務に関する法律の第1条を教えてください | Stage 5 T3 | ネガティブ結果時の判断 |
| T5 | 消費者に関する法律を3つ探し、それぞれの第1条を取得して比較 | Stage 5 T2 | 複雑タスクの計画過程 |

### 2.2 実装

- `src/main/scala/stages/Stage7Main.scala`: thinking キャプチャ付き実験
- 会話ログ: `stages/stage7/conversation-log.md`

---

## 3. Results

### 3.1 Thinking 統計

| ID | クエリ | Thinking chars | Content chars | 比率 | ツール呼び出し |
|----|--------|---------------|--------------|------|-------------|
| T1 | 民法709条 | 0 | 87 | 0% | get_article ×1 |
| T2 | 天気 | 199 | 175 | 53% | なし |
| T3 | 不法行為根拠 | 0 | 101 | 0% | find_laws + get_article |
| T4 | 非実在法令 | 335 | 0 | 100% | find_laws ×2 + get_article |
| T5 | 消費者比較 | 517 | 1577 | 25% | find_laws + get_article ×3 |
| **合計** | | **1051** | **1940** | **35%** | |

### 3.2 Thinking の内容分析

**T2（ツール不使用の判断）**: 「日本法に関する質問ではない → ツールは法律関連のみ → ツール不使用」。正しい判断の推論過程が明確に見える。

**T4（非実在法令、content 空）**: thinking で「皇族に対する尊崇義務を定めたものではない」「皇室典範を確認すべき」と正しく分析しているが、content に到達する前にトークンが尽きた。

**T5（複雑タスク）**: 「3つの法律を取得した。次にこれらの目的規定を比較する」。回答生成の構造計画。

### 3.3 構造的発見: ツール呼び出しターンでは thinking が返らない

T1, T3 で thinking chars = 0。**ツール選択の推論過程はキャプチャできない**。

原因: AgentLoop のループ構造。tool_calls を含むレスポンスでは content/reasoning_content が空（llama-server の挙動）。thinking が返されるのは最終回答（tool_calls なし）のレスポンスのみ。

→ 現在の実装では、観察可能な thinking は:
- ツール不使用の判断過程（T2）
- 最終回答生成時の推論（T4, T5）

### 3.4 遭遇した問題

1. **中間ラウンドの reasoning 未キャプチャ**: AgentLoop が中間ラウンド（tool_calls あり）の reasoning_content を捨てている。ツール選択の推論を観察するには改修が必要
2. **T4 content 空**: thinking=335 chars で max_tokens 消費。T4 は複雑な判断（非実在法令の探索）で thinking が長くなる

---

## 4. Discussion

### 4.1 thinking の有用性

観察された thinking は全て**有用な推論**:
- T2: ツール不使用の合理的判断
- T4: 法令の性質分析（「尊崇義務 ≠ 皇室典範」の正しい認識）
- T5: 回答構造の計画

「冗長な反芻」は観察されなかった。ただしサンプルサイズが小さく（thinking 付きは 3/5 ケースのみ）、より大規模なデータで検証が必要。

### 4.2 ツール選択の推論が見えない問題

Stage 7 の最大の発見は**ネガティブな発見** — 「ツール選択の推論過程がキャプチャできない」こと。tool_calls を含むレスポンスでは reasoning_content が空であり、「なぜそのツールを選んだか」は不可視。

対策候補:
- AgentLoop の各ラウンドで reasoning_content をキャプチャし、リスト化して返す
- llama-server の挙動として tool_calls 時にも reasoning_content が返るか確認（API 仕様依存）

### 4.3 thinking 比率のパターン

| パターン | Thinking 比率 | 例 |
|---------|-------------|-----|
| ツール呼び出し → 最終回答 | 0%（ツール呼び出しターン）| T1, T3 |
| ツール不使用 | ~50% | T2 |
| 複雑な最終回答 | 25-100% | T4, T5 |

ツール呼び出しが挟まるケースでは最終回答時の thinking が短くなる傾向。ツール結果が与えられた上での回答生成は thinking が少なくて済む。

### 4.4 Stage 8 への示唆

- REPL で thinking を表示するかどうかは「何が見えるか」に依存する。現状では最終回答の推論のみで、ツール選択の推論は見えない
- thinking の表示は `<details>` 折りたたみ（ConversationLogger と同じ）が適切
- T4 のように thinking で content が空になるケースでは、thinking 自体を回答として表示する判断が必要

---

## 5. 成果物

| ファイル | 内容 |
|---------|------|
| `src/main/scala/stages/Stage7Main.scala` | thinking キャプチャ付き実験 |
| `src/main/scala/messages/ChatMessage.scala` | reasoning フィールド追加 |
| `src/main/scala/agent/AgentLoop.scala` | TurnResult に reasoning 追加 |
| `src/main/scala/agent/ConversationLogger.scala` | thinkingBlock メソッド追加 |
| `stages/stage7/RESULTS.md` | 本レポート |
| `stages/stage7/NOTES.md` | 実験ノート |
| `stages/stage7/conversation-log.md` | thinking 付き会話ログ |

## 6. 次のステップ

- Stage 8（REPL 統合）に進む
- 中間ラウンドの reasoning キャプチャは発展的課題（A7-1）
- thinking の定量分析フレームワーク（A7-1）は larger scale データが必要

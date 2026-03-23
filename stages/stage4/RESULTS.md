# Stage 4: 状態管理と会話履歴 — 実験レポート

> 実施日: 2026-03-23
> 環境: Mac Studio (M4 Pro) → GPU WS (RTX PRO 4500 Blackwell)
> モデル: Qwen3.5-35B-A3B-Q4_K_M (llama-server, --jinja -fa on -c 8192)
> ガイド参照: `docs/agentic-system-learning-guide-scala.md` §4.1–§4.4
> 実験ノート: [`NOTES.md`](NOTES.md)

---

## 1. Introduction

### 1.1 目的

agentic loop における状態管理の設計パターンを実装し、コンテキストウィンドウの有限性が対話品質に与える影響を体験する。

### 1.2 予定していた作業

| 作業 | ガイド参照 | 実施状況 |
|------|-----------|---------|
| セッション永続化（ConversationState） | §4.1 | ✅ 実施 |
| 10ターン対話でコンテキスト推移を観察 | §4.2 | ✅ 実施 |
| truncation の影響観察 | §4.2 | ✅ 実施 |
| ChatMessage ADT 化 | §3.3（延期分） | ✅ 本ステージで実施 |
| sbt プロジェクト移行 | CLAUDE.md Language Strategy | ✅ 本ステージで実施 |

### 1.3 sbt プロジェクト移行

Stage 4 の複雑さ（ADT + 永続化 + エージェントループ + 複数ツール）を契機に、scala-cli から sbt に移行した。

```
src/main/scala/
├── messages/   ChatMessage ADT, JSON codecs
├── tools/      StatuteSearch, LawListSearch, Arithmetic, ToolDispatch
├── agent/      AgentLoop, ConversationState
└── stages/     Stage4Main (エントリポイント)
```

---

## 2. Methods

### 2.1 対話シナリオ（10ターン）

| Turn | Query | 期待される動作 |
|------|-------|-------------|
| 1 | 民法709条の条文 | search_statute |
| 2 | 要件を整理 | ツールなし（Turn 1 の結果を踏まえて回答） |
| 3 | 刑法199条の条文 | search_statute |
| 4 | 709 vs 199 の違い | ツールなし（Turn 1,3 の結果を踏まえて比較） |
| 5 | 個人情報に関する法律 | find_law_by_keyword |
| 6 | 個人情報保護法の第1条 | search_statute |
| 7 | 消費税計算 | calculate |
| 8 | 会話の要約 | ツールなし |
| 9 | 民法1条 | search_statute |
| 10 | 1条 vs 709条 | ツールなし（Turn 1,9 の結果を踏まえて比較） |

### 2.2 評価指標

- コンテキスト推移（メッセージ数、推定文字数、API total_tokens）
- ツール呼び出しの正否
- 過去の対話内容への参照ができているか
- truncation 後の対話品質

---

## 3. Results

### 3.1 コンテキスト推移

| Turn | Tool calls | API tokens | Messages | Est. chars |
|------|-----------|-----------|----------|-----------|
| 1 | search_statute | 1,434 | 5 | 646 |
| 2 | (none) | 1,026 | 7 | 967 |
| 3 | search_statute | 2,030 | 11 | 1,442 |
| 4 | (none) | 1,512 | 13 | 2,085 |
| 5 | find_law_by_keyword | 3,261 | 17 | 2,874 |
| 6 | search_statute | 4,005 | 21 | 3,718 |
| 7 | calculate | 4,312 | 25 | 4,156 |
| 8 | (none) | 2,537 | 27 | 4,635 |
| 9 | search_statute | 5,219 | 31 | 5,435 |
| 10 | (none) | 3,452 | 33 | 6,297 |

### 3.2 ツール呼び出し

全ターンで期待通りの動作。3ツールすべてが正しく選択された。

### 3.3 過去の対話参照

- Turn 2（「この条文の要件を整理」）: Turn 1 の民法709条を正しく参照
- Turn 4（709 vs 199 比較）: Turn 1,3 の結果を踏まえた比較表を生成
- Turn 8（会話要約）: Turn 1-7 の全内容を適切に要約
- Turn 10（1条 vs 709条比較）: Turn 1,9 の結果を踏まえた比較

→ 10ターン程度では対話品質の劣化は観察されなかった。

### 3.4 Truncation テスト

| | Before | After |
|---|--------|-------|
| Messages | 33 | 13 |
| Est. chars | 6,297 | 2,691 |
| Removed | — | 20 messages |

FIFO で古いメッセージから削除。system prompt + 最新のやり取りが残る。

### 3.5 遭遇した問題

1. **sbt コンパイルエラー**: `ChatMessage.fromJson` が `ChatMessage`（親 enum）を返すが `ChatMessage.Assistant` が必要。明示的な型アノテーションで解決
2. **個人情報保護法の条文**: Turn 6 で `search_statute("個人情報の保護に関する法律", "1")` が呼ばれたが、KnownLaws に未登録のためエラーのはず。しかし回答には条文内容が含まれる → モデルが内部知識で補完した可能性（→ NOTES.md 参照）

---

## 4. Discussion

### 4.1 コンテキスト消費の特性

- API total_tokens はターンごとに増加（1,434 → 5,219）。履歴蓄積による prompt tokens の増加
- ツール結果（条文テキスト）が含まれるターンで増加が顕著
- 10ターン / 33 messages / est. 6,300 chars で `-c 8192` の 63% 程度。あと 5-10 ターンで制限に到達すると推定

### 4.2 Thinking tokens は蓄積しない

`reasoning_content` は API レスポンスに含まれるが、次のリクエストの messages には含まれない。つまり thinking tokens はターンごとに消費されるが蓄積しない。これはコンテキスト管理にとって好都合 — 10ターン分の thinking が蓄積すると ~15-20K tokens で即座にコンテキスト超過する。

### 4.3 ガイドの予想との比較

ガイド §4.2 は「ツール呼び出し結果がトークン数を急増させる」「コンテキスト制限に近づいたときの挙動」の観察を求めていた。前者は確認できた（ツール呼び出しがあるターンで token 数が増加）。後者は 10ターンではまだ制限に到達しなかったため未観察。

### 4.4 Stage 5 への示唆

- ConversationState + AgentLoop の組み合わせで複数ターン対話が安定動作
- コンテキスト管理は 10ターン程度では問題にならない。長い対話や大きなツール結果を扱う場合に顕在化する
- sbt プロジェクト構造により、Stage 5-6 のコード追加が容易になった

---

## 5. 成果物

| ファイル | 内容 |
|---------|------|
| `build.sbt` | sbt プロジェクト定義 |
| `src/main/scala/messages/ChatMessage.scala` | メッセージ ADT + JSON 変換 |
| `src/main/scala/tools/*.scala` | ツール群（4ファイル） |
| `src/main/scala/agent/AgentLoop.scala` | エージェントループ |
| `src/main/scala/agent/ConversationState.scala` | セッション永続化 |
| `src/main/scala/stages/Stage4Main.scala` | 実験エントリポイント |
| `sessions/stage4-test.json` | セッションデータ（gitignore 対象） |
| `stages/stage4/RESULTS.md` | 本レポート |
| `stages/stage4/NOTES.md` | 実験ノート |

## 6. 次のステップ

- Stage 5（計画と分解）に進む
- コンテキスト制限到達時の挙動を長い対話で検証（任意）

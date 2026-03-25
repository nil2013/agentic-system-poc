# Stage 8: REPL 実装と統合 — 実験レポート

> 実施日: 2026-03-25
> 環境: Mac Studio (M4 Pro) → GPU WS (RTX PRO 4500 Blackwell)
> モデル: Qwen3.5-35B-A3B-Q4_K_M (llama-server, --jinja -fa on -c 16384)
> ガイド参照: `docs/guide/agentic-system-learning-guide-scala.md` §8.1–§8.3
> 計画: [`PLAN.md`](PLAN.md)
> 実験ノート: [`NOTES.md`](NOTES.md)

---

## 1. Introduction

### 1.1 目的

Stage 0-7 で構築した全コンポーネントを統合し、対話的に利用できる REPL を実装する。統合そのものが学習目標。

### 1.2 予定していた作業

| 作業 | PLAN.md 参照 |
|------|-------------|
| build.sbt に REPL 用設定追加（fork/connectInput） | Step 1 |
| Stage8Main.scala（REPL 本体 + コマンド） | Step 2 |
| 手動テスト（対話 + コマンド動作確認） | Step 4 |

### 1.3 統合されたコンポーネント

| コンポーネント | 使用箇所 |
|--------------|---------|
| AgentLoop | メイン対話ループ（ReAct + 全ラウンド reasoning キャプチャ） |
| ConversationState | セッション永続化（新規/復元/保存） |
| ConversationLogger | `/save` コマンドで会話ログ出力 |
| LlmClient | AgentLoop 経由（直接使用なし） |
| Prompts | `Prompts.Role` + `Prompts.FallbackControl` をデフォルト |
| ToolDispatch | AgentLoop 経由（find_laws, get_article, calculate） |

---

## 2. Methods

### 2.1 REPL コマンド

| コマンド | 動作 |
|---------|------|
| `/quit`, `/exit` | セッション保存して終了 |
| `/think` | thinking 表示の ON/OFF トグル |
| `/session [id]` | セッション情報表示 / 切替 |
| `/history` | メッセージ数・推定トークン表示 |
| `/save [path]` | 会話ログを Markdown 保存 |
| `/help` | コマンド一覧 |

### 2.2 設計判断

- **Adaptive 一択**: Stage 5 で Plan-then-Execute が全滅したため、AgentLoop（ReAct）をそのまま使用
- **自己評価なし**: Stage 6 で全クエリ初回パス（天井効果）だったため、REPL では自己評価を組み込まない
- **thinking はオプション表示**: `/think` で切替。デフォルト OFF

### 2.3 実行方法

```bash
sbt "runMain stages.Stage8Main"              # デフォルトセッション
sbt "runMain stages.Stage8Main my-session"   # セッション指定
```

---

## 3. Results

### 3.1 対話テスト

2セッションで計16ターン以上の対話を実施。

**repl-default セッション（5ターン）:**
- 民法709条（不法行為）、家族法（731/734/763条 parallel calling）、契約自由（92条）、金融法（ツールなし）、民訴（196条→91条）
- 法学知識を活かした回答（条文 + 解説 + 具体例 + 比較表）

**new_session セッション（11ターン）:**
- 民事訴訟法の全体構造を二分探索的に探索
- LLM が自発的に探索戦略を採用し、条文数（~405条）と大構造を概ね正確に推定

### 3.2 コマンド動作確認

| コマンド | 結果 |
|---------|------|
| `/think` | ✅ ON/OFF トグル、thinking 内容が表示される |
| `/history` | ✅ Messages: 67, Est. chars: 19612, Turns: 11 |
| `/save` | ✅ `conversation-log-1774408234.md` が生成される |
| `/quit` | ✅ 「セッション保存完了 (75 messages) Bye!」 |
| `/help` | ✅ コマンド一覧表示 |

### 3.3 セッション永続化

- `/quit` 後に同じセッションIDで再起動 → 「セッション復元: repl-default (75 messages, ~28473 chars)」で正常復元
- 「直前の会話なんだったっけ？」→ 過去10ターンの内容を正しく要約
- `--session new_session` で新規セッション作成 → 独立した会話

### 3.4 会話品質の評価

**良い挙動:**
- parallel tool calling（家族法で3条文同時取得）
- 法学知識に基づく条文選択（「家族法」→ 731/734/763条を自発的に選択）
- 構造探索での自発的な二分探索戦略
- 「中間報告」での「分かっていること/分かっていないこと」の明確な整理

**問題:**
- 民訴196条のハルシネーション（正解は91条。ツール機能欠如が主因）
- content 空問題の再現（3ターン目、契約自由の条文取得時）
- 金融法の質問でツールを呼ばずに内部知識のみで回答
- MAX_TOOL_ROUNDS=5 が探索的タスクのボトルネック

### 3.5 遭遇した問題

1. **sbt fork の TTY 非継承**: JLine が dumb terminal にフォールバックし、日本語 BackSpace と方向キーが動作しない → `run-repl.sh`（直接 JVM 起動）で解消
2. **MAX_TOOL_ROUNDS=5 のオーバーフロー**: `find_laws(1) + get_article(4) = 5` で上限到達。探索的タスクでは致命的
3. **content 空（境界パース問題）**: 散発的に回答が空になる。再試行で復帰可能だが UX 上の問題
4. **ハルシネーション**: ツール機能の限界（条文内容検索がない）により、条番号の推測を強いられる構造

---

## 4. Discussion

### 4.1 統合設計の評価

Stage 0-7 の全コンポーネントが REPL として自然に統合された。特に:
- **AgentLoop**: ReAct ループが対話的利用でも安定動作。parallel calling が自然に機能
- **ConversationState**: セッション永続化が透過的に動作。復元後も文脈参照が正確
- **Prompts**: `FallbackControl` が対話全体を通じて機能（非実在法令の問い合わせで正直に報告）
- **ConversationLogger**: `/save` で会話ログ保存。ただしメタデータ（tokens, 時刻）が不正確（事後変換のため）

### 4.2 Stage 5 の結論修正

民訴構造探索テストで、**Plan-then-Execute が有効なケースが発見された**: 「計画を立てて → 次のターンで実行」パターンで、計画時点で条文番号が確定しているため依存引数問題が発生しない。Stage 5 の結論「PtE は使えない」は「**PtE は引数が事前確定できるタスクでは有効、前ステップの結果に依存するタスクでは無効**」に修正すべき。

### 4.3 「中間報告」パターンの発見

ユーザーが「中間報告して」と求めることで、LLM が自発的に「分かっていること/分かっていないこと」を整理する。これは Stage 6 の self-evaluation をユーザーが手動発動させている構造であり、evaluator の thinking 消費問題がない（回答として content に入るため）。Stage EX での自動発動が有望。

### 4.4 Stage EX への示唆

詳細は NOTES.md に記録。主要な示唆:
- MAX_TOOL_ROUNDS の動的調整（探索的タスク対応）
- 法令構造表示ツール + 条文内容検索ツール（EX-1）
- PtE 適用条件の SystemPrompt ガイド
- 「中間報告」の N ターンごとの自動発動

---

## 5. 成果物

| ファイル | 内容 |
|---------|------|
| `build.sbt` | fork/connectInput + JLine 3 依存追加 |
| `src/main/scala/stages/Stage8Main.scala` | REPL 本体（~190行） |
| `run-repl.sh` | 直接 JVM 起動スクリプト（sbt TTY 問題回避） |
| `stages/stage8/PLAN.md` | 作業計画 |
| `stages/stage8/RESULTS.md` | 本レポート |
| `stages/stage8/NOTES.md` | 実験ノート（ハルシネーション分析、構造探索分析含む） |
| `sessions/repl-default.json` | テストセッションデータ |
| `sessions/new_session.json` | 探索テストセッションデータ |

## 6. 次のステップ

- **Stage 0-8 カリキュラム完了**。ここから先は Stage EX（REPL 実用化）
- 最優先: 法令内条文検索ツール（EX-1）と MAX_TOOL_ROUNDS 動的調整
- `docs/guide/advanced-topics.md` の「Stage EX: REPL 実用化ロードマップ」を参照

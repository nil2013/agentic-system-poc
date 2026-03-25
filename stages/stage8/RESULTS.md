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

（手動テスト後に記入）

### 3.1 対話テスト
### 3.2 コマンド動作確認
### 3.3 セッション永続化
### 3.4 会話品質の評価
### 3.5 遭遇した問題

---

## 4. Discussion

（テスト後に記入）

### 4.1 統合設計の評価
### 4.2 REPL の実用性
### 4.3 発展的課題

---

## 5. 成果物

| ファイル | 内容 |
|---------|------|
| `build.sbt` | fork/connectInput 設定追加 |
| `src/main/scala/stages/Stage8Main.scala` | REPL 本体 |
| `stages/stage8/PLAN.md` | 作業計画 |
| `stages/stage8/RESULTS.md` | 本レポート |
| `stages/stage8/NOTES.md` | 実験ノート |

## 6. 次のステップ

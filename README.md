# agentic-system-poc

ローカル LLM 推論（GPU ワークステーション上の llama.cpp）を使った agentic system の学習・PoC リポジトリ。

フレームワーク（LangChain 等）を使わず、エージェントアーキテクチャの各概念を段階的に自前で構築する。

## 前提環境

| 役割 | 構成 |
|------|------|
| 推論サーバ（大学） | GPU WS: RTX PRO 4500 Blackwell (32GB), Ubuntu 24.04, llama.cpp |
| 推論サーバ（自宅） | Mac ローカル: mlx-lm（Apple Silicon ネイティブ） |
| クライアント | Mac (M4 Pro / M4, 24GB), macOS |
| モデル（大学） | Qwen3.5-35B-A3B MoE（llama.cpp 経由） |
| モデル（自宅） | Qwen3.5-9B dense（mlx-lm 経由）。8-bit 量子化 |
| ドメインツール | e-Gov 法令 API V1/V2（認証不要、切り替え可能） |

### バックエンド切替

OpenAI 互換 API を共通インターフェースとし、3つのバックエンドを環境変数で切り替える（必須要件）。

| 環境 | サーバ | モデル |
|------|--------|--------|
| **大学** | llama-server (GPU WS) | Qwen3.5-35B-A3B |
| **自宅** | mlx-lm (Mac ローカル) | Qwen3.5-9B |
| **fallback** | OpenAI API | gpt-4o 等 |

切替は環境変数（`.env`）で行う:

```bash
# 大学: llama-server (GPU WS)
LLM_BASE_URL=http://<GPU-WS-IP>:8080/v1
LLM_API_KEY=dummy
LLM_MODEL=local

# 自宅: mlx-lm (Mac ローカル)
LLM_BASE_URL=http://localhost:8000/v1
LLM_API_KEY=dummy
LLM_MODEL=mlx-community/Qwen3.5-9B-MLX-8bit

# fallback: OpenAI API
LLM_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=sk-...
LLM_MODEL=gpt-4o
```

### e-Gov API バージョン切替

e-Gov 法令 API の V1/V2 を切り替え可能。CLI 引数が環境変数より優先される。

```bash
# 環境変数で指定
EGOV_API_VERSION=v2

# または CLI 引数で指定（環境変数より優先）
sbt "runMain stages.Stage8Main --egov-api v2"
```

V2 モードでは `search_keyword` ツール（条文内容の全文検索）が LLM に追加提示される。デフォルトは V1。

## セットアップ

```bash
# .env を作成して環境に応じたバックエンド設定を記入
cp .env.example .env
```

## 学習ステージ

各ステージが1つのアーキテクチャ概念に対応する。

| Stage | 概念 | 主な成果物 |
|-------|------|-----------|
| 0 | 推論 API 接続・ベースライン検証 | レイテンシ計測スクリプト |
| 1 | Structured Output | 法令 XML からの条文情報抽出（3手法比較） |
| 2 | Single Tool Calling (ReAct) | e-Gov API 検索ツール + エージェントループ |
| 3 | 複数ツール + ルーティング | ツール選択精度の検証 |
| 4 | 状態管理・会話履歴 | セッション永続化、コンテキスト窓管理 |
| 5 | 計画と分解 | 静的/適応的プランニング |
| 6 | 自己評価・修正ループ | 評価プロンプト + 再生成 |
| 7 | Thinking/Reasoning ブロック分析 | LLM 推論過程の観察 |
| 8 | 対話的 REPL | JLine 3 対応 REPL、全コンポーネント統合 |

Stage 0-8 で基礎カリキュラムは完了。Stage EX（REPL 実用化）で法令調査ツールとしての実用性を追求する。

詳細な実装手順は [`docs/guide/agentic-system-learning-guide-scala.md`](docs/guide/agentic-system-learning-guide-scala.md) を参照。
REPL 実用化ロードマップは [`docs/guide/repl-roadmap.md`](docs/guide/repl-roadmap.md) を参照。

## sbt プロジェクト構成（Stage 4+）

```
src/main/scala/
├── messages/     # ChatMessage ADT + JSON codecs
├── agent/        # AgentLoop, ConversationState, ConversationLogger, LlmClient, Prompts
├── tools/        # ToolDispatch (class, 動的 toolDefs), Arithmetic
│   └── egov/     # EGovLawApi trait + v1/V1Client + v2/V2Client + Domain 層
└── stages/       # Stage ごとのエントリポイント
```

## ディレクトリ構成

```
.
├── build.sbt            # sbt プロジェクト定義
├── project/             # sbt メタビルド
├── src/                 # Scala ソース + テスト（Stage 4+）
├── docs/
│   ├── guide/           # 学習ガイド（Scala版）、REPL ロードマップ、発展的課題
│   ├── egov-api/        # e-Gov API 仕様 + 設計書（v1/, v2/, docs-alpha/ に分類）
│   ├── research/        # 調査レポート
│   └── code/            # コードドキュメント（HTML）
├── scripts/             # ユーティリティスクリプト（スクレイピング等）
├── stages/              # ステージごとの作業ディレクトリ
│   ├── PROTOCOL.md      # Stage 実行プロトコル
│   ├── stage0-3/        # scala-cli スクリプト + RESULTS.md
│   ├── stage4-8/        # RESULTS.md, NOTES.md, 会話ログ
│   └── stage7/          # PLAN.md（Thinking 分析計画）
├── sessions/            # 会話セッションデータ（gitignore対象）
├── .env.example         # 環境変数テンプレート
└── .env                 # 環境変数（gitignore対象）
```

## ビルド・テスト・実行

```bash
# コンパイル
sbt compile

# ユニットテスト（ネットワーク不要）
sbt "testOnly tools.egov.ArticleNumberConverterTest tools.egov.LawRepositoryTest"

# 全テスト（統合テストはネットワーク必要）
sbt test

# REPL 起動（Stage 8）
sbt "runMain stages.Stage8Main"
# または直接 JVM 起動（JLine TTY 対応）
./run-repl.sh

# REPL 起動（V2 モード）
sbt "runMain stages.Stage8Main --egov-api v2"

# scala-cli スクリプト（Stage 0-3）
scala-cli run stages/stage0/latency.scala
```

## ドキュメント

- **コードドキュメント**: [`docs/code/README.md`](docs/code/README.md) — アーキテクチャ解説 + Usage（[HTML版](docs/code/index.html)）
- **学習ガイド**: [`docs/guide/agentic-system-learning-guide-scala.md`](docs/guide/agentic-system-learning-guide-scala.md)
- **REPL ロードマップ**: [`docs/guide/repl-roadmap.md`](docs/guide/repl-roadmap.md)
- **e-Gov API 設計書**: [`docs/egov-api/egov-law-client-design.md`](docs/egov-api/egov-law-client-design.md) — V1/V2 切り替え基盤を含む
- **ツール機能分析**: [`docs/egov-api/tool-capability-analysis.md`](docs/egov-api/tool-capability-analysis.md) — V1/V2 能力比較
- **e-Gov API ドキュメント索引**: [`docs/egov-api/CLAUDE.md`](docs/egov-api/CLAUDE.md)
- **API リファレンス**: `sbt doc` → `target/scala-3.6.4/api/index.html`

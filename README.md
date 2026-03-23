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
| ドメインツール | e-Gov 法令 API V1（認証不要） |

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

## セットアップ

```bash
# .env を作成して環境に応じたバックエンド設定を記入
cp .env.example .env
```

## ビルド・テスト・実行

```bash
# コンパイル
sbt compile

# ユニットテスト（ネットワーク不要）
sbt "testOnly tools.egov.ArticleNumberConverterTest tools.egov.LawRepositoryTest"

# 全テスト（統合テストはネットワーク必要）
sbt test

# Stage 4 実験（LLM サーバーが起動している必要あり）
sbt "runMain stages.Stage4Main"

# scala-cli スクリプト（Stage 0-3）
scala-cli run stages/stage0/latency.scala
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

詳細な実装手順は [`docs/guide/agentic-system-learning-guide-scala.md`](docs/guide/agentic-system-learning-guide-scala.md) を参照。
Python 版ガイド（[`docs/guide/agentic-system-learning-guide.md`](docs/guide/agentic-system-learning-guide.md)）は参考資料として残す。

## sbt プロジェクト構成（Stage 4+）

```
src/main/scala/
├── messages/     # ChatMessage ADT + JSON codecs
├── agent/        # AgentLoop, ConversationState, ConversationLogger
├── tools/        # ToolDispatch, Arithmetic
│   └── egov/     # e-Gov 法令 API V1 クライアント
└── stages/       # Stage ごとのエントリポイント
```

## ディレクトリ構成

```
.
├── build.sbt            # sbt プロジェクト定義
├── project/             # sbt メタビルド
├── src/                 # Scala ソース + テスト（Stage 4+）
├── docs/
│   ├── guide/           # 学習ガイド（Scala版 + Python版参考）
│   ├── egov-api/        # e-Gov 法令 API 仕様書 + 設計書
│   ├── research/        # 調査レポート
│   └── code/            # コードドキュメント（HTML）
├── stages/              # ステージごとの作業ディレクトリ
│   ├── PROTOCOL.md      # Stage 実行プロトコル
│   ├── stage0-3/        # scala-cli スクリプト + RESULTS.md
│   ├── stage4/          # RESULTS.md, NOTES.md, 会話ログ
│   └── stage7/          # PLAN.md（Thinking 分析計画）
├── sessions/            # 会話セッションデータ（gitignore対象）
├── .env.example         # 環境変数テンプレート
└── .env                 # 環境変数（gitignore対象）
```

## ドキュメント

- **コードドキュメント**: [`docs/code/README.md`](docs/code/README.md) — Scala 実装のアーキテクチャ解説 + Usage ガイド（[HTML版](docs/code/index.html)）
- **API リファレンス**: `sbt doc` → `target/scala-3.6.4/api/index.html`
- **学習ガイド**: [`docs/guide/agentic-system-learning-guide-scala.md`](docs/guide/agentic-system-learning-guide-scala.md)
- **e-Gov API 設計書**: [`docs/egov-api/egov-law-client-design.md`](docs/egov-api/egov-law-client-design.md)

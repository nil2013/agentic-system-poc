# agentic-system-poc

ローカル LLM 推論（GPU ワークステーション上の llama.cpp）を使った agentic system の学習・PoC リポジトリ。

フレームワーク（LangChain 等）を使わず、エージェントアーキテクチャの各概念を段階的に自前で構築する。

## 前提環境

| 役割 | 構成 |
|------|------|
| 推論サーバ | GPU WS: RTX PRO 4500 Blackwell (32GB), Ubuntu 24.04, llama.cpp |
| クライアント | Mac (M4 Pro / M4), macOS |
| モデル | Qwen3.5 35B（llama.cpp の OpenAI 互換 API 経由） |
| ドメインツール | e-Gov 法令 API V1（認証不要） |

### バックエンド切替

OpenAI 互換 API を共通インターフェースとし、ローカル LLM と OpenAI API をバックエンドとして切り替え可能にする（必須要件）。

- **大学**: GPU WS 上の llama.cpp に接続
- **自宅**: OpenAI API を使用（GPU WS にアクセス不可のため）

切替は環境変数（`.env`）で行う:

```bash
# ローカル LLM（大学）
LLM_BASE_URL=http://<GPU-WS-IP>:8080/v1
LLM_API_KEY=dummy
LLM_MODEL=local

# OpenAI API（自宅）
LLM_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=sk-...
LLM_MODEL=gpt-4o
```

## セットアップ

```bash
uv init
uv add httpx pydantic

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
| 7 | 研究タスク応用 | 法学ドメイン向け統合 |

詳細な実装手順は [`docs/agentic-system-learning-guide.md`](docs/agentic-system-learning-guide.md) を参照。

## 言語戦略

- **Stage 0–2**: Python（httpx, pydantic）— 学習フェーズ、コミュニティリソース活用
- **Stage 3+**: Scala 3 への移行を予定 — ADT + パターンマッチによる型安全なモデリング

## ディレクトリ構成（予定）

```
.
├── docs/                # 設計文書・学習ガイド
├── tools/               # ツール実装（Stage 2+）
├── sessions/            # 会話セッションデータ（Stage 4+、gitignore対象）
├── .env.example         # 環境変数テンプレート
├── .env                 # 環境変数（gitignore対象）
└── stage*_.py           # 各ステージの実装スクリプト
```

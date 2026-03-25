# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## General Guidance

- このプロジェクトでは速度よりも品質を重視する。素早い回答よりは最善の回答を目指すこと。
- 上記の指示は、YAGNI原則を無視することを意味するものではない。ad-hocな回答を禁じるもので、「その実装がいま必要か？」という問いは意味をなす。

## Project Overview

ローカル LLM 推論（GPU ワークステーション上の llama.cpp）を使った agentic system の学習・PoC リポジトリ。フレームワーク非依存で、エージェントアーキテクチャの各概念を段階的に構築する。

## Hardware / Infrastructure

- **GPU WS**: RTX PRO 4500 Blackwell (32GB GDDR7), Ubuntu 24.04, CUDA 13.2
- **Client**: Mac (M4 Pro/M4, 24GB), macOS
- **LLM（大学）**: Qwen3.5-35B-A3B MoE（llama.cpp サーバー経由、OpenAI 互換 API）
- **LLM（自宅）**: Qwen3.5-9B dense 8-bit（mlx-lm サーバー経由、OpenAI 互換 API）。詳細は `docs/research/2026-03-23_qwen35-9b-quantization-strategy.md` 参照
- **LLM（fallback）**: OpenAI API（gpt-4o 等）
- **Domain Tool**: e-Gov 法令 API (V1)

## Development Setup

```bash
# Scala 環境（scala-cli 使用、Stage 0–2）
brew install Virtuslab/scala-cli/scala-cli  # 未導入の場合
# 依存は各 .scala ファイル内の `//> using dep` ディレクティブで管理
```

## LLM Inference Server

すべてのバックエンドは OpenAI 互換 API を提供する。クライアントコードは `LLM_BASE_URL` の差し替えだけで切り替え可能。

### 大学: llama-server (GPU WS)

```bash
# GPU WS 側: llama-server 起動例
llama-server \
  -m <model.gguf> \
  --host 0.0.0.0 --port 8080 \
  -ngl 99 -c 8192 --jinja -fa on
```

- `--jinja` は Stage 2 以降の tool calling に**必須**
- `-c 8192`: 32GB VRAM でモデル(~20GB)ロード後、KVキャッシュに使える残量から 8192〜16384 が現実的
- API エンドポイント: `http://<GPU-WS-IP>:8080/v1/chat/completions`

### 自宅: mlx-lm (Mac ローカル)

```bash
# Mac 側: mlx-lm サーバー起動例
mlx_lm.server --model mlx-community/Qwen3.5-9B-MLX-8bit --port 8000
```

- mlx-lm 本体に tool calling サポートがマージ済み（PR #217, 2025-06-26）
- より安定した tool calling が必要な場合は `mlx-openai-server`（`--tool-call-parser qwen3_5`）も選択肢
- API エンドポイント: `http://localhost:8000/v1/chat/completions`

### Qwen3.5 + llama.cpp の注意点

- llama.cpp **b8149 以降**が必要（`qwen35moe` アーキテクチャサポート）
- 公式 chat_template.jinja に tool calling バグあり。コミュニティ修正版テンプレート（barubary/qwen3.5-barubary-attuned-chat-template）または Unsloth 版 GGUF を推奨
- "Uncensored" 派生モデルは tool calling の学習分布からずれる可能性あり。学習目的では公式 instruct 版を推奨
- llama.cpp の function calling ドキュメントで Qwen3.5 はネイティブサポート外（Qwen 2.5 まで）。Generic フォーマットハンドラにフォールバックする場合あり

## e-Gov 法令 API（ドメインツール）

認証不要・無料の公開 API。本プロジェクトでは V1（XML 形式）を使用。

- 法令一覧取得: `GET https://laws.e-gov.go.jp/api/1/lawlists/{法令種別}`
  - 1=全法令, 2=憲法・法律, 3=政令・勅令, 4=府省令・規則
- 法令本文取得: `GET https://laws.e-gov.go.jp/api/1/lawdata/{法令番号又は法令ID}`

## Architecture & Stages

段階的学習アプローチ。各ステージが1つのアーキテクチャ概念に対応:

- **Stage 0**: 推論 API 接続・ベースライン検証
- **Stage 1**: Structured Output（法令 XML から条文情報抽出）
- **Stage 2**: Single Tool Calling（ReAct パターン、e-Gov API 検索）
- **Stage 3**: 複数ツール + ルーティング
- **Stage 4**: 状態管理・会話履歴
- **Stage 5**: 計画と分解
- **Stage 6**: 自己評価・修正ループ
- **Stage 7**: Thinking/Reasoning ブロック分析（方針変更。詳細は `stages/stage7/PLAN.md`）

詳細な実装手順は `docs/guide/agentic-system-learning-guide-scala.md`（メイン）を参照。
Python 版の `docs/guide/agentic-system-learning-guide.md` は参考資料として残す。

### ガイドの運用方針

- **学習ガイド本体**（`agentic-system-learning-guide-scala.md`）は原則として静的文書。修正は設計上のバグ・環境前提の誤りに限定し、実験結果からの逆算は行わない（`stages/PROTOCOL.md` §4.2 参照）
- **発展的学習ガイド**（`docs/guide/advanced-topics.md`）は動的文書。各ステージの実験・調査中に発見された発展的課題を逐次記録・昇華させていく。本体ガイドが「基礎概念の獲得」、発展ガイドが「深掘りと応用」
- **調査レポート**（`docs/research/`）は実験・調査のスナップショット。発展ガイドの根拠となるデータ

## Language Strategy

- **Stage 0–2**: Scala 3 + scala-cli（スクリプト実行、sbt 不要）
  - sttp (HTTP), circe (JSON) を `//> using dep` で管理
  - ガイド本文のサンプルコードは Python だが、参考実装として読みつつ Scala で実装する
  - LangChain 等のフレームワーク不使用
- **Stage 3+**: Scala 3 + sbt プロジェクト
  - 複数ファイル・テスト・ビルド管理が必要になる段階で移行
  - sttp (HTTP), circe (JSON), cats-effect (非同期 IO)
  - ADT + パターンマッチでメッセージ型・ツール定義を型安全にモデリング

## Working Directory Structure

各ステージの作業はステージごとのディレクトリで行う:

```
stages/
├── stage0/    # 推論 API 疎通・レイテンシ計測
├── stage1/    # 構造化出力
├── stage2/    # 単一ツール呼び出し
├── stage3/    # 複数ツール + ルーティング
├── stage4/    # 状態管理・会話履歴
├── stage5/    # 計画と分解
└── stage6/    # 自己評価・修正ループ
```

### 実行プロトコル

各ステージの実行手順・RESULTS.md のフォーマット・ガイド修正ポリシー等は `stages/PROTOCOL.md` を参照。

### コード構成（sbt プロジェクト, Stage 4+）

```
src/main/scala/
├── messages/     # ChatMessage ADT, JSON codecs
├── tools/        # ToolDispatch, Arithmetic
│   └── egov/     # e-Gov API クライアント (Models, LawRepository, ArticleRepository, EGovApiClient, ArticleNumberConverter)
├── agent/        # AgentLoop, ConversationState, ConversationLogger, LlmClient, Prompts
└── stages/       # Stage ごとのエントリポイント (main)
```

Stage 0-3 の scala-cli スクリプトは `stages/stage0-3/` にそのまま残る。

## Key Design Decisions

- **バックエンド切替必須**: 大学（llama-server on GPU WS）・自宅（mlx-lm on Mac）・fallback（OpenAI API）の3構成を環境変数（`LLM_BASE_URL`, `LLM_API_KEY`, `LLM_MODEL`）で切り替え可能にすること
- **フレームワーク不使用**: LLM エージェントの内部構造を理解することが目的。LangChain 等の抽象化に隠れる概念を自分の手で実装する
- **ボトルネック**: LLM 推論（500ms〜数秒）が支配的。言語ランタイムの速度差は無視できる
- **JVM コスト**: 起動時のみ。定常状態の HTTP 性能は Go/Rust と同等
- **Rust/Go 不採用理由**: Rust は所有権モデルが JSON 変換と相性悪く、コンパイル時間がプロンプト反復に不向き。Go は型システムが弱い（ADT なし）

## Instant Handover (DELETE AFTER READING)

> **For next session**: 以下を確認してから作業再開。詳細は `.claude/logs/2026-03-25_session.md` をサブエージェントで参照。

### 本セッションの成果
- **Stage 0-7 完了**（全ステージ実施。Stage 8 REPL のみ残り）
- ガイド包括的改訂（R-01〜R-13）+ 発展的学習ガイド（24課題）
- reasoning_content キャプチャ機能実装
- Planning 文献調査 + Qwen3.5 量子化調査（2件）

### 主な知見
- **Stage 1-6 で一貫した天井効果**: 修正ループすら発動しない
- **Stage 7**: ツール呼び出しターンでは reasoning_content が空（ツール選択推論は観察不能）
  - → **修正済み**: AgentLoop が中間ラウンドの reasoning を未キャプチャだった実装バグ。Round 3 でツール選択推論が可視化
- **thinking 比率**: ツール結果あり → 低い (0-25%), ツール不使用 → 高い (~50%)
- **SystemPrompt 制御**: 静かなフォールバックを抑制し、Q2 で「誠実かつ能動的」な挙動を誘発

### 次のアクション（優先順）
1. Stage 8（REPL 統合）の実施
2. 発展的課題の選択的実施（A7-3 中間ラウンド reasoning が最優先）

### 運用上の注意
- llama-server は `--jinja -fa on` で起動すること
- `max_tokens` は 4096 以上を指定すること（thinking mode 対策）
- mmproj なしで VRAM ~21.7GB（Q4_K_M）。mmproj ありだと ~28GB

### 運用上の注意
- llama-server は `--jinja -fa on` で起動すること
- `max_tokens` は 4096 以上を指定すること（thinking mode 対策）
- mmproj なしで VRAM ~21.7GB（Q4_K_M）。mmproj ありだと ~28GB

# Stage 0: 推論 API 疎通・レイテンシ計測

> 実施日: 2026-03-23
> 環境: Mac Studio (M4 Pro) → GPU WS (RTX PRO 4500 Blackwell, 32GB)

## サーバー構成

- **モデル**: Qwen3.5-35B-A3B-Q4_K_M.gguf (Unsloth)
- **サーバー**: llama-server (`--jinja -fa on -ngl 99 -c 8192`)
- **VRAM 消費**: 21,688 MiB / 32,623 MiB（mmproj なし）
- **ヘルスチェック**: OK

## レイテンシ計測結果

| テスト | prompt tokens | completion tokens | prompt tok/s | generate tok/s | wall time |
|--------|--------------|-------------------|-------------|----------------|-----------|
| Short ("1+1は？") | 15 | 256 | 219.4 | 70.1 | 3.93 s |
| Medium ("三大原則を説明") | 23 | 512 | 479.2 | 69.8 | 7.45 s |
| Long (1018 tokens 入力) | 1,018 | 256 | 3,732.3 | 67.8 | 4.11 s |

## 観察

- **生成速度**: ~70 tok/s で安定。MoE 3B active + 896 GB/s 帯域の恩恵
- **プロンプト処理**: 短いプロンプトで 219-479 tok/s、長いプロンプト (1K tokens) で 3,732 tok/s。バッチ効率が効いている
- **Thinking mode**: デフォルト有効。全テストで `reasoning_content` に思考過程が入り、`content` が空（thinking だけで `max_tokens` を消費）。Stage 2 以降では `max_tokens` の拡大または thinking 制御が必要
- **合格基準 (10 tok/s 以上)**: 70 tok/s で大幅にクリア

## 遭遇した問題

1. **llama-server が `content: null` を拒否**: リクエスト JSON の `content` フィールドが `null` だと HTTP 500。`Option[String]` ではなく `String` で送る必要がある
2. **`-fa` オプション**: `-fa` 単体ではなく `-fa on` が正しい構文
3. **circe バージョン**: 0.14.10 は outdated、0.14.15 に更新

## 次のステップ

- Stage 1 に進む（構造化出力）
- Thinking mode の制御方法を調査（`/no_think` タグ or API パラメータ）
- Q6_K との比較検証は Stage 1-2 のテストケースで実施

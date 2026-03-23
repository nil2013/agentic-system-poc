# Toolchain Rules

## Language Priority

- **Scala 3 + scala-cli** をプロジェクトのデフォルト言語とする
- Python（python3）は使用しない。JSON 整形等の軽量タスクには `jq` を使う
- シェルスクリプト（bash）は環境セットアップやワンライナーに限定
- 実装・検証スクリプトは scala-cli で書き、`stages/stageN/` に配置する

## Environment Variables

- 端末依存の値（IP アドレス、ファイルパス、API キー等）をスクリプトにハードコードしない
- `sys.env.getOrElse("LLM_BASE_URL", ...)` のように環境変数経由で参照する
- 環境変数は `.env` または `.claude/settings.local.json` の `env` で端末ごとに設定する

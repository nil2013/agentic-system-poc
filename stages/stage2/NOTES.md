# Stage 2 実験ノート

実験中の観察・気付きを時系列で記録。最終的に RESULTS.md に統合する。

---

## 2026-03-23 13:40 — ツール単体テスト OK

- 民法709条、刑法199条、エラーケースすべて正常動作
- e-Gov API のレスポンスは 1-2 秒。ネットワーク遅延は問題なし

## 2026-03-23 13:45 — パス A 全5問 OK

- 全テストケースで期待通りの動作
- テスト 5（民法1条と709条の両方）: **1回の応答で parallel tool_calls を返した**。ガイドは「2回のツール呼び出し」を期待しているが、実際は 1ラウンドで 2つの tool_calls を同時に返している。これは llama-server の tool calling が parallel calling をサポートしていることを示す
- thinking mode の影響: max_tokens=4096 で content に回答が到達。ツール呼び出し判断は thinking 内で行われ、content には tool_calls JSON のみが入る（tool calling 時は content が空または null で、tool_calls フィールドに構造化データが入る）
- 回答品質: ツール結果を引用しつつ適切な説明を付加。markdown で整形されている

## 2026-03-23 13:55 — パス B: 4/5 成功、テスト 5 で MISMATCH

- テスト 1-4: 全て正常動作。single tool calling は安定
- テスト 5（複数呼び出し）: content が空で返った。ツール呼び出し JSON を生成しなかった
- **パス B は逐次的（1回の応答で1つのツール呼び出ししか返せない）** のに、テスト 5 は2つのツールを同時に呼ぶ必要がある。パス B のプロンプト設計では1回に1つの tool_call JSON しか許容していないため、モデルが「どちらを先に呼ぶか」で迷った可能性がある
- ただし content が完全に空なのは thinking がトークンを消費した可能性もある
- → パス A の parallel calling との構造的な差。パス B で複数呼び出しを実現するには、ループで逐次呼び出しする設計が必要（ガイドのパス B コードはこの設計にはなっている）
- パス B の回答品質はパス A と同等かやや詳細（要件分析まで言及）

## 2026-03-23 13:45 — scala-cli 複数ファイルでの warning

- `//> using` ディレクティブが複数ファイルにあると warning。`project.scala` に集約すべきだが、Stage 2 では無視（Stage 3 の sbt 移行で解消）
- `return` が non-local return warning。Scala 3 では `boundary`/`break` を推奨。ガイドのコードをそのまま使っているので逸脱しない

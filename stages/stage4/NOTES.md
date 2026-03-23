# Stage 4 実験ノート

実験中の観察・気付きを時系列で記録。最終的に RESULTS.md に統合する。

---

## 2026-03-23 15:45 — sbt プロジェクト初期化

- build.sbt 作成（Scala 3.6.4, sttp 4.0.19, circe 0.14.15, scala-xml 2.3.0, munit 1.1.0）
- ChatMessage ADT + JSON 変換（toJson / fromJson）を `messages` パッケージに
- ツール群を `tools` パッケージに移動（StatuteSearch, LawListSearch, Arithmetic, ToolDispatch）
- AgentLoop + ConversationState を `agent` パッケージに
- 初回コンパイルで ChatMessage の型マッチ問題（enum のバリアントが ChatMessage 型で返る）→ 明示的な型アノテーションで解決

## 2026-03-23 15:48 — 10ターン対話完走

### コンテキスト推移

| Turn | Query (要約) | Tool calls | API tokens | State msgs | Est. chars |
|------|-------------|-----------|-----------|-----------|-----------|
| 1 | 民法709条 | search_statute | 1,434 | 5 | 646 |
| 2 | 要件整理 | (none) | 1,026 | 7 | 967 |
| 3 | 刑法199条 | search_statute | 2,030 | 11 | 1,442 |
| 4 | 709 vs 199 比較 | (none) | 1,512 | 13 | 2,085 |
| 5 | 個人情報法律 | find_law_by_keyword | 3,261 | 17 | 2,874 |
| 6 | 個人情報保護法1条 | search_statute | 4,005 | 21 | 3,718 |
| 7 | 消費税計算 | calculate | 4,312 | 25 | 4,156 |
| 8 | 会話要約 | (none) | 2,537 | 27 | 4,635 |
| 9 | 民法1条 | search_statute | 5,219 | 31 | 5,435 |
| 10 | 1条 vs 709条比較 | (none) | 3,452 | 33 | 6,297 |

### 観察

- API tokens（total_tokens）がターンごとに増加: 1,434 → 5,219。履歴の蓄積で prompt tokens が増えている
- ツール呼び出し結果（条文テキスト）が含まれるターンでの token 増加が顕著
- 10ターンで est. chars ~6,300。`-c 8192` のコンテキスト制限にはまだ余裕があるが、あと数ターンで到達しそう
- **llama-server はコンテキスト超過時にエラーを返さず、内部で切り詰めている可能性**（Turn 9 で 5,219 tokens は -c 8192 の 63%）

### Truncation テスト

- 3000 chars 上限で truncate: 33 messages → 13 messages（20 messages 削除）
- system prompt + 最新のやり取りのみ残る
- 削除は先頭から順に行われる（FIFO）

### 個人情報保護法の検索

- Turn 6 で `search_statute("個人情報の保護に関する法律", "1")` が呼ばれた
- KnownLaws に「個人情報の保護に関する法律」は登録されていないので、エラーが返されるはず
- しかし回答は条文内容を含んでいる → **モデルが内部知識で補完した可能性**。要確認
- → あるいは LLM が条文テキストを生成しているだけで、実際のツール結果はエラーだった可能性。ログからは区別できない

## 2026-03-23 15:48 — thinking tokens は履歴に含まれない

- reasoning_content は API レスポンスに含まれるが、次のリクエストの messages には含まれない
- つまり thinking tokens はターンごとに消費されるが蓄積しない
- これはコンテキスト管理の観点では好都合（thinking は毎ターン ~1500-2000 tokens だが、それが10ターン分蓄積したら 15-20K tokens で即座にコンテキスト超過する）

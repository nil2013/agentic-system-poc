# Stage 6 実験ノート

実験中の観察・気付きを時系列で記録。最終的に RESULTS.md に統合する。

---

## 2026-03-24 15:35 — 実験設計

- SystemPrompt 制御を AgentConfig デフォルトに組み込む（Stage 5 で効果確認済み）
- tool_result_consistent の機械検証: ツール結果が "エラー:" で始まるのに回答に条文テキストパターンが含まれていたら不整合
- テストケース: 正常系（民法709条）+ ネガティブ系（非実在法令）+ 複合（正常+ネガティブの混在）

## 2026-03-24 16:00 — 実験結果（Round 1）

### 問題1: evaluator の content が全て空

全3クエリで evaluator の content が空。max_tokens=2048 でも thinking が消費しきっている。evaluator は JSON のみを出力すれば十分なので content は数十トークンで済むはずだが、thinking が ~1500-2000 tokens 消費するため 2048 では不足。

→ 対策: evaluator の max_tokens を 4096 に引き上げ

### 問題2: Q2 で「皇室典範」を自力で発見

Q2（非実在法令「皇族に対する尊崇義務に関する法律」）で、LLM が:
1. `find_laws("皇族に対する尊崇義務")` → 0件
2. **自力で `find_laws("皇室")` を追加呼び出し** → 皇室典範がヒット
3. `get_article("皇室典範", "1")` → **ツールで正しく取得成功**

これは SystemPrompt 制御が効いた結果の「良い方向への進化」:
- Stage 5 制御なし: 内部知識で皇室典範第1条を生成（出典偽装）
- Stage 5 制御あり: 「見つかりません」と報告
- Stage 6: **ツールを使って正しく発見**（adaptive に追加検索を実行）

→ 機械検証の toolConsistent=true は正しい判定。ツール経由で取得しているため。ただし「皇族に対する尊崇義務に関する法律」は非実在なのに「皇室典範がそれに該当する」と回答している点は意味的な問題

### 問題3: Q3 の answer が空

Q3 で answer が空文字列。thinking が max_tokens を消費。Q3 は複合タスク（民法709条 + 非実在法令）で、thinking が長くなった

→ 対策: max_tokens=4096 でもQ3には不足する可能性。ただし Stage 0 で確認した通り、thinking tokens は蓄積しないので、テスト回数を増やしても同じ問題が再発する

### 問題4: LLM evaluator が全て失敗したため二重防御の検証ができなかった

機械検証のみで判定が行われ、LLM 判定との一致率が計測できない

## 2026-03-24 16:03 — Round 2（evaluator max_tokens=4096）

### Q1: evaluator が動作、面白い false positive

evaluator が JSON を返した。結果:
- source_cited=true, answers_question=true, tool_result_consistent=true → **パス**
- ただし issues に「条文番号の誤り：709条 vs 第七百九条」と指摘
- **これは false positive** — 「第七百九条」は漢数字表記であり「709条」と同一。evaluator がアラビア数字と漢数字の対応を理解できていない
- internally_consistent=false もこの誤解に起因

→ **self-consistency bias ではなく、evaluator の知識不足**。法学ドメインの評価には条番号表記の正規化が必要

### Q2: evaluator 失敗だが agent の挙動が改善

evaluator は content 空（Q2 は複雑で thinking が長い）。しかし agent は:
1. `find_laws("皇族 尊崇義務")` → 0件
2. `find_laws("皇室")` → 皇室典範がヒット
3. `get_article("皇室典範", "1")` → ツール経由で取得成功

回答は「皇族に対する尊崇義務という文言そのものが第1条にある法律は存在しない」と**正直に報告**しつつ、関連法令（皇室典範）をツール経由で提示。Stage 5 の制御なし（出典偽装）→ 制御あり（存在しません）→ Stage 6（存在しないが関連法令をツールで発見）と段階的に改善

### Q3: 複合タスクで正直な回答

Q3 で「見つかりませんでした。比較できません」と正直に回答。ツールが否定結果を返した部分について内部知識で補完しなかった。SystemPrompt 制御が効いている。

### 機械検証 vs LLM 判定の一致率

Q1 のみ LLM evaluator が動作。tool_result_consistent、source_cited ともに一致（1/1）。サンプルサイズが小さすぎて統計的意味はないが、方向性は合致。

### evaluator の thinking 消費問題は構造的

Q2, Q3 で evaluator が失敗するのは、複雑なクエリほど evaluator の thinking が長くなるため。max_tokens=4096 でも不足。対策:
- response_format: json_object を指定して thinking を抑制できるか試す（ただし llama-server で動作するか未確認）
- thinking を無効にする方法が必要（/no_think が効かない問題は §0.3 で記録済み）

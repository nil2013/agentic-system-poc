# ツールルーティング検証テスト

> 目的: 全8ツールについて、LLM が適切なツールを選択するかを検証する
> 方法: curl で会話の冒頭（System + User）を送信し、LLM の tool_calls を観察
> SP: 現在の Prompts（Role + FallbackControl + capabilityNotice + TagHandling）

## テストケース

### 優先度 A（ナッジ・連鎖フローの検証）

| ID | 質問 | 期待ツール | 検証ポイント |
|----|------|-----------|------------|
| **T-3** | 「民法の不法行為に関する条文を全部見せて」 | `search_within_law` → `get_article_range` | 検索 → 範囲取得の連鎖が起きるか |
| **T-5** | 「個人情報保護法で「個人情報」の定義は？」 | `get_definitions` | 定義質問で新ツールを選ぶか |
| **T-7** | 「民法で「善意」はどういう意味？」 | `get_definitions` → `search_within_law` | ナッジフォールバック連鎖 |

### 優先度 B（個別ツールの選択精度）

| ID | 質問 | 期待ツール | 検証ポイント |
|----|------|-----------|------------|
| **T-4** | 「消費者契約法の全体構造を教えて」 | `get_law_structure` | 構造質問でツール使用するか（前回の課題） |
| **T-6** | 「行政手続法はどのくらいの規模の法律？」 | `get_law_metadata` | メタデータ質問 |
| **T-2** | 「民法709条を見せて」 | `get_article` | 条番号指定の直行パターン |

### 優先度 C（基本動作確認）

| ID | 質問 | 期待ツール | 検証ポイント |
|----|------|-----------|------------|
| **T-1** | 「民法について教えて」 | `find_laws` or 任意 | 曖昧質問の挙動 |
| **T-8** | 「1500 * 1.1 を計算して」 | `calculate` | 非法令ツール |

## 結果記録（2026-03-26）

### 一括結果

| ID | 質問 | 期待ツール | **実際** | 判定 |
|----|------|-----------|---------|------|
| T-2 | 民法709条を見せて | `get_article` | **`get_article`** | ✅ |
| T-3 | 民法の不法行為に関する条文を全部見せて | `search_within_law` | **`find_laws`** | ⚠️ |
| T-4 | 消費者契約法の全体構造を教えて | `get_law_structure` | **`find_laws`** | ⚠️ |
| T-5 | 個人情報保護法で「個人情報」の定義は？ | `get_definitions` | **`find_laws`** | ⚠️ |
| T-6 | 行政手続法はどのくらいの規模の法律？ | `get_law_metadata` | **`find_laws`** | ⚠️ |
| T-7 | 民法で「善意」はどういう意味？ | `get_definitions` | **`find_laws`** | ⚠️ |
| T-1 | 民法について教えて | 任意 | 未実施 | — |
| T-8 | 1500 * 1.1 を計算して | `calculate` | 未実施 | — |

### 観察: find_laws 偏重パターン

**7テスト中 6テストで `find_laws` を最初に呼ぶ**。唯一の例外は T-2（「民法709条を見せて」）で、`get_article` を直接呼んだ。

T-2 が直行できた理由: `get_article` の description に「法令IDまたは法令名」と明記されており、LLM が法令名を直接渡せることを認識している。

他のツール（get_definitions, get_law_structure, get_law_metadata, search_within_law）も全て「法令IDまたは法令名」を受け付けるが、LLM はまず `find_laws` で法令ID を取得しようとする。

### 分析

`find_laws` を経由すること自体は間違いではない（マルチステップの慎重なアプローチ）。しかし:
1. **無駄な1ラウンド消費**: find_laws → 目的ツール で 2 ラウンド使う
2. **MAX_TOOL_ROUNDS の圧迫**: ナッジ連鎖を含むと find_laws(1) + get_definitions(2) + search_within_law(3) + get_article(4) で 4 ラウンド
3. **T-2 は直行できた**: get_article の description パターンを他ツールに適用すれば改善する可能性

### 改善候補

**A. ツール description の統一**: 各ツールの description に「法令名を直接指定可能（find_laws は不要）」を明記
**B. SP にツール選択ガイドを追加**: 「法令名が分かっている場合は find_laws を経由せず直接ツールに渡してよい」
**C. find_laws の description 変更**: 「法令名が分かっている場合は他のツールに直接指定できるため find_laws は不要」

---

## 改善 A 適用後の再テスト結果

### 変更内容

全ツールの description に「法令名を直接指定可能（find_laws は不要）」を追加。
`find_laws` の description を「法令名が不明な場合やキーワードで探したい場合に使う」に変更。
`search_within_law` の「find_laws で法令IDを取得してから使用する」を削除。

### 再テスト結果

| ID | 質問 | Before | **After** | 判定 |
|----|------|--------|----------|------|
| T-2 | 民法709条を見せて | `get_article` ✅ | (変更なし) | ✅ |
| T-3 | 民法の不法行為に関する条文を全部見せて | `find_laws` ⚠️ | **`get_law_structure`** | ✅ |
| T-4 | 消費者契約法の全体構造を教えて | `find_laws` ⚠️ | **`get_law_structure`** | ✅ |
| T-5 | 個人情報保護法で「個人情報」の定義は？ | `find_laws` ⚠️ | **`get_definitions`** | ✅ |
| T-6 | 行政手続法はどのくらいの規模の法律？ | `find_laws` ⚠️ | **`get_law_metadata`** | ✅ |
| T-7 | 民法で「善意」はどういう意味？ | `find_laws` ⚠️ | **`get_definitions`** | ✅ |

### 結論

**description の「法令名を直接指定可能（find_laws は不要）」の一文で find_laws 偏重が完全に解消。** SP チューニング（改善 B）は不要だった。

T-3 は `search_within_law` ではなく `get_law_structure` を選んだが、「全部見せて」→「まず構造把握」は合理的な探索戦略。

---

## チューニング履歴

失敗パターンに対する SP / description の修正を記録する。

| 日付 | テスト ID | 問題 | 対応 | 結果 |
|------|----------|------|------|------|
| | | | | |

## 追加テストパターン候補

テスト失敗時に、同じツールを呼ぶべき別の質問パターンを追加して再検証する。

| ツール | 追加パターン候補 |
|--------|----------------|
| `get_definitions` | 「○○法で△△とは何を指す？」「○○法における△△の意味は？」 |
| `get_law_structure` | 「○○法の目次を見せて」「○○法は何章構成？」 |
| `get_law_metadata` | 「○○法の基本情報を教えて」「○○法はいつ公布された？」 |
| `get_article_range` | 「○○法の第X条から第Y条まで見せて」 |
| `search_within_law` | 「○○法で△△について規定している条文は？」 |

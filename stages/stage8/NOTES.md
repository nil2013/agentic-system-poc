# Stage 8 実験ノート

実験中の観察・気付きを時系列で記録。最終的に RESULTS.md に統合する。

---

## 2026-03-25 11:17 — REPL 起動時の Warning と日本語入力問題

### 症状

1. `scala.runtime.LazyVals$` / `sun.misc.Unsafe` 警告 → Scala 3.6.4 + JDK 25 の組み合わせ問題。キーボード問題とは無関係
2. `java.lang.System::load` / native access 警告 → JDK 24+ の制限。`--enable-native-access=ALL-UNNAMED` で抑制可能
3. **`Unable to create a system terminal, creating a dumb terminal`** → **主因**

### 原因分析

sbt の `connectInput := true` は stdin のバイト転送のみで、**TTY（制御端末）の継承は保証しない**。JLine は OS の制御端末（system terminal）を必要とするが、sbt fork 経由ではそれが取得できない。

dumb terminal にフォールバックすると JLine の行編集機能（カーソル移動、マルチバイト対応 BackSpace、入力履歴）が全て無効になる。

### 対応方針（優先順位順）

1. **sbt 以外での直接起動で切り分け**: これで直るなら sbt fork + TTY 継承が原因確定
2. **JLine 依存を明示化 + バージョン更新**: umbrella artifact → terminal/reader/terminal-jni 分離、3.28→3.30 系
3. **TerminalBuilder 設定改善**: `dumb(false)` で失敗を明示化、`provider("jni")`, `encoding(UTF_8)` 指定
4. **`--enable-native-access=ALL-UNNAMED`** を javaOptions に追加
5. **Scala/JDK 組み合わせ是正**: JDK 25 のサポート下限は Scala 3.7.1。3.6.4 は非推奨帯

### 補足: Scala 3.6.4 + JDK 25 の互換性

公式互換表では JDK 25 のサポート下限は Scala 3.7.1。3.6.4 は「動くことはあるが推奨されない」。`LazyVals` の `Unsafe` 依存は Scala 3.8 で解消。ただしこれはキーボード問題の直接原因ではない。

## 2026-03-25 11:44 — run-repl.sh での直接起動テスト

### 結果: 日本語入力問題は解消

直接 JVM 起動で JLine が system terminal を正常に取得。マルチバイト BackSpace、カーソル移動、入力履歴が正常動作。→ **sbt fork の TTY 非継承が原因確定**。

### 対話テスト（5ターン）

1. **「適当な条文紹介してよ」** → `get_article("民法", "709")` を呼び、不法行為の詳細解説を生成。正常動作
2. **「家族法の条文は？」** → `get_article` ×3 (731, 734, 763) の parallel calling。婚姻適齢、近親婚禁止、協議離婚の解説
3. **「契約の自由を定めた条文」** → `get_article("民法", "92")` を呼んだが **content が空**。thinking/content 境界パース問題の再現か
4. **「さっきの最終返答をもっかい出して」** → 再生成で正常に回答。民法92条 + 契約自由の原則の解説
5. **「金融関係の法律」** → **ツールを一切呼ばずに内部知識だけで回答**。銀行法、金商法、貸金業法等の体系的解説

### 観察

- 3ターン目の content 空: Stage 7 T4 と同じ境界パース問題の再現。再試行で復帰可能だがUX上の問題
- 5ターン目のツール不使用: 「解説してくれ」という質問の性質上ツール不要と判断した可能性。ただし `find_laws("金融")` で実在法令を確認してから回答する方が信頼性が高い。ツール追加 + SystemPrompt チューニングで改善余地あり（→ Stage EX）

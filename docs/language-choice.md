# 言語選択に関するやり取り

* Original: https://claude.ai/chat/4fb8d11c-e689-4d64-9847-42eac65ff5f7

### User

本当はScalaが好きなのですがJava上で動くシステムなのでAgentic Systemにするには少々重いのではないか・・・という不安を感じています（特に、反復的なTool Callingの過程では）。この辺、どうでしょうか。
とはいえPythonは好きな言語ではないので、AIを含むモダンなシステムでわりと採用されているRustまたはGoを検討しています。どう思いますか？

### Claude Opus 4.6 @ Web

率直に言うと、JVMの「重さ」への懸念はこのユースケースでは的外れです。そして、Rust/Goへの乗り換えは目的に対してROIが悪いと考えます。順に説明します。

## Agentic Systemのボトルネックはどこにあるか

Agentic loopの1イテレーションの時間内訳を概算します。

| 処理 | 所要時間 | 支配要因 |
|------|---------|---------|
| HTTP リクエスト送信 | ~1 ms | ネットワーク (LAN) |
| LLM推論（トークン生成） | **500 ms〜数秒** | **GPU推論速度** |
| JSONレスポンスのパース | ~0.1 ms | CPU |
| ツール実行（e-Gov API呼び出し等） | 100〜500 ms | 外部API |
| ツール結果のメッセージ構築 | ~0.01 ms | CPU |

LLM推論が全体の90%以上を占めます。クライアント側の言語ランタイムがどれだけ速くても、GPUがトークンを生成し終わるまで待っているだけです。JVMの起動時間（数秒）は長時間稼働するエージェントプロセスでは一度きりのコストですし、定常状態でのJVMのHTTPクライアント性能はGoやRustと実用上差がありません。

つまり、「反復的なtool callingでJVMが重い」という懸念は、ボトルネックではない場所を最適化しようとしていることになります。

## Scala 3が実は良い選択である理由

このユースケースの本質的な難しさは「速度」ではなく「型の安全性と構造の表現力」です。

Agentic systemのクライアントコードが扱うデータは、OpenAI互換APIのメッセージ型、ツール定義のJSONスキーマ、ツール呼び出し結果の構造化データ、会話状態のシリアライズ/デシリアライズといったものです。これらはまさにADT（代数的データ型）とパターンマッチの得意領域であり、Scala 3の強みが直接活きます。

```scala
// Scala 3でのメッセージ型の例（これがPythonだとdictの海になる）
enum Message {
  case System(content: String)
  case User(content: String)
  case Assistant(content: Option[String], toolCalls: List[ToolCall])
  case ToolResult(toolCallId: String, content: String)
}

enum AgentAction {
  case Respond(text: String)
  case CallTool(name: String, args: Map[String, String])
  case GiveUp(reason: String)
}
```

Pythonでは `dict[str, Any]` の海を泳ぐことになり、ツール呼び出しのJSONの `arguments` フィールドが文字列なのかオブジェクトなのか、`tool_calls` が `None` なのか空リストなのか、といった型の曖昧さに起因するバグが頻出します。Stage 2のパスAのコードを見返すと、`msg.get("tool_calls")` の `None` チェックや `json.loads(tc["function"]["arguments"])` のパースが随所にあり、これらはすべてScala 3なら型レベルで排除できるものです。

## Rust/Goに移行した場合に何が起こるか

**Rust:** 型安全性はScala 3と同等以上ですが、所有権モデルがHTTP応答のJSON→構造体→メッセージ履歴への変換で摩擦を生みます。`serde` + `reqwest` で書けはしますが、LLMエージェント開発は本質的に試行錯誤（プロンプト変更→実行→観察のサイクル）であり、コンパイル時間が長いRustはこのサイクルを遅くします。得られる性能上の利点はゼロ（ボトルネックはGPU推論なので）。

**Go:** コンパイルは速いですが、型システムがScala 3より貧弱です。ジェネリクスの制限、ADTの不在、エラーハンドリングの冗長さ（`if err != nil` の連鎖）が、ツール定義やメッセージ型の表現で不便になります。LLMエージェントのエコシステムもPythonほどではなく、Goで書く特段のメリットがありません。

**どちらも「好きな言語ではないPythonの代わりに、やはり好きではない言語で書く」というトレードオフになり、しかも得られるものが少ない。**

## 提案

学習ガイドのStage 0-2はPythonのまま進めてください。理由は、tool callingの互換性デバッグ（§0.3のQwen3.5 Jinjaテンプレート問題等）でコミュニティの情報がほぼすべてPythonベースであり、問題の切り分けにPythonの即時実行性が有利だからです。ここは「学習環境の構築」フェーズであり、言語の好みより問題の切り分け速度が優先します。

Stage 3以降でScala 3に移植してください。この時点で「OpenAI互換APIのメッセージ型」「ツール定義のスキーマ」「エージェントループの構造」が体感で分かっているので、Scala 3のADTとパターンマッチで適切にモデリングできます。依存ライブラリは `sttp`（HTTPクライアント）+ `circe`（JSON）+ `cats-effect`（非同期IO、並列ツール呼び出しに有用）で十分です。フレームワーク不使用なので移植のロックインはありません。

JVMの「重さ」を気にするなら、GraalVM Native Imageでネイティブバイナリにコンパイルする選択肢もありますが、前述の通りこのユースケースでは不要です。

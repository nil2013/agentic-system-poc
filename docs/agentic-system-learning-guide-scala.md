# ローカルLLM推論によるAgentic System段階的構築ガイド

> 作成日: 2026-03-22
>
> 本ドキュメントは、GPU WS上のローカルLLM推論APIを前提に、Mac側でagentic systemを段階的に構築するための自己完結的な学習ガイドである。各ステージで1つのアーキテクチャ概念を獲得し、最終的に法学ドメイン向けシステムの設計判断ができるようになることを目標とする。

---

## 0. 前提条件と技術的制約

### 0.1 ハードウェア構成

| 役割 | 機器 | 関連仕様 |
|------|------|----------|
| **推論サーバ** | GPGPU WS (RTX PRO 4500 Blackwell, 32GB GDDR7) | Ubuntu 24.04, CUDA 13.2, llama.cpp ビルド済み |
| **クライアント** | Mac mini M4 Pro (24GB) / MacBook Air M4 (24GB) | macOS, scala-cli |
| **ネットワーク** | Wi-Fi経由（ルータLAN 1GbE） | レイテンシは概ね1ms以下（LAN内）。帯域は実用上十分 |

### 0.2 推論サーバ側の現状

- **llama.cpp**: git clone → cmake ビルド済み。llama-server が利用可能
- **導入済みモデル**: `Qwen3.5-35B-A3B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf` (~20GB)

### 0.3 重大な技術的前提: Qwen3.5 + llama.cpp のtool calling互換性

**2026年3月時点の状況を以下に整理する。ここを読み飛ばすとStage 2以降で動かない。**

1. **llama.cppのバージョン要件**: Qwen3.5はアーキテクチャ `qwen35moe` として認識される。これをサポートするには **llama.cpp b8149以降** が必要。現在のビルドがこれより古い場合、`git pull && cmake --build build --config Release` で更新する必要がある。

2. **Qwen3.5のJinjaテンプレートにバグがある**: 公式のchat_template.jinjaにはtool calling関連で複数の既知バグ（`items`フィルタの型エラー等）が報告されている。コミュニティによる修正版テンプレート（[barubary/qwen3.5-barubary-attuned-chat-template](https://huggingface.co/barubary/qwen3.5-barubary-attuned-chat-template)）が存在し、llama.cppでの動作報告がある。

3. **"Uncensored" バリアントのリスク**: 導入済みモデルは公式instruct版をさらにファインチューニングした派生モデルであり、tool callingの学習分布からずれている可能性がある。**学習目的では公式のQwen3.5-35B-A3B GGUFの使用を強く推奨する。** Unsloth版（`unsloth/Qwen3.5-35B-A3B-GGUF`）が改善されたテンプレート付きで配布されている。

4. **llama-serverの起動には `--jinja` フラグが必須**: tool calling対応のためにはJinjaテンプレートエンジンを有効化する必要がある。なお、llama.cppのfunction calling公式ドキュメントでQwen3.5はネイティブサポートとしてリストされていない（Qwen 2.5まで）。Qwen3.5はGenericフォーマットハンドラにフォールバックする可能性があり、その場合トークン消費が増える。

5. **コンテキストウィンドウの実効制限**: Qwen3.5-35B-A3Bは262Kトークンのコンテキストをサポートするが、32GB VRAMに~20GBのモデルを載せると残り~12GBがKVキャッシュに使える。MoEモデルは全expertの重みがVRAMに常駐するため、実効コンテキストは数千〜数万トークン程度と見積もるべきである。llama-serverの `-c` パラメータで明示的に制限する（推奨: 8192〜16384から開始）。

**→ Stage 0の最初のタスクとして、これらの前提条件の検証を行う。**

### 0.4 言語とツールチェーン

本ガイドでは全ステージを **Scala 3 + scala-cli** で実装する。

scala-cliは単一ファイルで依存付き即時実行が可能であり、sbtプロジェクトの初期化は不要。ファイル先頭の `//> using` ディレクティブで依存を宣言するだけでよい。「curlで動作確認→Scalaスクリプトに落とす」という反復サイクルに十分対応できる。

```bash
# Mac側: scala-cliの導入確認
scala-cli version
# Scala CLI version: 1.12.3
# Scala version (default): 3.8.2
```

**共通依存ディレクティブ（全スクリプトのファイル先頭に記載）:**

```scala
//> using scala 3.6
//> using dep com.softwaremill.sttp.client4::core:4.0.19
//> using dep com.softwaremill.sttp.client4::circe:4.0.19
//> using dep io.circe::circe-generic:0.14.15
//> using dep io.circe::circe-parser:0.14.15
```

この4行で、HTTPクライアント（sttp）+ JSON encode/decode（circe）+ case classの自動導出が揃う。

> **補記: sttp-openai (sttp-ai) について。** SoftwareMillは `sttp-openai` (0.3.6) というOpenAI互換クライアントライブラリも提供しており、llama-server等のOpenAI互換エンドポイントにも接続できる。ただし、本ガイドの目的はagentic loopの内部構造を理解することであるため、Stage 0〜6ではHTTPリクエストとJSONの組み立てを自分で行う。Stage 7（研究タスクへの接続）以降で `sttp-openai` への移行を検討してもよい。

> **補記: Scala 3.6を指定する理由。** scala-cliのデフォルトは3.8.2だが、sttpやcirceの最新安定版がScala 3.8系でのバイナリ互換を保証しているか確認できていない。Scala 3.6（LTS系列3.3の後継で広くサポートされている）を指定することでライブラリの互換性問題を回避する。動作確認後に3.8へ引き上げてもよい。

### 0.5 e-Gov法令API

本ガイドのツール実装ではe-Gov法令APIを使用する。認証不要・無料で利用できる公開APIである。

| 版 | ベースURL | データ形式 | 状況 |
|----|-----------|-----------|------|
| V1 | `https://laws.e-gov.go.jp/api/1/` | XML | 安定稼働中 |
| V2 | `https://laws.e-gov.go.jp/api/2/` | JSON (XML も可) | 2025年3月〜運用開始 |

本ガイドではV1（XML形式）を使用する。Scala標準ライブラリの `scala.xml` で直接パースできるため追加依存が不要である。

**主要エンドポイント:**

- 法令一覧取得: `GET /api/1/lawlists/{法令種別}` （1=全法令, 2=憲法・法律, 3=政令・勅令, 4=府省令・規則）
- 法令本文取得: `GET /api/1/lawdata/{法令番号又は法令ID}`

```bash
# 動作確認: 行政手続法の全文取得
curl -s "https://laws.e-gov.go.jp/api/1/lawdata/405AC0000000088" | head -50
```

---

## Stage 0: 推論APIの疎通と基盤検証

**獲得する概念:** LLM推論のレイテンシ・スループット特性。推論サーバの起動・運用の基本。

**所要時間:** 2〜4時間（llama.cppの更新が必要な場合を含む）

### 0-A. llama.cppのバージョン確認と更新

```bash
# GPU WS側
cd ~/workspace/llama.cpp
git log --oneline -1  # 現在のコミットを確認

# b8149以降が必要。古い場合:
git pull
cmake --build build --config Release -j$(nproc)

# ビルド後の確認
./build/bin/llama-server --version
```

### 0-B. 推論サーバの起動

```bash
# GPU WS側: tool calling対応で起動
./build/bin/llama-server \
  -m /data/models/llm-models/Qwen3.5-35B-A3B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf \
  --host 0.0.0.0 --port 8080 \
  -ngl 99 \
  -c 8192 \
  --jinja \
  -fa on
```

**パラメータの意味:**

- `-ngl 99`: 全レイヤをGPUにオフロード
- `-c 8192`: コンテキスト長を8192トークンに制限（VRAM節約。後で拡大可能）
- `--jinja`: Jinjaテンプレートエンジンを有効化（Stage 2以降のtool callingに必須）
- `-fa on`: Flash Attention有効化（メモリ効率向上。`on`/`off`/`auto` から選択）

**起動時のログで確認すべき項目:**

- `Chat format:` の行 — `Hermes 2 Pro`、`Generic`、またはQwen固有のフォーマットが表示されるはず。`Generic` の場合はtool callingの信頼性が下がる可能性がある
- エラーなくモデルがロードされること
- KVキャッシュのサイズがVRAMに収まっていること

### 0-C. 基本的な疎通確認（Mac側）

```bash
# Mac側: curlで疎通確認
curl http://<GPGPU-WS-IP>:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "local",
    "messages": [{"role":"user","content":"1+1="}],
    "max_tokens": 32
  }'
```

### 0-D. レイテンシ計測スクリプト

```scala
//> using scala 3.6
//> using dep com.softwaremill.sttp.client4::core:4.0.19
//> using dep com.softwaremill.sttp.client4::circe:4.0.19
//> using dep io.circe::circe-generic:0.14.15
//> using dep io.circe::circe-parser:0.14.15

import sttp.client4.*
import sttp.client4.circe.*
import io.circe.*
import io.circe.generic.auto.*

// --- API型定義 ---
case class Message(role: String, content: String)
case class ChatRequest(
    model: String,
    messages: List[Message],
    max_tokens: Int,
    stream: Boolean = false
)
case class Usage(prompt_tokens: Int, completion_tokens: Int, total_tokens: Int)
case class Choice(message: Message)
case class ChatResponse(choices: List[Choice], usage: Option[Usage])

val BaseUrl = sys.env.getOrElse("LLM_BASE_URL", "http://192.168.1.100:8080")

def measureLatency(prompt: String, maxTokens: Int = 128): Unit = {
  val backend = DefaultSyncBackend()
  val req = ChatRequest("local", List(Message("user", prompt)), maxTokens)

  val t0 = System.nanoTime()
  val resp = basicRequest
    .post(uri"$BaseUrl/v1/chat/completions")
    .body(req)
    .response(asJson[ChatResponse])
    .send(backend)
  val elapsed = (System.nanoTime() - t0) / 1e9

  resp.body match {
    case Right(data) =>
      val u = data.usage.getOrElse(Usage(0, 0, 0))
      val tps = if (elapsed > 0) u.completion_tokens / elapsed else 0.0
      println(f"  prompt_tokens:     ${u.prompt_tokens}")
      println(f"  completion_tokens: ${u.completion_tokens}")
      println(f"  wall_time:         ${elapsed}%.3f s")
      println(f"  tokens/sec:        ${tps}%.1f")
      val text = data.choices.headOption.map(_.message.content.take(200)).getOrElse("(empty)")
      println(f"  response:          $text")
    case Left(err) =>
      println(s"  ERROR: $err")
  }
  backend.close()
}

@main def run(): Unit = {
  // ヘルスチェック
  val backend = DefaultSyncBackend()
  val health = basicRequest.get(uri"$BaseUrl/health").send(backend)
  println(s"Server health: ${health.body}")
  backend.close()

  println("\n--- Short response ---")
  measureLatency("1+1は？", maxTokens = 32)

  println("\n--- Medium response ---")
  measureLatency("日本国憲法の三大原則を簡潔に説明してください。", maxTokens = 256)

  println("\n--- Long input ---")
  val longInput = "以下の文章を要約してください。\n" + ("これはテスト文です。" * 200)
  measureLatency(longInput, maxTokens = 128)
}
```

```bash
# 実行
LLM_BASE_URL=http://<GPGPU-WS-IP>:8080 scala-cli run stage0_latency.scala
```

### 0-E. 確認すべき項目と合格基準

| 項目 | 合格基準 | 不合格時の対処 |
|------|---------|--------------|
| サーバ起動 | エラーなくモデルロード完了 | llama.cppバージョン確認。`qwen35moe` 未対応ならば更新 |
| curl応答 | JSON形式で応答が返る | `--host 0.0.0.0` の確認。ファイアウォール確認 |
| 生成速度 | 10 tokens/s 以上（MoE 3B activeなので高速なはず） | `-ngl 99` でGPUオフロード確認。`nvidia-smi` でVRAM使用率確認 |
| `/health` エンドポイント | `{"status":"ok"}` 相当の応答 | llama-serverのバージョン確認 |

### 0-F. Stage 0の成果物

- 動作するllama-serverの起動コマンド（再現可能な形で記録）
- レイテンシ計測結果のメモ（生成速度、TTFT、プロンプト処理速度）
- `Chat format:` ログの記録（Stage 2でのtool calling方式選択に使用）

---

## Stage 1: 構造化出力の制御

**獲得する概念:** LLMの出力をプログラムが解釈可能な形式に制約する技法。これがtool use（Stage 2以降）の基盤になる。

**所要時間:** 半日

### 1.1 設計原則: 「抽出」であって「生成」ではない

モデルの内部知識に依存する生成（例: 「尊属殺事件の争点を述べよ」）では、出力の正誤をground truthなしに検証できない。Stage 1では**入力テキストに正解が含まれるタスク**のみを扱う。こうすることで、構造化出力の設計改善のフィードバックループが回る。

### 1.2 題材: e-Gov法令XMLからの条文構造抽出

事前にe-Gov APIから法令XMLを取得し、ファイルとして保存しておく。推論時のネットワーク依存をゼロにする。

```bash
# Mac側: 民法の条文データを取得して保存
curl -s "https://laws.e-gov.go.jp/api/1/lawdata/129AC0000000089" > civil_code.xml
```

### 1.3 共通型定義と基盤コード

```scala
//> using scala 3.6
//> using dep com.softwaremill.sttp.client4::core:4.0.19
//> using dep com.softwaremill.sttp.client4::circe:4.0.19
//> using dep io.circe::circe-generic:0.14.15
//> using dep io.circe::circe-parser:0.14.15

import sttp.client4.*
import sttp.client4.circe.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.parser.decode

val BaseUrl = sys.env.getOrElse("LLM_BASE_URL", "http://192.168.1.100:8080")

// --- LLM API型 ---
case class Message(role: String, content: String)
case class ChatRequest(
    model: String,
    messages: List[Message],
    max_tokens: Int,
    temperature: Double = 0.0,
    response_format: Option[JsonObject] = None
)
case class Choice(message: Message)
case class ChatResponse(choices: List[Choice])

// --- 抽出結果型 ---
case class ParagraphInfo(paragraph_number: String, text: String)
case class ArticleInfo(
    article_number: String,
    caption: String,
    title: String,
    paragraphs: List[ParagraphInfo]
)

def chat(messages: List[Message],
         maxTokens: Int = 512,
         responseFormat: Option[JsonObject] = None): Either[String, String] = {
  val backend = DefaultSyncBackend()
  val req = ChatRequest("local", messages, maxTokens, 0.0, responseFormat)
  val resp = basicRequest
    .post(uri"$BaseUrl/v1/chat/completions")
    .body(req)
    .response(asJson[ChatResponse])
    .send(backend)
  backend.close()
  resp.body match {
    case Right(data) => Right(data.choices.head.message.content)
    case Left(err) => Left(err.toString)
  }
}
```

### 1.4 実験A: プロンプトによるJSON出力指示

```scala
val Article709Xml = """
<Article Num="709">
  <ArticleCaption>（不法行為による損害賠償）</ArticleCaption>
  <ArticleTitle>第七百九条</ArticleTitle>
  <Paragraph Num="1">
    <ParagraphNum/>
    <ParagraphSentence>
      <Sentence>故意又は過失によって他人の権利又は法律上保護される利益を侵害した者は、これによって生じた損害を賠償する責任を負う。</Sentence>
    </ParagraphSentence>
  </Paragraph>
</Article>
"""

val SystemPrompt = """|あなたは法令XMLから情報を抽出するアシスタントです。
  |与えられたXML断片から以下の情報をJSON形式で抽出してください。
  |JSONのみを出力し、それ以外のテキストは一切含めないでください。
  |
  |出力フォーマット:
  |{"article_number":"条番号","caption":"見出し","title":"条文タイトル",
  | "paragraphs":[{"paragraph_number":"項番号","text":"条文本文"}]}
  |""".stripMargin

def experimentA(): Unit = {
  println("=== Experiment A: Prompt-only JSON ===")
  var successCount = 0
  for (trial <- 1 to 5) {
    val result = chat(List(
      Message("system", SystemPrompt),
      Message("user", s"以下のXMLから情報を抽出してください:\n\n$Article709Xml")
    ))
    result match {
      case Right(text) =>
        decode[ArticleInfo](text) match {
          case Right(info) =>
            println(s"  Trial $trial: OK - article_number=${info.article_number}")
            successCount += 1
          case Left(err) =>
            println(s"  Trial $trial: PARSE FAIL - ${err.getMessage.take(80)}")
            println(s"    Raw: ${text.take(120)}")
        }
      case Left(err) =>
        println(s"  Trial $trial: API ERROR - $err")
    }
  }
  println(s"  Success rate: $successCount/5")
}
```

**このスクリプトを実行し、JSON解析の成功率を記録する。** プロンプトだけの場合、マークダウンのコードフェンス（`` ```json ... ``` ``）で囲んだり、前後に説明文を付けたりする失敗パターンが観察されるはずである。

### 1.5 実験B: JSON modeの利用

```scala
def experimentB(): Unit = {
  println("=== Experiment B: JSON mode ===")
  val jsonMode = JsonObject("type" -> Json.fromString("json_object"))
  var successCount = 0
  for (trial <- 1 to 5) {
    val result = chat(
      List(
        Message("system", SystemPrompt),
        Message("user", s"以下のXMLから情報を抽出してください:\n\n$Article709Xml")
      ),
      responseFormat = Some(jsonMode)
    )
    result match {
      case Right(text) =>
        decode[ArticleInfo](text) match {
          case Right(info) =>
            println(s"  Trial $trial: OK - article_number=${info.article_number}")
            successCount += 1
          case Left(err) =>
            println(s"  Trial $trial: PARSE FAIL (valid JSON but wrong shape?) - ${err.getMessage.take(80)}")
        }
      case Left(err) =>
        println(s"  Trial $trial: API ERROR - $err")
    }
  }
  println(s"  Success rate: $successCount/5")
}
```

### 1.6 実験C: JSON Schemaによる制約

llama-serverは `response_format` でJSON Schemaを指定できる。これにより出力トークンを文法レベルで制約する。

```scala
def experimentC(): Unit = {
  println("=== Experiment C: JSON Schema ===")
  val schema = JsonObject(
    "type" -> Json.fromString("json_schema"),
    "json_schema" -> Json.obj(
      "name" -> Json.fromString("article_extraction"),
      "strict" -> Json.fromBoolean(true),
      "schema" -> Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "article_number" -> Json.obj("type" -> Json.fromString("string")),
          "caption" -> Json.obj("type" -> Json.fromString("string")),
          "title" -> Json.obj("type" -> Json.fromString("string")),
          "paragraphs" -> Json.obj(
            "type" -> Json.fromString("array"),
            "items" -> Json.obj(
              "type" -> Json.fromString("object"),
              "properties" -> Json.obj(
                "paragraph_number" -> Json.obj("type" -> Json.fromString("string")),
                "text" -> Json.obj("type" -> Json.fromString("string"))
              ),
              "required" -> Json.arr(Json.fromString("paragraph_number"), Json.fromString("text"))
            )
          )
        ),
        "required" -> Json.arr(
          Json.fromString("article_number"), Json.fromString("caption"),
          Json.fromString("title"), Json.fromString("paragraphs")
        )
      )
    )
  )
  var successCount = 0
  for (trial <- 1 to 5) {
    val result = chat(
      List(
        Message("system", SystemPrompt),
        Message("user", s"以下のXMLから情報を抽出してください:\n\n$Article709Xml")
      ),
      responseFormat = Some(schema)
    )
    result match {
      case Right(text) =>
        decode[ArticleInfo](text) match {
          case Right(info) =>
            println(s"  Trial $trial: OK - article_number=${info.article_number}")
            successCount += 1
          case Left(err) =>
            println(s"  Trial $trial: PARSE FAIL - ${err.getMessage.take(80)}")
        }
      case Left(err) =>
        println(s"  Trial $trial: API ERROR - $err")
    }
  }
  println(s"  Success rate: $successCount/5")
}
```

### 1.7 評価方法

ground truthは入力XMLから機械的に導出できる。

```scala
val GroundTruth = ArticleInfo(
  article_number = "709",
  caption = "不法行為による損害賠償",
  title = "第七百九条",
  paragraphs = List(
    ParagraphInfo("1", "故意又は過失によって他人の権利又は法律上保護される利益を侵害した者は、これによって生じた損害を賠償する責任を負う。")
  )
)

def evaluate(result: ArticleInfo, truth: ArticleInfo): Map[String, Boolean] = {
  Map(
    "article_number" -> (result.article_number.trim == truth.article_number),
    "caption" -> (result.caption.trim == truth.caption),
    "title" -> (result.title.trim == truth.title),
    "paragraph_count" -> (result.paragraphs.size == truth.paragraphs.size),
    "first_para_text" -> result.paragraphs.headOption.zip(truth.paragraphs.headOption)
      .exists { case (r, t) => r.text.trim == t.text.trim }
  )
}
```

| 手法 | 予想される結果 |
|------|--------------|
| プロンプトのみ (A) | JSON解析成功率80-90%。コードフェンスや前置きテキストでパース失敗するケースあり |
| JSON mode (B) | JSON解析成功率100%。ただしスキーマに従わないフィールド名や構造のずれは起こりうる |
| JSON Schema (C) | JSON解析成功率100%、スキーマ準拠率100%。ただしレイテンシがA/Bより増加する可能性あり |

### 1.8 追加実験: 複数条文での信頼性検証

民法709条だけでなく、構造が異なる条文でも試す。

- **複数項がある条文**（例: 民法1条 — 3項構成）
- **号がある条文**（例: 民法5条2項 — 号構成。スキーマに「号」がないので、モデルがどう処理するかを観察）
- **見出しがない条文**（例: 附則の条文）

### 1.9 Stage 1の成果物

- 3手法の成功率比較表（5回×3手法×3条文 = 45回の実験結果）
- レイテンシ比較（手法間の差）
- 「JSON Schemaが最も信頼できるが、スキーマ設計の事前作業が必要」という（おそらく得られるであろう）知見の文書化

---

## Stage 2: 単一ツール呼び出し（ReActの最小形）

**獲得する概念:** LLMが「自分で答えるか、外部ツールを呼ぶか」を判断するループ。ReActパターンの核。

**所要時間:** 1〜2日（tool calling互換性のデバッグ時間を含む）

### 2.1 リスクと二重パス戦略

Stage 2はtool calling（function calling）の品質にLLMモデルとllama-serverの両方の対応状況が直結するため、最もトラブルが発生しやすいステージである。

- **パスA（推奨）**: llama-serverのOpenAI互換 `tools` パラメータを使用
- **パスB（フォールバック）**: tool callingをプロンプトエンジニアリングで模倣する

### 2.2 ツール実装: e-Gov法令検索

LLMから呼ばれる「ツール関数」をScalaで実装する。Mac側のクライアントコード内で実行される。

```scala
//> using scala 3.6
//> using dep com.softwaremill.sttp.client4::core:4.0.19
//> using dep org.scala-lang.modules::scala-xml:2.3.0

import sttp.client4.*
import scala.xml.*

object StatuteSearch {

  val EgovBase = "https://laws.e-gov.go.jp/api/1"

  val KnownLaws: Map[String, String] = Map(
    "民法" -> "129AC0000000089",
    "刑法" -> "140AC0000000045",
    "憲法" -> "321CONSTITUTION",
    "行政手続法" -> "405AC0000000088",
    "行政事件訴訟法" -> "337AC0000000139",
    "民事訴訟法" -> "408AC0000000109",
  )

  def searchStatute(lawName: String, articleNumber: String): String = {
    KnownLaws.get(lawName) match {
      case None =>
        s"エラー: '$lawName' は登録されていません。利用可能: ${KnownLaws.keys.mkString(", ")}"
      case Some(lawId) =>
        val backend = DefaultSyncBackend()
        val resp = basicRequest
          .get(uri"$EgovBase/lawdata/$lawId")
          .response(asString)
          .readTimeout(scala.concurrent.duration.Duration(30, "s"))
          .send(backend)
        backend.close()

        resp.body match {
          case Left(err) => s"エラー: API呼び出し失敗: $err"
          case Right(xmlStr) =>
            val root = XML.loadString(xmlStr)
            val articles = (root \\ "Article").filter(a => (a \ "@Num").text == articleNumber)
            articles.headOption match {
              case None => s"エラー: ${lawName}第${articleNumber}条が見つかりません。"
              case Some(article) =>
                val caption = (article \ "ArticleCaption").text.trim
                val title = (article \ "ArticleTitle").text.trim
                val sentences = (article \\ "Sentence").map(_.text.trim).filter(_.nonEmpty)
                (List(caption, title).filter(_.nonEmpty) ++ sentences).mkString("\n")
            }
        }
    }
  }
}
```

**ツール単体テスト:**

```scala
@main def testTool(): Unit = {
  println(StatuteSearch.searchStatute("民法", "709"))
  println("---")
  println(StatuteSearch.searchStatute("刑法", "199"))
  println("---")
  println(StatuteSearch.searchStatute("不存在", "1"))
}
```

このテストを実行し、正しく条文が取得できることを確認してからLLMとの統合に進む。

### 2.3 パスA: OpenAI互換 tool calling

```scala
//> using scala 3.6
//> using dep com.softwaremill.sttp.client4::core:4.0.19
//> using dep com.softwaremill.sttp.client4::circe:4.0.19
//> using dep io.circe::circe-generic:0.14.15
//> using dep io.circe::circe-parser:0.14.15
//> using dep org.scala-lang.modules::scala-xml:2.3.0

import sttp.client4.*
import sttp.client4.circe.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.parser.{decode, parse}

val BaseUrl = sys.env.getOrElse("LLM_BASE_URL", "http://192.168.1.100:8080")
val MaxToolRounds = 5

// --- ツール定義（OpenAI形式のJSONとして） ---
val ToolDefs: Json = Json.arr(
  Json.obj(
    "type" -> Json.fromString("function"),
    "function" -> Json.obj(
      "name" -> Json.fromString("search_statute"),
      "description" -> Json.fromString("日本の法令の条文を検索して取得する。法令名と条番号を指定する。"),
      "parameters" -> Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "law_name" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("法令名。例: 民法, 刑法, 憲法")
          ),
          "article_number" -> Json.obj(
            "type" -> Json.fromString("string"),
            "description" -> Json.fromString("条番号。数字のみ。例: 709, 199, 9")
          )
        ),
        "required" -> Json.arr(Json.fromString("law_name"), Json.fromString("article_number"))
      )
    )
  )
)

// --- ツールディスパッチ ---
def dispatchTool(name: String, args: JsonObject): String = {
  name match {
    case "search_statute" =>
      val lawName = args("law_name").flatMap(_.asString).getOrElse("")
      val articleNum = args("article_number").flatMap(_.asString).getOrElse("")
      StatuteSearch.searchStatute(lawName, articleNum)
    case other =>
      s"エラー: 未知のツール '$other'"
  }
}

// --- エージェントループ ---
def runAgent(userQuery: String): String = {
  // メッセージ履歴をJson配列として構築（tool_callsフィールドを含むため、case classでは表現しにくい）
  var messages = List(
    Json.obj("role" -> Json.fromString("system"), "content" -> Json.fromString(
      "あなたは日本法の条文検索ができるアシスタントです。" +
      "ユーザの質問に答えるために、必要に応じてsearch_statuteツールを使って条文を検索してください。" +
      "ツールが不要な質問にはツールを使わずに直接回答してください。"
    )),
    Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(userQuery))
  )

  for (round <- 0 until MaxToolRounds) {
    val body = Json.obj(
      "model" -> Json.fromString("local"),
      "messages" -> Json.arr(messages*),
      "tools" -> ToolDefs,
      "max_tokens" -> Json.fromInt(1024),
      "temperature" -> Json.fromDoubleOrNull(0.0)
    )

    val backend = DefaultSyncBackend()
    val resp = basicRequest
      .post(uri"$BaseUrl/v1/chat/completions")
      .contentType("application/json")
      .body(body.noSpaces)
      .response(asString)
      .readTimeout(scala.concurrent.duration.Duration(120, "s"))
      .send(backend)
    backend.close()

    val respJson = parse(resp.body.getOrElse("{}")).getOrElse(Json.Null)
    val choiceMsg = respJson.hcursor
      .downField("choices").downArray.downField("message").focus.getOrElse(Json.Null)

    val toolCalls = choiceMsg.hcursor.downField("tool_calls").as[List[Json]].getOrElse(Nil)

    if (toolCalls.isEmpty) {
      // ツール呼び出しなし → 最終回答
      return choiceMsg.hcursor.downField("content").as[String].getOrElse("(empty)")
    }

    // assistantメッセージを履歴に追加
    messages = messages :+ choiceMsg

    // 各ツール呼び出しを実行
    for (tc <- toolCalls) {
      val c = tc.hcursor
      val fnName = c.downField("function").downField("name").as[String].getOrElse("")
      val fnArgs = c.downField("function").downField("arguments").as[String].getOrElse("{}")
      val argsObj = parse(fnArgs).flatMap(_.as[JsonObject]).getOrElse(JsonObject.empty)
      val tcId = c.downField("id").as[String].getOrElse("")

      println(s"  [Tool call #$round] $fnName($fnArgs)")
      val result = dispatchTool(fnName, argsObj)

      messages = messages :+ Json.obj(
        "role" -> Json.fromString("tool"),
        "tool_call_id" -> Json.fromString(tcId),
        "content" -> Json.fromString(result)
      )
    }
  }
  "(MAX_TOOL_ROUNDS exceeded)"
}

@main def testAgent(): Unit = {
  println("=== Test 1: ツール必要 ===")
  println(runAgent("民法709条の条文を教えてください。"))

  println("\n=== Test 2: ツール不要 ===")
  println(runAgent("今日の天気はどうですか？"))

  println("\n=== Test 3: 間接的にツール必要 ===")
  println(runAgent("不法行為の成立要件は何条に書いてありますか？条文も示してください。"))
}
```

**型設計の注記:** OpenAI互換APIのメッセージには `tool_calls` という通常のテキストメッセージにはないフィールドが含まれる。ここでは `Json` を直接操作しているが、これは意図的な設計判断である。Stage 3以降でメッセージ型をADTとして整理する際の材料になる。

### 2.4 パスB: プロンプトベースのtool calling（フォールバック）

パスAが動作しない場合のフォールバック。`tools` パラメータを使わず、system promptにツール定義をテキストで埋め込む。

```scala
val PromptSystemMsg = """|あなたは日本法の条文検索ができるアシスタントです。
  |
  |## 利用可能なツール
  |### search_statute
  |日本の法令の条文を検索して取得する。
  |パラメータ:
  |- law_name (string, 必須): 法令名。例: 民法, 刑法, 憲法
  |- article_number (string, 必須): 条番号。数字のみ。例: 709, 199, 9
  |
  |## ツールの呼び出し方
  |ツールを使いたい場合は、以下のJSON形式のみを出力してください:
  |{"tool_call":{"name":"search_statute","arguments":{"law_name":"...","article_number":"..."}}}
  |
  |ツールが不要な場合は、通常のテキストで回答してください。
  |重要: ツールを呼ぶ場合はJSON以外のテキストを含めないでください。
  |""".stripMargin

def parseToolCall(content: String): Option[(String, JsonObject)] = {
  parse(content.trim).toOption
    .flatMap(_.hcursor.downField("tool_call").focus)
    .flatMap { tc =>
      for {
        name <- tc.hcursor.downField("name").as[String].toOption
        args <- tc.hcursor.downField("arguments").as[JsonObject].toOption
      } yield (name, args)
    }
}

def runAgentPromptBased(userQuery: String): String = {
  var messages = List(
    Json.obj("role" -> Json.fromString("system"), "content" -> Json.fromString(PromptSystemMsg)),
    Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(userQuery))
  )

  for (round <- 0 until MaxToolRounds) {
    val body = Json.obj(
      "model" -> Json.fromString("local"),
      "messages" -> Json.arr(messages*),
      "max_tokens" -> Json.fromInt(1024),
      "temperature" -> Json.fromDoubleOrNull(0.0)
    )
    val backend = DefaultSyncBackend()
    val resp = basicRequest
      .post(uri"$BaseUrl/v1/chat/completions")
      .contentType("application/json")
      .body(body.noSpaces)
      .response(asString)
      .readTimeout(scala.concurrent.duration.Duration(120, "s"))
      .send(backend)
    backend.close()

    val respJson = parse(resp.body.getOrElse("{}")).getOrElse(Json.Null)
    val content = respJson.hcursor
      .downField("choices").downArray.downField("message").downField("content")
      .as[String].getOrElse("")

    parseToolCall(content) match {
      case None =>
        return content // 最終回答
      case Some((fnName, args)) =>
        println(s"  [Tool call #$round] $fnName(${args.asJson.noSpaces})")
        val result = dispatchTool(fnName, args)
        messages = messages ++ List(
          Json.obj("role" -> Json.fromString("assistant"), "content" -> Json.fromString(content)),
          Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(
            s"ツールの実行結果:\n$result\n\nこの結果を踏まえて回答してください。"
          ))
        )
    }
  }
  "(MAX_TOOL_ROUNDS exceeded)"
}
```

### 2.5 検証と観察ポイント

| # | 質問 | 期待される動作 |
|---|------|--------------|
| 1 | 「民法709条の条文を教えてください」 | ツール呼び出し→条文取得→提示 |
| 2 | 「今日の天気は？」 | ツールを呼ばずに直接回答 |
| 3 | 「不法行為の損害賠償請求の根拠条文は？」 | ツール呼び出し（709条）→取得→説明 |
| 4 | 「刑法の殺人罪は何条ですか？条文も示してください」 | ツール呼び出し（199条）→取得→提示 |
| 5 | 「民法1条と709条の両方を見せてください」 | 2回のツール呼び出し |

### 2.6 Stage 2の成果物

- パスA/Bのどちらが安定動作したかの記録
- 5問のテスト結果
- tool callingで遭遇した問題とワークアラウンドの記録

---

## Stage 3: 複数ツール＋ルーティング

**獲得する概念:** ツール選択の判断品質。ツールdescriptionの書き方がルーティング精度を支配すること。

**所要時間:** 1日

### 3.1 ツールの追加

Stage 2の `search_statute` に加えて、以下のツールを追加する。

```scala
//> using dep org.scala-lang.modules::scala-xml:2.3.0

object LawListSearch {
  import sttp.client4.*
  import scala.xml.*

  private var cache: Option[Seq[(String, String, String)]] = None // (name, id, number)

  private def loadLawList(): Seq[(String, String, String)] = {
    cache.getOrElse {
      val backend = DefaultSyncBackend()
      val resp = basicRequest
        .get(uri"https://laws.e-gov.go.jp/api/1/lawlists/2")
        .response(asString)
        .readTimeout(scala.concurrent.duration.Duration(60, "s"))
        .send(backend)
      backend.close()
      val root = XML.loadString(resp.body.getOrElse("<DataRoot/>"))
      val laws = (root \\ "LawNameListInfo").map { info =>
        ((info \ "LawName").text, (info \ "LawId").text, (info \ "LawNo").text)
      }
      cache = Some(laws)
      laws
    }
  }

  def findLawByKeyword(keyword: String): String = {
    val matches = loadLawList().filter(_._1.contains(keyword)).take(10)
    if (matches.isEmpty) {
      s"'$keyword' を含む法令は見つかりませんでした。"
    } else {
      val lines = matches.map { case (name, _, number) => s"- $name（$number）" }
      s"'$keyword' を含む法令 (${matches.size}件):\n${lines.mkString("\n")}"
    }
  }
}

object Arithmetic {
  def calculate(expression: String): String = {
    // 安全な四則演算のみ。javax.script.ScriptEngineは使わない。
    try {
      // 簡易パーサ: 数値と+-*/のみ許可
      val sanitized = expression.replaceAll("[^0-9.+\\-*/() ]", "")
      if (sanitized != expression.trim) {
        return s"エラー: 許可されていない文字が含まれています"
      }
      // scala.util.Tryでの簡易eval（注: 本番コードではパーサを書くべき）
      val result = new javax.script.ScriptEngineManager().getEngineByName("js") match {
        case null => "エラー: 計算エンジンが利用できません"
        case engine => engine.eval(sanitized).toString
      }
      result
    } catch {
      case e: Exception => s"計算エラー: ${e.getMessage}"
    }
  }
}
```

### 3.2 ルーティング精度の比較実験

3つのツールのdescriptionをtools配列に含め、LLMに適切なツールを選択させる。同じ質問セットに対して、descriptionを2パターン用意して正答率を比較する。

| # | 質問 | 正しいツール |
|---|------|------------|
| 1 | 「個人情報に関する法律にはどんなものがありますか？」 | findLawByKeyword |
| 2 | 「民法709条の条文を見せてください」 | searchStatute |
| 3 | 「研究費140万円の30%はいくらですか？」 | calculate |
| 4 | 「消費者契約法の正式名称は？」 | findLawByKeyword |
| 5 | 「刑法199条を教えてください」 | searchStatute |
| 6 | 「50000 + 150000 + 400000 の合計は？」 | calculate |

### 3.3 メッセージ型のADT化（設計改善）

Stage 2ではメッセージを `Json` で直接操作していた。ここで型安全なADTに整理する。

```scala
enum ChatMessage {
  case System(content: String)
  case User(content: String)
  case Assistant(content: Option[String], toolCalls: List[ToolCallInfo])
  case ToolResult(toolCallId: String, content: String)
}

case class ToolCallInfo(id: String, name: String, arguments: JsonObject)

// ChatMessage → Json の変換
def messageToJson(msg: ChatMessage): Json = msg match {
  case ChatMessage.System(c) =>
    Json.obj("role" -> Json.fromString("system"), "content" -> Json.fromString(c))
  case ChatMessage.User(c) =>
    Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(c))
  case ChatMessage.Assistant(content, toolCalls) if toolCalls.nonEmpty =>
    Json.obj(
      "role" -> Json.fromString("assistant"),
      "content" -> content.map(Json.fromString).getOrElse(Json.Null),
      "tool_calls" -> Json.arr(toolCalls.map { tc =>
        Json.obj(
          "id" -> Json.fromString(tc.id),
          "type" -> Json.fromString("function"),
          "function" -> Json.obj(
            "name" -> Json.fromString(tc.name),
            "arguments" -> Json.fromString(tc.arguments.asJson.noSpaces)
          )
        )
      }*)
    )
  case ChatMessage.Assistant(content, _) =>
    Json.obj("role" -> Json.fromString("assistant"),
             "content" -> Json.fromString(content.getOrElse("")))
  case ChatMessage.ToolResult(id, c) =>
    Json.obj("role" -> Json.fromString("tool"),
             "tool_call_id" -> Json.fromString(id),
             "content" -> Json.fromString(c))
}
```

この型定義により、Pythonの `dict[str, Any]` で発生する「`tool_calls` が `None` なのか空リストなのか」「`content` が `null` なのか空文字列なのか」といった曖昧さがコンパイル時に排除される。

### 3.4 Stage 3の成果物

- descriptionパターン比較の正答率表
- ルーティング失敗パターンの分析
- ADT化されたメッセージ型（以降のStageで再利用）

---

## Stage 4: 状態管理と会話履歴

**獲得する概念:** agentic loopにおける状態管理の設計パターン。コンテキストウィンドウの有限性がアーキテクチャを規定すること。

**所要時間:** 1〜2日

### 4.1 セッション永続化

```scala
import java.nio.file.{Files, Path, Paths}

class ConversationState(sessionId: String) {
  private val dir = Paths.get("sessions")
  private val path = dir.resolve(s"$sessionId.json")
  private var _messages: List[ChatMessage] = {
    if (Files.exists(path)) {
      // JSONファイルから復元（circe decodeを使用）
      val text = Files.readString(path)
      // ここでは簡易的にJson配列として読み込み、ChatMessageに変換する
      // 実装はStage 3のADTに対するDecoder定義が必要
      Nil // TODO: 実装
    } else {
      Nil
    }
  }

  def messages: List[ChatMessage] = _messages

  def add(msg: ChatMessage): Unit = {
    _messages = _messages :+ msg
  }

  def save(): Unit = {
    Files.createDirectories(dir)
    val jsonArr = Json.arr(_messages.map(messageToJson)*)
    Files.writeString(path, jsonArr.spaces2)
  }

  def estimateTokens: Int = {
    // 粗い推定: 日本語1文字 ≈ 1トークン
    _messages.map(messageToJson).map(_.noSpaces.length).sum
  }

  def truncateIfNeeded(maxTokens: Int = 6000): Unit = {
    while (estimateTokens > maxTokens && _messages.size > 2) {
      val removed = _messages(1) // systemとlatest user以外の古いものから削除
      _messages = _messages.head :: _messages.drop(2)
      println(s"  [Truncated] ${removed.getClass.getSimpleName}")
    }
  }
}
```

### 4.2 直面する問題の体験

10ターン程度の対話を行い、以下を観察する:

- `estimateTokens` の推移
- ツール呼び出し結果（条文テキスト等）によるトークン数の急増
- `-c 8192` のコンテキスト制限に近づいたときのllama-serverの挙動
- `truncateIfNeeded` による古いメッセージ削除の対話品質への影響

### 4.3 研究との接続

会話履歴の「要約時に何が失われるか」は、基盤Cの「矮小化」概念と構造的に相似している。

### 4.4 Stage 4の成果物

- セッション保存・復元が動作する対話エージェント
- コンテキスト使用量の推移ログ
- 切り詰め方式のトレードオフ検討メモ

---

## Stage 5: 計画と分解（Planning）

**獲得する概念:** 複雑なタスクをサブタスクに分解し、実行計画を立ててから遂行するパターン。

**所要時間:** 1〜2日

### 5.1 題材設計の原則

**利用可能なツール（Stage 3で実装済み）の組み合わせだけで完結するタスク**でなければならない。

```
良い題材:
  「個人情報に関する法律を探し、その法律の第1条の条文を取得してください」
  → findLawByKeyword("個人情報") → searchStatute で第1条を取得

悪い題材:
  「民法709条の要件を列挙し、各要件について判例を1件ずつ検索せよ」
  → 判例検索ツールが存在しない。検証不能。
```

### 5.2 Plan-then-Execute パターン

```scala
case class PlanStep(id: Int, tool: String, args: Map[String, String], purpose: String)
case class Plan(steps: List[PlanStep])

val PlanningPrompt = """|与えられたタスクを、利用可能なツールだけで遂行できるステップに分解してください。
  |
  |利用可能なツール:
  |1. search_statute(law_name, article_number) — 法令の条文を取得
  |2. find_law_by_keyword(keyword) — キーワードで法令名を検索
  |3. calculate(expression) — 四則演算
  |
  |JSON形式で出力:
  |{"steps":[{"id":1,"tool":"ツール名","args":{"引数名":"値"},"purpose":"目的"}]}
  |""".stripMargin
```

### 5.3 Adaptive Planningとの比較

```
タスク: 「消費者に関する法律を3つ探し、それぞれの第1条の条文を取得して、
          目的規定を比較してください。」
```

- **Plan-then-Execute**: findLawByKeywordの結果（法令名）が事前に分からないため、第2ステップ以降の引数を事前確定できない
- **Adaptive**: findLawByKeywordの結果を見てから、次のsearchStatute呼び出しを決定する

**この比較を通じて「計画の静的確定の限界」を体感する。**

### 5.4 Stage 5の成果物

- Plan-then-Execute / Adaptive の実装
- 比較実験の結果

---

## Stage 6: 自己評価と修正ループ

**獲得する概念:** LLM出力の品質をプログラム的に評価し、不十分な場合に再生成するパターン。

**所要時間:** 1日

### 6.1 制約: 同一モデルによる自己評価

generator（回答生成）とevaluator（品質判定）に同じモデルを使う場合、自分の出力を「正しい」と判定しがちである（self-consistency bias）。

**緩和策:**

- evaluatorのsystem promptをgeneratorと大きく変える
- 評価基準を機械検証可能な項目に限定する（「条文番号が応答中に含まれているか」等）
- temperatureをevaluator側で上げて多数決をとる

### 6.2 構造化された評価

```scala
case class Evaluation(
    source_cited: Boolean,
    internally_consistent: Boolean,
    answers_question: Boolean,
    issues: List[String]
)

val EvaluatorPrompt = """|あなたは法律情報の品質検証者です。
  |以下の質問と回答のペアを検証し、JSONで評価結果を返してください。
  |
  |評価基準:
  |1. source_cited: 回答中に具体的な条文番号が引用されているか (true/false)
  |2. internally_consistent: 回答内で矛盾する記述がないか (true/false)
  |3. answers_question: 元の質問に対する直接的な回答が含まれているか (true/false)
  |4. issues: 問題点があれば列挙 (配列。なければ空配列)
  |
  |JSONのみ出力:
  |{"source_cited":true,"internally_consistent":true,"answers_question":true,"issues":[]}
  |""".stripMargin
```

### 6.3 修正ループ

```scala
def runWithSelfEval(query: String, maxRetries: Int = 3): String = {
  var currentQuery = query
  for (attempt <- 0 until maxRetries) {
    val answer = runAgent(currentQuery)

    // 機械検証: 条文番号パターンの存在確認
    val hasCitation = "第[一二三四五六七八九十百千\\d]+条".r.findFirstIn(answer).isDefined

    // LLM評価
    val evalResult = chat(
      List(
        ChatMessage.System(EvaluatorPrompt),
        ChatMessage.User(s"質問: $query\n\n回答: $answer")
      ).map(messageToJson).collect { case j => Message(
        j.hcursor.downField("role").as[String].getOrElse(""),
        j.hcursor.downField("content").as[String].getOrElse("")
      )},
      maxTokens = 256,
      responseFormat = Some(JsonObject("type" -> Json.fromString("json_object")))
    )

    evalResult.flatMap(decode[Evaluation](_).toOption) match {
      case Right(eval) if eval.source_cited && eval.answers_question =>
        println(s"  [Eval #$attempt] PASS (machine_citation=$hasCitation)")
        return answer
      case Right(eval) =>
        println(s"  [Eval #$attempt] FAIL: ${eval.issues.mkString("; ")}")
        currentQuery = s"$query\n\n前回の回答の問題点: ${eval.issues.mkString("; ")}\n修正して再回答してください。"
      case _ =>
        println(s"  [Eval #$attempt] eval parse failed, accepting answer")
        return answer
    }
  }
  runAgent(currentQuery) // 最終試行
}
```

### 6.4 Stage 6の成果物

- 自己評価ループの実装
- 機械検証可能項目とLLM判定の一致率
- self-consistency biasの観察記録

---

## Stage 7（応用）: 研究タスクへの接続

Stage 0〜6で獲得した概念を組み合わせて、実際の研究タスクに取り組む段階。

### 7.1 接続可能な研究タスク

| タスク | 主に使うStageの概念 | 追加で必要なもの |
|--------|-------------------|----------------|
| **C-ε: Agentic Legal Research** | Stage 2-5（ツール、計画） | 判例DB検索ツール、より高品質なモデル |
| **D-δ: 法的三段論法のCoT検証** | Stage 6（評価ループ） | 形式検証（Dung's framework）との接続 |
| **E-β: 判例要約の矮小化評価** | Stage 4（何が失われるか） | 判例テキストデータ、評価指標の設計 |

### 7.2 sttp-openaiへの移行検討

Stage 7以降では、自前のJSON組み立てから `sttp-openai` (`com.softwaremill.sttp.openai::core:0.3.6`) への移行を検討してもよい。同ライブラリはOpenAI互換エンドポイント（llama-server含む）に対応しており、tool calling、structured output、streamingを型安全に扱える。Stage 0-6で内部構造を理解しているので、ライブラリの抽象化が何を隠しているかを把握した上で使える。

### 7.3 sbtプロジェクトへの移行

Stage 7以降でコードが複数ファイルに成長した場合、scala-cliの単一ファイル実行から sbt プロジェクトに移行する。scala-cliの `export` コマンドでsbtプロジェクトを生成できる。

```bash
scala-cli export --sbt stage3_routing.scala
```

---

## 付録A: トラブルシューティング

### A.1 llama-serverが起動しない

| 症状 | 原因 | 対処 |
|------|------|------|
| `unknown model architecture: 'qwen35moe'` | llama.cppが古い | b8149以降に更新 |
| `CUDA out of memory` | VRAMが不足 | `-c` を小さくする（4096等）。`-ngl` を減らしてCPUオフロード |
| `error loading model` | GGUFファイルが破損/非対応 | `sha256sum` でチェックサム検証 |

### A.2 tool callingが動作しない

| 症状 | 原因 | 対処 |
|------|------|------|
| `tool_calls` が応答に含まれない | `--jinja` フラグ未指定 | 起動コマンドに `--jinja` を追加 |
| `Unknown filter 'items'` エラー | Jinjaテンプレートのバグ | コミュニティ修正版テンプレートを `--chat-template-file` で指定 |
| ツールを呼ぶべき質問でテキスト回答が返る | Genericフォーマットの制限 | パスB（プロンプトベース）にフォールバック |
| 引数のJSONが壊れる | モデルの出力品質 | 公式instruct版GGUFに切り替え。temperature=0.0を確認 |
| HTTP 500: `type must be string, but is null` | リクエスト JSON の `content` が `null` | `Message.content` を `Option[String]` ではなく `String` にする。リクエスト用とレスポンス用で型を分離するのが安全 |
| `content` が空で `reasoning_content` のみ返る | Qwen3.5 の thinking mode がデフォルト有効 | `max_tokens` を十分大きくする。または thinking を無効化（`/no_think` タグ等） |

### A.3 scala-cli関連

| 症状 | 原因 | 対処 |
|------|------|------|
| 初回実行が遅い（数十秒） | 依存のダウンロード＋コンパイル | 正常。2回目以降はキャッシュされて高速 |
| circeのderivedインスタンスが見つからない | `circe-generic` の `import io.circe.generic.auto.*` 漏れ | importを追加 |
| Scala 3.8系でコンパイルエラー | ライブラリのバイナリ互換性 | `//> using scala 3.6` に固定 |

### A.4 e-Gov APIのエラー

| 症状 | 原因 | 対処 |
|------|------|------|
| タイムアウト | APIの一時的な遅延 | `readTimeout` を60秒に延長。リトライ追加 |
| XML解析エラー | レスポンスがエラーXML | `<Code>` 要素を確認。`0` 以外はエラー |

---

## 付録B: ステージ間の依存関係と省略可能性

```
Stage 0 (疎通・基盤)
  └→ Stage 1 (構造化出力)
       └→ Stage 2 (単一ツール)
            └→ Stage 3 (複数ツール + ADT化)
            |    └→ Stage 5 (計画)
            └→ Stage 4 (状態管理)
                 └→ Stage 6 (自己評価)
```

- Stage 0, 1, 2 は省略不可。基盤であり、後続のすべてに影響する。
- Stage 3 は Stage 2 の拡張。メッセージ型のADT化はここで行う。
- Stage 4 と Stage 5 は独立。どちらを先に取り組んでもよい。
- Stage 6 は Stage 2-5 のいずれかのエージェントが動作していることが前提。

---

## 付録C: 所要時間の目安

| Stage | 推定時間 | 備考 |
|-------|---------|------|
| 0 | 2-4時間 | llama.cppの更新が必要な場合を含む |
| 1 | 半日 | JSON mode/Schema の挙動確認込み |
| 2 | 1-2日 | tool calling互換性のデバッグ時間を含む |
| 3 | 1日 | ツール追加 + description最適化 + ADT化 |
| 4 | 1-2日 | 状態管理の設計判断が入るため |
| 5 | 1-2日 | planning戦略の比較実験 |
| 6 | 1日 | evaluatorの設計 |

合計で1-2週間。各ステージの間に振り返りの時間を取ることを推奨する。

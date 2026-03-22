# ローカルLLM推論によるAgentic System段階的構築ガイド

> 作成日: 2026-03-21
>
> 本ドキュメントは、GPU WS上のローカルLLM推論APIを前提に、Mac側でagentic systemを段階的に構築するための自己完結的な学習ガイドである。各ステージで1つのアーキテクチャ概念を獲得し、最終的に法学ドメイン向けシステムの設計判断ができるようになることを目標とする。

---

## 0. 前提条件と技術的制約

### 0.1 ハードウェア構成

| 役割 | 機器 | 関連仕様 |
|------|------|----------|
| **推論サーバ** | GPGPU WS (RTX PRO 4500 Blackwell, 32GB GDDR7) | Ubuntu 24.04, CUDA 13.2, llama.cpp ビルド済み |
| **クライアント** | Mac mini M4 Pro (24GB) / MacBook Air M4 (24GB) | macOS, Python 3.x + uv |
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

### 0.4 言語選択

本ガイドではStage 0〜3を **Python（フレームワークなし）** で実装する。依存は `httpx`（HTTP クライアント）と `pydantic`（構造化データ）のみ。

**根拠**: LLMエージェントの内部構造を理解することが目的であり、LangChain等のフレームワークの抽象化に隠れる概念を自分の手で実装する方が学習効果が高い。Stage 4以降でScala 3への移植を検討する余地を残す。

```bash
# Mac側: プロジェクト初期化
mkdir -p ~/workspace/agentic-learning && cd ~/workspace/agentic-learning
uv init
uv add httpx pydantic
```

### 0.5 e-Gov法令API

本ガイドのツール実装ではe-Gov法令APIを使用する。認証不要・無料で利用できる公開APIである。

| 版 | ベースURL | データ形式 | 状況 |
|----|-----------|-----------|------|
| V1 | `https://laws.e-gov.go.jp/api/1/` | XML | 安定稼働中 |
| V2 | `https://laws.e-gov.go.jp/api/2/` | JSON (XML も可) | 2025年3月〜運用開始 |

本ガイドではV1（XML形式）を使用する。V2はJSON対応で新しいが、V1の方が実績が多くドキュメントも充実しているため、学習用途ではV1から始める方が問題の切り分けが容易である。

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
# GPU WS側: まずtool calling なしで基本動作を確認
./build/bin/llama-server \
  -m /data/models/llm-models/Qwen3.5-35B-A3B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf \
  --host 0.0.0.0 --port 8080 \
  -ngl 99 \
  -c 8192 \
  --jinja \
  -fa
```

**パラメータの意味:**

- `-ngl 99`: 全レイヤをGPUにオフロード
- `-c 8192`: コンテキスト長を8192トークンに制限（VRAM節約。後で拡大可能）
- `--jinja`: Jinjaテンプレートエンジンを有効化（Stage 2以降のtool callingに必須）
- `-fa`: Flash Attention有効化（メモリ効率向上）

**起動時のログで確認すべき項目:**

- `Chat format:` の行 — `Hermes 2 Pro`、`Generic`、またはQwen固有のフォーマットが表示されるはず。`Generic` の場合はtool callingの信頼性が下がる可能性がある
- エラーなくモデルがロードされること
- KVキャッシュのサイズがVRAMに収まっていること

### 0-C. 基本的な疎通確認（Mac側）

```bash
# Mac側: 非ストリーミング
curl http://<GPGPU-WS-IP>:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "local",
    "messages": [{"role":"user","content":"1+1="}],
    "max_tokens": 32
  }'
```

```bash
# Mac側: ストリーミング（TTFT体感用）
curl http://<GPGPU-WS-IP>:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "local",
    "messages": [{"role":"user","content":"日本の首都はどこですか？"}],
    "max_tokens": 64,
    "stream": true
  }'
```

### 0-D. レイテンシ計測スクリプト

```python
#!/usr/bin/env python3
"""Stage 0-D: 推論レイテンシの基本計測"""
import httpx
import time
import json

BASE_URL = "http://<GPGPU-WS-IP>:8080"  # ← 実際のIPに書き換え

def measure_latency(prompt: str, max_tokens: int = 128) -> dict:
    payload = {
        "model": "local",
        "messages": [{"role": "user", "content": prompt}],
        "max_tokens": max_tokens,
        "stream": False,
    }
    t0 = time.perf_counter()
    resp = httpx.post(f"{BASE_URL}/v1/chat/completions", json=payload, timeout=120)
    t1 = time.perf_counter()
    data = resp.json()

    usage = data.get("usage", {})
    prompt_tokens = usage.get("prompt_tokens", 0)
    completion_tokens = usage.get("completion_tokens", 0)
    wall_time = t1 - t0

    return {
        "prompt_tokens": prompt_tokens,
        "completion_tokens": completion_tokens,
        "wall_time_s": round(wall_time, 3),
        "tokens_per_sec": round(completion_tokens / wall_time, 1) if wall_time > 0 else 0,
        "response_text": data["choices"][0]["message"]["content"][:200],
    }

if __name__ == "__main__":
    # ヘルスチェック
    health = httpx.get(f"{BASE_URL}/health")
    print(f"Server health: {health.json()}")

    # 短い応答
    print("\n--- Short response ---")
    r = measure_latency("1+1は？", max_tokens=32)
    print(json.dumps(r, ensure_ascii=False, indent=2))

    # 中程度の応答
    print("\n--- Medium response ---")
    r = measure_latency("日本国憲法の三大原則を簡潔に説明してください。", max_tokens=256)
    print(json.dumps(r, ensure_ascii=False, indent=2))

    # 長い入力（プロンプト処理速度の計測）
    long_input = "以下の文章を要約してください。\n" + "これはテスト文です。" * 200
    print("\n--- Long input ---")
    r = measure_latency(long_input, max_tokens=128)
    print(json.dumps(r, ensure_ascii=False, indent=2))
```

### 0-E. 確認すべき項目と合格基準

| 項目 | 合格基準 | 不合格時の対処 |
|------|---------|--------------|
| サーバ起動 | エラーなくモデルロード完了 | llama.cppバージョン確認。`qwen35moe` 未対応ならば更新 |
| curl応答 | JSON形式で応答が返る | `--host 0.0.0.0` の確認。ファイアウォール確認 |
| 生成速度 | 10 tokens/s 以上（MoE 3B activeなので高速なはず） | `-ngl 99` でGPUオフロード確認。`nvidia-smi` でVRAM使用率確認 |
| `/health` エンドポイント | `{"status":"ok"}` | llama-serverのバージョン確認 |

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

このXMLから特定の条文を切り出してプロンプトに含め、構造化抽出させる。

### 1.3 実験A: プロンプトによるJSON出力指示

```python
#!/usr/bin/env python3
"""Stage 1-A: プロンプトのみでJSON出力を指示"""
import httpx
import json

BASE_URL = "http://<GPGPU-WS-IP>:8080"

# 民法709条のXML断片（civil_code.xmlから手動で切り出す）
ARTICLE_709_XML = """
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

SYSTEM_PROMPT = """\
あなたは法令XMLから情報を抽出するアシスタントです。
与えられたXML断片から以下の情報をJSON形式で抽出してください。
JSONのみを出力し、それ以外のテキストは一切含めないでください。

出力フォーマット:
{
  "article_number": "条番号（数字）",
  "caption": "見出し",
  "title": "条文タイトル",
  "paragraphs": [
    {
      "paragraph_number": "項番号（数字）",
      "text": "条文本文"
    }
  ]
}
"""

def extract_with_prompt_only(xml_text: str) -> dict:
    resp = httpx.post(
        f"{BASE_URL}/v1/chat/completions",
        json={
            "model": "local",
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": f"以下のXMLから情報を抽出してください:\n\n{xml_text}"},
            ],
            "max_tokens": 512,
            "temperature": 0.0,
        },
        timeout=60,
    )
    content = resp.json()["choices"][0]["message"]["content"]
    print(f"Raw output:\n{content}\n")
    try:
        return json.loads(content)
    except json.JSONDecodeError as e:
        print(f"JSON parse error: {e}")
        return None

if __name__ == "__main__":
    result = extract_with_prompt_only(ARTICLE_709_XML)
    if result:
        print("Parsed JSON:")
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print("FAILED: Output was not valid JSON")
```

**このスクリプトを5回実行し、JSON解析の成功率を記録する。** プロンプトだけの場合、マークダウンのコードフェンス（` ```json ... ``` `）で囲んだり、前後に説明文を付けたりする失敗パターンが観察されるはずである。

### 1.4 実験B: JSON modeの利用

```python
def extract_with_json_mode(xml_text: str) -> dict:
    resp = httpx.post(
        f"{BASE_URL}/v1/chat/completions",
        json={
            "model": "local",
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": f"以下のXMLから情報を抽出してください:\n\n{xml_text}"},
            ],
            "max_tokens": 512,
            "temperature": 0.0,
            "response_format": {"type": "json_object"},  # ← これが変更点
        },
        timeout=60,
    )
    content = resp.json()["choices"][0]["message"]["content"]
    return json.loads(content)  # JSON modeなら必ずパース可能なはず
```

### 1.5 実験C: JSON Schemaによる制約

llama-serverは `response_format` でJSON Schemaを指定できる。これにより出力トークンを文法レベルで制約する。

```python
ARTICLE_SCHEMA = {
    "type": "json_schema",
    "json_schema": {
        "name": "article_extraction",
        "strict": True,
        "schema": {
            "type": "object",
            "properties": {
                "article_number": {"type": "string"},
                "caption": {"type": "string"},
                "title": {"type": "string"},
                "paragraphs": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "paragraph_number": {"type": "string"},
                            "text": {"type": "string"},
                        },
                        "required": ["paragraph_number", "text"],
                    },
                },
            },
            "required": ["article_number", "caption", "title", "paragraphs"],
        },
    },
}

def extract_with_schema(xml_text: str) -> dict:
    resp = httpx.post(
        f"{BASE_URL}/v1/chat/completions",
        json={
            "model": "local",
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": f"以下のXMLから情報を抽出してください:\n\n{xml_text}"},
            ],
            "max_tokens": 512,
            "temperature": 0.0,
            "response_format": ARTICLE_SCHEMA,
        },
        timeout=60,
    )
    content = resp.json()["choices"][0]["message"]["content"]
    return json.loads(content)
```

### 1.6 評価方法

ground truthは入力XMLから機械的に抽出できる。以下の比較を行う。

```python
GROUND_TRUTH = {
    "article_number": "709",
    "caption": "不法行為による損害賠償",
    "title": "第七百九条",
    "paragraphs": [
        {
            "paragraph_number": "1",
            "text": "故意又は過失によって他人の権利又は法律上保護される利益を侵害した者は、これによって生じた損害を賠償する責任を負う。",
        }
    ],
}

def evaluate(result: dict, truth: dict) -> dict:
    """フィールドごとの完全一致率を計算"""
    scores = {}
    for key in truth:
        if key == "paragraphs":
            # 項の数と各項のtextが一致するかを検証
            r_paras = result.get("paragraphs", [])
            t_paras = truth["paragraphs"]
            scores["paragraph_count_match"] = len(r_paras) == len(t_paras)
            if r_paras and t_paras:
                scores["first_paragraph_text_match"] = (
                    r_paras[0].get("text", "").strip() == t_paras[0]["text"].strip()
                )
        else:
            scores[f"{key}_match"] = str(result.get(key, "")).strip() == str(truth[key]).strip()
    return scores
```

| 手法 | 予想される結果 |
|------|--------------|
| プロンプトのみ (A) | JSON解析成功率80-90%。コードフェンスや前置きテキストでパース失敗するケースあり |
| JSON mode (B) | JSON解析成功率100%。ただしスキーマに従わないフィールド名や構造のずれは起こりうる |
| JSON Schema (C) | JSON解析成功率100%、スキーマ準拠率100%。ただしレイテンシがA/Bより増加する可能性あり |

### 1.7 追加実験: 複数条文での信頼性検証

民法709条だけでなく、構造が異なる条文でも試す。

- **複数項がある条文**（例: 民法1条 — 3項構成）
- **号がある条文**（例: 民法5条2項 — 号構成。スキーマに「号」がないので、モデルがどう処理するかを観察）
- **見出しがない条文**（例: 附則の条文）

これにより、スキーマ設計の柔軟性と抽出の堅牢性を体感する。

### 1.8 Stage 1の成果物

- 3手法の成功率比較表（5回×3手法×3条文 = 45回の実験結果）
- レイテンシ比較（手法間の差）
- 「JSON Schemaが最も信頼できるが、スキーマ設計の事前作業が必要」という（おそらく得られるであろう）知見の文書化

---

## Stage 2: 単一ツール呼び出し（ReActの最小形）

**獲得する概念:** LLMが「自分で答えるか、外部ツールを呼ぶか」を判断するループ。ReActパターンの核。

**所要時間:** 1〜2日（tool calling互換性のデバッグ時間を含む）

### 2.1 Stage 2のリスクと対策

Stage 2はtool calling（function calling）の品質にLLMモデルとllama-serverの両方の対応状況が直結するため、最もトラブルが発生しやすいステージである。

**リスク1: llama-serverのtool calling非対応/不安定**

対策として、2つのパスを用意する。

- **パスA（推奨）**: llama-serverのOpenAI互換 `tools` パラメータを使用。Stage 0のログで `Chat format:` が `Hermes 2 Pro` 等であればこちらが使える可能性が高い。
- **パスB（フォールバック）**: tool callingをプロンプトエンジニアリングで模倣する。`tools` パラメータは使わず、system promptにツール定義をテキストで埋め込み、モデルに特定のJSON形式で「ツールを呼びたい」と出力させる。Stage 1のJSON Schema制約と組み合わせると信頼性が上がる。

**リスク2: "Uncensored" バリアントのtool calling品質**

§0.3で述べた通り、Uncensored版はtool callingの学習分布からずれている可能性がある。パスAで不安定な場合、まずは公式GGUFに切り替えて問題がモデル由来かサーバ由来かを切り分ける。

### 2.2 ツール実装: e-Gov法令検索

まず、LLMから呼ばれる「ツール関数」をPythonで実装する。これはMac側のクライアントコード内で実行される。

```python
"""tools/statute_search.py — e-Gov法令API V1を使った条文検索ツール"""
import httpx
import xml.etree.ElementTree as ET

EGOV_BASE = "https://laws.e-gov.go.jp/api/1"

# 主要法令のIDマッピング（事前に用意）
KNOWN_LAWS = {
    "民法": "129AC0000000089",
    "刑法": "140AC0000000045",
    "憲法": "321CONSTITUTION",
    "行政手続法": "405AC0000000088",
    "行政事件訴訟法": "337AC0000000139",
    "民事訴訟法": "408AC0000000109",
}

def search_statute(law_name: str, article_number: str) -> str:
    """
    指定された法令の指定された条文を取得する。

    Args:
        law_name: 法令名（例: "民法", "刑法"）
        article_number: 条番号（例: "709", "199"）

    Returns:
        条文テキスト。見つからない場合はエラーメッセージ。
    """
    law_id = KNOWN_LAWS.get(law_name)
    if not law_id:
        return f"エラー: '{law_name}' は登録されていません。利用可能: {', '.join(KNOWN_LAWS.keys())}"

    try:
        resp = httpx.get(f"{EGOV_BASE}/lawdata/{law_id}", timeout=30)
        resp.raise_for_status()
    except httpx.HTTPError as e:
        return f"エラー: API呼び出し失敗: {e}"

    root = ET.fromstring(resp.content)

    # Article要素を探索
    for article in root.iter("Article"):
        if article.get("Num") == article_number:
            # 条文テキストを組み立て
            parts = []
            caption_el = article.find("ArticleCaption")
            if caption_el is not None and caption_el.text:
                parts.append(caption_el.text)
            title_el = article.find("ArticleTitle")
            if title_el is not None and title_el.text:
                parts.append(title_el.text)
            for para in article.iter("Paragraph"):
                for sent in para.iter("Sentence"):
                    if sent.text:
                        parts.append(sent.text)
            if parts:
                return "\n".join(parts)

    return f"エラー: {law_name}第{article_number}条が見つかりません。"
```

**検証（ツール単体テスト）:**

```python
if __name__ == "__main__":
    print(search_statute("民法", "709"))
    print("---")
    print(search_statute("刑法", "199"))
    print("---")
    print(search_statute("不存在", "1"))  # エラーケース
```

このツール単体テストを実行し、正しく条文が取得できることを確認してからLLMとの統合に進む。

### 2.3 パスA: OpenAI互換 tool calling

```python
"""stage2_tool_calling.py — パスA: OpenAI互換tool calling"""
import httpx
import json
from tools.statute_search import search_statute

BASE_URL = "http://<GPGPU-WS-IP>:8080"
MAX_TOOL_ROUNDS = 5  # 無限ループ防止

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "search_statute",
            "description": "日本の法令の条文を検索して取得する。法令名と条番号を指定する。",
            "parameters": {
                "type": "object",
                "properties": {
                    "law_name": {
                        "type": "string",
                        "description": "法令名。例: 民法, 刑法, 憲法",
                    },
                    "article_number": {
                        "type": "string",
                        "description": "条番号。数字のみ。例: 709, 199, 9",
                    },
                },
                "required": ["law_name", "article_number"],
            },
        },
    }
]

TOOL_DISPATCH = {
    "search_statute": search_statute,
}

def run_agent(user_query: str) -> str:
    messages = [
        {
            "role": "system",
            "content": (
                "あなたは日本法の条文検索ができるアシスタントです。"
                "ユーザの質問に答えるために、必要に応じてsearch_statuteツールを使って条文を検索してください。"
                "ツールが不要な質問にはツールを使わずに直接回答してください。"
            ),
        },
        {"role": "user", "content": user_query},
    ]

    for round_num in range(MAX_TOOL_ROUNDS):
        resp = httpx.post(
            f"{BASE_URL}/v1/chat/completions",
            json={
                "model": "local",
                "messages": messages,
                "tools": TOOLS,
                "max_tokens": 1024,
                "temperature": 0.0,
            },
            timeout=120,
        )
        data = resp.json()
        choice = data["choices"][0]
        msg = choice["message"]

        # ツール呼び出しがない場合 → 最終回答
        if not msg.get("tool_calls"):
            return msg.get("content", "(empty response)")

        # ツール呼び出しを実行
        messages.append(msg)  # assistantのtool_callメッセージを履歴に追加
        for tc in msg["tool_calls"]:
            fn_name = tc["function"]["name"]
            fn_args = json.loads(tc["function"]["arguments"])
            print(f"  [Tool call #{round_num}] {fn_name}({fn_args})")

            if fn_name in TOOL_DISPATCH:
                result = TOOL_DISPATCH[fn_name](**fn_args)
            else:
                result = f"エラー: 未知のツール '{fn_name}'"

            messages.append({
                "role": "tool",
                "tool_call_id": tc["id"],
                "content": result,
            })

    return "(MAX_TOOL_ROUNDS exceeded)"


if __name__ == "__main__":
    # テスト1: ツールが必要な質問
    print("=== Test 1: ツール必要 ===")
    print(run_agent("民法709条の条文を教えてください。"))

    print("\n=== Test 2: ツール不要 ===")
    print(run_agent("今日の天気はどうですか？"))

    print("\n=== Test 3: ツール必要（間接的） ===")
    print(run_agent("不法行為の成立要件は何条に書いてありますか？条文も示してください。"))
```

### 2.4 パスB: プロンプトベースのtool calling（フォールバック）

パスAが動作しない場合のフォールバック。`tools` パラメータを使わず、system promptにツール定義を埋め込む。

```python
"""stage2_prompt_tool_calling.py — パスB: プロンプトベースのtool calling"""
import httpx
import json
import re
from tools.statute_search import search_statute

BASE_URL = "http://<GPGPU-WS-IP>:8080"

SYSTEM_PROMPT = """\
あなたは日本法の条文検索ができるアシスタントです。

## 利用可能なツール

### search_statute
日本の法令の条文を検索して取得する。
パラメータ:
- law_name (string, 必須): 法令名。例: 民法, 刑法, 憲法
- article_number (string, 必須): 条番号。数字のみ。例: 709, 199, 9

## ツールの呼び出し方

ツールを使いたい場合は、以下のJSON形式のみを出力してください:
{"tool_call": {"name": "search_statute", "arguments": {"law_name": "...", "article_number": "..."}}}

ツールが不要な場合は、通常のテキストで回答してください。

重要: ツールを呼ぶ場合はJSON以外のテキストを含めないでください。
"""

def parse_tool_call(content: str) -> dict | None:
    """応答からtool callのJSONを抽出する試み"""
    # まず全体がJSONかどうか
    try:
        parsed = json.loads(content.strip())
        if "tool_call" in parsed:
            return parsed["tool_call"]
    except json.JSONDecodeError:
        pass

    # JSON部分を正規表現で抽出
    match = re.search(r'\{[^{}]*"tool_call"[^{}]*\{[^{}]*\}[^{}]*\}', content)
    if match:
        try:
            parsed = json.loads(match.group())
            return parsed.get("tool_call")
        except json.JSONDecodeError:
            pass

    return None

def run_agent_prompt_based(user_query: str) -> str:
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": user_query},
    ]

    for round_num in range(5):
        resp = httpx.post(
            f"{BASE_URL}/v1/chat/completions",
            json={
                "model": "local",
                "messages": messages,
                "max_tokens": 1024,
                "temperature": 0.0,
            },
            timeout=120,
        )
        content = resp.json()["choices"][0]["message"]["content"]
        tool_call = parse_tool_call(content)

        if tool_call is None:
            # ツール呼び出しではない → 最終回答
            return content

        fn_name = tool_call["name"]
        fn_args = tool_call["arguments"]
        print(f"  [Tool call #{round_num}] {fn_name}({fn_args})")

        result = search_statute(**fn_args)

        messages.append({"role": "assistant", "content": content})
        messages.append({
            "role": "user",
            "content": f"ツールの実行結果:\n{result}\n\nこの結果を踏まえて回答してください。",
        })

    return "(MAX_TOOL_ROUNDS exceeded)"
```

### 2.5 検証と観察ポイント

以下の質問セットで両パスを比較する。

| # | 質問 | 期待される動作 |
|---|------|--------------|
| 1 | 「民法709条の条文を教えてください」 | ツール呼び出し→条文取得→提示 |
| 2 | 「今日の天気は？」 | ツールを呼ばずに直接回答（「分かりません」等） |
| 3 | 「不法行為の損害賠償請求の根拠条文は？」 | ツール呼び出し（709条）→取得→説明 |
| 4 | 「刑法の殺人罪は何条ですか？条文も示してください」 | ツール呼び出し（199条）→取得→提示 |
| 5 | 「民法1条と709条の両方を見せてください」 | 2回のツール呼び出し（1条、709条） |

**観察すべき項目:**

- ツールを呼ぶべき質問で呼べているか（再現率）
- ツールを呼ぶ必要がない質問で呼んでいないか（適合率）
- ツール呼び出しのJSONフォーマットが壊れる頻度
- 質問#5のような複数ツール呼び出しへの対応
- ツール結果を踏まえた最終回答の品質

### 2.6 Stage 2の成果物

- パスA/Bのどちらが安定動作したかの記録
- 5問のテスト結果（ツール呼び出しの正誤、最終回答の品質）
- tool callingで遭遇した問題とワークアラウンドの記録
- 「ツールを呼ぶ/呼ばないの判断はsystem promptの書き方に強く依存する」という（おそらく得られるであろう）知見

---

## Stage 3: 複数ツール＋ルーティング

**獲得する概念:** ツール選択の判断品質。ツールdescriptionの書き方がルーティング精度を支配すること。

**所要時間:** 1日

### 3.1 ツールの追加

Stage 2の `search_statute` に加えて、以下のツールを追加する。いずれもground truthが明確で検証可能なものを選ぶ。

```python
"""tools/law_list.py — 法令一覧からの法令名検索"""
import httpx
import xml.etree.ElementTree as ET

EGOV_BASE = "https://laws.e-gov.go.jp/api/1"

# キャッシュ: 一度取得した法令一覧はメモリに保持
_law_list_cache: list[dict] | None = None

def _load_law_list() -> list[dict]:
    global _law_list_cache
    if _law_list_cache is not None:
        return _law_list_cache
    resp = httpx.get(f"{EGOV_BASE}/lawlists/2", timeout=60)  # 憲法・法律のみ
    root = ET.fromstring(resp.content)
    laws = []
    for info in root.iter("LawNameListInfo"):
        name_el = info.find("LawName")
        id_el = info.find("LawId")
        no_el = info.find("LawNo")
        if name_el is not None and id_el is not None:
            laws.append({
                "name": name_el.text or "",
                "id": id_el.text or "",
                "number": no_el.text if no_el is not None else "",
            })
    _law_list_cache = laws
    return laws

def find_law_by_keyword(keyword: str) -> str:
    """
    法令名にキーワードを含む法令を検索する。

    Args:
        keyword: 検索キーワード（例: "個人情報", "消費者"）

    Returns:
        マッチした法令名と法令番号のリスト（最大10件）。
    """
    laws = _load_law_list()
    matches = [law for law in laws if keyword in law["name"]][:10]
    if not matches:
        return f"'{keyword}' を含む法令は見つかりませんでした。"
    lines = [f"- {m['name']}（{m['number']}）" for m in matches]
    return f"'{keyword}' を含む法令 ({len(matches)}件):\n" + "\n".join(lines)
```

```python
"""tools/arithmetic.py — 簡易計算ツール（検証用）"""
import ast
import operator

# 安全な演算子のみ許可
_SAFE_OPS = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
}

def calculate(expression: str) -> str:
    """
    数式を計算して結果を返す。四則演算のみ対応。

    Args:
        expression: 計算式（例: "100 * 0.3", "1500 + 2000"）

    Returns:
        計算結果。
    """
    try:
        tree = ast.parse(expression, mode="eval")
        result = _eval_node(tree.body)
        return str(result)
    except Exception as e:
        return f"計算エラー: {e}"

def _eval_node(node):
    if isinstance(node, ast.Constant):
        return node.value
    elif isinstance(node, ast.BinOp):
        op_func = _SAFE_OPS.get(type(node.op))
        if op_func is None:
            raise ValueError(f"未対応の演算子: {type(node.op).__name__}")
        return op_func(_eval_node(node.left), _eval_node(node.right))
    elif isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.USub):
        return -_eval_node(node.operand)
    else:
        raise ValueError(f"未対応の式: {type(node).__name__}")
```

### 3.2 ルーティングの設計

3つのツールのdescriptionをtools配列に含め、LLMに適切なツールを選択させる。

**実験: descriptionの粒度による精度変化**

同じ質問セットに対して、descriptionを2パターン用意して正答率を比較する。

- **パターン1（簡潔）**: `"日本の法令の条文を検索する"`, `"法令名を検索する"`, `"計算する"`
- **パターン2（詳細）**: Stage 2-3のコード例に示した詳細なdescription

| # | 質問 | 正しいツール |
|---|------|------------|
| 1 | 「個人情報に関する法律にはどんなものがありますか？」 | find_law_by_keyword |
| 2 | 「民法709条の条文を見せてください」 | search_statute |
| 3 | 「研究費140万円の30%はいくらですか？」 | calculate |
| 4 | 「消費者契約法の正式名称は？」 | find_law_by_keyword |
| 5 | 「刑法199条を教えてください」 | search_statute |
| 6 | 「3つの数字の合計を教えてください: 50000, 150000, 400000」 | calculate |

### 3.3 MAX_TOOL_ROUNDSの設計

ツールが増えると、LLMが不必要にツールを連続呼び出しする（ループ）リスクが高まる。`MAX_TOOL_ROUNDS` を3に制限し、超過した場合はその旨をユーザに報告する。

### 3.4 Stage 3の成果物

- descriptionパターン1 vs 2の正答率比較表
- ルーティング失敗パターンの分析（どのような質問でどのツールに誤ルーティングされるか）
- 「descriptionの書き方がルーティング精度を支配する」という知見の具体的な裏付け

---

## Stage 4: 状態管理と会話履歴

**獲得する概念:** agentic loopにおける状態管理の設計パターン。コンテキストウィンドウの有限性がアーキテクチャを規定すること。

**所要時間:** 1〜2日

### 4.1 複数ターン対話の実装

Stage 2-3のエージェントは1つの質問に1回答するステートレスな構造だった。これを複数ターンの対話に拡張する。

```python
"""stage4_stateful_agent.py — 会話履歴を保持するエージェント"""
import json
import pathlib

HISTORY_DIR = pathlib.Path("./sessions")
HISTORY_DIR.mkdir(exist_ok=True)

class ConversationState:
    def __init__(self, session_id: str):
        self.session_id = session_id
        self.messages: list[dict] = []
        self.path = HISTORY_DIR / f"{session_id}.json"
        if self.path.exists():
            self.messages = json.loads(self.path.read_text())

    def add(self, message: dict):
        self.messages.append(message)

    def save(self):
        self.path.write_text(json.dumps(self.messages, ensure_ascii=False, indent=2))

    def token_estimate(self) -> int:
        """粗い推定: 1文字 ≈ 1トークン（日本語の場合）"""
        total_chars = sum(len(json.dumps(m, ensure_ascii=False)) for m in self.messages)
        return total_chars

    def truncate_if_needed(self, max_tokens: int = 6000):
        """コンテキスト制限を超える場合、古いメッセージを削除"""
        while self.token_estimate() > max_tokens and len(self.messages) > 2:
            # system promptと最新のuser messageは保持
            # それ以外の古いものから削除
            removed = self.messages.pop(1)
            print(f"  [Truncated] role={removed.get('role')}, len={len(json.dumps(removed))}")
```

### 4.2 直面する問題の体験

**実験:** 以下のシナリオを実際に試す。

1. 10ターン程度の対話を行い、`token_estimate()` の推移を観察する
2. ツール呼び出し結果（条文テキスト等）がトークン数を急増させる様子を観察する
3. `-c 8192` のコンテキスト制限に近づいたときにllama-serverがどう振る舞うか確認する（エラーになるのか、自動的に切り詰められるのか）
4. `truncate_if_needed` による古いメッセージの削除が対話品質にどう影響するか

### 4.3 より高度な状態管理（設計の検討のみ）

実装は任意だが、設計を検討しておくべきパターン:

- **要約ベースの圧縮**: 古いメッセージを削除する代わりに、LLMに要約させて短いメッセージに置き換える。要約自体にLLM呼び出しが必要なのでレイテンシが増える。
- **検索ベースのメモリ**: 全会話履歴をベクトルDBに保存し、関連するものだけを取り出す（RAG的アプローチ）。実装コストが高い。
- **タスク結果のみ保持**: ツール呼び出しの結果全文ではなく、そこから抽出した要約のみを保持する。Stage 1の構造化抽出が基盤になる。

### 4.4 研究との接続

会話履歴の「要約時に何が失われるか」は、基盤Cの「矮小化」概念と構造的に相似している。Stage 4の実験結果は、E行タスク（判例要約の矮小化評価）の実験設計に直接転用できる。

### 4.5 Stage 4の成果物

- セッション保存・復元が動作する対話エージェント
- コンテキスト使用量の推移ログ（10ターン分）
- 切り詰め方式の比較検討メモ（削除 vs 要約 vs 検索、それぞれのトレードオフ）

---

## Stage 5: 計画と分解（Planning）

**獲得する概念:** 複雑なタスクをサブタスクに分解し、実行計画を立ててから遂行するパターン。

**所要時間:** 1〜2日

### 5.1 題材の設計

Stage 5の題材は、**利用可能なツール（Stage 3で実装済み）の組み合わせだけで完結するタスク**でなければならない。

```
良い題材:
  「個人情報に関する法律を探し、その法律の第1条の条文を取得してください」
  → find_law_by_keyword("個人情報") → search_statute で第1条を取得
  → ツール2回の組み合わせで完結。正解検証可能。

悪い題材:
  「民法709条の要件を列挙し、各要件について判例を1件ずつ検索せよ」
  → 判例検索ツールが存在しない。モデルの内部知識に依存する部分が生じ、検証不能。
```

### 5.2 Plan-then-Execute パターン

```python
"""stage5_planning.py — Plan-then-Execute"""

PLANNING_PROMPT = """\
あなたはタスクを計画するアシスタントです。
与えられたタスクを、利用可能なツールだけで遂行できるステップに分解してください。

利用可能なツール:
1. search_statute(law_name, article_number) — 法令の条文を取得
2. find_law_by_keyword(keyword) — キーワードで法令名を検索
3. calculate(expression) — 四則演算

以下のJSON形式で計画を出力してください:
{
  "steps": [
    {"id": 1, "tool": "ツール名", "args": {"引数名": "値"}, "purpose": "このステップの目的"},
    ...
  ]
}

注意:
- 利用可能なツールだけを使ってください。ツールで実現できない操作はステップに含めないでください。
- 各ステップは前のステップの結果を参照できます。ただし、引数の値は事前に確定できるもののみ記載してください。
  前のステップの結果に依存する引数は "depends_on_step_1_result" のように記載してください。
"""

def generate_plan(task: str) -> dict:
    resp = httpx.post(
        f"{BASE_URL}/v1/chat/completions",
        json={
            "model": "local",
            "messages": [
                {"role": "system", "content": PLANNING_PROMPT},
                {"role": "user", "content": task},
            ],
            "max_tokens": 1024,
            "temperature": 0.0,
            "response_format": {"type": "json_object"},
        },
        timeout=120,
    )
    return json.loads(resp.json()["choices"][0]["message"]["content"])
```

### 5.3 Adaptive Planningとの比較

Plan-then-Executeでは計画を事前に固定するが、Adaptive Planningでは各ステップの結果を見て次のステップを動的に決定する。

**比較実験:** 以下のタスクで両方のパターンを試し、結果を比較する。

```
タスク: 「消費者に関する法律を3つ探し、それぞれの第1条の条文を取得して、
          目的規定（法律の趣旨）を比較してください。」
```

- Plan-then-Execute: 最初にfind_law_by_keywordで法令リストを取得し、上位3件のsearch_statuteを計画する。ただし、find_law_by_keywordの結果（法令名）が事前に分からないため、計画の第2ステップ以降の引数を事前確定できない。
- Adaptive: find_law_by_keywordの結果を見てから、次のsearch_statue呼び出しを決定する。

**この比較を通じて「計画の静的確定の限界」を体感する。**

### 5.4 Stage 5の成果物

- Plan-then-Execute / Adaptive の実装（最小限）
- 比較実験の結果（どちらがどのようなタスクに適するか）
- 「事前に引数を確定できないステップがある場合、静的計画は機能しない」という知見の記録

---

## Stage 6: 自己評価と修正ループ

**獲得する概念:** LLM出力の品質をプログラム的に評価し、不十分な場合に再生成するパターン。

**所要時間:** 1日

### 6.1 既知の制約: 同一モデルによる自己評価

generator（回答生成）とevaluator（品質判定）に同じモデルを使う場合、自分の出力を「正しい」と判定しがちである（self-consistency bias）。この制約は本ステージでは解消できないが、認識した上で実験する。

**可能な緩和策（本ステージの範囲内）:**

- evaluatorのsystem promptをgeneratorと大きく変える（異なる観点からの評価を促す）
- 評価基準を具体的・検証可能な項目に限定する（「条文番号が応答中に含まれているか」「ground truthのテキストと一致するか」等）
- temperatureをevaluator側で上げて多様な判定を生成し、多数決をとる

### 6.2 構造化された評価基準

```python
EVALUATOR_PROMPT = """\
あなたは法律情報の品質検証者です。
以下の質問と回答のペアを検証し、JSONで評価結果を返してください。

評価基準:
1. source_cited: 回答中に具体的な条文番号が引用されているか (true/false)
2. internally_consistent: 回答内で矛盾する記述がないか (true/false)
3. answers_question: 元の質問に対する直接的な回答が含まれているか (true/false)
4. issues: 問題点があれば具体的に列挙 (配列。なければ空配列)

出力:
{"source_cited": true/false, "internally_consistent": true/false, "answers_question": true/false, "issues": [...]}
"""
```

評価基準のうち `source_cited` はプログラムで機械的に検証可能である（条文番号の正規表現マッチ）。これをevaluator LLMの判定と照合することで、evaluator自体の精度も計測できる。

### 6.3 修正ループの実装

```python
def run_with_self_eval(query: str, max_retries: int = 3) -> str:
    for attempt in range(max_retries):
        answer = run_agent(query)  # Stage 2-3のエージェント

        evaluation = evaluate_answer(query, answer)
        print(f"  [Eval attempt {attempt}] {json.dumps(evaluation, ensure_ascii=False)}")

        all_pass = (
            evaluation.get("source_cited", False)
            and evaluation.get("internally_consistent", False)
            and evaluation.get("answers_question", False)
        )
        if all_pass:
            return answer

        # 不合格: 問題点を添えて再生成
        issues = evaluation.get("issues", [])
        feedback = "前回の回答には以下の問題がありました:\n" + "\n".join(f"- {i}" for i in issues)
        query = f"{query}\n\n{feedback}\nこれらの問題を修正して再回答してください。"

    return f"(max_retries exceeded. Last answer: {answer[:200]})"
```

### 6.4 観察ポイント

- 再生成で品質は実際に向上するか？（向上しない場合、evaluatorの基準が問題か、generatorの能力が問題か）
- 再生成のコスト（レイテンシが何倍になるか）
- evaluatorが false negative を出す頻度（正しい回答を不合格にする）
- evaluatorが false positive を出す頻度（問題のある回答を合格にする。self-consistency biasの現れ）

### 6.5 Stage 6の成果物

- 自己評価ループの実装
- 評価基準のうち機械検証可能な項目（source_cited等）とLLM判定の一致率
- 「同一モデルによる自己評価の限界」の具体的な観察記録

---

## Stage 7（応用）: 研究タスクへの接続

Stage 0〜6で獲得した概念を組み合わせて、実際の研究タスクに取り組む段階。ここからは学習カリキュラムではなく研究開発である。

### 7.1 接続可能な研究タスク

| タスク | 主に使うStageの概念 | 追加で必要なもの |
|--------|-------------------|----------------|
| **C-ε: Agentic Legal Research** | Stage 2-5（ツール、計画） | 判例DB検索ツール、より高品質なモデル |
| **D-δ: 法的三段論法のCoT検証** | Stage 6（評価ループ） | 形式検証（Dung's framework）との接続 |
| **E-β: 判例要約の矮小化評価** | Stage 4（何が失われるか） | 判例テキストデータ、評価指標の設計 |
| **A-δ: 議論構造認識** | Stage 1（構造化抽出） | 山田氏データセット、アノテーション設計 |

### 7.2 モデル選択の再評価

Stage 0-6の経験を踏まえて、研究本番ではモデルを再検討する。

- **Qwen3.5-35B-A3B（公式instruct版）**: tool calling品質が最も安定するはず。学習目的のUncensored版から切り替えを推奨
- **Qwen3-Swallow 8B/32B**: 日本語に特化した蒸留モデル。QLoRA FTのベースモデル候補（タスク適合性分析 §3.1 Phase 3）
- **70Bモデル（玄界HPC）**: 合成データ生成用。ローカルでは動かさない

---

## 付録A: トラブルシューティング

### A.1 llama-serverが起動しない

| 症状 | 原因 | 対処 |
|------|------|------|
| `unknown model architecture: 'qwen35moe'` | llama.cppが古い | b8149以降に更新 |
| `CUDA out of memory` | VRAMが不足 | `-c` を小さくする（4096等）。`-ngl` を減らしてCPUオフロード |
| `error loading model` | GGUFファイルが破損/非対応 | `sha256sum` でチェックサム検証。Unsloth版GGUFの使用を検討 |

### A.2 tool callingが動作しない

| 症状 | 原因 | 対処 |
|------|------|------|
| `tool_calls` が応答に含まれない | `--jinja` フラグ未指定 | 起動コマンドに `--jinja` を追加 |
| `Unknown filter 'items'` エラー | Jinjaテンプレートのバグ | コミュニティ修正版テンプレートを `--chat-template-file` で指定 |
| ツールを呼ぶべき質問でテキスト回答が返る | Genericフォーマットの制限 | パスB（プロンプトベース）にフォールバック |
| 引数のJSONが壊れる | モデルの出力品質 | 公式instruct版GGUFに切り替え。temperature=0.0を確認 |

### A.3 e-Gov APIのエラー

| 症状 | 原因 | 対処 |
|------|------|------|
| タイムアウト | APIの一時的な遅延 | タイムアウトを60秒に延長。リトライ追加 |
| XML解析エラー | レスポンスがエラーXML | `<Code>` 要素を確認。`0` 以外はエラー |
| 条文が見つからない | 法令IDまたは条番号の不一致 | `KNOWN_LAWS` マッピングを確認。V1 APIの法令ID形式に注意 |

---

## 付録B: ステージ間の依存関係と省略可能性

```
Stage 0 (疎通・基盤)
  └→ Stage 1 (構造化出力)
       └→ Stage 2 (単一ツール)
            └→ Stage 3 (複数ツール)
            |    └→ Stage 5 (計画)
            └→ Stage 4 (状態管理)
                 └→ Stage 6 (自己評価)
```

- Stage 0, 1, 2 は省略不可。基盤であり、後続のすべてに影響する。
- Stage 3 は Stage 2 の拡張。Stage 2 で十分な知見が得られれば軽く流してもよい。
- Stage 4 と Stage 5 は独立。どちらを先に取り組んでもよい。
- Stage 6 は Stage 2-5 のいずれかのエージェントが動作していることが前提。

---

## 付録C: Scala 3への移植を検討する場合

Stage 4以降でScala 3に移植する場合、以下が最小限の構成要素となる。

- **HTTP クライアント**: `sttp` (Scala HTTP client) または `http4s` client
- **JSON処理**: `circe` (encode/decode) + `circe-generic` (case class自動導出)
- **非同期**: `cats-effect` IO monad（tool呼び出しの並行実行に有用）
- **ビルド**: sbt + Scala 3.x

Pythonで得た設計知見（ツール定義のdescription設計、コンテキスト管理戦略、評価基準の設計）はそのまま移植可能。LLM APIとのHTTP通信部分のみ書き換えれば済む。

LangChain等のPythonフレームワークに依存していないため、移植のロックインは発生しない。これがフレームワークなしで始めた最大の利点である。

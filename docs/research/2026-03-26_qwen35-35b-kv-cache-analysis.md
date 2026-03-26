# Qwen3.5-35B-A3B KV キャッシュ・インフラ分析

> 実施日: 2026-03-26
> 環境: GPU WS (RTX PRO 4500 Blackwell 32GB), llama.cpp build 8462
> モデル: Qwen3.5-35B-A3B-UD-Q4_K_XL.gguf (Unsloth, 20.70 GiB, 5.13 BPW)

## 1. 発見: Hybrid Attention-SSM アーキテクチャ

Qwen3.5-35B-A3B は純粋な Transformer ではなく、**Attention + SSM（Mamba 系）のハイブリッド**。

| パラメータ | 値 | 意味 |
|---|---|---|
| `n_layer` | 40 | 全レイヤー数 |
| `full_attention_interval` | 4 | 4層に1層が full attention |
| Attention 層 | 10 / 40 | KV キャッシュを使う層 |
| SSM 層 | 30 / 40 | 固定サイズ recurrent state（コンテキスト長非依存） |

**KV キャッシュが軽い理由**: Attention 層が全体の 1/4 しかないため。（実機確認）

## 2. KV キャッシュ実測

| `-c` | VRAM 使用量 | KV キャッシュ |
|------|------------|-------------|
| 16384 | 22056 MiB | — |
| 32768 | 22376 MiB | +320 MiB |
| 262144 | 27168 MiB | 5120 MiB（llama.cpp ログ） |

**1 token あたり ~20 KB**（= 5120 MiB / 262144 tokens）。（実機確認）

KV キャッシュ内訳（llama.cpp ログ）:
- K (f16): 2560 MiB
- V (f16): 2560 MiB
- 対象: 10 layers, 4 parallel sequences

## 3. VRAM 内訳

| コンポーネント | VRAM | ソース |
|---|---|---|
| モデル重み (Q4_K_XL) | 20686 MiB (CUDA) + 515 MiB (CPU) | llama.cpp ログ（実機確認） |
| KV キャッシュ (-c 262144) | 5120 MiB | llama.cpp ログ（実機確認） |
| RS バッファ (SSM state) | 251 MiB | llama.cpp ログ（実機確認） |
| Compute バッファ | 804 MiB (CUDA) + 520 MiB (CPU) | llama.cpp ログ（実機確認） |
| **合計 (nvidia-smi)** | **27168 MiB / 32623 MiB** | nvidia-smi（実機確認） |

残り ~5.5 GB。

## 4. モデルアーキテクチャ詳細

llama.cpp ログから抽出:（実機確認）

```
architecture     = qwen35moe
n_embd           = 2048
n_head           = 16
n_head_kv        = 2        (GQA 8:1)
n_embd_head_k    = 256
n_embd_head_v    = 256
n_embd_k_gqa     = 512
n_embd_v_gqa     = 512
n_expert         = 256
n_expert_used    = 8
n_ctx_train      = 262144   (ネイティブ 262K コンテキスト)
full_attention_interval = 4 (4層に1層が full attention)
ssm_d_inner      = 4096
ssm_d_state      = 128
ssm_d_conv       = 4
ssm_dt_rank      = 32
ssm_n_group      = 16
rope_freq_base   = 10000000.0
flash_attn       = enabled
```

## 5. 推論性能特性

### 単独実行時のベースライン性能（実機確認）

民法全文（122K prompt tokens）の処理:

| 指標 | 値 |
|------|-----|
| Prompt processing (PP) | **2,916 tok/s**（0.34 ms/tok） |
| Token generation (TG) | **99 tok/s**（10.1 ms/tok） |

### スロット競合による性能劣化（実機確認）

`n_parallel=4` での並列実行時:

| 条件 | PP (tok/s) | TG (tok/s) | PP 劣化率 |
|------|-----------|-----------|----------|
| 単独実行 | 2,916 | 99 | baseline |
| 4並列（最悪） | **388** | 39 | **7.5倍劣化** |
| 4並列（平均） | ~1,430 | ~38 | ~2倍劣化 |
| 2並列 | ~1,750 | ~65 | ~1.7倍劣化 |

PP（prefill）の劣化が TG（生成）より激しい。VRAM 帯域の競合が支配的。

### SWA キャッシュ非互換

スロット再利用時に「forcing full prompt re-processing due to lack of cache data (SWA)」が発生。Hybrid Attention-SSM アーキテクチャと llama.cpp のプロンプトキャッシュの互換性問題。

### GPU サーマル・電力特性（nvidia-smi 実測）

| 状態 | 温度 | 消費電力 | GPU-Util | Fan |
|------|------|---------|---------|-----|
| アイドル | 28-38°C | 9W | 0% | 30% |
| 単独推論（~81s） | 49→53°C (+4°C) | 198-200W | 91-100% | 30% |
| 4並列推論（~200s） | 50→73°C (+23°C) | 198-200W | 94-100% | 30→44% |

- **サーマルスロットリングなし**: ピーク 73°C < 閾値 83-90°C
- **電力制限が先に効く**: 推論中は TDP 200W に張り付き（power-limited）
- **並列持続で温度蓄積**: 単独 +4°C vs 4並列持続 +23°C

### Thinking Turns によるコンテキスト消費

Qwen3.5 の thinking mode では `<think>...</think>` ブロックがコンテキストを消費する。実効予算:

```
262K tokens（ネイティブ上限）
 - System prompt + 会話履歴: ~2K tokens
 - Thinking overhead（応答ごと）: ~500-2000 tokens
 - LLM 出力: ~1000-3000 tokens
 ≈ 入力に使える実効予算: ~256K tokens（~768K 文字）
```

`max_tokens=4096` の場合、Thinking が全量を消費し content=0 になるケースを確認（実機確認）。`max_tokens=8192` 以上を推奨。

## 6. 推奨構成

### 起動パラメータ

```bash
llama-server \
  -m Qwen3.5-35B-A3B-UD-Q4_K_XL.gguf \
  --host 0.0.0.0 --port 8080 \
  -ngl 99 -c 262144 --jinja -fa on
```

### コンテキスト制約の消失

262K tokens ≈ 日本語 ~780K 文字。法令全文の LLM 投入が技術的に可能:

| 法令 | 推定本文サイズ | 262K context |
|------|-------------|-------------|
| 行政手続法 | ~25KB | 余裕 |
| 刑法 | ~150KB | 余裕 |
| 民事訴訟法 | ~250KB | 可能 |
| 民法 | ~500KB | 可能 |

→ 要約実験の詳細は `2026-03-26_law-summarization-experiment.md` を参照。

### KV キャッシュ量子化の余地

現在 f16。q8_0 にすると KV キャッシュが半減（~2.5GB）:
```bash
--cache-type-k q8_0 --cache-type-v q8_0
```
品質劣化の検証は未実施（→ 要裏取り）。

### プロンプトキャッシュ

- チェックポイント間隔: 8,192 tokens
- 1チェックポイント: ~63 MiB
- キャッシュ上限: 8,192 MiB
- 最大チェックポイント（122K prompt）: 16個

# 研究室・自宅 計算機資源マップ

> 最終更新: 2026-03-17
>
> 本ドキュメントは、研究活動に用いる計算機資源（GPGPUワークステーション、ターミナル端末群、ネットワーク機器）の包括的な仕様・構成・運用情報を集約したものである。

---

## 1. GPGPUワークステーション（研究室設置・ヘッドレス運用）

## 1.0 概要

| カテゴリ | コンポーネント | 詳細仕様 / 備考 |
| --- | --- | --- |
| **CPU** | **AMD Ryzen 9 9950X** | Zen 5アーキテクチャ / 16コア32スレッド |
| **GPU** | **NVIDIA RTX PRO 4500 Blackwell** | 32GB級VRAM / 実装済み / `nvidia-smi` で確認済み |
| **マザーボード** | **ASUS ProArt X870E-CREATOR WIFI** | USB4搭載 / 10GbE + 2.5GbE搭載 / 通常運用では10GbE側NICでルータのLAN1 (1GbE) に接続 |
| **メモリ** | **Crucial DDR5 96GB (48GB x 2)** | DDR5-5600 / 2枚構成 |
| **ストレージ (Boot)** | **Crucial T500 1TB** | M.2_1スロット装着 / `/` 用 |
| **ストレージ (Data)** | **Kingston KC3000 4TB** | M.2_4スロット装着 / `/data` にマウント済み |
| **電源ユニット** | **玄人志向 KRPW-GS1000W/90+** | 1000W / 80PLUS GOLD認証 |
| **PCケース** | **Fractal Design Pop 2 Air Black Solid** | 高エアフロー設計 |
| **CPUクーラー** | **玄人志向 KURO-AIOWC360/V2** | 360mmオールインワン簡易水冷 |
| **ケースファン** | **Noctua NF-A12x25 PWM (x4)** | chromax.black.swap（静音・高静圧） |
| **OS** | **Ubuntu 24.04.4 LTS (Server)** | ヘッドレス運用 / NVIDIA Driver 595.45.04 / CUDA 13.2 |

### 1.1 CPU

| 項目 | 仕様 |
|------|------|
| **製品名** | AMD Ryzen 9 9950X |
| **アーキテクチャ** | Zen 5 (Granite Ridge) / TSMC 4nm FinFET |
| **コア / スレッド** | 16C / 32T (SMT) |
| **ベースクロック** | 4.3 GHz |
| **最大ブーストクロック** | 5.7 GHz (Precision Boost 2) |
| **キャッシュ** | L1 1,280KB / L2 16MB / L3 64MB（合計80MB） |
| **TDP / 最大消費電力** | 170W / 230W (PPT) |
| **TjMax** | 95°C |
| **対応メモリ** | DDR5-5600 (ネイティブ), ECC対応 |
| **PCIeレーン** | PCIe 5.0 (CPU直結: x16 GPU + M.2) |
| **ソケット** | AM5（2027年までサポート予定） |
| **命令セット拡張** | AVX-512, AVX2, FMA3, AES, SHA |
| **内蔵GPU** | AMD Radeon Graphics（RDNA 2ベース, 最小限） |

**運用上の注記:** AVX-512対応により、LLM推論時のトークン生成速度が前世代比で改善される。Precision Boost Overdrive (PBO) およびCurve Optimizerによるチューニング余地あり。デュアルCCD構成のため、コアパーキング機能がゲーミング以外のワークロードに影響しないか確認が望ましい。

### 1.2 GPU

| 項目 | 仕様 |
|------|------|
| **製品名** | NVIDIA RTX PRO 4500 (Blackwell) |
| **GPU** | GB203 |
| **CUDAコア** | 10,496 |
| **VRAM** | 32GB ECC GDDR7 |
| **メモリバス幅** | 256-bit |
| **メモリ帯域** | 896 GB/s |
| **ブーストクロック** | 最大 2,600 MHz |
| **Tensorコア** | 第5世代（FP4 / FP8 / FP16対応） |
| **RTコア** | 第4世代 |
| **TDP** | 200W |
| **インターフェース** | PCIe 5.0 x16 |
| **映像出力** | DisplayPort 2.1b × 4 |
| **冷却** | シングルファン・ブロワー型 |
| **フォームファクタ** | Standard Height, Dual-Slot（4.4"H × 10.5"L） |
| **エンコーダ / デコーダ** | 第9世代 NVENC / 第6世代 NVDEC |
| **実機確認メモ** | `nvidia-smi` / `nvidia-smi -q` (2026-03-17) で `NVIDIA RTX PRO 4500 Blackwell`, FB Memory Total 32623MiB, BAR1 32768MiB, Power Limit 200W, VBIOS `98.03.A2.00.01`, PCIe Max Gen5 x16 を確認 |

**LLM推論との関係:** 32GB級VRAMにより、FP16で13B級モデル、FP4/INT4量子化で70B級モデルの単一GPU推論が可能。TensorRT-LLMカーネルとの組み合わせでスループットが最大化される。NVLink非対応のため、VRAMを超えるモデルではCPUオフロードまたはアグレッシブな量子化が必要。なお、2026-03-17時点の`nvidia-smi -q`ではECC Modeは`Disabled`であり、ECCを前提にした運用にはなっていない。

**MoEモデルのFT制約（2026-03-19確認）:** Qwen3.5-35B-A3BのようなMoEアーキテクチャのモデルでは、QLoRA（4-bit量子化FT）が推奨されない（Unsloth公式ドキュメントで明示的に非推奨。BitsAndBytesライブラリのMoEサポート制約に起因）。BF16 LoRA FTが推奨されるが、BF16重みサイズは約67GB、BF16 LoRA学習のピークメモリは約72〜74GBであり、32GB VRAMの本GPUでは物理的に不可能。MoEモデルのFTには74GB以上のメモリを持つ環境（玄界H100、DGX Spark/GX10の128GB統合メモリ等）が必要。

### 1.3 マザーボード

| 項目 | 仕様 |
|------|------|
| **製品名** | ASUS ProArt X870E-CREATOR WIFI |
| **チップセット** | AMD X870E |
| **ソケット** | AM5 |
| **フォームファクタ** | ATX |
| **電源フェーズ** | 16+2+2 (各80A) |
| **メモリスロット** | DDR5 × 4 DIMM（最大192GB, 最大8000MT/s） |
| **PCIeスロット** | PCIe 5.0 x16 × 2, PCIe 4.0 x16(x4) × 1 |
| **M.2スロット** | 下表参照 |
| **有線LAN** | Marvell AQtion 10GbE + Intel I226-V 2.5GbE |
| **Wi-Fi** | Wi-Fi 7 (802.11be) 2.4/5/6GHz, Bluetooth 5.4 |
| **USB (リアI/O)** | USB4 × 2, USB 3.2 Gen2x2 Type-C × 1, USB 3.2 Gen2 Type-A × 7, USB 2.0 × 1 |
| **USB (内部ヘッダ)** | USB 3.2 Gen2x2 Type-C × 1 (30W QC4+), USB 3.2 Gen1 Type-A × 1 (2ポート), USB 2.0 × 3 (6ポート) |
| **映像出力** | HDMI (リアI/O), DisplayPort (USB4経由) |

**M.2スロット配置と使用状況:**

| スロット | 接続先 | PCIe世代 | 現在の使用 | 備考 |
|----------|--------|----------|-----------|------|
| M.2_1 | CPU直結 | PCIe 5.0 x4 | **Crucial T500 1TB** (Boot) | Gen4デバイスのため、PCIe 4.0速度で動作。Gen5 SSDへのアップグレードパスが残る |
| M.2_2 | CPU直結 | PCIe 5.0 x4 | 空き | PCIEX16(G5)_2と帯域共有 |
| M.2_3 | チップセット | PCIe 4.0 x4 | 空き | GPU直下で排熱の影響を受けやすく、現運用では使用していない |
| M.2_4 | チップセット | PCIe 4.0 x4 | **Kingston KC3000 4TB** (Data, `/data`) | 2026-03-17時点で実装・マウント済み。22110サイズ対応 |

**USB4に関する注記:** USB4ポートは最大40Gbps。Thunderbolt互換性についてはASMediaコントローラ (ASM4242) 経由であり、Intel認証のThunderboltとは異なる。Mac側からのThunderbolt接続時に互換性の制約が生じる可能性あり。

### 1.4 メモリ

| 項目 | 仕様 |
|------|------|
| **製品名** | Crucial Pro CP2K48G56C46U5 |
| **容量** | 96GB (48GB × 2) |
| **規格** | DDR5-5600 (PC5-44800) |
| **CASレイテンシ** | CL46 (46-45-45) |
| **電圧** | 1.1V |
| **フォームファクタ** | UDIMM 288-pin |
| **ランク構成** | 2Rx8 |
| **ECC** | On-die ECC (ODECC) のみ。システムレベルECCではない |
| **XMP / EXPO** | Intel XMP 3.0 / AMD EXPO 対応 |

**運用上の注記:** Ryzen 9950Xのネイティブ対応は DDR5-5600 であり、現構成はオーバークロック不要で定格動作する。EXPO プロファイルをBIOSで有効化すること。48GB DIMMはデュアルランクで、帯域はシングルランク比で改善される。スロット2本のみ使用のため、将来的に48GB × 2 を追加して192GBへの拡張が可能（マザーボード最大192GB）。

### 1.5 ストレージ

#### Boot: Crucial T500 1TB

| 項目 | 仕様 |
|------|------|
| **インターフェース** | PCIe 4.0 x4 / NVMe 2.0 |
| **フォームファクタ** | M.2 2280 |
| **コントローラ** | Phison PS5025-E25 |
| **NAND** | Micron 232層 3D TLC |
| **シーケンシャルR/W** | 最大 7,300 / 6,800 MB/s |
| **ランダムR/W** | 最大 1.15M / 1.44M IOPS |
| **耐久性 (TBW)** | 600TB |
| **保証** | 5年 |
| **装着スロット** | M.2_1 (CPU直結 PCIe 5.0 x4 → Gen4速度で動作) |

#### Data: Kingston KC3000 4TB

| 項目 | 仕様 |
|------|------|
| **インターフェース** | PCIe 4.0 x4 / NVMe 1.4 |
| **フォームファクタ** | M.2 2280 |
| **コントローラ** | Phison PS5018-E18 |
| **NAND** | Micron 176層 3D TLC |
| **シーケンシャルR/W** | 最大 7,000 / 7,000 MB/s |
| **ランダムR/W** | 最大 1M / 1M IOPS |
| **耐久性 (TBW)** | 3,200TB (3.2PB) |
| **DRAMキャッシュ** | あり（DDR4） |
| **SLCキャッシュ** | 動的割当、最大容量の約1/3 |
| **消費電力** | 最大 10.2W (書込時) |
| **放熱** | グラフェン・アルミニウム ヒートスプレッダー |
| **保証** | 5年 |
| **装着スロット** | M.2_4 (チップセット PCIe 4.0 x4) |
| **現在の利用状況** | `/data` にマウント済み（2026-03-17時点で約106GB使用） |

**注意:** KC3000 4TBは書込時消費電力が高く、持続的な大量書込で発熱しやすい。M.2_4スロットのマザーボード付属ヒートシンクと合わせて運用し、サーマルスロットリング（84°C）の発生有無をS.M.A.R.T.で監視することを推奨。
**03/17追記:** M.2_3スロットはGPUの設置されているPCIx16_1スロットの直下にあるためGPUの排熱を直接に受けるという物理配置上の問題があったため、M.2_4スロット側に設置した（M.2_2〜M.2_4スロットには同一のヒートシンクがついている）。`lsblk`/`df`上でもKC3000は`/dev/nvme1n1p1`として`/data`にマウントされていることを確認済み。

### 1.6 冷却

#### CPUクーラー

| 項目 | 仕様 |
|------|------|
| **製品名** | 玄人志向 KURO-AIOWC360/V2 |
| **種別** | オールインワン簡易水冷 |
| **ラジエーター** | 360mm (Asetek製) |
| **ファン** | 120mm FDB (流体動圧軸受) × 3（付属ファン） |
| **最大回転数** | 2,000 rpm |
| **最大風量** | 71.6 CFM |
| **騒音** | 最大 32.5 dBA |
| **ファン＋ラジエーター厚** | 約 53mm |
| **LED** | 非搭載（ブラックモデル） |
| **対応ソケット** | Intel LGA1851/1700/1200/115x, AMD AM5/AM4 |
| **保証** | 3年 |

#### ケースファン

| 項目 | 仕様 |
|------|------|
| **製品名** | Noctua NF-A12x25 PWM chromax.black.swap × 4 |
| **サイズ** | 120mm |
| **特性** | 高静圧・静音。PWM制御 |

**エアフロー設計の注記:** 実際のファン配置は、前面 120mm × 3 を吸気、天面 360mm ラジエーター + 120mm × 3 を排気、背面 120mm × 1 を排気としている。前面メッシュケースの利点を活かしつつ、CPUラジエーターの排熱を天面へ逃がす構成。

### 1.7 電源・ケース

| コンポーネント | 製品名 | 仕様 |
|------------|--------|------|
| **電源ユニット** | 玄人志向 KRPW-GS1000W/90+ | 1000W / 80PLUS GOLD / ATX電源 |
| **PCケース** | Fractal Design Pop 2 Air Black Solid | ATX対応 / ソリッドサイドパネル / 高エアフロー前面メッシュ |

**電力バジェット概算:** CPU 170W (PBOで最大230W) + GPU 200W + SSD×2 約17W + マザーボード・メモリ・ファン等 約50W → ピーク時 約500W弱。1000W電源に対して50%前後の負荷率であり、80PLUS GOLDの効率ピークゾーンに収まる。将来的にGPU交換やストレージ増設があっても余裕がある。

**研究室側の電源環境（分電盤）:** 研究室には単相三線式の分電盤が設置されており、主幹ブレーカーは **AC110/220V 30A**（漏電遮断器、過電圧表示機能付き）である。個別回路は上段・下段各4回路の計8回路で構成される。

| 段 | 回路名 | 定格 | 備考 |
|----|--------|------|------|
| 上段 | 照明 | 200V 20A | |
| 上段 | コンセント | 100V 20A | |
| 上段 | 室内機 | 100V 20A 30mA | 漏電遮断器付き（エアコン室内機用） |
| 上段 | 予備 | 100V 20A | |
| 下段 | コンセント (C1) | 100V 20A | |
| 下段 | HUB用 (C3) | 100V 20A | ネットワーク機器専用回路 |
| 下段 | 全熱交換器 (M1) | 100V 20A | 換気システム用 |
| 下段 | 予備 | 100V 20A | |

単相三線式のため、主幹30Aに対する最大供給能力はバランス負荷時で約6,000W、片線集中時で約3,000Wとなる。加えて、各個別回路のブレーカーは20Aであり、**1回路あたりの上限は100V × 20A = 2,000W**（200V回路は200V × 20A = 4,000W）である。研究室内の計算資源について、壁コンセント側での推定消費電力を以下に整理する。

| 機器 | Idle | Typical | Peak | 出典 |
|------|-----:|--------:|-----:|------|
| GPGPU WS | 79W | 278W | 552W | 資源マップ記載値 + PSU効率逆算（公式ドキュメント） |
| Mac Studio (M1 Max) | 12W | 45W | 115W | Apple Support / NotebookCheck実測（公式ドキュメント） |
| old-mini (2018 i7) ※未稼働 | 20W | 50W | 122W | Apple Support（公式ドキュメント） |
| Buffalo WNR-5400XE6 | 15W | 18W | 20W | Buffalo公式 定格20.4W。idle値は推定 |

建物設備（照明・エアコン・全熱交換器）の同時消費を加味した研究室全体の電力バジェットは以下の通り。

| シナリオ | Typical | Peak | 対バランス上限 (6kW) | 対片線上限 (3kW) |
|----------|--------:|-----:|--------------------:|----------------:|
| 現行構成（old-mini除外） | 約971W | 約2,438W | 16% / 41% | 32% / 81% |
| old-mini復活後 | 約1,021W | 約2,560W | 17% / 43% | 34% / 85% |

※ Typical / Peak の順。建物設備はエアコン定常500W・起動時1,500W、照明100〜200W（LED前提）、全熱交換器30〜50Wと推定。

**運用上の注意:** 片線集中＋全機器ピークで主幹容量の8割超に達するため、エアコン起動時の突入電流（定常の3〜5倍）とGPU負荷ピークが重なるとブレーカートリップのリスクがある。個別回路についても、GPGPU WSのピーク消費552Wは1回路上限2,000Wの約28%であり単体では余裕があるが、同一コンセント回路にモニタ・外付けストレージ等を集中接続すると回路単位でのトリップリスクが生じうる。old-mini復活時は、GPGPU WSとは別のコンセント回路（C1または予備）に接続し、回路レベルでの負荷分散を図ることを推奨する。

### 1.8 OS・ソフトウェア環境

| 項目 | 内容 |
|------|------|
| **OS** | Ubuntu 24.04.4 LTS (Server) |
| **運用形態** | ヘッドレス（SSH接続） |
| **NVIDIA Driver / CUDA** | 595.45.04 / 13.2 |
| **Python環境管理** | できる限り `uv sync` ベースを採用 |

**内部確認メモ（2026-03-17）:** `lspci`上では、NVIDIA dGPU（`10de:2c31`、`nvidia`ドライバ使用中）、AMD内蔵GPU（`1002:13c0`、`amdgpu`使用中）、Qualcomm WCN785x Wi-Fi 7、Intel I226-V 2.5GbE、Aquantia AQC113 10GbE を確認。加えて、ユーザ提供の `nvidia-smi` 実行結果（2026-03-17 14:47:52）および `nvidia-smi -q` 出力から、GPU正式名 `NVIDIA RTX PRO 4500 Blackwell`、FB Memory Total `32623MiB`、BAR1 `32768MiB`、Persistence Mode `Enabled`、ECC Mode `Disabled`、電力上限 `200W`、Driver `595.45.04`、CUDA `13.2`、VBIOS `98.03.A2.00.01`、PCIeリンク能力 `Gen5 x16` を確認した。なお、取得時点のリンク速度はアイドル状態のため `Current Gen1 x16` だった。

#### 導入済みプラットフォーム（常時起動ではない）

| プラットフォーム | 状態 | 備考 |
|--------------|------|------|
| **llama.cpp** | git clone → cmake ビルド済み | llama-cli, llama-server 等が利用可能 |
| **Stable Diffusion Web UI** | AUTOMATIC1111ベース改造版 | Ubuntu + uv + Blackwell対応ビルド |
| **Ollama** | systemctl 登録済み（現在停止） | `systemctl stop` 済み。今後は自動起動を無効化予定 |

#### 導入済みモデル

| 種別 | モデル/重み | 配備 | 備考 |
|------|-------------|------|------|
| LLM / VLM | Qwen3.5-35B-A3B-Uncensored-HauhauCS-Aggressive-Q4_K_M.gguf | `/data/models/llm-models/` | 約20GB。llama.cpp向け量子化GGUF |
| LLM / VLM | mmproj-Qwen3.5-35B-A3B-Uncensored-HauhauCS-Aggressive-f16.gguf | `/data/models/llm-models/` | 約858MB。上記モデル用の補助投影重み |
| 画像編集 | Qwen/Qwen-Image-Edit-2511 | `/data/huggingface/hub/` | HF cache配備済み。snapshot `6f3ccc0b56e431dc6a0c2b2039706d7d26f22cb9`、約54GB |
| 画像生成 | BeautifulRealisticAsian_V7.safetensors | `/data/models/stable-diffusion/` | 約2.0GB。Stable Diffusion系メインモデル |
| LoRA | BRA-LoRA-MK-v3_7500_lora_f32.safetensors | `/data/models/stable-diffusion/` | 約286MB。追加学習重み |
| LoRA | BRA-LoRA-MK-v4_test_5000_lora_f32.safetensors | `/data/models/stable-diffusion/` | 約286MB。テスト版追加学習重み |

#### 導入済みアプリケーション

| アプリ | 状態 | バージョン/リビジョン | 備考 |
|--------|------|----------------------|------|
| Claude Code | 導入済み | 2.1.76 | コーディングエージェント |
| Codex CLI | 導入済み | 0.114.0 | コーディングエージェント |
| Dropbox | 導入済み | ※CLIから版未確認 | 同期ディレクトリは `/data/Dropbox` に移設済み。将来的にはGPUサーバから除去予定 |
| gpgpu-runner (`Dropbox-Dispatcher`) | 自作・実装済み | v0.1.0 / `3093ef0` | Dropbox APIベースのGPGPUジョブ実行デーモン。systemd service 定義あり |
| QIE Platform (`qwen-image-edit`) | 自作・Phase 0-2 完了 | v0.1.0 / `495e728` | Qwen-Image-Edit用のGradio推論UI + LoRA学習基盤。`CLAUDE.md`ではPhase 3計画中 |
| Stable Diffusion Web UI (`customized-a1111`) | 改造版・Phase 2 完了 | v1.10.0 / `f8d68260` | AUTOMATIC1111ベース。uv化・CUDA専用化済み、Phase 3未着手 |
| vLLM (`vLLM`) | 試作・雛形 | v0.1.0 / 未コミット | `pyproject.toml` のみ確認。README未整備、説明文も暫定 |

#### その他（開発上重要な導入済みコマンド・基盤）

| カテゴリ | 項目 | 状態/版 | 備考 |
|----------|------|---------|------|
| Python / Build | uv | 0.10.9 | `/home/tnishimura/.local/bin/uv`。Python環境管理に利用 |
| Python / Build | Python 3 | 3.12.3 | `/usr/bin/python3` |
| Python / Build | git | 2.43.0 | `/usr/bin/git` |
| Python / Build | cmake | 3.28.3 | `/usr/bin/cmake` |
| Python / Build | gcc | 13.3.0 | `/usr/bin/gcc` |
| GPU / CUDA | nvcc | CUDA 13.2 | `/usr/local/cuda/bin/nvcc` |
| GPU / CUDA | nvidia-smi | 利用可 | NVIDIA Driver 595.45.04 / CUDA 13.2 確認済み |
| JavaScript | node | v18.19.1 | `/usr/bin/node` |
| JavaScript | npm | 9.2.0 | `/usr/bin/npm` |
| LLM Runtime | Ollama CLI | 0.17.7 | クライアント導入済み。実行時はサーバ未接続警告を確認 |
| LLM Runtime | llama.cpp binaries | ビルド済み | `/home/tnishimura/workspace/llama.cpp/build/bin/llama-cli`, `llama-server` を確認 |
| Coding Agent | Codex CLI | 0.114.0 | `/usr/local/bin/codex` |
| Coding Agent | Claude Code CLI | 2.1.76 | `/home/tnishimura/.local/bin/claude` |
| Container | docker | 導入済み | `/usr/bin/docker` |

**補記:** 2026-03-17時点のPATH確認では、`ninja`, `cargo`, `rustc`, `pnpm`, `docker-compose`, `ffmpeg`, `convert` (ImageMagick), `huggingface-cli` は未導入だった。必要に応じて追加導入を検討する。

---

## 2. ターミナル端末

すべてのターミナル端末はルータにWi-Fi接続しており、GPGPUワークステーションへはSSHでアクセスする。

### 2.1 Mac mini (M4 Pro, 24GB) — 自宅メインPC

| 項目 | 仕様 |
|------|------|
| **SoC** | Apple M4 Pro |
| **メモリ (UM)** | 24GB |
| **ストレージ** | 2TB SSD |
| **ネットワーク** | Wi-Fi経由でルータに接続 |
| **役割** | 自宅での日常作業・ターミナル端末 |

### 2.2 Mac Studio (M1 Max, 32GB) — 大学・研究室メインPC

| 項目 | 仕様 |
|------|------|
| **SoC** | Apple M1 Max |
| **メモリ (UM)** | 32GB |
| **ストレージ** | 1TB SSD |
| **ネットワーク** | Wi-Fi経由でルータに接続 |
| **有線LAN** | 10GbE (内蔵) |
| **役割** | 研究室での日常作業・ターミナル端末 |

**補記:** M1 Maxは10GbE NICを内蔵しているが、現在の運用ポリシーではルータへの有線接続は行っていない。GPGPUサーバとの高速転送が必要な場合、ルータ経由のWi-Fiがボトルネックになりうる。

### 2.3 MacBook Air (M4, 24GB) — モバイル端末

| 項目 | 仕様 |
|------|------|
| **SoC** | Apple M4 |
| **メモリ (UM)** | 24GB |
| **ストレージ** | 2TB SSD |
| **ネットワーク** | Wi-Fi経由でルータに接続 |
| **役割** | 大学・自宅兼用のモバイル端末 |

### 2.4 Mac mini (2018, Intel) — 補助サーバ候補 (old-mini)

| 項目 | 仕様 |
|------|------|
| **CPU** | 3.2 GHz 6コア Intel Core i7 |
| **メモリ** | 64GB 2667 MHz DDR4 |
| **ストレージ** | 2TB SSD |
| **有線LAN** | 1GbE (内蔵) |
| **その他I/F** | Thunderbolt 3 × 4 |
| **OS** | macOS 15.7.4 (24G517) |
| **ネットワーク** | Wi-Fiでルータに接続（有線接続も可） |
| **リモートアクセス** | VNC (macOS標準), SSH |
| **現在の状態** | 退役済み。補助サーバとして復活を検討中。サーバ用途での初期化は未了 |

**検討中の役割:**

- **Dropbox同期ノード:** GPGPUサーバからDropbox同期の負荷を分離し、モデルファイル等のデータレイクとしても活用する案。
- **WebUI / オーケストレータ:** GPGPUサーバ上のllama.cpp等にSSHトンネルで接続し、Webフロントエンドをold-mini側でホストする構成。ターミナル端末はold-mini経由でアクセスする案。
- **GPGPUサーバとのダイレクトリンク:** Thunderbolt 3 ↔ USB4（マザーボード側）またはLANポート直結（Mac側1GbE / GPGPU側2.5GbE）によるP2P接続を検討中。 (**2026/03/17追記:**) Thunderbolt3 ↔ USB4の直結そのものは良好に動くことを確認。GPGPUサーバとはThunderbolt Bridgeで接続中。

**運用メモ:** old-mini 側メインユーザのホームディレクトリは `/mnt/mac/lue` にマウントされており、当面はこの運用を継続する予定。

**構成図イメージ（案）:**
```
[GPGPU WS: llama.cpp等] --(SSHトンネル)--> [old-mini: Web Server / Orchestrator] --(Router)--> [Terminals]
```

---

## 3. ネットワーク構成

### 3.1 ルータ

| 項目 | 仕様 |
|------|------|
| **製品名** | Buffalo WNR-5400XE6 |
| **Wi-Fi規格** | Wi-Fi 6E (802.11ax) |
| **周波数帯** | トライバンド: 6GHz / 5GHz / 2.4GHz |
| **最大速度 (理論値)** | 2,401 + 2,401 + 573 Mbps（合計 5,375 Mbps） |
| **WANポート** | 2.5GbE × 1 |
| **LANポート** | 1GbE/100M/10M 対応 × 3 |
| **セキュリティ** | WPA3 Personal (WPA2互換モード対応) |
| **追加機能** | EasyMesh対応, バンドステアリングLite, OFDMA, TWT |
| **動作モード** | ルータモード（WAN側は学内LANに接続） |

### 3.2 接続トポロジ

```
                   [学内LAN]
                       |
                  WAN (2.5GbE)
                       |
              +------------------+
              |  Buffalo         |
              |  WNR-5400XE6     |
              |  (ルータモード)    |
              +--+--------+--+---+
                 |        |  |
           LAN(1G)  Wi-Fi  Wi-Fi
                 |     |      |     ...
           [GPGPU WS]  |   [Mac mini M4 Pro]
                     [Mac Studio]
                     [MacBook Air]
                     [old-mini]
```

### 3.3 実効帯域に関する注記

**⚠ 現在の有線ボトルネックはルータLAN側 1GbE:** 現在は、WNR-5400XE6 の 2.5GbE `INTERNET` ポートを学内ネットワークへのWAN uplinkとして使用し、GPGPUワークステーションは空きLANポートの1つ（LAN1, 1GbE）に接続している。通常運用でルータに接続しているのは GPGPU WS の 10GbE 側NICであり、2.5GbE 側NICには別IPを割り当てている。したがって、現運用での GPGPU WS ↔ ルータ間リンクは 1Gbps であり、10GbE NICを使っていても実効帯域はルータLAN側に制約される。

**2.5GbEポート転用の可能性:** Buffalo公式仕様では、`INTERNET` ポートをLANポートとして利用する場合、`AUTO/MANUAL` スイッチを `MANUAL` にし、`ROUTER/AP/WB` スイッチを `AP/WB` にして使用する構成が案内されている。これは技術的には 2.5GbE ポートを内向きリンクに転用できることを意味するが、その場合は少なくとも現在の「本機が学内LANへのWAN出口を持つルータ」という運用は維持できない。実現するには、別系統の上位ルータ・中継機・EasyMesh構成などでWAN到達性を補う必要がある。現時点では現実的な常用構成ではなく、検討事項に留まる。

各経路の理論上の実効帯域を整理すると:

| 経路 | ボトルネック | 実効上限 |
|------|------------|---------|
| GPGPU WS ↔ ルータ | ルータ LAN1 (1GbE) | **1 Gbps** |
| GPGPU WS ↔ ターミナル (Wi-Fi経由) | Wi-Fi 6E / ルータLAN1 (1GbE) | **実測数百Mbps〜1Gbps未満程度** |
| GPGPU WS ↔ old-mini (ルータ経由) | old-mini側 1GbE or Wi-Fi | **最大 1 Gbps** |
| GPGPU WS ↔ old-mini (ダイレクトLAN) | old-mini側 1GbE | **1 Gbps** |
| GPGPU WS ↔ 外部 (学内LAN) | GPGPU WS側 10GbE NIC→ルータLAN1 (1GbE) + ルータWAN側実リンク未確認 | **最大 1 Gbps** |

**補記:** マザーボード上の Intel I226-V 2.5GbE NIC は別IP割当済みで、old-miniとのダイレクト接続（P2P）など、ルータを介さない用途の候補として残している。ただしold-mini側が1GbEのため、実効は1Gbpsに制約される。

### 3.4 運用ポリシー

- **原則として有線接続はGPGPUサーバのみ。** ケーブル過多による物理的不便性およびルータ配置場所による端末配置制約を回避するため。
- ターミナル端末はすべてWi-Fi接続。
- old-miniは現在Wi-Fi接続。復活時に有線接続に切替える可能性あり。

---

## 4. 未記録事項・拡充候補

以下は、本ドキュメントを「完全な計算機資源マップ」とするために今後記録すべき事項である。

### ハードウェア情報

- [ ] GPGPUワークステーション付属ファンの有無・ケース標準ファンの扱い

### ネットワーク情報

- [ ] 各端末のIPアドレス体系・ホスト名（DHCPか固定か）
- [ ] GPGPUサーバの10GbE / 2.5GbE それぞれのIPアドレス（文書上は「別IP割当済み」までに留める）
- [ ] SSH鍵の管理方針（どの端末からどの端末にアクセス可能か）
- [ ] ファイアウォール / ポートフォワーディング設定
- [ ] 学内LAN ↔ ルータWAN の実リンク速度

### ソフトウェア・運用情報

- [ ] `uv sync` 非採用の例外プロジェクトの整理
- [ ] バックアップ戦略（4TB SSD取付後のパーティション計画を含む）

### 将来のアップグレードパス

- [ ] M.2_1スロットをPCIe 5.0 NVMe SSDに換装してBoot高速化（Crucial T700等）
- [ ] メモリ 192GB化（48GB × 4）
- [ ] 10GbE対応スイッチ導入によるGPGPU WS ↔ Mac Studio間の高速化
- [ ] old-miniのDropbox同期ノード化の具体設計
- [ ] RTX PRO 6000 Blackwell Max-Q（300W TDP / 96GB GDDR7 ECC / 1,792 GB/s / PCIe 5.0 x16）の導入検討。既存WSへの追加搭載が電力的に可能（RTX PRO 4500 200W + Max-Q 300W = 500W、PSU 1000Wに対して余裕あり）。日本価格推定¥130–150万。詳細は `DGX_Spark調達検討メモ.md` 参照
- [ ] DGX Spark / ASUS Ascent GX10 の導入検討。128GB統合メモリでMoE BF16 LoRA FTが可能（NVIDIA Developer ForumにてQwen3.5-35B-A3BのFT実証済み、2026-03-12投稿）。GX10 1TB: ¥598,000–786,900。消費電力~240W。詳細は `DGX_Spark調達検討メモ.md` 参照
- [ ] RTX PRO 5000 Blackwell のスペック・日本価格の確認待ち（US $4,439–4,569。VRAM容量・帯域幅が不明。48GBの場合BF16 LoRA 74GB要件を満たさない）

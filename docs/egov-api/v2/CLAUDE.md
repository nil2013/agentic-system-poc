# docs/egov-api/v2/ — 法令 API Version 2 仕様 + ドメインリファレンス

V2 API 仕様および、API バージョンに依存しないドメイン知識の整理文書を格納する。

## ファイル一覧と役割

| ファイル | 役割 | いつ参照するか |
|---|---|---|
| `lawapi-v2.yaml` | V2 OpenAPI 仕様（OpenAPI 3.0.3, ~126KB） | V2 エンドポイント・パラメータ・レスポンススキーマ・enum 定義を確認するとき |
| `domain-reference.md` | YAML を補完するドメインリファレンス | 法令ID の内部構造、法令番号の体系、XML 要素の意味、改正・施行モデル等、YAML に含まれないドメイン知識を確認するとき |

## V2 API エンドポイント（クイックリファレンス）

| エンドポイント | 用途 | V1 との対応 |
|---|---|---|
| `GET /laws` | 法令一覧取得（検索・フィルタリング・ページネーション） | `/api/1/lawlists` の拡張版 |
| `GET /law_revisions/{id}` | 法令履歴一覧取得 | V1 に対応なし（新規） |
| `GET /law_data/{id}` | 法令本文取得（JSON/XML 選択可） | `/api/1/lawdata` の拡張版 |
| `GET /attachment/{revision_id}` | 添付ファイル取得（JPG/PDF） | V1 では `lawdata` に内包 |
| `GET /keyword` | キーワード検索（全文検索） | V1 に対応なし（新規） |
| `GET /law_file/{type}/{id}` | 法令ファイルDL（xml/json/html/rtf/docx） | V1 に対応なし（新規） |

## YAML と domain-reference.md の分担

| 情報の種類 | `lawapi-v2.yaml` | `domain-reference.md` |
|---|---|---|
| エンドポイント仕様（パス、パラメータ、レスポンス型） | 完全 | — |
| enum 定義（`law_type`, `category_cd`, `repeal_status` 等） | 完全 | 意味の補足あり |
| `law_id` の内部構造（15桁エンコーディング） | なし（opaque string） | 完全 |
| `law_num` の体系（共同命令、人事院規則等の特殊形式） | なし | あり |
| `law_revision_id` の構造 | なし（opaque string） | 完全 |
| XML 要素の意味・運用実態 | `elm` パラメータ説明のみ | あり（XSD + docs-alpha 参照） |
| 改正・施行モデル（溶け込み、調整規定等） | なし | あり |
| XML→JSON 変換ルール（detailed/light） | 完全 | — |

## 注意事項

- `lawapi-v2.yaml` は大容量（~126KB）。全体を読む場合はサブエージェントの使用を推奨。
- 本プロジェクトは現在 V1 を使用中。V2 の仕様は将来の移行に備えた参考資料。
- `domain-reference.md` の内容は API バージョンに依存しない。V1 利用時にも参照可能。
- ドメインリファレンスの情報源（`../docs-alpha/`）は α版であり、将来変更される可能性がある。

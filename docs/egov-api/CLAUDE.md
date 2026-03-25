# docs/egov-api/ — e-Gov 法令 API ドキュメント群

e-Gov 法令検索 API に関するドキュメントを格納するディレクトリ。

## ディレクトリ構造

```
docs/egov-api/
├── v1/                         V1 API 仕様書群
├── v2/                         V2 API 仕様 + ドメインリファレンス
├── docs-alpha/                 α版ドキュメントのスクレイピング済みソース
├── XMLSchemaForJapaneseLaw_v3.xsd   法令標準XMLスキーマ（V1/V2共通）
├── egov-law-client-design.md        本プロジェクトのクライアント実装設計書
└── tool-capability-analysis.md      ツール化候補の分析文書
```

## 参照ルーティング

| 調べたいこと | 参照先 |
|---|---|
| **V1 API のエンドポイント・パラメータ・レスポンス** | `v1/` |
| **V2 API のエンドポイント・パラメータ・レスポンス** | `v2/lawapi-v2.yaml` |
| **法令ID・法令番号・法令種別・改正モデル等のドメイン知識** | `v2/domain-reference.md` |
| **法令 XML の構文定義（有効な要素・属性・親子制約）** | `XMLSchemaForJapaneseLaw_v3.xsd` |
| **法令 XML の意味解説（各要素の意味・運用実態）** | `docs-alpha/xml-schema.md` |
| **法令の条文構造と XML の対応関係** | `docs-alpha/law-structure-and-xml.md` |
| **改正・施行・バージョン管理の概念** | `docs-alpha/law-revisions.md`, `docs-alpha/version-control.md` |
| **本プロジェクトの e-Gov クライアント設計** | `egov-law-client-design.md` |
| **ツール化候補の優先順位・設計考慮** | `tool-capability-analysis.md` |

## ドキュメントの役割分類

| ドキュメント | 役割 | 性質 |
|---|---|---|
| `v1/*.md`, `v1/*.pdf` | V1 API 仕様 | 静的リファレンス |
| `v2/lawapi-v2.yaml` | V2 API 仕様（OpenAPI 3.0.3） | 静的リファレンス |
| `v2/domain-reference.md` | YAML の補完（ドメイン知識） | 整理済みリファレンス |
| `docs-alpha/*.md` | α版ドキュメントの原文保存 | ソース資料（スクレイピング） |
| `XMLSchemaForJapaneseLaw_v3.xsd` | XML スキーマ定義 | 静的リファレンス（V1/V2共通） |
| `egov-law-client-design.md` | 実装設計書 | プロジェクト固有 |
| `tool-capability-analysis.md` | ツール化候補分析 | プロジェクト固有 |

## 注意事項

- `docs-alpha/` は e-Gov 法令データドキュメンテーション（α版）のスクレイピング結果。URL が変更・削除される可能性がある旨の注記が原文に含まれる。
- XSD は V1/V2 共通。法令 XML のフォーマット自体はバージョン間で変わらない。
- `lawapi-v2.yaml` は V2 のみ。本プロジェクトは現在 V1 を使用しているが、V2 移行を視野に入れて資料を整備している。

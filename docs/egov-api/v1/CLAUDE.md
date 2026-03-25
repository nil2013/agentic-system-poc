# docs/egov-api/v1/ — 法令 API Version 1 仕様書群

本プロジェクトが現在使用している API バージョン。

## ファイル一覧と役割

| ファイル | 役割 | いつ参照するか |
|---|---|---|
| `hourei-api-v1-rest-spec.md` | V1 REST API 仕様（Markdown 形式） | エンドポイントのパラメータ・レスポンス構造を確認するとき。最も参照頻度が高い |
| `egov-law-api-v1-spec.md` | V1 API 仕様の詳細版 | `hourei-api-v1-rest-spec.md` で不足する詳細（エラーコード、制約等）を確認するとき |
| `houreiapi_shiyosyo.pdf` | 公式仕様書 PDF（一次資料） | 上記 Markdown の元資料。Markdown との齟齬が疑われるとき。大容量注意（565KB） |
| `houreiapi_message_ichiran.pdf` | メッセージ一覧 PDF | エラーメッセージの一覧・コード体系を確認するとき。大容量注意（161KB） |

## V1 API エンドポイント（クイックリファレンス）

| エンドポイント | 用途 |
|---|---|
| `GET /api/1/lawlists/{category}` | 法令名一覧取得（category: 1=全法令, 2=憲法・法律, 3=政令・勅令, 4=府省令・規則） |
| `GET /api/1/lawdata/{lawIdOrLawNum}` | 法令本文取得（XML） |
| `GET /api/1/articles;lawNum=...;article=...` | 条文内容取得（セミコロン区切りマトリクスパラメータ） |
| `GET /api/1/updatelawlists/{YYYYMMDD}` | 更新法令一覧取得（2020-11-24以降） |

## 注意事項

- PDF は大容量のため、直接読み込む前にサブエージェントでの読み取りを検討すること。
- 法令 XML のスキーマは `../XMLSchemaForJapaneseLaw_v3.xsd`（V1/V2 共通）。
- 法令ID・法令番号等のドメイン知識は `../v2/domain-reference.md` にバージョン非依存の形で整理されている。

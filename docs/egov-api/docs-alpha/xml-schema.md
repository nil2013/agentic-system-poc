<!-- source: https://laws.e-gov.go.jp/docs/law-data-basic/419a603-xml-schema-for-japanese-law/ -->
<!-- fetched: 2026-03-25T08:08:59Z -->

[法令データ ドキュメンテーション（α版）](/docs/)
法令標準XMLスキーマ
# 法令標準XMLスキーマ
法令XMLは、「法令標準XMLスキーマ」と呼ばれるXMLスキーマ（XSD）に基づいて作成されています。法令標準XMLスキーマは下記のURLからダウンロードすることができます。
<a href="https://laws.e-gov.go.jp/file/XMLSchemaForJapaneseLaw_v3.xsd" target="_blank" rel="noreferrer">https://laws.e-gov.go.jp/file/XMLSchemaForJapaneseLaw_v3.xsd<span> (opens in a new tab)</span></a>
法令標準XMLスキーマには、法令XMLに登場するタグの種類や、そのタグの属性や子要素の種類が定義されています。
このページでは、それぞれのタグや属性の意味を解説します。
## `Law`及びトップレベルの要素<a href="#law及びトップレベルの要素" id="law及びトップレベルの要素"></a>
### `<Law>`<a href="#tag-Law" id="tag-Law"></a>
法令XMLのルート要素です。法令の基本情報の属性を持っています。
**子要素**: `<LawNum>` | `<LawBody>`
**属性**:
- `Era`(required): `"Meiji"` | `"Taisho"` | `"Showa"` | `"Heisei"` | `"Reiwa"`
  - 法令番号に含まれる元号です。
- `Year`(required): `positiveInteger`
  - 法令番号に含まれる年号です。
- `Num`(required): `positiveInteger`
  - 法令番号に含まれる番号です。
- `PromulgateMonth`: `positiveInteger`
  - 公布の月です。
- `PromulgateDay`: `positiveInteger`
  - 公布の日です。
- `LawType`(required): `"Constitution"` | `"Act"` | `"CabinetOrder"` | `"ImperialOrder"` | `"MinisterialOrdinance"` | `"Rule"` | `"Misc"`
  - 法令の種別です。
- `Lang`(required): `"ja"` | `"en"`
  - 多言語対応を想定した属性です。e-Gov法令検索では`"ja"`が使用されています。
### `<LawNum>`<a href="#tag-LawNum" id="tag-LawNum"></a>
法令番号を表す要素です。
**子要素**: `string`
### `<LawBody>`<a href="#tag-LawBody" id="tag-LawBody"></a>
法令本体を表す要素です。
**子要素**: `<LawTitle>` | `<EnactStatement>` | `<TOC>` | `<Preamble>` | `<MainProvision>` | `<SupplProvision>` | `<AppdxTable>` | `<AppdxNote>` | `<AppdxStyle>` | `<Appdx>` | `<AppdxFig>` | `<AppdxFormat>`
**属性**:
- `Subject`: `string`
  - 件名を表します。題名のない法令への対応を想定して設けられた要素ですが、現在は基本的に件名を題名（`<LawTitle>`）に登録している法令がほとんどです。
## 書き出しの要素<a href="#書き出しの要素" id="書き出しの要素"></a>
### `<LawTitle>`<a href="#tag-LawTitle" id="tag-LawTitle"></a>
法令の題名（法令名）を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
**属性**:
- `Kana`: `string`
  - 法令名の読み（ひらがな）です。
- `Abbrev`: `string`
  - 法令の略称です。複数ある場合は`","`で区切って入力されます。
- `AbbrevKana`: `string`
  - 法令の略称の読み（ひらがな）です。複数ある場合は`","`で区切って入力されます。
### `<EnactStatement>`<a href="#tag-EnactStatement" id="tag-EnactStatement"></a>
制定文を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Preamble>`<a href="#tag-Preamble" id="tag-Preamble"></a>
前文を表す要素です。
**子要素**: `<Paragraph>`
## 目次<a href="#目次" id="目次"></a>
### `<TOC>`<a href="#tag-TOC" id="tag-TOC"></a>
目次を表す要素です。
**子要素**: `<TOCLabel>` | `<TOCPreambleLabel>` | `<TOCPart>` | `<TOCChapter>` | `<TOCSection>` | `<TOCArticle>` | `<TOCSupplProvision>` | `<TOCAppdxTableLabel>`
### `<TOCLabel>`<a href="#tag-TOCLabel" id="tag-TOCLabel"></a>
目次のラベルを表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<TOCPreambleLabel>`<a href="#tag-TOCPreambleLabel" id="tag-TOCPreambleLabel"></a>
目次中の「前文」の項目を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<TOCPart>`<a href="#tag-TOCPart" id="tag-TOCPart"></a>
目次中の「編」の項目を表す要素です。
**子要素**: `<PartTitle>` | `<ArticleRange>` | `<TOCChapter>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
### `<TOCChapter>`<a href="#tag-TOCChapter" id="tag-TOCChapter"></a>
目次中の「章」の項目を表す要素です。
**子要素**: `<ChapterTitle>` | `<ArticleRange>` | `<TOCSection>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
### `<TOCSection>`<a href="#tag-TOCSection" id="tag-TOCSection"></a>
目次中の「節」の項目を表す要素です。
**子要素**: `<SectionTitle>` | `<ArticleRange>` | `<TOCSubsection>` | `<TOCDivision>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
### `<TOCSubsection>`<a href="#tag-TOCSubsection" id="tag-TOCSubsection"></a>
目次中の「款」の項目を表す要素です。
**子要素**: `<SubsectionTitle>` | `<ArticleRange>` | `<TOCDivision>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
### `<TOCDivision>`<a href="#tag-TOCDivision" id="tag-TOCDivision"></a>
目次中の「⽬」を表す要素です。
**子要素**: `<DivisionTitle>` | `<ArticleRange>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
### `<TOCArticle>`<a href="#tag-TOCArticle" id="tag-TOCArticle"></a>
目次中の「条」の項目を表す要素です。
**子要素**: `<ArticleTitle>` | `<ArticleCaption>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
### `<TOCSupplProvision>`<a href="#tag-TOCSupplProvision" id="tag-TOCSupplProvision"></a>
目次中の「附則」の項目を表す要素です。
**子要素**: `<SupplProvisionLabel>` | `<ArticleRange>` | `<TOCArticle>` | `<TOCChapter>`
### `<TOCAppdxTableLabel>`<a href="#tag-TOCAppdxTableLabel" id="tag-TOCAppdxTableLabel"></a>
目次中の「別表」の項目を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<ArticleRange>`<a href="#tag-ArticleRange" id="tag-ArticleRange"></a>
目次中の項目に付記される条範囲を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
## 本則及び附則<a href="#本則及び附則" id="本則及び附則"></a>
### `<MainProvision>`<a href="#tag-MainProvision" id="tag-MainProvision"></a>
本則を表す要素です。
**子要素**: `<Part>` | `<Chapter>` | `<Section>` | `<Article>` | `<Paragraph>`
**属性**:
- `Extract`: `boolean`
  - 抄録（一部を抜粋して収録）している場合は`true`を指定します。
### `<SupplProvision>`<a href="#tag-SupplProvision" id="tag-SupplProvision"></a>
附則を表す要素です。
**子要素**: `<SupplProvisionLabel>` | `<Chapter>` | `<Article>` | `<Paragraph>` | `<SupplProvisionAppdxTable>` | `<SupplProvisionAppdxStyle>` | `<SupplProvisionAppdx>`
**属性**:
- `Type`: `"New"` | `"Amend"`
  - 制定時の場合は`"New"`、改正時の場合は`"Amend"`を指定します。
- `AmendLawNum`: `string`
  - 改正附則が属する改正法令の番号を指定します。
- `Extract`: `boolean`
  - 抄録（一部を抜粋して収録）している場合は`true`を指定します。
### `<SupplProvisionLabel>`<a href="#tag-SupplProvisionLabel" id="tag-SupplProvisionLabel"></a>
附則のラベルを表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
## 章など<a href="#章など" id="章など"></a>
### `<Part>`<a href="#tag-Part" id="tag-Part"></a>
「編」を表す要素です。
**子要素**: `<PartTitle>` | `<Article>` | `<Chapter>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<PartTitle>`<a href="#tag-PartTitle" id="tag-PartTitle"></a>
「編」の題名を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Chapter>`<a href="#tag-Chapter" id="tag-Chapter"></a>
「章」を表す要素です。
**子要素**: `<ChapterTitle>` | `<Article>` | `<Section>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<ChapterTitle>`<a href="#tag-ChapterTitle" id="tag-ChapterTitle"></a>
「章名」を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Section>`<a href="#tag-Section" id="tag-Section"></a>
「節」を表す要素です。
**子要素**: `<SectionTitle>` | `<Article>` | `<Subsection>` | `<Division>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<SectionTitle>`<a href="#tag-SectionTitle" id="tag-SectionTitle"></a>
「節名」を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Subsection>`<a href="#tag-Subsection" id="tag-Subsection"></a>
「款」を表す要素です。
**子要素**: `<SubsectionTitle>` | `<Article>` | `<Division>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<SubsectionTitle>`<a href="#tag-SubsectionTitle" id="tag-SubsectionTitle"></a>
「款名」を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Division>`<a href="#tag-Division" id="tag-Division"></a>
「目」を表す要素です。
**子要素**: `<DivisionTitle>` | `<Article>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<DivisionTitle>`<a href="#tag-DivisionTitle" id="tag-DivisionTitle"></a>
「目名」を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
## 条<a href="#条" id="条"></a>
### `<Article>`<a href="#tag-Article" id="tag-Article"></a>
「条」を表す要素です。
**子要素**: `<ArticleCaption>` | `<ArticleTitle>` | `<Paragraph>` | `<SupplNote>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<ArticleTitle>`<a href="#tag-ArticleTitle" id="tag-ArticleTitle"></a>
「条名」を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<ArticleCaption>`<a href="#tag-ArticleCaption" id="tag-ArticleCaption"></a>
条見出しを表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
**属性**:
- `CommonCaption`: `boolean`
  - 「共通見出し」である場合は`true`を指定します。
## 項・号・号の細分<a href="#項号号の細分" id="項号号の細分"></a>
### `<Paragraph>`<a href="#tag-Paragraph" id="tag-Paragraph"></a>
「項」を表す要素です。
**子要素**: `<ParagraphCaption>` | `<ParagraphNum>` | `<ParagraphSentence>` | `<AmendProvision>` | `<Class>` | `<TableStruct>` | `<FigStruct>` | `<StyleStruct>` | `<Item>` | `<List>`
**属性**:
- `Num`(required): `positiveInteger`
  - 番号です。
- `OldStyle`(default: `false`): `boolean`
  - 項の初字位置が古い形式である場合は`true`を指定します。
- `OldNum`(default: `false`): `boolean`
  - 項番号のない古い形式である場合は`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<ParagraphCaption>`<a href="#tag-ParagraphCaption" id="tag-ParagraphCaption"></a>
項見出しを表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
**属性**:
- `CommonCaption`: `boolean`
  - 「共通見出し」である場合は`true`を指定します。
### `<ParagraphNum>`<a href="#tag-ParagraphNum" id="tag-ParagraphNum"></a>
項番号を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<ParagraphSentence>`<a href="#tag-ParagraphSentence" id="tag-ParagraphSentence"></a>
項の文章（柱書）を表す要素です。
**子要素**: `<Sentence>`
### `<Item>`<a href="#tag-Item" id="tag-Item"></a>
「号」を表す要素です。
**子要素**: `<ItemTitle>` | `<ItemSentence>` | `<Subitem1>` | `<TableStruct>` | `<FigStruct>` | `<StyleStruct>` | `<List>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<ItemTitle>`<a href="#tag-ItemTitle" id="tag-ItemTitle"></a>
「号名」を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<ItemSentence>`<a href="#tag-ItemSentence" id="tag-ItemSentence"></a>
号の文章（柱書）を表す要素です。
**子要素**: `<Sentence>` | `<Column>` | `<Table>`
### `<Subitem1>`<a href="#tag-Subitem1" id="tag-Subitem1"></a>
「号の細分」（1階層目）を表す要素です。
**子要素**: `<Subitem1Title>` | `<Subitem1Sentence>` | `<Subitem2>` | `<TableStruct>` | `<FigStruct>` | `<StyleStruct>` | `<List>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<Subitem1Title>`<a href="#tag-Subitem1Title" id="tag-Subitem1Title"></a>
「号の細分名」（1階層目）を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Subitem1Sentence>`<a href="#tag-Subitem1Sentence" id="tag-Subitem1Sentence"></a>
号の細分（1階層目）の文章（柱書）を表す要素です。
**子要素**: `<Sentence>` | `<Column>` | `<Table>`
### `<Subitem2>`<a href="#tag-Subitem2" id="tag-Subitem2"></a>
「号の細分」（2階層目）を表す要素です。
**子要素**: `<Subitem2Title>` | `<Subitem2Sentence>` | `<Subitem3>` | `<TableStruct>` | `<FigStruct>` | `<StyleStruct>` | `<List>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<Subitem2Title>`<a href="#tag-Subitem2Title" id="tag-Subitem2Title"></a>
「号の細分名」（2階層目）を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Subitem2Sentence>`<a href="#tag-Subitem2Sentence" id="tag-Subitem2Sentence"></a>
号の細分（2階層目）の文章（柱書）を表す要素です。
**子要素**: `<Sentence>` | `<Column>` | `<Table>`
### `<Subitem3>`<a href="#tag-Subitem3" id="tag-Subitem3"></a>
「号の細分」（3階層目）を表す要素です。
**子要素**: `<Subitem3Title>` | `<Subitem3Sentence>` | `<Subitem4>` | `<TableStruct>` | `<FigStruct>` | `<StyleStruct>` | `<List>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<Subitem3Title>`<a href="#tag-Subitem3Title" id="tag-Subitem3Title"></a>
「号の細分名」（3階層目）を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Subitem3Sentence>`<a href="#tag-Subitem3Sentence" id="tag-Subitem3Sentence"></a>
号の細分（3階層目）の文章（柱書）を表す要素です。
**子要素**: `<Sentence>` | `<Column>` | `<Table>`
### `<Subitem4>`<a href="#tag-Subitem4" id="tag-Subitem4"></a>
「号の細分」（4階層目）を表す要素です。
**子要素**: `<Subitem4Title>` | `<Subitem4Sentence>` | `<Subitem5>` | `<TableStruct>` | `<FigStruct>` | `<StyleStruct>` | `<List>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<Subitem4Title>`<a href="#tag-Subitem4Title" id="tag-Subitem4Title"></a>
「号の細分名」（4階層目）を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Subitem4Sentence>`<a href="#tag-Subitem4Sentence" id="tag-Subitem4Sentence"></a>
号の細分（4階層目）の文章（柱書）を表す要素です。
**子要素**: `<Sentence>` | `<Column>` | `<Table>`
### `<Subitem5>`<a href="#tag-Subitem5" id="tag-Subitem5"></a>
「号の細分」（5階層目）を表す要素です。
**子要素**: `<Subitem5Title>` | `<Subitem5Sentence>` | `<Subitem6>` | `<TableStruct>` | `<FigStruct>` | `<StyleStruct>` | `<List>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<Subitem5Title>`<a href="#tag-Subitem5Title" id="tag-Subitem5Title"></a>
「号の細分名」（5階層目）を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Subitem5Sentence>`<a href="#tag-Subitem5Sentence" id="tag-Subitem5Sentence"></a>
号の細分（5階層目）の文章（柱書）を表す要素です。
**子要素**: `<Sentence>` | `<Column>` | `<Table>`
### `<Subitem6>`<a href="#tag-Subitem6" id="tag-Subitem6"></a>
「号の細分」（6階層目）を表す要素です。
**子要素**: `<Subitem6Title>` | `<Subitem6Sentence>` | `<Subitem7>` | `<TableStruct>` | `<FigStruct>` | `<StyleStruct>` | `<List>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<Subitem6Title>`<a href="#tag-Subitem6Title" id="tag-Subitem6Title"></a>
「号の細分名」（6階層目）を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Subitem6Sentence>`<a href="#tag-Subitem6Sentence" id="tag-Subitem6Sentence"></a>
号の細分（6階層目）の文章（柱書）を表す要素です。
**子要素**: `<Sentence>` | `<Column>` | `<Table>`
### `<Subitem7>`<a href="#tag-Subitem7" id="tag-Subitem7"></a>
「号の細分」（7階層目）を表す要素です。
**子要素**: `<Subitem7Title>` | `<Subitem7Sentence>` | `<Subitem8>` | `<TableStruct>` | `<FigStruct>` | `<StyleStruct>` | `<List>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<Subitem7Title>`<a href="#tag-Subitem7Title" id="tag-Subitem7Title"></a>
「号の細分名」（7階層目）を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Subitem7Sentence>`<a href="#tag-Subitem7Sentence" id="tag-Subitem7Sentence"></a>
号の細分（7階層目）の文章（柱書）を表す要素です。
**子要素**: `<Sentence>` | `<Column>` | `<Table>`
### `<Subitem8>`<a href="#tag-Subitem8" id="tag-Subitem8"></a>
「号の細分」（8階層目）を表す要素です。
**子要素**: `<Subitem8Title>` | `<Subitem8Sentence>` | `<Subitem9>` | `<TableStruct>` | `<FigStruct>` | `<StyleStruct>` | `<List>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<Subitem8Title>`<a href="#tag-Subitem8Title" id="tag-Subitem8Title"></a>
「号の細分名」（8階層目）を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Subitem8Sentence>`<a href="#tag-Subitem8Sentence" id="tag-Subitem8Sentence"></a>
号の細分（8階層目）の文章（柱書）を表す要素です。
**子要素**: `<Sentence>` | `<Column>` | `<Table>`
### `<Subitem9>`<a href="#tag-Subitem9" id="tag-Subitem9"></a>
「号の細分」（9階層目）を表す要素です。
**子要素**: `<Subitem9Title>` | `<Subitem9Sentence>` | `<Subitem10>` | `<TableStruct>` | `<FigStruct>` | `<StyleStruct>` | `<List>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<Subitem9Title>`<a href="#tag-Subitem9Title" id="tag-Subitem9Title"></a>
「号の細分名」（9階層目）を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Subitem9Sentence>`<a href="#tag-Subitem9Sentence" id="tag-Subitem9Sentence"></a>
号の細分（9階層目）の文章（柱書）を表す要素です。
**子要素**: `<Sentence>` | `<Column>` | `<Table>`
### `<Subitem10>`<a href="#tag-Subitem10" id="tag-Subitem10"></a>
「号の細分」（10階層目）を表す要素です。
**子要素**: `<Subitem10Title>` | `<Subitem10Sentence>` | `<TableStruct>` | `<FigStruct>` | `<StyleStruct>` | `<List>`
**属性**:
- `Num`(required): `string`
  - 番号です。
- `Delete`(default: `false`): `boolean`
  - 項目が効力を有さないものとして削除扱いとされている場合に`true`を指定します。
- `Hide`(default: `false`): `boolean`
  - 項目が非表示である場合に`true`を指定します。
### `<Subitem10Title>`<a href="#tag-Subitem10Title" id="tag-Subitem10Title"></a>
「号の細分名」（10階層目）を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Subitem10Sentence>`<a href="#tag-Subitem10Sentence" id="tag-Subitem10Sentence"></a>
号の細分（10階層目）の文章（柱書）を表す要素です。
**子要素**: `<Sentence>` | `<Column>` | `<Table>`
## 条文<a href="#条文" id="条文"></a>
### `<Sentence>`<a href="#tag-Sentence" id="tag-Sentence"></a>
条文を表す要素です。「前段」「後段」「本文」「ただし書」などの部分ごとに要素を分けます。
**子要素**: `<Line>` | `<QuoteStruct>` | `<ArithFormula>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
**属性**:
- `Num`: `positiveInteger`
  - 番号です。
- `Function`: `"main"` | `"proviso"`
  - 「本文」の場合は`"main"`、「ただし書」の場合は`"proviso"`を指定します。
- `Indent`: `"Paragraph"` | `"Item"` | `"Subitem1"` | `"Subitem2"` | `"Subitem3"` | `"Subitem4"` | `"Subitem5"` | `"Subitem6"` | `"Subitem7"` | `"Subitem8"` | `"Subitem9"` | `"Subitem10"`
  - 表内での表記でインデント調整が必要な場合に指定します。
- `WritingMode`(default: `vertical`): `"vertical"` | `"horizontal"`
  - 行送り方向です。縦書きの場合は`"vertical"`、横書きの場合は`"horizontal"`を指定します。
### `<Column>`<a href="#tag-Column" id="tag-Column"></a>
条文が空白で区切られている場合に、それぞれの部分を表す要素です。
**子要素**: `<Sentence>`
**属性**:
- `Num`: `positiveInteger`
  - 番号です。
- `LineBreak`(default: `false`): `boolean`
  - 改行がある場合は`"true"`を指定します。
- `Align`: `"left"` | `"center"` | `"right"` | `"justify"`
  - 文字揃えの位置を指定します。
## 列記<a href="#列記" id="列記"></a>
### `<List>`<a href="#tag-List" id="tag-List"></a>
列記を表す要素です。
**子要素**: `<ListSentence>` | `<Sublist1>`
### `<ListSentence>`<a href="#tag-ListSentence" id="tag-ListSentence"></a>
列記の条文を表す要素です。
**子要素**: `<Sentence>` | `<Column>`
### `<Sublist1>`<a href="#tag-Sublist1" id="tag-Sublist1"></a>
列記の細分（1段階目）を表す要素です。
**子要素**: `<Sublist1Sentence>` | `<Sublist2>`
### `<Sublist1Sentence>`<a href="#tag-Sublist1Sentence" id="tag-Sublist1Sentence"></a>
列記の細分（1段階目）の条文を表す要素です。
**子要素**: `<Sentence>` | `<Column>`
### `<Sublist2>`<a href="#tag-Sublist2" id="tag-Sublist2"></a>
列記の細分（2段階目）を表す要素です。
**子要素**: `<Sublist2Sentence>` | `<Sublist3>`
### `<Sublist2Sentence>`<a href="#tag-Sublist2Sentence" id="tag-Sublist2Sentence"></a>
列記の細分（2段階目）の条文を表す要素です。
**子要素**: `<Sentence>` | `<Column>`
### `<Sublist3>`<a href="#tag-Sublist3" id="tag-Sublist3"></a>
列記の細分（3段階目）を表す要素です。
**子要素**: `<Sublist3Sentence>`
### `<Sublist3Sentence>`<a href="#tag-Sublist3Sentence" id="tag-Sublist3Sentence"></a>
列記の細分（3段階目）の条文を表す要素です。
**子要素**: `<Sentence>` | `<Column>`
## 類<a href="#類" id="類"></a>
### `<Class>`<a href="#tag-Class" id="tag-Class"></a>
「類」を表す要素です。「類」は、廃止された家事裁判法（昭和二十二年法律第百五十二号）で用いられていた構造です。
**子要素**: `<ClassTitle>` | `<ClassSentence>` | `<Item>`
**属性**:
- `Num`(required): `string`
### `<ClassTitle>`<a href="#tag-ClassTitle" id="tag-ClassTitle"></a>
「類名」を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<ClassSentence>`<a href="#tag-ClassSentence" id="tag-ClassSentence"></a>
「類文」を表す要素です。
**子要素**: `<Sentence>` | `<Column>` | `<Table>`
## インライン要素<a href="#インライン要素" id="インライン要素"></a>
### `<QuoteStruct>`<a href="#tag-QuoteStruct" id="tag-QuoteStruct"></a>
改行を含む構造の引用を表す要素です。例えば、「図として捉える改正」などで使用されます。
**子要素**: `any`
### `<Ruby>`<a href="#tag-Ruby" id="tag-Ruby"></a>
ルビ付きの文字列を表す要素です。
**子要素**: `<Rt>` | `string`
### `<Rt>`<a href="#tag-Rt" id="tag-Rt"></a>
ルビの部分を表す要素です。
**子要素**: `string`
### `<Line>`<a href="#tag-Line" id="tag-Line"></a>
傍線を表す要素です。
**子要素**: `<QuoteStruct>` | `<ArithFormula>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
**属性**:
- `Style`(default: `solid`): `"dotted"` | `"double"` | `"none"` | `"solid"`
  - 傍線のスタイルを表す要素です。
### `<Sup>`<a href="#tag-Sup" id="tag-Sup"></a>
上付き文字を表す要素です。
**子要素**: `string`
### `<Sub>`<a href="#tag-Sub" id="tag-Sub"></a>
下付き文字を表す要素です。
**子要素**: `string`
## 表<a href="#表" id="表"></a>
### `<TableStruct>`<a href="#tag-TableStruct" id="tag-TableStruct"></a>
表項目を表す要素です。
**子要素**: `<TableStructTitle>` | `<Remarks>` | `<Table>`
### `<TableStructTitle>`<a href="#tag-TableStructTitle" id="tag-TableStructTitle"></a>
表項目名を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
**属性**:
- `WritingMode`(default: `vertical`): `"vertical"` | `"horizontal"`
  - 行送り方向です。縦書きの場合は`"vertical"`、横書きの場合は`"horizontal"`を指定します。
### `<Table>`<a href="#tag-Table" id="tag-Table"></a>
表を表す要素です。
**子要素**: `<TableHeaderRow>` | `<TableRow>`
**属性**:
- `WritingMode`(default: `vertical`): `"vertical"` | `"horizontal"`
  - 行送り方向です。縦書きの場合は`"vertical"`、横書きの場合は`"horizontal"`を指定します。
### `<TableRow>`<a href="#tag-TableRow" id="tag-TableRow"></a>
表の行（項）を表す要素です。
**子要素**: `<TableColumn>`
### `<TableHeaderRow>`<a href="#tag-TableHeaderRow" id="tag-TableHeaderRow"></a>
表の欄名の行（項）を表す要素です。
**子要素**: `<TableHeaderColumn>`
### `<TableHeaderColumn>`<a href="#tag-TableHeaderColumn" id="tag-TableHeaderColumn"></a>
表の欄名の列（欄）を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<TableColumn>`<a href="#tag-TableColumn" id="tag-TableColumn"></a>
表の列（欄）を表す要素です。
**子要素**: `<Part>` | `<Chapter>` | `<Section>` | `<Subsection>` | `<Division>` | `<Article>` | `<Paragraph>` | `<Item>` | `<Subitem1>` | `<Subitem2>` | `<Subitem3>` | `<Subitem4>` | `<Subitem5>` | `<Subitem6>` | `<Subitem7>` | `<Subitem8>` | `<Subitem9>` | `<Subitem10>` | `<FigStruct>` | `<Remarks>` | `<Sentence>` | `<Column>`
**属性**:
- `BorderTop`(default: `solid`): `"solid"` | `"none"` | `"dotted"` | `"double"`
  - 上の枠線スタイルを表す要素です。
- `BorderBottom`(default: `solid`): `"solid"` | `"none"` | `"dotted"` | `"double"`
  - 下の枠線スタイルを表す要素です。
- `BorderLeft`(default: `solid`): `"solid"` | `"none"` | `"dotted"` | `"double"`
  - 左の枠線スタイルを表す要素です。
- `BorderRight`(default: `solid`): `"solid"` | `"none"` | `"dotted"` | `"double"`
  - 右の枠線スタイルを表す要素です。
- `rowspan`: `string`
  - 行（項）の方向の結合数を指定します。
- `colspan`: `string`
  - 行（項）の方向の結合数を指定します。
- `Align`: `"left"` | `"center"` | `"right"` | `"justify"`
  - 行（項）の方向の位置を指定します。
- `Valign`: `"top"` | `"middle"` | `"bottom"`
  - 列（欄）の方向の位置を指定します。
## 図<a href="#図" id="図"></a>
### `<FigStruct>`<a href="#tag-FigStruct" id="tag-FigStruct"></a>
図項目を表す要素です。
**子要素**: `<FigStructTitle>` | `<Remarks>` | `<Fig>`
### `<FigStructTitle>`<a href="#tag-FigStructTitle" id="tag-FigStructTitle"></a>
図項目名を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Fig>`<a href="#tag-Fig" id="tag-Fig"></a>
図を表す要素です。
**子要素**:
**属性**:
- `src`(required): `string`
  - 図の参照URIを指定します。
## 算式<a href="#算式" id="算式"></a>
### `<ArithFormula>`<a href="#tag-ArithFormula" id="tag-ArithFormula"></a>
算式を表す要素です。
**子要素**: `string`
**属性**:
- `Num`: `positiveInteger`
  - 番号です。
## 改正規定<a href="#改正規定" id="改正規定"></a>
### `<AmendProvision>`<a href="#tag-AmendProvision" id="tag-AmendProvision"></a>
改正規定を表す要素です。
**子要素**: `<AmendProvisionSentence>` | `<NewProvision>`
### `<AmendProvisionSentence>`<a href="#tag-AmendProvisionSentence" id="tag-AmendProvisionSentence"></a>
改正規定文を表す要素です。
**子要素**: `<Sentence>`
### `<NewProvision>`<a href="#tag-NewProvision" id="tag-NewProvision"></a>
改正規定中の新規条文を表す要素です。
**子要素**: `<LawTitle>` | `<Preamble>` | `<TOC>` | `<Part>` | `<PartTitle>` | `<Chapter>` | `<ChapterTitle>` | `<Section>` | `<SectionTitle>` | `<Subsection>` | `<SubsectionTitle>` | `<Division>` | `<DivisionTitle>` | `<Article>` | `<SupplNote>` | `<Paragraph>` | `<Item>` | `<Subitem1>` | `<Subitem2>` | `<Subitem3>` | `<Subitem4>` | `<Subitem5>` | `<Subitem6>` | `<Subitem7>` | `<Subitem8>` | `<Subitem9>` | `<Subitem10>` | `<List>` | `<Sentence>` | `<AmendProvision>` | `<AppdxTable>` | `<AppdxNote>` | `<AppdxStyle>` | `<Appdx>` | `<AppdxFig>` | `<AppdxFormat>` | `<SupplProvisionAppdxStyle>` | `<SupplProvisionAppdxTable>` | `<SupplProvisionAppdx>` | `<TableStruct>` | `<TableRow>` | `<TableColumn>` | `<FigStruct>` | `<NoteStruct>` | `<StyleStruct>` | `<FormatStruct>` | `<Remarks>` | `<LawBody>`
## 様式等<a href="#様式等" id="様式等"></a>
### `<NoteStruct>`<a href="#tag-NoteStruct" id="tag-NoteStruct"></a>
記項目を表す要素です。
**子要素**: `<NoteStructTitle>` | `<Remarks>` | `<Note>`
### `<NoteStructTitle>`<a href="#tag-NoteStructTitle" id="tag-NoteStructTitle"></a>
記項目名を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Note>`<a href="#tag-Note" id="tag-Note"></a>
「記」を表す要素です。
**子要素**: `any`
### `<StyleStruct>`<a href="#tag-StyleStruct" id="tag-StyleStruct"></a>
様式項目を表す要素です。
**子要素**: `<StyleStructTitle>` | `<Remarks>` | `<Style>`
### `<StyleStructTitle>`<a href="#tag-StyleStructTitle" id="tag-StyleStructTitle"></a>
様式項目名を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Style>`<a href="#tag-Style" id="tag-Style"></a>
様式を表す要素です。
**子要素**: `any`
### `<FormatStruct>`<a href="#tag-FormatStruct" id="tag-FormatStruct"></a>
書式項目を表す要素です。
**子要素**: `<FormatStructTitle>` | `<Remarks>` | `<Format>`
### `<FormatStructTitle>`<a href="#tag-FormatStructTitle" id="tag-FormatStructTitle"></a>
書式項目名を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<Format>`<a href="#tag-Format" id="tag-Format"></a>
書式を表す要素です。
**子要素**: `any`
## 別表等<a href="#別表等" id="別表等"></a>
### `<AppdxTable>`<a href="#tag-AppdxTable" id="tag-AppdxTable"></a>
別表を表す要素です。
**子要素**: `<AppdxTableTitle>` | `<RelatedArticleNum>` | `<TableStruct>` | `<Item>` | `<Remarks>`
**属性**:
- `Num`: `positiveInteger`
  - 番号です。
### `<AppdxTableTitle>`<a href="#tag-AppdxTableTitle" id="tag-AppdxTableTitle"></a>
別表名を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
**属性**:
- `WritingMode`(default: `vertical`): `"vertical"` | `"horizontal"`
  - 行送り方向です。縦書きの場合は`"vertical"`、横書きの場合は`"horizontal"`を指定します。
### `<AppdxNote>`<a href="#tag-AppdxNote" id="tag-AppdxNote"></a>
別記を表す要素です。
**子要素**: `<AppdxNoteTitle>` | `<RelatedArticleNum>` | `<NoteStruct>` | `<FigStruct>` | `<TableStruct>` | `<Remarks>`
**属性**:
- `Num`: `positiveInteger`
  - 番号です。
### `<AppdxNoteTitle>`<a href="#tag-AppdxNoteTitle" id="tag-AppdxNoteTitle"></a>
別記名を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
**属性**:
- `WritingMode`(default: `vertical`): `"vertical"` | `"horizontal"`
  - 行送り方向です。縦書きの場合は`"vertical"`、横書きの場合は`"horizontal"`を指定します。
### `<AppdxStyle>`<a href="#tag-AppdxStyle" id="tag-AppdxStyle"></a>
別記様式を表す要素です。
**子要素**: `<AppdxStyleTitle>` | `<RelatedArticleNum>` | `<StyleStruct>` | `<Remarks>`
**属性**:
- `Num`: `positiveInteger`
  - 番号です。
### `<AppdxStyleTitle>`<a href="#tag-AppdxStyleTitle" id="tag-AppdxStyleTitle"></a>
別記様式名を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
**属性**:
- `WritingMode`(default: `vertical`): `"vertical"` | `"horizontal"`
  - 行送り方向です。縦書きの場合は`"vertical"`、横書きの場合は`"horizontal"`を指定します。
### `<AppdxFormat>`<a href="#tag-AppdxFormat" id="tag-AppdxFormat"></a>
別記書式を表す要素です。
**子要素**: `<AppdxFormatTitle>` | `<RelatedArticleNum>` | `<FormatStruct>` | `<Remarks>`
**属性**:
- `Num`: `positiveInteger`
  - 番号です。
### `<AppdxFormatTitle>`<a href="#tag-AppdxFormatTitle" id="tag-AppdxFormatTitle"></a>
別記書式名を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
**属性**:
- `WritingMode`(default: `vertical`): `"vertical"` | `"horizontal"`
  - 行送り方向です。縦書きの場合は`"vertical"`、横書きの場合は`"horizontal"`を指定します。
### `<Appdx>`<a href="#tag-Appdx" id="tag-Appdx"></a>
付録を表す要素です。
**子要素**: `<ArithFormulaNum>` | `<RelatedArticleNum>` | `<ArithFormula>` | `<Remarks>`
### `<ArithFormulaNum>`<a href="#tag-ArithFormulaNum" id="tag-ArithFormulaNum"></a>
算式番号を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
### `<AppdxFig>`<a href="#tag-AppdxFig" id="tag-AppdxFig"></a>
別図を表す要素です。
**子要素**: `<AppdxFigTitle>` | `<RelatedArticleNum>` | `<FigStruct>` | `<TableStruct>`
**属性**:
- `Num`: `positiveInteger`
  - 番号です。
### `<AppdxFigTitle>`<a href="#tag-AppdxFigTitle" id="tag-AppdxFigTitle"></a>
別図名を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
**属性**:
- `WritingMode`(default: `vertical`): `"vertical"` | `"horizontal"`
  - 行送り方向です。縦書きの場合は`"vertical"`、横書きの場合は`"horizontal"`を指定します。
### `<RelatedArticleNum>`<a href="#tag-RelatedArticleNum" id="tag-RelatedArticleNum"></a>
関係条文番号を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
## 附則別表等<a href="#附則別表等" id="附則別表等"></a>
### `<SupplProvisionAppdxTable>`<a href="#tag-SupplProvisionAppdxTable" id="tag-SupplProvisionAppdxTable"></a>
附則別表を表す要素です。
**子要素**: `<SupplProvisionAppdxTableTitle>` | `<RelatedArticleNum>` | `<TableStruct>`
**属性**:
- `Num`: `positiveInteger`
  - 番号です。
### `<SupplProvisionAppdxTableTitle>`<a href="#tag-SupplProvisionAppdxTableTitle" id="tag-SupplProvisionAppdxTableTitle"></a>
附則別表名を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
**属性**:
- `WritingMode`(default: `vertical`): `"vertical"` | `"horizontal"`
  - 行送り方向です。縦書きの場合は`"vertical"`、横書きの場合は`"horizontal"`を指定します。
### `<SupplProvisionAppdxStyle>`<a href="#tag-SupplProvisionAppdxStyle" id="tag-SupplProvisionAppdxStyle"></a>
附則様式を表す要素です。
**子要素**: `<SupplProvisionAppdxStyleTitle>` | `<RelatedArticleNum>` | `<StyleStruct>`
**属性**:
- `Num`: `positiveInteger`
  - 番号です。
### `<SupplProvisionAppdxStyleTitle>`<a href="#tag-SupplProvisionAppdxStyleTitle" id="tag-SupplProvisionAppdxStyleTitle"></a>
附則様式名を表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
**属性**:
- `WritingMode`(default: `vertical`): `"vertical"` | `"horizontal"`
  - 行送り方向です。縦書きの場合は`"vertical"`、横書きの場合は`"horizontal"`を指定します。
### `<SupplProvisionAppdx>`<a href="#tag-SupplProvisionAppdx" id="tag-SupplProvisionAppdx"></a>
附則付録を表す要素です。
**子要素**: `<ArithFormulaNum>` | `<RelatedArticleNum>` | `<ArithFormula>`
**属性**:
- `Num`: `positiveInteger`
  - 番号です。
## 備考・付記<a href="#備考付記" id="備考付記"></a>
### `<Remarks>`<a href="#tag-Remarks" id="tag-Remarks"></a>
備考を表す要素です。
**子要素**: `<RemarksLabel>` | `<Item>` | `<Sentence>`
### `<RemarksLabel>`<a href="#tag-RemarksLabel" id="tag-RemarksLabel"></a>
備考ラベルを表す要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
**属性**:
- `LineBreak`(default: `false`): `boolean`
  - 改行がある場合は`"true"`を指定します。
### `<SupplNote>`<a href="#tag-SupplNote" id="tag-SupplNote"></a>
付記を表す要素です。<a href="https://laws.e-gov.go.jp/document?lawid=335AC0000000105" rel="noopener noreferrer" target="_blank">道路交通法（昭和三十五年法律第百五号）</a>の`（罰則 〇〇〇〇）`のために設けられた要素です。
**子要素**: `<Line>` | `<Ruby>` | `<Sup>` | `<Sub>` | `string`
Light
------------------------------------------------------------------------
[法令データ ドキュメンテーション（α版）](/docs/)
- 現時点で本サイトは、正式化前の試験的な公開（α版）です。本サイトの資料は、今後内容をブラッシュアップの上、正式化することを目指しています。
- 本サイトの内容は、特に注記のない限り、政府の公式見解を表すものではなく、執筆者の見解を示すものにとどまります。法令分野の専門家以外の方にもわかりやすい記述となるように、表現や業務内容を簡略化等している部分があり、政府の公式見解や実際の業務とは差異がある部分がありますので、その点御注意下さい。
- 本サイトの内容は、必ずしも常にアップデートされているとは限らず、また、予告なく URL が変更されたり削除される場合があります。
- 本サイトに記載のサンプルコードのご利用は、ご自身の責任で行ってください。万が一サンプルコードの利用による不利益が生じた場合でも、一切の責任を負いかねますのでご了承ください。

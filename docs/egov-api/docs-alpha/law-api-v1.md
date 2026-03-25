<!-- source: https://laws.e-gov.go.jp/docs/law-data-basic/8529371-law-api-v1/ -->
<!-- fetched: 2026-03-25T08:08:59Z -->

[法令データ ドキュメンテーション（α版）](/docs/)
法令API（Version 1）
# 法令API（Version 1）の概要
法令API Version 1では、次の機能を提供しています。本稿では、それぞれの機能について概要を解説し、サンプルコードを紹介します。
- 法令名一覧取得API：公布済み現行法令の法令ID、名称、法令番号などの情報の一覧を提供します。
- 法令取得API：指定した現行法令の本文（法令XML）と図などの添付データを提供します。
- 条文内容取得API：法令の指定部分の条文（法令XML）と図などの添付データを提供します。
- 更新法令一覧取得API：指定した日付に更新された法令の一覧を提供します。
## 法令API Version 1のリクエスト・レスポンス<a href="#法令api-version-1のリクエストレスポンス" id="法令api-version-1のリクエストレスポンス"></a>
法令API Version 1は、HTTPを用いたWeb APIで、機能ごとに設けられたエンドポイントURLへのGETリクエストを受け取り、XML形式のレスポンスを行います。
レスポンスXMLはルート要素`<DataRoot>`を持ち、エラーの有無などの情報が`<Result>`要素に、返却データ本体が`<ApplData>`要素に格納されます。
（`…`は注記）
    <?xml version="1.0" encoding="UTF-8"?>
    <DataRoot>
      <Result>
        <Code>0</Code>
        <Message/>
      </Result>
      <ApplData>
        <LawId>…</LawId>
        <LawNum/>
        <LawFullText>
          <Law …>
            …
          </Law>
        </LawFullText>
      </ApplData>
    </DataRoot>
- `<DataRoot>`要素は法令APIのレスポンスXMLのルート要素です。
- `<Result>`要素は処理結果コードなどのを格納する要素です。
- `<Code>`要素は処理結果コードを表します。正常時は `0`、エラー時は `1` が格納されます。エンドポイントによりその他のコードが定義されている場合があります。
- `<Message>`要素は処理結果のメッセージを格納します。正常時は空です。
- `<ApplData>`要素は返却データ本体を格納する要素です。内容はエンドポイントにより異なります。システム例外発生時はタグが出力されません。
## 法令名一覧取得API<a href="#法令名一覧取得api" id="法令名一覧取得api"></a>
公布済み現行法令の法令ID、名称、法令番号などの情報の一覧を提供します。法令の種類（「全法令」、「憲法・法律」、「政令・勅令」又は「府省令・規則」）を指定して絞り込むこともできます。
### リクエストURI<a href="#リクエストuri" id="リクエストuri"></a>
    https://laws.e-gov.go.jp/api/1/lawlists/
- ``: 取得対象の法令種別です。下記の値を設定できます。
  - `1`: 全法令
  - `2`: 憲法・法律
  - `3`: 政令・勅令
  - `4`: 府省令・規則
### レスポンスXML（`ApplData`内の要素）<a href="#レスポンスxmlappldata内の要素" id="レスポンスxmlappldata内の要素"></a>
- `Category`: リクエストで指定した法令種別
- `LawNameListInfo`: 法令ごとの情報。複数回出現します。
- `LawNameListInfo/LawId`: 法令ID
- `LawNameListInfo/LawName`: 法令名
- `LawNameListInfo/LawNo`: 法令番号
- `LawNameListInfo/PromulgationDate`: 公布日（`yyyyMMdd`）
### 例<a href="#例" id="例"></a>
全法令の一覧を取得するURI：
    https://laws.e-gov.go.jp/api/1/lawlists/1
レスポンス（`…`は注記）：
    <?xml version="1.0" encoding="UTF-8"?>
    <DataRoot>
      <Result>
        <Code>0</Code>
        <Message/>
      </Result>
      <ApplData>
        <Category>1</Category>
        <LawNameListInfo>
          <LawId>321CONSTITUTION</LawId>
          <LawName>日本国憲法</LawName>
          <LawNo>昭和二十一年憲法</LawNo>
          <PromulgationDate>19461103</PromulgationDate>
        </LawNameListInfo>
        <LawNameListInfo>…</LawNameListInfo>
        <LawNameListInfo>…</LawNameListInfo>
        …
      </ApplData>
    </DataRoot>
### Try it out\!<a href="#try-it-out" id="try-it-out"></a>
サンプルコードの実行方法
⚠️
このサイトに記載のサンプルコードのご利用は、ご自身の責任で行ってください。万が一サンプルコードの利用による不利益が生じた場合でも、一切の責任を負いかねますのでご了承ください。
サンプルコードは、一例として、下記の手順で実行することができます。
1.  `test.html`というテキストファイルを作成し、下記の内容を入力して保存してください。
    <html><head><meta charset="utf-8"/><script>
    【この場所にコードを記述】
    </script></head></html>
1.  `test.html`をブラウザで開き、JavaScriptコンソールを開くと、出力が表示されます。JavaScriptコンソールは、Chrome/Edgeの場合は、`Ctrl+Shift+J` (Windows/Linux) または `Cmd+Opt+J` (Mac) で開くことができます。
全法令の一覧を表示するサンプルコードを示します。
法令APIから全法令の一覧を表示
    (async () => {
        // 法令APIから全法令の一覧を取得し、出力する
        const r = await fetch("https://laws.e-gov.go.jp/api/1/lawlists/1");
        const xml = await r.text();
        console.log(xml);
    })();
## 法令取得API<a href="#法令取得api" id="法令取得api"></a>
施行中の現行法令の全文（法令XML）を取得します。本文に図が含まれている場合は、図の画像を取得することもできます。
### リクエストURI<a href="#リクエストuri-1" id="リクエストuri-1"></a>
    https://laws.e-gov.go.jp/api/1/lawdata/
- ``: 取得対象の法令番号又は法令IDです。
### レスポンスXML（`ApplData`内の要素）<a href="#レスポンスxmlappldata内の要素-1" id="レスポンスxmlappldata内の要素-1"></a>
- `LawId`: リクエストで指定した法令ID
- `LawNum`: リクエストで指定した法令番号
- `LawFullText`: 法令の全文（法令XML）
- `ImageData`: 添付画像（添付画像を含むフォルダをZip圧縮したバイナリをBase64でエンコードしたもの。当該フォルダは法令XML中では`./pict/`フォルダとして参照されます）※法令の全文に画像を含む場合、タグを出力する。
### 例<a href="#例-1" id="例-1"></a>
行政手続法（平成五年法律第八十八号）の全文（法令XML）を取得するURI：
    https://laws.e-gov.go.jp/api/1/lawdata/405AC0000000088
レスポンス（`…`は注記）：
    <?xml version="1.0" encoding="UTF-8"?>
    <DataRoot>
      <Result>
        <Code>0</Code>
        <Message/>
      </Result>
      <ApplData>
        <LawId>405AC0000000088</LawId>
        <LawNum/>
        <LawFullText>
          <Law …>
            <LawNum>平成五年法律第八十八号</LawNum>
            <LawBody>
              <LawTitle …>行政手続法</LawTitle>
              …
            </LawBody>
          </Law>
        </LawFullText>
      </ApplData>
    </DataRoot>
### Try it out\!<a href="#try-it-out-1" id="try-it-out-1"></a>
サンプルコードの実行方法
⚠️
このサイトに記載のサンプルコードのご利用は、ご自身の責任で行ってください。万が一サンプルコードの利用による不利益が生じた場合でも、一切の責任を負いかねますのでご了承ください。
サンプルコードは、一例として、下記の手順で実行することができます。
1.  `test.html`というテキストファイルを作成し、下記の内容を入力して保存してください。
    <html><head><meta charset="utf-8"/><script>
    【この場所にコードを記述】
    </script></head></html>
1.  `test.html`をブラウザで開き、JavaScriptコンソールを開くと、出力が表示されます。JavaScriptコンソールは、Chrome/Edgeの場合は、`Ctrl+Shift+J` (Windows/Linux) または `Cmd+Opt+J` (Mac) で開くことができます。
行政手続法（平成五年法律第八十八号）の全文（法令XML）を表示するサンプルコードを示します。
法令APIから法令全文を表示
    (async () => {
        // 法令APIから行政手続法（平成五年法律第八十八号）の全文（法令XML）を取得し、出力する
        const r = await fetch("https://laws.e-gov.go.jp/api/1/lawdata/405AC0000000088");
        const xml = await r.text();
        console.log(xml);
    })();
国旗及び国歌に関する法律（平成十一年法律第百二十七号）の全文（法令XML）を取得し、`ImageData`に格納された画像の一つを表示するサンプルコードを示します。
⚠️
下記のサンプルコードでは、Zipの解凍のために外部パッケージ（`fflate`）を動的にインポートしますのでご注意ください。
法令APIから法令全文と画像を表示
    (async () => {
        // 法令APIから国旗及び国歌に関する法律（平成十一年法律第百二十七号）の全文（法令XML）を取得し、出力する
        const r = await fetch("https://laws.e-gov.go.jp/api/1/lawdata/411AC0000000127");
        const xml = await r.text();
        const parser = new DOMParser();
        const doc = parser.parseFromString(xml, "application/xml");
        console.log(doc);
        
        // ImageDataに格納されたBase64文字列をデコードし、得られたZipバイナリを解凍し、画像バイナリを抽出する
        const zipBase64 = doc.getElementsByTagName("ImageData")[0].textContent ?? "";
        const zipBuf = Uint8Array.from(atob(zipBase64), c => c.charCodeAt(0));
        const fflate = await import("https://cdn.skypack.dev/fflate@0.8.2?min");
        const picBuf = fflate.unzipSync(zipBuf)["H11HO127-002.jpg"];
     
        // 画像を表示
        const picDataUrl = `data:image/jpeg;base64,$`;
        console.log("%c ", `background-image: url($); background-size: 100% 100%; padding: 149px 225px;`);
    })();
## 条文内容取得API<a href="#条文内容取得api" id="条文内容取得api"></a>
指定した条件（法令番号又は法令 ID 並びに条、項又は別表）に合致する施行中の現行法令の内容を取得します。本文に図が含まれている場合は、図の画像を取得することもできます。
### リクエストURI<a href="#リクエストuri-2" id="リクエストuri-2"></a>
    https://laws.e-gov.go.jp/api/1/articles;lawNum=;lawId=;article=;paragraph=;appdxTable=
- ``、``: 取得対象の法令番号及び法令IDです。どちらかの指定が必要です。
- ``、``、``: 取得対象の条、項及び別表です。いずれかの指定が必要で、``と``は組み合わせて指定することができます。
### レスポンスXML（`ApplData`内の要素）<a href="#レスポンスxmlappldata内の要素-2" id="レスポンスxmlappldata内の要素-2"></a>
- `LawId`: リクエストで指定した法令ID
- `LawNum`: リクエストで指定した法令番号
- `Article`: リクエストで指定した条
- `Paragraph`: リクエストで指定した項
- `AppdxTable`: リクエストで指定した別表
- `LawContents`: 法令の該当条文（法令XML）
- `AppdxTableTitleLists`: 取得要求した条件（別表）に合致する別表名の候補リスト
- `AppdxTableTitleLists/AppdxTableTitle`: 取得要求した条件（別表）に合致する別表名の候補。複数回出現します。
- `ImageData`: 添付画像（添付画像を`pict`フォルダに格納してZip圧縮したバイナリをBase64でエンコードしたもの）※法令の該当条文に画像を含む場合、タグを出力する。
### 例<a href="#例-2" id="例-2"></a>
行政手続法（平成五年法律第八十八号）第一条の条文（法令XML）を取得するURI：
    https://laws.e-gov.go.jp/api/1/articles;lawId=405AC0000000088;article=1
レスポンス：
    <?xml version="1.0" encoding="UTF-8"?>
    <DataRoot>
      <Result>
        <Code>0</Code>
        <Message/>
      </Result>
      <ApplData>
        <LawId>405AC0000000088</LawId>
        <LawNum/>
        <Article>第一条</Article>
        <Paragraph/>
        <AppdxTable/>
        <LawContents>
          <Article Num="1">
            <ArticleCaption>（目的等）</ArticleCaption>
            <ArticleTitle>第一条</ArticleTitle>
            <Paragraph Num="1">
              <ParagraphNum/>
              <ParagraphSentence>
                <Sentence Num="1" WritingMode="vertical">この法律は、処分、行政指導及び届出に関する手続並びに命令等を定める手続に関し、共通する事項を定めることによって、行政運営における公正の確保と透明性（行政上の意思決定について、その内容及び過程が国民にとって明らかであることをいう。第四十六条において同じ。）の向上を図り、もって国民の権利利益の保護に資することを目的とする。</Sentence>
              </ParagraphSentence>
            </Paragraph>
            <Paragraph Num="2">
              <ParagraphNum>２</ParagraphNum>
              <ParagraphSentence>
                <Sentence Num="1" WritingMode="vertical">処分、行政指導及び届出に関する手続並びに命令等を定める手続に関しこの法律に規定する事項について、他の法律に特別の定めがある場合は、その定めるところによる。</Sentence>
              </ParagraphSentence>
            </Paragraph>
          </Article>
        </LawContents>
      </ApplData>
    </DataRoot>
     
### Try it out\!<a href="#try-it-out-2" id="try-it-out-2"></a>
サンプルコードの実行方法
⚠️
このサイトに記載のサンプルコードのご利用は、ご自身の責任で行ってください。万が一サンプルコードの利用による不利益が生じた場合でも、一切の責任を負いかねますのでご了承ください。
サンプルコードは、一例として、下記の手順で実行することができます。
1.  `test.html`というテキストファイルを作成し、下記の内容を入力して保存してください。
    <html><head><meta charset="utf-8"/><script>
    【この場所にコードを記述】
    </script></head></html>
1.  `test.html`をブラウザで開き、JavaScriptコンソールを開くと、出力が表示されます。JavaScriptコンソールは、Chrome/Edgeの場合は、`Ctrl+Shift+J` (Windows/Linux) または `Cmd+Opt+J` (Mac) で開くことができます。
行政手続法（平成五年法律第八十八号）第一条の条文（法令XML）を表示するサンプルコードを示します。
法令APIから条文を表示
    (async () => {
        // 法令APIから行政手続法（平成五年法律第八十八号）第一条の条文（法令XML）を取得し、出力する
        const r = await fetch("https://laws.e-gov.go.jp/api/1/articles;lawId=405AC0000000088;article=1");
        const xml = await r.text();
        console.log(xml);
    })();
## 更新法令一覧取得API<a href="#更新法令一覧取得api" id="更新法令一覧取得api"></a>
指定した日付に更新された法令の一覧を取得します。なお、取得できる項目は、e-Gov 法令検索の更新法令一覧画面から出力できる CSV ファイルと同様です。
### リクエストURI<a href="#リクエストuri-3" id="リクエストuri-3"></a>
    https://laws.e-gov.go.jp/api/1/updatelawlists/
- ``: 取得対象の日付（`yyyyMMdd`）※指定可能な年月日は 2020年11月24日以降です。また、未来の日付は指定できません。
### レスポンスXML（`ApplData`内の要素）<a href="#レスポンスxmlappldata内の要素-3" id="レスポンスxmlappldata内の要素-3"></a>
- `Date`: リクエストで指定した年月日
- `LawNameListInfo`: 法令ごとの情報。複数回出現します。
- `LawNameListInfo/LawTypeName`: 法令種別
- `LawNameListInfo/LawNo`: 法令番号
- `LawNameListInfo/LawName`: 法令名
- `LawNameListInfo/LawNameKana`: 法令名読み
- `LawNameListInfo/OldLawName`: 旧法令名
- `LawNameListInfo/PromulgationDate`: 公布年月日
- `LawNameListInfo/AmendName`: 改正法令名
- `LawNameListInfo/AmendNo`: 改正法令番号
- `LawNameListInfo/AmendPromulgationDate`: 改正法令公布日
- `LawNameListInfo/EnforcementDate`: 施行日
- `LawNameListInfo/EnforcementComment`: 施行日備考
- `LawNameListInfo/LawId`: 法令ID
- `LawNameListInfo/LawUrl`: 本文URL
- `LawNameListInfo/EnforcementFlg`: 未施行（施行済：`0`、未施行：`1`）
- `LawNameListInfo/AuthFlg`: 所管課確認中（確認済：`0`、確認中：`1`）
### 例<a href="#例-3" id="例-3"></a>
2023年2月1日に更新された法令データの一覧を取得するURI：
    https://laws.e-gov.go.jp/api/1/updatelawlists/20230201
レスポンス（`…`は注記）：
    <?xml version="1.0" encoding="UTF-8"?>
    <DataRoot>
      <Result>
        <Code>0</Code>
        <Message/>
      </Result>
      <ApplData>
        <Date>20230201</Date>
        <LawNameListInfo>
          <LawTypeName>政令</LawTypeName>
          <LawNo>昭和四十三年政令第十四号</LawNo>
          <LawName>液化石油ガスの保安の確保及び取引の適正化に関する法律施行令</LawName>
          <LawNameKana>えきかせきゆがすのほあんのかくほおよびとりひきのてきせいかにかんするほうりつせこうれい</LawNameKana>
          <OldLawName/>
          <PromulgationDate>19680207</PromulgationDate>
          <AmendName>液化石油ガスの保安の確保及び取引の適正化に関する法律施行令及び高圧ガス保安法施行令の一部を改正する政令</AmendName>
          <AmendNo>令和五年政令第七号</AmendNo>
          <AmendPromulgationDate>20230118</AmendPromulgationDate>
          <EnforcementDate>20230401</EnforcementDate>
          <EnforcementComment/>
          <LawId>343CO0000000014</LawId>
          <LawUrl>https://laws.e-gov.go.jp/document?lawid=343CO0000000014_20230401_505CO0000000007</LawUrl>
          <EnforcementFlg>1</EnforcementFlg>
          <AuthFlg>1</AuthFlg>
        </LawNameListInfo>
        …
      </ApplData>
    </DataRoot>
     
### Try it out\!<a href="#try-it-out-3" id="try-it-out-3"></a>
サンプルコードの実行方法
⚠️
このサイトに記載のサンプルコードのご利用は、ご自身の責任で行ってください。万が一サンプルコードの利用による不利益が生じた場合でも、一切の責任を負いかねますのでご了承ください。
サンプルコードは、一例として、下記の手順で実行することができます。
1.  `test.html`というテキストファイルを作成し、下記の内容を入力して保存してください。
    <html><head><meta charset="utf-8"/><script>
    【この場所にコードを記述】
    </script></head></html>
1.  `test.html`をブラウザで開き、JavaScriptコンソールを開くと、出力が表示されます。JavaScriptコンソールは、Chrome/Edgeの場合は、`Ctrl+Shift+J` (Windows/Linux) または `Cmd+Opt+J` (Mac) で開くことができます。
2023年2月1日に更新された法令データの一覧を表示するサンプルコードを示します。
法令APIから更新法令一覧を表示
    (async () => {
        // 法令APIから2023年2月1日に更新された法令データの一覧取得し、出力する
        const r = await fetch("https://laws.e-gov.go.jp/api/1/updatelawlists/20230201");
        const xml = await r.text();
        console.log(xml);
    })();
Light
------------------------------------------------------------------------
[法令データ ドキュメンテーション（α版）](/docs/)
- 現時点で本サイトは、正式化前の試験的な公開（α版）です。本サイトの資料は、今後内容をブラッシュアップの上、正式化することを目指しています。
- 本サイトの内容は、特に注記のない限り、政府の公式見解を表すものではなく、執筆者の見解を示すものにとどまります。法令分野の専門家以外の方にもわかりやすい記述となるように、表現や業務内容を簡略化等している部分があり、政府の公式見解や実際の業務とは差異がある部分がありますので、その点御注意下さい。
- 本サイトの内容は、必ずしも常にアップデートされているとは限らず、また、予告なく URL が変更されたり削除される場合があります。
- 本サイトに記載のサンプルコードのご利用は、ご自身の責任で行ってください。万が一サンプルコードの利用による不利益が生じた場合でも、一切の責任を負いかねますのでご了承ください。

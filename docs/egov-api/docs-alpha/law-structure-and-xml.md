<!-- source: https://laws.e-gov.go.jp/docs/law-data-basic/8ebd8bc-law-structure-and-xml/ -->
<!-- fetched: 2026-03-25T08:08:58Z -->

[法令データ ドキュメンテーション（α版）](/docs/)
法令の条文構造と法令XML
# 法令の条文構造と法令XML
本稿では、法令のおおまかな構造について解説し、またそれらの構造が法令標準XMLスキーマを用いたXML（法令XML）でどのように表現されるか、具体例とともに解説します。
## その1：本則や附則など<a href="#その1本則や附則など" id="その1本則や附則など"></a>
法令はものによっては長く複雑な構造を持ちますが、一定の形式に従って記述されており、典型的なパターンを持っています。まずは、e-Gov法令検索で閲覧できる<a href="https://laws.e-gov.go.jp/document?lawid=425AC0000000027" target="_blank" rel="noreferrer">行政手続における特定の個人を識別するための番号の利用等に関する法律（平成二十五年法律第二十七号）<span> (opens in a new tab)</span></a>（以下「マイナンバー法」）を例におおまかな構造を見ていきます。
マイナンバー法抜粋（`…`は注記、2024年1月23日時点）：
    平成二十五年法律第二十七号
    行政手続における特定の個人を識別するための番号の利用等に関する法律
    ...
    　（目的）
    第一条　この法律は、行政機関、地方公共団体その他の行政事務を処理する者が、...
    ...
    　　　附　則
    　（施行期日）
    第一条　この法律は、公布の日から起算して三年を超えない範囲内において政令で定める日から施行する。ただし、次の各号に掲げる規定は、当該各号に定める日から施行する。
    　...
    ...
- 1行目: [**法令番号**](/docs/law-data-basic/607318a-lawtypes-and-lawid/#lawnum)です。なお、現行のe-Gov法令検索ではこのように表示していますが、官報では少し書き方が異なります（<a href="https://kanpou.npb.go.jp/old/20130531/20130531g00112/20130531g001120021f.html" target="_blank" rel="noreferrer">参考<span> (opens in a new tab)</span></a>）。
- 2行目: **法令名**です。
- 6-9行目: 法令の内容である条文です。後述する「附則」と対比して「**本則**」と呼びます。本則の条文を指すときは、単に「第一条」のように参照します。多くの場合は「条」を基本単位として記述し、「条建て」と呼ばれます。「項」を基本単位とした「項建て」のように他の記述方法を用いる法令もあります。
- 11-15行目: 「**附則**」です。法令の施行期日などを記述します。附則の条文を指すときは「附則第一条」のように参照します。
ここまでの法令の階層構造をまとめると下記のようになります。
    法令（法令XML全体）
      ├─法令番号
      ├─法令名
      ├─本則
      │   └─条など
      └─附則
          └─条など
### 法令XMLでの表現<a href="#法令xmlでの表現" id="法令xmlでの表現"></a>
上記「その1」の条文を法令標準XMLスキーマを用いたXML（法令XML）で表現すると下記のようになります。（一部属性や章の構造などは省略しています。）
（`…`は注記）
    <Law>
      <LawNum>平成二十五年法律第二十七号</LawNum>
      <LawBody>
        <LawTitle>行政手続における特定の個人を識別するための番号の利用等に関する法律</LawTitle>
     
        …
     
        <MainProvision>
          …
          <Article Num="1">
            <ArticleCaption>（目的）</ArticleCaption>
            <ArticleTitle>第一条</ArticleTitle>
            <Paragraph Num="1">
              <ParagraphNum/>
              <ParagraphSentence>
                <Sentence Num="1">この法律は、行政機関、地方公共団体その他の行政事務を処理する者が、…</Sentence>
              </ParagraphSentence>
            </Paragraph>
          </Article>
          …
        </MainProvision>
     
        <SupplProvision>
          <SupplProvisionLabel>附　則</SupplProvisionLabel>
          <Article Num="1">
            <ArticleCaption>（施行期日）</ArticleCaption>
            <ArticleTitle>第一条</ArticleTitle>
            <Paragraph Num="1">
              <ParagraphNum/>
              <ParagraphSentence>
                <Sentence Function="main" Num="1">この法律は、公布の日から起算して三年を超えない範囲内において政令で定める日から施行する。</Sentence>
                <Sentence Function="proviso" Num="2">ただし、次の各号に掲げる規定は、当該各号に定める日から施行する。</Sentence>
              </ParagraphSentence>
              …
            </Paragraph>
          </Article>
          …
        </SupplProvision>
     
        …
     
      </LawBody>
    </Law>
- `<Law>`要素は法令XMLのルート要素です。
- `<LawNum>`要素は「法令番号」を表します。
- `<LawBody>`要素は法令名や本則、附則などを含む法令の内容全体を表します。
- `<LawTitle>`要素は「法令名」を表します。
- `<MainProvision>`要素は「本則」を表します。
- `<SupplProvision>`要素は「附則」を表します。
- `<SupplProvisionLabel>`要素は「附則の題名」を表します。
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
法令APIでマイナンバー法の法令XMLを取得し、そこから`<LawNum>`（法令番号）、`<LawTitle>`（法令名）、本則の`<Article>`（条）冒頭3つ、附則の`<Article>`（条）冒頭3つを取得してみます。
⚠️
下記のサンプルコードでは、`<Article>`の取得時に、簡単のため、`getElementsByTagName`を使用しています。この場合、意図せずタグの深い階層に入れ子になった`<Article>`を取得することがあり、これはいわゆる「条の一覧を取得」よりも余計にタグを取得してしまう可能性があるので、実際のアプリ作成時にはご注意ください。
法令XMLから法令番号等を表示
    (async () => {
        // 法令APIからマイナンバー法（法令ID "425AC0000000027"）の法令本文XMLを取得する
        const r = await fetch("https://laws.e-gov.go.jp/api/1/lawdata/425AC0000000027");
        const xml = await r.text();
        const parser = new DOMParser();
        const doc = parser.parseFromString(xml, "application/xml");
     
        // LawNum要素を取得する
        const lawNumEl = doc.querySelector("Law > LawNum");
        console.log(lawNumEl);
     
        // LawTitle要素を取得する
        const lawTitleEl = doc.querySelector("Law > LawBody > LawTitle");
        console.log(lawTitleEl);
     
        // MainProvision中のArticleを取得する
        const mainProvisionEl = doc.querySelector("Law > LawBody > MainProvision");
        console.log(mainProvisionEl);
        const mpArticleElList = [...(mainProvisionEl?.getElementsByTagName("Article") ?? [])];
        for (const el of mpArticleElList.slice(0, 3)) console.log(el);
     
        // （AmendLawNum属性を持たない）SupplProvision中のArticleを取得する
        const supplProvisionEl = doc.querySelector("Law > LawBody > SupplProvision:not([AmendLawNum])");
        console.log(supplProvisionEl);
        const spArticleElList = [...(supplProvisionEl?.getElementsByTagName("Article") ?? [])];
        for (const el of spArticleElList.slice(0, 3)) console.log(el);
    })();
## その2：章や別表など<a href="#その2章や別表など" id="その2章や別表など"></a>
同じマイナンバー法を用いて、「その1」では略した部分を一部広げて見ていきます。
マイナンバー法抜粋（`…`は注記、2024年1月23日時点）：
    平成二十五年法律第二十七号
    行政手続における特定の個人を識別するための番号の利用等に関する法律
    目次
    　第一章　総則（第一条―第六条）
    　第二章　個人番号（第七条―第十六条）
    　…
    　　　第一章　総則
    　（目的）
    第一条　この法律は、行政機関、地方公共団体その他の行政事務を処理する者が、…
    …
    　　　附　則
    　（施行期日）
    第一条　この法律は、公布の日から起算して三年を超えない範囲内において政令で定める日から施行する。ただし、次の各号に掲げる規定は、当該各号に定める日から施行する。
    　…
    …
    　　　附　則　（平成二四年八月二二日法律第六七号）　抄
    …
    　　　附　則　（平成二四年一一月二六日法律第一〇二号）　抄
    …
    別表第一（第九条関係）
    …
    別表第二（第十九条、第二十一条関係）
    …
- 4-7行目: **目次**です。章などの大きな構造を記述する場合が多いです。
- 9行目: **章名**です。章のほかに「編」や「節」などの階層があります。条項をグループ化するために用いられます。
- 16-22行目（再掲）: 附則です。後述の改正附則に対比して「**原始附則**」と呼ばれます。
- 24-28行目: 「**改正附則**」です。これら改正附則は原始附則とは異なり、現在見ている法令（上記の例ではマイナンバー法）には属さず、[改正法令](/docs/law-data-basic/da91fe9-law-revisions/#lifecycle-of-law)に属します。言い換えると、改正附則は改正法令の原始附則です。そのため、改正附則を指す場合は改正法令の法令番号などを用いて参照します。
- 30-34行目: **別表**です。こちらは現在見ている法令（上記の例ではマイナンバー法）に属し、「別表第一」のように参照します。別表のほかに、別図、別記などのバリエーションがあります。
ここまでの法令の階層構造をまとめると下記のようになります。
    法令（法令XML全体）
      ├─法令番号
      ├─法令名
      ├─目次など
      ├─本則
      │   └─章など
      │       └─条など
      ├─原始附則
      │   └─条など
      ├─改正附則
      │   └─条など
      └─別表など
### 法令XMLでの表現<a href="#法令xmlでの表現-1" id="法令xmlでの表現-1"></a>
上記「その2」の条文を法令標準XMLスキーマを用いたXML（法令XML）で表現すると下記のようになります。（一部属性は省略しています。）
（`…`は注記）
    <Law>
      <LawNum>平成二十五年法律第二十七号</LawNum>
      <LawBody>
        <LawTitle>行政手続における特定の個人を識別するための番号の利用等に関する法律</LawTitle>
        <TOC>
          <TOCLabel>目次</TOCLabel>
          <TOCChapter Num="1">
            <ChapterTitle>第一章　総則</ChapterTitle>
            <ArticleRange>（第一条―第六条）</ArticleRange>
          </TOCChapter>
          <TOCChapter Num="2">
            <ChapterTitle>第二章　個人番号</ChapterTitle>
            <ArticleRange>（第七条―第十六条）</ArticleRange>
          </TOCChapter>
          …
        </TOC>
        <MainProvision>
          <Chapter Num="1">
            <ChapterTitle>第一章　総則</ChapterTitle>
            <Article Num="1">
              <ArticleCaption>（目的）</ArticleCaption>
              <ArticleTitle>第一条</ArticleTitle>
              <Paragraph Num="1">
                <ParagraphNum/>
                <ParagraphSentence>
                  <Sentence Num="1">この法律は、行政機関、地方公共団体その他の行政事務を処理する者が、…</Sentence>
                </ParagraphSentence>
              </Paragraph>
            </Article>
            …
          </Chapter>
          …
        </MainProvision>
        <SupplProvision>
          <SupplProvisionLabel>附　則</SupplProvisionLabel>
          <Article Num="1">
            <ArticleCaption>（施行期日）</ArticleCaption>
            <ArticleTitle>第一条</ArticleTitle>
            <Paragraph Num="1">
              <ParagraphNum/>
              <ParagraphSentence>
                <Sentence Function="main" Num="1">この法律は、公布の日から起算して三年を超えない範囲内において政令で定める日から施行する。</Sentence>
                <Sentence Function="proviso" Num="2">ただし、次の各号に掲げる規定は、当該各号に定める日から施行する。</Sentence>
              </ParagraphSentence>
              …
            </Paragraph>
          </Article>
          …
        </SupplProvision>
        <SupplProvision AmendLawNum="平成二四年八月二二日法律第六七号" Extract="true">
          <SupplProvisionLabel>附　則</SupplProvisionLabel>
          …
        </SupplProvision>
        <SupplProvision AmendLawNum="平成二四年一一月二六日法律第一〇二号" Extract="true">
          <SupplProvisionLabel>附　則</SupplProvisionLabel>
          …
        </SupplProvision>
        …
        <AppdxTable Num="1">
          <AppdxTableTitle>別表第一</AppdxTableTitle>
          <RelatedArticleNum>（第九条関係）</RelatedArticleNum>
          …
        </AppdxTable>
        <AppdxTable Num="2">
          <AppdxTableTitle>別表第二</AppdxTableTitle>
          <RelatedArticleNum>（第十九条、第二十一条関係）</RelatedArticleNum>
        </AppdxTable>
      </LawBody>
    </Law>
- `<TOC>`要素は「目次」を表します。
- `<TOCLabel>`要素は「目次の題名」を表します。
- `<TOCChapter>`要素は目次の項目のうち「章」を表すものです。他にも編や節など、目次の要素を表すタグのバリエーションがあります。
- 8行目の`<ChapterTitle>`要素は後述の19行目の`<ChapterTitle>`と同じタグで、「章の題名」を表します。
- `<ArticleRange>`要素は目次の項目に付記されている「条の範囲」を表します。
- `<Chapter>`要素は「章」を表します。他にも編や節など、条項のグループを表すタグのバリエーションがあります。
- 19行目の`<ChapterTitle>`要素は「章の題名」を表します。
- （再掲）34行目の`<SupplProvision>`要素は「附則」（原始附則）を表します。
- 50行目の`<SupplProvision>`は改正附則ですが、原始附則も改正附則も同じ`<SupplProvision>`要素を用います。ただし、改正附則の場合は改正附則が属する改正法令の法令番号を`AmendLawNum`属性として指定します。ここでの法令番号の形式は通常法令の参照の際に用いられる形式とは異なりますが、年、法令種別、番号の内容は同じものを指しています。また、附則の全部を記述するのではなく抜粋（抄録）する場合は`Extract="true"`属性を指定します。e-Gov法令検索の画面上は「抄」と表示されます。
- `<AppdxTable>`要素は「別表」を表します。他にも別図や別記など、付属の要素を表すタグのバリエーションがあります。
- `<AppdxTableTitle>`要素は「別表の題名」を表します。
- `<RelatedArticleNum>`要素は別表などに付記されている「関係する条」を表します。
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
法令APIでマイナンバー法の法令XMLを取得し、そこから本則直下の`<Chapter>`（章）、原始附則の`<SupplProvision>`、改正附則の`<SupplProvision>`のうち冒頭3つ、`<AppdxTable>`（別表）を取得してみます。
法令XMLから章等を表示
    (async () => {
        // 法令APIからマイナンバー法（法令ID "425AC0000000027"）の法令本文XMLを取得する
        const r = await fetch("https://laws.e-gov.go.jp/api/1/lawdata/425AC0000000027");
        const xml = await r.text();
        const parser = new DOMParser();
        const doc = parser.parseFromString(xml, "application/xml");
     
        // MainProvision直下のChapterを取得する
        const chapterElList = [...doc.querySelectorAll("Law > LawBody > MainProvision > Chapter")];
        for (const el of chapterElList) console.log(el);
     
        // 原始附則のSupplProvisionを取得する
        const origSpElList = [...doc.querySelectorAll("Law > LawBody > SupplProvision:not([AmendLawNum])")];
        for (const el of origSpElList) console.log(el);
     
        // 改正附則のSupplProvisionを取得する
        const amendSpElList = [...doc.querySelectorAll("Law > LawBody > SupplProvision[AmendLawNum]")];
        for (const el of amendSpElList.slice(0, 3)) console.log(el);
     
        // AppdxTableを取得する
        const appdxTableElList = [...doc.querySelectorAll("Law > LawBody > AppdxTable")];
        for (const el of appdxTableElList) console.log(el);
    })();
## その3：条や項など<a href="#その3条や項など" id="その3条や項など"></a>
同じマイナンバー法を用いて、今度は条項の構造を見ていきます。
マイナンバー法抜粋（`…`は注記、2024年1月23日時点）：
    　（個人番号とすべき番号の生成）
    第八条　市町村長は、前条第一項又は第二項の規定により…
    ２　機構は、前項の規定により市町村長から…
    　一　他のいずれの個人番号…
    　二　前項の住民票コードを…
    　三　前号の住民票コードを…
    ３　機構は、前項の規定により個人番号とすべき番号を…
- 1行目: **条の見出し**です。
- 2行目: **条**の本体です。「第八条」は条名です。なお、この例では条の下位構造として「項」が使用されているため、「第一項」として扱われます。そのため、「市町村長は、…」の条文を指す場合は「**第八条第一項**」のように参照することになります。
- 3行目: **項**です。条の次の下位構造です。3行目は項番号が「２」なので「第二項」ということになります。条番号を含めた参照は「**第八条第二項**」です。なお、この項は下位要素として「号」を含んでいますが、下位要素以外の部分（ここでは3行目の文章）を「**柱書**」と呼ぶことがあります。
- 4行目: **号**です。項の次の下位構造です。4行目は号名が「一」なので「第一号」ということになります。条番号を含めた参照は「**第八条第二項第一号**」です。
💡
項のない条（一項建ての条）の場合は条の直下に号が付くことがあります。この場合は「第○条第○号」のように参照することとなります。ただし、この場合でも、法令XML上は項番号の無い`Paragraph`要素が使用されます。
条項の階層構造は、「条」→「項」→「号」のように細かくなります。「号」のさらに下位構造もあり、「**号の細分**」のように呼ばれます。
「条」や「号」には、「枝番号」を付すことができます。例えば、「第二十一条の二」のような条名にすることができます。枝番号は、元の条名とは入れ子構造の関係にはなく、「第二十一条の二」は、「第二十一条」とは独立した、まったく別の条です。枝番号をさらに深くして、「第三十八条の三の二」のようにすることもできます。この場合でも、「第三十八条」や「第三十八条の三」とは独立した、まったく別の条です。
条や項に関する階層構造をまとめると下記のようになります。
    条
      ├─条見出し
      ├─条名
      └─項
          ├─項番号（第二項以降の場合）
          ├─項の柱書
          └─号
              ├─号名
              ├─号の柱書
              └─号の細分
                  └─…
### 法令XMLでの表現<a href="#法令xmlでの表現-2" id="法令xmlでの表現-2"></a>
上記「その3」の条文を法令標準XMLスキーマを用いたXML（法令XML）で表現すると下記のようになります。（一部属性や上位構造は省略しています。）
（`…`は注記）
    <Article Num="8">
      <ArticleCaption>（個人番号とすべき番号の生成）</ArticleCaption>
      <ArticleTitle>第八条</ArticleTitle>
      <Paragraph Num="1">
        <ParagraphNum/>
        <ParagraphSentence>
          <Sentence Num="1">市町村長は、前条第一項又は第二項の規定により…</Sentence>
        </ParagraphSentence>
      </Paragraph>
      <Paragraph Num="2">
        <ParagraphNum>２</ParagraphNum>
        <ParagraphSentence>
          <Sentence Num="1">機構は、前項の規定により市町村長から…</Sentence>
        </ParagraphSentence>
        <Item Num="1">
          <ItemTitle>一</ItemTitle>
          <ItemSentence>
            <Sentence Num="1">他のいずれの個人番号…</Sentence>
          </ItemSentence>
        </Item>
        <Item Num="2">
          <ItemTitle>二</ItemTitle>
          <ItemSentence>
            <Sentence Num="1">前項の住民票コードを…</Sentence>
          </ItemSentence>
        </Item>
        <Item Num="3">
          <ItemTitle>三</ItemTitle>
          <ItemSentence>
            <Sentence Num="1">前号の住民票コードを…</Sentence>
          </ItemSentence>
        </Item>
      </Paragraph>
      <Paragraph Num="3">
        <ParagraphNum>３</ParagraphNum>
        <ParagraphSentence>
          <Sentence Num="1">機構は、前項の規定により個人番号とすべき番号を…</Sentence>
        </ParagraphSentence>
      </Paragraph>
    </Article>
- `<Article>`要素は「条」を表します。
- `<ArticleCaption>`要素は「条の見出し」を表します。
- `<ArticleTitle>`要素は「条名」を表します。
- `<Paragraph>`要素は「項」を表します。なお、一項建ての条の場合でも、項番号を省略した項が一つあるとみなして`<Paragraph>`要素を含め、その要素の中に本文を記述します。
- 5行目の`<ParagraphNum>`要素は「項番号」を表しますが、条に含まれる第一項の項番号は省略するので、その場合は`<ParagraphNum>`要素は空のものを指定します。
- `<ParagraphSentence>`要素は「項の柱書」を表します。なお、「柱書」は、ここでは下位要素を含まない「項」直下の文章を指します。
- `<Sentence>`要素は文章を表します。
- 11行目の`<ParagraphNum>`要素は「項番号」を表します。
- `<Item>`要素は「号」を表します。
- `<ItemTitle>`要素は「号名」を表します。
- `<ItemSentence>`要素は「号の柱書」を表します。
`<Article>`要素や`<Paragraph>`要素、`<Item>`要素には`Num`属性があり、条名、項番号、号名に対応した番号が格納されています。枝番号の場合は、アンダースコアでつないで、例えば「第三十八条の三の二」の場合は、`Num="38_3_2"`のようにします。
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
法令APIでマイナンバー法の法令XMLを取得し、そこから第八条を取り出して条項を出力してみます。
⚠️
下記のサンプルコードでは、`<Article>`や`<Paragraph>`などの要素の取得時に、簡単のため、`getElementsByTagName`や`querySelector`の子孫結合子を使用しています。この場合、意図せずタグの深い階層に入れ子になったタグを取得してしまう可能性があるので、実際のアプリ作成時にはご注意ください。
法令XMLから条項を表示
    (async () => {
        // 法令APIからマイナンバー法（法令ID "425AC0000000027"）の法令本文XMLを取得する
        const r = await fetch("https://laws.e-gov.go.jp/api/1/lawdata/425AC0000000027");
        const xml = await r.text();
        const parser = new DOMParser();
        const doc = parser.parseFromString(xml, "application/xml");
     
        // 出力する行の配列
        const lines = [];
     
        // 第八条のArticleを取得
        const articleEl = doc.querySelector('Law > LawBody > MainProvision Article[Num="8"]');
        const articleCaptionEl = articleEl?.getElementsByTagName("ArticleCaption")[0];
        if (articleCaptionEl) lines.push(`　$`)
        const articleTitleEl = articleEl?.getElementsByTagName("ArticleTitle")[0];
     
        // Paragraphを処理
        for (const [pi, paragraphEl] of [...(articleEl?.getElementsByTagName("Paragraph") ?? [])].entries()) {
            const paragraphNumEl = paragraphEl?.getElementsByTagName("ParagraphNum")[0];
            const paragraphSentenceEl = paragraphEl?.getElementsByTagName("ParagraphSentence")[0];
            lines.push(`$$　$`)
     
            // Itemを処理
            for (const itemEl of [...paragraphEl.getElementsByTagName("Item")]) {
                const itemTitleEl = itemEl?.getElementsByTagName("ItemTitle")[0];
                const itemSentenceEl = itemEl?.getElementsByTagName("ItemSentence")[0];
                lines.push(`　$　$`)
            }
        }
     
        console.log(lines.join("\n"))
    })();
## もっと詳しく<a href="#もっと詳しく" id="もっと詳しく"></a>
法令の条文構造と法令XMLについてさらに詳しくは、 [法令標準XMLスキーマ](/docs/law-data-basic/419a603-xml-schema-for-japanese-law/) のページをご覧ください。
Light
------------------------------------------------------------------------
[法令データ ドキュメンテーション（α版）](/docs/)
- 現時点で本サイトは、正式化前の試験的な公開（α版）です。本サイトの資料は、今後内容をブラッシュアップの上、正式化することを目指しています。
- 本サイトの内容は、特に注記のない限り、政府の公式見解を表すものではなく、執筆者の見解を示すものにとどまります。法令分野の専門家以外の方にもわかりやすい記述となるように、表現や業務内容を簡略化等している部分があり、政府の公式見解や実際の業務とは差異がある部分がありますので、その点御注意下さい。
- 本サイトの内容は、必ずしも常にアップデートされているとは限らず、また、予告なく URL が変更されたり削除される場合があります。
- 本サイトに記載のサンプルコードのご利用は、ご自身の責任で行ってください。万が一サンプルコードの利用による不利益が生じた場合でも、一切の責任を負いかねますのでご了承ください。

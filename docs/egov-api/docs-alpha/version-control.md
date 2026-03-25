<!-- source: https://laws.e-gov.go.jp/docs/docs/ba4d819-laws-and-version-control/ -->
<!-- fetched: 2026-03-25T08:08:59Z -->

[法令データ ドキュメンテーション（α版）](/docs/)
法令文書とバージョン管理
<span class="small">2023/3/27</span>
# 法令文書とバージョン管理
本稿では、法令文書とバージョン管理にまつわる事情を技術的側面から解説します。
## 改正法令と施行期日<a href="#改正法令と施行期日" id="改正法令と施行期日"></a>
### 公布と施行<a href="#公布と施行" id="公布と施行"></a>
- 法令は、成立後、官報に掲載されます。これを「公布」と呼びます。
- 成立・公布された法令は、内容が確定されています。
  - 法令の内容を変更するには、他法令による改正を行う必要があります。
- 法令の効力を発生させることを「施行」と呼びます。
- 施行期日は、「公布の日」や「何月何日」、「政令で定める日」といったように指定することができます。
  - 「政令で定める日」のような指定を行う場合、具体的にいつ施行されるかは、公布の段階では確定していないことになります。
<img src="/docs/_next/static/media/030-promulgation-and-enforcement.a44767b4.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1429" alt="公布と施行" />
### 改正法令の施行<a href="#改正法令の施行" id="改正法令の施行"></a>
- 改正法令は、他の法令を改正します。改正のタイミングは、改正法令の施行期日です。
- 下の図は、「*A*<span aria-hidden="true">A</span>法」という被改正法令が、「*P*<span aria-hidden="true">P</span>法」及び「*Q*<span aria-hidden="true">Q</span>法」という法令により順次改正される様子を表しています。
- 過去時点の被改正法令の内容は一意に特定することができます。例えば、下の図では、 *T*<sub>*Q*</sub><span aria-hidden="true">TQ​</span>日や *T*<sub>*P*</sub><span aria-hidden="true">TP​</span>日は既に確定した過去の日付なので、これらの過去時点での*A*<span aria-hidden="true">A</span>法の内容は一意に特定できます。
<img src="/docs/_next/static/media/040-enforcement-of-amendment.3b311949.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1449" alt="改正法令の施行" />
### 施行期日が前後しうる場合<a href="#施行期日が前後しうる場合" id="施行期日が前後しうる場合"></a>
- しかし、未来の日付については、その時点の被改正法令の内容を一意に特定できるとは限りません。
- 例えば下の図では、2つの改正法令（*P*<span aria-hidden="true">P</span>と*Q*<span aria-hidden="true">Q</span>）の施行期日が前後しうる場合、2つの施行期日の中間での状態が、 *A*<sub>*P*</sub><span aria-hidden="true">AP​</span>及び *A*<sub>*Q*</sub><span aria-hidden="true">AQ​</span>の2種類存在します。
  - このような現象は、(1)公布済み（＝内容が確定）で未施行（＝効力が未発生）である複数の改正規定が存在して、(2)しかも施行期日が未確定で前後しうる場合に発生します。
  - なお、下の図でいう *A*<sub>*P*</sub><span aria-hidden="true">AP​</span>と *A*<sub>*Q*</sub><span aria-hidden="true">AQ​</span>の内容は異なるのが通常ですが、 *A*<sub>*P**Q*</sub><span aria-hidden="true">APQ​</span>と *A*<sub>*Q**P*</sub><span aria-hidden="true">AQP​</span>については両者を一致させることが通常であると考えられます。
<img src="/docs/_next/static/media/050-undetermined-enforcement-order.4c524748.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1441" alt="施行期日が前後しうる場合" />
### 被改正法令の未来の状態<a href="#被改正法令の未来の状態" id="被改正法令の未来の状態"></a>
- ただし、未来の状態が一意に特定できないといっても、内容が曖昧であるわけではありません。
- 公布済みの改正法令の内容は確定されているため、被改正法令の未来の状態は、改正法令の施行期日順のパターンごとに事前に確定します。
  - 下の図でいうと、 *A*<sub>*P*</sub><span aria-hidden="true">AP​</span>、 *A*<sub>*P**Q*</sub><span aria-hidden="true">APQ​</span>、 *A*<sub>*Q*</sub><span aria-hidden="true">AQ​</span>、 *A*<sub>*Q**P*</sub><span aria-hidden="true">AQP​</span>の内容はそれぞれ曖昧性なく確定しているので、それぞれの内容が実社会に適用されて問題ないかを事前に確認できます。
  - ところで、改正法令（下の図の*P*<span aria-hidden="true">P</span>と*Q*<span aria-hidden="true">Q</span>）は、内容が確定されているにもかかわらず施行期日の前後により問題を生じないよう、工夫する必要があります。その工夫については後ほど「[調整規定](/docs/docs/ba4d819-laws-and-version-control/#%E8%AA%BF%E6%95%B4%E8%A6%8F%E5%AE%9A)」の項目で述べます。
<img src="/docs/_next/static/media/060-future-status-of-amended-law.3f0794de.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="新たな改正法令を検討するとき" />
### 新たな改正法令を検討するとき<a href="#新たな改正法令を検討するとき" id="新たな改正法令を検討するとき"></a>
- 新たな改正法*R*<span aria-hidden="true">R</span>を検討する場合は、*R*<span aria-hidden="true">R</span>がどのパターンのどの時点に適用するかにより、条文の内容が変わることになります。
- そのため、改正法令の検討時には、未来の法令内容の状態を正確に把握する必要があります。
  - なお、パターンの数は施行期日の前後する法令の数が増えると急激に増大するので、通常の法制事務においては、特定の施行期日の順序（例えば *T*<sub>*P*</sub> &lt; *T*<sub>*Q*</sub><span aria-hidden="true">TP​&lt;TQ​</span> ）を前提としてしまうことも多いです。ただし、この場合、万が一実際の施行順が入れ替わってしまうと（例えば *T*<sub>*Q*</sub> &lt; *T*<sub>*P*</sub><span aria-hidden="true">TQ​&lt;TP​</span> ）、事前に想定していないパターン（ *A*<sub>*Q*</sub><span aria-hidden="true">AQ​</span>）が適用されてしまうので、そのような事態は避けなければなりません。
<img src="/docs/_next/static/media/070-considering-new-amendment.44d855ea.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="被改正法令の未来の状態" />
## 改正法令の競合<a href="#改正法令の競合" id="改正法令の競合"></a>
### 法令の改正規定<a href="#法令の改正規定" id="法令の改正規定"></a>
- 法令の改正は、曖昧性をなくすため、改正箇所と改正内容を文字単位で明示する方法を用います。
  - 「改め文」と呼ばれます。
  - 改正規定が適用されて法令の文章が更新される文字操作を「溶け込ませる」と呼び、このようにして生成された文章を「溶け込み」と呼ぶことがあります。
<img src="/docs/_next/static/media/100-amendment-clause.05aa2118.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1429" alt="法令の改正規定" />
### 改正規定が溶け込まない例<a href="#改正規定が溶け込まない例" id="改正規定が溶け込まない例"></a>
- 改正規定で指定している改正箇所や改正内容が改正前の法令とミスマッチしている場合は、改正規定を溶け込ませることができません。
  - この状態で改正法令が公布されると、誤りとなってしまいます。
<img src="/docs/_next/static/media/110-inconsistent-amendment-clause.a866bcf6.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1429" alt="改正規定が溶け込まない例" />
### 複数の改正規定が連続する場合<a href="#複数の改正規定が連続する場合" id="複数の改正規定が連続する場合"></a>
- 複数の改正規定が連続するとき、それぞれの改正規定は一つ前の改正規定が溶け込んだ条文に対して適用されます。
<img src="/docs/_next/static/media/120-consecutive-amendment-clauses.7e37d4d8.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1429" alt="複数の改正規定が連続する場合" />
### 改正規定の順序が前後した場合<a href="#改正規定の順序が前後した場合" id="改正規定の順序が前後した場合"></a>
- 意図せず改正規定が前後すると、適用する改正規定と適用先の規定の内容に齟齬（競合）が生じ、溶け込まない事態が発生してしまいます。
<img src="/docs/_next/static/media/130-inconsistent-order-of-amendment-clauses.015b6956.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1429" alt="改正規定の順序が前後した場合" />
### 調整規定<a href="#調整規定" id="調整規定"></a>
- 施行期日が前後する場合の競合を解決する工夫の一つが「調整規定」です。
- 「調整規定」は、施行期日が前後しうる他の改正法令がある場合に、両者の施行順に応じて改正の内容を読み替えます。
- 下の図では、*P*<span aria-hidden="true">P</span>の第 *P*<sub>2</sub><span aria-hidden="true">P2​</span>条が調整規定です。*P*<span aria-hidden="true">P</span>と*Q*<span aria-hidden="true">Q</span>の施行順に応じて、改正規定である第 *P*<sub>1</sub><span aria-hidden="true">P1​</span>条を読み替えることで、齟齬が生じないようにします。
<img src="/docs/_next/static/media/140-adjustment-clause.befe49e3.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1429" alt="調整規定" />
## 時系列に対する変更<a href="#時系列に対する変更" id="時系列に対する変更"></a>
### 法令の時系列<a href="#法令の時系列" id="法令の時系列"></a>
- ある法令が順次改正される様子を、下の図のように時系列で表すことができます。
- 過去の時系列が修正されるのは、基本的に誤字の訂正（正誤）などに限られます。
- 一方、未来の時系列は、改正法令が策定されることにより変更されます。また、未公布時点での編集作業まで考慮に入れると、法令案の編集によっても時系列の変更が発生することになります。
<img src="/docs/_next/static/media/160-timeline-of-laws.e72ab946.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="法令の時系列" />
### 後に施行する改正法令の追加<a href="#後に施行する改正法令の追加" id="後に施行する改正法令の追加"></a>
- 未施行の改正を前提とした改正法令を時系列上の後に追加することができます。
<img src="/docs/_next/static/media/170-adding-amendment-after.cc52053f.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="後に施行する改正法令の追加" />
### 割り込み施行<a href="#割り込み施行" id="割り込み施行"></a>
- 未施行の改正法令の前に施行する改正法令を追加することもできます。このように、先に施行する改正法令が後から登場することを「割り込み施行」と呼ぶことがあります。
<img src="/docs/_next/static/media/180-interrupting-enforcement.0ea0985d.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="割り込み施行" />
### 未施行の改正法令の改正<a href="#未施行の改正法令の改正" id="未施行の改正法令の改正"></a>
- 公布済みで未施行の改正法令の内容を変更したい事情が生じた場合は、改正法令の改正を行います。
- 例えば、割り込み施行が生じたために、既に公布済みの改正法令が前提としていた被改正法例の内容が変わってしまい、これに伴って公布済み改正法令の内容を調整しなければならなくなることがあります。このような場合は、改正法令の改正を行います。
<img src="/docs/_next/static/media/190-amending-unenacted-amendment.88b8fdf1.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="未施行の改正法令の改正" />
## 複数の改正規定からなる改正法令<a href="#multiple-amendment-clauses" id="multiple-amendment-clauses"></a>
### 複数の法令を改正する改正法令<a href="#複数の法令を改正する改正法令" id="複数の法令を改正する改正法令"></a>
- 一つの改正法令で、複数の法令を改正することができます。
- 通常、それぞれの被改正法例に対応する改正規定を、別々の条として改正法令の中に記述します。
<img src="/docs/_next/static/media/210-amendment-of-multiple-laws.a7d76cae.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="複数の法令を改正する改正法令" />
### 複数の施行期日を持つ改正法令<a href="#複数の施行期日を持つ改正法令" id="複数の施行期日を持つ改正法令"></a>
- 一つの改正法令の中で、改正規定ごとに施行期日を分けることができます。
- このとき、後に施行する改正規定が前に施行する改正規定の内容に依存することがあります。このような場合は、それぞれの改正規定を別々の条として改正法令の中に記述します。
  - このような改正方法を「n段ロケット」（例えば「2段ロケット」）と呼ぶことがあります。
<img src="/docs/_next/static/media/220-multiple-enforcement-dates.076622bb.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="複数の施行期日を持つ改正法令" />
### 複数法令の改正・複数施行期日の組み合わせ<a href="#複数法令の改正複数施行期日の組み合わせ" id="複数法令の改正複数施行期日の組み合わせ"></a>
- 一つの改正法令において、複数の法令に対する改正規定や、一つの法令に対する複数の施行期日の改正規定を組み合わせることができます。
<img src="/docs/_next/static/media/230-amend-multiple-laws-multiple-dates.4750b799.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="複数法令の改正・複数施行期日の組み合わせ" />
### 一部施行済みの改正法令<a href="#一部施行済みの改正法令" id="一部施行済みの改正法令"></a>
- 複数の施行期日を持つ改正法令の場合、時間の経過と共に一部のみ施行されることがあります。
- なお、改正法令はその全部が一体として公布されるので、公布済みと未公布の規定が同じ法令の中で共存することはありません。
<img src="/docs/_next/static/media/240-partially-enforced-amendment.95908fce.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="一部施行済みの改正法令" />
### 新規制定法令の附則による改正<a href="#新規制定法令の附則による改正" id="新規制定法令の附則による改正"></a>
- 法令を新規に制定するとき、その新規制定法令の附則で他の法令を改正することができます。
- 当該改正規定は施行して他の法令に溶け込むことで役目を終えますが、新規制定部分は存在し続けます。
<img src="/docs/_next/static/media/250-amend-by-suppl-provision.564c22c7.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="新規制定法令の附則による改正" />
### 改正法令の改正規定以外の部分<a href="#改正法令の改正規定以外の部分" id="改正法令の改正規定以外の部分"></a>
- 改正法令についても、改正規定以外を含む場合（経過措置等）、その部分については新規制定法令と同様のライフサイクルが始まります。
<img src="/docs/_next/static/media/260-remaining-part-of-amendment.32cb37d0.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="改正法令の改正規定以外の部分" />
### 複数の改正法令の依存関係<a href="#複数の改正法令の依存関係" id="複数の改正法令の依存関係"></a>
- 複数の改正法令が同一の法令を改正する場合、それらの改正法令は他の改正法令の内容に依存することになります。
- 下の図では、*A*<span aria-hidden="true">A</span>法を改正する*Q*<span aria-hidden="true">Q</span>法中の改正規定 *Q*<sub>2</sub><span aria-hidden="true">Q2​</span>は、前に施行する*P*<span aria-hidden="true">P</span>法中の改正規定 *P*<sub>1</sub><span aria-hidden="true">P1​</span>に依存します。また、*B*<span aria-hidden="true">B</span>法を改正する*Q*<span aria-hidden="true">Q</span>法及び*C*<span aria-hidden="true">C</span>法では、 *Q*<sub>3</sub><span aria-hidden="true">Q3​</span>→*C*<sub>*s*1</sub><span aria-hidden="true">Cs1​</span>→*Q*<sub>1</sub><span aria-hidden="true">Q1​</span>のように相互に依存することになります。
<img src="/docs/_next/static/media/270-dependency-of-amendments.49c35be6.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="複数の改正法令の依存関係" />
## 編集によるバージョン<a href="#編集によるバージョン" id="編集によるバージョン"></a>
### 法令案の編集<a href="#法令案の編集" id="法令案の編集"></a>
- 法令文書は、法令改正による時系列上の変化に加え、編集によっても変化します。本資料では、便宜上これを「バージョン」の変化として説明します。
  - 例えば下の図では、被改正法例 *A*<sub>0</sub><span aria-hidden="true">A0​</span>が *P*<sub>1</sub><span aria-hidden="true">P1​</span>によって改正されて *A*<sub>*P*<sub>1</sub></sub><span aria-hidden="true">AP1​​</span>になる時系列上の変化に加えて、法令案 *P*<sub>1</sub><span aria-hidden="true">P1​</span>を編集して *P*<sub>2</sub><span aria-hidden="true">P2​</span>とする変化が同時に発生しています。
  - *P*<sub>1</sub><span aria-hidden="true">P1​</span>の編集に伴って、溶け込んだ被改正法例の内容も変化し、 *A*<sub>*P*<sub>1</sub></sub><span aria-hidden="true">AP1​​</span>が *A*<sub>*P*<sub>2</sub></sub><span aria-hidden="true">AP2​​</span>になります。下の図では、これらをまとめて、バージョン *V*<sub>1</sub><span aria-hidden="true">V1​</span>からバージョン *V*<sub>2</sub><span aria-hidden="true">V2​</span>への変化として表しています。
- 現状、法令案の編集段階における統一的なバージョン管理の手法は定まっておらず、各々の部署で、ファイルのバックアップなどの任意の方法で管理されています。
<img src="/docs/_next/static/media/290-drafting-law.c0f575f1.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="法令案の編集" />
### 正誤<a href="#正誤" id="正誤"></a>
- ときには、公布後の法令について、誤字修正等を目的として官報正誤による修正が行われることがあります。この場合も、新たな編集バージョンとして考えることができます。
<img src="/docs/_next/static/media/300-correction.967d3c47.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="正誤" />
### 時系列上の修正<a href="#時系列上の修正" id="時系列上の修正"></a>
- 時系列上で改正規定を追加・削除する場合も編集バージョンの追加が発生します。
<img src="/docs/_next/static/media/310-editing-timeline.fbb4ef54.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="時系列上の修正" />
### 編集バージョンの分岐<a href="#編集バージョンの分岐" id="編集バージョンの分岐"></a>
- あるバージョンを元に改正法令案の編集を開始した後、別のところでも編集が行われることでバージョンが分岐することがあります。
- 例えば下の図では、バージョン *V*<sub>0</sub><span aria-hidden="true">V0​</span>を元に編集を始めてバージョン *V*<sub>*α*<sub>1</sub></sub><span aria-hidden="true">Vα1​​</span>を追加したところ、新たに改正法令が公布されてバージョン *V*<sub>*β*<sub>1</sub></sub><span aria-hidden="true">Vβ1​​</span>が別途追加された様子を表しています。
<img src="/docs/_next/static/media/320-edit-version-branching.8f343be5.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="編集バージョンの分岐" />
### 編集バージョンのマージ<a href="#編集バージョンのマージ" id="編集バージョンのマージ"></a>
- 法令案の編集中に新たに改正法令が公布されたなどの場合は、公布された改正法令を編集中の法令案の検討に反映する必要があります。
- 例えば下の図では、バージョン *V*<sub>*α*<sub>1</sub></sub><span aria-hidden="true">Vα1​​</span>で改正法令 *P*<sub>1</sub><span aria-hidden="true">P1​</span>を編集していたところ、新たに改正法令*Q*<span aria-hidden="true">Q</span>が公布されたバージョン *V*<sub>*β*<sub>1</sub></sub><span aria-hidden="true">Vβ1​​</span>が生じた様子を表しています。
- 単に*Q*<span aria-hidden="true">Q</span>を時系列に追加するだけでは、改正の競合などにより、時系列上の整合性が確保されません。下の図では、時系列上の整合性を確保するため、 *P*<sub>1</sub><span aria-hidden="true">P1​</span>を *P*<sub>2</sub><span aria-hidden="true">P2​</span>に更新し、新たなバージョン *V*<sub>*α*<sub>2</sub></sub><span aria-hidden="true">Vα2​​</span>を追加した様子を表しています。
<img src="/docs/_next/static/media/330-merging-edit-versions.a205599b.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="編集バージョンのマージ" />
### バージョンの分散管理<a href="#バージョンの分散管理" id="バージョンの分散管理"></a>
- 編集中の法令案は、必要に応じた情報管理が行われます。
- 各部署では、主に公開されているバージョンを元に編集を開始し、各部署内で編集を行った後、閣議決定や官報掲載などの手続により編集結果を公開します。
  - 繰り返しですが、現状、法令案の編集段階における統一的なバージョン管理の手法は定まっていません。現状は、概念的にはこのようなバージョン管理が行われていると考えますが、その具体的な方法は多くの場面で手作業であると考えられます。
<img src="/docs/_next/static/media/340-distributed-version-control.4c823839.svg" loading="lazy" decoding="async" data-nimg="1" width="2194" height="1430" alt="バージョンの分散管理" />
## Appendix<a href="#appendix" id="appendix"></a>
### 参考文献<a href="#参考文献" id="参考文献"></a>
本資料の作成に当たっては、下記資料を参考にしました。
- 法制執務研究会 編「新訂 ワークブック法制執務 第２版」
- <a href="https://houseikyoku.sangiin.go.jp/column/index.htm" target="_blank" rel="noreferrer">参議院法制局「法律の[窓]」<span> (opens in a new tab)</span></a>
Light
------------------------------------------------------------------------
[法令データ ドキュメンテーション（α版）](/docs/)
- 現時点で本サイトは、正式化前の試験的な公開（α版）です。本サイトの資料は、今後内容をブラッシュアップの上、正式化することを目指しています。
- 本サイトの内容は、特に注記のない限り、政府の公式見解を表すものではなく、執筆者の見解を示すものにとどまります。法令分野の専門家以外の方にもわかりやすい記述となるように、表現や業務内容を簡略化等している部分があり、政府の公式見解や実際の業務とは差異がある部分がありますので、その点御注意下さい。
- 本サイトの内容は、必ずしも常にアップデートされているとは限らず、また、予告なく URL が変更されたり削除される場合があります。
- 本サイトに記載のサンプルコードのご利用は、ご自身の責任で行ってください。万が一サンプルコードの利用による不利益が生じた場合でも、一切の責任を負いかねますのでご了承ください。

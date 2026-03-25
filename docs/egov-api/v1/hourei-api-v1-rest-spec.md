# e-Gov Law API v1 (REST-focused) - Rewritten Markdown Specification

**Source basis**: *e-Gov 法令 API 仕様書（Version 1）1.4版, 2024-07-29*.

## 0. Scope of this rewritten specification

This document is a **REST-focused rewrite** of the official e-Gov Law API v1 specification.

- It **focuses on HTTP endpoints, URI structure, parameters, response structure, status codes, and operational caveats**.
- It **does not attempt to restate XSD/XML schema artifacts in full**, because the original request explicitly prioritizes the REST API view and the XSD and related XML artifacts are provided separately.
- XML response elements are still documented to the extent necessary to use the REST API correctly.
- Where the official specification appears internally inconsistent, this rewrite **preserves the official statement where possible and flags the inconsistency explicitly** rather than silently normalizing it.

---

## 1. Overview

The API provides the following four endpoint groups:

1. **Law list API** (`lawlists`)  
   Returns a list of promulgated current laws, including law ID, law name, law number, and promulgation date.

2. **Law data API** (`lawdata`)  
   Returns the full text of an in-force current law. If the text includes images, image payload data can also be returned.

3. **Article content API** (`articles`)  
   Returns a specific article, paragraph, or appendix table from an in-force current law. If the requested content includes images, image payload data can also be returned.

4. **Updated law list API** (`updatelawlists`)  
   Returns the list of laws updated on a specified date. The returned fields are equivalent to the CSV export from the updated-law list screen in e-Gov Law Search.

### Notes on source wording

The official document says that it provides “**3 kinds of API**” while it actually enumerates **4 APIs**. This rewrite treats the endpoint list as authoritative and documents **4 APIs**.

---

## 2. Versioning and base URL

- **API version covered by this document**: `1`
- The `{Version}` path segment **must be set to `1`**.
- As of the 1.4 revision (2024-07-29), the host is:

```text
https://laws.e-gov.go.jp
```

### Base path

```text
https://laws.e-gov.go.jp/api/1
```

---

## 3. Common protocol behavior

## 3.1 HTTP method

All endpoints documented in the source specification use:

```text
GET
```

## 3.2 Response format

The official document states that the API returns **XML** together with an HTTP status code.

### Common XML envelope

Most responses follow this envelope:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<DataRoot>
  <Result>
    <Code>...</Code>
    <Message>...</Message>
  </Result>
  <ApplData>
    ...application data...
  </ApplData>
</DataRoot>
```

### Structural rules

- `Result` contains the API processing result.
- `ApplData` contains the application data.
- If a **system exception** occurs, `ApplData` is **not output**.
- In non-system-exception cases, `ApplData` is output even if there are no records; however, some child elements inside `ApplData` may be omitted depending on result count or endpoint rules.

## 3.3 Escaped characters in XML

The official document explicitly states that the following characters are escaped in the XML output:

| Original character | Escaped form |
|---|---|
| `'` | `&apos;` |
| `"` | `&quot;` |
| `&` | `&amp;` |
| `<` | `&lt;` |
| `>` | `&gt;` |

Consumers must unescape these as needed.

## 3.4 Common `Result.Code` values

| Code | Meaning | Notes |
|---|---|---|
| `0` | Success | Normal success |
| `1` | Error | Also used when the result count is `0`, and also when multiple law data records exist for a parameterized law number, according to the official code table |
| `2` | Multiple candidates | Used only by the **Article content API** when retrieving an appendix table and multiple candidates match |

### Important interpretation note

`Result.Code` and the HTTP status code are related but not identical:

- A request can return an HTTP-level success-like response semantics while still using an application-level `Code` to distinguish cases.
- In particular, the Article content API may return HTTP `300 Multiple Choices` and `Result.Code = 2` when an appendix-table request matches multiple candidate tables.

## 3.5 Message field

`Result/Message` contains a message string of up to 256 full-width/half-width characters.

- On normal success, it is empty.
- The official document says the message contents are defined in **“Appendix 1: Law API message list”**, but that appendix is **not included in the attached specification PDF**.
- Therefore, this rewrite documents the field but does **not invent or reconstruct the missing message catalog**.

---

## 4. Endpoint: Law list API

Returns a list of promulgated current laws.

## 4.1 Endpoint

```text
GET /api/1/lawlists/{category}
```

Full URI template:

```text
https://laws.e-gov.go.jp/api/{Version}/lawlists/{法令種別}
```

## 4.2 Path parameters

| Name | Required | Type in source | Description |
|---|---|---|---|
| `Version` | Yes | Alphanumeric | API version. Must be `1`. |
| `category` (`法令種別`) | Yes | Alphanumeric | Code for law category. See [§9 Law category codes](#9-law-category-codes). |

## 4.3 Category code values

| Code | Meaning |
|---|---|
| `1` | All laws |
| `2` | Constitution / Acts |
| `3` | Cabinet Orders / Imperial Orders |
| `4` | Ministerial Ordinances / Rules |

### Source note

In prose, the source sometimes shortens code `4` to “府省令”, but the formal code-definition table defines it as **“府省令・規則”**. This rewrite follows the code-definition table.

## 4.4 Response body

### XML declaration

```xml
<?xml version="1.0" encoding="UTF-8"?>
```

### Response structure

| Element | Repeat | Required | Character type | Length | Description |
|---|---:|---|---|---|---|
| `DataRoot` | 1 | Yes | - | - | Root element |
| `Result` | 1 | Yes | - | - | Processing result block |
| `Code` | 1 | Yes | Alphanumeric | 1 | Result code (`0` success, `1` error) |
| `Message` | 1 | Yes | Full-width/half-width | <=256 | Message; empty on success |
| `ApplData` | 1 | Conditional | - | - | Application data block; omitted only on system exception |
| `Category` | 1 | Yes within `ApplData` | Alphanumeric | 1 | Requested law category |
| `LawNameListInfo` | n | Conditional | - | - | List item for each law matching the requested category; omitted if count is 0 |
| `LawId` | 1 | Yes within `LawNameListInfo` | Half-width | >=1 | Law ID |
| `LawName` | 1 | Yes within `LawNameListInfo` | Full-width | >=1 | Law name |
| `LawNo` | 1 | Yes within `LawNameListInfo` | Full-width/half-width | >=1 | Law number |
| `PromulgationDate` | 1 | Yes within `LawNameListInfo` | Numeric | 8 | Promulgation date in `yyyyMMdd` |

## 4.5 HTTP status codes

| Status | Meaning |
|---|---|
| `200 OK` | Success |
| `400 Bad Request` | Error caused by API caller |
| `404 Not Found` | No matching data exists |
| `500 Internal Server Error` | Server-side processing error |

## 4.6 Example

```text
GET https://laws.e-gov.go.jp/api/1/lawlists/1
```

---

## 5. Endpoint: Law data API

Returns the full text of an in-force current law.

If the law text contains figures or other images, image payload data may also be returned.

## 5.1 Endpoint

```text
GET /api/1/lawdata/{lawNumOrLawId}
```

Full URI template:

```text
https://laws.e-gov.go.jp/api/{Version}/lawdata/{法令番号}又は{法令ID}
```

## 5.2 Path parameters

The official specification conceptually lists two alternative identifiers, but the URI shape is a **single path segment** containing **either** the law number **or** the law ID.

| Name | Required | Type in source | Description |
|---|---|---|---|
| `Version` | Yes | Alphanumeric | API version. Must be `1`. |
| `lawNum` | Either `lawNum` or `lawId` is required | Full-width/half-width | Target law number |
| `lawId` | Either `lawNum` or `lawId` is required | Half-width | Target law ID |

## 5.3 Response body

### Response structure

| Element | Repeat | Required | Character type | Length | Description |
|---|---:|---|---|---|---|
| `DataRoot` | 1 | Yes | - | - | Root element |
| `Result` | 1 | Yes | - | - | Processing result block |
| `Code` | 1 | Yes | Alphanumeric | 1 | Result code (`0` success, `1` error) |
| `Message` | 1 | Yes | Full-width/half-width | <=256 | Message; empty on success |
| `ApplData` | 1 | Conditional | - | - | Application data block; omitted only on system exception |
| `LawId` | 1 | Conditional | Half-width | `0` or `>=1` | Requested law ID |
| `LawNum` | 1 | Conditional | Full-width/half-width | `0` or `>=1` | Requested law number |
| `LawFullText` | 1 | Yes within successful data response | Full-width/half-width | >=1 | Full text of the matching law |
| `ImageData` | 1 | Conditional | Alphanumeric | >=1 | Image payload; output only if the law full text contains images |

### `ImageData` semantics

The official specification defines `ImageData` as:

- image data collected under a folder named `pict`
- each folder compressed in **ZIP** format
- the ZIP file then **Base64-encoded**

This element is output only when the law text contains image content.

## 5.4 HTTP status codes

| Status | Meaning |
|---|---|
| `200 OK` | Success |
| `400 Bad Request` | Error caused by API caller |
| `404 Not Found` | No matching data exists |
| `406 Not Acceptable` | Response exceeds the capacity the API can return, or multiple law data records exist |
| `500 Internal Server Error` | Server-side processing error |

## 5.5 Example

Using a law number:

```text
GET https://laws.e-gov.go.jp/api/1/lawdata/平成十五年法律第五十七号
```

Using a law ID:

```text
GET https://laws.e-gov.go.jp/api/1/lawdata/415AC0000000057
```

---

## 6. Endpoint: Article content API

Returns content from an in-force current law matching one of these target patterns:

- an **article**
- a **paragraph**
- a **paragraph under a specified article**
- an **appendix table**

If the returned content includes images, image payload data may also be returned.

## 6.1 Endpoint

```text
GET /api/1/articles;lawNum={...};article={...};paragraph={...};appdxTable={...}
```

Full URI template:

```text
https://laws.e-gov.go.jp/api/{Version}/articles;lawNum={法令番号};article={条};paragraph={項};appdxTable={別表}
```

The official specification also allows `lawId` instead of `lawNum`:

```text
https://laws.e-gov.go.jp/api/{Version}/articles;lawId={法令ID};article={条};paragraph={項};appdxTable={別表}
```

## 6.2 Important URI-shape note

This endpoint does **not** use a standard query string (`?key=value&...`).
It uses **semicolon-delimited path parameters** appended to the `/articles` path segment.

That URI form should be preserved when constructing requests.

## 6.3 Parameters

| Logical name | Parameter name | Required | Type in source | Description |
|---|---|---|---|---|
| `Version` | - | Yes | Alphanumeric | API version. Must be `1`. |
| Law number | `lawNum` | Either `lawNum` or `lawId` is required | Full-width/half-width | Target law number |
| Law ID | `lawId` | Either `lawNum` or `lawId` is required | Half-width | Target law ID |
| Article | `article` | Pattern-dependent | Full-width/half-width | Target article |
| Paragraph | `paragraph` | Pattern-dependent | Full-width/half-width | Target paragraph |
| Appendix table | `appdxTable` | Pattern-dependent | Full-width/half-width | Target appendix table |

## 6.4 Allowed parameter combinations

The official specification defines the following request patterns:

| Retrieval target | Law number or law ID | `article` | `paragraph` | `appdxTable` |
|---|---:|---:|---:|---:|
| Retrieve an article | Required | Required | - | - |
| Retrieve a paragraph | Required | - | Required | - |
| Retrieve a paragraph under an article | Required | Required | Required | - |
| Retrieve an appendix table | Required | - | - | Required |

### Implication

The API distinguishes between:

- **paragraph-only lookup** (`paragraph` without `article`), and
- **paragraph-under-article lookup** (`article` + `paragraph`).

This is explicitly reflected in the official parameter-pattern table and should not be collapsed.

## 6.5 Examples

Using law number:

```text
GET https://laws.e-gov.go.jp/api/1/articles;lawNum=平成十五年法律第五十七号;article=第十一条
```

Using law ID:

```text
GET https://laws.e-gov.go.jp/api/1/articles;lawId=415AC0000000057;article=第十一条
```

## 6.6 Response body

| Element | Repeat | Required | Character type | Length | Description |
|---|---:|---|---|---|---|
| `DataRoot` | 1 | Yes | - | - | Root element |
| `Result` | 1 | Yes | - | - | Processing result block |
| `Code` | 1 | Yes | Alphanumeric | 1 | Result code (`0` success / `1` error / `2` multiple candidates) |
| `Message` | 1 | Yes | Full-width/half-width | <=256 | Message; empty on success |
| `ApplData` | 1 | Conditional | - | - | Application data block; omitted only on system exception |
| `LawId` | 1 | Conditional | Half-width | `0` or `>=1` | Requested law ID |
| `LawNum` | 1 | Conditional | Full-width/half-width | `0` or `>=1` | Requested law number |
| `Article` | 1 | Conditional | Full-width/half-width | `0` or `>=1` | Requested article; empty when retrieving a paragraph-only target or an appendix table |
| `Paragraph` | 1 | Conditional | Full-width/half-width | `0` or `>=1` | Requested paragraph; empty when retrieving an article or an appendix table |
| `AppdxTable` | 1 | Conditional | Full-width/half-width | `0` or `>=1` | Requested appendix table; empty when retrieving an article, paragraph, or article-scoped paragraph |
| `LawContents` | 1 | Yes within successful data response | Full-width/half-width | >=1 | The law content matching the specified condition |
| `AppdxTableTitleLists` | 1 | Conditional | - | - | Candidate list of appendix table names; output only when `Code = 2` |
| `AppdxTableTitle` | n | Conditional | Full-width/half-width | >=1 | Candidate appendix table title |
| `ImageData` | 1 | Conditional | Alphanumeric | >=1 | Image payload; output only if the returned content includes images |

### `AppdxTableTitleLists` behavior

This element is returned **only** when the appendix-table request matches multiple candidates.
In that case:

- `Result.Code = 2`
- the HTTP status is `300 Multiple Choices`
- candidate titles are returned in `AppdxTableTitleLists/AppdxTableTitle`

### `ImageData` semantics

As in the Law data API, `ImageData` is:

- image data collected under a folder named `pict`
- compressed folder-by-folder as ZIP
- Base64-encoded

## 6.7 HTTP status codes

| Status | Meaning |
|---|---|
| `200 OK` | Success |
| `300 Multiple Choices` | Multiple candidates exist (only for appendix-table retrieval when multiple matches exist) |
| `400 Bad Request` | Error caused by API caller |
| `404 Not Found` | No matching data exists |
| `406 Not Acceptable` | Response exceeds the capacity the API can return, or multiple law data records exist |
| `500 Internal Server Error` | Server-side processing error |

## 6.8 Operational caveats

### Duplicate law numbers

If multiple laws share the same law number, the Article content API **cannot retrieve them using `lawNum`**.
In such cases, use **`lawId`** instead.

The official document also notes that XML for such laws can be obtained from the relevant article page or search-result list in e-Gov Law Search.

### Appendix-table matching is prefix-based

When retrieving an appendix table, the specified appendix-table name is matched by **prefix search**.
That is why multiple candidates can occur.

### Long appendix-table names may trigger `Request Rejected`

If the appendix-table value supplied in the URI is too long, the server may return an HTML page titled **`Request Rejected`** instead of the expected API XML.

The official guidance is:

1. shorten the appendix-table parameter value,
2. retry the Article content API,
3. if still unresolved, keep the timestamp and support ID and contact e-Gov support.

See [§11 `Request Rejected` HTML behavior](#11-request-rejected-html-behavior).

---

## 7. Endpoint: Updated law list API

Returns the list of laws updated on a specified date.

The official document states that the returned items are the same as those available in the CSV exported from the updated-law list screen of e-Gov Law Search.

## 7.1 Endpoint

```text
GET /api/1/updatelawlists/{date}
```

Full URI template:

```text
https://laws.e-gov.go.jp/api/{Version}/updatelawlists/{年月日}
```

## 7.2 Path parameters

| Name | Required | Type in source | Description |
|---|---|---|---|
| `Version` | Yes | Alphanumeric | API version. Must be `1`. |
| `date` (`年月日`) | Yes | Numeric | Target date in `yyyyMMdd` format |

### Date constraints

- Allowed dates are **2020-11-24 or later**.
- **Future dates cannot be specified**.

## 7.3 Response body

| Element | Repeat | Required | Character type | Length | Description |
|---|---:|---|---|---|---|
| `DataRoot` | 1 | Yes | - | - | Root element |
| `Result` | 1 | Yes | - | - | Processing result block |
| `Code` | 1 | Yes | Alphanumeric | 1 | Result code (`0` success, `1` error) |
| `Message` | 1 | Yes | Full-width/half-width | <=256 | Message; empty on success |
| `ApplData` | 1 | Conditional | - | - | Application data block; omitted only on system exception |
| `Date` | 1 | Yes within `ApplData` | Numeric | **source table says `1`** | Requested date |
| `LawNameListInfo` | n | Conditional | - | - | Updated-law entries matching the requested date; omitted if count is 0 |
| `LawTypeName` | 1 | Yes within `LawNameListInfo` | Full-width | >=1 | Law type name |
| `LawNo` | 1 | Yes within `LawNameListInfo` | Full-width/half-width | >=1 | Law number |
| `LawName` | 1 | Yes within `LawNameListInfo` | Full-width | >=1 | Law name |
| `LawNameKana` | 1 | Yes within `LawNameListInfo` | Full-width | >=1 | Reading of law name |
| `OldLawName` | 1 | Yes within `LawNameListInfo` | Full-width | >=1 | Old law name |
| `PromulgationDate` | 1 | Yes within `LawNameListInfo` | Numeric | 8 | Promulgation date |
| `AmendName` | 1 | Yes within `LawNameListInfo` | Full-width | >=1 | Amending law name |
| `AmendNo` | 1 | Yes within `LawNameListInfo` | Full-width | >=1 | Amending law number |
| `AmendPromulgationDate` | 1 | Yes within `LawNameListInfo` | Full-width | >=1 | Amending law promulgation date |
| `EnforcementDate` | 1 | Yes within `LawNameListInfo` | Full-width | >=1 | Enforcement date |
| `EnforcementComment` | 1 | Yes within `LawNameListInfo` | Full-width/half-width | >=1 | Enforcement-date note |
| `LawId` | 1 | Yes within `LawNameListInfo` | Half-width | >=1 | Law ID |
| `LawUrl` | 1 | Yes within `LawNameListInfo` | Half-width | >=1 | Full-text URL |
| `EnforcementFlg` | 1 | Yes within `LawNameListInfo` | Half-width | 1 | Enforcement flag (`0` = enforced, `1` = not yet enforced) |
| `AuthFlg` | 1 | Yes within `LawNameListInfo` | Half-width | 1 | Authority-confirmation flag (`0` = confirmed, `1` = under confirmation) |

## 7.4 HTTP status codes

| Status | Meaning |
|---|---|
| `200 OK` | Success |
| `400 Bad Request` | Error caused by API caller |
| `404 Not Found` | No matching data exists |
| `500 Internal Server Error` | Server-side processing error |

## 7.5 Important source inconsistency

The response-table row for `Date` lists its length as **`1`**, while:

- the request parameter is explicitly defined as `yyyyMMdd`, and
- semantically the returned date is the requested target date.

This appears to be an **internal inconsistency in the source specification**.
This rewrite preserves the official table value by quoting it above, but implementers should expect an 8-digit date string for an actual `yyyyMMdd` value.

---

## 8. HTTP status summary by endpoint

| Endpoint | 200 | 300 | 400 | 404 | 406 | 500 |
|---|---:|---:|---:|---:|---:|---:|
| Law list API | Yes | No | Yes | Yes | No | Yes |
| Law data API | Yes | No | Yes | Yes | Yes | Yes |
| Article content API | Yes | Yes | Yes | Yes | Yes | Yes |
| Updated law list API | Yes | No | Yes | Yes | No | Yes |

### Meaning of `406 Not Acceptable`

For the Law data API and Article content API, `406 Not Acceptable` is used when:

- the data exceeds the capacity the API can return, **or**
- multiple law data records exist.

---

## 9. Law category codes

Used by the Law list API path parameter `{法令種別}`.

| Code | Japanese label in source | Meaning |
|---|---|---|
| `1` | 全法令 | All laws |
| `2` | 憲法・法律 | Constitution / Acts |
| `3` | 政令・勅令 | Cabinet Orders / Imperial Orders |
| `4` | 府省令・規則 | Ministerial Ordinances / Rules |

---

## 10. Result-code summary

| `Result.Code` | Meaning | Used by |
|---|---|---|
| `0` | Success | All endpoints |
| `1` | Error | All endpoints |
| `2` | Multiple candidates | Article content API only, when appendix-table lookup yields multiple candidates |

---

## 11. `Request Rejected` HTML behavior

The official specification warns that, in some cases, the API does **not** return the expected XML body and instead returns an HTML document of the following form:

```html
<html>
<head>
<title>Request Rejected</title>
</head>
<body>
リクエスト内容を表示できません。お手数ですが、以下の Your support ID に続く数字を電子政府利用支援センターまでお知らせください。<br>
The requested URL was rejected. Please consult with support service call center for the e-Government service on online portal, e-Gov.<br><br>
Your support ID is: *******************
</body>
</html>
```

### Official handling guidance

If this occurs:

1. review whether the request parameter values were specified correctly,
2. correct the request and retry,
3. if the problem persists, record:
   - the transmission time, and
   - the value following `Your support ID is:`
4. then contact the e-Gov support center.

### Special relevance to appendix-table requests

The specification explicitly associates this condition with **Article content API** requests where the `appdxTable` parameter value is too long.

---

## 12. Practical implementation notes (clearly separated from the official text)

The following points are not new normative rules from the official PDF; they are developer-facing observations needed to apply the REST interface correctly.

### 12.1 The API is XML-first, not JSON-first

The official specification only documents **XML responses**. There is no JSON response format described in this source document.

### 12.2 The `articles` endpoint uses matrix-style parameters

Many HTTP client examples default to query parameters. That would produce a different URI shape.

The documented form is:

```text
/articles;lawNum=...;article=...
```

not:

```text
/articles?lawNum=...&article=...
```

### 12.3 The missing message catalog prevents a fully closed error taxonomy

Because the specification references an external “Appendix 1” for message texts and that appendix is absent from the provided file, a consumer can reliably use:

- HTTP status code,
- `Result.Code`, and
- the raw `Message` string,

but cannot derive a complete pre-defined message catalog from this PDF alone.

---

## 13. Minimal request examples

## 13.1 Get all law names

```http
GET https://laws.e-gov.go.jp/api/1/lawlists/1
```

## 13.2 Get a law by law ID

```http
GET https://laws.e-gov.go.jp/api/1/lawdata/415AC0000000057
```

## 13.3 Get an article by law number

```http
GET https://laws.e-gov.go.jp/api/1/articles;lawNum=平成十五年法律第五十七号;article=第十一条
```

## 13.4 Get a paragraph by law ID

```http
GET https://laws.e-gov.go.jp/api/1/articles;lawId=415AC0000000057;paragraph=第一項
```

## 13.5 Get an appendix table by law ID

```http
GET https://laws.e-gov.go.jp/api/1/articles;lawId=415AC0000000057;appdxTable=別表第一
```

## 13.6 Get updated laws for a date

```http
GET https://laws.e-gov.go.jp/api/1/updatelawlists/20240729
```

---

## 14. Condensed endpoint reference

| Endpoint | Purpose | Key identifier(s) | Special notes |
|---|---|---|---|
| `/api/1/lawlists/{category}` | List laws by category | Category code | Category codes are fixed numeric values |
| `/api/1/lawdata/{lawNumOrLawId}` | Get full law text | Law number or law ID | Returns `ImageData` if the text includes images |
| `/api/1/articles;...` | Get article / paragraph / appendix-table content | Law number or law ID + target selector | Uses semicolon path parameters; appendix-table requests may return multiple candidates |
| `/api/1/updatelawlists/{yyyyMMdd}` | List laws updated on a date | Date | Date must be >= `20201124` and not in the future |

---

## 15. Known source-level ambiguities and inconsistencies

This section is included so that the rewritten specification remains faithful to the source without pretending the source is perfectly uniform.

1. **Number of APIs**  
   The source says “3 kinds of API” but enumerates 4.

2. **Law category label for code `4`**  
   Prose shortens it to “府省令”, while the code-definition table gives “府省令・規則”. This rewrite follows the code-definition table.

3. **`Date` field length in Updated law list API**  
   The response table shows length `1`, even though the request date format is explicitly `yyyyMMdd`. This rewrite treats that as a source inconsistency and does not silently normalize the official table entry.

4. **External message appendix missing**  
   The PDF references “Appendix 1: Law API message list”, but that appendix is not part of the attached file.

---

## 16. Recommended use of identifiers

Where both are available, `lawId` is the more robust identifier because the official specification explicitly notes cases in which **law numbers are duplicated**, making `lawNum` insufficient for Article content API retrieval.

For implementations that need stable programmatic retrieval, this is the safer default identifier strategy.

#!/usr/bin/env zsh
# scrape-egov-docs.sh
# e-Gov 法令データドキュメンテーション（α版）のスクレイピングスクリプト
# Nextra (Next.js SSG) サイトから <article> 要素を抽出し、pandoc で Markdown 変換
#
# 依存: curl, pandoc, sed, awk
# 出力先: docs/egov-api/docs-alpha/

set -euo pipefail

BASE_URL="https://laws.e-gov.go.jp/docs"
OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/docs/egov-api/docs-alpha"

mkdir -p "$OUT_DIR"

# ページ定義: "slug|outfile" のペア配列
PAGES=(
  "|index"
  "law-data-basic/607318a-lawtypes-and-lawid|lawtypes-and-lawid"
  "law-data-basic/da91fe9-law-revisions|law-revisions"
  "law-data-basic/8ebd8bc-law-structure-and-xml|law-structure-and-xml"
  "law-data-basic/419a603-xml-schema-for-japanese-law|xml-schema"
  "law-data-basic/8529371-law-api-v1|law-api-v1"
  "docs/ba4d819-laws-and-version-control|version-control"
)

fetch_and_convert() {
  local slug="$1"
  local outfile="$2"
  local url="${BASE_URL}/${slug}"
  # trailing slash が必要（Nextra のルーティング）
  [[ -n "$slug" ]] && url="${url}/"

  echo "  Fetching: ${url}"

  curl -s "$url" \
    | awk '/<article/,/<\/article>/' \
    | sed 's/<img[^>]*base64[^>]*\/>//g' \
    | sed 's/<svg[^>]*>.*<\/svg>//g' \
    | sed 's/ class="[^"]*"//g' \
    | sed 's/ style="[^"]*"//g' \
    | sed 's/ aria-label="[^"]*"//g' \
    | sed 's/ dir="ltr"//g' \
    | sed 's/ role="[^"]*"//g' \
    | sed 's/ data-language="[^"]*"//g' \
    | sed 's/ theme="[^"]*"//g' \
    | pandoc --from html --to markdown_strict --wrap=none 2>/dev/null \
    | sed '/^$/N;/^\n$/d' \
    | sed 's/\[](#[^)]*){[^}]*}//g' \
    | sed 's/{[^}]*}//g' \
    | sed 's/^:::.*//g' \
    | grep -v '^$' \
    | sed '/^法令データの基本$/d' \
    | sed '/^法令API Version 1$/d; /^OpenAI API$/d' \
    > "${OUT_DIR}/${outfile}.md"

  # ソースURL をファイル先頭に付記
  local tmp
  tmp=$(mktemp)
  {
    echo "<!-- source: ${url} -->"
    echo "<!-- fetched: $(date -u +%Y-%m-%dT%H:%M:%SZ) -->"
    echo ""
    cat "${OUT_DIR}/${outfile}.md"
  } > "$tmp"
  mv "$tmp" "${OUT_DIR}/${outfile}.md"

  local lines
  lines=$(wc -l < "${OUT_DIR}/${outfile}.md" | tr -d ' ')
  echo "  → ${OUT_DIR}/${outfile}.md (${lines} lines)"
}

echo "=== e-Gov 法令データドキュメンテーション（α版）スクレイピング ==="
echo "出力先: ${OUT_DIR}"
echo ""

for entry in "${PAGES[@]}"; do
  slug="${entry%%|*}"
  outfile="${entry##*|}"
  fetch_and_convert "$slug" "$outfile"
done

echo ""
echo "=== 完了 ==="
ls -la "$OUT_DIR"

#!/usr/bin/env zsh
# SP Provenance Strength Comparison Test
# 「消費者基本法に罰則はある？」を4段階の SP 強度で試し、最終回答を比較する。

set -euo pipefail

BASE_URL="${LLM_BASE_URL:-http://localhost:8080/v1}"
MODEL="${LLM_MODEL:-local}"

QUESTION="${1:-消費者基本法に罰則はある？}"

# 法令ごとのデータを引数で切り替え
case "$QUESTION" in
  *刑法*)
    STRUCTURE_RESULT='目次: 刑法（明治四十年法律第四十五号） ■ 第一編 総則 ├ 第一章 通則（第一条―第八条） ├ 第二章 刑（第九条―第二十一条） ├ 第三章 期間計算（第二十二条―第二十四条） ├ 第四章 刑の執行猶予（第二十五条―第二十七条の七） ■ 第二編 罪 ├ 第二章 内乱に関する罪（第七十七条―第八十条） ├ 第二十六章 殺人の罪（第百九十九条―第二百三条） ■ 附則'
    SEARCH_RESULT="'罰則' を含む条文は見つかりませんでした。"
    SEARCH_ARGS='{"law_id":"刑法","keyword":"罰則"}'
    STRUCTURE_ARGS='{"law_id":"刑法"}'
    ;;
  *軽犯罪*)
    STRUCTURE_RESULT='目次: 軽犯罪法（昭和二十三年法律第三十九号） （章・節なし。第一条―第四条 + 附則）'
    SEARCH_RESULT="'罰則' を含む条文は見つかりませんでした。"
    SEARCH_ARGS='{"law_id":"軽犯罪法","keyword":"罰則"}'
    STRUCTURE_ARGS='{"law_id":"軽犯罪法"}'
    ;;
  *)
    STRUCTURE_RESULT='目次: 消費者基本法（昭和四十三年法律第七十八号） ■ 第一章 総則（第一条―第十条の二） ■ 第二章 基本的施策（第十一条―第二十三条） ■ 第三章 行政機関等（第二十四条―第二十六条） ■ 第四章 消費者政策会議等（第二十七条―第二十九条） ■ 附則'
    SEARCH_RESULT="'罰則' を含む条文は見つかりませんでした。"
    SEARCH_ARGS='{"law_id":"消費者基本法","keyword":"罰"}'
    STRUCTURE_ARGS='{"law_id":"消費者基本法"}'
    ;;
esac

run_test() {
  local label="$1"
  local sp_addition="$2"

  local full_sp="あなたは日本法に関するアシスタントです。必要に応じてツールを使ってください。"
  if [[ -n "$sp_addition" ]]; then
    full_sp="$full_sp $sp_addition"
  fi

  echo "================================================================"
  echo "=== $label ==="
  echo "================================================================"
  echo "SP: ${sp_addition:-（なし）}"
  echo ""

  local payload
  payload=$(jq -n \
    --arg model "$MODEL" \
    --arg sp "$full_sp" \
    --arg question "$QUESTION" \
    --arg struct "$STRUCTURE_RESULT" \
    --arg search "$SEARCH_RESULT" \
    --arg sargs "$STRUCTURE_ARGS" \
    --arg wargs "$SEARCH_ARGS" \
    '{
      model: $model,
      temperature: 0.0,
      max_tokens: 2048,
      tools: [
        {type:"function",function:{name:"search_within_law",description:"特定法令の条文内容をキーワード検索。法令名を直接指定可能。",parameters:{type:"object",properties:{law_id:{type:"string"},keyword:{type:"string"}},required:["law_id","keyword"]}}},
        {type:"function",function:{name:"get_law_structure",description:"法令の構造（目次）を表示。法令名を直接指定可能。",parameters:{type:"object",properties:{law_id:{type:"string"}},required:["law_id"]}}},
        {type:"function",function:{name:"get_article",description:"条文を取得。法令IDまたは法令名と条番号を指定。",parameters:{type:"object",properties:{law_id_or_name:{type:"string"},article_number:{type:"string"}},required:["law_id_or_name","article_number"]}}}
      ],
      messages: [
        {role:"system",content:$sp},
        {role:"user",content:$question},
        {role:"assistant",content:null,tool_calls:[{id:"c1",type:"function",function:{name:"get_law_structure",arguments:$sargs}}]},
        {role:"tool",tool_call_id:"c1",content:$struct},
        {role:"assistant",content:null,tool_calls:[{id:"c2",type:"function",function:{name:"search_within_law",arguments:$wargs}}]},
        {role:"tool",tool_call_id:"c2",content:$search}
      ]
    }')

  local tmpfile
  tmpfile=$(mktemp)
  curl -s "$BASE_URL/chat/completions" \
    -H "Content-Type: application/json" \
    -d "$payload" > "$tmpfile"

  local finish content tool_calls
  finish=$(jq -r '.choices[0].finish_reason' < "$tmpfile" 2>/dev/null || echo "parse_error")
  content=$(jq -r '.choices[0].message.content // ""' < "$tmpfile" 2>/dev/null || echo "(parse error)")
  tool_calls=$(jq -r '[.choices[0].message.tool_calls[]? | .function.name] | join(", ")' < "$tmpfile" 2>/dev/null || echo "")
  rm -f "$tmpfile"

  echo "finish: $finish"
  [[ -n "$tool_calls" ]] && echo "tool_calls: $tool_calls"
  echo ""
  echo "--- Response ---"
  echo "$content"
  echo ""
  echo ""
}

run_test "0_Baseline" ""

run_test "1_Light" \
  "ツールで確認できていない事実について断言しないこと。確認した範囲と根拠を明示すること。"

run_test "2_Moderate" \
  "事実的な主張にはツール結果による根拠を示すこと。ツールで確認していない事実は推測であることを明示すること。"

run_test "3_Strong" \
  "すべての事実的主張はツールで確認した結果に基づくこと。ツールで確認していない事実を述べないこと。内部知識による補足は、ツール結果を提示した上で付記すること。"

/** OpenAI Chat Completions API 互換のメッセージ型を Scala 3 ADT として定義する。
  *
  * == 設計判断: なぜ ADT か ==
  * LLM API のメッセージは role によって構造が異なる（`assistant` は `tool_calls` を持ちうるが
  * `user` は持たない、など）。JSON (`Map[String, Any]`) で扱うと、これらの構造差異が
  * 実行時まで検出できない。Scala 3 の enum ADT にすることで:
  *  - パターンマッチの網羅性チェック（`match` の漏れがコンパイルエラーに）
  *  - `toolCalls` の有無が型レベルで表現される
  *  - [[agent.AgentLoop]] のループ制御が型安全に
  *
  * == JSON シリアライゼーション ==
  * [[ChatMessage.toJson]] / [[ChatMessage.fromJson]] は OpenAI Chat Completions API の
  * JSON 形式との相互変換を行う。重要な非対称性:
  *  - '''`tool_calls[].function.arguments`''': API レスポンスでは JSON '''文字列'''（`"{\"law_name\":\"民法\"}"`）
  *    として返される。`fromJson` はこれをパースして `JsonObject` に変換する。
  *    `toJson` は逆に `JsonObject` を JSON 文字列にシリアライズして API に送り返す。
  *
  * @see [[agent.AgentLoop]] 本 ADT を消費するメインのエージェントループ
  * @see [[agent.ConversationState]] 本 ADT のリストをセッションとして永続化する
  */
package messages

import io.circe.*
import io.circe.syntax.*

/** ツール呼び出し1件の情報。[[ChatMessage.Assistant]] の `toolCalls` リストの要素。
  *
  * @param id        API が割り当てる一意の ID。`ToolResult` で結果を返す際に使用する。
  *                  フォーマットはバックエンド依存（llama.cpp と OpenAI で異なる）。
  * @param name      ツール名。[[tools.ToolDispatch.dispatch]] の `name` 引数に渡される。
  * @param arguments パース済みの引数。API レスポンスでは JSON 文字列だが、
  *                  `fromJson` で `JsonObject` に変換済み。
  */
case class ToolCallInfo(id: String, name: String, arguments: JsonObject)

/** OpenAI Chat Completions API 互換のメッセージ ADT。
  *
  * '''不変条件''':
  *  - `Assistant` の `toolCalls` が非空 → LLM がツール実行を要求している
  *  - `Assistant` の `toolCalls` が空 → LLM の最終回答（エージェントループの終了条件）
  *
  * '''拡張ポイント''': 新しいロール（例: OpenAI の `developer`）を追加するには、
  * enum にバリアントを追加し、`toJson` / `fromJson` の両方を更新する。
  */
enum ChatMessage {
  case System(content: String)
  case User(content: String)
  case Assistant(content: Option[String], toolCalls: List[ToolCallInfo], reasoning: Option[String] = None)
  case ToolResult(toolCallId: String, content: String)
}

object ChatMessage {

  /** ChatMessage を OpenAI Chat Completions API の JSON 形式に変換する。
    *
    * この JSON は LLM API リクエストの `messages` 配列に含まれる。
    * `tool_calls` 付きの `Assistant` メッセージでは、`arguments` を JSON 文字列に
    * 再シリアライズする（API がネストされた JSON ではなく文字列を期待するため）。
    */
  def toJson(msg: ChatMessage): Json = msg match {
    case ChatMessage.System(c) =>
      Json.obj("role" -> Json.fromString("system"), "content" -> Json.fromString(c))
    case ChatMessage.User(c) =>
      Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(c))
    case ChatMessage.Assistant(content, toolCalls, _) if toolCalls.nonEmpty =>
      Json.obj(
        "role" -> Json.fromString("assistant"),
        "content" -> content.map(Json.fromString).getOrElse(Json.Null),
        "tool_calls" -> Json.arr(toolCalls.map { tc =>
          Json.obj(
            "id" -> Json.fromString(tc.id),
            "type" -> Json.fromString("function"),
            "function" -> Json.obj(
              "name" -> Json.fromString(tc.name),
              "arguments" -> Json.fromString(tc.arguments.asJson.noSpaces)
            )
          )
        }*)
      )
    case ChatMessage.Assistant(content, _, _) =>
      Json.obj(
        "role" -> Json.fromString("assistant"),
        "content" -> Json.fromString(content.getOrElse(""))
      )
    case ChatMessage.ToolResult(id, c) =>
      Json.obj(
        "role" -> Json.fromString("tool"),
        "tool_call_id" -> Json.fromString(id),
        "content" -> Json.fromString(c)
      )
  }

  /** LLM API レスポンスまたはセッション JSON から ChatMessage を復元する。
    *
    * 未知の `role` に対しては `None` を返す（前方互換性）。
    * `tool_calls` の `arguments` は JSON 文字列からパースして `JsonObject` に変換する。
    *
    * @see [[agent.ConversationState]] セッション復元時に使用
    */
  def fromJson(json: Json): Option[ChatMessage] = {
    val c = json.hcursor
    c.downField("role").as[String].toOption.flatMap {
      case "system" =>
        c.downField("content").as[String].toOption.map(ChatMessage.System(_))
      case "user" =>
        c.downField("content").as[String].toOption.map(ChatMessage.User(_))
      case "assistant" =>
        val content = c.downField("content").as[String].toOption
        val toolCalls = c.downField("tool_calls").as[List[Json]].toOption.getOrElse(Nil).flatMap { tc =>
          val h = tc.hcursor
          for {
            id <- h.downField("id").as[String].toOption
            name <- h.downField("function").downField("name").as[String].toOption
            argsStr <- h.downField("function").downField("arguments").as[String].toOption
            args <- io.circe.parser.parse(argsStr).flatMap(_.as[JsonObject]).toOption
          } yield ToolCallInfo(id, name, args)
        }
        val reasoning = c.downField("reasoning_content").as[String].toOption
        Some(ChatMessage.Assistant(content, toolCalls, reasoning))
      case "tool" =>
        for {
          id <- c.downField("tool_call_id").as[String].toOption
          content <- c.downField("content").as[String].toOption
        } yield ChatMessage.ToolResult(id, content)
      case _ => None
    }
  }
}

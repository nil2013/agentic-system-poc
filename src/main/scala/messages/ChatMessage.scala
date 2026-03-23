package messages

import io.circe.*
import io.circe.syntax.*

case class ToolCallInfo(id: String, name: String, arguments: JsonObject)

enum ChatMessage {
  case System(content: String)
  case User(content: String)
  case Assistant(content: Option[String], toolCalls: List[ToolCallInfo])
  case ToolResult(toolCallId: String, content: String)
}

object ChatMessage {

  def toJson(msg: ChatMessage): Json = msg match {
    case ChatMessage.System(c) =>
      Json.obj("role" -> Json.fromString("system"), "content" -> Json.fromString(c))
    case ChatMessage.User(c) =>
      Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(c))
    case ChatMessage.Assistant(content, toolCalls) if toolCalls.nonEmpty =>
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
    case ChatMessage.Assistant(content, _) =>
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

  /** API レスポンスの JSON から ChatMessage を復元 */
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
        Some(ChatMessage.Assistant(content, toolCalls))
      case "tool" =>
        for {
          id <- c.downField("tool_call_id").as[String].toOption
          content <- c.downField("content").as[String].toOption
        } yield ChatMessage.ToolResult(id, content)
      case _ => None
    }
  }
}

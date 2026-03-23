package agent

import messages.*
import io.circe.*
import io.circe.syntax.*
import io.circe.parser.{parse => parseJson}
import java.nio.file.{Files, Path, Paths}

class ConversationState(sessionId: String, sessionsDir: Path = Paths.get("sessions")) {

  private val path = sessionsDir.resolve(s"$sessionId.json")

  private var _messages: List[ChatMessage] = {
    if (Files.exists(path)) {
      val text = Files.readString(path)
      parseJson(text).flatMap(_.as[List[Json]]).toOption.getOrElse(Nil)
        .flatMap(ChatMessage.fromJson)
    } else {
      Nil
    }
  }

  def messages: List[ChatMessage] = _messages

  def add(msg: ChatMessage): Unit = {
    _messages = _messages :+ msg
  }

  def addAll(msgs: List[ChatMessage]): Unit = {
    _messages = _messages ++ msgs
  }

  def save(): Unit = {
    Files.createDirectories(sessionsDir)
    val jsonArr = Json.arr(_messages.map(ChatMessage.toJson)*)
    Files.writeString(path, jsonArr.spaces2)
  }

  /** 粗いトークン数推定（JSON 文字列長ベース） */
  def estimateTokens: Int = {
    _messages.map(ChatMessage.toJson).map(_.noSpaces.length).sum
  }

  /** コンテキスト制限を超える場合、古いメッセージを削除 */
  def truncateIfNeeded(maxTokens: Int = 6000): List[ChatMessage] = {
    var truncated = List.empty[ChatMessage]
    while (estimateTokens > maxTokens && _messages.size > 2) {
      // system prompt (先頭) と最新の user message は保持
      // それ以外の古いものから削除
      val removed = _messages(1)
      _messages = _messages.head :: _messages.drop(2)
      truncated = truncated :+ removed
    }
    truncated
  }

  def messageCount: Int = _messages.size

  override def toString: String =
    s"ConversationState($sessionId, ${_messages.size} messages, ~${estimateTokens} est. tokens)"
}

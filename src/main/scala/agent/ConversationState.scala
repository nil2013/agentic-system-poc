package agent

import messages.*
import io.circe.*
import io.circe.syntax.*
import io.circe.parser.{parse => parseJson}
import java.nio.file.{Files, Path, Paths}

/** 会話セッションの状態を管理し、JSON ファイルに永続化する。
  *
  * == セッション管理 ==
  * `sessionId` に基づいて `sessions/{sessionId}.json` にメッセージ履歴を保存・復元する。
  * コンストラクタ実行時に既存ファイルがあれば自動的にロードする。
  *
  * == コンテキストウィンドウ管理 ==
  * LLM のコンテキストウィンドウは有限（`-c 8192` の場合 8192 トークン）であり、
  * 対話が長くなるとメッセージ履歴がこの上限に近づく。[[truncateIfNeeded]] は
  * 古いメッセージから削除してコンテキスト内に収める。
  *
  * == 既知の制限 ==
  *  - '''スレッドセーフティ''': `var _messages` に同期機構がない。単一スレッドでの使用を前提とする
  *  - '''トークン推定の精度''': [[estimateTokens]] は JSON 文字列の文字数を数えるヒューリスティック。
  *    実際のトークン数とは 3-4 倍のずれがある（日本語 1 文字 ≈ 1-3 トークン）。
  *    精密なトークンカウントが必要な場合は tiktoken 相当のライブラリが必要
  *  - '''切り詰めの粒度''': [[truncateIfNeeded]] はメッセージ単位で削除するため、
  *    ツール呼び出し（`Assistant` + `ToolResult`）のペアが分断される可能性がある
  *
  * @param sessionId   セッション識別子。ファイル名に使用される
  * @param sessionsDir セッションファイルの保存先ディレクトリ（デフォルト: `sessions/`、gitignore 対象）
  *
  * @see [[AgentLoop.runTurn]] 本クラスの `messages` を入力として受け取る
  * @see [[ConversationLogger]] 会話ログの markdown 出力（本クラスとは独立）
  */
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

  /** 現在のメッセージ履歴（読み取り専用ビュー） */
  def messages: List[ChatMessage] = _messages

  /** メッセージを1件追加する */
  def add(msg: ChatMessage): Unit = {
    _messages = _messages :+ msg
  }

  /** メッセージを複数件追加する */
  def addAll(msgs: List[ChatMessage]): Unit = {
    _messages = _messages ++ msgs
  }

  /** メッセージ履歴を JSON ファイルに保存する。ディレクトリがなければ作成する。 */
  def save(): Unit = {
    Files.createDirectories(sessionsDir)
    val jsonArr = Json.arr(_messages.map(ChatMessage.toJson)*)
    Files.writeString(path, jsonArr.spaces2)
  }

  /** メッセージ履歴のトークン数を粗く推定する。
    *
    * 各メッセージの JSON 文字列長の合計を返す。実際のトークン数ではなく、
    * [[truncateIfNeeded]] の閾値判定用のヒューリスティック。
    * '''`maxTokens` パラメータ名は misnomer'''（実態は文字数）。
    */
  def estimateTokens: Int = {
    _messages.map(ChatMessage.toJson).map(_.noSpaces.length).sum
  }

  /** コンテキスト制限を超える場合、古いメッセージを先頭から削除する。
    *
    * '''切り詰め戦略''': system prompt（インデックス 0）を保持し、インデックス 1 から
    * 順に削除する（FIFO）。最新のメッセージほど保持される。
    *
    * @param maxTokens 推定文字数の上限（デフォルト: 6000）。
    *                  '''注意: 実際のトークン数ではなく [[estimateTokens]] の値を使用する'''
    * @return 削除されたメッセージのリスト（ログ出力用）
    */
  def truncateIfNeeded(maxTokens: Int = 6000): List[ChatMessage] = {
    var truncated = List.empty[ChatMessage]
    while (estimateTokens > maxTokens && _messages.size > 2) {
      val removed = _messages(1)
      _messages = _messages.head :: _messages.drop(2)
      truncated = truncated :+ removed
    }
    truncated
  }

  /** 現在のメッセージ数 */
  def messageCount: Int = _messages.size

  override def toString: String =
    s"ConversationState($sessionId, ${_messages.size} messages, ~${estimateTokens} est. tokens)"
}

package agent

/** LLM 推論中のドットアニメーション表示。
  *
  * 500ms 間隔で `Thinking.` → `Thinking..` → ... → `Thinking.....` → `Thinking.` と
  * ドットを 1-5 でサイクルする。`\r` キャリッジリターンで同一行を上書き。
  *
  * == スレッドセーフティ ==
  * `start()` と `stop()` は同一スレッドから呼ぶこと（AgentLoop のメインスレッド）。
  * 内部のアニメーションスレッドは `@volatile` フラグで制御。
  *
  * == 使用パターン ==
  * {{{
  * val spinner = new Spinner()
  * spinner.start()
  * try {
  *   val result = blockingCall()
  * } finally {
  *   spinner.stop()
  * }
  * }}}
  */
class Spinner(label: String = "Thinking") {

  @volatile private var running = false
  private var thread: Thread = _

  /** アニメーションを開始する。既に実行中の場合は何もしない。 */
  def start(): Unit = {
    if (running) { return }
    running = true
    thread = new Thread(() => {
      var dots = 1
      while (running) {
        val display = label + "." * dots + " " * (5 - dots)
        print(s"\r  $display")
        System.out.flush()
        dots = if (dots >= 5) 1 else dots + 1
        try {
          Thread.sleep(500)
        } catch {
          case _: InterruptedException =>
            running = false
        }
      }
    }, "spinner-thread")
    thread.setDaemon(true)
    thread.start()
  }

  /** アニメーションを停止し、行をクリアする。 */
  def stop(): Unit = {
    running = false
    if (thread != null) {
      thread.interrupt()
      thread.join(1000)
    }
    print("\r" + " " * (label.length + 10) + "\r")
    System.out.flush()
  }
}

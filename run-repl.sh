#!/bin/bash
# Stage 8 REPL を sbt を介さずに直接 JVM で起動するスクリプト。
# sbt fork の TTY 継承問題を切り分けるために使用。
#
# 使い方:
#   ./run-repl.sh                                    # デフォルト
#   ./run-repl.sh --url http://host:port/v1          # 接続先指定
#   ./run-repl.sh --url http://host:port/v1 my-session  # セッション指定

set -e

# sbt compile が必要（クラスファイルが最新であること）
echo "Checking compilation..."
sbt --batch compile 2>/dev/null || { echo "sbt compile failed"; exit 1; }

# classpath を取得
CP=$(sbt --batch "export runtime:fullClasspath" 2>/dev/null | tail -1)

echo "Starting REPL (direct JVM)..."
exec java \
  --enable-native-access=ALL-UNNAMED \
  -Dfile.encoding=UTF-8 \
  -cp "$CP" \
  stages.Stage8Main "$@"

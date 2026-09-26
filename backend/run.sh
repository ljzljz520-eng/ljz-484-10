#!/usr/bin/env bash
# 启动服务：./run.sh [端口] [数据目录] [前端目录]
set -e
cd "$(dirname "$0")"

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
  echo "错误：未找到 java，请先安装 JDK 11+ 或设置 JAVA_HOME" >&2
  exit 1
fi

[ -d out ] || ./build.sh
exec "$JAVA_BIN" -Dfile.encoding=UTF-8 -cp out legalcase.Main "$@"

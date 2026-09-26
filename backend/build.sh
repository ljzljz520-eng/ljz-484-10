#!/usr/bin/env bash
# 编译全部 Java 源码到 out/（仅需 JDK 11+，不依赖 Maven/Gradle）
set -e
cd "$(dirname "$0")"

# 定位 javac：优先 JAVA_HOME，其次 PATH
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
if ! command -v "$JAVAC" >/dev/null 2>&1; then
  echo "错误：未找到 javac，请先安装 JDK 11+ 或设置 JAVA_HOME" >&2
  exit 1
fi

rm -rf out
mkdir -p out
find src -name '*.java' > sources.txt
"$JAVAC" -encoding UTF-8 -d out @sources.txt
rm -f sources.txt
echo "编译完成 -> backend/out/"

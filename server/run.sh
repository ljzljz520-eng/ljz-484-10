#!/usr/bin/env bash
# 一键编译并启动法律案例讲解稿系统（需要 JDK 11+，推荐 17）
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name '*.java')
echo "编译完成，启动服务..."
exec java -cp out com.lawcase.Main

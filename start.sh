#!/bin/bash
# ============================================================================
# FountainHub 一键启动（给 Linux / macOS 用；Windows 请直接用 java -jar 或 mvn）
# ============================================================================
#
# 【前置条件】
#   1) 已建库 + 已按顺序导入 sql/ 下 6 个脚本（见 README「本地运行指南」）
#   2) 已把 src/main/resources/application-local.yml.example
#      复制成 application-local.yml 并填好数据库密码（该文件不进 Git）
#   3) 已 mvn clean package（或已拿到现成的 jar）
#
# 【用法】
#   chmod +x start.sh && ./start.sh
#
# ============================================================================

set -e
cd "$(dirname "$0")"

# ---- ① 首次启动、且数据库 users 表为空时，用它建 3 个测试账号 ----
#      ⚠️ 只在"空库第一次启动"时生效；不设这个变量就【不建账号】（代码里没有默认口令）
#      ⚠️ 别把真实密码提交进 Git —— 本地测试用 123456 就够了
export FH_INIT_PASSWORD="${FH_INIT_PASSWORD:-123456}"

# ---- ② 找到 jar（target 下的，或本目录下已放好的）----
JAR=""
for candidate in ./fountainhub.jar ./target/fountainhub-*.jar; do
    if [ -f "$candidate" ]; then JAR="$candidate"; break; fi
done

if [ -z "$JAR" ]; then
    echo "❌ 没找到 jar。请先执行： mvn clean package -DskipTests"
    exit 1
fi

# ---- ③ 数据库配置：优先用同目录的 application-local.yml ----
if [ -f ./application-local.yml ]; then
    echo "✅ 使用 ./application-local.yml 作为数据库配置"
    exec java -jar "$JAR" --spring.config.additional-location=file:./application-local.yml
else
    echo "⚠️  没找到 ./application-local.yml，将尝试用 classpath 里的配置启动"
    echo "    （如果你是照着 README 做的，应该先把 application-local.yml 拷到本目录或 src/main/resources/）"
    exec java -jar "$JAR"
fi
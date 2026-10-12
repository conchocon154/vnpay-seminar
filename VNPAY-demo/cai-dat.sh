#!/bin/sh
# Cài thư viện cho demo VNPAY trên macOS và Linux.
# Chạy:  sh cai-dat.sh          (Java + org.json)
#        sh cai-dat.sh --ngrok  (thêm ngrok để chạy sandbox thật)
set -eu
cd "$(dirname "$0")"

JSON_VER=20250517
JSON_URL="https://repo1.maven.org/maven2/org/json/json/$JSON_VER/json-$JSON_VER.jar"
JSON_SHA1=d67181bbd819ccceb929b580a4e2fcb0c8b17cd8
JAR=lib/json.jar

hoi() { printf '%s [y/N] ' "$1"; read -r tl; [ "$tl" = y ] || [ "$tl" = Y ]; }

java_major() {
    command -v java >/dev/null 2>&1 || { echo 0; return; }
    v=$(java -version 2>&1 | head -n 1 | sed -E 's/.*version "([^"]+)".*/\1/')
    case "$v" in 1.*) echo "$v" | cut -d. -f2 ;; *) echo "$v" | cut -d. -f1 ;; esac
}

echo "== 1. Java (cần 17 trở lên)"
if [ "$(java_major)" -ge 17 ] 2>/dev/null; then
    echo "Đã có: $(java -version 2>&1 | head -n 1)"
else
    echo "Chưa có Java 17 trở lên."
    if [ "$(uname)" = Darwin ] && command -v brew >/dev/null 2>&1; then
        hoi "Cài Temurin JDK 21 bằng Homebrew?" && brew install --cask temurin@21
    elif command -v apt-get >/dev/null 2>&1; then
        hoi "Cài OpenJDK 21 bằng apt (cần mật khẩu sudo)?" && sudo apt-get update && sudo apt-get install -y openjdk-21-jdk
    fi
    if ! [ "$(java_major)" -ge 17 ] 2>/dev/null; then
        echo "Hãy tải JDK 21 tại https://adoptium.net, cài xong mở terminal mới rồi chạy lại script này."
        exit 1
    fi
fi

echo "== 2. Thư viện org.json $JSON_VER"
sha1() { if command -v shasum >/dev/null 2>&1; then shasum "$1"; else sha1sum "$1"; fi | cut -d' ' -f1; }
if [ -f "$JAR" ] && [ "$(sha1 "$JAR")" = "$JSON_SHA1" ]; then
    echo "Đã có $JAR"
else
    mkdir -p lib
    echo "Tải từ Maven Central..."
    curl -fsSL "$JSON_URL" -o "$JAR.tmp"
    if [ "$(sha1 "$JAR.tmp")" != "$JSON_SHA1" ]; then
        rm -f "$JAR.tmp"; echo "File tải về không đúng checksum, dừng lại."; exit 1
    fi
    mv "$JAR.tmp" "$JAR"; echo "Đã tải $JAR"
fi

if [ "${1:-}" = "--ngrok" ]; then
    echo "== 3. ngrok"
    if command -v ngrok >/dev/null 2>&1; then
        echo "Đã có ngrok"
    elif [ "$(uname)" = Darwin ] && command -v brew >/dev/null 2>&1; then
        brew install --cask ngrok
    else
        echo "Tải ngrok tại https://ngrok.com/download"
    fi
    echo "Nhớ chạy: ngrok config add-authtoken <token trong trang ngrok>"
fi

echo "== Biên dịch và kiểm tra"
javac --release 17 -cp "$JAR" SeminarDemo.java DemoChecks.java
java -cp ".:$JAR" DemoChecks
echo
echo "Xong. Chạy demo:  java -cp '.:lib/json.jar' SeminarDemo"
echo "Rồi mở http://localhost:8090"

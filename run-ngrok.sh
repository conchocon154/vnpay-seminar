#!/usr/bin/env bash
# BẢN 2 — CÓ NGROK
# Mở tunnel public để VNPAY gọi IPN thật vào máy bạn, đúng kiến trúc chuẩn.
# Chương trình tự đọc URL ngrok cấp, bạn chỉ việc dán 2 URL vào merchant portal.
set -euo pipefail
cd "$(dirname "$0")"
[ -f .env ] && { set -a; . ./.env; set +a; }
: "${VNPAY_TMN_CODE:?Chưa có VNPAY_TMN_CODE — điền vào file .env}"
: "${VNPAY_HASH_SECRET:?Chưa có VNPAY_HASH_SECRET — điền vào file .env}"

command -v ngrok >/dev/null || { echo "Chưa cài ngrok: brew install --cask ngrok"; exit 1; }
ngrok config check >/dev/null 2>&1 || {
  echo "Chưa có authtoken. Lấy tại https://dashboard.ngrok.com/get-started/your-authtoken rồi chạy:"
  echo "  ngrok config add-authtoken <token>"
  exit 1
}

# Dùng lại tunnel đang mở nếu có, không thì mở mới
if ! curl -sf -o /dev/null http://127.0.0.1:4040/api/tunnels; then
  ngrok http 8080 --log=stdout > /tmp/ngrok-vnpay.log 2>&1 &
  NGROK_PID=$!
  trap 'kill $NGROK_PID 2>/dev/null || true' EXIT
  echo "Đang mở tunnel ngrok..."
  for _ in $(seq 1 30); do
    curl -sf -o /dev/null http://127.0.0.1:4040/api/tunnels && break
    sleep 1
  done
fi

export VNPAY_MODE=ngrok
unset VNPAY_PUBLIC_URL VNPAY_RETURN_URL 2>/dev/null || true
exec java VnpayDemo.java

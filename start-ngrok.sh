#!/usr/bin/env bash
# Mở tunnel ngrok, lấy URL công khai, rồi chạy Spring Boot với đúng ReturnURL.
# Gõ một lệnh là xong: ./start-ngrok.sh
set -euo pipefail

PORT=${PORT:-8080}
: "${VNPAY_TMN_CODE:?Chua export VNPAY_TMN_CODE}"
: "${VNPAY_HASH_SECRET:?Chua export VNPAY_HASH_SECRET}"

command -v ngrok >/dev/null || { echo "Chua cai ngrok: brew install --cask ngrok"; exit 1; }
if ! ngrok config check >/dev/null 2>&1; then
  echo "Chua co authtoken. Lay tai https://dashboard.ngrok.com/get-started/your-authtoken roi chay:"
  echo "  ngrok config add-authtoken <token>"
  exit 1
fi

pkill -f "ngrok http" 2>/dev/null || true
ngrok http "$PORT" --log=stdout > /tmp/ngrok-vnpay.log 2>&1 &
NGROK_PID=$!
trap 'kill $NGROK_PID 2>/dev/null || true' EXIT

echo "Dang cho ngrok cap URL..."
PUBLIC_URL=""
for _ in $(seq 1 30); do
  PUBLIC_URL=$(curl -s http://127.0.0.1:4040/api/tunnels \
    | python3 -c 'import sys,json;t=json.load(sys.stdin)["tunnels"];print(next((x["public_url"] for x in t if x["public_url"].startswith("https")),""))' 2>/dev/null || true)
  [ -n "$PUBLIC_URL" ] && break
  sleep 1
done
[ -n "$PUBLIC_URL" ] || { echo "Khong lay duoc URL ngrok. Xem /tmp/ngrok-vnpay.log"; exit 1; }

export VNPAY_RETURN_URL="$PUBLIC_URL/vnpay/return"

cat <<INFO

============================================================
  Web:        $PUBLIC_URL
  ReturnURL:  $VNPAY_RETURN_URL
  IPN URL:    $PUBLIC_URL/vnpay/ipn      <-- dan vao merchant portal
============================================================
  Khai bao 2 URL tren tai https://sandbox.vnpayment.vn/merchantv2/
  Muc: Cau hinh / Thong tin website. URL doi moi lan chay lai ngrok.
============================================================

INFO

mvn -B spring-boot:run

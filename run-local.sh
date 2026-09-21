#!/usr/bin/env bash
# Bản không cần ngrok.
# Chạy hết trên localhost. IPN không về được nên đơn chốt bằng API querydr,
# tức là server tự hỏi VNPAY trạng thái thật.
# Hợp khi cả lớp cùng chạy trên máy mình.
set -euo pipefail
cd "$(dirname "$0")"
[ -f .env ] && { set -a; . ./.env; set +a; }
: "${VNPAY_TMN_CODE:?Chưa có VNPAY_TMN_CODE — điền vào file .env}"
: "${VNPAY_HASH_SECRET:?Chưa có VNPAY_HASH_SECRET — điền vào file .env}"

export VNPAY_MODE=local
unset VNPAY_PUBLIC_URL VNPAY_RETURN_URL 2>/dev/null || true
exec java VnpayDemo.java

#!/usr/bin/env bash
# BẢN 1 — KHÔNG CẦN NGROK
# Chạy hoàn toàn trên localhost. IPN không về được, nên đơn hàng được chốt
# bằng API querydr (server tự hỏi VNPAY trạng thái thật).
# Dùng khi cả lớp cùng chạy trên máy cá nhân.
set -euo pipefail
cd "$(dirname "$0")"
[ -f .env ] && { set -a; . ./.env; set +a; }
: "${VNPAY_TMN_CODE:?Chưa có VNPAY_TMN_CODE — điền vào file .env}"
: "${VNPAY_HASH_SECRET:?Chưa có VNPAY_HASH_SECRET — điền vào file .env}"

export VNPAY_MODE=local
unset VNPAY_PUBLIC_URL VNPAY_RETURN_URL 2>/dev/null || true
exec java VnpayDemo.java

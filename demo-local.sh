#!/usr/bin/env bash
# Chạy thử cả luồng mà không cần ngrok, không cần bấm thẻ.
# Script tự ký IPN bằng HashSecret của mình để giả làm VNPAY gọi về.
set -euo pipefail

BASE=${BASE:-http://localhost:8080}
SECRET=${VNPAY_HASH_SECRET:?can export VNPAY_HASH_SECRET}
AMOUNT=${AMOUNT:-50000}

echo "1. Tạo payment URL"
CREATE=$(curl -s -X POST "$BASE/api/payments" -H 'Content-Type: application/json' \
  -d "{\"amount\":$AMOUNT,\"orderInfo\":\"Thanh toan don hang DEMO\",\"bankCode\":\"NCB\"}")
echo "$CREATE"
TXNREF=$(echo "$CREATE" | python3 -c 'import sys,json;print(json.load(sys.stdin)["txnRef"])')

sign_ipn() {
  TXNREF="$1" AMOUNT="$2" SECRET="$SECRET" python3 - <<'PY'
import hashlib, hmac, os, urllib.parse
p = {
  "vnp_Amount": str(int(os.environ["AMOUNT"]) * 100),
  "vnp_BankCode": "NCB",
  "vnp_BankTranNo": "VNP14422574",
  "vnp_CardType": "ATM",
  "vnp_OrderInfo": "Thanh toan don hang DEMO",
  "vnp_PayDate": "20260916103015",
  "vnp_ResponseCode": "00",
  "vnp_TmnCode": os.environ.get("VNPAY_TMN_CODE", "DEMO0001"),
  "vnp_TransactionNo": "14422574",
  "vnp_TransactionStatus": "00",
  "vnp_TxnRef": os.environ["TXNREF"],
}
q = "&".join(f"{k}={urllib.parse.quote_plus(v)}" for k, v in sorted(p.items()))
h = hmac.new(os.environ["SECRET"].encode(), q.encode(), hashlib.sha512).hexdigest()
print(q + "&vnp_SecureHash=" + h)
PY
}

IPN_QUERY=$(sign_ipn "$TXNREF" "$AMOUNT")

echo "2. VNPAY gọi IPN lần đầu, chờ RspCode 00"
curl -s "$BASE/vnpay/ipn?$IPN_QUERY"; echo

echo "3. VNPAY retry lần hai, chờ 02 vì đơn đã chốt"
curl -s "$BASE/vnpay/ipn?$IPN_QUERY"; echo

echo "4. IPN bị sửa số tiền, chờ 97 vì chữ ký hỏng"
curl -s "$BASE/vnpay/ipn?${IPN_QUERY/vnp_Amount=$((AMOUNT*100))/vnp_Amount=100}"; echo

echo "5. Trạng thái đơn hàng"
curl -s "$BASE/api/orders/$TXNREF"; echo

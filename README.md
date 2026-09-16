# VNPAY Sandbox × Spring Boot — Demo tái sử dụng được

Tích hợp cổng thanh toán VNPAY (sandbox) vào microservice Spring Boot: **tạo yêu cầu thanh toán → nhận callback → đối soát giao dịch**.

Toàn bộ logic ký/verify nằm trong 1 file không phụ thuộc Spring: `util/VnpayUtils.java` — copy sang project khác là chạy.

---

## 1. Chạy trong 3 phút

```bash
# Đăng ký sandbox: https://sandbox.vnpayment.vn/devreg/ -> email trả về TmnCode + HashSecret
export VNPAY_TMN_CODE=xxxxxxxx
export VNPAY_HASH_SECRET=xxxxxxxxxxxxxxxx
mvn spring-boot:run
# mở http://localhost:8080
```

Thẻ test (NCB): `9704198526191432198` · `NGUYEN VAN A` · `07/15` · OTP `123456`

Test toàn bộ luồng **không cần ngrok, không cần bấm thẻ** (script tự ký IPN bằng HashSecret của bạn):

```bash
./demo-local.sh
```

---

## 2. Luồng chạy

```
Browser          Payment Service            VNPAY
   │  POST /api/payments  │                    │
   │─────────────────────>│ tạo order PENDING  │
   │   {paymentUrl}       │ ký HMAC-SHA512     │
   │<─────────────────────│                    │
   │  redirect ─────────────────────────────-->│  user nhập thẻ + OTP
   │                      │                    │
   │                      │<── GET /vnpay/ipn ─│  (1) server-to-server: NGUỒN SỰ THẬT
   │                      │ verify → PAID      │      trả {"RspCode":"00"}
   │<── GET /vnpay/return ─────────────────────│  (2) browser quay về: CHỈ ĐỂ HIỂN THỊ
   │  GET /api/orders/{ref}│                   │
   │                      │─ POST querydr ────>│  (3) đối soát chủ động khi IPN mất
```

**Quy tắc sống còn:** cộng tiền/giao hàng **chỉ** ở IPN (1). ReturnURL (2) user sửa URL được → chỉ dùng để vẽ màn hình.

---

## 3. API của service

| Method | Path | Mục đích |
|---|---|---|
| POST | `/api/payments` | `{amount, orderInfo, bankCode}` → `{txnRef, paymentUrl}` |
| GET | `/api/orders/{txnRef}` | FE polling trạng thái thật |
| POST | `/api/orders/{txnRef}/verify?transactionDate=yyyyMMddHHmmss` | gọi `querydr` sang VNPAY |
| GET | `/vnpay/return` | browser quay về (hiển thị) |
| GET | `/vnpay/ipn` | VNPAY gọi server-to-server (ghi DB) |

---

## 4. Ký chữ ký — 3 dòng cốt lõi

```java
String query = params.entrySet().stream().sorted(...)      // 1. sort alphabet
        .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), US_ASCII))  // 2. encode GIÁ TRỊ
        .collect(joining("&"));
String secureHash = hmacSHA512(hashSecret, query);          // 3. HMAC-SHA512 → hex thường
```

Verify callback = đúng công thức đó, sau khi **bỏ `vnp_SecureHash` và `vnp_SecureHashType`**.

---

## 5. Bảng mã cần nhớ

**Trả về cho IPN** (bắt buộc JSON + HTTP 200, VNPAY retry tới khi nhận `00`):

| RspCode | Khi nào |
|---|---|
| `00` | Đã ghi nhận xong |
| `01` | Không tìm thấy đơn |
| `02` | Đơn đã xử lý rồi (idempotent) |
| `04` | Số tiền không khớp |
| `97` | Sai checksum |
| `99` | Lỗi không xác định |

**Đọc từ VNPAY:** thành công ⟺ `vnp_ResponseCode == "00"` **VÀ** `vnp_TransactionStatus == "00"`.
Hay gặp: `24` user hủy · `51` không đủ số dư · `11` hết hạn thanh toán · `75` ngân hàng bảo trì.

---

## 6. 8 cái bẫy hay dính

1. **`vnp_Amount` phải × 100** và là số nguyên (50.000đ → `5000000`).
2. **Ký phải URL-encode giá trị**; verify cũng phải encode lại vì servlet đã decode sẵn.
3. **Thời gian theo GMT+7**, format `yyyyMMddHHmmss`. Demo chính chủ của VNPAY dùng `Etc/GMT+7` — POSIX hiểu là **UTC−7**, sai 14 tiếng. Dùng `Asia/Ho_Chi_Minh`.
4. **`vnp_TxnRef` duy nhất trong 24h** theo TmnCode. Retry thanh toán phải sinh mã mới.
5. **IPN phải public internet** → dev dùng `ngrok http 8080`, khai URL trong merchant portal.
6. **IPN phải idempotent** — VNPAY retry nhiều lần; trả `02` nếu đã xử lý.
7. **Luôn so lại số tiền** với DB trước khi đánh dấu PAID (chống sửa `vnp_Amount`).
8. **`vnp_ReturnUrl` phải khớp domain** đã đăng ký, không thì VNPAY từ chối.

---

## 7. Đưa vào microservice thật

- Tách `payment-service` riêng; các service khác chỉ nghe event `OrderPaid` (outbox pattern trong `IpnService`).
- `HashSecret` vào Vault/K8s Secret, **không** vào `application.yml` commit git.
- Bảng `payment_transaction` với `UNIQUE(txn_ref)` + optimistic lock — thay cho `synchronized` trong demo.
- Job `@Scheduled` quét đơn PENDING quá 15 phút → gọi `querydr` để tự chốt trạng thái.
- Không log `vnp_SecureHash`, không log HashSecret.

## 8. Dùng cho đồ án nào

Bất kỳ đồ án có "đặt hàng" hoặc "nạp tiền": e-commerce, đặt vé/phòng/sân, học phí, ví điện tử, SaaS subscription (dùng token hóa thẻ), quyên góp. Chỉ cần thay `OrderStore` bằng repository của bạn.

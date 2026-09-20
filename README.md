# VNPAY Sandbox × Spring Boot — Demo tái sử dụng được

Tích hợp cổng thanh toán VNPAY (sandbox) vào microservice Spring Boot: **tạo yêu cầu thanh toán → nhận callback → đối soát giao dịch**.

Toàn bộ logic ký/verify nằm trong 1 file không phụ thuộc Spring: `util/VnpayUtils.java` — copy sang project khác là chạy.

---

## 0a. Code xuất phát cho buổi seminar

| File | Dùng khi nào |
|---|---|
| [`start/index.html`](start/index.html) | HTML thuần, double-click là mở. Không server, không VNPAY. 4 chỗ `TODO`. |
| [`ShopStart.java`](ShopStart.java) | Có server, cửa hàng chạy được, phần VNPAY để trống. 6 chỗ `TODO`. `java ShopStart.java` |

Đáp án của cả hai là [`VnpayDemo.java`](VnpayDemo.java).

---

## 0b. Bản một file — dành cho cả lớp

Muốn thử ngay mà không cài gì: [`VnpayDemo.java`](VnpayDemo.java) là **toàn bộ demo trong một file**,
HTML nhúng bên trong, không Maven, không Spring, không thư viện ngoài. Chỉ cần JDK 17+.

Điền `VNPAY_TMN_CODE` và `VNPAY_HASH_SECRET` vào file `.env`, rồi chọn một trong hai bản:

```bash
./run-local.sh     # BẢN 1 — không cần ngrok
```

```bash
./run-ngrok.sh     # BẢN 2 — có ngrok, nhận IPN thật
```

| | Bản không ngrok | Bản có ngrok |
|---|---|---|
| Cài đặt | Không cần gì thêm | Cần ngrok + authtoken |
| Khai URL trong portal | Không | Có, đổi mỗi lần chạy lại |
| IPN | Không về được | VNPAY gọi thật vào `/vnpay/ipn` |
| Chốt đơn bằng | API `querydr` | IPN, `querydr` làm dự phòng |
| Hợp cho | Khi mạng hỏng, dùng dự phòng | Buổi seminar dùng bản này |

Buổi seminar chạy bản ngrok theo yêu cầu môn học. Hướng dẫn đăng ký và cài ngrok cho cả lớp
nằm ở [`CHUAN-BI.md`](CHUAN-BI.md). Bản không ngrok giữ lại để dự phòng khi mạng trục trặc.

`run-ngrok.sh` tự mở tunnel và **tự đọc URL ngrok cấp** (qua API cục bộ cổng 4040) — không phải
copy URL bằng tay. Chương trình in sẵn 2 URL cần dán vào merchant portal.

File được chia thành 10 bước đánh số, đọc từ trên xuống: cấu hình → giao diện → đơn hàng → ký →
verify → tạo URL → IPN → querydr → ReturnURL → khởi động server.

Phần còn lại của README nói về bản Spring Boot đầy đủ bên dưới.

---

## 1. Chạy trong 3 phút

```bash
# Đăng ký sandbox: https://sandbox.vnpayment.vn/devreg/ -> email trả về TmnCode + HashSecret
export VNPAY_TMN_CODE=xxxxxxxx
export VNPAY_HASH_SECRET=xxxxxxxxxxxxxxxx
mvn spring-boot:run
# mở http://localhost:8080  (cửa hàng demo)
```

Thẻ test (NCB): `9704198526191432198` · `NGUYEN VAN A` · `07/15` · OTP `123456`

Test toàn bộ luồng **không cần ngrok, không cần bấm thẻ** (script tự ký IPN bằng HashSecret của bạn):

```bash
./demo-local.sh
```

---

## 2. Trang web demo (thuần HTML)

| File | Vai trò |
|---|---|
| `static/index.html` | Cửa hàng 4 sản phẩm + modal checkout |
| `static/result.html` | Trang kết quả, poll `/api/orders/{txnRef}` chờ IPN |
| `static/style.css` | CSS thuần, không framework |

**Vì sao HTML không gọi thẳng VNPAY được?** Muốn tạo URL thanh toán thì phải ký HMAC-SHA512 bằng `HashSecret`.
Đặt secret trong JavaScript = ai mở View Source cũng ký được đơn giả và tự "xác nhận đã thanh toán".
Nên trang HTML chỉ gọi `POST /api/payments`, còn việc ký nằm ở server. Đây là ràng buộc bắt buộc của mọi cổng thanh toán.

---

## 3. Host public bằng ngrok

IPN là cuộc gọi **server-to-server** — VNPAY phải với tới được máy bạn, nên `localhost` không đủ.

```bash
brew install --cask ngrok
ngrok config add-authtoken <token>      # free, lấy tại dashboard.ngrok.com
export VNPAY_TMN_CODE=... VNPAY_HASH_SECRET=...
./start-ngrok.sh                         # mở tunnel + chạy app với đúng ReturnURL
```

Script in ra 3 URL:

```
Web:        https://xxxx.ngrok-free.app
ReturnURL:  https://xxxx.ngrok-free.app/vnpay/return
IPN URL:    https://xxxx.ngrok-free.app/vnpay/ipn
```

Dán **ReturnURL** và **IPN URL** vào `sandbox.vnpayment.vn/merchantv2/` → *Cấu hình → Thông tin website*.

Hai lưu ý của ngrok free:
- URL **đổi mỗi lần chạy lại** → phải khai lại trong merchant portal.
- Trang cảnh báo *"You are about to visit…"* hiện 1 lần cho trình duyệt. IPN không dính vì không phải browser.

---

## 4. Luồng chạy

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

**Quy tắc sống còn:** cộng tiền/giao hàng **chỉ** từ nguồn server-to-server — IPN (1) hoặc `querydr` (3).
ReturnURL (2) user sửa URL được → chỉ dùng để vẽ màn hình.

> Thực tế gặp phải: portal sandbox không cho khai IPN URL (*Danh sách website* trống, *Cài đặt thông báo* lỗi kết nối),
> nên IPN không về. `ReconcileService` gọi `querydr` ngay trên ReturnURL để chốt đơn — vẫn verify chữ ký và so số tiền, không tin tham số URL.

---

## 5. API của service

| Method | Path | Mục đích |
|---|---|---|
| POST | `/api/payments` | `{amount, orderInfo, bankCode}` → `{txnRef, paymentUrl}` |
| GET | `/api/orders/{txnRef}` | FE polling trạng thái thật |
| POST | `/api/orders/{txnRef}/verify?transactionDate=yyyyMMddHHmmss` | gọi `querydr` sang VNPAY |
| | | *ReturnURL cũng tự gọi `querydr` khi đơn còn PENDING* |
| GET | `/vnpay/return` | browser quay về → verify chữ ký → 302 sang `/result.html` |
| GET | `/vnpay/ipn` | VNPAY gọi server-to-server (ghi DB) |

---

## 6. Ký chữ ký — 3 dòng cốt lõi

```java
String query = params.entrySet().stream().sorted(...)      // 1. sort alphabet
        .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), US_ASCII))  // 2. encode GIÁ TRỊ
        .collect(joining("&"));
String secureHash = hmacSHA512(hashSecret, query);          // 3. HMAC-SHA512 → hex thường
```

Verify callback = đúng công thức đó, sau khi **bỏ `vnp_SecureHash` và `vnp_SecureHashType`**.

---

## 7. Bảng mã cần nhớ

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

## 8. 8 cái bẫy hay dính

1. **`vnp_Amount` phải × 100** và là số nguyên (50.000đ → `5000000`).
2. **Ký phải URL-encode giá trị**; verify cũng phải encode lại vì servlet đã decode sẵn.
3. **Thời gian theo GMT+7**, format `yyyyMMddHHmmss`. Demo chính chủ của VNPAY dùng `Etc/GMT+7` — POSIX hiểu là **UTC−7**, sai 14 tiếng. Dùng `Asia/Ho_Chi_Minh`.
4. **`vnp_TxnRef` duy nhất trong 24h** theo TmnCode. Retry thanh toán phải sinh mã mới.
5. **IPN phải public internet** → dev dùng `ngrok http 8080`, khai URL trong merchant portal.
6. **IPN phải idempotent** — VNPAY retry nhiều lần; trả `02` nếu đã xử lý.
7. **Luôn so lại số tiền** với DB trước khi đánh dấu PAID (chống sửa `vnp_Amount`).
8. **`vnp_ReturnUrl` phải khớp domain** đã đăng ký, không thì VNPAY từ chối.

---

## 9. Đưa vào microservice thật

- Tách `payment-service` riêng; các service khác chỉ nghe event `OrderPaid` (outbox pattern trong `IpnService`).
- `HashSecret` vào Vault/K8s Secret, **không** vào `application.yml` commit git.
- Bảng `payment_transaction` với `UNIQUE(txn_ref)` + optimistic lock — thay cho `synchronized` trong demo.
- Job `@Scheduled` quét đơn PENDING quá 15 phút → gọi `querydr` để tự chốt trạng thái.
- Không log `vnp_SecureHash`, không log HashSecret.

## 10. Dùng cho đồ án nào

Bất kỳ đồ án có "đặt hàng" hoặc "nạp tiền": e-commerce, đặt vé/phòng/sân, học phí, ví điện tử, SaaS subscription (dùng token hóa thẻ), quyên góp. Chỉ cần thay `OrderStore` bằng repository của bạn.

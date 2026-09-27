# Tích hợp VNPAY Sandbox

Tạo yêu cầu thanh toán, nhận callback, đối soát giao dịch. Có hai bản: một file Java cho buổi seminar và một project Spring Boot đầy đủ.

## Các file

| File | Dùng để |
|---|---|
| `lab/ShopStart.java` + `lab/index.html` | Code xuất phát, 6 TODO |
| `lab/ShopDone.java` | Đáp án |
| `VnpayDemo.java` | Toàn bộ demo trong một file, HTML nhúng sẵn |
| `src/` | Bản Spring Boot |
| `CHUAN-BI.md` | Cài đặt trước buổi seminar |
| `SEMINAR.md` | Thứ tự trong buổi |

## Bước 1. Lấy TmnCode và HashSecret

1. Đăng ký ở https://sandbox.vnpayment.vn/devreg/
2. Copy `.env.example` thành `.env`
3. Điền `VNPAY_TMN_CODE` và `VNPAY_HASH_SECRET` từ email

## Bước 2. Chạy bản một file

Cần JDK 17+.

- Có ngrok, nhận IPN thật:

  ```bash
  ./run-ngrok.sh
  ```

- Không có ngrok, chốt đơn bằng `querydr`:

  ```bash
  ./run-local.sh
  ```

`run-ngrok.sh` tự đọc URL ngrok ở cổng 4040 và in ra 2 URL cần khai trong merchant portal.

## Bước 3. Chạy bản Spring Boot

1. Chạy:

   ```bash
   export VNPAY_TMN_CODE=xxxxxxxx
   export VNPAY_HASH_SECRET=xxxxxxxxxxxxxxxx
   mvn spring-boot:run
   ```

2. Mở http://localhost:8080
3. Cần IPN thật thì chạy `./start-ngrok.sh` thay cho `mvn spring-boot:run`
4. Test không cần thẻ, không cần ngrok:

   ```bash
   ./demo-local.sh
   ```

## Bước 4. Khai URL trong merchant portal

1. Đăng nhập https://sandbox.vnpayment.vn/merchantv2/
2. Cấu hình → Thông tin website
3. Điền:
   - URL trả về: `https://<id>.ngrok-free.app/vnpay/return`
   - URL IPN: `https://<id>.ngrok-free.app/vnpay/ipn`

URL ngrok đổi mỗi lần chạy lại, phải khai lại.

## Bước 5. Thanh toán bằng thẻ test

| Ô | Giá trị |
|---|---|
| Ngân hàng | NCB |
| Số thẻ | `9704198526191432198` |
| Tên chủ thẻ | `NGUYEN VAN A` |
| Ngày phát hành | `07/15` |
| OTP | `123456` |

## Luồng chạy

```
Browser          Payment Service            VNPAY
   │  POST /api/payments  │                    │
   │─────────────────────>│ tạo order PENDING  │
   │   {paymentUrl}       │ ký HMAC-SHA512     │
   │<─────────────────────│                    │
   │  redirect ───────────────────────────────>│  nhập thẻ + OTP
   │                      │<── GET /vnpay/ipn ─│  (1) ghi status
   │<── GET /vnpay/return ─────────────────────│  (2) chỉ hiển thị
   │                      │─ POST querydr ────>│  (3) đối soát khi IPN không về
```

Chỉ ghi status từ (1) và (3). ReturnURL (2) nằm trên browser, khách sửa được.

## API

| Method | Path | Việc |
|---|---|---|
| POST | `/api/payments` | Nhận `{amount, orderInfo, bankCode}`, trả `{txnRef, paymentUrl}` |
| GET | `/api/orders/{txnRef}` | Trạng thái đơn |
| POST | `/api/orders/{txnRef}/verify?transactionDate=yyyyMMddHHmmss` | Gọi `querydr` |
| GET | `/vnpay/return` | Verify chữ ký, 302 sang `/result.html` |
| GET | `/vnpay/ipn` | VNPAY gọi server-to-server |

## Ký chữ ký

1. Sort params theo alphabet, bỏ value rỗng
2. URL-encode value (US_ASCII), nối bằng `&`
3. HMAC-SHA512 với `HashSecret`, ra hex lowercase

Verify: bỏ `vnp_SecureHash` và `vnp_SecureHashType`, ký lại, so sánh.

`querydr` ký khác: nối các trường bằng `|` theo đúng thứ tự tài liệu, không sort.

## RspCode trả cho IPN

| RspCode | Khi nào |
|---|---|
| `00` | Ghi nhận xong |
| `01` | Không tìm thấy đơn |
| `02` | Đơn đã xử lý |
| `04` | Sai số tiền |
| `97` | Sai checksum |
| `99` | Lỗi khác |

Thanh toán thành công khi `vnp_ResponseCode == "00"` và `vnp_TransactionStatus == "00"`.

Mã hay gặp: `24` khách hủy, `51` không đủ số dư, `11` hết hạn, `75` ngân hàng bảo trì.

## Lỗi hay gặp

| Lỗi | Sửa |
|---|---|
| Sai số tiền | `vnp_Amount` nhân 100, số nguyên. 50.000đ → `5000000` |
| Sai chữ ký khi verify | Encode lại value, servlet đã decode sẵn |
| Lệch giờ | Dùng `Asia/Ho_Chi_Minh`, không dùng `Etc/GMT+7` (là UTC−7) |
| Trùng mã đơn | `vnp_TxnRef` duy nhất trong 24h, thanh toán lại sinh mã mới |
| IPN không về | Cần URL công khai (ngrok) hoặc gọi `querydr` |
| IPN bị gọi nhiều lần | Đơn đã xử lý thì trả `02` |
| Bị sửa số tiền | So `vnp_Amount` với DB trước khi PAID |
| VNPAY từ chối ReturnUrl | `vnp_ReturnUrl` phải khớp domain đã đăng ký |

## Đưa vào đồ án

1. Copy `util/VnpayUtils.java` sang project
2. Thay `OrderStore` bằng repository của bạn
3. Khai `vnpay.*` trong `application.yml`, secret đọc từ biến môi trường
4. Thêm `UNIQUE(txn_ref)` cho bảng giao dịch
5. Job `@Scheduled` quét đơn PENDING quá 15 phút, gọi `querydr`

# Seminar: VNPAY Sandbox Integration
**Thời lượng 30 phút · 70% live-coding · Repo: `vnpay-seminar`**

Mục tiêu duy nhất: nghe xong về mở IDE tự tích hợp được VNPAY vào đồ án của mình.

---

## Phần 1 — Technology Overview (3')

> **Bài toán:** đồ án cần thu tiền, nhưng lưu số thẻ = phạm luật (PCI-DSS) + ngân hàng không cấp API trực tiếp cho sinh viên.

VNPAY = cổng trung gian: **user nhập thẻ trên trang của VNPAY, không phải trang của bạn.** Server bạn chỉ trao đổi *chữ ký*, không bao giờ thấy số thẻ.

Nói 1 câu cho cả buổi: **VNPAY chỉ là redirect + HMAC-SHA512.** Không có SDK, không có OAuth. Nắm chữ ký là xong.

Sandbox: `https://sandbox.vnpayment.vn/devreg/` → email trả `TmnCode` + `HashSecret`, dùng ngay, miễn phí.

---

## Phần 2 — Architecture & Workflow (4')

Vẽ lên bảng 3 mũi tên:

```
(1) Tạo URL   : service ký params → redirect browser sang VNPAY
(2) IPN       : VNPAY → server bạn (server-to-server)  ← GHI DB Ở ĐÂY
(3) ReturnURL : VNPAY → browser user                   ← CHỈ HIỂN THỊ
(+) querydr   : server bạn → VNPAY, hỏi lại trạng thái thật
```

**Câu hỏi chốt hạ để hỏi cả lớp:** *"Tại sao không cộng tiền ở ReturnURL cho nhanh?"*
→ Vì đó là URL trên trình duyệt của user. User sửa `vnp_ResponseCode=00` là mua hàng free. Và nếu user tắt tab ngay sau khi trả tiền, ReturnURL không bao giờ về → mất đơn. IPN là kênh server-to-server, có retry.

---

## Phần 3 — Hands-on Demo (12')

### 3.1 Ký chữ ký — `VnpayUtils.java` (4')
Mở file, chỉ đúng 3 bước: **sort alphabet → URL-encode giá trị → HMAC-SHA512 → hex thường**.

Nhấn 2 chi tiết làm 90% người mới sai:
- `vnp_Amount` phải **× 100** (50.000đ → `5000000`)
- Lúc verify callback phải **encode lại** giá trị, vì servlet đã decode sẵn.

### 3.2 Tạo payment URL — `PaymentService.createPayment()` (3')
Mở `http://localhost:8080` (cửa hàng demo, HTML thuần) → bấm **Mua ngay** → server ký → redirect sang VNPAY.
Chỉ rõ cho lớp: trang HTML **không** giữ `HashSecret`, nó chỉ gọi `POST /api/payments`.

Hoặc chạy bằng curl:
```bash
curl -X POST localhost:8080/api/payments -H 'Content-Type: application/json' \
  -d '{"amount":50000,"orderInfo":"Thanh toan don hang DEMO","bankCode":"NCB"}'
```
Dán URL vào browser → nhập thẻ test `9704198526191432198` / `NGUYEN VAN A` / `07/15` / OTP `123456`.

### 3.3 Nhận IPN — `IpnService.handle()` (4')
Đọc to 5 bước: `97` sai chữ ký → `01` không có đơn → `04` sai tiền → `02` đã xử lý → `00` xong.
Nhấn: **VNPAY retry cho tới khi nhận `00`** ⇒ handler bắt buộc idempotent.

Demo không cần ngrok — script tự ký IPN bằng chính HashSecret:
```bash
./demo-local.sh
```
Kết quả chạy thật:
```
2. IPN lần 1  → {"RspCode":"00","Message":"Confirm Success"}
3. IPN lần 2  → {"RspCode":"02","Message":"Order already confirmed"}   ← idempotent
4. Sửa tiền   → {"RspCode":"97","Message":"Invalid Checksum"}          ← chữ ký chặn
5. Đơn hàng   → status: PAID, transactionNo: 14422574
```

### 3.4 Đối soát — `VnpayQueryService` + `ReconcileService` (2')

**Kể câu chuyện thật này, nó đắt giá hơn mọi slide lý thuyết:**

> Khi tích hợp, portal sandbox không cho khai IPN URL — mục *Danh sách website* trống, *Cài đặt thông báo* báo `Kết nối hệ thống tạm thời bị gián đoạn`. Tức là IPN **không bao giờ về**. Nếu chỉ code theo tài liệu thì demo chết tại chỗ.

Cách cứu: gọi API `querydr` — server tự hỏi VNPAY trạng thái thật. Vẫn an toàn y như IPN vì có đủ 3 lớp: hỏi thẳng VNPAY (không qua trình duyệt) → verify chữ ký response → so lại số tiền với DB.

Log thật của giao dịch demo:
```
Chot bang querydr: txnRef=20260920125606777443 status=PAID transactionNo=15683165
```

Cảnh báo khi code: hash của `querydr` **không sort alphabet**, mà là chuỗi nối bằng `|` đúng thứ tự tài liệu. Copy nhầm hàm ký của luồng `pay` là fail ngay.

---

## Phần 4 — Integration vào Spring Boot microservice (4')

- `VnpayProperties` (`@ConfigurationProperties`) — secret lấy từ env/Vault, không commit.
- `payment-service` đứng riêng; service khác chỉ nghe event `OrderPaid` publish từ `IpnService`.
- Bảng `payment_transaction` có `UNIQUE(txn_ref)` + optimistic lock (demo dùng `synchronized` cho gọn).
- Dev cần IPN: `./start-ngrok.sh` → in ra Web/ReturnURL/IPN URL, khai 2 URL sau vào merchant portal.
- `@Scheduled` mỗi 5 phút quét đơn PENDING quá 15 phút → `querydr` chốt trạng thái.

**Đồ án nào dùng được:** e-commerce, đặt vé/phòng/sân, đóng học phí, ví điện tử, quyên góp, SaaS — bất kỳ chỗ nào có "đặt hàng" hoặc "nạp tiền". Thay mỗi `OrderStore` bằng repository của bạn.

---

## Phần 5 — Best Practices & Limitations (4')

**Phải làm**
1. Chỉ tin IPN; ReturnURL chỉ để vẽ màn hình.
2. So lại số tiền với DB trước khi PAID.
3. Idempotent bằng trạng thái đơn.
4. `vnp_TxnRef` duy nhất trong 24h — retry phải sinh mã mới.
5. Thời gian `Asia/Ho_Chi_Minh`. *Demo chính chủ VNPAY dùng `Etc/GMT+7` = UTC−7, lệch 14 tiếng — đừng copy chỗ đó.*
6. So sánh chữ ký kiểu constant-time; không log secret/hash.

**Giới hạn**
- Sandbox có lúc không khai được IPN URL → phải có đường lùi `querydr`, đừng phụ thuộc mỗi IPN.
- Không phải REST API thuần → bắt buộc redirect, khó cho mobile app (phải nhúng WebView).
- Sandbox không phản ánh 100% production (hạn mức, ngân hàng bảo trì).
- Hoàn tiền phải qua API `refund` + đối soát tay, không tự động.
- Mỗi cổng (VNPAY/MoMo/ZaloPay) một kiểu ký → nên bọc sau interface `PaymentGateway` nếu đồ án cần nhiều cổng.

---

## Phần 6 — Q&A (3')

Câu hay bị hỏi, chuẩn bị sẵn:
- *IPN không về thì sao?* → `querydr`. Chính bài này đã phải dùng nó vì portal sandbox hỏng.
- *localhost nhận IPN kiểu gì?* → ngrok, hoặc `demo-local.sh` tự ký.
- *Sai chữ ký mà không biết vì sao?* → in ra `hashData` rồi so từng ký tự; 90% do quên encode hoặc quên bỏ `vnp_SecureHash`.
- *Có test được không cần thẻ?* → có, unit test `VnpayUtilsTest` (6 test, chạy offline).

---

## Phần 7 — Source & Docs (bàn giao)

| Bạn cần | Mở file |
|---|---|
| **Bản một file để cả lớp chạy ngay** | `VnpayDemo.java` — `java VnpayDemo.java`, không cần Maven |
| Copy logic ký/verify | `util/VnpayUtils.java` |
| Tạo URL thanh toán | `service/PaymentService.java` |
| Xử lý IPN | `service/IpnService.java` |
| Đối soát | `service/VnpayQueryService.java` + `service/ReconcileService.java` |
| Hướng dẫn + bảng mã lỗi + 8 cái bẫy | `README.md` |
| Test không cần thẻ | `./demo-local.sh`, `mvn test` |

Ba dòng để tích hợp vào đồ án của bạn: copy `VnpayUtils.java` → thay `OrderStore` bằng repository của bạn → khai `vnpay.*` trong `application.yml`.

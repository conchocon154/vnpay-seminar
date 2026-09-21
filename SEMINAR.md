# Seminar: VNPAY Sandbox Integration
**Thời lượng 30 phút · 70% live-coding · Repo: `vnpay-seminar`**

Mục tiêu duy nhất: nghe xong về mở IDE tự tích hợp được VNPAY vào đồ án của mình.

---

## Phần 1. Công nghệ này giải quyết chuyện gì (3 phút)

> **Bài toán:** đồ án cần thu tiền, nhưng lưu số thẻ = phạm luật (PCI-DSS) + ngân hàng không cấp API trực tiếp cho sinh viên.

VNPAY = cổng trung gian: **user nhập thẻ trên trang của VNPAY, không phải trang của bạn.** Server bạn chỉ trao đổi *chữ ký*, không bao giờ thấy số thẻ.

Nói 1 câu cho cả buổi: **VNPAY chỉ là redirect + HMAC-SHA512.** Không có SDK, không có OAuth. Nắm chữ ký là xong.

Đăng ký sandbox ở `https://sandbox.vnpayment.vn/devreg/`, email trả về `TmnCode` và `HashSecret`, miễn phí và dùng được ngay.

---

## Phần 2. Kiến trúc và luồng chạy (4 phút)

Vẽ lên bảng 3 mũi tên:

```
(1) Tạo URL   : service ký params → redirect browser sang VNPAY
(2) IPN       : VNPAY → server bạn (server-to-server)  ← GHI DB Ở ĐÂY
(3) ReturnURL : VNPAY → browser user                   ← CHỈ HIỂN THỊ
(+) querydr   : server bạn → VNPAY, hỏi lại trạng thái thật
```

**Câu hỏi chốt hạ để hỏi cả lớp:** *"Tại sao không cộng tiền ở ReturnURL cho nhanh?"*
Vì đó là URL trên trình duyệt của khách. Sửa `vnp_ResponseCode=00` là mua hàng miễn phí. Và khách tắt tab ngay sau khi trả tiền thì ReturnURL không bao giờ về, mình mất đơn đã thu tiền. IPN là kênh server gọi server, lại có retry.

---

## Phần 3. Gõ code thật (12 phút)

### 3.1 Ký chữ ký, file `VnpayUtils.java` (4 phút)
Mở file ra, chỉ có ba bước: sort alphabet, URL-encode giá trị, rồi băm HMAC-SHA512 ra hex chữ thường.

Nhấn 2 chi tiết làm 90% người mới sai:
- `vnp_Amount` phải nhân 100, 50.000đ thành `5000000`
- Lúc verify callback phải encode lại giá trị, vì servlet đã decode sẵn.

### 3.2 Tạo payment URL, hàm `PaymentService.createPayment()` (3 phút)
Mở `http://localhost:8080`, bấm Mua ngay, server ký xong thì chuyển trang sang VNPAY.
Chỉ rõ cho lớp: trang HTML **không** giữ `HashSecret`, nó chỉ gọi `POST /api/payments`.

Hoặc chạy bằng curl:
```bash
curl -X POST localhost:8080/api/payments -H 'Content-Type: application/json' \
  -d '{"amount":50000,"orderInfo":"Thanh toan don hang DEMO","bankCode":"NCB"}'
```
Dán URL vào trình duyệt rồi nhập thẻ test `9704198526191432198`, `NGUYEN VAN A`, `07/15`, OTP `123456`.

### 3.3 Nhận IPN, hàm `IpnService.handle()` (4 phút)
Đọc to năm nhánh: `97` sai chữ ký, `01` không có đơn, `04` sai số tiền, `02` đã xử lý rồi, `00` ghi nhận xong.
Nhấn mạnh: VNPAY retry tới khi nhận được `00`, nên handler bắt buộc idempotent.

Demo này không cần ngrok, script tự ký IPN bằng chính HashSecret:
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

### 3.4 Đối soát bằng `VnpayQueryService` và `ReconcileService` (2 phút)

**Kể câu chuyện thật này, nó đắt giá hơn mọi slide lý thuyết:**

> Lúc tích hợp, portal sandbox không cho khai IPN URL. Mục *Danh sách website* trống trơn, *Cài đặt thông báo* thì báo `Kết nối hệ thống tạm thời bị gián đoạn`. Nghĩa là IPN không bao giờ về. Chỉ code theo tài liệu thì demo chết tại chỗ.

Cách cứu là gọi API `querydr` để server tự hỏi VNPAY trạng thái thật. Vẫn an toàn như IPN vì đủ ba lớp: hỏi thẳng VNPAY chứ không qua trình duyệt, verify chữ ký của response, rồi so lại số tiền với DB.

Log thật của giao dịch demo:
```
Chot bang querydr: txnRef=20260920125606777443 status=PAID transactionNo=15683165
```

Cảnh báo khi code: hash của `querydr` **không sort alphabet**, mà là chuỗi nối bằng `|` đúng thứ tự tài liệu. Copy nhầm hàm ký của luồng `pay` là fail ngay.

---

## Phần 4. Đưa vào microservice Spring Boot (4 phút)

- `VnpayProperties` dùng `@ConfigurationProperties`, secret lấy từ biến môi trường hoặc Vault, không commit.
- `payment-service` đứng riêng; service khác chỉ nghe event `OrderPaid` publish từ `IpnService`.
- Bảng `payment_transaction` có `UNIQUE(txn_ref)` + optimistic lock (demo dùng `synchronized` cho gọn).
- Cần IPN lúc dev thì chạy `./start-ngrok.sh`, nó in ra Web, ReturnURL và IPN URL. Khai hai URL sau vào merchant portal.
- Job `@Scheduled` mỗi 5 phút quét đơn PENDING quá 15 phút rồi gọi `querydr` chốt trạng thái.

Đồ án nào dùng được: bán hàng online, đặt vé, đặt phòng, đặt sân, đóng học phí, ví điện tử, quyên góp, SaaS. Nói chung là bất kỳ chỗ nào có đặt hàng hoặc nạp tiền. Việc phải làm chỉ là thay `OrderStore` bằng repository của bạn.

---

## Phần 5. Nên làm gì và vướng ở đâu (4 phút)

**Phải làm**
1. Chỉ tin IPN; ReturnURL chỉ để vẽ màn hình.
2. So lại số tiền với DB trước khi PAID.
3. Idempotent bằng trạng thái đơn.
4. `vnp_TxnRef` duy nhất trong 24h, thanh toán lại phải sinh mã mới.
5. Thời gian `Asia/Ho_Chi_Minh`. Demo của chính VNPAY dùng `Etc/GMT+7`, tức UTC trừ 7, lệch 14 tiếng. Đừng copy chỗ đó.
6. So sánh chữ ký kiểu constant-time; không log secret/hash.

**Giới hạn**
- Sandbox có lúc không khai được IPN URL, nên phải có đường lùi `querydr`. Đừng phụ thuộc mỗi IPN.
- Không phải REST API thuần nên bắt buộc redirect. App mobile phải nhúng WebView.
- Sandbox không phản ánh 100% production (hạn mức, ngân hàng bảo trì).
- Hoàn tiền phải qua API `refund` + đối soát tay, không tự động.
- VNPAY, MoMo, ZaloPay mỗi bên ký một kiểu. Đồ án cần nhiều cổng thì bọc sau interface `PaymentGateway`.

---

## Phần 6. Hỏi đáp (3 phút)

Câu hay bị hỏi, chuẩn bị sẵn:
- *IPN không về thì sao?* Gọi `querydr`. Chính bài này đã phải dùng nó vì portal sandbox hỏng.
- *localhost nhận IPN kiểu gì?* Dùng ngrok, hoặc `demo-local.sh` tự ký.
- *Sai chữ ký mà không biết vì sao?* In `hashData` ra rồi so từng ký tự. 90% là quên encode hoặc quên bỏ `vnp_SecureHash`.
- *Có test được không cần thẻ?* Được, `VnpayUtilsTest` có 6 test chạy offline.

---

## Phần 7. Bàn giao

| Bạn cần | Mở file |
|---|---|
| Bản gói trong một file cho cả lớp | `VnpayDemo.java`, chạy `java VnpayDemo.java`, không cần Maven |
| Copy logic ký/verify | `util/VnpayUtils.java` |
| Tạo URL thanh toán | `service/PaymentService.java` |
| Xử lý IPN | `service/IpnService.java` |
| Đối soát | `service/VnpayQueryService.java` + `service/ReconcileService.java` |
| Hướng dẫn + bảng mã lỗi + 8 cái bẫy | `README.md` |
| Test không cần thẻ | `./demo-local.sh`, `mvn test` |

Ba việc để đưa vào đồ án của bạn: copy `VnpayUtils.java` sang project, thay `OrderStore` bằng repository sẵn có, rồi khai `vnpay.*` trong `application.yml`.

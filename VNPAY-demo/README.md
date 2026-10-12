# VNPAY Sandbox Integration — demo seminar SOA

Một chương trình Java mở hai HTTP service: Order Service (8090) và Payment Service (8091). Hai service trao đổi qua HTTP JSON, cùng JVM để chạy gọn. Dữ liệu lưu RAM. Đơn mẫu có giá 25.000 VND do Order Service xác định.

Bài thực hành tại lớp, có kết quả mong đợi từng bước: xem `THUC-HANH.md`.

## Chạy mô phỏng local (không cần key, không gọi VNPAY)

Cần JDK 17 trở lên. Mở terminal ngay trong thư mục này.

macOS/Linux:

```bash
javac --release 17 -cp lib/json.jar SeminarDemo.java
java -cp '.:lib/json.jar' SeminarDemo
```

Windows PowerShell:

```powershell
javac --release 17 -cp lib/json.jar SeminarDemo.java
java -cp '.;lib/json.jar' SeminarDemo
```

Mở http://localhost:8090. Chương trình mặc định hiện `LOCAL MOCK`. Biến `VNPAY_MODE=sandbox` mới bật kết nối sandbox thật. Nếu đã đặt biến này từ lần trước thì xóa trước khi tập local.

### Kịch bản 1: Callback bình thường và IPN trùng

1. Bấm Thanh toán, chọn **Thanh cong + IPN**.
2. Mock gửi IPN có chữ ký đến endpoint hai lần, terminal hiện mã `00` và `02`.
3. ReturnURL chuyển về trang kết quả. Đơn hiện `PAID`.

### Kịch bản 2: Chưa nhận IPN

1. Tạo đơn mới, chọn **Thanh cong, chua gui IPN**.
2. Mock lưu kết quả thành công nhưng không gửi IPN. ReturnURL đưa khách về trang kết quả, đơn vẫn `PENDING`.
3. Bấm **Truy van giao dich**. Payment Service xác minh response mock theo cấu trúc querydr rồi cập nhật `PAID`.
4. Terminal hiện `querydr verified=true local=PAID`.

**Mock là mô phỏng local**. Không phải giao dịch VNPAY, không chứng minh sandbox thật đã chấp nhận request. Mock sử dụng cùng hàm kiểm tra chữ ký và đối chiếu dữ liệu nhưng không gọi API querydr của VNPAY.

## Chạy VNPAY Sandbox thật

1. Đăng ký sandbox tại https://sandbox.vnpayment.vn/devreg/ và nhận TmnCode/HashSecret từ VNPAY.
2. Dừng demo local bằng Ctrl+C. Đặt các biến bên dưới bằng thông tin của bạn, không ghi key vào file code.
3. Chạy `ngrok http 8091` (ngrok là công cụ riêng, không kèm trong zip). Dùng HTTPS URL được cấp.
4. Trong cấu hình merchant sandbox, đặt URL IPN là `https://<domain>/vnpay/ipn`.
5. Đặt ReturnURL cùng domain và khởi động chương trình.

macOS/Linux:

```bash
export VNPAY_MODE=sandbox
export VNPAY_TMN_CODE='THAY_MA_MERCHANT'
export VNPAY_HASH_SECRET='THAY_SECRET'
export VNPAY_RETURN_URL='https://<domain>/vnpay/return'
java -cp '.:lib/json.jar' SeminarDemo
```

Windows PowerShell:

```powershell
$env:VNPAY_MODE='sandbox'
$env:VNPAY_TMN_CODE='THAY_MA_MERCHANT'
$env:VNPAY_HASH_SECRET='THAY_SECRET'
$env:VNPAY_RETURN_URL='https://<domain>/vnpay/return'
java -cp '.;lib/json.jar' SeminarDemo
```

Mở http://localhost:8090 và thanh toán trên trang sandbox. Lấy dữ liệu thẻ thử từ tài liệu VNPAY hiện hành, không nhập thẻ thật. Nếu IPN chưa về, trang có thể còn PENDING; bấm Truy van giao dich để gọi querydr thật. ReturnURL đưa trình duyệt về localhost nên người chạy trình duyệt phải dùng cùng máy chạy demo.

Chế độ sandbox mở cổng 8091 để tunnel có thể nhận callback. `/payments` và `/verify` yêu cầu token nội bộ do chương trình tự sinh. Không công khai Order Service hoặc dùng demo làm ứng dụng production.

## Code nào dùng để thuyết trình?

- `paymentUrl`, `canonical`, `hmac`: Payment request.
- `valid`, `ipn`, `confirm`: Callback handling.
- `query`, `queryHash`, `verifyQuery`: Transaction verification.
- `main`: endpoints của hai service và giao diện HTML đơn giản.
- `/mock` và `/mock/finish`: công cụ tập demo, chỉ đăng ký ở chế độ mock.

API nội bộ:

| Caller | Endpoint | Vai trò |
|---|---|---|
| Order Service | POST Payment `/payments` | Tạo yêu cầu thanh toán từ đơn đã lưu |
| Payment Service | POST Order `/internal/result` | Báo kết quả đã xác minh |
| Order Service | POST Payment `/verify` | Yêu cầu chủ động truy vấn |

Kênh VNPAY:

| Kênh | Phương thức | Dữ liệu |
|---|---|---|
| Payment URL | GET / chuyển hướng | Query string có chữ ký |
| ReturnURL | GET / trình duyệt | Query string, chỉ chuyển về trang kết quả |
| IPN | GET / server gọi server | Query string; merchant trả JSON |
| querydr | POST | JSON; kiểm tra chữ ký response |

## Kiểm tra các trường hợp lỗi

```bash
javac --release 17 -cp lib/json.jar SeminarDemo.java DemoChecks.java
java -cp '.:lib/json.jar' DemoChecks
```

Windows thay `:` bằng `;` trong classpath. Các kiểm tra dùng key local: callback bị sửa tiền, querydr bị sửa chữ ký, response được ký nhưng sai tiền hoặc sai mã đơn, API truy vấn thành công nhưng giao dịch còn pending. Chạy ở chế độ mock.

## Giới hạn và lựa chọn của demo

- Cùng JVM, lưu RAM, khởi động lại mất dữ liệu. `synchronized` chỉ bảo vệ một JVM.
- Mã đơn UUID, giá mẫu cố định. Không có tài khoản người dùng, giỏ hàng hoặc lưu giao dịch vào database.
- IP khách/server đặt loopback để đơn giản. Hệ thống thực tế cần xác định IP đúng theo hạ tầng.
- IPN cập nhật PAID/FAILED; querydr chỉ tự ghi PAID khi xác minh đầy đủ và status 00. Các mã khác giữ nguyên để đối soát. Chưa có scheduler hoặc xử lý đầy đủ mọi trạng thái.
- Không có transaction phân tán. Gọi lại `/internal/result` khi Order đã xử lý không tạo thêm cập nhật.
- Exception HTTP trả 500 cho phía gọi thử lại. Chưa ánh xạ toàn bộ exception IPN sang JSON RspCode 99.
- Hệ thống thật cần database, authentication, giới hạn request, retry/backoff, giám sát và đối soát bền vững.

## Nguồn

- PAY, ReturnURL và IPN: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html
- querydr: https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr&refund.html
- Thư viện JSON: `org.json:json:20250517`, từ Maven Central. Jar kèm theo để không cần Maven khi trình bày.

Chưa chạy thanh toán sandbox thật bằng tài khoản merchant trong quá trình tạo demo này. Hãy chạy thử trước buổi seminar với key của bạn. Chế độ local và kiểm tra lỗi đã được chạy.

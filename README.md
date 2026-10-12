# Hướng dẫn chung: Seminar VNPAY Sandbox

Lê Minh Đăng – 520K0108 · Seminar môn SOA

Tài liệu này gồm toàn bộ các bước: tải về, cài thư viện, chạy demo, thực hành tại lớp và chạy với VNPAY Sandbox thật.

## Chuẩn bị trước buổi học

1. Tải và giải nén gói tài liệu (mục 2).
2. Mở thư mục `VNPAY-demo`, chạy script cài đặt (mục 3):
    - macOS, Linux: `sh cai-dat.sh`
    - Windows: bấm đúp `cai-dat.bat`
3. Thấy 8 dòng `PASS` và dòng `Xong` là máy đã sẵn sàng.

## 1. Nội dung gói tài liệu

| Tệp | Nội dung |
|---|---|
| `HUONG-DAN.pdf` | Tài liệu này |
| `VNPAY-Seminar.pptx` | Slide thuyết trình |
| `VNPAY-demo/SeminarDemo.java` | Mã nguồn demo: Order Service (cổng 8090) và Payment Service (cổng 8091) |
| `VNPAY-demo/DemoChecks.java` | Bộ kiểm tra tự động |
| `VNPAY-demo/lib/json.jar` | Thư viện org.json |
| `VNPAY-demo/cai-dat.sh`, `cai-dat.bat` | Script cài thư viện (macOS/Linux, Windows) |
| `VNPAY-demo/README.md` | Mô tả chi tiết mã nguồn và các endpoint |
| `Thong-tin-sandbox-VNPAY.pdf` | Tài khoản VNPAY Sandbox dùng chung và thẻ test. Chỉ có trong file zip gửi trong nhóm lớp, không có trên GitHub vì chứa khóa bí mật |

## 2. Tải và giải nén

1. Tải gói tài liệu theo một trong hai cách:
    - File `VNPAY-Seminar.zip` gửi trong nhóm lớp.
    - GitHub https://github.com/conchocon154/vnpay-seminar: bấm **Code → Download ZIP**, hoặc chạy `git clone https://github.com/conchocon154/vnpay-seminar.git`.
2. Giải nén ra một thư mục, ví dụ `Documents/VNPAY-Seminar`.
3. Mở terminal ngay trong thư mục `VNPAY-demo`:
    - macOS: chuột phải thư mục `VNPAY-demo` trong Finder, chọn **New Terminal at Folder**.
    - Windows: mở thư mục `VNPAY-demo`, gõ `powershell` vào thanh địa chỉ rồi nhấn Enter.

Các lệnh trong tài liệu đều chạy trong thư mục `VNPAY-demo`.

## 3. Cài thư viện

| Thư viện | Dùng để | Bắt buộc |
|---|---|---|
| JDK 17 trở lên (khuyến nghị Temurin 21) | Biên dịch và chạy Java | Có |
| org.json 20250517 (`lib/json.jar`) | Đọc, ghi JSON | Có, đã kèm sẵn |
| ngrok | Mở địa chỉ HTTPS công khai cho VNPAY gọi về | Chỉ khi chạy sandbox thật |

Không cần Maven, Spring hay IDE.

### Cách 1: chạy script cài đặt (khuyến nghị)

Script kiểm tra Java, cài JDK 21 nếu máy chưa có (hỏi trước khi cài), tải org.json từ Maven Central nếu thiếu và kiểm tra checksum, rồi biên dịch và chạy bộ kiểm tra.

macOS, Linux:

```bash
sh cai-dat.sh
```

Windows: bấm đúp file `cai-dat.bat`.

Kết quả mong đợi: 8 dòng `PASS` và dòng `Xong`. Nếu script vừa cài JDK, mở terminal mới rồi chạy lại script một lần nữa.

Cần thêm ngrok để chạy sandbox thật:

```bash
sh cai-dat.sh --ngrok
```

Windows: trong PowerShell chạy `.\cai-dat.bat -Ngrok`.

Script dùng Homebrew trên macOS, apt trên Ubuntu, winget trên Windows. Máy không có các công cụ này thì script in ra đường dẫn để tải thủ công.

### Cách 2: cài thủ công

1. JDK: tải Temurin 21 tại https://adoptium.net, cài xong mở terminal mới, gõ `java -version` thấy `21` là được.
2. org.json: đã có sẵn trong `lib/json.jar`. Nếu thiếu, tải `json-20250517.jar` tại https://repo1.maven.org/maven2/org/json/json/20250517/, đổi tên thành `json.jar` và đặt vào thư mục `lib`.
3. ngrok (tùy chọn): tải tại https://ngrok.com/download, đăng ký tài khoản miễn phí, chạy `ngrok config add-authtoken <token>`.

## 4. Chạy demo ở chế độ mock

Chế độ mock không cần tài khoản VNPAY, không cần Internet. Chương trình tự giả lập cổng thanh toán. Buổi học dùng chế độ này.

macOS, Linux:

```bash
javac --release 17 -cp lib/json.jar SeminarDemo.java
java -cp '.:lib/json.jar' SeminarDemo
```

Windows PowerShell:

```powershell
javac --release 17 -cp lib/json.jar SeminarDemo.java
java -cp '.;lib/json.jar' SeminarDemo
```

Kết quả mong đợi: terminal hiện `Mode LOCAL MOCK http://localhost:8090`. Giữ nguyên terminal này trong suốt mục 4 và 5. Dừng chương trình bằng **Ctrl+C**.

### Tình huống 1: IPN gửi trùng

1. Mở http://localhost:8090, bấm **Thanh toan**.
2. Ở trang mock, bấm **Thanh cong + IPN**.

Kết quả mong đợi: trang kết quả hiện `PAID`; terminal có hai dòng

```text
First IPN {"Message":"Confirm success","RspCode":"00"}
Duplicate IPN {"Message":"Already confirmed","RspCode":"02"}
```

Lần thứ hai trả `02` vì đơn đã được xác nhận. Đơn chỉ cập nhật một lần.

### Tình huống 2: khách hàng quay về trước khi có IPN

1. Quay lại http://localhost:8090, bấm **Thanh toan**.
2. Bấm **Thanh cong, chua gui IPN**. Trang kết quả hiện `PENDING`: khách đã quay về nhưng server chưa có kết quả đáng tin cậy.
3. Bấm **Truy van giao dich**.

Kết quả mong đợi: đơn chuyển `PAID`; terminal có dòng `querydr verified=true local=PAID`.

## 5. Thực hành: gọi trực tiếp các endpoint

Demo vẫn chạy ở terminal đầu. Mở terminal thứ hai trong thư mục `VNPAY-demo`. Windows dùng `curl.exe` thay cho `curl`.

### Bài 1: gọi API nội bộ không có token

```bash
curl -X POST http://localhost:8091/payments
```

Kết quả mong đợi: `Forbidden`. API nội bộ yêu cầu header `X-Demo-Token`; bên ngoài không tạo được giao dịch.

### Bài 2: gửi IPN giả mạo

```bash
curl "http://localhost:8091/vnpay/ipn?vnp_TxnRef=abc&vnp_SecureHash=123"
```

Kết quả mong đợi:

```text
{"Message":"Invalid signature or merchant","RspCode":"97"}
```

Không có HashSecret thì không tạo được chữ ký hợp lệ.

### Bài 3: gửi IPN hợp lệ, gửi trùng, sửa số tiền

1. Mở http://localhost:8090, bấm **Thanh toan**. Đến trang mock thì **không bấm nút nào**.
2. Sao chép mã sau `ref=` trên thanh địa chỉ, ví dụ `bee10364c7ee476da708209f3000a6ed`. Gọi mã này là REF.
3. Chạy lệnh dưới, thay `REF` bằng mã vừa sao chép:

    ```bash
    curl -si -X POST http://localhost:8091/mock/finish -d "ref=REF&scenario=delayed"
    ```

4. Trong kết quả có dòng `Location: http://localhost:8091/vnpay/return?vnp_Amount=2500000&...`. Sao chép toàn bộ phần sau dấu `?`. Gọi phần này là QUERY.
5. Gửi IPN với QUERY (giữ dấu ngoặc kép):

    ```bash
    curl "http://localhost:8091/vnpay/ipn?QUERY"
    ```

    Kết quả mong đợi: `{"Message":"Confirm success","RspCode":"00"}`. Mở http://localhost:8090/result?ref=REF thấy `PAID`.

6. Chạy lại đúng lệnh ở bước 5. Kết quả mong đợi: `{"Message":"Already confirmed","RspCode":"02"}`.
7. Trong QUERY, sửa `vnp_Amount=2500000` thành `vnp_Amount=100` rồi gọi lại. Kết quả mong đợi: `RspCode` là `97`, vì số tiền thay đổi thì chữ ký không còn khớp.

ReturnURL dùng được làm IPN vì hai kênh mang cùng bộ tham số và cùng chữ ký. VNPAY thật cũng gửi cùng bộ tham số cho ReturnURL và IPN.

## 6. Chạy với VNPAY Sandbox thật

Phần này dùng tài khoản trong `Thong-tin-sandbox-VNPAY.pdf` (chỉ có trong file zip). Tài khoản chỉ dùng để học, giao dịch dùng thẻ test, không phát sinh tiền thật. Muốn dùng tài khoản riêng thì đăng ký tại https://sandbox.vnpayment.vn/devreg/.

**Bước 1. Mở ngrok** (cài bằng `sh cai-dat.sh --ngrok` hoặc thủ công theo mục 3). Ở một terminal riêng:

```bash
ngrok http 8091
```

Ghi lại địa chỉ HTTPS ngrok cấp, ví dụ `https://abcd-1234.ngrok-free.app`.

**Bước 2. Đặt biến môi trường và chạy.** Lấy `vnp_TmnCode` và `vnp_HashSecret` trong PDF. Sao chép chính xác, không thừa khoảng trắng. Không ghi các giá trị này vào mã nguồn.

macOS, Linux:

```bash
export VNPAY_MODE=sandbox
export VNPAY_TMN_CODE='<vnp_TmnCode trong PDF>'
export VNPAY_HASH_SECRET='<vnp_HashSecret trong PDF>'
export VNPAY_RETURN_URL='https://<domain ngrok>/vnpay/return'
java -cp '.:lib/json.jar' SeminarDemo
```

Windows PowerShell:

```powershell
$env:VNPAY_MODE='sandbox'
$env:VNPAY_TMN_CODE='<vnp_TmnCode trong PDF>'
$env:VNPAY_HASH_SECRET='<vnp_HashSecret trong PDF>'
$env:VNPAY_RETURN_URL='https://<domain ngrok>/vnpay/return'
java -cp '.;lib/json.jar' SeminarDemo
```

Kết quả mong đợi: terminal hiện `Mode VNPAY SANDBOX`.

**Bước 3. Thanh toán thử.** Mở http://localhost:8090, bấm **Thanh toan**. Trình duyệt chuyển sang trang VNPAY Sandbox. Chọn thẻ nội địa, ngân hàng **NCB**, nhập số thẻ, tên chủ thẻ, ngày phát hành và mã OTP theo mục **Thẻ test** trong PDF.

**Bước 4. Cập nhật đơn.** Sau khi thanh toán, VNPAY đưa trình duyệt về trang kết quả. Tài khoản sandbox được dùng chung nên IPN không gửi về máy của từng người; đơn có thể vẫn `PENDING`. Bấm **Truy van giao dich**: Payment Service gọi querydr tới VNPAY, xác minh kết quả rồi cập nhật đơn thành `PAID`.

Khi quay lại chế độ mock, mở terminal mới hoặc xóa biến `VNPAY_MODE`.

## 7. Kiểm tra tự động

Tắt demo rồi chạy:

```bash
javac --release 17 -cp lib/json.jar SeminarDemo.java DemoChecks.java
java -cp '.:lib/json.jar' DemoChecks
```

Windows đổi `:` thành `;` trong lệnh `java`. Kết quả mong đợi: 8 dòng `PASS`.

## 8. Lỗi thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| `javac` không được nhận diện | Chưa cài JDK hoặc chưa mở lại terminal sau khi cài |
| `Address already in use` | Cổng 8090 hoặc 8091 đang bị chiếm; tắt chương trình đang chạy rồi chạy lại |
| `Could not find or load main class` trên Windows | Classpath dùng `;` thay cho `:` |
| `curl` trên Windows trả lỗi lạ | Dùng `curl.exe` thay cho `curl` |
| `Set sandbox credentials` | Đã bật `VNPAY_MODE=sandbox` nhưng chưa đặt TmnCode hoặc HashSecret |
| Trang VNPAY báo "Sai chữ ký" | HashSecret sao chép thiếu hoặc thừa ký tự |
| Trang kết quả không mở sau khi thanh toán | `VNPAY_RETURN_URL` sai domain ngrok, hoặc ngrok đã tắt |

## 9. Tài liệu tham khảo

- Tài liệu API thanh toán: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html
- Tài liệu truy vấn giao dịch: https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr&refund.html
- Đăng ký tài khoản sandbox riêng: https://sandbox.vnpayment.vn/devreg/

# Seminar VNPAY Sandbox: hướng dẫn tải và chạy demo

Lê Minh Đăng – 520K0108 · Seminar môn SOA

## 1. Nội dung gói tài liệu

| Tệp | Nội dung |
|---|---|
| `VNPAY-Seminar.pptx` | Slide thuyết trình |
| `VNPAY-demo/` | Mã nguồn demo: `SeminarDemo.java`, `DemoChecks.java`, thư viện `lib/json.jar` |
| `VNPAY-demo/cai-dat.sh`, `cai-dat.bat` | Script cài thư viện (macOS/Linux, Windows) |
| `VNPAY-demo/THUC-HANH.md` | Bài thực hành tại lớp, có kết quả mong đợi từng bước |
| `VNPAY-demo/README.md` | Mô tả chi tiết mã nguồn và các endpoint |
| `HUONG-DAN.pdf` | Bản PDF của hướng dẫn này |

`Thong-tin-sandbox-VNPAY.pdf` (tài khoản sandbox dùng chung và thẻ test) chứa khóa bí mật nên không đưa lên GitHub. File này được gửi riêng trong nhóm lớp.

## 2. Tải và giải nén

1. Tải mã nguồn: trên trang GitHub bấm **Code → Download ZIP**, hoặc chạy `git clone https://github.com/conchocon154/vnpay-seminar.git`. Nếu nhận file `VNPAY-Seminar.zip` trong nhóm lớp thì dùng file đó.
2. Giải nén ra một thư mục, ví dụ `Documents/VNPAY-Seminar`.
3. Mở terminal ngay trong thư mục `VNPAY-demo`:
    - macOS: chuột phải thư mục `VNPAY-demo` trong Finder, chọn **New Terminal at Folder**.
    - Windows: mở thư mục `VNPAY-demo`, gõ `powershell` vào thanh địa chỉ rồi nhấn Enter.

## 3. Cài thư viện

Demo cần các thư viện sau:

| Thư viện | Dùng để | Bắt buộc |
|---|---|---|
| JDK 17 trở lên (khuyến nghị Temurin 21) | Biên dịch và chạy Java | Có |
| org.json 20250517 (`lib/json.jar`) | Đọc, ghi JSON | Có, đã kèm sẵn |
| ngrok | Mở địa chỉ HTTPS công khai cho VNPAY gọi về | Chỉ khi chạy sandbox thật |

Không cần Maven, Spring hay IDE.

### Cách 1: chạy script cài đặt (khuyến nghị)

Script nằm trong thư mục `VNPAY-demo`. Script kiểm tra Java, cài JDK 21 nếu máy chưa có (hỏi trước khi cài), tải org.json từ Maven Central nếu thiếu và kiểm tra checksum, rồi biên dịch và chạy bộ kiểm tra.

macOS, Linux (mở terminal trong thư mục `VNPAY-demo`):

```bash
sh cai-dat.sh
```

Windows: bấm đúp file `cai-dat.bat`.

Cuối cùng hiện 8 dòng `PASS` và dòng `Xong` là cài thành công. Nếu script vừa cài JDK, mở terminal mới rồi chạy lại script một lần nữa.

Cần thêm ngrok để chạy sandbox thật thì thêm tham số:

```bash
sh cai-dat.sh --ngrok
```

Windows: mở PowerShell trong thư mục `VNPAY-demo` rồi chạy `.\cai-dat.bat -Ngrok`.

Script dùng Homebrew trên macOS, apt trên Ubuntu, winget trên Windows. Máy không có các công cụ này thì script in ra đường dẫn để tải thủ công.

### Cách 2: cài thủ công

1. JDK: tải Temurin 21 tại https://adoptium.net, cài xong mở terminal mới, gõ `java -version` thấy `21` là được.
2. org.json: đã có sẵn trong `VNPAY-demo/lib/json.jar`. Nếu thiếu, tải `json-20250517.jar` tại https://repo1.maven.org/maven2/org/json/json/20250517/ rồi đổi tên thành `json.jar`, đặt vào thư mục `lib`.
3. ngrok (tùy chọn): tải tại https://ngrok.com/download, đăng ký tài khoản miễn phí, chạy `ngrok config add-authtoken <token>`.

## 4. Chạy chế độ mock (dùng trong buổi học)

Chế độ mock không cần tài khoản VNPAY, không cần Internet. Chương trình tự giả lập cổng thanh toán.

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

Terminal hiện `Mode LOCAL MOCK http://localhost:8090` là chạy thành công. Mở http://localhost:8090 trên trình duyệt.

Hai tình huống demo:

1. Bấm **Thanh toan**, chọn **Thanh cong + IPN**. Đơn chuyển `PAID`; terminal hiện IPN lần đầu trả `00`, lần sau trả `02`.
2. Tạo đơn mới, chọn **Thanh cong, chua gui IPN**. Đơn vẫn `PENDING`. Bấm **Truy van giao dich**, đơn chuyển `PAID`.

Các bài thực hành tiếp theo (gọi trực tiếp endpoint bằng curl, gửi IPN giả, gửi IPN trùng) nằm trong `VNPAY-demo/THUC-HANH.md`.

Dừng chương trình bằng **Ctrl+C**.

## 5. Chạy với VNPAY Sandbox thật

Phần này dùng tài khoản sandbox ghi trong `Thong-tin-sandbox-VNPAY.pdf`. Tài khoản chỉ dùng để học, giao dịch dùng thẻ test, không phát sinh tiền thật.

**Bước 1. Mở ngrok** (cài bằng `sh cai-dat.sh --ngrok` hoặc thủ công theo mục 3). Ở một terminal riêng:

```bash
ngrok http 8091
```

Ghi lại địa chỉ HTTPS ngrok cấp, ví dụ `https://abcd-1234.ngrok-free.app`.

**Bước 2. Đặt biến môi trường và chạy.** Lấy `vnp_TmnCode` và `vnp_HashSecret` trong `Thong-tin-sandbox-VNPAY.pdf`. Sao chép chính xác, không thừa khoảng trắng. Không ghi các giá trị này vào mã nguồn.

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

Terminal hiện `Mode VNPAY SANDBOX` là đúng chế độ.

**Bước 3. Thanh toán thử.** Mở http://localhost:8090, bấm **Thanh toan**. Trình duyệt chuyển sang trang VNPAY Sandbox. Chọn thẻ nội địa, ngân hàng **NCB**, nhập số thẻ, tên chủ thẻ, ngày phát hành và mã OTP theo mục **Thẻ test** trong PDF.

**Bước 4. Cập nhật đơn.** Sau khi thanh toán, VNPAY đưa trình duyệt về trang kết quả. Tài khoản sandbox được dùng chung nên IPN không gửi về máy của từng người; đơn có thể vẫn `PENDING`. Bấm **Truy van giao dich**: Payment Service gọi querydr tới VNPAY, xác minh kết quả rồi cập nhật đơn thành `PAID`.

Khi quay lại chế độ mock, đóng terminal và mở terminal mới, hoặc xóa biến `VNPAY_MODE`.

## 6. Kiểm tra tự động

Tắt demo rồi chạy:

```bash
javac --release 17 -cp lib/json.jar SeminarDemo.java DemoChecks.java
java -cp '.:lib/json.jar' DemoChecks
```

Windows đổi `:` thành `;` trong lệnh `java`. Kết quả: 8 dòng `PASS`.

## 7. Lỗi thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| `javac` không được nhận diện | Chưa cài JDK hoặc chưa mở lại terminal sau khi cài |
| `Address already in use` | Cổng 8090 hoặc 8091 đang bị chiếm; tắt chương trình đang chạy rồi chạy lại |
| `Could not find or load main class` trên Windows | Classpath dùng `;` thay cho `:` |
| `Set sandbox credentials` | Đã bật `VNPAY_MODE=sandbox` nhưng chưa đặt TmnCode hoặc HashSecret |
| Trang VNPAY báo "Sai chữ ký" | HashSecret sao chép thiếu hoặc thừa ký tự |
| Trang kết quả không mở sau khi thanh toán | `VNPAY_RETURN_URL` sai domain ngrok, hoặc ngrok đã tắt |

## 8. Tài liệu tham khảo

- Tài liệu API thanh toán: https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html
- Tài liệu truy vấn giao dịch: https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr&refund.html
- Đăng ký tài khoản sandbox riêng: https://sandbox.vnpayment.vn/devreg/

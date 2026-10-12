# Thực hành tại lớp

Làm theo thứ tự, mỗi bước có kết quả mong đợi để tự kiểm tra. Chỉ cần JDK 17 trở lên, không cần key VNPAY.

## Bước 0. Kiểm tra máy

```bash
java -version
```

Ra `17` trở lên là được. Chưa có thì cài Temurin JDK 21 tại adoptium.net.

## Bước 1. Chạy demo

Mở terminal ngay trong thư mục `VNPAY-demo`.

macOS, Linux:

```bash
javac --release 17 -cp lib/json.jar SeminarDemo.java
java -cp '.:lib/json.jar' SeminarDemo
```

Windows PowerShell (đổi `:` thành `;`):

```powershell
javac --release 17 -cp lib/json.jar SeminarDemo.java
java -cp '.;lib/json.jar' SeminarDemo
```

Kết quả mong đợi: terminal hiện `Mode LOCAL MOCK http://localhost:8090`. Để nguyên terminal này, đừng tắt.

Lỗi `Address already in use` nghĩa là cổng 8090 hoặc 8091 đang bị chiếm. Tắt chương trình đang dùng cổng đó rồi chạy lại.

## Bước 2. Tình huống 1: IPN tới hai lần

1. Mở http://localhost:8090, bấm **Thanh toan**.
2. Ở trang mock, bấm **Thanh cong + IPN**.

Kết quả mong đợi:

- Trình duyệt về trang kết quả, đơn `PAID`.
- Terminal có hai dòng:

```text
First IPN {"Message":"Confirm success","RspCode":"00"}
Duplicate IPN {"Message":"Already confirmed","RspCode":"02"}
```

Lần thứ hai trả `02` vì đơn đã hết PENDING. Đơn chỉ được xác nhận một lần.

## Bước 3. Tình huống 2: khách quay về trước khi có IPN

1. Quay lại http://localhost:8090, bấm **Thanh toan**.
2. Bấm **Thanh cong, chua gui IPN**.
3. Trang kết quả hiện `PENDING`. Khách đã về nhưng server chưa có kết quả đáng tin nên chưa đổi đơn.
4. Bấm **Truy van giao dich**.

Kết quả mong đợi: đơn thành `PAID`, terminal có dòng `querydr verified=true local=PAID`.

## Bước 4. Gọi thẳng vào service

Mở terminal thứ hai, demo vẫn chạy ở terminal đầu. Windows gõ `curl.exe` thay cho `curl`.

### 4.1. Gọi API nội bộ mà không có token

```bash
curl -X POST http://localhost:8091/payments
```

Kết quả mong đợi: `Forbidden`. API nội bộ cần header `X-Demo-Token`, bên ngoài không tự tạo giao dịch được.

### 4.2. Gửi IPN giả

```bash
curl "http://localhost:8091/vnpay/ipn?vnp_TxnRef=abc&vnp_SecureHash=123"
```

Kết quả mong đợi:

```text
{"Message":"Invalid signature or merchant","RspCode":"97"}
```

Không có HashSecret thì không tạo được chữ ký đúng.

### 4.3. Gửi IPN thật, gửi lặp, rồi sửa số tiền

1. Mở http://localhost:8090, bấm **Thanh toan**. Đến trang mock thì **đừng bấm nút nào**.
2. Copy mã sau `ref=` trên thanh địa chỉ, ví dụ `bee10364c7ee476da708209f3000a6ed`. Gọi mã này là REF.
3. Chạy lệnh dưới, thay `REF` bằng mã vừa copy:

```bash
curl -si -X POST http://localhost:8091/mock/finish -d "ref=REF&scenario=delayed"
```

4. Trong kết quả có dòng `Location: http://localhost:8091/vnpay/return?vnp_Amount=2500000&...`. Copy toàn bộ phần sau dấu `?`. Gọi phần này là QUERY.
5. Gửi IPN với QUERY (nhớ giữ dấu ngoặc kép):

```bash
curl "http://localhost:8091/vnpay/ipn?QUERY"
```

Kết quả mong đợi: `{"Message":"Confirm success","RspCode":"00"}`. Mở http://localhost:8090/result?ref=REF thấy `PAID`.

6. Chạy lại đúng lệnh ở bước 5.

Kết quả mong đợi: `{"Message":"Already confirmed","RspCode":"02"}`.

7. Trong QUERY, sửa `vnp_Amount=2500000` thành `vnp_Amount=100` rồi gọi lại.

Kết quả mong đợi: `RspCode` là `97`. Số tiền đổi thì chữ ký không còn khớp.

Link ReturnURL dùng làm IPN được vì cả hai mang cùng bộ tham số và cùng chữ ký. VNPAY thật cũng gửi cùng bộ tham số cho ReturnURL và IPN.

## Bước 5. Chạy bộ kiểm tra lỗi

Tắt demo ở terminal đầu bằng Ctrl+C rồi chạy:

macOS, Linux:

```bash
javac --release 17 -cp lib/json.jar SeminarDemo.java DemoChecks.java
java -cp '.:lib/json.jar' DemoChecks
```

Windows thì đổi `:` thành `;` trong lệnh `java`.

Kết quả mong đợi: 8 dòng, dòng nào cũng bắt đầu bằng `PASS`.

## Muốn chạy với VNPAY Sandbox thật

Xem mục "Chạy VNPAY Sandbox thật" trong README.md. Cần đăng ký tài khoản sandbox, cài ngrok, và đặt key bằng biến môi trường. Không ghi key vào file code.

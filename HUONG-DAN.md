# Hướng dẫn chạy PaymentService.java

Trong thư mục có 2 file code, để chung một chỗ:

- `PaymentService.java`: Payment Service tích hợp VNPAY, bản hoàn chỉnh
- `index.html`: giao diện cửa hàng

Bước 1 và bước 3 phải chờ email xác nhận, nên làm trước.

## Bước 1. Tài khoản sandbox VNPAY

1. Vào https://sandbox.vnpayment.vn/devreg/
2. Tên website: `VNPAY Shop Demo`
3. Địa chỉ URL: `https://vnpay-demo-<mssv>.com` (không cần có thật, chỉ cần đúng định dạng)
4. Email của bạn, mật khẩu 6–15 ký tự
5. Mở email, lưu lại `vnp_TmnCode` (8 ký tự) và `vnp_HashSecret` (32 ký tự)

Lỗi ở ô URL: `http://localhost:8080` sai định dạng vì thiếu đuôi tên miền. Báo "Website đã được đăng ký" thì đổi tên khác.

## Bước 2. JDK 17 trở lên

1. Gõ `java -version`, ra 17 trở lên là được
2. Chưa có: macOS `brew install openjdk`, Windows và Linux tải ở https://adoptium.net

## Bước 3. ngrok (để nhận IPN)

1. Đăng ký ở https://dashboard.ngrok.com/signup
2. Cài:
    - macOS: `brew install --cask ngrok`
    - Linux: `sudo snap install ngrok`
    - Windows: tải zip ở https://ngrok.com/download, giải nén `ngrok.exe` vào chung thư mục với `PaymentService.java`, khi chạy gõ `.\ngrok.exe` thay cho `ngrok`
3. Vào https://dashboard.ngrok.com/get-started/your-authtoken, copy dòng `ngrok config add-authtoken ...`, dán vào terminal, Enter
4. Gõ `ngrok config check`, ra `Valid configuration file` là xong

## Bước 4. Chạy thử, chưa cần ngrok

Mở thư mục bằng VSCode, Terminal → New Terminal.

macOS, Linux:

```bash
export VNPAY_TMN_CODE=xxxxxxxx
export VNPAY_HASH_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
java PaymentService.java
```

Windows PowerShell:

```powershell
$env:VNPAY_TMN_CODE="xxxxxxxx"
$env:VNPAY_HASH_SECRET="xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
java PaymentService.java
```

Thay `xxxx` bằng giá trị trong email. Log hiện `TmnCode ...` là đã đọc được key.

1. Mở http://localhost:8080, bấm **Thanh toán**
2. Trang VNPAY hiện ra, nhập thẻ test:

    | Ô | Nhập |
    |---|---|
    | Ngân hàng | NCB |
    | Số thẻ | `9704198526191432198` |
    | Tên chủ thẻ | `NGUYEN VAN A` |
    | Ngày phát hành | `07/15` |
    | OTP | `123456` |

3. Quay về trang kết quả thấy **PAID**. Log ghi `querydr ... -> PAID` vì chưa có ngrok nên IPN chưa về

## Bước 5. Chạy có ngrok để nhận IPN

1. Terminal 1: `ngrok http 8080`, copy link `https://....ngrok-free.app`, để yên cửa sổ này
2. Vào https://sandbox.vnpayment.vn/merchantv2/ → Cấu hình → Thông tin website, khai:
    - URL trả về: `https://<id>.ngrok-free.app/vnpay/return`
    - URL nhận kết quả IPN: `https://<id>.ngrok-free.app/vnpay/ipn`

Terminal 2: đặt 2 biến như bước 4, thêm biến `VNPAY_RETURN_URL` rồi chạy.

macOS, Linux:

```bash
export VNPAY_RETURN_URL=https://<id>.ngrok-free.app/vnpay/return
java PaymentService.java
```

Windows PowerShell:

```powershell
$env:VNPAY_RETURN_URL="https://<id>.ngrok-free.app/vnpay/return"
java PaymentService.java
```

Sau đó thanh toán lại. Log có dòng `IPN ... -> PAID` là VNPAY đã gọi về. Mở http://127.0.0.1:4040 để xem request IPN mà VNPAY gửi tới.

## Lỗi hay gặp

| Hiện tượng | Cách sửa |
|---|---|
| Log ghi "Chưa có VNPAY_TMN_CODE" | Chưa đặt biến môi trường, hoặc đặt ở terminal khác |
| `Address already in use` | Port 8080 đang bị chiếm, tắt chương trình cũ |
| VNPAY báo "Sai chữ ký" | HashSecret sai hoặc dính dấu cách |
| Không thấy dòng IPN | Chưa khai URL IPN, hoặc link ngrok đã đổi |
| Thanh toán xong không quay về web | Chưa đặt `VNPAY_RETURN_URL` rồi chạy lại |

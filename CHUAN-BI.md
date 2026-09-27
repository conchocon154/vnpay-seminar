# Chuẩn bị trước seminar VNPAY

Bước 1 và bước 3 phải chờ email, làm sớm.

## Bước 1. Tài khoản sandbox VNPAY

1. Vào https://sandbox.vnpayment.vn/devreg/
2. Tên website: `VNPAY Shop Demo`
3. Địa chỉ URL: `https://vnpay-demo-<mssv>.com` (không cần có thật, chỉ cần đúng định dạng)
4. Email: email của bạn. Mật khẩu: 6–15 ký tự
5. Mở email, lưu lại `vnp_TmnCode` (8 ký tự) và `HashSecret` (32 ký tự)

Lỗi hay gặp ở ô URL:
- `http://localhost:8080` → sai định dạng, phải có đuôi tên miền
- "Website đã được đăng ký" → đổi tên khác, gắn MSSV vào

## Bước 2. JDK 17+

1. Gõ `java -version`, ra 17 trở lên thì qua bước 3
2. Chưa có:
   - macOS: `brew install openjdk`
   - Windows / Linux: tải ở https://adoptium.net

## Bước 3. ngrok

Mỗi người một tài khoản riêng, bản free chỉ mở được 1 tunnel.

1. Đăng ký ở https://dashboard.ngrok.com/signup (email hoặc Google)
2. Cài:
   - macOS: `brew install --cask ngrok`
   - Linux: `sudo snap install ngrok`
   - Windows: tải zip ở https://ngrok.com/download, giải nén `ngrok.exe` vào cùng thư mục với `ShopStart.java`, lúc chạy gõ `.\ngrok.exe` thay cho `ngrok`
3. Gõ `ngrok version`, ra số phiên bản là cài xong
4. Vào https://dashboard.ngrok.com/get-started/your-authtoken, copy dòng `ngrok config add-authtoken ...`, dán vào terminal, Enter
5. Gõ `ngrok config check`, ra `Valid configuration file` là xong
6. Chạy thử:

   ```bash
   ngrok http 8080
   ```

   Dòng Forwarding hiện địa chỉ `https://....ngrok-free.app` là được. `Ctrl+C` để tắt.

Địa chỉ ngrok đổi mỗi lần chạy lại, chưa khai vào VNPAY ở bước này.

## Bước 4. Chạy thử code

1. Tải `ShopStart.java` và `index.html` trên Drive, để chung một thư mục
2. Mở terminal tại thư mục đó:

   ```bash
   java ShopStart.java
   ```

3. Mở http://localhost:8080, thấy cửa hàng
4. Bấm Thanh toán ra "Chưa làm TODO 5" là đúng

Không double-click mở `index.html`, trang cần server Java chạy mới bấm được.

## Checklist

- [ ] Có email VNPAY chứa TmnCode và HashSecret
- [ ] `java -version` ra 17+
- [ ] `ngrok config check` ra `Valid configuration file`
- [ ] `ngrok http 8080` hiện địa chỉ `.ngrok-free.app`
- [ ] `java ShopStart.java` chạy, localhost:8080 thấy cửa hàng

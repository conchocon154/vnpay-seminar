# Chuẩn bị trước buổi seminar VNPAY

Nhờ mọi người làm 3 việc dưới đây trước hôm seminar. Việc số 1 phải chờ email nên làm sớm giùm mình.

## 1. Đăng ký tài khoản sandbox VNPAY

Vào https://sandbox.vnpayment.vn/devreg/ và điền:

- Tên website: `VNPAY Shop Demo`
- Địa chỉ URL: một tên miền không trùng ai, ví dụ `https://vnpay-demo-<mssv>.com`
- Email đăng ký: email của bạn
- Mật khẩu: 6 đến 15 ký tự

Hai lỗi hay dính ở ô Địa chỉ URL:

- Điền `http://localhost:8080` sẽ trượt, form bắt buộc phải có đuôi tên miền 2 đến 10 chữ cái.
- Điền tên miền phổ biến kiểu `github.com` thì báo "Website đã được đăng ký" vì người khác đăng ký trước rồi. Gắn mã số sinh viên vào cho chắc.

Không cần sở hữu tên miền đó thật, sandbox chỉ kiểm tra định dạng thôi.

Email trả về 2 giá trị, nhớ giữ lại: `vnp_TmnCode` (8 ký tự) và `HashSecret` (32 ký tự).

## 2. Cài JDK 17 trở lên

Gõ `java -version`, thấy 17 trở lên là được. Chưa có thì:

- macOS: `brew install openjdk`
- Windows hoặc Linux: tải ở https://adoptium.net

Không cần Maven, không cần IntelliJ, không cần cài thư viện gì thêm.

## 3. Chạy thử code xuất phát

Tải 2 file trong thư mục này:

- `index.html`: double-click là mở. Cửa hàng HTML thuần, chưa có server.
- `ShopStart.java`: chạy `java ShopStart.java` rồi mở http://localhost:8080

Cả hai đều hiện được cửa hàng, bấm Thanh toán thì báo "Chưa nối VNPAY". Đúng rồi đấy, phần còn thiếu chính là nội dung buổi seminar.

## ngrok thì sao?

Buổi seminar chạy được hết trên localhost nên không bắt buộc cài. Ai muốn thử luồng IPN thật thì cài thêm:

```
brew install --cask ngrok
ngrok config add-authtoken <token lấy ở dashboard.ngrok.com>
```

## Trước khi vào lớp, kiểm lại

1. Có email VNPAY chứa TmnCode và HashSecret
2. `java -version` ra 17 trở lên
3. `java ShopStart.java` chạy được, mở http://localhost:8080 thấy cửa hàng

Đủ 3 cái này là code theo được từ đầu tới cuối.

Source code đầy đủ: https://github.com/conchocon154/vnpay-seminar

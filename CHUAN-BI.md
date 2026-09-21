# Chuẩn bị trước buổi seminar VNPAY

Nhờ mọi người làm 4 việc dưới đây trước hôm seminar. Việc 1 và việc 3 đều phải chờ email nên làm sớm giùm mình.

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

## 3. Đăng ký và cài ngrok

Buổi này bắt buộc dùng ngrok. Lý do: sau khi khách trả tiền xong, VNPAY gọi ngược về server của mình để báo kết quả (gọi là IPN). Máy mình chạy ở `localhost` thì VNPAY ở ngoài internet không với tới được, nên cần ngrok tạo một địa chỉ công khai trỏ về máy mình.

Mỗi người phải có tài khoản riêng, bản miễn phí chỉ cho mở 1 tunnel một lúc nên không dùng chung được.

### 3.1. Tạo tài khoản

1. Vào https://dashboard.ngrok.com/signup
2. Đăng ký bằng email, hoặc bấm nút đăng nhập bằng Google / GitHub cho nhanh
3. Mở email xác nhận rồi bấm vào link kích hoạt

### 3.2. Cài ngrok

macOS:

```
brew install --cask ngrok
```

Windows:

1. Tải file zip ở https://ngrok.com/download (chọn Windows)
2. Giải nén ra được `ngrok.exe`
3. Bỏ `ngrok.exe` vào cùng thư mục với `ShopStart.java` cho tiện, hoặc thêm thư mục đó vào PATH

Linux:

```
sudo snap install ngrok
```

Kiểm tra: gõ `ngrok version`, ra số phiên bản là được. Trên Windows nếu chưa thêm PATH thì gõ `.\ngrok.exe version`.

### 3.3. Gắn authtoken

Đây là bước hay quên nhất. Không có authtoken thì ngrok không chạy.

1. Vào https://dashboard.ngrok.com/get-started/your-authtoken
2. Ở khung "Command line" có sẵn dòng `ngrok config add-authtoken ...`, bấm icon copy bên phải, nó copy kèm token thật của bạn
3. Dán vào terminal rồi Enter

Kiểm tra bằng `ngrok config check`, phải ra `Valid configuration file at ...`. Còn báo `no such file` là chưa gắn được token.

### 3.4. Chạy thử tunnel

```
ngrok http 8080
```

Màn hình sẽ hiện dòng kiểu:

```
Forwarding   https://a1b2-42-115-242-109.ngrok-free.app -> http://localhost:8080
```

Cái địa chỉ `https://....ngrok-free.app` đó là địa chỉ công khai của máy bạn. Nhớ 2 điều:

- Địa chỉ này **đổi mỗi lần chạy lại ngrok**, nên hôm seminar chạy xong mới đi khai vào VNPAY, đừng khai từ hôm trước.
- Lần đầu mở bằng trình duyệt nó chặn một trang cảnh báo "You are about to visit...", bấm **Visit Site** là qua. IPN không dính trang này vì không phải trình duyệt.

Bấm `Ctrl+C` để tắt tunnel.

### 3.5. Khai 2 URL vào VNPAY

Việc này làm tại lớp, ghi ra đây để biết trước. Đăng nhập https://sandbox.vnpayment.vn/merchantv2/, vào Cấu hình, Thông tin website, rồi điền:

- URL trả về: `https://<địa-chỉ-ngrok>/vnpay/return`
- URL nhận kết quả (IPN): `https://<địa-chỉ-ngrok>/vnpay/ipn`

Lưu ý thật: portal sandbox thỉnh thoảng lỗi, mục Danh sách website trống trơn hoặc Cài đặt thông báo báo "Kết nối hệ thống tạm thời bị gián đoạn". Gặp vậy thì khỏi lo, code vẫn chốt được đơn bằng API querydr, trong buổi mình sẽ nói kỹ chỗ này.

## 4. Chạy thử code xuất phát

Tải 2 file trong thư mục này:

- `index.html`: double-click là mở. Cửa hàng HTML thuần, chưa có server.
- `ShopStart.java`: chạy `java ShopStart.java` rồi mở http://localhost:8080

Cả hai đều hiện được cửa hàng, bấm Thanh toán thì báo "Chưa nối VNPAY". Đúng rồi đấy, phần còn thiếu chính là nội dung buổi seminar.

## Trước khi vào lớp, kiểm lại

1. Có email VNPAY chứa TmnCode và HashSecret
2. `java -version` ra 17 trở lên
3. `ngrok config check` ra `Valid configuration file`
4. `ngrok http 8080` chạy được, hiện ra địa chỉ `.ngrok-free.app`
5. `java ShopStart.java` chạy được, mở http://localhost:8080 thấy cửa hàng

Đủ 5 cái này là code theo được từ đầu tới cuối.

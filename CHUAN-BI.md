# Cài trước khi lên lớp

Hôm seminar cả lớp sẽ gõ code cùng mình, nên mỗi người cần sẵn một tài khoản sandbox VNPAY, JDK và ngrok. Cái nào cũng dễ, nhưng tài khoản VNPAY với ngrok đều phải chờ email xác nhận. Làm trước một hai hôm cho khỏi cuống.

## Tài khoản sandbox VNPAY

Vào https://sandbox.vnpayment.vn/devreg/ rồi điền form. Tên website ghi gì cũng được, mình để `VNPAY Shop Demo`. Email là email của bạn, mật khẩu từ 6 tới 15 ký tự.

Ô Địa chỉ URL là chỗ hay kẹt. Form này bắt phải có đuôi tên miền, nên `http://localhost:8080` bị từ chối. Còn điền mấy tên quen như `github.com` thì nó báo "Website đã được đăng ký", vì có người lấy rồi. Cách đơn giản nhất là gắn MSSV vào, kiểu `https://vnpay-demo-520k0108.com`. Tên miền này không cần có thật, sandbox chỉ xét định dạng.

Vài phút sau VNPAY gửi email có `vnp_TmnCode` (8 ký tự) và `HashSecret` (32 ký tự). Hai cái này dùng ngay ở TODO đầu tiên, giữ email lại.

## JDK 17

Mở terminal gõ `java -version`. Ra 17, 21 hay mới hơn là xong phần này.

Chưa có thì trên macOS gõ `brew install openjdk`, còn Windows với Linux tải ở https://adoptium.net. Code chỉ có một file Java, chạy thẳng bằng lệnh `java`, không cần Maven hay IntelliJ.

## ngrok

Giảng viên yêu cầu buổi này phải có ngrok, và thật ra không có cũng không được. Khách thanh toán xong, VNPAY sẽ gọi một request về server mình để báo kết quả, request đó gọi là IPN. Server chạy ở `localhost` thì bên VNPAY không gọi tới được. ngrok mở cho máy mình một địa chỉ `https` công khai để nhận request đó.

Mỗi người tự đăng ký một tài khoản nhé. Bản free chỉ mở được một tunnel mỗi lúc, xài chung là đá nhau.

### Đăng ký

Vào https://dashboard.ngrok.com/signup, đăng ký bằng email hoặc bấm đăng nhập Google cho nhanh. Đăng ký bằng email thì nhớ mở hộp thư bấm link kích hoạt.

### Cài

macOS:

```bash
brew install --cask ngrok
```

Linux:

```bash
sudo snap install ngrok
```

Windows thì tải zip ở https://ngrok.com/download, giải nén ra `ngrok.exe`. Bỏ nó chung thư mục với `ShopStart.java` là dùng được, lúc chạy gõ `.\ngrok.exe` thay cho `ngrok`. Ai quen thì thêm vào PATH.

Gõ `ngrok version` thấy số phiên bản là cài xong.

### Gắn authtoken

Bước này hay bị bỏ qua nhất. Thiếu authtoken thì ngrok không chạy.

Vào https://dashboard.ngrok.com/get-started/your-authtoken. Trong khung Command line có sẵn một dòng `ngrok config add-authtoken ...` đã kèm token của bạn. Bấm nút copy bên phải, dán vào terminal, Enter.

Gõ `ngrok config check` để kiểm. Ra `Valid configuration file at ...` là được.

### Chạy thử

```bash
ngrok http 8080
```

Nhìn dòng Forwarding, sẽ có một địa chỉ dạng `https://a1b2-42-115-242-109.ngrok-free.app`. Đó là địa chỉ công khai của máy bạn lúc này.

Địa chỉ đó đổi mỗi lần chạy lại ngrok. Vậy nên đừng đem nó khai vào VNPAY từ ở nhà, lên lớp chạy ngrok xong mình khai chung một lượt.

Lần đầu mở địa chỉ này bằng trình duyệt, ngrok chặn lại một trang "You are about to visit...". Bấm Visit Site là qua. IPN không bị chặn vì VNPAY gọi thẳng từ server, không qua trình duyệt.

Thử xong thì `Ctrl+C` để tắt.

## Chạy thử code

Tải `ShopStart.java` với `index.html` trên Drive về, để chung một thư mục. Mở terminal ngay thư mục đó:

```bash
java ShopStart.java
```

Vào http://localhost:8080 sẽ thấy cửa hàng. Bấm Thanh toán ra chữ "Chưa làm TODO 5" là đúng, phần đó lên lớp mới code.

Đừng double-click mở `index.html`. Trang này gọi API của server Java, mở kiểu file thì không có server nào trả lời, bấm nút sẽ không ra gì.

## Kiểm lại trước khi đi

1. Có email VNPAY chứa TmnCode và HashSecret
2. `java -version` ra 17 trở lên
3. `ngrok config check` ra `Valid configuration file`
4. `ngrok http 8080` hiện được địa chỉ `.ngrok-free.app`
5. `java ShopStart.java` chạy, vào localhost:8080 thấy cửa hàng

Qua được năm cái này là lên lớp theo kịp. Kẹt chỗ nào cứ nhắn mình trước.

# Chứng minh: Payment Service giao tiếp với VNPAY bằng gì

Các thử nghiệm chạy ngày 10/10/2026 trên VNPAY sandbox bằng tài khoản sandbox của mình. TmnCode và chữ ký được che bớt.

## 1. Tài liệu VNPAY nói gì

Nguồn: sandbox.vnpayment.vn/apis/docs, API v2.1.0, mục "Thanh toán PAY" và "Truy vấn, hoàn tiền".

| Kênh | Phương thức | Dữ liệu | Trích tài liệu |
|---|---|---|---|
| Tạo thanh toán | GET (chuyển trang) | Query string tới `paymentv2/vpcpay.html` | "URL Thanh toán là địa chỉ URL mang thông tin thanh toán." |
| ReturnURL | GET (chuyển trang) | Query string | "Không cập nhật kết quả giao dịch tại địa chỉ này." |
| IPN | GET, server to server, cần SSL | Query string, mình trả JSON `{RspCode, Message}` | "Thao tác cập nhật/xử lý kết quả sau khi thanh toán được thực hiện tại URL này." |
| querydr | POST, `Content-Type: application/json` | JSON tới `merchant_webapi/api/transaction` | Chữ ký nối các trường bằng `\|` theo thứ tự cố định |

IPN: mình trả `00` hoặc `02` thì VNPAY dừng. Trả mã khác hoặc quá thời gian thì VNPAY gọi lại, tối đa 10 lần, mỗi lần cách 5 phút.

## 2. Thử chữ ký trên link thanh toán

Tạo link bằng đúng cách của `createPayment`, mở trên sandbox, xem VNPAY trả về gì.

| Thử | VNPAY trả về | Kết luận |
|---|---|---|
| Link đúng | Trang thanh toán NCB, hiện 25.000 VND và mã đơn | Dữ liệu đi trên query string |
| Ký xong rồi sửa `vnp_Amount` | `Payment/Error.html?code=70` "Sai chữ ký" | VNPAY kiểm tra chữ ký, khách không sửa giá được |
| Ký chuỗi không sắp xếp | code=70 | Sắp xếp là bắt buộc |
| Ký chuỗi chưa encode | code=70 | Encode giá trị là bắt buộc |
| Chữ ký viết HOA | Vẫn mở trang thanh toán | Chữ thường chỉ là theo code mẫu, không bắt buộc |

Ảnh chụp: `slides-html/vnpay-pay.png`, `slides-html/vnpay-sai-chu-ky.png`.

## 3. Gọi querydr thật

Hỏi lại giao dịch thử ngày 20/9.

Request:

```
POST https://sandbox.vnpayment.vn/merchant_webapi/api/transaction
Content-Type: application/json

{"vnp_RequestId":"1806ffeea9224660", "vnp_Version":"2.1.0", "vnp_Command":"querydr",
 "vnp_TmnCode":"AUA0****", "vnp_TxnRef":"20260920125606777443",
 "vnp_OrderInfo":"Truy van GD ma:20260920125606777443",
 "vnp_TransactionDate":"20260920125606", "vnp_CreateDate":"20261010152951",
 "vnp_IpAddr":"127.0.0.1", "vnp_SecureHash":"727657287b0e316d..."}
```

Response:

```
HTTP 200  application/json; charset=utf-8

{"vnp_ResponseCode":"00", "vnp_Message":"QueryDR success", "vnp_TxnRef":"20260920125606777443",
 "vnp_Amount":"2500000", "vnp_OrderInfo":"Thanh toan C? ph? s?a ??", "vnp_BankCode":"NCB",
 "vnp_PayDate":"20260920130557", "vnp_TransactionNo":"15683165", "vnp_TransactionType":"01",
 "vnp_TransactionStatus":"00", "vnp_SecureHash":"8e524dd0d772909d..."}
```

- Chữ ký trong response tính lại theo thứ tự trong tài liệu: khớp.
- Nội dung đơn gửi đi có dấu, VNPAY trả về `C? ph? s?a ??`. Vì vậy `vnp_OrderInfo` phải viết không dấu.

## 4. Thử IPN trên máy mình

Chạy `ShopDone.java` với key giả, tự ký request IPN rồi gọi bằng GET:

| Gửi | Trả về |
|---|---|
| Chữ ký đúng, lần đầu | `{"RspCode":"00","Message":"Confirm Success"}` |
| Gửi lại y chang | `{"RspCode":"02","Message":"Order already confirmed"}` |
| Sửa số tiền, giữ chữ ký cũ | `{"RspCode":"97","Message":"Invalid Checksum"}` |
| Ký đúng nhưng mã đơn không có | `{"RspCode":"01","Message":"Order not Found"}` |
| Ký đúng nhưng số tiền lệch | `{"RspCode":"04","Message":"Invalid Amount"}` |

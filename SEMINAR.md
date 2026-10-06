# Seminar VNPAY Sandbox: thứ tự trong buổi

Slide ở `VNPAY-seminar.pptx`, 25 slide. Ghi chú mỗi slide có phần Nói, Làm và 2 câu hỏi kèm đáp án.

## Bước 1. Mở đầu (slide 1–4)

1. Slide 2: cho xem kết quả cuối buổi, đơn chuyển sang PAID
2. Slide 3: 4 request. Chỉ IPN và querydr được ghi status, ReturnURL chỉ để hiển thị
3. Slide 4: để `ShopStart.java` và `index.html` chung một thư mục, chạy `java ShopStart.java`, mở http://localhost:8080

## Bước 2. Ký và verify (slide 5–9)

1. TODO 1: điền `TMN_CODE` (8 ký tự) và `HASH_SECRET` (32 ký tự)
2. TODO 2: `hmacSHA512` trả hex lowercase, 128 ký tự
3. TODO 3: `buildQueryString`: sort alphabet, bỏ value rỗng, URL-encode value
4. TODO 4: `isValidSignature`: bỏ `vnp_SecureHash`, ký lại phần còn lại, so sánh

## Bước 3. Tạo URL thanh toán (slide 10–13)

1. TODO 5 phần 1: lưu đơn PENDING, ráp 13 tham số `vnp_*`
2. TODO 5 phần 2: ký, trả `{txnRef, paymentUrl}`
3. Kiểm tra 1: bấm Thanh toán, chuyển sang trang VNPAY
4. Quẹt thẻ NCB `9704198526191432198` / `NGUYEN VAN A` / `07/15` / OTP `123456`. Đơn PAID nhờ querydr, log chưa có IPN

## Bước 4. ngrok (slide 14–19)

1. Slide 14: khách tắt tab thì không ai gọi querydr, đơn kẹt PENDING. Cần IPN, nên cần ngrok
2. Tạo tài khoản, cài ngrok, gắn authtoken
3. `ngrok http 8080`, giữ nguyên cửa sổ đó
4. Copy URL `https` ở dòng Forwarding
5. Khai URL trả về và URL IPN trong merchant portal
6. Set `VNPAY_RETURN_URL` (macOS: `export`, Windows: `$env:`) rồi chạy lại Java

## Bước 5. IPN (slide 20–21)

1. TODO 6: `handleIpn` trả `RspCode`:

   | RspCode | Khi nào |
   |---|---|
   | `97` | Sai chữ ký |
   | `01` | Không có đơn |
   | `04` | Sai số tiền |
   | `02` | Đơn đã xử lý |
   | `00` | Ghi nhận xong |

2. Kiểm tra 2: thanh toán lại, log có dòng IPN, đơn PAID

## Bước 6. Kết thúc (slide 22–25)

1. Slide 22: hàm `reconcile` gọi querydr, ký nối bằng `|`, không sort
2. Slide 23: lỗi thường gặp
3. Slide 24: mấy điều nên nhớ khi tích hợp VNPAY, cách chuyển sang Spring Boot
4. Slide 25: hỏi đáp, gửi `ShopDone.java` cho lớp

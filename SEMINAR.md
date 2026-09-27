# Seminar VNPAY Sandbox: thứ tự trong buổi

Slide ở `VNPAY-seminar.pptx`, câu hỏi và đáp án cho từng slide nằm trong phần ghi chú.

## Bước 1. Mở đầu (slide 1–4)

1. Slide 1: cho xem kết quả cuối buổi, đơn chuyển sang PAID
2. Slide 2: cả lớp để `ShopStart.java` và `index.html` chung một thư mục
3. Slide 3: chạy `java ShopStart.java`, mở http://localhost:8080
4. Slide 4: 4 luồng. Chỉ IPN và querydr được ghi status, ReturnURL chỉ để hiển thị

## Bước 2. Ký và verify (slide 5–9)

1. TODO 1: điền `TMN_CODE` (8 ký tự) và `HASH_SECRET` (32 ký tự)
2. TODO 2: `hmacSHA512` trả hex lowercase, 128 ký tự
3. TODO 3: `buildQueryString`: sort alphabet, bỏ value rỗng, URL-encode value
4. TODO 4: `isValidSignature`: bỏ `vnp_SecureHash`, ký lại phần còn lại, so sánh

## Bước 3. Tạo URL thanh toán (slide 10–13)

1. TODO 5: lưu đơn PENDING, ráp 13 tham số `vnp_*`, ký, trả `{txnRef, paymentUrl}`
2. Checkpoint 1: bấm Thanh toán, chuyển sang trang VNPAY
3. Quẹt thẻ NCB `9704198526191432198` / `NGUYEN VAN A` / `07/15` / OTP `123456`. Đơn vẫn PENDING vì chưa có IPN

## Bước 4. ngrok (slide 14–19)

1. Tạo tài khoản, cài ngrok, gắn authtoken
2. `ngrok http 8080`, giữ nguyên cửa sổ đó
3. Copy URL `https` ở dòng Forwarding
4. Khai URL trả về và URL IPN trong merchant portal
5. `export VNPAY_RETURN_URL=...` rồi chạy lại Java

## Bước 5. IPN (slide 20–21)

1. TODO 6: `handleIpn` trả `RspCode`:

   | RspCode | Khi nào |
   |---|---|
   | `97` | Sai chữ ký |
   | `01` | Không có đơn |
   | `04` | Sai số tiền |
   | `02` | Đơn đã xử lý |
   | `00` | Ghi nhận xong |

2. Checkpoint 2: thanh toán lại, log có IPN, đơn chuyển PAID

## Bước 6. Kết thúc (slide 22–24)

1. Slide 22: IPN không về thì chốt đơn bằng `querydr`
2. Slide 23: lỗi thường gặp
3. Slide 24: sáu điều mang về đồ án, gửi `ShopDone.java` cho lớp

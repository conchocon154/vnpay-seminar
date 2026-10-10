# Seminar VNPAY trong kiến trúc microservice: thứ tự trong buổi

Slide ở `VNPAY-seminar.pptx`, 29 slide. Ghi chú mỗi slide có phần Nói, Làm, các bước gõ code và câu hỏi kèm đáp án. Bằng chứng cho từng ý nằm ở `CHUNG-MINH.md`.

## Phần 1. Bức tranh chung và chứng minh (slide 1–9)

1. Slide 2: hôm nay làm gì, hai nhãn "VNPAY yêu cầu" và "Mình chọn"
2. Slide 3: Payment Service là chỗ duy nhất nói chuyện với VNPAY, phát event `OrderPaid`
3. Slide 4: VNPAY là dịch vụ bên ngoài, hợp đồng API do VNPAY đặt
4. Slide 5–6: 4 kênh giao tiếp: redirect GET, ReturnURL GET, IPN GET (webhook), querydr POST JSON
5. Slide 7: vì sao redirect + query string, ảnh trang thanh toán thật
6. Slide 8: vì sao IPN là kênh chính, trích tài liệu, VNPAY gọi lại 10 lần
7. Slide 9: thử chữ ký trên sandbox: sửa tiền, không sắp xếp, không encode đều bị từ chối

## Phần 2. Code Payment Service (slide 10–18)

1. Slide 10: chạy code ban đầu
2. TODO 1–4 (slide 11–14): key, `hmacSHA512`, `buildQueryString`, `isValidSignature`
3. TODO 5 (slide 15–16): `createPayment`, 13 tham số, trả `{txnRef, paymentUrl}`
4. Slide 17–18: test lần 1, thanh toán thẻ test, đơn PAID nhờ querydr

## Phần 3. Mở endpoint ra internet (slide 19–21)

1. IPN cần URL HTTPS public, dev dùng ngrok, thật dùng API Gateway hoặc Ingress
2. `ngrok http 8080`, khai URL trả về và URL IPN, set `VNPAY_RETURN_URL` rồi chạy lại Java

## Phần 4. IPN và querydr (slide 22–24)

1. TODO 6: `handleIpn` trả 97, 01, 04, 02, 00
2. Test lần 2: log có dòng IPN
3. querydr: request và response thật gọi lên sandbox

## Phần 5. Nhìn lại theo microservice (slide 25–29)

1. Các pattern: gateway cho dịch vụ ngoài, webhook, retry + idempotency, eventual consistency, reconciliation, message authentication
2. Từ demo lên hệ thống thật: DB, transaction, outbox, job định kỳ, API Gateway, Secret Manager
3. Lỗi hay gặp, tóm tắt, hỏi đáp, gửi `ShopDone.java` cho lớp

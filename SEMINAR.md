# Seminar VNPAY trong kiến trúc microservice: thứ tự trong buổi

Slide ở `VNPAY-seminar.pptx`, 32 slide. Buổi này không code cùng: lớp nhận trước `PaymentService.java` hoàn chỉnh, bài nói giải thích vì sao. Ghi chú mỗi slide có phần Nói và câu hỏi kèm đáp án. Bằng chứng nằm ở `CHUNG-MINH.md`.

## Phần 1. Bức tranh chung (slide 1–6)

1. Nội dung buổi, hai nhãn "VNPAY yêu cầu" và "Mình chọn"
2. Payment Service là service duy nhất giao tiếp với VNPAY
3. VNPAY là dịch vụ bên ngoài, hợp đồng do VNPAY quy định
4. Bốn kênh: link thanh toán GET, ReturnURL GET, IPN GET, querydr POST JSON

## Phần 2. Tham số và định dạng dữ liệu (slide 7–14)

1. 13 tham số bắt buộc, vì sao từng tham số bắt buộc
2. Định dạng ngày giờ, số tiền, mã, văn bản, chữ ký
3. Vì sao dùng query string, vì sao phải encode
4. Vì sao ký theo cách này, các thử nghiệm trên sandbox

## Phần 3. IPN và querydr (slide 15–17)

1. IPN: VNPAY gửi gì, trả gì, mã trả về và retry
2. querydr: vì sao POST JSON, request và response thật

## Phần 4. Code và lý do (slide 18–26)

1. Cấu trúc file, cấu hình, ba hàm chữ ký
2. createPayment, handleIpn, ReturnURL, reconcile

## Phần 5. Demo và microservice (slide 27–32)

1. ngrok, chạy demo, xem request IPN ở 127.0.0.1:4040
2. Các pattern microservice, demo so với hệ thống thật, tóm tắt, hỏi đáp

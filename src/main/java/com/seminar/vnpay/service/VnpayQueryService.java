package com.seminar.vnpay.service;

import com.seminar.vnpay.config.VnpayProperties;
import com.seminar.vnpay.util.VnpayUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gọi API querydr để hỏi VNPAY trạng thái thật của một giao dịch.
 * Dùng khi IPN không về, khi khách đóng trình duyệt giữa chừng, hoặc cho job đối soát cuối ngày.
 */
@Service
public class VnpayQueryService {

    private final VnpayProperties props;
    private final RestClient restClient = RestClient.create();

    public VnpayQueryService(VnpayProperties props) {
        this.props = props;
    }

    /**
     * @param txnRef          mã đơn đã gửi sang VNPAY
     * @param transactionDate thời điểm tạo giao dịch, dạng yyyyMMddHHmmss
     */
    public Map<String, Object> queryTransaction(String txnRef, String transactionDate, String clientIp) {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String version = "2.1.0";
        String command = "querydr";
        String orderInfo = "Truy van GD ma:" + txnRef;
        String createDate = LocalDateTime.now(PaymentService.VN_ZONE).format(PaymentService.VNP_TIME);

        // querydr không sort alphabet. Hash là chuỗi nối bằng '|' đúng thứ tự dưới đây.
        String hashData = String.join("|",
                requestId, version, command, props.getTmnCode(),
                txnRef, transactionDate, createDate, clientIp, orderInfo);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", version);
        body.put("vnp_Command", command);
        body.put("vnp_TmnCode", props.getTmnCode());
        body.put("vnp_TxnRef", txnRef);
        body.put("vnp_OrderInfo", orderInfo);
        body.put("vnp_TransactionDate", transactionDate);
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", clientIp);
        body.put("vnp_SecureHash", VnpayUtils.hmacSHA512(props.getHashSecret(), hashData));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(props.getApiUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        Map<String, Object> result = new LinkedHashMap<>(response == null ? Map.of() : response);
        result.put("signatureValid", isResponseSignatureValid(result));
        return result;
    }

    /** Response cũng có chữ ký, verify xong mới được tin kết quả. */
    private boolean isResponseSignatureValid(Map<String, Object> r) {
        String received = str(r.get("vnp_SecureHash"));
        if (received.isEmpty()) {
            return false;
        }
        String hashData = String.join("|",
                str(r.get("vnp_ResponseId")), str(r.get("vnp_Command")), str(r.get("vnp_ResponseCode")),
                str(r.get("vnp_Message")), str(r.get("vnp_TmnCode")), str(r.get("vnp_TxnRef")),
                str(r.get("vnp_Amount")), str(r.get("vnp_BankCode")), str(r.get("vnp_PayDate")),
                str(r.get("vnp_TransactionNo")), str(r.get("vnp_TransactionType")),
                str(r.get("vnp_TransactionStatus")), str(r.get("vnp_OrderInfo")),
                str(r.get("vnp_PromotionCode")), str(r.get("vnp_PromotionAmount")));
        return VnpayUtils.constantTimeEquals(
                VnpayUtils.hmacSHA512(props.getHashSecret(), hashData), received);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}

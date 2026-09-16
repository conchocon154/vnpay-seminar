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
 * Doi soat server-to-server (API querydr).
 * Dung khi: IPN khong ve, user dong trinh duyet, hoac job doi soat cuoi ngay.
 */
@Service
public class VnpayQueryService {

    private final VnpayProperties props;
    private final RestClient restClient = RestClient.create();

    public VnpayQueryService(VnpayProperties props) {
        this.props = props;
    }

    /**
     * @param txnRef          ma don hang da gui sang VNPAY
     * @param transactionDate thoi diem tao giao dich, dinh dang yyyyMMddHHmmss
     */
    public Map<String, Object> queryTransaction(String txnRef, String transactionDate, String clientIp) {
        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String version = "2.1.0";
        String command = "querydr";
        String orderInfo = "Truy van GD ma:" + txnRef;
        String createDate = LocalDateTime.now(PaymentService.VN_ZONE).format(PaymentService.VNP_TIME);

        // API querydr KHONG sap xep alphabet - hash la chuoi noi bang '|' theo dung thu tu nay.
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

    /** Response cung co chu ky - phai verify truoc khi tin ket qua. */
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

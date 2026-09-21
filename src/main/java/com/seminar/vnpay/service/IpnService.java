package com.seminar.vnpay.service;

import com.seminar.vnpay.config.VnpayProperties;
import com.seminar.vnpay.domain.Order;
import com.seminar.vnpay.domain.OrderStore;
import com.seminar.vnpay.util.VnpayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Xử lý IPN, cuộc gọi từ server VNPAY sang server mình.
 * Chỉ ở đây mới được cộng tiền hay giao hàng.
 * VNPAY retry tới khi nhận được RspCode 00, nên hàm này gọi mấy lần cũng chỉ ghi nhận một lần.
 */
@Service
public class IpnService {

    private static final Logger log = LoggerFactory.getLogger(IpnService.class);

    private final VnpayProperties props;
    private final OrderStore orderStore;

    public IpnService(VnpayProperties props, OrderStore orderStore) {
        this.props = props;
        this.orderStore = orderStore;
    }

    public Map<String, String> handle(Map<String, String> params) {
        try {
            // Chữ ký sai
            if (!VnpayUtils.isValidSignature(params, props.getHashSecret())) {
                return respond("97", "Invalid Checksum");
            }

            // Không tìm thấy đơn
            String txnRef = params.get("vnp_TxnRef");
            Optional<Order> found = orderStore.findByTxnRef(txnRef);
            if (found.isEmpty()) {
                return respond("01", "Order not Found");
            }
            Order order = found.get();

            // Số tiền không khớp, chặn trò sửa amount trên URL
            long amountFromVnpay = Long.parseLong(params.get("vnp_Amount"));
            if (amountFromVnpay != order.getAmount() * 100) {
                return respond("04", "Invalid Amount");
            }

            synchronized (order) {
                // Đã xử lý rồi thì thôi, không cộng tiền lần hai
                if (order.getStatus() != Order.Status.PENDING) {
                    return respond("02", "Order already confirmed");
                }

                String responseCode = params.get("vnp_ResponseCode");
                String transactionStatus = params.get("vnp_TransactionStatus");
                boolean success = "00".equals(responseCode) && "00".equals(transactionStatus);

                order.setStatus(success ? Order.Status.PAID : Order.Status.FAILED);
                order.setResponseCode(responseCode);
                order.setTransactionNo(params.get("vnp_TransactionNo"));
                order.setBankCode(params.get("vnp_BankCode"));
                order.setPayDate(params.get("vnp_PayDate"));
                orderStore.save(order);

                log.info("IPN txnRef={} status={} vnpTransactionNo={}",
                        txnRef, order.getStatus(), order.getTransactionNo());
                // Chỗ này bỏ vào outbox rồi phát event OrderPaid cho service khác.
            }

            // Ghi nhận xong, trả 00 để VNPAY ngừng retry
            return respond("00", "Confirm Success");

        } catch (Exception e) {
            log.error("Loi xu ly IPN", e);
            return respond("99", "Unknown error");
        }
    }

    private Map<String, String> respond(String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("RspCode", code);
        body.put("Message", message);
        return body;
    }
}

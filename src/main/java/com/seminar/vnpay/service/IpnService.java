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
 * Xu ly IPN (Instant Payment Notification) - server-to-server.
 * DAY la nguon su that duy nhat de cong tien / giao hang.
 * VNPAY retry cho den khi nhan duoc RspCode=00 => handler BAT BUOC idempotent.
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
            // B1: chu ky sai -> 97
            if (!VnpayUtils.isValidSignature(params, props.getHashSecret())) {
                return respond("97", "Invalid Checksum");
            }

            // B2: khong tim thay don -> 01
            String txnRef = params.get("vnp_TxnRef");
            Optional<Order> found = orderStore.findByTxnRef(txnRef);
            if (found.isEmpty()) {
                return respond("01", "Order not Found");
            }
            Order order = found.get();

            // B3: sai so tien -> 04 (chong sua amount tren URL)
            long amountFromVnpay = Long.parseLong(params.get("vnp_Amount"));
            if (amountFromVnpay != order.getAmount() * 100) {
                return respond("04", "Invalid Amount");
            }

            synchronized (order) {
                // B4: da xu ly roi -> 02 (idempotent, KHONG cong tien lan 2)
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
                // Cho vao outbox de publish event OrderPaid cho service khac (neu co).
            }

            // B5: da ghi nhan xong -> 00. VNPAY se ngung retry.
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

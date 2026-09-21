package com.seminar.vnpay.service;

import com.seminar.vnpay.domain.Order;
import com.seminar.vnpay.domain.OrderStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Chốt trạng thái đơn bằng API querydr thay vì ngồi chờ IPN.
 *
 * Dùng khi chưa khai được IPN URL trong merchant portal, khi IPN thất lạc,
 * hoặc cho job đối soát cuối ngày. Vẫn an toàn như IPN vì hỏi thẳng VNPAY,
 * verify chữ ký của response, rồi so lại số tiền. Tham số trên URL trình duyệt thì không tin.
 */
@Service
public class ReconcileService {

    private static final Logger log = LoggerFactory.getLogger(ReconcileService.class);

    private final VnpayQueryService queryService;
    private final OrderStore orderStore;

    public ReconcileService(VnpayQueryService queryService, OrderStore orderStore) {
        this.queryService = queryService;
        this.orderStore = orderStore;
    }

    /** @param transactionDate dạng yyyyMMddHHmmss, lấy từ vnp_PayDate. Thiếu thì dùng giờ tạo đơn. */
    public Optional<Order> confirm(String txnRef, String transactionDate, String clientIp) {
        Optional<Order> found = orderStore.findByTxnRef(txnRef);
        if (found.isEmpty()) {
            return found;
        }
        Order order = found.get();
        if (order.getStatus() != Order.Status.PENDING) {
            return found;   // chốt rồi thì thôi
        }

        String date = (transactionDate == null || transactionDate.isBlank())
                ? LocalDateTime.ofInstant(order.getCreatedAt(), PaymentService.VN_ZONE).format(PaymentService.VNP_TIME)
                : transactionDate;

        Map<String, Object> r;
        try {
            r = queryService.queryTransaction(txnRef, date, clientIp);
        } catch (Exception e) {
            log.warn("querydr that bai cho txnRef={}: {}", txnRef, e.toString());
            return found;
        }

        if (!Boolean.TRUE.equals(r.get("signatureValid"))) {
            log.error("Chu ky response querydr KHONG hop le cho txnRef={} -> bo qua", txnRef);
            return found;
        }

        String responseCode = str(r.get("vnp_ResponseCode"));
        String transactionStatus = str(r.get("vnp_TransactionStatus"));

        synchronized (order) {
            if (order.getStatus() != Order.Status.PENDING) {
                return found;
            }
            // vnp_ResponseCode nói truy vấn có thành công không, còn kết quả giao dịch nằm ở vnp_TransactionStatus.
            if (!"00".equals(responseCode)) {
                log.info("querydr txnRef={} chua co ket qua, vnp_ResponseCode={}", txnRef, responseCode);
                return found;
            }
            if (!amountMatches(r.get("vnp_Amount"), order)) {
                log.error("So tien querydr khong khop don hang txnRef={} -> khong chot", txnRef);
                return found;
            }

            boolean paid = "00".equals(transactionStatus);
            order.setStatus(paid ? Order.Status.PAID : Order.Status.FAILED);
            order.setResponseCode(transactionStatus);
            order.setTransactionNo(str(r.get("vnp_TransactionNo")));
            order.setBankCode(str(r.get("vnp_BankCode")));
            order.setPayDate(str(r.get("vnp_PayDate")));
            orderStore.save(order);
            log.info("Chot bang querydr: txnRef={} status={} transactionNo={}",
                    txnRef, order.getStatus(), order.getTransactionNo());
        }
        return found;
    }

    private boolean amountMatches(Object amount, Order order) {
        try {
            return Long.parseLong(str(amount)) == order.getAmount() * 100;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}

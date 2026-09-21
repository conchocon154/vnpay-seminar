package com.seminar.vnpay.web;

import com.seminar.vnpay.domain.Order;
import com.seminar.vnpay.domain.OrderStore;
import com.seminar.vnpay.service.PaymentService;
import com.seminar.vnpay.service.VnpayQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService paymentService;
    private final VnpayQueryService queryService;
    private final OrderStore orderStore;

    public PaymentController(PaymentService paymentService, VnpayQueryService queryService, OrderStore orderStore) {
        this.paymentService = paymentService;
        this.queryService = queryService;
        this.orderStore = orderStore;
    }

    public record CreatePaymentRequest(long amount, String orderInfo, String bankCode) {}

    /** Frontend gọi API này, nhận paymentUrl rồi tự chuyển trang sang VNPAY. */
    @PostMapping("/payments")
    public Map<String, String> create(@RequestBody CreatePaymentRequest req, HttpServletRequest http) {
        String info = (req.orderInfo() == null || req.orderInfo().isBlank())
                ? "Thanh toan don hang" : req.orderInfo();
        return paymentService.createPayment(req.amount(), info, req.bankCode(), clientIp(http));
    }

    /** Frontend hỏi trạng thái đơn trong lúc chờ VNPAY xác nhận. */
    @GetMapping("/orders/{txnRef}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String txnRef) {
        return orderStore.findByTxnRef(txnRef)
                .map(PaymentController::toJson)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Hỏi thẳng VNPAY trạng thái thật của giao dịch. */
    @PostMapping("/orders/{txnRef}/verify")
    public Map<String, Object> verify(@PathVariable String txnRef,
                                      @RequestParam String transactionDate,
                                      HttpServletRequest http) {
        return queryService.queryTransaction(txnRef, transactionDate, clientIp(http));
    }

    private static Map<String, Object> toJson(Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("txnRef", o.getTxnRef());
        m.put("amount", o.getAmount());
        m.put("orderInfo", o.getOrderInfo());
        m.put("status", o.getStatus());
        m.put("responseCode", o.getResponseCode());
        m.put("transactionNo", o.getTransactionNo());
        m.put("bankCode", o.getBankCode());
        m.put("payDate", o.getPayDate());
        return m;
    }

    /** VNPAY cần IPv4. Chạy sau nginx hay gateway thì phải đọc X-Forwarded-For. */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        return "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip) ? "127.0.0.1" : ip;
    }
}

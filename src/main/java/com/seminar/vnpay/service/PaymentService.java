package com.seminar.vnpay.service;

import com.seminar.vnpay.config.VnpayProperties;
import com.seminar.vnpay.domain.Order;
import com.seminar.vnpay.domain.OrderStore;
import com.seminar.vnpay.util.VnpayUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {

    /** VNPAY tính giờ GMT+7, đừng lấy giờ máy chủ. */
    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnpayProperties props;
    private final OrderStore orderStore;

    public PaymentService(VnpayProperties props, OrderStore orderStore) {
        this.props = props;
        this.orderStore = orderStore;
    }

    /**
     * Lưu đơn ở trạng thái PENDING rồi trả về URL để trình duyệt chuyển sang VNPAY.
     * @param amount số tiền VND, ví dụ 50000. Việc nhân 100 làm ở trong hàm.
     */
    public Map<String, String> createPayment(long amount, String orderInfo, String bankCode, String clientIp) {
        if (amount < 5_000 || amount >= 1_000_000_000L) {
            throw new IllegalArgumentException("So tien phai tu 5.000 den duoi 1 ty VND");
        }

        String txnRef = newTxnRef();
        orderStore.save(new Order(txnRef, amount, orderInfo));

        LocalDateTime now = LocalDateTime.now(VN_ZONE);

        Map<String, String> p = new HashMap<>();
        p.put("vnp_Version", "2.1.0");
        p.put("vnp_Command", "pay");
        p.put("vnp_TmnCode", props.getTmnCode());
        p.put("vnp_Amount", String.valueOf(amount * 100));   // VNPAY tính theo đơn vị nhân 100
        p.put("vnp_CurrCode", "VND");
        p.put("vnp_TxnRef", txnRef);
        p.put("vnp_OrderInfo", orderInfo);
        p.put("vnp_OrderType", "other");
        p.put("vnp_Locale", "vn");
        p.put("vnp_ReturnUrl", props.getReturnUrl());
        p.put("vnp_IpAddr", clientIp);
        p.put("vnp_CreateDate", now.format(VNP_TIME));
        p.put("vnp_ExpireDate", now.plusMinutes(props.getExpireMinutes()).format(VNP_TIME));
        if (bankCode != null && !bankCode.isBlank()) {
            p.put("vnp_BankCode", bankCode);   // bỏ trống thì VNPAY hiện trang chọn ngân hàng
        }

        String url = props.getPayUrl() + "?" + VnpayUtils.signAndBuildQuery(p, props.getHashSecret());
        return Map.of("txnRef", txnRef, "paymentUrl", url);
    }

    /** Mã đơn phải duy nhất trong 24h theo TmnCode. Thực tế nên ghép orderId với số lần thanh toán lại. */
    private String newTxnRef() {
        return LocalDateTime.now(VN_ZONE).format(VNP_TIME)
                + ThreadLocalRandom.current().nextInt(100_000, 999_999);
    }
}

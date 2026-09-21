package com.seminar.vnpay.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ký và verify HMAC-SHA512. Phần khó của VNPAY nằm gọn trong file này.
 * Không phụ thuộc Spring nên copy nguyên si sang project khác là chạy.
 */
public final class VnpayUtils {

    private VnpayUtils() {
    }

    /** Băm data bằng secretKey, trả về chuỗi hex chữ thường. */
    public static String hmacSHA512(String secretKey, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Khong tao duoc HMAC-SHA512", e);
        }
    }

    /**
     * Sắp tham số theo alphabet, URL-encode phần giá trị, rồi nối bằng '&'.
     * Chuỗi trả về vừa làm hashData vừa làm query string nên hai bên không lệch nhau.
     * Tham số rỗng bị loại, vì VNPAY cũng không tính chúng vào chữ ký.
     */
    public static String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : new TreeMap<>(params).entrySet()) {
            String value = e.getValue();
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII))
              .append('=')
              .append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
        }
        return sb.toString();
    }

    /** Ký rồi trả về query string đã kèm sẵn vnp_SecureHash. */
    public static String signAndBuildQuery(Map<String, String> params, String secretKey) {
        String query = buildQueryString(params);
        return query + "&vnp_SecureHash=" + hmacSHA512(secretKey, query);
    }

    /**
     * Verify chữ ký nhận được ở ReturnURL hoặc IPN.
     * params là toàn bộ query param đọc ra, servlet đã URL-decode sẵn.
     */
    public static boolean isValidSignature(Map<String, String> params, String secretKey) {
        String received = params.get("vnp_SecureHash");
        if (received == null || received.isBlank()) {
            return false;
        }
        Map<String, String> clone = new TreeMap<>(params);
        clone.remove("vnp_SecureHash");
        clone.remove("vnp_SecureHashType");
        String expected = hmacSHA512(secretKey, buildQueryString(clone));
        return constantTimeEquals(expected, received);
    }

    /** So sánh không phụ thuộc thời gian, chống timing attack. */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= Character.toLowerCase(a.charAt(i)) ^ Character.toLowerCase(b.charAt(i));
        }
        return diff == 0;
    }
}

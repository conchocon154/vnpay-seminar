package com.seminar.vnpay.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Toan bo "phan kho" cua VNPAY nam o day: ky va verify HMAC-SHA512.
 * Class nay khong phu thuoc Spring -> copy nguyen si sang project khac duoc.
 */
public final class VnpayUtils {

    private VnpayUtils() {
    }

    /** HMAC-SHA512(secretKey, data) -> chuoi hex thuong. */
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
     * Sap xep tham so theo alphabet, URL-encode GIA TRI, noi bang '&'.
     * Chuoi tra ve vua dung lam hashData, vua dung lam query string.
     * LUU Y: tham so rong/null bi loai bo - VNPAY cung khong tinh chung vao chu ky.
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

    /** Ky tham so va tra ve query string da kem vnp_SecureHash. */
    public static String signAndBuildQuery(Map<String, String> params, String secretKey) {
        String query = buildQueryString(params);
        return query + "&vnp_SecureHash=" + hmacSHA512(secretKey, query);
    }

    /**
     * Verify chu ky tren ReturnURL / IPN.
     * params = toan bo query params nhan duoc (servlet da URL-decode san).
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

    /** So sanh khong phu thuoc thoi gian -> chong timing attack. */
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

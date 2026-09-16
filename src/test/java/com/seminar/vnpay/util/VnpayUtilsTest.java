package com.seminar.vnpay.util;

import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VnpayUtilsTest {

    private static final String SECRET = "SANDBOXSECRETKEY1234567890ABCDEF";

    @Test
    void hmacSHA512_khop_voi_test_vector_chuan() {
        assertEquals(
                "b42af09057bac1e2d41708e48a902e09b5ff7f12ab428a4fe86653c73dd248fb"
                        + "82f948a549f7b791a5b41915ee4d1ec3935357e4e2317250d0372afa2ebeeb3a",
                VnpayUtils.hmacSHA512("key", "The quick brown fox jumps over the lazy dog"));
    }

    @Test
    void query_duoc_sap_xep_alphabet_va_bo_gia_tri_rong() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("vnp_Version", "2.1.0");
        p.put("vnp_Amount", "5000000");
        p.put("vnp_BankCode", "");          // bi loai bo
        p.put("vnp_Command", "pay");
        assertEquals("vnp_Amount=5000000&vnp_Command=pay&vnp_Version=2.1.0",
                VnpayUtils.buildQueryString(p));
    }

    @Test
    void gia_tri_co_dau_cach_duoc_url_encode() {
        Map<String, String> p = Map.of("vnp_OrderInfo", "Thanh toan don hang 1+2");
        assertEquals("vnp_OrderInfo=Thanh+toan+don+hang+1%2B2", VnpayUtils.buildQueryString(p));
    }

    /** Mo phong dung luong di: ky -> gui URL -> servlet decode -> verify. */
    @Test
    void ky_roi_verify_lai_thi_hop_le() {
        Map<String, String> p = new HashMap<>();
        p.put("vnp_Version", "2.1.0");
        p.put("vnp_Command", "pay");
        p.put("vnp_TmnCode", "DEMO0001");
        p.put("vnp_Amount", "5000000");
        p.put("vnp_TxnRef", "20260916123456");
        p.put("vnp_OrderInfo", "Thanh toan don hang DEMO");
        p.put("vnp_ResponseCode", "00");

        String query = VnpayUtils.signAndBuildQuery(p, SECRET);
        assertTrue(VnpayUtils.isValidSignature(decodeQuery(query), SECRET));
    }

    @Test
    void sua_so_tien_tren_URL_thi_chu_ky_sai() {
        Map<String, String> p = new HashMap<>();
        p.put("vnp_TxnRef", "20260916123456");
        p.put("vnp_Amount", "5000000");
        Map<String, String> received = decodeQuery(VnpayUtils.signAndBuildQuery(p, SECRET));

        received.put("vnp_Amount", "100");   // hacker sua tay
        assertFalse(VnpayUtils.isValidSignature(received, SECRET));
    }

    @Test
    void thieu_chu_ky_thi_khong_hop_le() {
        assertFalse(VnpayUtils.isValidSignature(Map.of("vnp_TxnRef", "abc"), SECRET));
    }

    /** Servlet container URL-decode san param, test nay mo phong dung hanh vi do. */
    private static Map<String, String> decodeQuery(String query) {
        Map<String, String> map = new HashMap<>();
        for (String pair : query.split("&")) {
            int i = pair.indexOf('=');
            map.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
        }
        return map;
    }
}

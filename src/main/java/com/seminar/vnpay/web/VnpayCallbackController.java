package com.seminar.vnpay.web;

import com.seminar.vnpay.config.VnpayProperties;
import com.seminar.vnpay.service.IpnService;
import com.seminar.vnpay.util.VnpayUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class VnpayCallbackController {

    private final VnpayProperties props;
    private final IpnService ipnService;

    public VnpayCallbackController(VnpayProperties props, IpnService ipnService) {
        this.props = props;
        this.ipnService = ipnService;
    }

    /**
     * ReturnURL: trinh duyet cua user quay ve. CHI DE HIEN THI.
     * Tuyet doi khong cong tien / giao hang o day - user co the sua URL.
     */
    @GetMapping("/vnpay/return")
    public Map<String, Object> returnUrl(@RequestParam Map<String, String> params) {
        boolean validSignature = VnpayUtils.isValidSignature(params, props.getHashSecret());
        boolean paid = validSignature
                && "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("signatureValid", validSignature);
        view.put("displayStatus", !validSignature ? "INVALID_SIGNATURE" : (paid ? "SUCCESS" : "FAILED"));
        view.put("txnRef", params.get("vnp_TxnRef"));
        view.put("responseCode", params.get("vnp_ResponseCode"));
        view.put("message", "Trang thai chinh thuc lay tu GET /api/orders/{txnRef} (cap nhat boi IPN)");
        return view;
    }

    /**
     * IPN URL: VNPAY goi server-to-server. Phai public tren internet (dung ngrok khi dev).
     * Bat buoc tra JSON {"RspCode":"..","Message":".."} va HTTP 200.
     */
    @GetMapping(value = "/vnpay/ipn", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> ipn(@RequestParam Map<String, String> params) {
        return ipnService.handle(params);
    }
}

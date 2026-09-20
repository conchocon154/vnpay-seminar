package com.seminar.vnpay.web;

import com.seminar.vnpay.config.VnpayProperties;
import com.seminar.vnpay.service.IpnService;
import com.seminar.vnpay.util.VnpayUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
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
     * Verify chu ky xong thi redirect sang trang tinh result.html.
     */
    @GetMapping("/vnpay/return")
    public ResponseEntity<Void> returnUrl(@RequestParam Map<String, String> params) {
        boolean validSignature = VnpayUtils.isValidSignature(params, props.getHashSecret());

        URI target = UriComponentsBuilder.fromPath("/result.html")
                .queryParam("txnRef", params.getOrDefault("vnp_TxnRef", ""))
                .queryParam("responseCode", params.getOrDefault("vnp_ResponseCode", ""))
                .queryParam("valid", validSignature)
                .build()
                .encode()
                .toUri();

        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
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

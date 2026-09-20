package com.seminar.vnpay.web;

import com.seminar.vnpay.config.VnpayProperties;
import com.seminar.vnpay.service.IpnService;
import com.seminar.vnpay.service.ReconcileService;
import com.seminar.vnpay.util.VnpayUtils;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ReconcileService reconcileService;

    public VnpayCallbackController(VnpayProperties props, IpnService ipnService,
                                   ReconcileService reconcileService) {
        this.props = props;
        this.ipnService = ipnService;
        this.reconcileService = reconcileService;
    }

    /**
     * ReturnURL: trinh duyet cua user quay ve. CHI DE HIEN THI.
     * Tuyet doi khong cong tien / giao hang o day - user co the sua URL.
     * Verify chu ky xong thi redirect sang trang tinh result.html.
     */
    @GetMapping("/vnpay/return")
    public ResponseEntity<Void> returnUrl(@RequestParam Map<String, String> params,
                                          HttpServletRequest http) {
        boolean validSignature = VnpayUtils.isValidSignature(params, props.getHashSecret());

        // Du phong khi IPN chua khai duoc: hoi thang VNPAY qua querydr de chot trang thai.
        // Van khong tin tham so tren URL - chi dung txnRef lam khoa tra cuu.
        if (validSignature) {
            reconcileService.confirm(params.get("vnp_TxnRef"), params.get("vnp_PayDate"),
                    PaymentController.clientIp(http));
        }

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

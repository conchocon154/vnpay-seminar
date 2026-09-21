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
     * ReturnURL, nơi trình duyệt của khách quay về. Chỉ dùng để hiển thị.
     * Đừng cộng tiền hay giao hàng ở đây, vì khách sửa URL được.
     * Verify chữ ký xong thì chuyển sang trang result.html.
     */
    @GetMapping("/vnpay/return")
    public ResponseEntity<Void> returnUrl(@RequestParam Map<String, String> params,
                                          HttpServletRequest http) {
        boolean validSignature = VnpayUtils.isValidSignature(params, props.getHashSecret());

        // Phòng khi chưa khai được IPN, hỏi thẳng VNPAY qua querydr để chốt trạng thái.
        // Vẫn không tin tham số trên URL, chỉ mượn txnRef làm khoá tra cứu.
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
     * IPN, VNPAY gọi thẳng vào đây nên địa chỉ phải công khai trên internet. Khi dev thì dùng ngrok.
     * Phải trả JSON {"RspCode":"..","Message":".."} kèm HTTP 200.
     */
    @GetMapping(value = "/vnpay/ipn", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> ipn(@RequestParam Map<String, String> params) {
        return ipnService.handle(params);
    }
}

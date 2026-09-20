package com.seminar.vnpay.web;

import com.seminar.vnpay.config.VnpayProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trang thiet lap cho moi truong DEV: dan TmnCode/HashSecret thang tren web thay vi sua .env.
 *
 * CANH BAO: endpoint nay ghi de credential luc dang chay -> CHI DUNG CHO SANDBOX.
 * Truoc khi len production phai xoa class nay va nap secret tu Vault/K8s Secret.
 * Da chan san: chi nhan request goi truc tiep vao localhost, khong nhan qua tunnel/proxy.
 */
@RestController
@RequestMapping("/api/config")
public class SetupController {

    private static final Logger log = LoggerFactory.getLogger(SetupController.class);

    private final VnpayProperties props;

    public SetupController(VnpayProperties props) {
        this.props = props;
    }

    public record ConfigRequest(String tmnCode, String hashSecret, String returnUrl) {}

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", isSet(props.getTmnCode()) && isSet(props.getHashSecret()));
        m.put("tmnCode", isSet(props.getTmnCode()) ? props.getTmnCode() : "");
        m.put("hashSecretMasked", isSet(props.getHashSecret()) ? mask(props.getHashSecret()) : "");
        m.put("returnUrl", props.getReturnUrl());
        return m;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> save(@RequestBody ConfigRequest req, HttpServletRequest http) {
        if (!isLocalRequest(http)) {
            log.warn("Tu choi /api/config tu ngoai localhost: host={}", http.getHeader("Host"));
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Chi cau hinh duoc khi mo truc tiep http://localhost:8080/setup.html"));
        }
        if (!isSet(req.tmnCode()) || !isSet(req.hashSecret())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Thieu TmnCode hoac HashSecret"));
        }

        props.setTmnCode(req.tmnCode().trim());
        props.setHashSecret(req.hashSecret().trim());
        if (isSet(req.returnUrl())) {
            props.setReturnUrl(req.returnUrl().trim());
        }

        String savedTo;
        try {
            savedTo = writeEnvFile();
        } catch (IOException e) {
            log.error("Khong ghi duoc .env", e);
            savedTo = "(chi luu trong RAM - khong ghi duoc .env: " + e.getMessage() + ")";
        }

        log.info("Da cap nhat cau hinh VNPAY: tmnCode={} returnUrl={}", props.getTmnCode(), props.getReturnUrl());
        return ResponseEntity.ok(Map.of("message", "Da luu", "savedTo", savedTo));
    }

    /** Ghi lai .env de lan sau khoi dong khong phai nhap lai. File nay nam trong .gitignore. */
    private String writeEnvFile() throws IOException {
        Path env = Path.of(System.getProperty("user.dir"), ".env");
        String content = """
                # File nay do trang /setup.html ghi ra. Da nam trong .gitignore.
                VNPAY_TMN_CODE=%s
                VNPAY_HASH_SECRET=%s
                VNPAY_RETURN_URL=%s
                """.formatted(props.getTmnCode(), props.getHashSecret(), props.getReturnUrl());
        Files.writeString(env, content);
        return env.toString();
    }

    /**
     * Chan cau hinh qua tunnel: ngrok luon dat Host = *.ngrok-free.app va them X-Forwarded-For,
     * nen chi request go thang localhost moi di qua duoc.
     */
    private boolean isLocalRequest(HttpServletRequest http) {
        if (http.getHeader("X-Forwarded-For") != null) {
            return false;
        }
        String host = http.getHeader("Host");
        return host != null && (host.startsWith("localhost") || host.startsWith("127.0.0.1"));
    }

    private static boolean isSet(String v) {
        return v != null && !v.isBlank() && !"CHANGE_ME".equals(v);
    }

    private static String mask(String v) {
        return v.length() <= 6 ? "******" : v.substring(0, 3) + "•".repeat(v.length() - 6) + v.substring(v.length() - 3);
    }
}

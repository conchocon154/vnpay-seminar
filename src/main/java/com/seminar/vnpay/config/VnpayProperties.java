package com.seminar.vnpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vnpay")
public class VnpayProperties {

    /** Ma website (TmnCode) lay tu sandbox merchant portal. */
    private String tmnCode;
    /** Chuoi bi mat de ky HMAC-SHA512. KHONG commit len git. */
    private String hashSecret;
    /** URL trang thanh toan sandbox. */
    private String payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    /** API truy van / hoan tien (server-to-server). */
    private String apiUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    /** URL trinh duyet quay ve sau khi thanh toan. */
    private String returnUrl = "http://localhost:8080/vnpay/return";
    /** So phut don hang het han. */
    private int expireMinutes = 15;

    public String getTmnCode() { return tmnCode; }
    public void setTmnCode(String tmnCode) { this.tmnCode = tmnCode; }
    public String getHashSecret() { return hashSecret; }
    public void setHashSecret(String hashSecret) { this.hashSecret = hashSecret; }
    public String getPayUrl() { return payUrl; }
    public void setPayUrl(String payUrl) { this.payUrl = payUrl; }
    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    public int getExpireMinutes() { return expireMinutes; }
    public void setExpireMinutes(int expireMinutes) { this.expireMinutes = expireMinutes; }
}

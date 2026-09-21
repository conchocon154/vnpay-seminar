package com.seminar.vnpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vnpay")
public class VnpayProperties {

    /** Mã website, lấy trong email VNPAY gửi lúc đăng ký sandbox. */
    private String tmnCode;
    /** Chuỗi bí mật dùng để ký. Đừng commit lên git. */
    private String hashSecret;
    /** Trang thanh toán của sandbox. */
    private String payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    /** API truy vấn và hoàn tiền, server gọi server. */
    private String apiUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    /** Nơi trình duyệt quay về sau khi khách trả tiền xong. */
    private String returnUrl = "http://localhost:8080/vnpay/return";
    /** Đơn hết hạn sau bao nhiêu phút. */
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

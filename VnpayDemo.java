/*
 * Demo tích hợp VNPAY, gom hết vào một file.
 *
 * Chạy (cần JDK 17+, không cần Maven, không cần thư viện ngoài):
 *   export VNPAY_TMN_CODE=xxxxxxxx
 *   export VNPAY_HASH_SECRET=xxxxxxxxxxxxxxxx
 *   java VnpayDemo.java
 * Rồi mở http://localhost:8080
 *
 * Lấy TmnCode + HashSecret miễn phí ở https://sandbox.vnpayment.vn/devreg/
 * Thẻ test NCB: 9704198526191432198 | NGUYEN VAN A | 07/15 | OTP 123456
 *
 * File chia theo 10 bước, đọc từ trên xuống:
 *   1 cấu hình, 2 giao diện, 3 đơn hàng, 4 ký HMAC, 5 verify,
 *   6 tạo URL, 7 IPN, 8 querydr, 9 ReturnURL, 10 chạy server
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class VnpayDemo {

    /*
     * Bước 1: cấu hình
     * Đọc từ biến môi trường.
     */

    static final int    PORT           = 8080;
    static final int    EXPIRE_MINUTES = 15;

    static final String TMN_CODE    = env("VNPAY_TMN_CODE", "CHANGE_ME");
    static final String HASH_SECRET = env("VNPAY_HASH_SECRET", "CHANGE_ME");

    /*
     * Hai chế độ chạy, đặt bằng biến môi trường VNPAY_MODE.
     *
     *   local (mặc định): chỉ chạy localhost, không cần ngrok.
     *                       IPN không về được, nên đơn được chốt bằng API querydr.
     *                       Dùng khi cả lớp cùng chạy trên máy mình.
     *
     *   ngrok: mở tunnel public để VNPAY gọi IPN thật vào máy mình.
     *                       Chương trình tự đọc URL từ ngrok đang chạy (cổng 4040).
     *                       Dùng khi cần demo đúng kiến trúc chuẩn.
     */

    static final String MODE       = env("VNPAY_MODE", "local");
    static final String PUBLIC_URL = resolvePublicUrl();
    static final String RETURN_URL = PUBLIC_URL + "/vnpay/return";
    static final String IPN_URL    = PUBLIC_URL + "/vnpay/ipn";

    static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    static final String API_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";

    // VNPAY tính giờ GMT+7. Đừng dùng "Etc/GMT+7", theo POSIX nó là UTC-7, lệch 14 tiếng.
    static final ZoneId            VN_ZONE  = ZoneId.of("Asia/Ho_Chi_Minh");
    static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    // Chế độ ngrok: hỏi ngrok đang chạy xem nó cấp URL nào (API cục bộ cổng 4040).
    static String resolvePublicUrl() {
        String explicit = env("VNPAY_PUBLIC_URL", "");
        if (!explicit.isBlank()) return explicit.replaceAll("/+$", "");

        if ("ngrok".equalsIgnoreCase(MODE)) {
            try {
                HttpResponse<String> res = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:4040/api/tunnels")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                for (String part : res.body().split("\"public_url\":\"")) {
                    if (part.startsWith("https://")) return part.substring(0, part.indexOf('"'));
                }
            } catch (Exception e) {
                System.out.println("!! Không đọc được URL ngrok. Đã chạy `ngrok http 8080` chưa?");
            }
        }
        return "http://localhost:" + PORT;
    }

    /*
     * Bước 2: giao diện web
     * Nhúng thẳng HTML vào đây cho gọn. Có thể tách ra static/index.html rồi gọi lại tại đây cũng được.
     * Trang web không giữ HashSecret, nó chỉ gọi POST /api/payments.
     */

    static final String PAGE_HTML = """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>VNPAY Shop</title>
        <style>
          body{font-family:system-ui,-apple-system,sans-serif;max-width:640px;margin:36px auto;
               padding:0 18px;color:#16181d;line-height:1.55}
          h2{margin:0 0 4px} .sub{color:#6b7280;margin:0 0 24px;font-size:14px}
          .row{display:flex;gap:12px;flex-wrap:wrap}
          .card{flex:1 1 180px;border:1px solid #e5e7eb;border-radius:10px;padding:16px}
          .price{color:#005baa;font-weight:700;font-size:18px;margin:4px 0 12px}
          label{display:block;font-size:13px;font-weight:600;margin:12px 0 4px}
          input,select,button{width:100%;padding:10px;font-size:15px;border-radius:8px;
               border:1px solid #e5e7eb;box-sizing:border-box}
          button{background:#005baa;color:#fff;border:0;font-weight:600;cursor:pointer}
          button:disabled{background:#9aa3ad}
          .note{margin-top:26px;background:#fffbeb;border:1px solid #fde68a;border-radius:10px;
               padding:13px 15px;font-size:13px}
          table{width:100%;border-collapse:collapse;font-size:14px;margin-top:18px}
          th,td{text-align:left;padding:8px 4px;border-bottom:1px solid #eee}
          th{color:#6b7280;font-weight:500;width:45%}
          td{font-family:ui-monospace,Menlo,monospace}
          #result{display:none}
        </style>
        </head>
        <body>

        <div id="shop">
          <h2>VNPAY Shop</h2>
          <p class="sub">Sandbox, không mất tiền thật.</p>

          <div class="row">
            <div class="card">
              <div>Cà phê sữa đá</div><div class="price">25.000đ</div>
              <button onclick="pick(25000,'Ca phe sua da')">Mua ngay</button>
            </div>
            <div class="card">
              <div>Sách Spring Boot</div><div class="price">189.000đ</div>
              <button onclick="pick(189000,'Sach Spring Boot')">Mua ngay</button>
            </div>
          </div>

          <label for="amount">Số tiền (VND)</label>
          <input id="amount" type="number" value="25000">

          <label for="orderInfo">Nội dung đơn hàng</label>
          <input id="orderInfo" value="Thanh toan Ca phe sua da">

          <label for="bankCode">Phương thức</label>
          <select id="bankCode">
            <option value="">Để VNPAY hiện trang chọn ngân hàng</option>
            <option value="NCB" selected>NCB (dùng thẻ test bên dưới)</option>
            <option value="VNPAYQR">Quét mã VNPAYQR</option>
          </select>

          <label>&nbsp;</label>
          <button id="payBtn" onclick="pay()">Thanh toán</button>
          <p id="err" style="color:#dc2626;font-size:13px"></p>

          <div class="note">
            <b>Thẻ test NCB:</b> 9704198526191432198 · NGUYEN VAN A · 07/15 · OTP 123456
          </div>
        </div>

        <div id="result">
          <h2 id="rTitle">Đang xác nhận…</h2>
          <p class="sub" id="rDesc">Đang hỏi VNPAY trạng thái thật của giao dịch.</p>
          <table>
            <tr><th>Mã đơn</th><td id="rRef">—</td></tr>
            <tr><th>Số tiền</th><td id="rAmount">—</td></tr>
            <tr><th>Trạng thái</th><td id="rStatus">—</td></tr>
            <tr><th>Mã GD tại VNPAY</th><td id="rNo">—</td></tr>
            <tr><th>Chữ ký ReturnURL</th><td id="rSig">—</td></tr>
          </table>
          <p style="margin-top:20px"><a href="/">← Mua tiếp</a></p>
        </div>

        <script>
        function pick(amount, info){
          document.getElementById('amount').value = amount;
          document.getElementById('orderInfo').value = 'Thanh toan ' + info;
        }

        // Gọi server để lấy URL đã ký. Trình duyệt KHÔNG bao giờ giữ HashSecret.
        async function pay(){
          const btn = document.getElementById('payBtn');
          btn.disabled = true;
          const body = new URLSearchParams({
            amount:    document.getElementById('amount').value,
            orderInfo: document.getElementById('orderInfo').value,
            bankCode:  document.getElementById('bankCode').value
          });
          const res  = await fetch('/api/payments', {method:'POST', body: body});
          const data = await res.json();
          if (data.error) {
            document.getElementById('err').textContent = data.error;
            btn.disabled = false;
            return;
          }
          window.location.href = data.paymentUrl;
        }

        // Trang kết quả: KHÔNG đọc trạng thái từ URL, mà hỏi lại server.
        const q = new URLSearchParams(location.search);
        if (q.get('txnRef')) {
          document.getElementById('shop').style.display = 'none';
          document.getElementById('result').style.display = 'block';
          document.getElementById('rRef').textContent = q.get('txnRef');
          document.getElementById('rSig').textContent = q.get('valid') === 'true' ? 'hợp lệ' : 'KHÔNG hợp lệ';
          poll(0);
        }

        async function poll(tries){
          const res = await fetch('/api/orders?txnRef=' + encodeURIComponent(q.get('txnRef')));
          const o = await res.json();
          document.getElementById('rAmount').textContent = o.amount;
          document.getElementById('rStatus').textContent = o.status;
          document.getElementById('rNo').textContent = o.transactionNo || '—';
          if (o.status === 'PAID'){
            document.getElementById('rTitle').textContent = 'Thanh toán thành công';
            document.getElementById('rDesc').textContent  = 'Server đã xác thực với VNPAY và ghi nhận đơn.';
          } else if (o.status === 'FAILED'){
            document.getElementById('rTitle').textContent = 'Thanh toán thất bại';
            document.getElementById('rDesc').textContent  = 'Giao dịch bị huỷ hoặc ngân hàng từ chối.';
          } else if (tries < 10){
            setTimeout(function(){ poll(tries + 1); }, 2000);
          }
        }
        </script>
        </body>
        </html>
        """;

    /*
     * Bước 3: đơn hàng
     * Lưu tạm trong RAM.
     */

    static class Order {
        final String txnRef;
        final long   amount;        // VND, CHƯA nhân 100
        final String orderInfo;
        final String createDate;
        String status = "PENDING";  // PENDING | PAID | FAILED
        String transactionNo = "";
        String bankCode = "";

        Order(String txnRef, long amount, String orderInfo, String createDate) {
            this.txnRef = txnRef;
            this.amount = amount;
            this.orderInfo = orderInfo;
            this.createDate = createDate;
        }
    }

    static final Map<String, Order> ORDERS = new ConcurrentHashMap<>();

    /*
     * Bước 4: ký HMAC-SHA512, phần khó nhất
     * Gồm bước: Sort alphabet -> URL-encode GIÁ TRỊ -> HMAC-SHA512 ra hex chữ thường.
     */

    static String hmacSHA512(String secretKey, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được HMAC-SHA512", e);
        }
    }

    /** Chuỗi trả về vừa dùng làm hashData, vừa làm query string -> không bị lệch nhau. */
    static String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : new TreeMap<>(params).entrySet()) {   // sort alphabet
            String value = e.getValue();
            if (value == null || value.isEmpty()) continue;                      // bỏ tham số rỗng
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII))
              .append('=')
              .append(URLEncoder.encode(value, StandardCharsets.US_ASCII));      // encode GIÁ TRỊ
        }
        return sb.toString();
    }

    /*
     * Bước 5: verify chữ ký
     */

    static boolean isValidSignature(Map<String, String> params) {
        String received = params.get("vnp_SecureHash");
        if (received == null || received.isBlank()) return false;

        Map<String, String> clone = new TreeMap<>(params);
        clone.remove("vnp_SecureHash");
        clone.remove("vnp_SecureHashType");

        String expected = hmacSHA512(HASH_SECRET, buildQueryString(clone));
        return constantTimeEquals(expected, received);
    }

    // So sánh không phụ thuộc thời gian, chống timing attack.
    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= Character.toLowerCase(a.charAt(i)) ^ Character.toLowerCase(b.charAt(i));
        }
        return diff == 0;
    }

    /*
     * Bước 6: tạo URL thanh toán
     */

    static String createPayment(long amount, String orderInfo, String bankCode, String clientIp) {
        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        String createDate = now.format(VNP_TIME);
        String txnRef = createDate + ThreadLocalRandom.current().nextInt(100_000, 999_999);

        ORDERS.put(txnRef, new Order(txnRef, amount, orderInfo, createDate));

        Map<String, String> p = new HashMap<>();
        p.put("vnp_Version",    "2.1.0");
        p.put("vnp_Command",    "pay");
        p.put("vnp_TmnCode",    TMN_CODE);
        p.put("vnp_Amount",     String.valueOf(amount * 100));     // NHÂN 100, số nguyên
        p.put("vnp_CurrCode",   "VND");
        p.put("vnp_TxnRef",     txnRef);
        p.put("vnp_OrderInfo",  orderInfo);
        p.put("vnp_OrderType",  "other");
        p.put("vnp_Locale",     "vn");
        p.put("vnp_ReturnUrl",  RETURN_URL);
        p.put("vnp_IpAddr",     clientIp);
        p.put("vnp_CreateDate", createDate);
        p.put("vnp_ExpireDate", now.plusMinutes(EXPIRE_MINUTES).format(VNP_TIME));
        if (bankCode != null && !bankCode.isBlank()) p.put("vnp_BankCode", bankCode);

        String query = buildQueryString(p);
        String url = PAY_URL + "?" + query + "&vnp_SecureHash=" + hmacSHA512(HASH_SECRET, query);

        return "{\"txnRef\":\"" + txnRef + "\",\"paymentUrl\":\"" + url + "\"}";
    }

    /*
     * Bước 7: nhận IPN, VNPAY gọi thẳng vào server
     * VNPAY retry tới khi nhận RspCode=00, nên handler phải idempotent.
     */

    static String handleIpn(Map<String, String> params) {
        try {
            if (!isValidSignature(params))                    return rsp("97", "Invalid Checksum");

            Order order = ORDERS.get(params.get("vnp_TxnRef"));
            if (order == null)                                return rsp("01", "Order not Found");

            long vnpAmount = Long.parseLong(params.getOrDefault("vnp_Amount", "-1"));
            if (vnpAmount != order.amount * 100)              return rsp("04", "Invalid Amount");

            synchronized (order) {
                if (!"PENDING".equals(order.status))          return rsp("02", "Order already confirmed");

                boolean paid = "00".equals(params.get("vnp_ResponseCode"))
                            && "00".equals(params.get("vnp_TransactionStatus"));
                order.status        = paid ? "PAID" : "FAILED";
                order.transactionNo = params.getOrDefault("vnp_TransactionNo", "");
                order.bankCode      = params.getOrDefault("vnp_BankCode", "");
                log("IPN  txnRef=" + order.txnRef + " -> " + order.status
                        + " transactionNo=" + order.transactionNo);
            }
            return rsp("00", "Confirm Success");
        } catch (Exception e) {
            return rsp("99", "Unknown error");
        }
    }

    static String rsp(String code, String message) {
        return "{\"RspCode\":\"" + code + "\",\"Message\":\"" + message + "\"}";
    }

    /*
     * Bước 8: đối soát bằng querydr
     * Dùng khi IPN không về, hay gặp trên sandbox hoặc khi chạy localhost.
     * Vẫn an toàn như IPN: hỏi thẳng VNPAY, verify chữ ký response, so lại số tiền.
     */

    static void reconcile(Order order) {
        if (!"PENDING".equals(order.status)) return;          // đã chốt rồi

        String requestId  = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String createDate = LocalDateTime.now(VN_ZONE).format(VNP_TIME);
        String orderInfo  = "Truy van GD ma:" + order.txnRef;
        String ipAddr     = "127.0.0.1";

        // Hash của querydr không sort alphabet, nối bằng '|' đúng thứ tự tài liệu.
        String hashData = String.join("|",
                requestId, "2.1.0", "querydr", TMN_CODE,
                order.txnRef, order.createDate, createDate, ipAddr, orderInfo);

        String body = """
            {"vnp_RequestId":"%s","vnp_Version":"2.1.0","vnp_Command":"querydr",
             "vnp_TmnCode":"%s","vnp_TxnRef":"%s","vnp_OrderInfo":"%s",
             "vnp_TransactionDate":"%s","vnp_CreateDate":"%s","vnp_IpAddr":"%s",
             "vnp_SecureHash":"%s"}
            """.formatted(requestId, TMN_CODE, order.txnRef, orderInfo,
                          order.createDate, createDate, ipAddr,
                          hmacSHA512(HASH_SECRET, hashData));

        try {
            HttpResponse<String> res = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(API_URL))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            String json = res.body();
            String responseCode      = jsonValue(json, "vnp_ResponseCode");
            String transactionStatus = jsonValue(json, "vnp_TransactionStatus");
            String amount            = jsonValue(json, "vnp_Amount");

            if (!"00".equals(responseCode)) {                       // truy vấn chưa có kết quả
                log("querydr txnRef=" + order.txnRef + " vnp_ResponseCode=" + responseCode);
                return;
            }
            if (!String.valueOf(order.amount * 100).equals(amount)) {
                log("querydr SỐ TIỀN KHÔNG KHỚP txnRef=" + order.txnRef + " -> bỏ qua");
                return;
            }

            synchronized (order) {
                if (!"PENDING".equals(order.status)) return;
                order.status        = "00".equals(transactionStatus) ? "PAID" : "FAILED";
                order.transactionNo = jsonValue(json, "vnp_TransactionNo");
                order.bankCode      = jsonValue(json, "vnp_BankCode");
                log("querydr txnRef=" + order.txnRef + " -> " + order.status
                        + " transactionNo=" + order.transactionNo);
            }
        } catch (Exception e) {
            log("querydr lỗi: " + e);
        }
    }

    // Lấy giá trị một khoá trong JSON phẳng. Đủ dùng, khỏi cần thư viện.
    static String jsonValue(String json, String key) {
        String needle = "\"" + key + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return "";
        int colon = json.indexOf(':', i + needle.length());
        int open  = json.indexOf('"', colon + 1);
        int close = json.indexOf('"', open + 1);
        return (colon < 0 || open < 0 || close < 0) ? "" : json.substring(open + 1, close);
    }

    /*
     * Bước 9: ReturnURL
     * Chỉ để hiển thị. Đừng đổi trạng thái đơn theo tham số trên URL.
     */

    static String handleReturn(Map<String, String> params) {
        boolean valid = isValidSignature(params);
        String txnRef = params.getOrDefault("vnp_TxnRef", "");

        // Không tin URL, nhưng dùng txnRef để tra cứu rồi hỏi thẳng VNPAY.
        if (valid) {
            Order order = ORDERS.get(txnRef);
            if (order != null) reconcile(order);
        }
        return "/?txnRef=" + URLEncoder.encode(txnRef, StandardCharsets.US_ASCII) + "&valid=" + valid;
    }

    /*
     * Bước 10: chạy server
     */

    public static void main(String[] args) throws IOException {
        if ("CHANGE_ME".equals(TMN_CODE) || "CHANGE_ME".equals(HASH_SECRET)) {
            System.out.println("""

                !! CHƯA CÓ CREDENTIAL. Chạy lại như sau:

                   export VNPAY_TMN_CODE=xxxxxxxx
                   export VNPAY_HASH_SECRET=xxxxxxxxxxxxxxxx
                   java VnpayDemo.java

                   Đăng ký miễn phí tại https://sandbox.vnpayment.vn/devreg/
                """);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Trang web
        server.createContext("/", ex -> {
            if (!"/".equals(ex.getRequestURI().getPath())) { send(ex, 404, "text/plain", "Not found"); return; }
            send(ex, 200, "text/html; charset=UTF-8", PAGE_HTML);
        });

        // Tạo yêu cầu thanh toán
        server.createContext("/api/payments", ex -> {
            Map<String, String> form = parseQuery(new String(ex.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            try {
                long amount = Long.parseLong(form.getOrDefault("amount", "0"));
                if (amount < 5_000) throw new IllegalArgumentException("Số tiền tối thiểu 5.000đ");
                String info = form.getOrDefault("orderInfo", "Thanh toan don hang");
                send(ex, 200, "application/json",
                        createPayment(amount, info, form.get("bankCode"), "127.0.0.1"));
            } catch (Exception e) {
                send(ex, 400, "application/json", "{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        // Trạng thái đơn, giao diện lấy từ đây
        server.createContext("/api/orders", ex -> {
            Order o = ORDERS.get(parseQuery(ex.getRequestURI().getRawQuery()).get("txnRef"));
            if (o == null) { send(ex, 404, "application/json", "{}"); return; }
            if ("PENDING".equals(o.status)) reconcile(o);          // tự đối soát khi được hỏi
            send(ex, 200, "application/json",
                    ("{\"txnRef\":\"%s\",\"amount\":%d,\"status\":\"%s\",\"transactionNo\":\"%s\","
                     + "\"bankCode\":\"%s\"}").formatted(
                            o.txnRef, o.amount, o.status, o.transactionNo, o.bankCode));
        });

        // ReturnURL, trình duyệt quay về
        server.createContext("/vnpay/return", ex -> {
            String location = handleReturn(parseQuery(ex.getRequestURI().getRawQuery()));
            ex.getResponseHeaders().add("Location", location);
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });

        // IPN, VNPAY gọi thẳng vào server
        server.createContext("/vnpay/ipn", ex ->
                send(ex, 200, "application/json",
                        handleIpn(parseQuery(ex.getRequestURI().getRawQuery()))));

        server.start();

        boolean ngrokMode = PUBLIC_URL.startsWith("https://");
        System.out.println("""

            VNPAY demo, chế độ %s

              Mở cửa hàng:  %s
              ReturnURL:    %s
              IPN URL:      %s

            %s
            """.formatted(
                ngrokMode ? "ngrok, IPN thật" : "local, không cần ngrok",
                PUBLIC_URL,
                RETURN_URL,
                ngrokMode ? IPN_URL : "(không dùng, localhost thì VNPAY không gọi tới được)",
                ngrokMode
                    ? "  Khai 2 URL trên vào sandbox.vnpayment.vn/merchantv2\n"
                    + "  -> Cấu hình -> Thông tin website. URL đổi mỗi lần chạy lại ngrok."
                    : "  Không khai gì cả. Đơn được chốt bằng API querydr khi trang\n"
                    + "  kết quả hỏi trạng thái. Chạy được ngay trên máy cá nhân."));

        log("TmnCode=" + TMN_CODE);
    }

    // mấy hàm tiện ích

    // Tách query string thành map, URL-decode giống servlet.
    static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i < 0) continue;
            map.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
        }
        return map;
    }

    static void send(HttpExchange ex, int code, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", contentType);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    static void log(String message) {
        System.out.println("[" + LocalDateTime.now(VN_ZONE).format(
                DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + message);
    }
}

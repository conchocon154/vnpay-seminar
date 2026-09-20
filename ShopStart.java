/* =====================================================================================
 *  SHOPSTART — CODE XUẤT PHÁT CHO BUỔI SEMINAR
 *  -------------------------------------------------------------------------------
 *  Server chạy được, cửa hàng hiện ra, NHƯNG chưa có một dòng VNPAY nào.
 *  Trong buổi seminar ta sẽ điền lần lượt 6 chỗ TODO bên dưới.
 *
 *  CHẠY (chỉ cần JDK 17+, không Maven, không thư viện ngoài):
 *      java ShopStart.java
 *  Rồi mở http://localhost:8080
 *
 *  ---------------------------------------------------------------------------
 *  LỘ TRÌNH ĐIỀN TRONG BUỔI NÓI
 *    TODO 1 — Cấu hình TmnCode / HashSecret
 *    TODO 2 — Hàm ký HMAC-SHA512              <- trái tim của VNPAY
 *    TODO 3 — Hàm verify chữ ký
 *    TODO 4 — Tạo URL thanh toán
 *    TODO 5 — Nhận IPN (server-to-server)
 *    TODO 6 — ReturnURL + chốt trạng thái đơn
 *
 *  File đáp án đầy đủ: VnpayDemo.java
 * ===================================================================================== */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ShopStart {

    static final int PORT = 8080;

    // VNPAY tính giờ GMT+7. KHÔNG dùng "Etc/GMT+7" — theo POSIX nó là UTC-7, lệch 14 tiếng.
    static final ZoneId            VN_ZONE  = ZoneId.of("Asia/Ho_Chi_Minh");
    static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /* =================================================================================
     * TODO 1 — CẤU HÌNH
     * Lấy TmnCode + HashSecret miễn phí tại https://sandbox.vnpayment.vn/devreg/
     * Đọc từ biến môi trường để không bao giờ commit secret lên git.
     *
     *   static final String TMN_CODE    = env("VNPAY_TMN_CODE", "CHANGE_ME");
     *   static final String HASH_SECRET = env("VNPAY_HASH_SECRET", "CHANGE_ME");
     *   static final String RETURN_URL  = env("VNPAY_RETURN_URL",
     *                                         "http://localhost:8080/vnpay/return");
     *   static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
     *   static final String API_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
     * ================================================================================= */

    static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    /* ---------------------------------------------------------------------------------
     * ĐƠN HÀNG — phần này đã viết sẵn, không phải đụng tới trong buổi nói.
     * --------------------------------------------------------------------------------- */

    static class Order {
        final String txnRef;
        final long   amount;        // VND, CHƯA nhân 100
        final String orderInfo;
        final String createDate;
        String status = "PENDING";  // PENDING | PAID | FAILED
        String transactionNo = "";
        String bankCode = "";

        Order(String txnRef, long amount, String orderInfo, String createDate) {
            this.txnRef = txnRef;  this.amount = amount;
            this.orderInfo = orderInfo;  this.createDate = createDate;
        }
    }

    static final Map<String, Order> ORDERS = new ConcurrentHashMap<>();

    /** Mã đơn duy nhất trong 24h theo TmnCode. */
    static String newTxnRef(String createDate) {
        return createDate + ThreadLocalRandom.current().nextInt(100_000, 999_999);
    }

    /* =================================================================================
     * TODO 2 — KÝ HMAC-SHA512   <-- TRÁI TIM CỦA VNPAY
     *
     * Ba bước: sort alphabet -> URL-encode GIÁ TRỊ -> HMAC-SHA512 ra hex CHỮ THƯỜNG.
     *
     *   static String hmacSHA512(String secretKey, String data) {
     *       Mac mac = Mac.getInstance("HmacSHA512");
     *       mac.init(new SecretKeySpec(secretKey.getBytes(UTF_8), "HmacSHA512"));
     *       byte[] bytes = mac.doFinal(data.getBytes(UTF_8));
     *       StringBuilder hex = new StringBuilder();
     *       for (byte b : bytes) hex.append(String.format("%02x", b));
     *       return hex.toString();
     *   }
     *
     *   static String buildQueryString(Map<String, String> params) {
     *       // TreeMap để sort alphabet, bỏ tham số rỗng,
     *       // URLEncoder.encode(value, US_ASCII) cho TỪNG GIÁ TRỊ, nối bằng '&'
     *   }
     * ================================================================================= */

    /* =================================================================================
     * TODO 3 — VERIFY CHỮ KÝ
     *
     * Dùng lại đúng buildQueryString ở trên, chỉ thêm một việc:
     * bỏ vnp_SecureHash và vnp_SecureHashType ra khỏi map TRƯỚC khi băm.
     *
     *   static boolean isValidSignature(Map<String, String> params) { ... }
     *
     * Hai lỗi chiếm 90% ca "sai chữ ký":
     *   1. quên URL-encode lại (servlet đã decode sẵn tham số)
     *   2. quên bỏ chính vnp_SecureHash ra khỏi chuỗi
     * ================================================================================= */

    /* =================================================================================
     * TODO 4 — TẠO URL THANH TOÁN
     *
     *   static String createPayment(long amount, String orderInfo, String bankCode, String ip) {
     *       // 1. lưu đơn ở trạng thái PENDING
     *       // 2. ráp map tham số vnp_*  (nhớ vnp_Amount = amount * 100)
     *       // 3. query = buildQueryString(p)
     *       // 4. url = PAY_URL + "?" + query + "&vnp_SecureHash=" + hmacSHA512(HASH_SECRET, query)
     *   }
     * ================================================================================= */

    /** Bản tạm: chưa ký được nên báo lỗi cho frontend. Xoá khi làm xong TODO 4. */
    static String createPaymentStub(long amount, String orderInfo) {
        String createDate = LocalDateTime.now(VN_ZONE).format(VNP_TIME);
        String txnRef = newTxnRef(createDate);
        ORDERS.put(txnRef, new Order(txnRef, amount, orderInfo, createDate));
        log("Đã tạo đơn " + txnRef + " — nhưng CHƯA ký được URL (xem TODO 4)");
        return "{\"error\":\"Chưa nhúng VNPAY. Làm TODO 1 đến TODO 4 trong ShopStart.java\"}";
    }

    /* =================================================================================
     * TODO 5 — NHẬN IPN (server-to-server)
     *
     * VNPAY retry tới khi nhận RspCode=00 -> handler BẮT BUỘC idempotent.
     *
     *   97 chữ ký sai -> 01 không có đơn -> 04 sai số tiền
     *   -> 02 đã xử lý rồi -> 00 ghi nhận xong
     *
     * Đây là NƠI DUY NHẤT được cộng tiền / giao hàng.
     * ================================================================================= */

    static String handleIpnStub(Map<String, String> params) {
        log("Nhận IPN cho txnRef=" + params.get("vnp_TxnRef") + " — nhưng chưa verify (xem TODO 5)");
        return "{\"RspCode\":\"99\",\"Message\":\"Chua lam TODO 5\"}";
    }

    /* =================================================================================
     * TODO 6 — RETURNURL
     *
     * Trình duyệt user quay về đây. CHỈ để hiển thị — không đổi trạng thái đơn
     * theo tham số trên URL, vì user sửa URL được.
     *
     * Nếu chạy KHÔNG có ngrok thì IPN không về được, lúc đó gọi API querydr
     * để hỏi thẳng VNPAY trạng thái thật (xem VnpayDemo.java).
     * ================================================================================= */

    static String handleReturnStub(Map<String, String> params) {
        return "/?txnRef=" + params.getOrDefault("vnp_TxnRef", "") + "&valid=false";
    }

    /* ---------------------------------------------------------------------------------
     * GIAO DIỆN — đã viết sẵn, giống hệt bản đáp án.
     * --------------------------------------------------------------------------------- */

    static final String PAGE_HTML = """
        <!DOCTYPE html>
        <html lang="vi">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>VNPAY Shop — code xuất phát</title>
        <style>
          body{font-family:system-ui,-apple-system,sans-serif;max-width:640px;margin:36px auto;
               padding:0 18px;color:#16181d;line-height:1.55}
          h2{margin:0 0 4px} .sub{color:#6b7280;margin:0 0 20px;font-size:14px}
          .status{background:#fef3c7;border:1px solid #fcd34d;border-radius:8px;
               padding:10px 14px;font-size:13.5px;margin-bottom:22px}
          .row{display:flex;gap:12px;flex-wrap:wrap}
          .card{flex:1 1 180px;border:1px solid #e5e7eb;border-radius:10px;padding:16px}
          .price{color:#005baa;font-weight:700;font-size:18px;margin:4px 0 12px}
          label{display:block;font-size:13px;font-weight:600;margin:12px 0 4px}
          input,select,button{width:100%;padding:10px;font-size:15px;border-radius:8px;
               border:1px solid #e5e7eb;box-sizing:border-box}
          button{background:#005baa;color:#fff;border:0;font-weight:600;cursor:pointer}
          .note{margin-top:26px;background:#fffbeb;border:1px solid #fde68a;border-radius:10px;
               padding:13px 15px;font-size:13px}
          #msg{font-size:13.5px;margin-top:10px;min-height:20px}
        </style>
        </head>
        <body>

        <h2>VNPAY Shop</h2>
        <p class="sub">Cửa hàng demo cho buổi seminar.</p>

        <div class="status">
          <b>Trạng thái: chưa nhúng VNPAY.</b> Server chạy rồi, phần ký chữ ký còn trống.
        </div>

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
          <option value="NCB" selected>NCB — dùng thẻ test bên dưới</option>
          <option value="VNPAYQR">Quét mã VNPAYQR</option>
        </select>

        <label>&nbsp;</label>
        <button id="payBtn" onclick="pay()">Thanh toán</button>
        <p id="msg"></p>

        <div class="note">
          <b>Thẻ test NCB:</b> 9704198526191432198 · NGUYEN VAN A · 07/15 · OTP 123456
        </div>

        <script>
        function pick(amount, info){
          document.getElementById('amount').value = amount;
          document.getElementById('orderInfo').value = 'Thanh toan ' + info;
        }

        // Phần này đã xong: gọi server xin URL. Trình duyệt KHÔNG bao giờ giữ HashSecret.
        async function pay(){
          const body = new URLSearchParams({
            amount:    document.getElementById('amount').value,
            orderInfo: document.getElementById('orderInfo').value,
            bankCode:  document.getElementById('bankCode').value
          });
          const res  = await fetch('/api/payments', {method:'POST', body: body});
          const data = await res.json();

          if (data.error) {
            document.getElementById('msg').innerHTML =
                '<span style="color:#b45309">' + data.error + '</span>';
            return;
          }
          window.location.href = data.paymentUrl;
        }
        </script>
        </body>
        </html>
        """;

    /* ---------------------------------------------------------------------------------
     * SERVER
     * --------------------------------------------------------------------------------- */

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/", ex -> {
            if (!"/".equals(ex.getRequestURI().getPath())) { send(ex, 404, "text/plain", "Not found"); return; }
            send(ex, 200, "text/html; charset=UTF-8", PAGE_HTML);
        });

        server.createContext("/api/payments", ex -> {
            Map<String, String> form = parseQuery(
                    new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            long amount = Long.parseLong(form.getOrDefault("amount", "0"));
            String info = form.getOrDefault("orderInfo", "Thanh toan don hang");
            // TODO 4: đổi dòng dưới thành createPayment(amount, info, form.get("bankCode"), "127.0.0.1")
            send(ex, 200, "application/json", createPaymentStub(amount, info));
        });

        server.createContext("/api/orders", ex -> {
            Order o = ORDERS.get(parseQuery(ex.getRequestURI().getRawQuery()).get("txnRef"));
            if (o == null) { send(ex, 404, "application/json", "{}"); return; }
            send(ex, 200, "application/json",
                    ("{\"txnRef\":\"%s\",\"amount\":%d,\"status\":\"%s\",\"transactionNo\":\"%s\","
                     + "\"bankCode\":\"%s\"}").formatted(
                            o.txnRef, o.amount, o.status, o.transactionNo, o.bankCode));
        });

        server.createContext("/vnpay/return", ex -> {
            String location = handleReturnStub(parseQuery(ex.getRequestURI().getRawQuery()));
            ex.getResponseHeaders().add("Location", location);
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });

        server.createContext("/vnpay/ipn", ex ->
                send(ex, 200, "application/json",
                        handleIpnStub(parseQuery(ex.getRequestURI().getRawQuery()))));

        server.start();
        log("ShopStart đang chạy tại http://localhost:" + PORT);
        log("Cửa hàng hiện ra được, nhưng chưa nhúng VNPAY — mở file này và tìm TODO 1.");
    }

    /* ----------------------------- tiện ích nhỏ ----------------------------- */

    /** Tách query string thành map, đã URL-decode giống hệt servlet. */
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
        System.out.println("[" + LocalDateTime.now(VN_ZONE)
                .format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + message);
    }
}

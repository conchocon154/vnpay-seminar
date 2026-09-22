/*
 * Chạy:  java ShopStart.java      (JDK 17+, không cần Maven)
 * Mở:    http://localhost:8080
 *
 * Bản đã điền xong sáu chỗ TODO.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ShopDone {

    static final java.nio.charset.Charset UTF_8 = StandardCharsets.UTF_8;
    static final java.nio.charset.Charset ASCII = StandardCharsets.US_ASCII;

    static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    static final String API_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    static final int PORT = 8080;

    /* ===== 1. Hai giá trị lấy từ email đăng ký sandbox ===== */

    static final String TMN_CODE    = env("VNPAY_TMN_CODE", "");
    static final String HASH_SECRET = env("VNPAY_HASH_SECRET", "");

    // Khi chưa mở ngrok thì để nguyên localhost.
    static final String RETURN_URL = env("VNPAY_RETURN_URL", "http://localhost:" + PORT + "/vnpay/return");


    /* ===== 2. Băm chuỗi bằng HMAC-SHA512 =====
     * key  = HASH_SECRET
     * data = chuỗi query do buildQueryString ráp ra
     * trả về chuỗi hex chữ thường, dài 128 ký tự
     */
    static String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(data.getBytes(UTF_8));

            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA512", e);
        }
    }


    /* ===== 3. Ráp map tham số thành chuỗi query =====
     * params = các tham số vnp_* sắp gửi đi, hoặc nhận được từ VNPAY
     * sắp theo alphabet, bỏ giá trị rỗng, URL-encode phần giá trị, nối bằng &
     */
    static String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : new TreeMap<>(params).entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), ASCII)).append('=')
              .append(URLEncoder.encode(e.getValue(), ASCII));
        }
        return sb.toString();
    }


    /* ===== 4. Kiểm tra chữ ký VNPAY gửi kèm =====
     * params = toàn bộ query param nhận được ở /vnpay/ipn hoặc /vnpay/return
     * bỏ vnp_SecureHash và vnp_SecureHashType ra, ký lại phần còn lại rồi so
     */
    static boolean isValidSignature(Map<String, String> params) {
        String received = params.get("vnp_SecureHash");
        if (received == null || received.isBlank()) return false;

        Map<String, String> clone = new TreeMap<>(params);
        clone.remove("vnp_SecureHash");
        clone.remove("vnp_SecureHashType");

        return hmacSHA512(HASH_SECRET, buildQueryString(clone)).equalsIgnoreCase(received);
    }


    /* ===== 5. Tạo đơn và sinh URL thanh toán =====
     * amount    = số tiền VND, từ ô nhập trên trang web
     * orderInfo = nội dung đơn, từ ô nhập trên trang web
     * bankCode  = mã ngân hàng, từ ô chọn trên trang web, có thể rỗng
     * trả về JSON {"txnRef":"...","paymentUrl":"..."}
     */
    static String createPayment(long amount, String orderInfo, String bankCode) {
        LocalDateTime now = LocalDateTime.now(VN);
        String createDate = now.format(TIME);
        String txnRef = newTxnRef(createDate);
        ORDERS.put(txnRef, new Order(txnRef, amount, orderInfo, createDate));

        Map<String, String> p = new HashMap<>();
        p.put("vnp_Version", "2.1.0");
        p.put("vnp_Command", "pay");
        p.put("vnp_TmnCode", TMN_CODE);
        p.put("vnp_Amount", String.valueOf(amount * 100));
        p.put("vnp_CurrCode", "VND");
        p.put("vnp_TxnRef", txnRef);
        p.put("vnp_OrderInfo", orderInfo);
        p.put("vnp_OrderType", "other");
        p.put("vnp_Locale", "vn");
        p.put("vnp_ReturnUrl", RETURN_URL);
        p.put("vnp_IpAddr", "127.0.0.1");
        p.put("vnp_CreateDate", createDate);
        p.put("vnp_ExpireDate", now.plusMinutes(15).format(TIME));
        if (bankCode != null && !bankCode.isBlank()) p.put("vnp_BankCode", bankCode);

        String query = buildQueryString(p);
        String url = PAY_URL + "?" + query + "&vnp_SecureHash=" + hmacSHA512(HASH_SECRET, query);
        return "{\"txnRef\":\"" + txnRef + "\",\"paymentUrl\":\"" + url + "\"}";
    }


    /* ===== 6. Nhận IPN, cập nhật trạng thái đơn =====
     * params = query param VNPAY gửi sang, đã parse sẵn
     * trả về JSON {"RspCode":"..","Message":".."}
     * 97 sai chữ ký · 01 không có đơn · 04 sai số tiền · 02 đã xử lý · 00 xong
     */
    static String handleIpn(Map<String, String> params) {
        if (!isValidSignature(params)) return rsp("97", "Invalid Checksum");

        Order order = ORDERS.get(params.get("vnp_TxnRef"));
        if (order == null) return rsp("01", "Order not Found");

        if (Long.parseLong(params.getOrDefault("vnp_Amount", "-1")) != order.amount * 100)
            return rsp("04", "Invalid Amount");

        synchronized (order) {
            if (!"PENDING".equals(order.status)) return rsp("02", "Order already confirmed");

            boolean paid = "00".equals(params.get("vnp_ResponseCode"))
                        && "00".equals(params.get("vnp_TransactionStatus"));
            order.status = paid ? "PAID" : "FAILED";
            order.transactionNo = params.getOrDefault("vnp_TransactionNo", "");
            order.bankCode = params.getOrDefault("vnp_BankCode", "");
            log("IPN " + order.txnRef + " -> " + order.status + " " + order.transactionNo);
        }
        return rsp("00", "Confirm Success");
    }


    /* ================= phần dưới không đụng tới ================= */

    // Đơn hàng trong bộ nhớ. amount lưu số tiền thật, chưa nhân 100.
    static class Order {
        final String txnRef, orderInfo, createDate;
        final long amount;
        String status = "PENDING", transactionNo = "", bankCode = "";
        Order(String txnRef, long amount, String orderInfo, String createDate) {
            this.txnRef = txnRef; this.amount = amount;
            this.orderInfo = orderInfo; this.createDate = createDate;
        }
    }

    static final Map<String, Order> ORDERS = new ConcurrentHashMap<>();

    // Mã đơn duy nhất trong 24h: thời điểm tạo ghép 6 số ngẫu nhiên.
    static String newTxnRef(String createDate) {
        return createDate + ThreadLocalRandom.current().nextInt(100_000, 999_999);
    }

    static String rsp(String code, String message) {
        return "{\"RspCode\":\"" + code + "\",\"Message\":\"" + message + "\"}";
    }

    // Hỏi thẳng VNPAY trạng thái thật, dùng khi IPN không về. order = đơn còn PENDING.
    static void reconcile(Order order) {
        if (!"PENDING".equals(order.status) || HASH_SECRET.isEmpty()) return;

        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String now = LocalDateTime.now(VN).format(TIME);
        String info = "Truy van GD ma:" + order.txnRef;

        // Hash của querydr nối bằng '|', không sắp alphabet.
        String hashData = String.join("|", requestId, "2.1.0", "querydr", TMN_CODE,
                order.txnRef, order.createDate, now, "127.0.0.1", info);

        String body = ("{\"vnp_RequestId\":\"%s\",\"vnp_Version\":\"2.1.0\",\"vnp_Command\":\"querydr\","
                + "\"vnp_TmnCode\":\"%s\",\"vnp_TxnRef\":\"%s\",\"vnp_OrderInfo\":\"%s\","
                + "\"vnp_TransactionDate\":\"%s\",\"vnp_CreateDate\":\"%s\",\"vnp_IpAddr\":\"127.0.0.1\","
                + "\"vnp_SecureHash\":\"%s\"}").formatted(requestId, TMN_CODE, order.txnRef, info,
                order.createDate, now, hmacSHA512(HASH_SECRET, hashData));

        try {
            HttpResponse<String> res = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(API_URL))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body, UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString());

            String json = res.body();
            if (!"00".equals(jsonValue(json, "vnp_ResponseCode"))) return;
            if (!String.valueOf(order.amount * 100).equals(jsonValue(json, "vnp_Amount"))) return;

            synchronized (order) {
                if (!"PENDING".equals(order.status)) return;
                order.status = "00".equals(jsonValue(json, "vnp_TransactionStatus")) ? "PAID" : "FAILED";
                order.transactionNo = jsonValue(json, "vnp_TransactionNo");
                order.bankCode = jsonValue(json, "vnp_BankCode");
                log("querydr " + order.txnRef + " -> " + order.status + " " + order.transactionNo);
            }
        } catch (Exception e) {
            log("querydr lỗi: " + e);
        }
    }

    // Lấy giá trị một khoá trong JSON phẳng.
    static String jsonValue(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        int colon = json.indexOf(':', i), open = json.indexOf('"', colon + 1);
        int close = json.indexOf('"', open + 1);
        return (colon < 0 || open < 0 || close < 0) ? "" : json.substring(open + 1, close);
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Trang web đọc thẳng từ index.html cạnh file này, sửa xong bấm F5 là thấy.
        server.createContext("/", ex -> {
            if (!"/".equals(ex.getRequestURI().getPath())) { send(ex, 404, "text/plain", "Not found"); return; }
            send(ex, 200, "text/html; charset=UTF-8", Files.readString(Path.of("index.html")));
        });

        // Trang web gọi vào đây khi bấm Thanh toán.
        server.createContext("/api/payments", ex -> {
            Map<String, String> form = parseQuery(new String(ex.getRequestBody().readAllBytes(), UTF_8));
            long amount = Long.parseLong(form.getOrDefault("amount", "0"));
            send(ex, 200, "application/json", createPayment(amount,
                    form.getOrDefault("orderInfo", "Thanh toan don hang"), form.get("bankCode")));
        });

        // Trang kết quả hỏi trạng thái đơn. Còn PENDING thì hỏi VNPAY luôn.
        server.createContext("/api/orders", ex -> {
            Order o = ORDERS.get(parseQuery(ex.getRequestURI().getRawQuery()).get("txnRef"));
            if (o == null) { send(ex, 404, "application/json", "{}"); return; }
            reconcile(o);
            send(ex, 200, "application/json",
                    ("{\"txnRef\":\"%s\",\"amount\":%d,\"status\":\"%s\",\"transactionNo\":\"%s\"}")
                            .formatted(o.txnRef, o.amount, o.status, o.transactionNo));
        });

        // VNPAY gọi server sang server sau khi khách trả tiền.
        server.createContext("/vnpay/ipn", ex ->
                send(ex, 200, "application/json", handleIpn(parseQuery(ex.getRequestURI().getRawQuery()))));

        // Trình duyệt khách quay về. Chỉ kiểm chữ ký rồi chuyển sang trang kết quả.
        server.createContext("/vnpay/return", ex -> {
            Map<String, String> p = parseQuery(ex.getRequestURI().getRawQuery());
            String txnRef = p.getOrDefault("vnp_TxnRef", "");
            ex.getResponseHeaders().add("Location",
                    "/?txnRef=" + URLEncoder.encode(txnRef, ASCII) + "&valid=" + isValidSignature(p));
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });

        server.start();
        log("http://localhost:" + PORT);
        log(TMN_CODE.isEmpty() ? "Chưa điền TMN_CODE và HASH_SECRET, xem TODO 1." : "TmnCode " + TMN_CODE);
        log("ReturnUrl " + RETURN_URL);
    }

    static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    // Tách query string thành map, đã URL-decode giống servlet.
    static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i < 0) continue;
            map.put(URLDecoder.decode(pair.substring(0, i), UTF_8),
                    URLDecoder.decode(pair.substring(i + 1), UTF_8));
        }
        return map;
    }

    static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(UTF_8);
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    static void log(String m) {
        System.out.println("[" + LocalDateTime.now(VN).format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + m);
    }
}

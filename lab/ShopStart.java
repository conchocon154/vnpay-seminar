/*
 * java ShopStart.java   ->   http://localhost:8080
 * Sáu chỗ TODO là phần code trong buổi, code mẫu nằm trên slide.
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

public class ShopStart {

    static final java.nio.charset.Charset UTF_8 = StandardCharsets.UTF_8;
    static final java.nio.charset.Charset ASCII = StandardCharsets.US_ASCII;

    static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    static final String API_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    static final int PORT = 8080;

    // TODO 1. TmnCode và HashSecret, lấy trong email sandbox gửi về.
    static final String TMN_CODE    = "";
    static final String HASH_SECRET = "";

    // Đổi khi bật ngrok: export VNPAY_RETURN_URL=https://<id>.ngrok-free.app/vnpay/return
    static final String RETURN_URL = env("VNPAY_RETURN_URL", "http://localhost:" + PORT + "/vnpay/return");


    // TODO 2. Hash HMAC-SHA512, trả về hex lowercase.
    // key: HASH_SECRET. data: query string do buildQueryString ráp ra.
    static String hmacSHA512(String key, String data) {

        return "";
    }


    // TODO 3. Ráp params thành query string: sort alphabet, bỏ value rỗng, URL-encode value.
    // params: các tham số vnp_* sắp gửi đi, hoặc vừa nhận từ VNPAY.
    static String buildQueryString(Map<String, String> params) {

        return "";
    }


    // TODO 4. Verify vnp_SecureHash: bỏ nó ra, ký lại phần còn lại rồi so.
    // params: query param nhận ở /vnpay/ipn hoặc /vnpay/return.
    static boolean isValidSignature(Map<String, String> params) {

        return false;
    }


    // TODO 5. Lưu đơn PENDING, ráp tham số vnp_*, ký, trả JSON {txnRef, paymentUrl}.
    // amount, orderInfo, bankCode: ba ô nhập trên index.html. bankCode có thể rỗng.
    static String createPayment(long amount, String orderInfo, String bankCode) {

        return "{\"error\":\"Chưa làm TODO 5\"}";
    }


    // TODO 6. Xử lý IPN, cập nhật status đơn, trả JSON {RspCode, Message}.
    // params: query param VNPAY gửi sang. Mã trả về: 97, 01, 04, 02, 00.
    static String handleIpn(Map<String, String> params) {

        return rsp("99", "Chua lam TODO 6");
    }


    /* ===== phần dưới đã viết sẵn, không đụng tới ===== */

    // Đơn trong bộ nhớ. amount là số tiền thật, chưa nhân 100.
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

    // txnRef duy nhất trong 24h.
    static String newTxnRef(String createDate) {
        return createDate + ThreadLocalRandom.current().nextInt(100_000, 999_999);
    }

    static String rsp(String code, String message) {
        return "{\"RspCode\":\"" + code + "\",\"Message\":\"" + message + "\"}";
    }

    // Gọi API querydr hỏi VNPAY status thật, dùng khi IPN không về.
    static void reconcile(Order order) {
        if (!"PENDING".equals(order.status) || HASH_SECRET.isEmpty()) return;

        String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String now = LocalDateTime.now(VN).format(TIME);
        String info = "Truy van GD ma:" + order.txnRef;

        // hashData của querydr nối bằng '|', không sort alphabet.
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

    // Đọc một field trong JSON phẳng.
    static String jsonValue(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        int colon = json.indexOf(':', i), open = json.indexOf('"', colon + 1);
        int close = json.indexOf('"', open + 1);
        return (colon < 0 || open < 0 || close < 0) ? "" : json.substring(open + 1, close);
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Đọc index.html từ đĩa mỗi lần tải trang, sửa xong bấm F5 là thấy.
        server.createContext("/", ex -> {
            if (!"/".equals(ex.getRequestURI().getPath())) { send(ex, 404, "text/plain", "Not found"); return; }
            send(ex, 200, "text/html; charset=UTF-8", Files.readString(Path.of("index.html")));
        });

        // index.html gọi vào đây khi bấm Thanh toán.
        server.createContext("/api/payments", ex -> {
            Map<String, String> form = parseQuery(new String(ex.getRequestBody().readAllBytes(), UTF_8));
            long amount = Long.parseLong(form.getOrDefault("amount", "0"));
            send(ex, 200, "application/json", createPayment(amount,
                    form.getOrDefault("orderInfo", "Thanh toan don hang"), form.get("bankCode")));
        });

        // Trang kết quả hỏi status. Còn PENDING thì gọi querydr.
        server.createContext("/api/orders", ex -> {
            Order o = ORDERS.get(parseQuery(ex.getRequestURI().getRawQuery()).get("txnRef"));
            if (o == null) { send(ex, 404, "application/json", "{}"); return; }
            reconcile(o);
            send(ex, 200, "application/json",
                    ("{\"txnRef\":\"%s\",\"amount\":%d,\"status\":\"%s\",\"transactionNo\":\"%s\"}")
                            .formatted(o.txnRef, o.amount, o.status, o.transactionNo));
        });

        // IPN, VNPAY gọi server to server sau khi khách trả tiền.
        server.createContext("/vnpay/ipn", ex ->
                send(ex, 200, "application/json", handleIpn(parseQuery(ex.getRequestURI().getRawQuery()))));

        // ReturnURL, browser quay về. Chỉ verify rồi redirect, không ghi status.
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

    // Query string -> map, đã URL-decode.
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

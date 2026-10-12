import com.sun.net.httpserver.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** SOA seminar: hai service HTTP trong một JVM, trạng thái lưu RAM. */
public class SeminarDemo {
    static final boolean MOCK = !"sandbox".equals(System.getenv("VNPAY_MODE"));
    static final String TMN = MOCK ? "DEMOV210" : env("VNPAY_TMN_CODE");
    static final String SECRET = MOCK ? "local-mock-key" : env("VNPAY_HASH_SECRET");
    static final String TOKEN = UUID.randomUUID().toString();
    static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    static final String API_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    static final String RETURN = Optional.ofNullable(System.getenv("VNPAY_RETURN_URL")).orElse("http://localhost:8091/vnpay/return");
    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final Map<String,Order> ORDERS = new ConcurrentHashMap<>();
    static final Map<String,Payment> PAYMENTS = new ConcurrentHashMap<>();
    static final Map<String,String> MOCK_RESULTS = new ConcurrentHashMap<>();
    static class Order { final long amount = 25000; volatile String status = "PENDING"; }
    static class Payment {
        final String ref, date; final long amount; String status = "PENDING";
        Payment(String ref,long amount) { this.ref=ref; this.amount=amount; date=now(); }
    }
    interface Handler { void run(HttpExchange e) throws Exception; }
    static void route(HttpServer s,String path,String method,Handler h) {
        s.createContext(path,e -> {
            try {
                if(!path.equals(e.getRequestURI().getPath())) { send(e,404,"Not found"); return; }
                if(!method.equals(e.getRequestMethod())) { send(e,405,"Method not allowed"); return; }
                h.run(e);
            } catch(Exception ex) { System.err.println("Request failed: "+ex.getClass().getSimpleName()); sendSafe(e,500,"Request failed"); }
            finally { e.close(); }
        });
    }
    static boolean internal(HttpExchange e) throws Exception {
        if(TOKEN.equals(e.getRequestHeaders().getFirst("X-Demo-Token"))) return true;
        send(e,403,"Forbidden"); return false;
    }
    public static void main(String[] args) throws Exception {
        if(!MOCK && (TMN.isBlank() || SECRET.isBlank())) throw new IllegalStateException("Set sandbox credentials");
        HttpServer order=HttpServer.create(new InetSocketAddress("127.0.0.1",8090),0);
        HttpServer pay=HttpServer.create(new InetSocketAddress(MOCK ? "127.0.0.1" : "0.0.0.0",8091),0);
        order.setExecutor(Executors.newCachedThreadPool()); pay.setExecutor(Executors.newCachedThreadPool());
        route(order,"/","GET",e -> html(e,"<h1>VNPAY Seminar</h1><p>"+(MOCK?"LOCAL MOCK: khong ket noi VNPAY":"VNPAY SANDBOX")+"</p><p>Don hang mau: 25.000 VND</p><form method='post' action='/checkout'><button>Thanh toan</button></form>"));
        route(order,"/checkout","POST",e -> {
            String ref=UUID.randomUUID().toString().replace("-",""); ORDERS.put(ref,new Order());
            JSONObject r=post("http://127.0.0.1:8091/payments",new JSONObject().put("ref",ref).put("amount",25000));
            redirect(e,r.getString("url"));
        });
        route(order,"/internal/result","POST",e -> {
            if(!internal(e)) return; JSONObject b=body(e); Order o=ORDERS.get(b.getString("ref"));
            if(o==null || b.getLong("amount")!=o.amount) { send(e,400,"Order mismatch"); return; }
            synchronized(o) { if("PENDING".equals(o.status)) o.status=b.getString("status"); }
            json(e,new JSONObject().put("status",o.status));
        });
        route(order,"/result","GET",e -> {
            String ref=params(e).getOrDefault("ref",""); Order o=ORDERS.get(ref);
            if(o==null) { send(e,404,"Unknown order"); return; }
            html(e,"<h1>Ket qua don hang</h1><p>"+o.status+"</p><p>"+ref+"</p><form method='post' action='/verify'><input type='hidden' name='ref' value='"+ref+"'><button>Truy van giao dich</button></form><p><a href='/'>Tao don moi</a></p>");
        });
        route(order,"/verify","POST",e -> {
            String ref=parse(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).get("ref");
            post("http://127.0.0.1:8091/verify",new JSONObject().put("ref",ref)); redirect(e,"/result?ref="+enc(ref));
        });
        route(pay,"/payments","POST",e -> {
            if(!internal(e)) return; JSONObject b=body(e); Payment p=new Payment(b.getString("ref"),b.getLong("amount"));
            PAYMENTS.put(p.ref,p); json(e,new JSONObject().put("url",MOCK ? "http://localhost:8091/mock?ref="+enc(p.ref):paymentUrl(p)));
        });
        route(pay,"/vnpay/return","GET",e -> {
            Map<String,String> m=params(e); if(!valid(m)) { send(e,400,"Invalid signature"); return; }
            // ReturnURL chỉ chuyển trang, không cập nhật đơn hàng.
            redirect(e,"http://localhost:8090/result?ref="+enc(m.get("vnp_TxnRef")));
        });
        route(pay,"/vnpay/ipn","GET",e -> { JSONObject r=ipn(params(e)); System.out.println("IPN "+r); json(e,r); });
        route(pay,"/verify","POST",e -> {
            if(!internal(e)) return; Payment p=PAYMENTS.get(body(e).getString("ref"));
            if(p==null) { send(e,404,"Unknown payment"); return; }
            JSONObject r=query(p); boolean verified=verifyQuery(p,r);
            if(verified && "00".equals(r.optString("vnp_TransactionStatus"))) confirm(p,"PAID");
            // Trạng thái chưa kết thúc / mã khác giữ nguyên để đối soát sau.
            System.out.println("querydr verified="+verified+" local="+p.status);
            json(e,new JSONObject().put("verified",verified).put("status",p.status));
        });
        if(MOCK) {
            route(pay,"/mock","GET",e -> {
                String ref=params(e).get("ref"); if(!PAYMENTS.containsKey(ref)) { send(e,404,"Unknown payment"); return; }
                html(e,"<h1>Mo phong cong thanh toan</h1><p>Mock local, khong phai trang VNPAY</p><form method='post' action='/mock/finish'><input type='hidden' name='ref' value='"+ref+"'><button name='scenario' value='success'>Thanh cong + IPN</button><button name='scenario' value='delayed'>Thanh cong, chua gui IPN</button></form>");
            });
            route(pay,"/mock/finish","POST",e -> {
                Map<String,String> f=parse(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)); Payment p=PAYMENTS.get(f.get("ref"));
                if(p==null) { send(e,404,"Unknown payment"); return; } MOCK_RESULTS.put(p.ref,"00"); Map<String,String> m=callback(p);
                if("success".equals(f.get("scenario"))) {
                    String url="http://localhost:8091/vnpay/ipn?"+canonical(m);
                    System.out.println("First IPN "+get(url)); System.out.println("Duplicate IPN "+get(url));
                }
                redirect(e,"http://localhost:8091/vnpay/return?"+canonical(m));
            });
        }
        order.start(); pay.start(); System.out.println("Mode "+(MOCK?"LOCAL MOCK":"VNPAY SANDBOX")+" http://localhost:8090");
    }
    static String paymentUrl(Payment p) {
        Map<String,String> m=new TreeMap<>();
        m.put("vnp_Version","2.1.0"); m.put("vnp_Command","pay"); m.put("vnp_TmnCode",TMN);
        m.put("vnp_TxnRef",p.ref); m.put("vnp_Amount",Long.toString(p.amount*100)); m.put("vnp_CurrCode","VND");
        m.put("vnp_OrderInfo","Thanh toan don hang mau"); m.put("vnp_OrderType","other"); m.put("vnp_Locale","vn");
        m.put("vnp_ReturnUrl",RETURN); m.put("vnp_IpAddr","127.0.0.1"); m.put("vnp_CreateDate",p.date);
        m.put("vnp_ExpireDate",LocalDateTime.now(VN).plusMinutes(15).format(TIME));
        String data=canonical(m); return PAY_URL+"?"+data+"&vnp_SecureHash="+hmac(data);
    }
    static JSONObject ipn(Map<String,String> m) throws Exception {
        if(!valid(m) || !TMN.equals(m.get("vnp_TmnCode"))) return rsp("97","Invalid signature or merchant");
        Payment p=PAYMENTS.get(m.get("vnp_TxnRef")); if(p==null) return rsp("01","Order not found");
        if(!Long.toString(p.amount*100).equals(m.get("vnp_Amount"))) return rsp("04","Invalid amount");
        synchronized(p) {
            if(!"PENDING".equals(p.status)) return rsp("02","Already confirmed");
            boolean paid="00".equals(m.get("vnp_ResponseCode")) && "00".equals(m.get("vnp_TransactionStatus"));
            confirm(p,paid?"PAID":"FAILED"); return rsp("00","Confirm success");
        }
    }
    static void confirm(Payment p,String status) throws Exception {
        synchronized(p) {
            if(!"PENDING".equals(p.status)) return;
            // Order Service lỗi thì để PENDING, cho phép IPN thử lại.
            post("http://127.0.0.1:8090/internal/result",new JSONObject().put("ref",p.ref).put("amount",p.amount).put("status",status));
            p.status=status;
        }
    }
    static JSONObject query(Payment p) throws Exception {
        if(MOCK) {
            JSONObject r=new JSONObject().put("vnp_ResponseId","MOCK01").put("vnp_Command","querydr").put("vnp_ResponseCode","00")
                .put("vnp_Message","Mock result").put("vnp_TmnCode",TMN).put("vnp_TxnRef",p.ref).put("vnp_Amount",Long.toString(p.amount*100))
                .put("vnp_BankCode","NCB").put("vnp_PayDate",p.date).put("vnp_TransactionNo","123456").put("vnp_TransactionType","01")
                .put("vnp_TransactionStatus",MOCK_RESULTS.getOrDefault(p.ref,"01")).put("vnp_OrderInfo","Thanh toan don hang mau");
            return r.put("vnp_SecureHash",hmac(queryHash(r)));
        }
        JSONObject b=new JSONObject().put("vnp_RequestId",UUID.randomUUID().toString().replace("-",""))
            .put("vnp_Version","2.1.0").put("vnp_Command","querydr").put("vnp_TmnCode",TMN).put("vnp_TxnRef",p.ref)
            .put("vnp_TransactionDate",p.date).put("vnp_CreateDate",now()).put("vnp_IpAddr","127.0.0.1").put("vnp_OrderInfo","Truy van don hang mau");
        String data=join(b,"vnp_RequestId","vnp_Version","vnp_Command","vnp_TmnCode","vnp_TxnRef","vnp_TransactionDate","vnp_CreateDate","vnp_IpAddr","vnp_OrderInfo");
        return post(API_URL,b.put("vnp_SecureHash",hmac(data)));
    }
    static String queryHash(JSONObject r) {
        return join(r,"vnp_ResponseId","vnp_Command","vnp_ResponseCode","vnp_Message","vnp_TmnCode","vnp_TxnRef","vnp_Amount","vnp_BankCode","vnp_PayDate","vnp_TransactionNo","vnp_TransactionType","vnp_TransactionStatus","vnp_OrderInfo","vnp_PromotionCode","vnp_PromotionAmount");
    }
    static boolean verifyQuery(Payment p,JSONObject r) {
        return same(hmac(queryHash(r)),r.optString("vnp_SecureHash")) && "00".equals(r.optString("vnp_ResponseCode"))
            && TMN.equals(r.optString("vnp_TmnCode")) && p.ref.equals(r.optString("vnp_TxnRef"))
            && Long.toString(p.amount*100).equals(r.optString("vnp_Amount")) && "01".equals(r.optString("vnp_TransactionType"));
    }
    static Map<String,String> callback(Payment p) {
        Map<String,String> m=new TreeMap<>(); m.put("vnp_TmnCode",TMN); m.put("vnp_TxnRef",p.ref);
        m.put("vnp_Amount",Long.toString(p.amount*100)); m.put("vnp_ResponseCode","00"); m.put("vnp_TransactionStatus","00");
        m.put("vnp_TransactionNo","123456"); m.put("vnp_SecureHash",hmac(canonical(m))); return m;
    }
    static boolean valid(Map<String,String> input) {
        Map<String,String> m=new TreeMap<>(input); String signature=m.remove("vnp_SecureHash"); m.remove("vnp_SecureHashType");
        m.keySet().removeIf(k -> !k.startsWith("vnp_")); return same(hmac(canonical(m)),signature);
    }
    static String canonical(Map<String,String> m) {
        List<String> pairs=new ArrayList<>(); new TreeMap<>(m).forEach((k,v) -> { if(v!=null && !v.isEmpty()) pairs.add(enc(k)+"="+enc(v)); });
        return String.join("&",pairs);
    }
    static String hmac(String data) {
        try { Mac mac=Mac.getInstance("HmacSHA512"); mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    static boolean same(String a,String b) { return b!=null && MessageDigest.isEqual(a.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),b.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)); }
    static String join(JSONObject j,String...keys) { return String.join("|",Arrays.stream(keys).map(k -> j.optString(k,"")).toList()); }
    static JSONObject post(String url,JSONObject b) throws Exception {
        HttpRequest.Builder q=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json");
        if(url.startsWith("http://127.0.0.1:")) q.header("X-Demo-Token",TOKEN);
        HttpResponse<String> r=CLIENT.send(q.POST(HttpRequest.BodyPublishers.ofString(b.toString())).build(),HttpResponse.BodyHandlers.ofString());
        if(r.statusCode()!=200) throw new IllegalStateException("HTTP "+r.statusCode()); return new JSONObject(r.body());
    }
    static String get(String url) throws Exception { return CLIENT.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofString()).body(); }
    static JSONObject body(HttpExchange e) throws Exception { return new JSONObject(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)); }
    static Map<String,String> params(HttpExchange e) { return parse(e.getRequestURI().getRawQuery()); }
    static Map<String,String> parse(String s) {
        Map<String,String> m=new TreeMap<>(); if(s!=null) for(String pair:s.split("&")) {
            String[] kv=pair.split("=",2); if(kv.length==2) { String k=URLDecoder.decode(kv[0],StandardCharsets.UTF_8);
                if(m.put(k,URLDecoder.decode(kv[1],StandardCharsets.UTF_8))!=null) throw new IllegalArgumentException("Duplicate field"); }
        } return m;
    }
    static String enc(String s) { return URLEncoder.encode(s,StandardCharsets.UTF_8); }
    static String now() { return LocalDateTime.now(VN).format(TIME); }
    static String env(String n) { return Optional.ofNullable(System.getenv(n)).orElse(""); }
    static JSONObject rsp(String c,String m) { return new JSONObject().put("RspCode",c).put("Message",m); }
    static void json(HttpExchange e,JSONObject j) throws Exception { e.getResponseHeaders().set("Content-Type","application/json; charset=utf-8"); send(e,200,j.toString()); }
    static void html(HttpExchange e,String s) throws Exception { e.getResponseHeaders().set("Content-Type","text/html; charset=utf-8"); send(e,200,"<!doctype html><meta charset='utf-8'><style>body{font:20px system-ui;max-width:850px;margin:60px auto}button{font:inherit;padding:12px;margin:8px}p{overflow-wrap:anywhere}</style>"+s); }
    static void send(HttpExchange e,int c,String s) throws Exception { byte[] b=s.getBytes(StandardCharsets.UTF_8); e.sendResponseHeaders(c,b.length); e.getResponseBody().write(b); }
    static void sendSafe(HttpExchange e,int c,String s) { try { send(e,c,s); } catch(Exception ignored) {} }
    static void redirect(HttpExchange e,String url) throws Exception { e.getResponseHeaders().set("Location",url); e.sendResponseHeaders(303,-1); }
}

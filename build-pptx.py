from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN

INK   = RGBColor(0x16, 0x18, 0x1D)
MUTED = RGBColor(0x6B, 0x77, 0x88)
BLUE  = RGBColor(0x0A, 0x5B, 0xA8)
RED   = RGBColor(0xC4, 0x2B, 0x22)
CODEBG= RGBColor(0x11, 0x1A, 0x27)
CODEFG= RGBColor(0xE4, 0xEC, 0xF5)
LINE  = RGBColor(0xD8, 0xDE, 0xE6)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)

prs = Presentation()
prs.slide_width  = Inches(13.333)
prs.slide_height = Inches(7.5)
BLANK = prs.slide_layouts[6]

W = prs.slide_width
MARGIN = Inches(0.62)
CW = W - 2 * MARGIN


Y = Inches(0.45)
GAP = Inches(0.13)


def slide():
    global Y
    Y = Inches(0.45)
    s = prs.slides.add_slide(BLANK)
    bg = s.background.fill
    bg.solid()
    bg.fore_color.rgb = WHITE
    return s


def textbox(s, left, top, width, height):
    tb = s.shapes.add_textbox(left, top, width, height)
    tf = tb.text_frame
    tf.word_wrap = True
    return tf


def para(tf, text, size=16, bold=False, color=INK, space_after=6, font="Arial", first=False):
    p = tf.paragraphs[0] if first else tf.add_paragraph()
    p.text = text
    p.space_after = Pt(space_after)
    for r in p.runs:
        r.font.size = Pt(size)
        r.font.bold = bold
        r.font.color.rgb = color
        r.font.name = font
    return p


def title(s, eyebrow, text):
    global Y
    h = Pt(12 * 1.4) + Pt(32 * 1.25) + Inches(0.1)
    tf = textbox(s, MARGIN, Y, CW, h)
    para(tf, eyebrow.upper(), size=12, bold=True, color=BLUE, space_after=4, first=True)
    para(tf, text, size=32, bold=True, color=INK, space_after=0)
    Y = Y + h + Inches(0.22)


import math


def wrapped(lines, size, width_in=12.1):
    """Ước lượng số dòng sau khi xuống dòng tự động."""
    per_line = max(20, int(width_in * 96 / (size * 0.53)))
    return sum(max(1, math.ceil(len(t) / per_line)) for t in lines)


def body(s, lines, top=None, size=16):
    global Y
    h = Pt(size * 1.35) * wrapped(lines, size) + Pt(7) * len(lines) + Inches(0.12)
    tf = textbox(s, MARGIN, Y, CW, h)
    Y = Y + h + GAP
    for i, t in enumerate(lines):
        para(tf, t, size=size, color=INK, space_after=7, first=(i == 0))
    return tf


def code(s, lines, top=None, height=None, size=13):
    global Y
    n = len(lines)
    h = height or Pt(size * 1.34) * n + Inches(0.26)
    box = s.shapes.add_textbox(MARGIN, Y, CW, h)
    Y = Y + h + GAP
    box.fill.solid()
    box.fill.fore_color.rgb = CODEBG
    box.line.color.rgb = CODEBG
    tf = box.text_frame
    tf.word_wrap = False
    tf.margin_left = Inches(0.22)
    tf.margin_right = Inches(0.16)
    tf.margin_top = Inches(0.11)
    tf.margin_bottom = Inches(0.09)
    for i, t in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.text = t
        p.space_after = Pt(0)
        for r in p.runs:
            r.font.size = Pt(size)
            r.font.name = "Roboto Mono"
            r.font.color.rgb = CODEFG
    return box


def note(s, head, lines, top=None, accent=RED):
    global Y
    h = Pt(15 * 1.3) * (wrapped(lines, 14, 11.8) + 1) + Inches(0.26)
    box = s.shapes.add_textbox(MARGIN, Y, CW, h)
    Y = Y + h + GAP
    box.fill.solid()
    box.fill.fore_color.rgb = RGBColor(0xF7, 0xF8, 0xFA)
    box.line.color.rgb = LINE
    tf = box.text_frame
    tf.word_wrap = True
    tf.margin_left = Inches(0.2)
    tf.margin_top = Inches(0.12)
    para(tf, head, size=15, bold=True, color=accent, space_after=3, first=True)
    for t in lines:
        para(tf, t, size=14, color=INK, space_after=3)
    return box


def filetag(s, path, top=None):
    global Y
    h = Inches(0.24)
    tf = textbox(s, MARGIN, Y, CW, h)
    para(tf, path, size=12, color=MUTED, font="Roboto Mono", space_after=0, first=True)
    Y = Y + h + Inches(0.04)


# ---------------------------------------------------------------- slides

s = slide()
tf = textbox(s, MARGIN, Inches(2.1), CW, Inches(2.6))
para(tf, "SEMINAR", size=13, bold=True, color=BLUE, space_after=8, first=True)
para(tf, "Nhúng VNPAY vào một cửa hàng có sẵn", size=40, bold=True, color=INK, space_after=14)
para(tf, "Mở file ShopStart.java đã tải trước. Hôm nay mình điền 6 chỗ TODO trong đó.",
     size=18, color=MUTED, space_after=6)
para(tf, "Cần JDK 17 trở lên. Không cần Maven, không cần cài thư viện nào.",
     size=18, color=MUTED, space_after=0)

s = slide()
title(s, "Vấn đề", "Tại sao phải qua cổng thanh toán")
body(s, [
    "Lưu số thẻ trên server của mình là vi phạm PCI-DSS, và ngân hàng không cấp API trực tiếp cho đồ án.",
    "VNPAY đảo ngược luồng: khách nhập thẻ trên trang của VNPAY, không phải trang của mình.",
    "Server mình chỉ trao đổi chữ ký, không bao giờ thấy số thẻ.",
])
note(s, "Câu chốt cho cả buổi", [
    "VNPAY không có SDK. Nó chỉ là redirect cộng một chữ ký HMAC-SHA512.",
    "Nắm được chữ ký là xong 90% bài toán.",
], Inches(3.5), accent=BLUE)

s = slide()
title(s, "Kiến trúc", "Bốn đường đi, chỉ hai đường được tin")
code(s, [
    "1. Browser -> Server mình    tạo đơn PENDING, ký tham số, trả về paymentUrl",
    "",
    "2. VNPAY   -> Server mình    IPN, gọi thẳng server. TIN ĐƯỢC, cộng tiền ở đây",
    "",
    "3. VNPAY   -> Browser        ReturnURL. KHÔNG TIN, chỉ để vẽ màn hình",
    "",
    "4. Server mình -> VNPAY      querydr, hỏi lại trạng thái. TIN ĐƯỢC",
], Inches(1.85))
note(s, "Hỏi cả lớp trước khi sang slide sau", [
    '"Sao không cộng tiền luôn ở ReturnURL cho nhanh?"',
    "Vì đó là URL trên trình duyệt của khách. Sửa vnp_ResponseCode=00 là mua hàng miễn phí.",
    "Và khách tắt tab ngay sau khi trả tiền thì ReturnURL không bao giờ về.",
], Inches(4.6))

s = slide()
title(s, "Lộ trình", "Sáu chỗ trống trong ShopStart.java")
code(s, [
    "TODO 1   Cấu hình TmnCode và HashSecret                    2 phút",
    "TODO 2   Hàm ký HMAC-SHA512                                6 phút",
    "TODO 3   Hàm verify chữ ký                                 3 phút",
    "TODO 4   Tạo URL thanh toán                                5 phút",
    "TODO 5   Nhận IPN                                          5 phút",
    "TODO 6   ReturnURL và chốt trạng thái đơn                  4 phút",
], Inches(1.95), size=15)
body(s, [
    "Phần đơn hàng, giao diện và server đã viết sẵn trong file, mình không đụng tới.",
    "Ai gõ không kịp cứ ngồi xem, cuối buổi mình gửi bản làm xong.",
], top=Inches(4.5))

s = slide()
title(s, "TODO 1", "Cấu hình")
filetag(s, "ShopStart.java", Inches(1.62))
code(s, [
    'static final String TMN_CODE    = env("VNPAY_TMN_CODE", "CHANGE_ME");',
    'static final String HASH_SECRET = env("VNPAY_HASH_SECRET", "CHANGE_ME");',
    'static final String RETURN_URL  = env("VNPAY_RETURN_URL",',
    '                                      "http://localhost:8080/vnpay/return");',
    '',
    'static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";',
    'static final String API_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";',
], Inches(1.95))
body(s, ["Chạy app thì truyền qua biến môi trường:"], top=Inches(3.85))
code(s, [
    "export VNPAY_TMN_CODE=xxxxxxxx",
    "export VNPAY_HASH_SECRET=xxxxxxxxxxxxxxxx",
    "java ShopStart.java",
], Inches(4.35))
note(s, "Đừng hardcode secret rồi push lên GitHub", [
    "Ai clone repo cũng ký được đơn thay bạn. GitHub có bot quét chuyện này.",
], Inches(5.9))

s = slide()
title(s, "TODO 2", "Hàm ký, phần khó nhất")
body(s, ["Ba bước: sort alphabet, URL-encode giá trị, rồi HMAC-SHA512 ra hex chữ thường."])
filetag(s, "ShopStart.java", Inches(2.22))
code(s, [
    "static String hmacSHA512(String secretKey, String data) {",
    "    try {",
    '        Mac mac = Mac.getInstance("HmacSHA512");',
    '        mac.init(new SecretKeySpec(secretKey.getBytes(UTF_8), "HmacSHA512"));',
    "        byte[] bytes = mac.doFinal(data.getBytes(UTF_8));",
    "",
    "        StringBuilder hex = new StringBuilder(bytes.length * 2);",
    '        for (byte b : bytes) hex.append(String.format("%02x", b));',
    "        return hex.toString();                      // hex chữ thường",
    "    } catch (Exception e) {",
    '        throw new IllegalStateException("Không tạo được HMAC-SHA512", e);',
    "    }",
    "}",
], Inches(2.55))

s = slide()
title(s, "TODO 2", "Ráp chuỗi để đem đi băm")
filetag(s, "ShopStart.java", Inches(1.62))
code(s, [
    "static String buildQueryString(Map<String, String> params) {",
    "    StringBuilder sb = new StringBuilder();",
    "",
    "    for (var e : new TreeMap<>(params).entrySet()) {          // 1. sort alphabet",
    "        if (e.getValue() == null || e.getValue().isEmpty()) continue;",
    "        if (sb.length() > 0) sb.append('&');",
    "",
    "        sb.append(URLEncoder.encode(e.getKey(), US_ASCII)).append('=')",
    "          .append(URLEncoder.encode(e.getValue(), US_ASCII));  // 2. encode giá trị",
    "    }",
    "    return sb.toString();",
    "}",
], Inches(1.95))
note(s, "Đây là chỗ 90% người mới bị sai chữ ký", [
    "Quên encode GIÁ TRỊ. vnp_OrderInfo có dấu cách là hỏng ngay.",
    "Để lọt tham số rỗng vào chuỗi. VNPAY bỏ qua chúng, mình giữ lại là lệch.",
], Inches(5.35))

s = slide()
title(s, "TODO 3", "Verify chữ ký khi VNPAY gọi về")
body(s, ["Dùng lại đúng hàm vừa viết, thêm một việc: bỏ chính chữ ký ra trước khi băm."])
filetag(s, "ShopStart.java", Inches(2.22))
code(s, [
    "static boolean isValidSignature(Map<String, String> params) {",
    '    String received = params.get("vnp_SecureHash");',
    "    if (received == null || received.isBlank()) return false;",
    "",
    "    Map<String, String> clone = new TreeMap<>(params);",
    '    clone.remove("vnp_SecureHash");',
    '    clone.remove("vnp_SecureHashType");',
    "",
    "    String expected = hmacSHA512(HASH_SECRET, buildQueryString(clone));",
    "    return constantTimeEquals(expected, received);",
    "}",
], Inches(2.55))
note(s, "Cách debug khi bí", [
    "In buildQueryString(clone) ra rồi so từng ký tự với query string trên thanh địa chỉ.",
], Inches(5.55), accent=BLUE)

s = slide()
title(s, "TODO 4", "Ráp tham số và tạo URL")
code(s, [
    "vnp_Amount       2500000                 nhân 100, số nguyên. 25.000đ -> 2500000",
    "vnp_TxnRef       20260920125606777443    duy nhất trong 24h theo TmnCode",
    "vnp_CreateDate   20260920125606          giờ GMT+7, không phải giờ máy chủ",
    "vnp_ReturnUrl    https://....ngrok...    phải khớp URL đã khai",
    "vnp_BankCode     NCB                     bỏ trống thì VNPAY hiện trang chọn",
], Inches(1.75), size=13)
filetag(s, "ShopStart.java", Inches(3.35))
code(s, [
    'p.put("vnp_Amount", String.valueOf(amount * 100));',
    'p.put("vnp_CreateDate", now.format(VNP_TIME));',
    'p.put("vnp_ExpireDate", now.plusMinutes(15).format(VNP_TIME));',
    "",
    "String query = buildQueryString(p);",
    'String url = PAY_URL + "?" + query + "&vnp_SecureHash=" + hmacSHA512(HASH_SECRET, query);',
], Inches(3.68))
note(s, "Cái bẫy nằm ngay trong code mẫu của chính VNPAY", [
    'Họ dùng Etc/GMT+7, mà theo POSIX nó là UTC trừ 7, lệch 14 tiếng.',
    'Giao dịch hết hạn ngay lúc vừa tạo. Mình dùng ZoneId.of("Asia/Ho_Chi_Minh").',
])

s = slide()
title(s, "Dừng lại kiểm tra", "Chạy thử đã, đừng viết tiếp")
code(s, [
    "export VNPAY_TMN_CODE=... VNPAY_HASH_SECRET=...",
    "java ShopStart.java",
    "",
    "# mở http://localhost:8080, bấm Mua ngay rồi Thanh toán",
], Inches(1.85))
note(s, "Hiện ra form nhập thẻ NCB", [
    "Chữ ký đúng rồi. Đi tiếp phần ngrok.",
], Inches(3.4), accent=RGBColor(0x0B, 0x6B, 0x47))
note(s, "Báo Chữ ký không hợp lệ", [
    "Sai HashSecret, hoặc quên encode ở TODO 2.",
    "Báo website không tồn tại thì là sai TmnCode, hoặc vnp_ReturnUrl không khớp domain đã khai.",
], Inches(4.5))

s = slide()
title(s, "Bắt buộc", "ngrok, để VNPAY với tới máy mình")
body(s, [
    "IPN là cuộc gọi từ server VNPAY vào server mình. Máy ở localhost thì từ internet không ai thấy.",
])
code(s, [
    "ngrok http 8080",
    "",
    "Forwarding   https://a1b2-42-115-242-109.ngrok-free.app -> http://localhost:8080",
], Inches(2.5))
body(s, ["Khai 2 URL vào sandbox.vnpayment.vn/merchantv2, mục Cấu hình, Thông tin website:"],
     top=Inches(3.65))
code(s, [
    "URL trả về             https://a1b2-....ngrok-free.app/vnpay/return",
    "URL nhận kết quả IPN   https://a1b2-....ngrok-free.app/vnpay/ipn",
], Inches(4.15))
note(s, "Hai chuyện của bản ngrok miễn phí", [
    "Địa chỉ đổi mỗi lần chạy lại, phải khai lại trong portal. Làm sát giờ demo.",
    "Lần đầu mở bằng trình duyệt gặp trang cảnh báo, bấm Visit Site là qua. IPN không dính.",
], Inches(5.35))

s = slide()
title(s, "TODO 5", "Nhận IPN, nơi duy nhất được cộng tiền")
body(s, ["VNPAY retry tới khi nhận RspCode 00, nên hàm này gọi mấy lần cũng chỉ cộng tiền một lần."])
code(s, [
    "if (!isValidSignature(params))",
    '    return rsp("97", "Invalid Checksum");',
    "",
    'Order order = ORDERS.get(params.get("vnp_TxnRef"));',
    'if (order == null)              return rsp("01", "Order not Found");',
    "",
    'if (Long.parseLong(params.get("vnp_Amount")) != order.amount * 100)',
    '    return rsp("04", "Invalid Amount");        // chống sửa số tiền trên URL',
    "synchronized (order) {",
    '    if (!"PENDING".equals(order.status))',
    '        return rsp("02", "Order already confirmed");',
    "",
    '    boolean paid = "00".equals(params.get("vnp_ResponseCode"))',
    '                && "00".equals(params.get("vnp_TransactionStatus"));',
    '    order.status = paid ? "PAID" : "FAILED";',
    "}",
    'return rsp("00", "Confirm Success");           // VNPAY ngừng retry',
], Inches(2.25), size=12.5)

s = slide()
title(s, "TODO 6", "ReturnURL chỉ để vẽ màn hình")
filetag(s, "ShopStart.java", Inches(1.62))
code(s, [
    "static String handleReturn(Map<String, String> params) {",
    "    boolean valid = isValidSignature(params);",
    '    String txnRef = params.getOrDefault("vnp_TxnRef", "");',
    "",
    '    return "/?txnRef=" + encode(txnRef) + "&valid=" + valid;',
    "}",
], Inches(1.95))
body(s, ["Để ý cái KHÔNG có ở đây: không dòng nào đổi trạng thái đơn hàng.",
         "Tham số trên trình duyệt là dữ liệu do khách mang về, không phải sự thật."])
filetag(s, "trang kết quả, phía trình duyệt", Inches(3.95))
code(s, [
    "// không đọc kết quả từ URL, hỏi lại server cho chắc",
    "const res   = await fetch('/api/orders?txnRef=' + txnRef);",
    "const order = await res.json();",
    "",
    "if (order.status === 'PAID') showSuccess(order);",
], Inches(4.28))
s = slide()
title(s, "Tình huống thật", "Khi IPN không bao giờ về")
note(s, "Chuyện xảy ra lúc mình dựng bài này", [
    "Portal sandbox không cho khai IPN URL. Danh sách website trống trơn,",
    'Cài đặt thông báo báo "Kết nối hệ thống tạm thời bị gián đoạn".',
    "Code đúng hết mà IPN không tới. Chỉ biết mỗi IPN thì demo chết tại chỗ.",
], Inches(1.75))
body(s, [
    "Đường lùi là API querydr: server tự hỏi VNPAY trạng thái thật.",
    "Vẫn an toàn như IPN vì đủ ba lớp: hỏi thẳng VNPAY, verify chữ ký response, so lại số tiền.",
], top=Inches(3.5))
code(s, [
    "// Hash của querydr không sort alphabet, nối bằng '|' đúng thứ tự tài liệu",
    'String hashData = String.join("|",',
    '        requestId, "2.1.0", "querydr", TMN_CODE,',
    "        txnRef, transactionDate, createDate, ipAddr, orderInfo);",
], Inches(4.5))
note(s, "Mang về dùng cho đồ án", [
    "Một job @Scheduled quét đơn PENDING quá hạn rồi gọi querydr.",
    "Hệ thống thanh toán thật nào cũng có, vì IPN thất lạc là chuyện bình thường.",
], Inches(5.85), accent=RGBColor(0x0B, 0x6B, 0x47))

s = slide()
title(s, "Demo", "Quẹt thẻ thật trên sandbox")
code(s, [
    "Số thẻ          9704198526191432198",
    "Tên chủ thẻ     NGUYEN VAN A",
    "Ngày phát hành  07/15",
    "OTP             123456",
], Inches(1.85))
body(s, ["Log server lúc chốt đơn, nên mở sẵn cửa sổ terminal khi demo:"], top=Inches(3.5))
code(s, [
    "Chot bang querydr: txnRef=20260920125606777443 status=PAID transactionNo=15683165",
], Inches(3.95))
note(s, "Phòng khi wifi trục trặc", [
    "demo-local.sh tự ký IPN bằng chính HashSecret, diễn được cả ba ca:",
    "thành công ra 00, retry ra 02, bị sửa số tiền ra 97. Không cần internet, không cần bấm thẻ.",
], Inches(4.75), accent=BLUE)

s = slide()
title(s, "Đưa vào đồ án", "Từ file demo sang microservice")
code(s, [
    "payment-service đứng riêng, service khác chỉ nghe event OrderPaid",
    "",
    "HashSecret nạp từ Vault hoặc K8s Secret, không để trong application.yml",
    "",
    "Bảng payment_transaction có UNIQUE(txn_ref) và optimistic lock",
    "(demo dùng synchronized, chạy nhiều instance là không đủ)",
    "",
    "Nhiều cổng thanh toán thì bọc sau interface PaymentGateway",
], Inches(1.9), size=15)
body(s, [
    "Đồ án nào dùng được ngay: bất kỳ chỗ nào có đặt hàng hoặc nạp tiền.",
    "Bán hàng online, đặt vé, đặt sân, đóng học phí, ví điện tử, quyên góp.",
    "Việc phải làm chỉ là thay chỗ lưu đơn hàng bằng repository của mình.",
], top=Inches(4.6))

s = slide()
title(s, "Tổng kết", "Sáu điều nên nhớ")
code(s, [
    "1. Chỉ tin IPN và querydr, hai kênh server gọi server",
    "2. So lại số tiền với đơn trong DB trước khi đánh dấu PAID",
    "3. Idempotent bằng trạng thái đơn, đã xử lý thì trả 02",
    "4. vnp_TxnRef duy nhất trong 24h, thanh toán lại phải sinh mã mới",
    '5. Giờ Asia/Ho_Chi_Minh, đừng dùng Etc/GMT+7',
    "6. Không log secret, không log chữ ký",
], Inches(1.85), size=15)
note(s, "Nếu chỉ nhớ được một câu", [
    "Tham số trên trình duyệt là dữ liệu do khách mang về, không phải sự thật.",
], Inches(4.35), accent=BLUE)
body(s, [
    "Giới hạn: bắt buộc redirect nên app mobile phải nhúng WebView. Sandbox không giống",
    "production 100%. Hoàn tiền phải gọi API refund rồi đối soát tay. ngrok free đổi địa chỉ mỗi lần chạy.",
], top=Inches(5.5), size=14)

s = slide()
title(s, "Hỏi đáp", "Mấy câu chắc sẽ bị hỏi")
body(s, [
    "IPN không về thì đơn treo mãi à?",
    "Không. Job quét đơn PENDING quá hạn rồi gọi querydr để chốt.",
    "",
    "Sao phải cài ngrok, không có cách nào khác?",
    "Cần một địa chỉ công khai để VNPAY gọi vào. Deploy lên server thật cũng được, nhưng chậm hơn.",
    "",
    "Báo sai chữ ký mà không hiểu vì sao?",
    "In hashData ra rồi so từng ký tự. Gần như luôn là quên URL-encode, hoặc quên bỏ vnp_SecureHash.",
    "",
    "Test được không nếu chưa có thẻ, chưa có mạng?",
    "Được, phần ký và verify có unit test chạy offline hoàn toàn.",
], top=Inches(1.8), size=15)

s = slide()
title(s, "Bàn giao", "Mang về dùng cho đồ án")
body(s, ["Sau buổi hôm nay mình bỏ bản làm xong vào đúng thư mục Drive lúc nãy."])
code(s, [
    "VnpayDemo.java      bản đã điền xong 6 TODO",
    "  Bước 4 và 5       hàm ký và verify chữ ký",
    "  Bước 8            đối soát khi IPN không về",
    "  VNPAY_MODE        chạy có ngrok hay không ngrok",
], Inches(2.5), size=15)
note(s, "Ba bước để nhét vào đồ án của bạn", [
    "1. Copy hai hàm hmacSHA512 và buildQueryString sang project",
    "2. Thay chỗ lưu đơn hàng bằng repository sẵn có của bạn",
    "3. Nạp TmnCode và HashSecret từ biến môi trường, đừng hardcode",
], Inches(4.35), accent=RGBColor(0x0B, 0x6B, 0x47))
tf = textbox(s, MARGIN, Y, CW, Inches(0.5))
para(tf, "Có gì không chạy cứ nhắn mình.", size=17, color=MUTED, first=True)

out = "/Users/minhdang_work/vnpay-seminar/VNPAY-seminar.pptx"
prs.save(out)
print("slides:", len(prs.slides.__iter__.__self__._sldIdLst))
print("saved:", out)

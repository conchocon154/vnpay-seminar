"""Dựng bộ slide seminar VNPAY.

Phong cách Tech Modern: nền sáng, khối code nền tối, card bo góc, nhấn xanh.
Sửa nội dung trong phần SLIDES ở cuối file rồi chạy lại là xong.
"""
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR
import math

# bảng màu
INK    = RGBColor(0x11, 0x11, 0x11)
INK2   = RGBColor(0x3F, 0x45, 0x4D)
MUTED  = RGBColor(0x6B, 0x72, 0x80)
ACCENT = RGBColor(0x00, 0x66, 0xFF)
BG     = RGBColor(0xF8, 0xF9, 0xFA)
CARD   = RGBColor(0xFF, 0xFF, 0xFF)
LINE   = RGBColor(0xE3, 0xE6, 0xEA)
CODEBG = RGBColor(0x0C, 0x13, 0x1E)
CODEFG = RGBColor(0xE6, 0xED, 0xF7)
CODEDIM= RGBColor(0x7D, 0x8C, 0xA3)
OK     = RGBColor(0x0B, 0x6B, 0x47)
WARN   = RGBColor(0xC4, 0x2B, 0x22)
TINT   = RGBColor(0xEC, 0xF2, 0xFF)

FONT = "Be Vietnam Pro"
MONO = "Roboto Mono"

prs = Presentation()
prs.slide_width  = Inches(13.333)
prs.slide_height = Inches(7.5)
BLANK = prs.slide_layouts[6]

MARGIN = Inches(0.72)
CW = prs.slide_width - 2 * MARGIN
H = prs.slide_height
Y = Inches(0.5)
GAP = Inches(0.2)


def slide():
    global Y
    Y = Inches(0.5)
    s = prs.slides.add_slide(BLANK)
    s.background.fill.solid()
    s.background.fill.fore_color.rgb = BG
    return s


def _tf(shape):
    tf = shape.text_frame
    tf.word_wrap = True
    return tf


def _p(tf, text, size, bold=False, color=INK, font=FONT, space=4, first=False, align=None):
    p = tf.paragraphs[0] if first else tf.add_paragraph()
    p.text = text
    p.space_after = Pt(space)
    p.line_spacing = 1.18
    if align is not None:
        p.alignment = align
    for r in p.runs:
        r.font.size = Pt(size)
        r.font.bold = bold
        r.font.color.rgb = color
        r.font.name = font
    return p


def _rounded(s, left, top, width, height, fill, line=None, radius=0.035):
    sh = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, left, top, width, height)
    sh.adjustments[0] = radius
    sh.fill.solid()
    sh.fill.fore_color.rgb = fill
    if line is None:
        sh.line.fill.background()
    else:
        sh.line.color.rgb = line
        sh.line.width = Pt(1)
    sh.shadow.inherit = False
    return sh


def wrapped(lines, size, width_in):
    per = max(16, int(width_in * 96 / (size * 0.52)))
    return sum(max(1, math.ceil(len(t) / per)) for t in lines)


def title(s, eyebrow, action):
    """Tiêu đề mang tính kết luận, không quá 10 từ."""
    global Y
    assert len(action.split()) <= 10, f"tiêu đề dài quá 10 từ: {action}"
    box = s.shapes.add_textbox(MARGIN, Y, CW, Inches(1.18))
    tf = _tf(box)
    _p(tf, eyebrow.upper(), 11.5, bold=True, color=ACCENT, space=5, first=True)
    _p(tf, action, 30, bold=True, color=INK, space=0)
    Y = Y + Inches(1.18) + Inches(0.2)


def lead(s, text, size=15.5):
    global Y
    h = Pt(size * 1.35) * wrapped([text], size, 11.6) + Inches(0.1)
    tf = _tf(s.shapes.add_textbox(MARGIN, Y, CW, h))
    _p(tf, text, size, color=INK2, first=True)
    Y = Y + h + GAP


def cards(s, items, height=None):
    """items: list (tiêu đề, nội dung). Dùng thay cho gạch đầu dòng."""
    global Y
    n = len(items)
    gap = Inches(0.22)
    w = int((CW - gap * (n - 1)) / n)
    wi = w / 914400
    h = height or max(
        Pt(14 * 1.35) * wrapped([b for _, b in items], 14, wi - 0.5) + Inches(0.78)
        for _, b in items)
    for i, (head, body) in enumerate(items):
        left = MARGIN + i * (w + gap)
        _rounded(s, left, Y, w, h, CARD, LINE)
        box = s.shapes.add_textbox(left + Inches(0.22), Y + Inches(0.16),
                                   w - Inches(0.44), h - Inches(0.3))
        tf = _tf(box)
        _p(tf, head, 15, bold=True, color=INK, space=5, first=True)
        _p(tf, body, 13.5, color=INK2, space=0)
    Y = Y + h + GAP


def table(s, headers, rows, widths):
    """Bảng so sánh. widths là tỉ lệ cột."""
    global Y
    nrow, ncol = len(rows) + 1, len(headers)
    h = Inches(0.42) + Inches(0.38) * len(rows)
    shape = s.shapes.add_table(nrow, ncol, MARGIN, Y, CW, h)
    tbl = shape.table
    total = sum(widths)
    for c, wgt in enumerate(widths):
        tbl.columns[c].width = int(CW * wgt / total)
    tbl.rows[0].height = Inches(0.42)
    for r in range(1, nrow):
        tbl.rows[r].height = Inches(0.38)
    for c, head in enumerate(headers):
        cell = tbl.cell(0, c)
        cell.fill.solid(); cell.fill.fore_color.rgb = TINT
        cell.vertical_anchor = MSO_ANCHOR.MIDDLE
        cell.margin_left = Inches(0.14)
        _p(_tf(cell), head, 12, bold=True, color=ACCENT, space=0, first=True)
    for r, row in enumerate(rows, start=1):
        for c, val in enumerate(row):
            cell = tbl.cell(r, c)
            cell.fill.solid(); cell.fill.fore_color.rgb = CARD
            cell.vertical_anchor = MSO_ANCHOR.MIDDLE
            cell.margin_left = Inches(0.14)
            mono = val.startswith("`") and val.endswith("`")
            _p(_tf(cell), val.strip("`"), 12.5, color=INK if c == 0 else INK2,
               font=MONO if mono else FONT, space=0, first=True)
    Y = Y + h + GAP


def tiers(s, items):
    """Sơ đồ phân tầng: mỗi dòng là một chặng, có số thứ tự và nhãn kết luận."""
    global Y
    rh = Inches(0.72)
    for i, (num, lane, text, verdict) in enumerate(items):
        top = Y + i * (rh + Inches(0.1))
        strong = verdict is not None and verdict[0] == "ok"
        _rounded(s, MARGIN, top, CW, rh, TINT if strong else CARD, LINE)
        chip = _rounded(s, MARGIN + Inches(0.18), top + Inches(0.17),
                        Inches(0.42), Inches(0.38), ACCENT if strong else RGBColor(0xDD, 0xE3, 0xEA))
        _p(_tf(chip), num, 13, bold=True,
           color=RGBColor(0xFF, 0xFF, 0xFF) if strong else INK2, font=MONO, space=0, first=True)
        tb = s.shapes.add_textbox(MARGIN + Inches(0.78), top + Inches(0.09),
                                  Inches(2.0), Inches(0.55))
        _p(_tf(tb), lane, 11, bold=True, color=MUTED, space=0, first=True)
        tw = CW - Inches(3.0) - (Inches(1.5) if verdict else Inches(0))
        tb2 = s.shapes.add_textbox(MARGIN + Inches(2.85), top + Inches(0.17), tw, Inches(0.5))
        _p(_tf(tb2), text, 13.5, color=INK, space=0, first=True)
        if verdict:
            kind, label = verdict
            pill = _rounded(s, MARGIN + CW - Inches(1.65), top + Inches(0.19),
                            Inches(1.42), Inches(0.34),
                            RGBColor(0xDE, 0xF3, 0xE8) if kind == "ok" else RGBColor(0xFD, 0xE8, 0xE6))
            _p(_tf(pill), label, 11.5, bold=True, color=OK if kind == "ok" else WARN,
               space=0, first=True)
    Y = Y + len(items) * (rh + Inches(0.1)) + GAP


def code(s, lines, size=12.5, path=None):
    global Y
    if path:
        tb = s.shapes.add_textbox(MARGIN, Y, CW, Inches(0.24))
        _p(_tf(tb), path, 11, color=MUTED, font=MONO, space=0, first=True)
        Y = Y + Inches(0.26)
    h = Pt(size * 1.34) * len(lines) + Inches(0.3)
    box = _rounded(s, MARGIN, Y, CW, h, CODEBG)
    tf = box.text_frame
    tf.word_wrap = False
    tf.margin_left = Inches(0.26); tf.margin_top = Inches(0.13)
    tf.margin_right = Inches(0.16); tf.margin_bottom = Inches(0.1)
    for i, t in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.text = t
        p.space_after = Pt(0)
        p.line_spacing = 1.34
        for r in p.runs:
            r.font.size = Pt(size)
            r.font.name = MONO
            r.font.color.rgb = CODEDIM if t.strip().startswith(("//", "#")) else CODEFG
    Y = Y + h + GAP


def callout(s, head, body, kind="warn"):
    global Y
    color = {"warn": WARN, "ok": OK, "info": ACCENT}[kind]
    h = Pt(13.5 * 1.35) * wrapped([body], 13.5, 11.2) + Inches(0.62)
    _rounded(s, MARGIN, Y, CW, h, CARD, LINE)
    bar = s.shapes.add_shape(MSO_SHAPE.RECTANGLE, MARGIN, Y + Inches(0.06), Pt(3.2), h - Inches(0.12))
    bar.fill.solid(); bar.fill.fore_color.rgb = color
    bar.line.fill.background(); bar.shadow.inherit = False
    tb = s.shapes.add_textbox(MARGIN + Inches(0.26), Y + Inches(0.13), CW - Inches(0.5), h - Inches(0.26))
    tf = _tf(tb)
    _p(tf, head, 14, bold=True, color=color, space=4, first=True)
    _p(tf, body, 13.5, color=INK2, space=0)
    Y = Y + h + GAP


def picture(s, path, height_in, caption=None):
    global Y
    pic = s.shapes.add_picture(path, MARGIN, Y, height=Inches(height_in))
    pic.left = int(MARGIN + (CW - pic.width) / 2)
    Y = Y + pic.height + Inches(0.1)
    if caption:
        tb = s.shapes.add_textbox(MARGIN, Y, CW, Inches(0.3))
        _p(_tf(tb), caption, 12, color=MUTED, space=0, first=True)
        Y = Y + Inches(0.3)
    Y = Y + GAP


def placeholder(s, label, height_in=2.2):
    """Ô chừa sẵn để chèn ảnh chụp màn hình."""
    global Y
    box = s.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, MARGIN, Y, CW, Inches(height_in))
    box.adjustments[0] = 0.02
    box.fill.solid(); box.fill.fore_color.rgb = RGBColor(0xEF, 0xF2, 0xF6)
    box.line.color.rgb = RGBColor(0xC3, 0xCB, 0xD6)
    box.line.width = Pt(1); box.line.dash_style = 4
    box.shadow.inherit = False
    _p(_tf(box), label, 13, color=MUTED, space=0, first=True)
    Y = Y + Inches(height_in) + GAP


def kpis(s, items):
    global Y
    n = len(items); gap = Inches(0.22)
    w = int((CW - gap * (n - 1)) / n); h = Inches(1.08)
    for i, (val, lab) in enumerate(items):
        left = MARGIN + i * (w + gap)
        _rounded(s, left, Y, w, h, CARD, LINE)
        tb = s.shapes.add_textbox(left + Inches(0.2), Y + Inches(0.14), w - Inches(0.4), h - Inches(0.26))
        tf = _tf(tb)
        _p(tf, val, 21, bold=True, color=ACCENT, font=MONO, space=2, first=True)
        _p(tf, lab, 12, color=MUTED, space=0)
    Y = Y + h + GAP


# ============================== SLIDES ==============================

ASSETS = __file__.rsplit("/", 1)[0] + "/assets/"

# 1. mở đầu
s = slide()
tb = s.shapes.add_textbox(MARGIN, Inches(1.9), CW, Inches(2.2))
tf = _tf(tb)
_p(tf, "SEMINAR · SPRING BOOT MICROSERVICES", 12, bold=True, color=ACCENT, space=10, first=True)
_p(tf, "Nhúng VNPAY vào cửa hàng có sẵn", 40, bold=True, color=INK, space=10)
_p(tf, "Mở ShopStart.java đã tải trước. Hôm nay điền sáu chỗ TODO trong đó.",
   17, color=INK2, space=0)
Y = Inches(4.3)
picture(s, ASSETS + "cua-hang.jpg", 2.5,
        "Cửa hàng chạy sẵn. Nút Thanh toán chưa nối gì, đó là việc hôm nay.")

# 2. vấn đề
s = slide()
title(s, "Vấn đề", "Tự nhận thẻ là vi phạm PCI-DSS")
cards(s, [
    ("Vướng pháp lý",
     "Lưu số thẻ trên server của mình là vi phạm PCI-DSS. Ngân hàng cũng không cấp API "
     "trực tiếp cho một đồ án sinh viên."),
    ("Vướng kỹ thuật",
     "Việt Nam có hàng chục ngân hàng nội địa, mỗi nơi một giao thức. Tự nối từng cái "
     "là bất khả thi."),
    ("Cách VNPAY giải",
     "Khách nhập thẻ trên trang của VNPAY. Server mình chỉ trao đổi chữ ký, "
     "không bao giờ thấy số thẻ."),
])
callout(s, "Câu chốt cho cả buổi",
        "VNPAY không có SDK. Nó chỉ là redirect cộng một chữ ký HMAC-SHA512. "
        "Nắm được chữ ký là xong chín phần mười.", "info")

# 3. kiến trúc
s = slide()
title(s, "Kiến trúc", "Chỉ hai trong bốn đường được phép cộng tiền")
tiers(s, [
    ("1", "Browser đến server", "Tạo đơn PENDING, ký tham số, trả về paymentUrl", None),
    ("2", "VNPAY đến server", "IPN, gọi thẳng vào server mình", ("ok", "Tin được")),
    ("3", "VNPAY đến browser", "ReturnURL, trình duyệt khách quay về", ("bad", "Không tin")),
    ("4", "Server đến VNPAY", "querydr, hỏi lại trạng thái thật", ("ok", "Tin được")),
])
callout(s, "Hỏi cả lớp trước khi sang slide sau",
        "Sao không cộng tiền luôn ở ReturnURL cho nhanh? Vì đó là URL trên trình duyệt của khách. "
        "Sửa vnp_ResponseCode=00 là mua hàng miễn phí. Khách tắt tab sau khi trả tiền thì "
        "ReturnURL không bao giờ về, mình mất đơn đã thu tiền.")

# 4. lộ trình
s = slide()
title(s, "Lộ trình", "Sáu chỗ trống cần điền trong ShopStart.java")
table(s, ["Chỗ", "Việc phải làm", "Thời lượng"], [
    ["TODO 1", "Cấu hình TmnCode và HashSecret", "2 phút"],
    ["TODO 2", "Hàm ký HMAC-SHA512", "6 phút"],
    ["TODO 3", "Hàm verify chữ ký", "3 phút"],
    ["TODO 4", "Tạo URL thanh toán", "5 phút"],
    ["TODO 5", "Nhận IPN", "5 phút"],
    ["TODO 6", "ReturnURL và chốt trạng thái đơn", "4 phút"],
], widths=[1.4, 6, 1.6])
lead(s, "Đơn hàng, giao diện và server đã viết sẵn trong file, mình không đụng tới. "
        "Ai gõ không kịp cứ ngồi xem, cuối buổi có bản làm xong.")

# 5. TODO 1
s = slide()
title(s, "TODO 1", "Secret nạp từ biến môi trường, không hardcode")
code(s, [
    'static final String TMN_CODE    = env("VNPAY_TMN_CODE", "CHANGE_ME");',
    'static final String HASH_SECRET = env("VNPAY_HASH_SECRET", "CHANGE_ME");',
    'static final String RETURN_URL  = env("VNPAY_RETURN_URL",',
    '                                      "http://localhost:8080/vnpay/return");',
], path="ShopStart.java")
code(s, [
    "export VNPAY_TMN_CODE=xxxxxxxx",
    "export VNPAY_HASH_SECRET=xxxxxxxxxxxxxxxx",
    "java ShopStart.java",
])
callout(s, "Đừng hardcode secret rồi push lên GitHub",
        "Ai clone repo cũng ký được đơn thay bạn, và GitHub có bot quét chuyện này.")

# 6. TODO 2a
s = slide()
title(s, "TODO 2", "Chữ ký chỉ gồm ba bước")
lead(s, "Sắp tham số theo alphabet, URL-encode phần giá trị, rồi băm HMAC-SHA512 ra hex chữ thường.")
code(s, [
    "static String hmacSHA512(String secretKey, String data) {",
    "    try {",
    '        Mac mac = Mac.getInstance("HmacSHA512");',
    '        mac.init(new SecretKeySpec(secretKey.getBytes(UTF_8), "HmacSHA512"));',
    "        byte[] bytes = mac.doFinal(data.getBytes(UTF_8));",
    "",
    "        StringBuilder hex = new StringBuilder(bytes.length * 2);",
    '        for (byte b : bytes) hex.append(String.format("%02x", b));',
    "        return hex.toString();",
    "    } catch (Exception e) {",
    '        throw new IllegalStateException("Không tạo được HMAC-SHA512", e);',
    "    }",
    "}",
], path="ShopStart.java")

# 7. TODO 2b
s = slide()
title(s, "TODO 2", "Encode giá trị trước khi đem băm")
code(s, [
    "static String buildQueryString(Map<String, String> params) {",
    "    StringBuilder sb = new StringBuilder();",
    "",
    "    for (var e : new TreeMap<>(params).entrySet()) {",
    "        if (e.getValue() == null || e.getValue().isEmpty()) continue;",
    "        if (sb.length() > 0) sb.append('&');",
    "",
    "        sb.append(URLEncoder.encode(e.getKey(), US_ASCII)).append('=')",
    "          .append(URLEncoder.encode(e.getValue(), US_ASCII));",
    "    }",
    "    return sb.toString();",
    "}",
], path="ShopStart.java")
cards(s, [
    ("Lỗi thứ nhất",
     "Quên encode phần giá trị. vnp_OrderInfo có dấu cách là chữ ký lệch ngay."),
    ("Lỗi thứ hai",
     "Để lọt tham số rỗng vào chuỗi. VNPAY bỏ qua chúng, mình giữ lại là sai."),
])

# 8. TODO 3
s = slide()
title(s, "TODO 3", "Verify là ký lại rồi đem so")
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
], path="ShopStart.java")
callout(s, "Cách debug khi chữ ký không khớp",
        "In buildQueryString(clone) ra rồi so từng ký tự với query string trên thanh địa chỉ. "
        "Gần như luôn lòi ra một trong hai lỗi ở slide trước.", "info")

# 9. TODO 4
s = slide()
title(s, "TODO 4", "Số tiền nhân 100, giờ theo GMT+7")
table(s, ["Tham số", "Giá trị mẫu", "Chỗ dễ sai"], [
    ["vnp_Amount", "`2500000`", "Nhân 100 và là số nguyên. 25.000đ thành 2500000"],
    ["vnp_TxnRef", "`20260920125606777443`", "Duy nhất trong 24h theo TmnCode"],
    ["vnp_CreateDate", "`20260920125606`", "Giờ GMT+7, không phải giờ máy chủ"],
    ["vnp_BankCode", "`NCB`", "Bỏ trống thì VNPAY hiện trang chọn ngân hàng"],
], widths=[2.2, 3.4, 5.6])
code(s, [
    'p.put("vnp_Amount", String.valueOf(amount * 100));',
    "String query = buildQueryString(p);",
    'String url = PAY_URL + "?" + query + "&vnp_SecureHash=" + hmacSHA512(HASH_SECRET, query);',
])
callout(s, "Cái bẫy nằm ngay trong code mẫu của chính VNPAY",
        'Demo của họ dùng Etc/GMT+7, mà theo POSIX nó là UTC trừ 7, lệch 14 tiếng, '
        'giao dịch hết hạn ngay lúc vừa tạo. Dùng ZoneId.of("Asia/Ho_Chi_Minh").')

# 10. checkpoint
s = slide()
title(s, "Kiểm tra", "Chạy thử trước khi viết tiếp")
code(s, [
    "export VNPAY_TMN_CODE=... VNPAY_HASH_SECRET=...",
    "java ShopStart.java",
    "",
    "# mở http://localhost:8080, bấm Mua ngay rồi Thanh toán",
])
cards(s, [
    ("Hiện form nhập thẻ NCB", "Chữ ký đúng rồi, đi tiếp phần ngrok."),
    ("Báo chữ ký không hợp lệ", "Sai HashSecret, hoặc quên encode ở TODO 2."),
    ("Báo website không tồn tại", "Sai TmnCode, hoặc vnp_ReturnUrl không khớp domain đã khai."),
])
placeholder(s, "Chèn ảnh chụp trang nhập thẻ NCB của VNPAY vào ô này", 1.75)

# 11. ngrok
s = slide()
title(s, "Bắt buộc", "ngrok mở đường cho VNPAY gọi về máy")
lead(s, "IPN là cuộc gọi từ server VNPAY vào server mình. Máy ở localhost thì "
        "từ internet không ai thấy.")
code(s, [
    "ngrok http 8080",
    "",
    "Forwarding   https://a1b2-42-115-242-109.ngrok-free.app -> http://localhost:8080",
])
table(s, ["Khai vào merchant portal", "Giá trị"], [
    ["URL trả về", "`https://a1b2-....ngrok-free.app/vnpay/return`"],
    ["URL nhận kết quả IPN", "`https://a1b2-....ngrok-free.app/vnpay/ipn`"],
], widths=[3.4, 7.8])
callout(s, "Hai chuyện của bản ngrok miễn phí",
        "Địa chỉ đổi mỗi lần chạy lại nên phải khai lại trong portal, làm sát giờ demo. "
        "Lần đầu mở bằng trình duyệt sẽ gặp trang cảnh báo, bấm Visit Site là qua.")

# 12. TODO 5
s = slide()
title(s, "TODO 5", "IPN là nơi duy nhất được cộng tiền")
lead(s, "VNPAY retry tới khi nhận RspCode 00, nên hàm này gọi mấy lần cũng chỉ ghi nhận một lần.")
code(s, [
    "if (!isValidSignature(params))",
    '    return rsp("97", "Invalid Checksum");',
    "",
    'Order order = ORDERS.get(params.get("vnp_TxnRef"));',
    'if (order == null)              return rsp("01", "Order not Found");',
    "",
    'if (Long.parseLong(params.get("vnp_Amount")) != order.amount * 100)',
    '    return rsp("04", "Invalid Amount");',
    "synchronized (order) {",
    '    if (!"PENDING".equals(order.status))',
    '        return rsp("02", "Order already confirmed");',
    "",
    '    boolean paid = "00".equals(params.get("vnp_ResponseCode"))',
    '                && "00".equals(params.get("vnp_TransactionStatus"));',
    '    order.status = paid ? "PAID" : "FAILED";',
    "}",
    'return rsp("00", "Confirm Success");',
], size=12, path="ShopStart.java")

# 13. TODO 6
s = slide()
title(s, "TODO 6", "ReturnURL chỉ dùng để vẽ màn hình")
code(s, [
    "static String handleReturn(Map<String, String> params) {",
    "    boolean valid = isValidSignature(params);",
    '    String txnRef = params.getOrDefault("vnp_TxnRef", "");',
    "",
    '    return "/?txnRef=" + encode(txnRef) + "&valid=" + valid;',
    "}",
], path="ShopStart.java")
code(s, [
    "// không đọc kết quả từ URL, hỏi lại server cho chắc",
    "const res   = await fetch('/api/orders?txnRef=' + txnRef);",
    "const order = await res.json();",
    "if (order.status === 'PAID') showSuccess(order);",
], path="trang kết quả, phía trình duyệt")
callout(s, "Để ý cái không có ở đây",
        "Không một dòng nào đổi trạng thái đơn hàng. Tham số trên trình duyệt là dữ liệu "
        "do khách mang về, không phải sự thật.")

# 14. querydr
s = slide()
title(s, "Tình huống thật", "querydr cứu khi IPN không bao giờ về")
callout(s, "Chuyện xảy ra lúc dựng bài này",
        "Portal sandbox không cho khai IPN URL. Danh sách website trống trơn, Cài đặt thông báo "
        "báo lỗi kết nối. Code đúng hết mà IPN không tới. Chỉ biết mỗi IPN thì demo chết tại chỗ.")
lead(s, "Đường lùi là gọi querydr để server tự hỏi VNPAY. Vẫn an toàn như IPN vì hỏi thẳng VNPAY, "
        "verify chữ ký của response, rồi so lại số tiền.")
code(s, [
    "// hash của querydr không sort alphabet, nối bằng '|' đúng thứ tự tài liệu",
    'String hashData = String.join("|",',
    '        requestId, "2.1.0", "querydr", TMN_CODE,',
    "        txnRef, transactionDate, createDate, ipAddr, orderInfo);",
])

# 15. demo
s = slide()
title(s, "Demo", "Giao dịch thật đã chốt PAID bằng querydr")
kpis(s, [("25.000đ", "Số tiền"), ("15683165", "Mã GD tại VNPAY"),
          ("PAID", "Trạng thái đơn"), ("querydr", "Nguồn chốt đơn")])
picture(s, ASSETS + "ket-qua.jpg", 2.25)
code(s, ["Chot bang querydr: txnRef=20260920125606777443 status=PAID transactionNo=15683165"],
     size=11.5)

# 16. microservice
s = slide()
title(s, "Đưa vào đồ án", "Tách payment-service, secret để ngoài mã nguồn")
cards(s, [
    ("Ranh giới service",
     "payment-service đứng riêng. Service khác không gọi VNPAY, chúng nghe event OrderPaid."),
    ("Cấu hình và secret",
     "Dùng @ConfigurationProperties. HashSecret nạp từ Vault hoặc K8s Secret."),
])
cards(s, [
    ("Chống trùng ở tầng DB",
     "Bảng payment_transaction có UNIQUE(txn_ref) và optimistic lock. "
     "Demo dùng synchronized, chạy nhiều instance là không đủ."),
    ("Nhiều cổng thanh toán",
     "MoMo và ZaloPay mỗi bên ký một kiểu. Cần nhiều cổng thì bọc sau interface PaymentGateway."),
])
lead(s, "Đồ án nào dùng được: bán hàng online, đặt vé, đặt sân, đóng học phí, ví điện tử, quyên góp. "
        "Việc phải làm chỉ là thay chỗ lưu đơn hàng bằng repository của mình.", size=14)

# 17. quy tắc
s = slide()
title(s, "Tổng kết", "Sáu quy tắc giữ cho tiền không thất thoát")
table(s, ["Quy tắc", "Vì sao"], [
    ["Chỉ tin IPN và querydr", "Hai kênh server gọi server, có chữ ký"],
    ["So lại số tiền với DB", "Chặn trò sửa vnp_Amount trên URL"],
    ["Idempotent bằng trạng thái đơn", "VNPAY retry nhiều lần, đã xử lý thì trả 02"],
    ["vnp_TxnRef duy nhất trong 24h", "Thanh toán lại phải sinh mã mới"],
    ["Giờ Asia/Ho_Chi_Minh", "Etc/GMT+7 lệch 14 tiếng, đơn hết hạn ngay"],
    ["Không log secret và chữ ký", "Lộ HashSecret là mất quyền ký"],
], widths=[4.2, 7])
lead(s, "Giới hạn phải biết trước: bắt buộc redirect nên app mobile phải nhúng WebView, "
        "hoàn tiền phải gọi API refund rồi đối soát tay, và ngrok free đổi địa chỉ mỗi lần chạy.",
     size=14)

# 18. hỏi đáp
s = slide()
title(s, "Hỏi đáp", "Bốn câu chắc chắn bị hỏi")
table(s, ["Câu hỏi", "Trả lời"], [
    ["IPN không về thì đơn treo mãi à?", "Job quét đơn PENDING quá hạn rồi gọi querydr chốt"],
    ["Sao phải cài ngrok?", "Cần địa chỉ công khai để VNPAY gọi vào, deploy server thật cũng được"],
    ["Sai chữ ký mà không hiểu vì sao?", "In hashData ra so từng ký tự, thường là quên encode"],
    ["Chưa có thẻ thì test kiểu gì?", "Phần ký và verify có unit test chạy offline"],
], widths=[4.4, 6.8])

# 19. bàn giao
s = slide()
title(s, "Bàn giao", "Ba bước để đưa vào đồ án của bạn")
tiers(s, [
    ("1", "Copy", "Mang hai hàm hmacSHA512 và buildQueryString sang project", None),
    ("2", "Thay", "Đổi chỗ lưu đơn hàng sang repository sẵn có của bạn", None),
    ("3", "Cấu hình", "Nạp TmnCode và HashSecret từ biến môi trường", None),
])
callout(s, "Sau buổi hôm nay",
        "Mình bỏ VnpayDemo.java, tức bản đã điền xong sáu TODO, vào đúng thư mục Drive lúc nãy. "
        "Có gì không chạy cứ nhắn mình.", "ok")

out = __file__.rsplit("/", 1)[0] + "/VNPAY-seminar.pptx"
prs.save(out)
print("đã lưu", len(prs.slides._sldIdLst), "slide:", out)

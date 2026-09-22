"""Dựng bộ slide seminar VNPAY.

Phong cách Tech Modern: nền sáng, khối code nền tối, card bo góc, nhấn xanh.
Sửa nội dung trong phần SLIDES ở cuối file rồi chạy lại là xong.
"""
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
import math
import pathlib

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
    p.alignment = PP_ALIGN.LEFT if align is None else align
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


# Đo bề rộng chữ bằng chính file font, thay vì đoán số ký tự trên một dòng.
_FONTDIR = str(pathlib.Path.home() / "Library" / "Fonts")
_cache = {}


def _font(size, bold):
    key = (round(size, 1), bold)
    if key not in _cache:
        try:
            from PIL import ImageFont
            name = "BeVietnamPro-Bold.ttf" if bold else "BeVietnamPro-Regular.ttf"
            _cache[key] = ImageFont.truetype(_FONTDIR + "/" + name, int(size * 96 / 72))
        except Exception:
            _cache[key] = None
    return _cache[key]


def wrapped(lines, size, width_in, bold=False):
    """Số dòng thật sau khi xuống dòng tự động, đo bằng font thật."""
    f = _font(size, bold)
    if f is None:
        per = max(14, int(width_in * 96 / (size * 0.62)))
        return sum(max(1, math.ceil(len(t) / per)) for t in lines)

    limit = width_in * 96
    total = 0
    for text in lines:
        words, cur, n = text.split(), "", 1
        for w in words:
            trial = w if not cur else cur + " " + w
            if f.getlength(trial) <= limit:
                cur = trial
            else:
                n += 1
                cur = w
        total += n
    return total


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
    h = height or Pt(13.5 * 1.5) * max(
        wrapped([b], 13.5, wi - 0.62) for _, b in items) + Inches(0.74)
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
        chip.text_frame.vertical_anchor = MSO_ANCHOR.MIDDLE
        _p(_tf(chip), num, 13, bold=True, align=PP_ALIGN.CENTER,
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
            pill.text_frame.vertical_anchor = MSO_ANCHOR.MIDDLE
            _p(_tf(pill), label, 11.5, bold=True, align=PP_ALIGN.CENTER,
               color=OK if kind == "ok" else WARN, space=0, first=True)
    Y = Y + len(items) * (rh + Inches(0.1)) + GAP


def code(s, lines, size=12.5, path=None):
    global Y
    if path:
        tb = s.shapes.add_textbox(MARGIN, Y, CW, Inches(0.24))
        _p(_tf(tb), path, 11, color=MUTED, font=MONO, space=0, first=True)
        Y = Y + Inches(0.26)
    lh = Pt(size * 1.42)
    h = lh * len(lines) + Inches(0.3)
    box = _rounded(s, MARGIN, Y, CW, h, CODEBG)
    tf = box.text_frame
    tf.word_wrap = False
    tf.vertical_anchor = MSO_ANCHOR.TOP
    tf.margin_left = Inches(0.26); tf.margin_top = Inches(0.13)
    tf.margin_right = Inches(0.16); tf.margin_bottom = Inches(0.1)
    for i, t in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.text = t
        p.space_after = Pt(0)
        p.alignment = PP_ALIGN.LEFT
        p.line_spacing = lh
        for r in p.runs:
            r.font.size = Pt(size)
            r.font.name = MONO
            r.font.color.rgb = CODEDIM if t.strip().startswith(("//", "#")) else CODEFG
    Y = Y + h + GAP


def callout(s, head, body, kind="warn"):
    global Y
    color = {"warn": WARN, "ok": OK, "info": ACCENT}[kind]
    h = Pt(13.5 * 1.42) * wrapped([body], 13.5, 11.2) + Inches(0.56)
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
    box.text_frame.vertical_anchor = MSO_ANCHOR.MIDDLE
    _p(_tf(box), label, 13, color=MUTED, space=0, first=True, align=PP_ALIGN.CENTER)
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

# 1
s = slide()
tb = s.shapes.add_textbox(MARGIN, Inches(1.75), CW, Inches(2.0))
tf = _tf(tb)
_p(tf, "VNPAY SANDBOX · SPRING BOOT", 12, bold=True, color=ACCENT, space=10, first=True)
_p(tf, "Kết quả: ShopStart.java xử lý trọn luồng thanh toán", 34, bold=True, color=INK, space=8)
_p(tf, "Chín bước code, hai lần kiểm tra. JDK 17, không Maven, không thư viện ngoài.",
   16, color=INK2, space=0)
Y = Inches(3.95)
picture(s, ASSETS + "cua-hang.jpg", 2.5)

# 2
s = slide()
title(s, "Kiến trúc", "Bốn endpoint và bốn luồng dữ liệu")
table(s, ["Endpoint", "Ai gọi", "Nhiệm vụ"], [
    ["POST /api/payments", "Trình duyệt", "Tạo đơn PENDING, ký tham số, trả paymentUrl"],
    ["GET /vnpay/ipn", "Server VNPAY", "Verify chữ ký, cập nhật trạng thái đơn"],
    ["GET /vnpay/return", "Trình duyệt", "Verify chữ ký, redirect sang trang kết quả"],
    ["GET /api/orders", "Trình duyệt", "Trả trạng thái đơn, gọi querydr nếu còn PENDING"],
], widths=[3.0, 2.2, 6.0])
lead(s, "Trạng thái đơn chỉ được ghi ở /vnpay/ipn và ở nhánh querydr trong /api/orders. "
        "Hai chỗ đó đều xác thực chữ ký trước khi ghi.", size=14)

# 3
s = slide()
title(s, "Chuẩn bị", "JDK 17, TmnCode, HashSecret, ngrok")
table(s, ["Thành phần", "Cách lấy", "Kiểm tra"], [
    ["JDK 17+", "adoptium.net hoặc brew install openjdk", "`java -version`"],
    ["TmnCode, HashSecret", "sandbox.vnpayment.vn/devreg", "Email trả về 8 và 32 ký tự"],
    ["ngrok", "brew install --cask ngrok", "`ngrok config check`"],
    ["File nguồn", "ShopStart.java trong thư mục Drive", "`java ShopStart.java`"],
], widths=[2.6, 5.2, 3.4])
code(s, [
    "export VNPAY_TMN_CODE=xxxxxxxx",
    "export VNPAY_HASH_SECRET=xxxxxxxxxxxxxxxx",
])

# 4
s = slide()
title(s, "Bước 1", "Khai báo cấu hình từ biến môi trường")
code(s, [
    'static final String TMN_CODE    = env("VNPAY_TMN_CODE", "CHANGE_ME");',
    'static final String HASH_SECRET = env("VNPAY_HASH_SECRET", "CHANGE_ME");',
    'static final String RETURN_URL  = env("VNPAY_RETURN_URL",',
    '                                      "http://localhost:8080/vnpay/return");',
    'static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";',
    'static final String API_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";',
    "",
    'static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");',
    'static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");',
], path="ShopStart.java · TODO 1")
callout(s, "Ràng buộc",
        "Etc/GMT+7 theo chuẩn POSIX là UTC trừ 7, lệch 14 tiếng so với giờ Việt Nam. "
        "vnp_CreateDate và vnp_ExpireDate sinh từ ZoneId Asia/Ho_Chi_Minh.", "info")

# 5
s = slide()
title(s, "Bước 2", "Hàm hmacSHA512")
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
    '        throw new IllegalStateException("HMAC-SHA512", e);',
    "    }",
    "}",
], path="ShopStart.java · TODO 2")
lead(s, "Đầu ra là chuỗi hex 128 ký tự, chữ thường. VNPAY so sánh chuỗi nên hoa thường khác nhau.",
     size=14)

# 6
s = slide()
title(s, "Bước 3", "Hàm buildQueryString")
code(s, [
    "static String buildQueryString(Map<String, String> params) {",
    "    StringBuilder sb = new StringBuilder();",
    "",
    "    for (var e : new TreeMap<>(params).entrySet()) {      // sort theo alphabet",
    "        if (e.getValue() == null || e.getValue().isEmpty()) continue;",
    "        if (sb.length() > 0) sb.append('&');",
    "",
    "        sb.append(URLEncoder.encode(e.getKey(), US_ASCII)).append('=')",
    "          .append(URLEncoder.encode(e.getValue(), US_ASCII));",
    "    }",
    "    return sb.toString();",
    "}",
], path="ShopStart.java · TODO 2")
cards(s, [
    ("Đầu ra dùng hai nơi",
     "Chuỗi này vừa là hashData đem băm, vừa là query string gắn vào URL."),
    ("Điều kiện để chữ ký khớp",
     "Tham số rỗng bị loại. Phần giá trị phải URL-encode, tên tham số thì không bắt buộc."),
])

# 7
s = slide()
title(s, "Bước 4", "Hàm isValidSignature")
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
], path="ShopStart.java · TODO 3")
lead(s, "Servlet trả về tham số đã URL-decode, nên buildQueryString phải encode lại trước khi băm.",
     size=14)

# 8
s = slide()
title(s, "Bước 5", "Hàm createPayment và bảng tham số")
table(s, ["Tham số", "Giá trị", "Ràng buộc"], [
    ["vnp_Version", "`2.1.0`", "Cố định"],
    ["vnp_Amount", "`amount * 100`", "Số nguyên, đơn vị nhân 100"],
    ["vnp_TxnRef", "`yyyyMMddHHmmss + 6 số`", "Duy nhất trong 24h theo TmnCode"],
    ["vnp_CreateDate", "`now(VN_ZONE)`", "Định dạng yyyyMMddHHmmss"],
    ["vnp_ExpireDate", "`now + 15 phút`", "Quá hạn thì VNPAY từ chối"],
], widths=[2.8, 3.6, 4.8])
code(s, [
    "String query = buildQueryString(p);",
    'String url = PAY_URL + "?" + query + "&vnp_SecureHash=" + hmacSHA512(HASH_SECRET, query);',
], path="ShopStart.java · TODO 4")

# 9
s = slide()
title(s, "Bước 6", "Endpoint POST /api/payments")
code(s, [
    'server.createContext("/api/payments", ex -> {',
    "    Map<String, String> form = parseQuery(",
    "            new String(ex.getRequestBody().readAllBytes(), UTF_8));",
    "",
    '    long amount = Long.parseLong(form.getOrDefault("amount", "0"));',
    '    String info = form.getOrDefault("orderInfo", "Thanh toan don hang");',
    "",
    '    send(ex, 200, "application/json",',
    '         createPayment(amount, info, form.get("bankCode"), "127.0.0.1"));',
    "});",
], path="ShopStart.java · TODO 4")
lead(s, "Trình duyệt nhận paymentUrl rồi gán vào window.location.href. HashSecret không rời khỏi server.",
     size=14)

# 10
s = slide()
title(s, "Kiểm tra 1", "Sinh URL và mở trang VNPAY")
code(s, [
    "java ShopStart.java",
    "",
    "curl -X POST localhost:8080/api/payments \\",
    '     --data-urlencode "amount=25000" --data-urlencode "bankCode=NCB"',
])
table(s, ["Kết quả", "Nguyên nhân"], [
    ["Hiện form nhập thẻ NCB", "Chữ ký hợp lệ, sang bước 7"],
    ["Chữ ký không hợp lệ", "Sai HASH_SECRET, hoặc thiếu URL-encode ở bước 3"],
    ["Website không tồn tại", "Sai TMN_CODE, hoặc vnp_ReturnUrl khác domain đã khai"],
], widths=[4.0, 7.2])
placeholder(s, "Ảnh chụp trang nhập thẻ NCB", 1.5)

# 11
s = slide()
title(s, "Bước 7", "Mở ngrok và khai hai URL")
code(s, [
    "ngrok http 8080",
    "",
    "Forwarding   https://a1b2-42-115-242-109.ngrok-free.app -> http://localhost:8080",
])
table(s, ["Trường trong merchant portal", "Giá trị"], [
    ["URL trả về", "`https://<id>.ngrok-free.app/vnpay/return`"],
    ["URL nhận kết quả IPN", "`https://<id>.ngrok-free.app/vnpay/ipn`"],
    ["Biến môi trường", "`VNPAY_RETURN_URL=https://<id>.ngrok-free.app/vnpay/return`"],
], widths=[4.2, 7.0])
lead(s, "Bản ngrok miễn phí cấp id mới mỗi lần chạy, phải khai lại hai URL trên.", size=14)

# 12
s = slide()
title(s, "Bước 8", "Handler IPN và năm mã trả về")
code(s, [
    'if (!isValidSignature(params))               return rsp("97", "Invalid Checksum");',
    "",
    'Order order = ORDERS.get(params.get("vnp_TxnRef"));',
    'if (order == null)                           return rsp("01", "Order not Found");',
    'if (vnpAmount != order.amount * 100)         return rsp("04", "Invalid Amount");',
    "",
    "synchronized (order) {",
    '    if (!"PENDING".equals(order.status))     return rsp("02", "Order already confirmed");',
    "",
    '    boolean paid = "00".equals(params.get("vnp_ResponseCode"))',
    '                && "00".equals(params.get("vnp_TransactionStatus"));',
    '    order.status = paid ? "PAID" : "FAILED";',
    "}",
    'return rsp("00", "Confirm Success");',
], size=12, path="ShopStart.java · TODO 5")
lead(s, "VNPAY gửi lại IPN cho tới khi nhận RspCode 00. Nhánh 02 giữ cho đơn chỉ được ghi một lần.",
     size=14)

# 13
s = slide()
title(s, "Bước 9", "ReturnURL và đối soát querydr")
code(s, [
    "// ReturnURL: verify chữ ký rồi redirect, không ghi trạng thái",
    "boolean valid = isValidSignature(params);",
    'return "/?txnRef=" + encode(txnRef) + "&valid=" + valid;',
], path="ShopStart.java · TODO 6")
code(s, [
    "// querydr: hash nối bằng '|', không sort alphabet",
    'String hashData = String.join("|", requestId, "2.1.0", "querydr", TMN_CODE,',
    "        txnRef, transactionDate, createDate, ipAddr, orderInfo);",
    "",
    "// chỉ ghi PAID khi chữ ký response hợp lệ và số tiền khớp",
    'order.status = "00".equals(r.get("vnp_TransactionStatus")) ? "PAID" : "FAILED";',
])
lead(s, "Khai được IPN thì IPN ghi trạng thái. Không khai được thì /api/orders gọi querydr khi đơn "
        "còn PENDING.", size=14)

# 14
s = slide()
title(s, "Kiểm tra 2", "Thẻ test và trạng thái PAID")
table(s, ["Trường", "Giá trị"], [
    ["Số thẻ", "`9704198526191432198`"],
    ["Tên chủ thẻ", "`NGUYEN VAN A`"],
    ["Ngày phát hành", "`07/15`"],
    ["OTP", "`123456`"],
], widths=[3.0, 8.2])
code(s, [
    "Chot bang querydr: txnRef=20260920125606777443 status=PAID transactionNo=15683165",
    "",
    '{"txnRef":"20260920125606777443","amount":25000,"status":"PAID",'
    '"transactionNo":"15683165","bankCode":"NCB"}',
], size=11.5)
kpis(s, [("9", "Bước code"), ("6", "Hàm phải viết"),
         ("4", "Endpoint"), ("0", "Thư viện ngoài")])

NOTES = {
 1: "Kết quả cuối buổi: một file ShopStart.java chạy được, sinh URL thanh toán, nhận IPN, "
    "chốt trạng thái đơn.\nHỏi ai đã chạy thử file xuất phát chưa.",
 2: "Nhấn: trạng thái đơn chỉ ghi ở hai chỗ, IPN và nhánh querydr. ReturnURL không ghi gì.\n"
    "Nếu lớp hỏi vì sao, trả lời: ReturnURL chạy qua trình duyệt của khách, tham số sửa được.",
 3: "Kiểm tra nhanh: ai chưa có TmnCode và HashSecret thì ngồi cùng bạn bên cạnh.\n"
    "Ai chưa gắn authtoken ngrok thì xử lý ngay lúc này, bước 7 sẽ cần.",
 4: "Chỉ vào ZoneId. Etc/GMT+7 là UTC trừ 7, giao dịch hết hạn ngay khi tạo.",
 5: "Nhắc hex chữ thường. Nếu ai dùng %02X thì chữ ký không khớp.",
 6: "Ba điểm: TreeMap để sort, bỏ tham số rỗng, encode phần giá trị.\n"
    "Đây là nơi phát sinh phần lớn lỗi sai chữ ký.",
 7: "Nhấn hai dòng remove. Servlet đã decode nên phải encode lại.",
 8: "Viết lên bảng: 25.000đ thành 2500000.\nvnp_TxnRef trùng trong 24h thì VNPAY từ chối.",
 9: "Nhắc: HashSecret chỉ nằm trên server. JavaScript chỉ nhận paymentUrl.",
10: "Cho lớp 3 phút chạy thử. Đi quanh xem ai kẹt.\n"
    "Quá nửa lớp chưa qua được thì gửi bản làm xong rồi đi tiếp.",
11: "Cả lớp mở ngrok ngay lúc này vì id đổi mỗi lần chạy.\n"
    "Sau khi khai URL nhớ export lại VNPAY_RETURN_URL rồi chạy lại app.",
12: "Đọc to năm mã: 97, 01, 04, 02, 00.\n"
    "Hỏi lớp: nếu bỏ nhánh 02 thì chuyện gì xảy ra khi VNPAY gọi lại lần hai?",
13: "Nhấn: hash của querydr nối bằng dấu gạch đứng, không sort alphabet. "
    "Dùng nhầm buildQueryString ở đây là fail.",
14: "Mở sẵn terminal để chỉ vào dòng log.\n"
    "Mạng hỏng thì chạy demo-local.sh, tự ký IPN, không cần internet.\n\n"
    "Bốn câu hay bị hỏi:\n"
    "1. IPN không về thì sao? Job quét đơn PENDING quá hạn rồi gọi querydr.\n"
    "2. Sao phải ngrok? Cần địa chỉ công khai để VNPAY gọi vào.\n"
    "3. Sai chữ ký? In hashData ra so từng ký tự, thường là quên encode.\n"
    "4. Chưa có thẻ? Phần ký và verify có unit test chạy offline.",
}
for idx, sl in enumerate(prs.slides, 1):
    if idx in NOTES:
        sl.notes_slide.notes_text_frame.text = NOTES[idx]

out = __file__.rsplit("/", 1)[0] + "/VNPAY-seminar.pptx"
prs.save(out)
print("đã lưu", len(prs.slides._sldIdLst), "slide")

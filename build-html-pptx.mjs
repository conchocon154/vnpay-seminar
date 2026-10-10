/*
 * Dựng VNPAY-seminar.pptx từ các slide HTML trong slides-html/.
 *
 * Mỗi slide được mở bằng Chrome, đo vị trí từng khối chữ, khung, bảng, ảnh,
 * rồi vẽ lại bằng chữ và hình thật của PowerPoint. Chữ là chữ thật nên chiếu
 * lên màn lớn vẫn nét, không bị nhoè như khi cả slide là một tấm ảnh.
 * Ghi chú người nói đọc từ notes.json.
 *
 * Chạy:  npm install  &&  npm run build:slides
 * Máy không có Chrome của puppeteer thì đặt PUPPETEER_EXECUTABLE_PATH trỏ vào Google Chrome.
 */
import puppeteer from 'puppeteer';
import PptxGenJS from 'pptxgenjs';
import JSZip from 'jszip';
import { readdir, readFile, writeFile } from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const SLIDES = path.join(ROOT, 'slides-html');
const OUT_PPTX = path.join(ROOT, 'VNPAY-seminar.pptx');
const W = 1920, H = 1080, PX = 13.333 / W;          // 1px trên HTML = PX inch trên slide
const SANS = 'Arial', MONO = 'Courier New';         // hai font có sẵn trên mọi máy Windows, macOS

const notes = JSON.parse(await readFile(path.join(SLIDES, 'notes.json'), 'utf8'));
const files = (await readdir(SLIDES)).filter(f => f.endsWith('.html')).sort();

// Chạy trong trang: trả về danh sách hình cần vẽ theo đúng thứ tự trên dưới
function extract() {
  const items = [];
  const rgba = c => {
    const m = c.match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    const [r, g, b, a = 1] = m[1].split(',').map(Number);
    if (a === 0) return null;
    return { hex: [r, g, b].map(v => v.toString(16).padStart(2, '0')).join(''), alpha: a };
  };
  const box = r => ({ x: r.left, y: r.top, w: r.width, h: r.height });
  const isMono = cs => /courier|mono/i.test(cs.fontFamily);
  const INLINE = new Set(['SPAN', 'B', 'STRONG', 'I', 'EM', 'A', 'CODE', 'BR']);

  function boxes(el, cs, r) {
    const fill = rgba(cs.backgroundColor);
    const bw = ['Top', 'Right', 'Bottom', 'Left'].map(s => parseFloat(cs['border' + s + 'Width']) || 0);
    const bc = ['Top', 'Right', 'Bottom', 'Left'].map(s => rgba(cs['border' + s + 'Color']));
    const radius = parseFloat(cs.borderTopLeftRadius) || 0;
    const uniform = bw.every(v => v === bw[0]) && bw[0] > 0;
    if (fill || uniform) items.push({ t: 'rect', ...box(r), fill, radius,
      line: uniform ? { ...bc[0], w: bw[0] } : null });
    if (!uniform) {
      if (bw[0] && bc[0]) items.push({ t: 'rect', x: r.left, y: r.top, w: r.width, h: bw[0], fill: bc[0] });
      if (bw[2] && bc[2]) items.push({ t: 'rect', x: r.left, y: r.bottom - bw[2], w: r.width, h: bw[2], fill: bc[2] });
      if (bw[3] && bc[3]) items.push({ t: 'rect', x: r.left, y: r.top, w: bw[3], h: r.height, fill: bc[3] });
    }
  }

  function runsOf(el, pre, upper) {
    const runs = [];
    const visit = n => {
      if (n.nodeType === 3) {
        const cs = getComputedStyle(n.parentElement);
        let text = n.textContent;
        if (!pre) text = text.replace(/\s+/g, ' ');
        if (upper || cs.textTransform === 'uppercase') text = text.toUpperCase();
        if (text) runs.push({ text, mono: isMono(cs), size: parseFloat(cs.fontSize),
          color: rgba(cs.color).hex, bold: parseInt(cs.fontWeight) >= 600 });
      } else if (n.nodeType === 1 && getComputedStyle(n).display !== 'none') n.childNodes.forEach(visit);
    };
    el.childNodes.forEach(visit);
    if (!pre && runs.length) {
      runs[0].text = runs[0].text.replace(/^ /, '');
      runs[runs.length - 1].text = runs[runs.length - 1].text.replace(/ $/, '');
    }
    return runs.filter(r => r.text);
  }

  function walk(el) {
    const cs = getComputedStyle(el);
    if (cs.display === 'none' || cs.visibility === 'hidden') return;
    const r = el.getBoundingClientRect();
    if (el !== document.body) {
      if (el.tagName === 'IMG') {
        items.push({ t: 'img', ...box(r), src: decodeURI(new URL(el.src).pathname) });
        const line = rgba(cs.borderTopColor), w = parseFloat(cs.borderTopWidth);
        if (w && line) items.push({ t: 'rect', ...box(r), fill: null, radius: parseFloat(cs.borderTopLeftRadius) || 0, line: { ...line, w } });
        return;
      }
      boxes(el, cs, r);
    }
    // dấu chấm hoặc số thứ tự trước mỗi dòng gạch đầu dòng
    if (el.tagName === 'LI') {
      const b = getComputedStyle(el, '::before');
      const ol = el.parentElement.classList.contains('num');
      if (ol) {
        const idx = [...el.parentElement.children].indexOf(el) + 1;
        const lh = parseFloat(cs.lineHeight) || parseFloat(cs.fontSize) * 1.2;
        items.push({ t: 'text', x: r.left, y: r.top, w: 46, h: lh, align: 'left', valign: 'top', lh, lines: 1,
          runs: [{ text: idx + '.', mono: false, size: parseFloat(cs.fontSize), color: rgba(b.color).hex, bold: true }] });
      } else {
        items.push({ t: 'dot', x: r.left + parseFloat(b.left), y: r.top + parseFloat(b.top),
          w: parseFloat(b.width), h: parseFloat(b.height), fill: rgba(b.backgroundColor) });
      }
    }
    const kids = [...el.childNodes];
    const hasText = kids.some(n => n.nodeType === 3 && n.textContent.trim());
    const allInline = kids.every(n => n.nodeType !== 1 || (INLINE.has(n.tagName) && getComputedStyle(n).display === 'inline'));
    if (el !== document.body && (hasText || allInline) && el.textContent.trim()) {
      const pre = cs.whiteSpace.startsWith('pre');
      const runs = runsOf(el, pre, false);
      if (!runs.length) return;
      const range = document.createRange();
      range.selectNodeContents(el);
      const rr = range.getBoundingClientRect();
      const fs = parseFloat(cs.fontSize);
      const lh = cs.lineHeight === 'normal' ? fs * 1.2 : parseFloat(cs.lineHeight);
      const lines = Math.round(rr.height / lh);
      const pl = parseFloat(cs.paddingLeft) + parseFloat(cs.borderLeftWidth);
      const pr = parseFloat(cs.paddingRight) + parseFloat(cs.borderRightWidth);
      const centered = cs.textAlign === 'center' || cs.justifyContent === 'center';
      let x, w;
      if (lines <= 1) { x = rr.left; w = rr.width; }
      else { x = r.left + pl; w = r.width - pl - pr; }
      items.push({ t: 'text', x, y: rr.top, w, h: rr.height, lh, runs, lines,
        align: centered ? 'center' : 'left', valign: 'top', pre });
      return;
    }
    [...el.children].forEach(walk);
  }
  walk(document.body);
  return { bg: rgba(getComputedStyle(document.body).backgroundColor).hex, items };
}

const browser = await puppeteer.launch({ args: ['--no-sandbox', '--font-render-hinting=none'] });
const page = await browser.newPage();
await page.setViewport({ width: W, height: H, deviceScaleFactor: 1 });

const pptx = new PptxGenJS();
pptx.defineLayout({ name: 'WIDE', width: 13.333, height: 7.5 });
pptx.layout = 'WIDE';
pptx.title = 'Tích hợp VNPAY Sandbox';
pptx.author = 'Lê Minh Đăng';

const problems = [];
for (const f of files) {
  await page.goto('file://' + path.join(SLIDES, f), { waitUntil: 'networkidle0' });
  await page.evaluateHandle('document.fonts.ready');
  const over = await page.evaluate(() => {
    const issues = [];
    const h = document.documentElement.scrollHeight - 1080;
    if (h > 2) issues.push('cao hơn ' + h + 'px');
    document.querySelectorAll('pre').forEach(p => {
      if (p.scrollWidth > p.clientWidth + 1) issues.push('code rộng hơn ' + (p.scrollWidth - p.clientWidth) + 'px');
    });
    const foot = document.querySelector('.foot'), content = document.querySelector('.content');
    if (foot && content) {
      const bottom = Math.max(...[...content.querySelectorAll('*')].map(e => e.getBoundingClientRect().bottom));
      if (bottom > foot.getBoundingClientRect().top - 12) issues.push('đè chân trang');
    }
    return issues;
  });
  if (over.length) { console.log('  TRÀN', f + ':', over.join(', ')); problems.push(f); }

  const { bg, items } = await page.evaluate(extract);
  const slide = pptx.addSlide();
  slide.background = { color: bg };
  const inch = v => +(v * PX).toFixed(4);

  for (const it of items) {
    if (it.t === 'rect') {
      const opts = { x: inch(it.x), y: inch(it.y), w: inch(it.w), h: inch(it.h),
        fill: it.fill ? { color: it.fill.hex, transparency: Math.round((1 - it.fill.alpha) * 100) } : { type: 'none' },
        line: it.line ? { color: it.line.hex, width: Math.max(0.5, it.line.w * 0.5) } : { type: 'none' } };
      if (it.radius > 0) {
        opts.rectRadius = inch(Math.min(it.radius, it.w / 2, it.h / 2));
        slide.addShape(pptx.ShapeType.roundRect, opts);
      } else slide.addShape(pptx.ShapeType.rect, opts);
    } else if (it.t === 'dot') {
      slide.addShape(pptx.ShapeType.ellipse, { x: inch(it.x), y: inch(it.y + 2), w: inch(it.w), h: inch(it.h),
        fill: { color: it.fill.hex }, line: { type: 'none' } });
    } else if (it.t === 'img') {
      slide.addImage({ path: it.src, x: inch(it.x), y: inch(it.y), w: inch(it.w), h: inch(it.h) });
    } else if (it.t === 'text') {
      // Chừa thêm chỗ để font trong PowerPoint hơi rộng hơn cũng không bị rớt dòng
      const slack = it.lines <= 1 ? Math.max(12, it.w * 0.06) : it.w * 0.03;
      const x = it.align === 'center' ? it.x - slack / 2 : it.x;
      const runs = it.runs.map(r => ({ text: r.text, options: {
        fontFace: r.mono ? MONO : SANS, fontSize: +(r.size * 0.5).toFixed(1), color: r.color, bold: r.bold } }));
      const base = { x: inch(x), w: inch(it.w + slack), margin: 0, align: it.align, fit: 'none', wrap: !it.pre, isTextBox: true };
      if (it.lines <= 1) {
        // một dòng: căn giữa theo chiều dọc trong đúng ô dòng mà trình duyệt đã vẽ
        slide.addText(runs, { ...base, y: inch(it.y - Math.max(...it.runs.map(r => r.size)) * 0.06), h: inch(Math.max(it.h, it.lh)), valign: 'middle' });
      } else {
        // nhiều dòng: giữ khoảng cách dòng như HTML, kéo lên nửa phần giãn dòng mà PowerPoint đặt ở trên
        const fs = Math.max(...it.runs.map(r => r.size));
        slide.addText(runs, { ...base, y: inch(it.y - (it.lh - fs * 1.15) / 2), h: inch(it.h + it.lh * 0.5),
          valign: 'top', lineSpacing: +(it.lh * 0.5).toFixed(1) });
      }
    }
  }
  const key = f.slice(0, 2);
  if (notes[key]) slide.addNotes(notes[key]);
  console.log('dựng', f, items.length, 'hình');
}
await browser.close();

// pptxgenjs ghi cả ghi chú vào một đoạn, PowerPoint không xuống dòng theo \n.
// Tách mỗi dòng thành một đoạn riêng, in đậm dòng Nói, Làm, Gõ theo thứ tự và dòng Hỏi.
const zip = await JSZip.loadAsync(await pptx.write({ outputType: 'nodebuffer' }));
const para = line => {
  const bold = /^(Nói|Làm|Gõ theo thứ tự)$/.test(line) || line.startsWith('Hỏi:');
  if (!line) return '<a:p><a:endParaRPr lang="vi-VN"/></a:p>';
  return `<a:p><a:r><a:rPr lang="vi-VN" sz="1600"${bold ? ' b="1"' : ''} dirty="0"/><a:t>${line}</a:t></a:r></a:p>`;
};
for (const name of Object.keys(zip.files).filter(n => /^ppt\/notesSlides\/notesSlide\d+\.xml$/.test(n))) {
  const xml = await zip.file(name).async('string');
  zip.file(name, xml.replace(
    /<a:p><a:r><a:rPr lang="en-US" dirty="0"\/><a:t>([\s\S]*?)<\/a:t><\/a:r><a:endParaRPr lang="en-US" dirty="0"\/><\/a:p>/,
    (_, text) => text.split(/\r?\n/).map(para).join('')));
}
await writeFile(OUT_PPTX, await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' }));
console.log('đã ghi', OUT_PPTX, '|', files.length, 'slide');
if (problems.length) {
  console.log('CẢNH BÁO, slide bị tràn:', problems.join(', '));
  process.exitCode = 1;
}

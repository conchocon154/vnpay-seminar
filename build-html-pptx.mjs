/*
 * Dựng bộ slide theo cách của skill claude-html-powerpoint-skill:
 * mỗi slide là một file HTML, Puppeteer render ra PNG, pptxgenjs ghép lại.
 *
 * Khác bản gốc một chỗ: có thêm ghi chú người thuyết trình, đọc từ notes.json.
 *
 * Chạy:  npm install  &&  node build-html-pptx.mjs
 */
import puppeteer from 'puppeteer';
import PptxGenJS from 'pptxgenjs';
import JSZip from 'jszip';
import { readdir, mkdir, readFile, writeFile } from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const SLIDES = path.join(ROOT, 'slides-html');
const OUT_IMG = path.join(ROOT, 'slides-html', 'render');
const OUT_PPTX = path.join(ROOT, 'VNPAY-seminar.pptx');

const W = 1920, H = 1080;

const notes = JSON.parse(await readFile(path.join(SLIDES, 'notes.json'), 'utf8'));
const files = (await readdir(SLIDES)).filter(f => f.endsWith('.html')).sort();
await mkdir(OUT_IMG, { recursive: true });

const browser = await puppeteer.launch({ args: ['--no-sandbox', '--font-render-hinting=none'] });
const page = await browser.newPage();
await page.setViewport({ width: W, height: H, deviceScaleFactor: 2 });

const images = [];
const overflow = [];
for (const f of files) {
  await page.goto('file://' + path.join(SLIDES, f), { waitUntil: 'networkidle0' });
  await page.evaluateHandle('document.fonts.ready');

  // Cảnh báo nếu nội dung vượt quá khổ slide, vì phần thừa sẽ bị cắt mất
  // Cảnh báo luôn khi khối code bị cắt ngang hoặc nội dung đè lên chân trang
  const over = await page.evaluate(() => {
    const d = document.documentElement, b = document.body;
    const issues = [];
    const h = Math.max(d.scrollHeight, b.scrollHeight) - 1080;
    if (h > 2) issues.push('cao hơn ' + h + 'px');
    document.querySelectorAll('pre').forEach(p => {
      if (p.scrollWidth > p.clientWidth + 1) issues.push('code rộng hơn ' + (p.scrollWidth - p.clientWidth) + 'px');
    });
    const foot = document.querySelector('.foot');
    const content = document.querySelector('.content');
    if (foot && content) {
      const bottom = Math.max(...[...content.querySelectorAll('*')].map(e => e.getBoundingClientRect().bottom));
      if (bottom > foot.getBoundingClientRect().top - 8) issues.push('đè chân trang');
    }
    return issues;
  });
  if (over.length) { console.log('  TRÀN', f + ':', over.join(', ')); overflow.push(f); }

  const png = path.join(OUT_IMG, f.replace('.html', '.png'));
  await page.screenshot({ path: png, clip: { x: 0, y: 0, width: W, height: H } });
  images.push({ png, key: f.slice(0, 2) });
  console.log('render', f);
}
await browser.close();

const pptx = new PptxGenJS();
pptx.defineLayout({ name: 'CUSTOM', width: 13.333, height: 7.5 });
pptx.layout = 'CUSTOM';
pptx.title = 'VNPAY Sandbox, Spring Boot';
pptx.author = 'DangLe';

for (const { png, key } of images) {
  const slide = pptx.addSlide();
  slide.addImage({ path: png, x: 0, y: 0, w: '100%', h: '100%' });
  if (notes[key]) slide.addNotes(notes[key]);
}

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
  const fixed = xml.replace(
    /<a:p><a:r><a:rPr lang="en-US" dirty="0"\/><a:t>([\s\S]*?)<\/a:t><\/a:r><a:endParaRPr lang="en-US" dirty="0"\/><\/a:p>/,
    (_, text) => text.split(/\r?\n/).map(para).join(''));
  zip.file(name, fixed);
}
await writeFile(OUT_PPTX, await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' }));
console.log('đã ghi', OUT_PPTX, '|', images.length, 'slide');
if (overflow.length) {
  console.log('CẢNH BÁO, slide bị cắt:', overflow.join(', '));
  process.exitCode = 1;
}

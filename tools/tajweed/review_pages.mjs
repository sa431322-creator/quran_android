// Builds an HTML viewer of the QPC V4 tajweed pages whose lines are wider than the page.
// usage: node tools/tajweed/review_pages.mjs <pages.json> <fonts dir> <sura_names.xml> <out.html>
// <fonts dir> holds p1.ttf … p604.ttf from https://static-cdn.tarteel.ai/qul/fonts/quran_fonts/v4-tajweed/ttf/
// e.g. pages.json = feature/tajweed/src/main/assets/tajweed/pages.json,
//      sura_names.xml = common/ui/core/src/main/res/values-ar/sura_names.xml
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const [pagesPath, fontsDir, namesPath, outPath] = process.argv.slice(2);
const { pages } = JSON.parse(readFileSync(pagesPath, 'utf8'));
const suraNames = [...readFileSync(namesPath, 'utf8').matchAll(/<item>([^<]*)<\/item>/g)].map((m) => m[1]);

function loadFont(file) {
  const b = readFileSync(file);
  const t = {};
  for (let i = 0; i < b.readUInt16BE(4); i++) { const o = 12 + i * 16; t[b.toString('ascii', o, o + 4)] = b.readUInt32BE(o + 8); }
  const upem = b.readUInt16BE(t.head + 18);
  const nhm = b.readUInt16BE(t.hhea + 34);
  const adv = (g) => b.readUInt16BE(t.hmtx + 4 * Math.min(g, nhm - 1));
  const c = t.cmap; const st = c + b.readUInt32BE(c + 8); const sc = b.readUInt16BE(st + 6) / 2; const cmap = {};
  for (let k = 0; k < sc; k++) {
    const end = b.readUInt16BE(st + 14 + k * 2), start = b.readUInt16BE(st + 16 + sc * 2 + k * 2);
    const delta = b.readInt16BE(st + 16 + sc * 4 + k * 2), roOff = st + 16 + sc * 6 + k * 2, ro = b.readUInt16BE(roOff);
    for (let ch = start; ch <= end && ch !== 0xffff; ch++) {
      let g; if (ro === 0) g = (ch + delta) & 0xffff; else { g = b.readUInt16BE(roOff + ro + (ch - start) * 2); if (g) g = (g + delta) & 0xffff; }
      cmap[ch] = g;
    }
  }
  return (word) => [...word].reduce((s, ch) => s + adv(cmap[ch.codePointAt(0)] ?? 0), 0) / upem;
}

// same decoration rules as the app (TajweedPage.kt)
const slots = pages.map((p) => Array(p.p <= 2 ? 8 : 15).fill(null).map(() => ({ type: 'blank' })));
pages.forEach((p, i) => {
  p.l.forEach(([n, w]) => { slots[i][n - 1] = { type: 'text', words: w.split('|') }; });
  p.h.forEach(([sura, line]) => {
    const decos = sura === 1 || sura === 9 ? [{ type: 'header', sura }] : [{ type: 'header', sura }, { type: 'basmala' }];
    decos.forEach((d, j) => {
      let target = i, slot = line - decos.length + j - 1;
      if (slot < 0) { target = i - 1; slot += slots[target].length; }
      if (target >= 0 && slots[target][slot].type === 'blank') slots[target][slot] = d;
    });
  });
});

const flagged = [];
pages.forEach((p, i) => {
  const width = loadFont(join(fontsDir, `p${p.p}.ttf`));
  let max = 0;
  const lines = slots[i].map((s) => {
    if (s.type !== 'text') return s;
    const em = s.words.reduce((sum, w) => sum + width(w), 0);
    max = Math.max(max, em);
    return { ...s, em: Math.round(em * 100) / 100 };
  });
  if (max > 18) flagged.push({ page: p.p, sura: suraNames[p.s - 1], juz: p.j, max: Math.round(max * 10) / 10, lines });
});

const data = JSON.stringify({ suraNames, pages: flagged });
const html = `<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>صفحات التجويد غير المتناسقة</title>
<style>
  :root { --bg: #f7f0e1; --paper: #fcf8ef; --ink: #1c1a16; --muted: #5a5347; --primary: #1f4e56; --gold: #c8a45e; --bad: #b3261e; --border: #e4dac4; }
  * { box-sizing: border-box; }
  body { margin: 0; background: var(--bg); color: var(--ink); font-family: "Noto Naskh Arabic", Tahoma, sans-serif; }
  header { background: var(--bg); border-bottom: 1px solid var(--border); padding: 12px 16px; }
  h1 { margin: 0 0 4px; font-size: 20px; color: var(--primary); }
  .note { margin: 0; font-size: 13px; color: var(--muted); line-height: 1.7; }
  .controls { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-top: 8px; font-size: 13px; }
  .controls button { font: inherit; border: 1px solid var(--primary); background: transparent; color: var(--primary); border-radius: 99px; padding: 4px 12px; cursor: pointer; }
  .controls button[aria-pressed="true"] { background: var(--primary); color: var(--paper); }
  nav { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
  nav a { font-size: 12px; text-decoration: none; color: var(--ink); border: 1px solid var(--border); background: var(--paper); border-radius: 6px; padding: 2px 8px; white-space: nowrap; }
  nav a b { color: var(--bad); font-weight: 600; }
  main { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 420px), 1fr)); gap: 16px; padding: 16px; }
  article { background: var(--paper); border: 1px solid var(--border); border-radius: 10px; padding: 10px 12px 14px; scroll-margin-top: 12px; }
  article h2 { margin: 0 0 8px; font-size: 15px; display: flex; justify-content: space-between; gap: 8px; }
  article h2 span { color: var(--muted); font-weight: 400; font-size: 13px; }
  .page { container-type: inline-size; }
  .row { position: relative; height: 11.5cqw; display: flex; align-items: center; white-space: nowrap; }
  .row.text { font-size: calc(100cqw / 17.4); }
  .row.justify { justify-content: space-between; }
  .row.center { justify-content: center; gap: 0.25em; }
  .row.bad { outline: 1.5px solid var(--bad); outline-offset: -1px; border-radius: 3px; background: rgba(179, 38, 30, 0.05); }
  .row.bad::after { content: attr(data-em) " em"; position: absolute; left: 2px; top: 1px; font: 600 10px/1 Tahoma, sans-serif; color: #fff; background: var(--bad); border-radius: 3px; padding: 2px 4px; }
  .row.text span { display: inline-block; }
  .header { justify-content: center; border: 1.5px solid var(--gold); border-radius: 6px; margin: 1.2cqw 0; height: 9.1cqw; color: var(--primary); font-weight: 700; font-size: 4cqw; background: rgba(31, 78, 86, 0.06); }
  .basmala { justify-content: center; font-size: 4.6cqw; color: var(--muted); }
  footer { padding: 0 16px 24px; font-size: 12px; color: var(--muted); }
</style>
</head>
<body>
<header>
  <h1>٤٦ صفحة فيها أسطر غير متناسقة في خط التجويد V4</h1>
  <p class="note">في هذه الصفحات يرسم خط QUL نفسه بعض الأسطر أعرض من الصفحة (السطر العادي حوالي ١٦٫٢ em). الأسطر المعنية محاطة بإطار أحمر ومعها عرضها. «كما في التطبيق»: يُصغَّر السطر ليتسع. «الخط كما هو»: كما يرسمه الخط بلا تعديل.</p>
  <div class="controls">
    <span>العرض:</span>
    <button id="app" aria-pressed="true">كما في التطبيق</button>
    <button id="raw" aria-pressed="false">الخط كما هو</button>
  </div>
  <nav id="nav"></nav>
</header>
<main id="pages"></main>
<footer>الخطوط تُحمَّل من static-cdn.tarteel.ai (QUL، QPC V4 Tajweed)، وترتيب الأسطر من quran.com (mushaf 19). يحتاج العرض إلى اتصال بالإنترنت.</footer>
<script>
const DATA = ${data};
const fa = (n) => String(n).replace(/[0-9]/g, (d) => '٠١٢٣٤٥٦٧٨٩'[d]).replace('.', '٫');
const fonts = document.createElement('style');
fonts.textContent = DATA.pages.map((p) => '@font-face{font-family:"p' + p.page + '";src:url("https://static-cdn.tarteel.ai/qul/fonts/quran_fonts/v4-tajweed/ttf/p' + p.page + '.ttf") format("truetype");font-display:block}').join('');
document.head.append(fonts);
const nav = document.getElementById('nav');
const main = document.getElementById('pages');
for (const p of DATA.pages) {
  const link = document.createElement('a');
  link.href = '#p' + p.page;
  link.innerHTML = fa(p.page) + ' · <b>' + fa(p.max) + '</b>';
  nav.append(link);
  const article = document.createElement('article');
  article.id = 'p' + p.page;
  article.innerHTML = '<h2>صفحة ' + fa(p.page) + ' <span>' + p.sura + ' · الجزء ' + fa(p.juz) + ' · أعرض سطر ' + fa(p.max) + ' em</span></h2>';
  const page = document.createElement('div');
  page.className = 'page';
  for (const line of p.lines) {
    const row = document.createElement('div');
    row.className = 'row';
    if (line.type === 'text') {
      row.classList.add('text', line.em >= 14.79 && p.page > 2 ? 'justify' : 'center');
      row.style.fontFamily = '"p' + p.page + '"';
      row.dataset.em = fa(line.em);
      row.dataset.scale = Math.min(1, 16.4 / line.em);
      if (line.em > 18) row.classList.add('bad');
      for (const word of line.words) { const span = document.createElement('span'); span.textContent = word; row.append(span); }
    } else if (line.type === 'header') {
      row.classList.add('header');
      row.textContent = 'سورة ' + DATA.suraNames[line.sura - 1];
    } else if (line.type === 'basmala') {
      row.classList.add('basmala');
      row.textContent = 'بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ';
    }
    page.append(row);
  }
  article.append(page);
  main.append(article);
}
function mode(asApp) {
  document.getElementById('app').setAttribute('aria-pressed', asApp);
  document.getElementById('raw').setAttribute('aria-pressed', !asApp);
  for (const row of document.querySelectorAll('.row.text')) {
    row.style.fontSize = asApp ? 'calc(100cqw / 17.4 * ' + row.dataset.scale + ')' : '';
    row.style.justifyContent = !asApp && row.classList.contains('bad') ? 'flex-start' : '';
    row.style.overflow = asApp ? '' : 'visible';
  }
}
document.getElementById('app').onclick = () => mode(true);
document.getElementById('raw').onclick = () => mode(false);
mode(true);
</script>
</body>
</html>
`;
writeFileSync(outPath, html);
console.log(`${flagged.length} pages -> ${outPath}`);
console.log(flagged.map((p) => `${p.page}:${p.max}`).join(' '));

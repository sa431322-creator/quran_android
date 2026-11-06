// Downloads what the page check needs into tools/mushaf/verify/.cache (git-ignored):
//   fonts/p<N>.ttf     QPC V2 page fonts (QUL / Tarteel CDN)
//   reference/<N>.png  V2 page images (King Saud University electronic mushaf), the reference
//   words-api.tsv      every word of the V2 layout as the quran.com API gives it: page, line,
//                      index on the line, glyph code, Uthmani text and type (word / end)
//   words.tsv          the same with tools/mushaf/layout-corrections.mjs applied (what the
//                      app's layout holds), rewritten on every run
// Files already there are kept, so a second run only fills gaps.
//
//   node tools/mushaf/verify/prepare.mjs
import { existsSync, mkdirSync, readFileSync, renameSync, statSync, writeFileSync } from 'node:fs';
import { correctPage } from '../layout-corrections.mjs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const cache = join(here, '.cache');
const PAGES = 604;
const FONT_URL = (p) => `https://static-cdn.tarteel.ai/qul/fonts/quran_fonts/v2/ttf/p${p}.ttf`;
const REFERENCE_URL = (p) => `https://quran.ksu.edu.sa/png_big/${p}.png`;
const WORDS_URL = (p) => `https://api.quran.com/api/v4/verses/by_page/${p}?words=true` +
  '&word_fields=code_v2,text_uthmani,line_number,page_number&per_page=50';

async function retry(what, fn) {
  for (let attempt = 1; ; attempt++) {
    try {
      return await fn();
    } catch (e) {
      if (attempt >= 5) throw new Error(`${what}: ${e.message}`);
      await new Promise((r) => setTimeout(r, 1000 * attempt));
    }
  }
}

async function download(url, file, minBytes) {
  if (existsSync(file) && statSync(file).size >= minBytes) return false;
  await retry(url, async () => {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const bytes = Buffer.from(await response.arrayBuffer());
    if (bytes.length < minBytes) throw new Error(`only ${bytes.length} bytes`);
    writeFileSync(file, bytes);
  });
  return true;
}

async function inBatches(items, size, fn) {
  for (let i = 0; i < items.length; i += size) await Promise.all(items.slice(i, i + size).map(fn));
}

const pages = Array.from({ length: PAGES }, (_, i) => i + 1);
for (const dir of ['fonts', 'reference']) mkdirSync(join(cache, dir), { recursive: true });

let fetched = 0;
await inBatches(pages, 8, async (p) => {
  if (await download(FONT_URL(p), join(cache, 'fonts', `p${p}.ttf`), 1000)) fetched++;
  if (await download(REFERENCE_URL(p), join(cache, 'reference', `${p}.png`), 1000)) fetched++;
});
console.log(`fonts and reference images ready (${fetched} downloaded now)`);

const wordsFile = join(cache, 'words-api.tsv');
// (an older cache kept the API words in words.tsv)
if (!existsSync(wordsFile) && existsSync(join(cache, 'words.tsv'))) renameSync(join(cache, 'words.tsv'), wordsFile);
if (!existsSync(wordsFile)) {
  const rows = new Map(); // word id -> row
  await inBatches(pages, 6, async (p) => {
    const json = await retry(`words of page ${p}`, async () => {
      const response = await fetch(WORDS_URL(p));
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return response.json();
    });
    for (const verse of json.verses) {
      verse.words.forEach((w) => {
        rows.set(w.id, [w.page_number, w.line_number, verse.verse_key, w.position, w.char_type_name,
          w.code_v2, (w.text_uthmani ?? '').replace(/\t/g, ' ')]);
      });
    }
  });
  const sorted = [...rows.entries()].sort((a, b) => a[0] - b[0]).map(([, r]) => r);
  // index of each word on its line, in reading order
  const counters = new Map();
  const out = ['page\tline\tindex\tayah\tposition\ttype\tcode\tuthmani'];
  for (const [page, line, ayah, position, type, code, text] of sorted) {
    const k = `${page}:${line}`;
    const index = counters.get(k) ?? 0;
    counters.set(k, index + 1);
    out.push([page, line, index, ayah, position, type, code, text].join('\t'));
  }
  writeFileSync(wordsFile, out.join('\n') + '\n');
  console.log(`words-api.tsv: ${sorted.length} words`);
} else {
  console.log('words-api.tsv already there');
}

// words.tsv: the API words with the layout corrections, re-indexed on each line
const [header, ...rows] = readFileSync(wordsFile, 'utf8').trim().split('\n');
const byPage = new Map();
for (const row of rows) {
  const [page, line, , ayah, position, type, code, text] = row.split('\t');
  const p = Number(page);
  if (!byPage.has(p)) byPage.set(p, []);
  byPage.get(p).push({ page: p, line: Number(line), ayah, position, type, code, text, key: `${ayah}:${position}` });
}
const out = [header];
for (const [p, words] of [...byPage.entries()].sort((a, b) => a[0] - b[0])) {
  const corrected = correctPage(p, words).sort((a, b) => a.line - b.line);
  const counters = new Map();
  for (const w of corrected) {
    const index = counters.get(w.line) ?? 0;
    counters.set(w.line, index + 1);
    out.push([w.page, w.line, index, w.ayah, w.position, w.type, w.code, w.text].join('\t'));
  }
}
writeFileSync(join(cache, 'words.tsv'), out.join('\n') + '\n');
console.log(`words.tsv: ${out.length - 1} glyph units after corrections`);

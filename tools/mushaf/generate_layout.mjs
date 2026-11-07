// Builds feature/mushaf/src/main/assets/mushaf/v4/pages.json from the quran.com API.
//
// The QPC V4 tajweed fonts (one font per page, from QUL) are glyph based: every word is one
// glyph code in that page's font. Mushaf 19 on quran.com is the V4 (1441H) layout, so its
// code_v2 + line_number give the exact glyphs and lines each page font expects.
//
// Usage: node tools/mushaf/generate_layout.mjs
//
// The plain King Fahd Madinah mushaf (QPC V2, 1421H) takes --edition v2 (with the line-break
// corrections of layout-corrections.mjs); --pages limits it to a few pages:
//   node tools/mushaf/generate_layout.mjs --edition v2 --out feature/mushaf/src/main/assets/mushaf/v2/pages.json
//
// The API ignores its mushaf parameter: the glyph field asked for decides the page_number and
// line_number it returns, and code_v2 gives the V2 layout that both editions here share.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { correctPage } from './layout-corrections.mjs';

const PAGES = 604;
const EDITIONS = {
  v4: { glyphs: 'code_v2', mushaf: 19, name: 'QPC V4 tajweed 1441H' },
  // v2 takes the line corrections in layout-corrections.mjs, so its lines break like the print
  v2: { glyphs: 'code_v2', mushaf: 1, name: 'QPC V2 Madinah 1421H', corrected: true },
};
const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

function arg(name, fallback) {
  const at = process.argv.indexOf(`--${name}`);
  return at > 0 ? process.argv[at + 1] : fallback;
}
const EDITION = EDITIONS[arg('edition', 'v4')];
if (!EDITION) throw new Error('--edition must be v4 or v2');
// only these pages (a trial), or every page
const wanted = arg('pages', '').split(',').filter(Boolean).map(Number);
const outputPages = wanted.length ? wanted : Array.from({ length: PAGES }, (_, i) => i + 1);
// a verse can start on the page before, so the pages around each wanted page are fetched too
const fetchPages = [...new Set(outputPages.flatMap((p) => [p - 1, p, p + 1]))]
  .filter((p) => p >= 1 && p <= PAGES)
  .sort((a, b) => a - b);
const out = arg('out', join(root, 'feature', 'mushaf', 'src', 'main', 'assets', 'mushaf', 'v4', 'pages.json'));

async function fetchPage(page) {
  const url = `https://api.quran.com/api/v4/verses/by_page/${page}` +
    `?words=true&word_fields=${EDITION.glyphs},line_number,page_number&fields=juz_number` +
    `&mushaf=${EDITION.mushaf}&per_page=50`;
  for (let attempt = 1; ; attempt++) {
    try {
      const response = await fetch(url);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const json = await response.json();
      if (json.pagination && json.pagination.total_pages > 1) {
        throw new Error(`page ${page} has more than 50 verses`);
      }
      return json.verses;
    } catch (e) {
      if (attempt >= 4) throw e;
      await new Promise((r) => setTimeout(r, 1000 * attempt));
    }
  }
}

// a verse can run over a page boundary, so words are bucketed by their own page_number
const pageWords = Array.from({ length: PAGES + 1 }, () => new Map());
for (const page of fetchPages) {
  for (const verse of await fetchPage(page)) {
    const [sura, ayah] = verse.verse_key.split(':').map(Number);
    verse.words.forEach((word, index) => {
      pageWords[word.page_number].set(word.id, { ...word, sura, ayah, index, juz: verse.juz_number });
    });
  }
  if (page % 50 === 0) console.log(`page ${page}`);
}

const pages = [];
for (const page of outputPages) {
  let words = [...pageWords[page].values()]
    .sort((a, b) => a.sura - b.sura || a.ayah - b.ayah || a.index - b.index);
  if (EDITION.corrected) {
    words = correctPage(page, words.map((w) => ({
      ...w, line: w.line_number, key: `${w.sura}:${w.ayah}:${w.index + 1}`, code: w[EDITION.glyphs],
    }))).map((w) => ({ ...w, line_number: w.line, [EDITION.glyphs]: w.code }));
  }
  if (words.length === 0) throw new Error(`no words on page ${page}`);
  const lines = new Map();
  const suraStarts = [];
  // each word's ayah in reading order, as runs of [sura, ayah, word count]
  const wordAyahs = [];
  for (const word of words) {
    const last = wordAyahs[wordAyahs.length - 1];
    if (last && last[0] === word.sura && last[1] === word.ayah) last[2]++;
    else wordAyahs.push([word.sura, word.ayah, 1]);
    if (!lines.has(word.line_number)) lines.set(word.line_number, []);
    lines.get(word.line_number).push(word[EDITION.glyphs]);
    if (word.ayah === 1 && word.index === 0 && !word.part) suraStarts.push([word.sura, word.line_number]);
  }
  pages.push({
    p: page,
    s: words[0].sura,
    j: words[0].juz,
    // [sura, ayah] of the page's first word, to move between this and other mushaf layouts
    f: [words[0].sura, words[0].ayah],
    // [line number, words separated by "|" (a word's glyphs can include a space)]
    l: [...lines.entries()].sort((a, b) => a[0] - b[0]).map(([n, w]) => [n, w.join('|')]),
    // [sura, line of its first ayah]
    h: suraStarts,
    w: wordAyahs,
  });
}

mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, JSON.stringify({ source: `quran.com api v4, ${EDITION.glyphs} (${EDITION.name})`, pages }));
console.log(`wrote ${out}`);

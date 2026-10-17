// Builds feature/tajweed/src/main/assets/tajweed/pages.json from the quran.com API.
//
// The QPC V4 tajweed fonts (one font per page, from QUL) are glyph based: every word is one
// glyph code in that page's font. Mushaf 19 on quran.com is the V4 (1441H) layout, so its
// code_v2 + line_number give the exact glyphs and lines each page font expects.
//
// Usage: node tools/tajweed/generate_pages.mjs
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const MUSHAF_V4_TAJWEED = 19;
const PAGES = 604;
const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const out = join(root, 'feature', 'tajweed', 'src', 'main', 'assets', 'tajweed', 'pages.json');

async function fetchPage(page) {
  const url = `https://api.quran.com/api/v4/verses/by_page/${page}` +
    `?words=true&word_fields=code_v2,line_number,page_number&fields=juz_number` +
    `&mushaf=${MUSHAF_V4_TAJWEED}&per_page=50`;
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
for (let page = 1; page <= PAGES; page++) {
  for (const verse of await fetchPage(page)) {
    const [sura, ayah] = verse.verse_key.split(':').map(Number);
    verse.words.forEach((word, index) => {
      pageWords[word.page_number].set(word.id, { ...word, sura, ayah, index, juz: verse.juz_number });
    });
  }
  if (page % 50 === 0) console.log(`page ${page}`);
}

const pages = [];
for (let page = 1; page <= PAGES; page++) {
  const words = [...pageWords[page].values()]
    .sort((a, b) => a.sura - b.sura || a.ayah - b.ayah || a.index - b.index);
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
    lines.get(word.line_number).push(word.code_v2);
    if (word.ayah === 1 && word.index === 0) suraStarts.push([word.sura, word.line_number]);
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
writeFileSync(out, JSON.stringify({ source: 'quran.com api v4, mushaf 19 (QPC V4 tajweed)', pages }));
console.log(`wrote ${out}`);

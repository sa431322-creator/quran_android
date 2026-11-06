// Data checks of a text mushaf layout (assets/mushaf/v2/pages.json), page by page, before any
// image comparison. Writes docs/text-mushaf/reports/data-<date>.md and .json.
//
//   node tools/mushaf/verify/check_data.mjs [--layout <pages.json>]
//
// Checks:
//   pages      604 pages, numbered 1..604
//   rows       every row of the page is used once: text lines plus the sura header and basmala
//              rows the reader adds (15 rows, 8 on pages 1-2), no row used twice, no blank row
//   words      77,429 words and 6,236 ayah markers in all, each line's glyph count matching the
//              ayahs it holds, every ayah 1:1..114:6 once and in order, an ayah only split across
//              consecutive pages
//   sajda      the page and line of each of the 15 sajda ayahs (for the image check)
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const at = process.argv.indexOf('--layout');
const layoutPath = at > 0 ? process.argv[at + 1]
  : join(root, 'feature', 'mushaf', 'src', 'main', 'assets', 'mushaf', 'v2', 'pages.json');
const layout = JSON.parse(readFileSync(layoutPath, 'utf8'));

const AYAHS_PER_SURA = [7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111,
  110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54,
  53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12,
  30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15,
  21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6];
const WORDS = 77429;
const AYAHS = 6236;
const SAJDA = [[7, 206], [13, 15], [16, 50], [17, 109], [19, 58], [22, 18], [22, 77], [25, 60],
  [27, 26], [32, 15], [38, 24], [41, 38], [53, 62], [84, 21], [96, 19]];

const problems = []; // { page, check, detail }
const problem = (page, check, detail) => problems.push({ page, check, detail });

const pages = layout.pages.slice().sort((a, b) => a.p - b.p);
if (pages.length !== 604) problem(0, 'pages', `${pages.length} pages instead of 604`);
pages.forEach((p, i) => { if (p.p !== i + 1) problem(p.p, 'pages', `page ${p.p} at position ${i + 1}`); });

// rows: text lines plus decorations, placed like buildMushafPages does
const rowsOf = pages.map((p) => Array(p.p <= 2 ? 8 : 15).fill(null));
pages.forEach((page, index) => {
  for (const [line, words] of page.l) {
    if (line < 1 || line > rowsOf[index].length) problem(page.p, 'rows', `line ${line} is outside the page`);
    else if (rowsOf[index][line - 1]) problem(page.p, 'rows', `line ${line} appears twice`);
    else rowsOf[index][line - 1] = `text:${words.split('|').length}`;
  }
});
pages.forEach((page, index) => {
  for (const [sura, line] of page.h) {
    const decorations = sura === 1 || sura === 9 ? ['header'] : ['header', 'basmala'];
    decorations.forEach((kind, i) => {
      let target = index;
      let slot = line - decorations.length + i - 1;
      if (slot < 0) {
        target = index - 1;
        slot += rowsOf[target].length;
      }
      if (target < 0) return problem(page.p, 'rows', `sura ${sura}: no page before for its ${kind}`);
      if (rowsOf[target][slot]) {
        problem(pages[target].p, 'rows', `sura ${sura} ${kind} would cover row ${slot + 1} (${rowsOf[target][slot]})`);
      } else {
        rowsOf[target][slot] = `${kind}:${sura}`;
      }
    });
  }
});
rowsOf.forEach((rows, index) => {
  rows.forEach((row, i) => { if (!row) problem(pages[index].p, 'rows', `row ${i + 1} is blank`); });
});

// words and ayahs
let glyphs = 0;
let previous = null; // [sura, ayah, page]
const expected = [];
AYAHS_PER_SURA.forEach((n, s) => { for (let a = 1; a <= n; a++) expected.push([s + 1, a]); });
let next = 0;
const ayahPages = new Map();
for (const page of pages) {
  const lineGlyphs = page.l.reduce((sum, [, words]) => sum + words.split('|').length, 0);
  const runGlyphs = page.w.reduce((sum, run) => sum + run[2], 0);
  if (lineGlyphs !== runGlyphs) problem(page.p, 'words', `${lineGlyphs} glyphs on the lines, ${runGlyphs} in the ayah runs`);
  glyphs += runGlyphs;
  for (const [sura, ayah] of page.w) {
    const k = `${sura}:${ayah}`;
    if (previous && previous[0] === sura && previous[1] === ayah) {
      if (page.p !== previous[2] + 1 && page.p !== previous[2]) problem(page.p, 'words', `${k} continues from page ${previous[2]}`);
      if (page.p === previous[2]) problem(page.p, 'words', `${k} appears twice on the page`);
    } else {
      const want = expected[next];
      if (!want || want[0] !== sura || want[1] !== ayah) {
        problem(page.p, 'words', `${k} where ${want ? want.join(':') : 'nothing'} was expected`);
      }
      next++;
    }
    previous = [sura, ayah, page.p];
    if (!ayahPages.has(k)) ayahPages.set(k, page.p);
  }
}
if (next !== AYAHS) problem(0, 'words', `${next} ayahs instead of ${AYAHS}`);
if (glyphs !== WORDS + AYAHS) problem(0, 'words', `${glyphs} glyphs instead of ${WORDS} words + ${AYAHS} ayah markers`);

// sajda: the page and line holding each sajda ayah's last glyph (its marker sits at the end)
const sajda = SAJDA.map(([s, a]) => {
  for (const page of pages) {
    let word = 0;
    const runs = page.w.map(([rs, ra, n]) => { const r = [rs, ra, word, word + n]; word += n; return r; });
    const run = runs.find((r) => r[0] === s && r[1] === a);
    if (!run) continue;
    // a run may continue on the next page: take the last page holding it
    const nextPage = pages[page.p];
    if (nextPage && nextPage.w[0][0] === s && nextPage.w[0][1] === a) continue;
    let index = 0;
    for (const [line, words] of page.l.slice().sort((x, y) => x[0] - y[0])) {
      const n = words.split('|').length;
      if (run[3] - 1 < index + n) return { ayah: `${s}:${a}`, page: page.p, line };
      index += n;
    }
  }
  problem(0, 'sajda', `${s}:${a} not found`);
  return { ayah: `${s}:${a}`, page: null, line: null };
});

// report
const date = new Date().toISOString().slice(0, 10);
const byCheck = (c) => problems.filter((p) => p.check === c);
const checks = ['pages', 'rows', 'words', 'sajda'];
const md = [];
md.push(`# Text mushaf data check — ${date}`, '');
md.push(`Layout: \`${layoutPath.replace(root, '').replace(/\\/g, '/').replace(/^\//, '')}\` (${layout.source ?? 'unknown source'})`, '');
md.push('| check | result |', '|---|---|');
for (const c of checks) md.push(`| ${c} | ${byCheck(c).length === 0 ? 'pass' : `**${byCheck(c).length} problems**`} |`);
md.push('', `Totals: ${pages.length} pages, ${next} ayahs, ${glyphs} glyphs (${glyphs - AYAHS} words + ${AYAHS} ayah markers).`, '');
md.push('## Sajda places', '', '| ayah | page | line |', '|---|---|---|');
for (const s of sajda) md.push(`| ${s.ayah} | ${s.page} | ${s.line} |`);
if (problems.length) {
  md.push('', '## Problems', '', '| page | check | detail |', '|---|---|---|');
  for (const p of problems) md.push(`| ${p.page || '—'} | ${p.check} | ${p.detail} |`);
} else {
  md.push('', 'No problems.');
}
const outDir = join(root, 'docs', 'text-mushaf', 'reports');
mkdirSync(outDir, { recursive: true });
writeFileSync(join(outDir, `data-${date}.md`), md.join('\n') + '\n');
writeFileSync(join(outDir, `data-${date}.json`), JSON.stringify({ date, problems, sajda }, null, 1));
console.log(md.slice(0, 12).join('\n'));
console.log(problems.length ? `${problems.length} problems; see the report` : 'clean');
process.exit(problems.length ? 1 : 0);

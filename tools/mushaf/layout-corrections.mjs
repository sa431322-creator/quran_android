// Corrections to the V2 line data from the quran.com API, so the text mushaf breaks lines where
// the printed Madinah mushaf V2 does. Only which line a word is on changes; glyphs never change.
//
// Each one was found by tools/mushaf/verify (PageCheck.java: the word is not at the end of its
// line in the print, and the next printed line starts with it) and confirmed two ways:
//   - the V2 reference page image (docs/text-mushaf/reports, crops p<page>-r<line>.png);
//   - the V2 page font's own metrics: with the API's line, one line is the widest on its page
//     (up to +2.6 em) and the next one short; with the print's line both are the page's usual
//     width (99% of all lines are within 0.23 em of their page's median).
// Report: docs/text-mushaf/reports/pages-2026-11-06.md.

/** A word (sura:ayah:position) that the print puts on another line of its page. */
export const MOVES = [
  { page: 27, word: '2:181:5', from: 14, to: 15 }, // فَإِنَّمَآ: 17.42/13.96 em → 15.75/15.63
  { page: 177, word: '8:6:10', from: 11, to: 12 }, // وَهُمْ: 17.10/14.12 → 15.66/15.57
  { page: 400, word: '29:33:11', from: 5, to: 6 }, // وَقَالُوا۟: 17.50/13.46 → 15.48/15.48
  { page: 443, word: '36:52:10', from: 12, to: 13 }, // ٱلرَّحْمَـٰنُ: 18.21/13.25 → 15.67/15.79
  { page: 589, word: '84:21:7', from: 13, to: 14 }, // ayah marker ٢١: 16.50/14.88 → 15.63/15.75
];

/**
 * A word whose glyphs the print splits over two lines: one glyph per part, with its part of the
 * Uthmani text. بَعْدَ مَا (13:37:8) is one word in the data (two glyphs) and ends line 6 with
 * بَعْدَ, starts line 7 with مَا in the print.
 */
export const SPLITS = [
  { page: 254, word: '13:37:8', lines: [6, 7], texts: ['بَعْدَ', 'مَا'] },
];

/**
 * Applies the corrections to the words of one page. Each word is
 * { page, line, key: 'sura:ayah:position', code, text, ...anything else } in reading order;
 * returns them in reading order, a split word becoming one entry per glyph (same key, `part` 0, 1).
 */
export function correctPage(page, words) {
  const out = [];
  for (const word of words) {
    const move = MOVES.find((m) => m.page === page && m.word === word.key);
    if (move) {
      if (word.line !== move.from) throw new Error(`correction ${move.word}: expected on line ${move.from}, found on ${word.line}`);
      out.push({ ...word, line: move.to });
      continue;
    }
    const split = SPLITS.find((s) => s.page === page && s.word === word.key);
    if (split) {
      const glyphs = [...word.code.replace(/ /g, '')];
      if (glyphs.length !== split.lines.length) throw new Error(`split ${split.word}: ${glyphs.length} glyphs`);
      glyphs.forEach((glyph, part) => out.push({ ...word, line: split.lines[part], code: glyph, text: split.texts[part], part }));
      continue;
    }
    out.push(word);
  }
  for (const c of [...MOVES, ...SPLITS]) {
    if (c.page === page && !words.some((w) => w.key === c.word)) throw new Error(`correction ${c.word}: not on page ${page}`);
  }
  return out;
}

/** Extra glyph units the splits add to the whole mushaf. */
export const EXTRA_UNITS = SPLITS.reduce((n, s) => n + s.lines.length - 1, 0);

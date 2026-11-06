// Writes the page tables of a mushaf edition (MadaniV2DataSource.kt) from its layout.
//
// The layout (assets/mushaf/v2/pages.json, from generate_layout.mjs) says which ayah each word
// belongs to, page by page. Every table the reader needs follows from that:
//   - the page of each sura      = the page of its first word
//   - sura/ayah starting a page  = the ayah of the page's first word
//   - the page of each juz       = the page of the juz's first ayah
//   - juz shown on a page        = the juz of the page's first word, when a juz starts mid-page
//   - quarter starting on a page = the last quarter whose first ayah is on that page
// Ayah counts, makki/madani and the 240 quarter start ayahs do not depend on the print, so they
// are read from the existing MadaniDataSource.kt.
//
// Usage:
//   node tools/mushaf/generate_data_source.mjs                 writes MadaniV2DataSource.kt
//   node tools/mushaf/generate_data_source.mjs --check <layout.json>
//     checks the rules: builds tables from <layout.json> and compares them with
//     MadaniDataSource.kt (only meaningful for a layout of that same print).
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const SOURCE_KT = join(root, 'pages', 'data', 'madani', 'src', 'main', 'kotlin', 'com', 'quran',
  'labs', 'androidquran', 'pages', 'data', 'madani', 'MadaniDataSource.kt');
const LAYOUT = join(root, 'feature', 'mushaf', 'src', 'main', 'assets', 'mushaf', 'v2', 'pages.json');
const OUT = join(root, 'pages', 'data', 'madaniv2', 'src', 'main', 'kotlin', 'com', 'quran',
  'labs', 'androidquran', 'pages', 'data', 'madaniv2', 'MadaniV2DataSource.kt');

function intArray(kt, name) {
  const body = kt.match(new RegExp(`${name}\\s*=\\s*intArrayOf\\(([\\s\\S]*?)\\)`))[1];
  return body.replace(/\/\*[\s\S]*?\*\//g, '').split(',').map((s) => s.trim()).filter(Boolean).map(Number);
}
function booleanArray(kt, name) {
  const body = kt.match(new RegExp(`${name}\\s*=\\s*booleanArrayOf\\(([\\s\\S]*?)\\)`))[1];
  return body.replace(/\/\*[\s\S]*?\*\//g, '').split(',').map((s) => s.trim()).filter(Boolean).map((s) => s === 'true');
}
function quarters(kt) {
  const body = kt.split('quartersArray')[1].split('manzilPageArray')[0];
  return [...body.matchAll(/SuraAyah\((\d+),\s*(\d+)\)/g)].map((m) => [Number(m[1]), Number(m[2])]);
}
function overrides(kt) {
  const body = kt.match(/juzDisplayPageArrayOverride\s*=\s*mapOf\(([^)]*)\)/)[1];
  return Object.fromEntries([...body.matchAll(/(\d+)\s+to\s+(\d+)/g)].map((m) => [Number(m[1]), Number(m[2])]));
}

const key = (sura, ayah) => sura * 1000 + ayah;

/**
 * The juz printed in the header of each page where a juz starts below the page's first line,
 * read from the V2 reference pages (quran.ksu.edu.sa/png_big/<page>.png) on 2026-11-06.
 * Page 62 starts with 3:92, where the print marks juz 4 (the quarter table says 3:93).
 */
const PRINTED_JUZ_HEADERS = { 62: 4, 121: 6, 201: 10, 502: 26 };

/** Tables for a layout whose pages carry `w`: runs of [sura, ayah, word count] in reading order. */
export function buildTables(layout, quarterStarts) {
  const pages = layout.pages.slice().sort((a, b) => a.p - b.p);
  if (pages.length !== 604 || pages.some((p, i) => p.p !== i + 1)) throw new Error('layout must have pages 1..604');
  const firstPageOfAyah = new Map();
  for (const page of pages) {
    for (const [sura, ayah] of page.w) {
      if (!firstPageOfAyah.has(key(sura, ayah))) firstPageOfAyah.set(key(sura, ayah), page.p);
    }
  }
  const pageOf = (sura, ayah) => {
    const p = firstPageOfAyah.get(key(sura, ayah));
    if (!p) throw new Error(`ayah ${sura}:${ayah} is on no page`);
    return p;
  };
  const suraForPage = pages.map((p) => p.w[0][0]);
  const ayahForPage = pages.map((p) => p.w[0][1]);
  const pageForSura = Array.from({ length: 114 }, (_, i) => pageOf(i + 1, 1));
  const juzStarts = Array.from({ length: 30 }, (_, j) => quarterStarts[j * 8]);
  const pageForJuz = juzStarts.map(([s, a]) => pageOf(s, a));
  // A juz starting below the top of its page: the printed header decides which juz that page
  // shows, so every such page must be listed in PRINTED_JUZ_HEADERS after checking the print.
  const juzOverride = {};
  const unchecked = [];
  juzStarts.forEach(([s, a], j) => {
    const page = pageForJuz[j];
    if (j === 0 || (suraForPage[page - 1] === s && ayahForPage[page - 1] === a)) return;
    const printed = PRINTED_JUZ_HEADERS[page];
    if (printed === undefined) unchecked.push(`page ${page} (juz ${j + 1} starts at ${s}:${a})`);
    else if (printed !== j + 1) juzOverride[page] = printed;
  });
  if (unchecked.length) {
    throw new Error(`check the printed juz header of: ${unchecked.join(', ')}; add it to PRINTED_JUZ_HEADERS`);
  }
  const quarterStartByPage = Array(604).fill(-1);
  quarterStarts.forEach(([s, a], q) => {
    if (q > 0) quarterStartByPage[pageOf(s, a) - 1] = q;
  });
  return { suraForPage, ayahForPage, pageForSura, pageForJuz, juzOverride, quarterStartByPage };
}

function rows(values, perRow, label) {
  const out = [];
  for (let i = 0; i < values.length; i += perRow) {
    const chunk = values.slice(i, i + perRow);
    out.push(`      /* ${label} ${String(i + 1).padStart(3)} - ${String(i + chunk.length).padStart(3)} */ ${chunk.join(', ')}`);
  }
  return out.join(',\n');
}

function kotlin(t, kt) {
  const ayahCounts = intArray(kt, 'numberOfAyahsForSuraArray');
  const makki = booleanArray(kt, 'isMakkiBySuraArray');
  const q = quarters(kt);
  const quarterRows = [];
  for (let h = 0; h < 60; h++) {
    quarterRows.push(`    /* hizb ${String(h + 1).padEnd(2)} */ ` +
      q.slice(h * 4, h * 4 + 4).map(([s, a]) => `SuraAyah(${s}, ${a})`).join(', '));
  }
  const override = Object.entries(t.juzOverride).map(([p, j]) => `${p} to ${j}`).join(', ');
  return `package com.quran.labs.androidquran.pages.data.madaniv2

import com.quran.data.model.SuraAyah
import com.quran.data.source.QuranDataSource

/**
 * Page tables of the King Fahd Madinah mushaf V2 (QPC V2, 1421H print).
 *
 * Generated by tools/mushaf/generate_data_source.mjs from the V2 layout; do not edit by hand.
 */
class MadaniV2DataSource : QuranDataSource {
  override val numberOfPages = 604

  override val pageForSuraArray = intArrayOf(
${rows(t.pageForSura, 10, 'sura')})

  override val suraForPageArray = intArrayOf(
${rows(t.suraForPage, 20, 'page')})

  override val ayahForPageArray = intArrayOf(
${rows(t.ayahForPage, 20, 'page')})

  override val pageForJuzArray = intArrayOf(
${rows(t.pageForJuz, 10, 'juz')})

  override val juzDisplayPageArrayOverride = ${override ? `mapOf(${override})` : 'emptyMap<Int, Int>()'}

  override val numberOfAyahsForSuraArray = intArrayOf(
${rows(ayahCounts, 14, 'sura')})

  override val isMakkiBySuraArray = booleanArrayOf(
${rows(makki, 10, 'sura')})

  override val quarterStartByPage = intArrayOf(
${rows(t.quarterStartByPage, 20, 'page')})

  override val quartersArray = arrayOf(
${quarterRows.join(',\n')}
  )

  override val manzilPageArray: Array<Int> = emptyArray()
  override val haveSidelines: Boolean = false
  override val pagesToSkip: Int = 0
}
`;
}

const kt = readFileSync(SOURCE_KT, 'utf8');
const quarterStarts = quarters(kt);
if (quarterStarts.length !== 240) throw new Error(`expected 240 quarters, found ${quarterStarts.length}`);

const checkAt = process.argv.indexOf('--check');
if (checkAt > 0) {
  const t = buildTables(JSON.parse(readFileSync(process.argv[checkAt + 1], 'utf8')), quarterStarts);
  const expected = {
    suraForPage: intArray(kt, 'suraForPageArray'),
    ayahForPage: intArray(kt, 'ayahForPageArray'),
    pageForSura: intArray(kt, 'pageForSuraArray'),
    pageForJuz: intArray(kt, 'pageForJuzArray'),
    quarterStartByPage: intArray(kt, 'quarterStartByPage'),
    juzOverride: overrides(kt),
  };
  let bad = 0;
  for (const name of Object.keys(expected)) {
    const a = JSON.stringify(t[name]);
    const b = JSON.stringify(expected[name]);
    if (a === b) {
      console.log(`ok   ${name}`);
    } else {
      bad++;
      const diffs = [];
      if (Array.isArray(t[name])) {
        t[name].forEach((v, i) => { if (v !== expected[name][i]) diffs.push(`#${i + 1}: got ${v}, expected ${expected[name][i]}`); });
      } else {
        diffs.push(`got ${a}, expected ${b}`);
      }
      console.log(`FAIL ${name}: ${diffs.length} differences; ${diffs.slice(0, 8).join('; ')}`);
    }
  }
  process.exit(bad ? 1 : 0);
}

const t = buildTables(JSON.parse(readFileSync(LAYOUT, 'utf8')), quarterStarts);
mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, kotlin(t, kt));
console.log(`wrote ${OUT}`);

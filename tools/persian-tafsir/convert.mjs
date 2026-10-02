#!/usr/bin/env node
// Converts the Persian tafsir Word document (جامع البیان – ترجمۀ تفسیری قرآن کریم)
// into the bundled SQLite database used by :common:persiantafsir.
//
// Usage (from the project root, Node 22.5+ for node:sqlite):
//   node tools/persian-tafsir/convert.mjs [input.docx] [output.db]
//
// Document structure this relies on:
//   - paragraph style "af"          -> surah heading ("سوره بقره")
//   - paragraph style "a7"          -> bismillah line (not an ayah)
//   - Word list-numbered paragraphs -> one paragraph per ayah, in order
// Ayah numbers are Word auto-numbering (not text) and one list restarts mid-surah
// (Ghafir 76), so ayahs are numbered by position within their surah. The per-surah
// counts are validated against the app's own table in MadaniDataSource.kt, and
// nothing is written unless all 114 surahs match.

import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';
import { DatabaseSync } from 'node:sqlite';

const SCHEMA_VERSION = 1;

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const defaultInput = ['quran.docx', 'quran.docx.docx']
  .map(name => path.join(projectRoot, name))
  .find(file => fs.existsSync(file));
const inputPath = process.argv[2] ? path.resolve(process.argv[2]) : defaultInput;
const outputPath = process.argv[3]
  ? path.resolve(process.argv[3])
  : path.join(projectRoot, 'app/src/main/assets/persian_tafsir.db');

function fail(message) {
  console.error(`error: ${message}`);
  process.exit(1);
}

if (!inputPath || !fs.existsSync(inputPath)) {
  fail('input .docx not found (pass it as the first argument)');
}

// --- minimal zip reader (a .docx is a zip archive) ---
function readZipEntry(zipPath, entryName) {
  const buf = fs.readFileSync(zipPath);
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 65557); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) fail('not a zip/docx file');
  const entries = buf.readUInt16LE(eocd + 10);
  let offset = buf.readUInt32LE(eocd + 16);
  for (let n = 0; n < entries; n++) {
    const method = buf.readUInt16LE(offset + 10);
    const compressedSize = buf.readUInt32LE(offset + 20);
    const nameLength = buf.readUInt16LE(offset + 28);
    const extraLength = buf.readUInt16LE(offset + 30);
    const commentLength = buf.readUInt16LE(offset + 32);
    const localHeader = buf.readUInt32LE(offset + 42);
    const name = buf.toString('utf8', offset + 46, offset + 46 + nameLength);
    if (name === entryName) {
      const localNameLength = buf.readUInt16LE(localHeader + 26);
      const localExtraLength = buf.readUInt16LE(localHeader + 28);
      const start = localHeader + 30 + localNameLength + localExtraLength;
      const data = buf.subarray(start, start + compressedSize);
      if (method === 0) return data.toString('utf8');
      if (method === 8) return zlib.inflateRawSync(data).toString('utf8');
      fail(`unsupported zip compression method ${method}`);
    }
    offset += 46 + nameLength + extraLength + commentLength;
  }
  fail(`${entryName} not found in ${zipPath}`);
}

// --- expected ayah counts, read from the app so there is a single source of truth ---
function readExpectedAyahCounts() {
  const source = fs.readFileSync(path.join(projectRoot,
    'pages/data/madani/src/main/kotlin/com/quran/labs/androidquran/pages/data/madani/MadaniDataSource.kt'), 'utf8');
  const block = source.match(/numberOfAyahsForSuraArray\s*=\s*intArrayOf\(([\s\S]*?)\)/);
  if (!block) fail('could not find numberOfAyahsForSuraArray in MadaniDataSource.kt');
  const counts = block[1].replace(/\/\*[\s\S]*?\*\//g, '').split(',')
    .map(s => s.trim()).filter(Boolean).map(Number);
  if (counts.length !== 114 || counts.some(Number.isNaN)) fail('unexpected ayah count table');
  return counts;
}

// --- paragraph extraction ---
function decodeXml(text) {
  return text
    .replace(/&#x([0-9a-f]+);/gi, (_, hex) => String.fromCodePoint(parseInt(hex, 16)))
    .replace(/&#(\d+);/g, (_, dec) => String.fromCodePoint(parseInt(dec, 10)))
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'").replace(/&amp;/g, '&');
}

function paragraphText(paragraphXml) {
  let text = '';
  const runs = paragraphXml.match(/<w:r[ >][\s\S]*?<\/w:r>/g) || [];
  for (const run of runs) {
    const pieces = run.matchAll(/<w:t(?: [^>]*)?>([\s\S]*?)<\/w:t>|<w:t\/>|<w:(tab|br|cr|noBreakHyphen)\/>/g);
    for (const piece of pieces) {
      if (piece[1] !== undefined) text += decodeXml(piece[1]);
      else if (piece[2] === 'noBreakHyphen') text += '-';
      else if (piece[2]) text += ' ';
    }
  }
  return text;
}

// keeps ZWNJ (نیم‌فاصله), which is meaningful in Persian; only normalizes whitespace
function clean(text) {
  return text.replace(/[ \t \r\n]+/g, ' ').trim();
}

function cleanHeading(text) {
  return clean(text.replace(/[‌‎‏]/g, ' ')).replace(/^سوره\s*/, '').trim();
}

function parseDocument(xml) {
  const paragraphs = xml.match(/<w:p[ >][\s\S]*?<\/w:p>/g) || [];
  const surahs = [];
  let current = null;
  for (const p of paragraphs) {
    const style = (p.match(/<w:pStyle w:val="([^"]+)"/) || [])[1];
    const numId = (p.match(/<w:numId w:val="(\d+)"/) || [])[1];
    const text = clean(paragraphText(p));
    if (style === 'af') {
      current = { name: cleanHeading(paragraphText(p)), bismillah: null, ayahs: [] };
      surahs.push(current);
    } else if (!current) {
      // title page and table of contents
    } else if (style === 'a7') {
      current.bismillah = text;
    } else if (numId && numId !== '0') {
      current.ayahs.push(text);
    } else if (text) {
      fail(`unexpected unnumbered text in surah ${surahs.length}: "${text.slice(0, 60)}"`);
    }
  }
  return surahs;
}

// --- validation ---
function validate(surahs, expected) {
  const errors = [];
  if (surahs.length !== 114) errors.push(`found ${surahs.length} surah headings, expected 114`);
  surahs.forEach((surah, i) => {
    if (surah.ayahs.length !== expected[i]) {
      errors.push(`surah ${i + 1} (${surah.name}): ${surah.ayahs.length} ayahs, expected ${expected[i]}`);
    }
    surah.ayahs.forEach((text, j) => {
      if (!text) errors.push(`surah ${i + 1} ayah ${j + 1} is empty`);
    });
  });
  // fingerprints of well-known ayahs, to catch any positional drift
  const checks = [
    [2, 1, 'الف، لام، میم'],
    [36, 1, 'یا، سین'],
    [40, 76, 'دروازه'],
    [112, 1, 'یگانه'],
  ];
  if (!errors.length) {
    for (const [sura, ayah, needle] of checks) {
      const text = surahs[sura - 1].ayahs[ayah - 1].replace(/‌/g, '');
      if (!text.includes(needle)) errors.push(`fingerprint mismatch at ${sura}:${ayah}`);
    }
  }
  if (errors.length) fail(`validation failed:\n  ${errors.join('\n  ')}`);
}

// --- output ---
function writeDatabase(surahs, file) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const temp = `${file}.tmp`;
  fs.rmSync(temp, { force: true });
  const db = new DatabaseSync(temp);
  db.exec(`
    CREATE TABLE surah(
      sura INTEGER NOT NULL PRIMARY KEY,
      name TEXT NOT NULL,
      bismillah TEXT
    );
    CREATE TABLE tafsir(
      sura INTEGER NOT NULL,
      ayah INTEGER NOT NULL,
      text TEXT NOT NULL,
      PRIMARY KEY(sura, ayah)
    );
    CREATE TABLE properties(
      property TEXT NOT NULL PRIMARY KEY,
      value TEXT NOT NULL
    );
  `);
  const insertSurah = db.prepare('INSERT INTO surah(sura, name, bismillah) VALUES (?, ?, ?)');
  const insertAyah = db.prepare('INSERT INTO tafsir(sura, ayah, text) VALUES (?, ?, ?)');
  const insertProperty = db.prepare('INSERT INTO properties(property, value) VALUES (?, ?)');
  db.exec('BEGIN');
  surahs.forEach((surah, i) => {
    insertSurah.run(i + 1, surah.name, surah.bismillah);
    surah.ayahs.forEach((text, j) => insertAyah.run(i + 1, j + 1, text));
  });
  insertProperty.run('title', 'جامع البیان – ترجمۀ تفسیری قرآن کریم');
  insertProperty.run('author', 'عبدالقدوس دهقان');
  insertProperty.run('source', path.basename(inputPath));
  db.exec('COMMIT');
  // matches the SQLDelight schema version, so the driver never tries to create tables
  db.exec(`PRAGMA user_version = ${SCHEMA_VERSION}`);
  db.exec('VACUUM');
  db.close();
  fs.renameSync(temp, file);
}

const expected = readExpectedAyahCounts();
const surahs = parseDocument(readZipEntry(inputPath, 'word/document.xml'));
validate(surahs, expected);
writeDatabase(surahs, outputPath);

const total = surahs.reduce((sum, s) => sum + s.ayahs.length, 0);
console.log(`wrote ${total} ayahs in ${surahs.length} surahs to ${path.relative(projectRoot, outputPath)}` +
  ` (${Math.round(fs.statSync(outputPath).size / 1024)} KB)`);

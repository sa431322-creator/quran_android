// Builds ayahinfo_<width>.db, the reader's ayah highlighting database for the V2 page images, from
// the word boxes RenderPages.java wrote (out/glyphs-<width>.tsv). Same schema as the classic
// databases: one row per word with its line and box in image pixels.
//
//   node tools/mushaf/images/build_ayahinfo.mjs <width>
import { existsSync, readFileSync, rmSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const width = process.argv[2];
if (!width) throw new Error('usage: build_ayahinfo.mjs <width>');
const out = join(dirname(fileURLToPath(import.meta.url)), 'out');
const tsv = join(out, `glyphs-${width}.tsv`);
const dbPath = join(out, `ayahinfo_${width}.db`);
if (existsSync(dbPath)) rmSync(dbPath);

const db = new DatabaseSync(dbPath);
db.exec(`CREATE TABLE glyphs(
   glyph_id int not null,
   page_number int not null,
   line_number int not null,
   sura_number int not null,
   ayah_number int not null,
   position int not null,
   min_x int not null,
   max_x int not null,
   min_y int not null,
   max_y int not null,
   primary key(glyph_id)
)`);
const insert = db.prepare('INSERT INTO glyphs VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
const [, ...rows] = readFileSync(tsv, 'utf8').trim().split('\n');
db.exec('BEGIN');
rows.forEach((row, i) => insert.run(i + 1, ...row.split('\t').map(Number)));
db.exec('COMMIT');
db.exec('CREATE INDEX sura_ayah_idx on glyphs(sura_number, ayah_number)');
db.exec('CREATE INDEX page_idx on glyphs(page_number)');
const pages = db.prepare('SELECT count(DISTINCT page_number) n FROM glyphs').get().n;
db.close();
console.log(`wrote ${dbPath}: ${rows.length} words on ${pages} pages`);

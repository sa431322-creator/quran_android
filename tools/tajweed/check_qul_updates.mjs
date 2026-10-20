// Checks whether QUL has fixed the QPC V4 tajweed fonts the app uses, against the last check.
//
// Compares every page font on Tarteel's CDN (ETag) with qul-baseline.json, re-measures the line
// widths of any font that changed, and reports QUL's own signals: whether the resource page still
// calls V4 tajweed disabled, and recent issues about V4 tajweed. Nothing in the app is changed.
//
// Usage: node tools/tajweed/check_qul_updates.mjs [--measure-all] [--update-baseline]
//   --measure-all       download and measure all 604 fonts, not only the changed ones
//   --update-baseline   save this check as the new baseline (after the user agreed to update)
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..', '..');
const BASELINE = join(here, 'qul-baseline.json');
const PAGES_JSON = join(root, 'feature', 'tajweed', 'src', 'main', 'assets', 'tajweed', 'pages.json');
const FONT_URL = (page) => `https://static-cdn.tarteel.ai/qul/fonts/quran_fonts/v4-tajweed/ttf/p${page}.ttf`;
// the pages where QUL says V4 tajweed is disabled for proofreading (as of 2026-10)
const NOTICE_PAGES = [
  'https://qul.tarteel.ai/resources/mushaf-layout/19',
  'https://qul.tarteel.ai/docs/glyph-based'
];
const FONT_PAGE = 'https://qul.tarteel.ai/resources/font/240';
/** Issues known to be about V4 tajweed: glyph problems, availability and permission. */
const WATCHED_ISSUES = [175, 723, 737, 766];
const REPO = 'TarteelAI/quranic-universal-library';
const PAGES = 604;
/** A full line is about 16.2 em; wider than this is a line the font did not fit to the page. */
const OVERWIDE_EM = 18;

const args = new Set(process.argv.slice(2));

// every request gives up after 30 s, so one stalled connection cannot hang the check
const fetch = (url, options = {}) => globalThis.fetch(url, { ...options, signal: AbortSignal.timeout(30_000) });

async function withRetry(fn, attempts = 4) {
  for (let i = 1; ; i++) {
    try {
      return await fn();
    } catch (e) {
      if (i >= attempts) throw e;
      await new Promise((r) => setTimeout(r, 800 * i));
    }
  }
}

async function inBatches(items, size, fn) {
  const out = [];
  for (let i = 0; i < items.length; i += size) out.push(...(await Promise.all(items.slice(i, i + size).map(fn))));
  return out;
}

async function fontHeaders(page) {
  return withRetry(async () => {
    const r = await fetch(FONT_URL(page), { method: 'HEAD' });
    if (!r.ok) throw new Error(`p${page}: HTTP ${r.status}`);
    return { page, etag: r.headers.get('etag'), lastModified: r.headers.get('last-modified'), size: Number(r.headers.get('content-length')) };
  });
}

/** The widest text line of a page, in em, from the font's own glyph advances. */
function widestLineEm(fontBytes, page) {
  const b = Buffer.from(fontBytes);
  const t = {};
  for (let i = 0; i < b.readUInt16BE(4); i++) { const o = 12 + i * 16; t[b.toString('ascii', o, o + 4)] = b.readUInt32BE(o + 8); }
  const upem = b.readUInt16BE(t.head + 18);
  const metrics = b.readUInt16BE(t.hhea + 34);
  const advance = (glyph) => b.readUInt16BE(t.hmtx + 4 * Math.min(glyph, metrics - 1));
  const sub = t.cmap + b.readUInt32BE(t.cmap + 8);
  const segments = b.readUInt16BE(sub + 6) / 2;
  const cmap = {};
  for (let k = 0; k < segments; k++) {
    const end = b.readUInt16BE(sub + 14 + k * 2), start = b.readUInt16BE(sub + 16 + segments * 2 + k * 2);
    const delta = b.readInt16BE(sub + 16 + segments * 4 + k * 2), rangeAt = sub + 16 + segments * 6 + k * 2, range = b.readUInt16BE(rangeAt);
    for (let ch = start; ch <= end && ch !== 0xffff; ch++) {
      let g; if (range === 0) g = (ch + delta) & 0xffff; else { g = b.readUInt16BE(rangeAt + range + (ch - start) * 2); if (g) g = (g + delta) & 0xffff; }
      cmap[ch] = g;
    }
  }
  return Math.max(...page.l.map(([, words]) =>
    [...words.replace(/\|/g, '')].reduce((sum, ch) => sum + advance(cmap[ch.codePointAt(0)] ?? 0), 0) / upem));
}

async function measure(pageNumbers, layout) {
  const results = await inBatches(pageNumbers, 12, async (n) => {
    const bytes = await withRetry(async () => {
      const r = await fetch(FONT_URL(n));
      if (!r.ok) throw new Error(`p${n}: HTTP ${r.status}`);
      return r.arrayBuffer();
    });
    return [n, Math.round(widestLineEm(bytes, layout.pages[n - 1]) * 100) / 100];
  });
  return Object.fromEntries(results);
}

async function github(path) {
  const headers = { Accept: 'application/vnd.github+json', 'User-Agent': 'tasnim-tajweed-check' };
  if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  const r = await fetch(`https://api.github.com/${path}`, { headers });
  if (!r.ok) throw new Error(`GitHub ${path}: HTTP ${r.status}`);
  return r.json();
}

const baseline = existsSync(BASELINE) ? JSON.parse(readFileSync(BASELINE, 'utf8')) : null;
const layout = JSON.parse(readFileSync(PAGES_JSON, 'utf8'));
const report = { checkedAt: new Date().toISOString(), previousCheck: baseline?.checkedAt ?? null };

// 1. the fonts on the CDN
const headers = await inBatches(Array.from({ length: PAGES }, (_, i) => i + 1), 24, fontHeaders);
const changed = baseline ? headers.filter((h) => baseline.fonts[h.page]?.etag !== h.etag).map((h) => h.page) : [];
report.fontsChanged = changed;
report.newestFont = headers.map((h) => h.lastModified).sort((a, b) => new Date(b) - new Date(a))[0];

// 2. line widths: the changed fonts (or all), with the widths of the others carried over
const toMeasure = args.has('--measure-all') || !baseline ? headers.map((h) => h.page) : changed;
const measured = await measure(toMeasure, layout);
const widths = { ...(baseline?.widestLineEm ?? {}), ...measured };
const overwide = Object.entries(widths).filter(([, em]) => em > OVERWIDE_EM).map(([p]) => Number(p)).sort((a, b) => a - b);
report.overwidePages = overwide;
report.fixedPages = (baseline?.overwidePages ?? []).filter((p) => !overwide.includes(p));
report.newlyOverwidePages = overwide.filter((p) => !(baseline?.overwidePages ?? []).includes(p));

// 3. QUL's own signals
try {
  const notices = await Promise.all(NOTICE_PAGES.map(async (url) => {
    const html = await (await fetch(url)).text();
    return { url, saysDisabled: /disabled/i.test(html) && /proofread/i.test(html) };
  }));
  report.noticePages = notices;
  report.qulSaysDisabled = notices.some((n) => n.saysDisabled);
  // while disabled, the font page's download buttons are placeholders ("#_")
  const fontHtml = await (await fetch(FONT_PAGE)).text();
  report.fontPageDownloadsArePlaceholders = /href="#_"/.test(fontHtml);
} catch (e) {
  report.resourcePageError = String(e.message ?? e);
}
try {
  const since = (baseline?.checkedAt ?? '2024-01-01').slice(0, 10);
  const query = encodeURIComponent(`repo:${REPO} "v4" tajweed updated:>=${since}`);
  const issues = await github(`search/issues?q=${query}&sort=updated&per_page=30`);
  report.issuesUpdated = issues.items
    .filter((i) => /v4/i.test(i.title + ' ' + (i.body ?? '')))
    .map((i) => ({ number: i.number, state: i.state, updated: i.updated_at.slice(0, 10), title: i.title, url: i.html_url }));
  report.watchedIssues = await Promise.all(WATCHED_ISSUES.map(async (n) => {
    const issue = await github(`repos/${REPO}/issues/${n}`);
    const comments = await github(`repos/${REPO}/issues/${n}/comments?per_page=100`);
    const last = comments[comments.length - 1];
    return {
      number: n, state: issue.state, title: issue.title, url: issue.html_url, comments: comments.length,
      lastComment: last ? { by: last.user.login, at: last.created_at.slice(0, 10), text: (last.body ?? '').replace(/\s+/g, ' ').slice(0, 300) } : null
    };
  }));
  const repo = await github(`repos/${REPO}`);
  report.repoPushedAt = repo.pushed_at;
} catch (e) {
  report.githubError = String(e.message ?? e);
}

// summary
const lines = [];
lines.push(`QUL V4 tajweed check — ${report.checkedAt.slice(0, 10)}${report.previousCheck ? ` (previous: ${report.previousCheck.slice(0, 10)})` : ' (first check)'}`);
lines.push(`Fonts changed on the CDN: ${changed.length}${changed.length ? ` → pages ${changed.join(', ')}` : ''} (newest font: ${report.newestFont})`);
lines.push(`Pages with over-wide lines (> ${OVERWIDE_EM} em): ${overwide.length}${report.fixedPages.length ? `; fixed since last check: ${report.fixedPages.join(', ')}` : ''}${report.newlyOverwidePages.length && baseline ? `; NEW: ${report.newlyOverwidePages.join(', ')}` : ''}`);
if ('qulSaysDisabled' in report) {
  lines.push(`QUL still says V4 tajweed is disabled for proofreading: ${report.qulSaysDisabled ? 'yes' : 'NO'}; font page downloads are placeholders: ${report.fontPageDownloadsArePlaceholders ? 'yes' : 'NO'}`);
}
const previousIssues = Object.fromEntries((baseline?.watchedIssues ?? []).map((i) => [i.number, i]));
const issueChanged = (i) => {
  const was = previousIssues[i.number];
  return Boolean(was && (was.state !== i.state || was.comments !== i.comments));
};
if (report.watchedIssues) {
  lines.push('Watched issues (glyphs, availability, permission):');
  report.watchedIssues.forEach((i) => {
    lines.push(`  #${i.number} [${i.state}] ${i.comments} comments${issueChanged(i) ? ' ← CHANGED' : ''} — ${i.title}`);
    if (i.lastComment) lines.push(`      last: ${i.lastComment.by} ${i.lastComment.at}: ${i.lastComment.text.slice(0, 160)}`);
  });
}
if (report.issuesUpdated) {
  lines.push(`V4 tajweed issues updated since ${(baseline?.checkedAt ?? '2024-01-01').slice(0, 10)}: ${report.issuesUpdated.length}`);
  report.issuesUpdated.slice(0, 12).forEach((i) => lines.push(`  #${i.number} [${i.state}] ${i.updated} ${i.title}`));
}
if (report.resourcePageError) lines.push(`Resource page: ${report.resourcePageError}`);
if (report.githubError) lines.push(`GitHub: ${report.githubError}`);
const fixedSignal = (baseline && changed.length > 0) || report.fixedPages.length > 0 ||
  report.qulSaysDisabled === false || report.fontPageDownloadsArePlaceholders === false ||
  (report.watchedIssues ?? []).some(issueChanged);
lines.push(fixedSignal ? 'VERDICT: possible update — review before updating the app.' : 'VERDICT: no fix yet — keep waiting.');
console.log(lines.join('\n'));

const reportFile = join(mkdtempSync(join(tmpdir(), 'qul-check-')), 'report.json');
writeFileSync(reportFile, JSON.stringify(report, null, 2));
console.log(`full report: ${reportFile}`);

if (args.has('--update-baseline') || !baseline) {
  writeFileSync(BASELINE, JSON.stringify({
    checkedAt: report.checkedAt,
    note: 'Last QUL V4 tajweed check; written by tools/tajweed/check_qul_updates.mjs.',
    overwidePages: overwide,
    qulSaysDisabled: report.qulSaysDisabled,
    watchedIssues: (report.watchedIssues ?? []).map(({ number, state, comments }) => ({ number, state, comments })),
    widestLineEm: widths,
    fonts: Object.fromEntries(headers.map((h) => [h.page, { etag: h.etag, lastModified: h.lastModified, size: h.size }]))
  }, null, 1) + '\n');
  console.log(`baseline ${baseline ? 'updated' : 'created'}: ${BASELINE}`);
}

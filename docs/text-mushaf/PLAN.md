# Text mushaf (Madinah V2) and ayah cards: full plan

Branch: `tasnim/text-mushaf-trial` (trial done; real work goes on a new branch
`tasnim/text-mushaf` cut from it). Decision already made: **Madinah mushaf V2 only**, for both
text and images; V1 (1405H) is not supported.

Status: **approved 2026-11-06** with the recommended answers (see "Decisions" at the end). Work in progress.

---

## What I found in the code (drives the design)

1. **The reader already supports several "page types".** `PageProvider` (one per mushaf, in a
   map keyed by name), `QuranDataSource` (page tables: page of each sura, first ayah of each page,
   juz and quarter pages), `PageViewFactoryProvider` (a page type can supply its own page
   fragment), `AyahTrackerItem` (how highlighting, selection and the ayah toolbar talk to a page),
   and `PageSelectActivity` (switching page types). A text page can plug in here, so bookmarks,
   last page, ayah selection, audio highlighting, the tafsir panel, translation mode, tablet
   two-page mode and jumping keep working **without touching `PagerActivity`'s logic**.
2. **The whole app counts pages with V1 tables today** (`MadaniDataSource`). V2 needs its own
   tables. 565 of 604 pages differ, so stored page numbers must be moved once to V2:
   page bookmarks, ayah bookmarks' page column, `last_pages` and the last-page setting.
3. **`translations/tasnim_translations_ar_fa.xlsx` has no Quran translation.** Its sheets are
   UI strings, sura names and reciter names. The existing Persian tafsir DB is
   «جامع البیان – ترجمۀ تفسیری» (Dehghan), an explanatory translation, one text per ayah.
   → open question 3.
4. **In the V2 fonts, the ayah-number marker is a glyph of the font** (the word of type `end`).
   Restyling it means drawing our marker in that glyph's exact box instead of the font glyph.
   Line widths and line breaks stay identical. → open question 4.
5. The tajweed mushaf already uses the V2 page layout (with V4 fonts), so it can become a third
   way to draw the same V2 pages instead of a separate activity. → open question 6.

---

## Code organization (target)

```
pages/madaniv2/                       new page type "madani_v2"
  MadaniV2PageProvider.kt             V2 images URLs, qaris, preview strings
pages/data/madaniv2/
  MadaniV2DataSource.kt               V2 page tables (generated, never hand-edited)

feature/mushaf/                       renamed from feature/tajweed; the shared engine
  data/MushafEdition.kt               PLAIN_V2, TAJWEED_V4 (fonts URL, layout asset)
  data/MushafRepository.kt            layout + per-page fonts (was TajweedRepository)
  data/MushafPage.kt                  page/line/word model, ayah keys (was TajweedPage)
  ui/page/MushafPageView.kt           draws one page (was TajweedPageView)
  ui/page/decor/                      Tasnim decorations: ayah marker, sura frame, header/footer
  ui/page/PageMetrics.kt              one place for sizing: width, aspect, landscape, font scale
  ui/card/                            Phase 2: AyahCard, AyahCardList
  assets/mushaf/v2/pages.json         full 604-page layout
  assets/mushaf/v4/pages.json         tajweed layout (moved from tajweed/pages.json)

app/.../ui/mushaf/
  TextPageFragment.kt                 page fragment for "madani_v2" in text mode
  TextPageTrackerItem.kt              highlight/selection/toolbar positions ↔ MushafPageView
  MushafPageViewFactory.kt            text page when the setting says text, else image (null)
  MushafDisplaySetting.kt             text/image preference
app/.../data/upgrade/
  V1ToV2PageMigration.kt              one-time move of stored page numbers to V2

tools/mushaf/                         replaces tools/tajweed
  generate_layout.mjs                 layout for every page (was generate_layout.mjs)
  generate_data_source.mjs            writes MadaniV2DataSource.kt from the layout
  verify/                             page check tool + report (below)
  render_images/                      V2 page images + ayahinfo DB (if option 1a)
docs/text-mushaf/reports/             verification reports, checked in
```

Removed at the end of Phase 1: `TextMushafTrialActivity`, the debug menu entry,
`MADINAH_V2_TRIAL`, `text-mushaf-trial` assets (including the KSU reference images in debug
assets; the verify tool keeps its reference images outside the APK).

---

## Phase 1: full text mushaf

### 1.1 Data (all 604 pages)
- `generate_layout.mjs --edition v2` → `assets/mushaf/v2/pages.json` (every page, every line,
  every word's glyph code, sura starts, ayah of every word).
- `generate_data_source.mjs` → `MadaniV2DataSource.kt`: page of each sura, sura/ayah starting
  each page, juz pages, quarter (hizb) pages, from the same layout.
- New page type `madani_v2` registered in `QuranDataModule`, made the default; `madani` (V1)
  removed from the app.

### 1.2 Reader integration, default text and the setting
- `MushafPageViewFactory` returns `TextPageFragment` when the display setting is **text**
  (default for everyone) and falls back to the image page when it is **image**.
- Setting in Settings → «نمایش مصحف: متنی / تصویری», saved in `QuranSettings`.
- `TextPageTrackerItem` gives the reader what image pages give it today: ayah at a touch point,
  toolbar position for an ayah, highlight/unhighlight (selection, audio, bookmarks, tags).
  Word-level audio highlight works too, because the engine knows each word's box.
- Tablet two-page mode: `providePageView` for `TabletFragment`.
- Fonts: one download for the whole mushaf (open question 5), mirrored on the Tasnim server,
  progress in the existing download UI; per-page download as a fallback.
- `V1ToV2PageMigration` runs once on upgrade: ayah bookmarks get their V2 page from their ayah;
  page bookmarks, `last_pages` and the last-page setting map V1 page → its first ayah → V2 page.
  Unit-tested on all 604 pages.

### 1.3 Image mode in V2 (needed for the setting)
Option **1a (recommended)**: generate V2 page images from the V2 fonts (the way quran.com made
its V1 images), at the widths the app uses, plus the `ayahinfo` glyph-box DB for highlighting.
Host them on the Tasnim server. Option **1b**: official V2 images (KFGQPC or KSU, with
permission) plus building `ayahinfo` by locating ayahs in the images. → open question 1.

### 1.4 Page check tool (every page, no sampling)
`tools/mushaf/verify` runs on the emulator as an instrumented test, plus a Node step for data
checks, and writes `docs/text-mushaf/reports/verify-<date>.md` and a JSON file.
Reference = independent V2 page images (KSU electronic mushaf, the only complete V2 image set
reachable now; open question 2). These stay outside the APK and are used by the tool only.

Per page:
| check | how |
|---|---|
| 15 lines (8 on pages 1–2), line positions | detect text rows in the reference image (ink profile); compare with our rows |
| line breaks, first and last word of each line | render each of our lines with the page font at the reference scale; correlate the first and the last word's glyph images with the two ends of the reference row; correlate the whole line |
| sura header and basmala placement | rows classified in the reference (frame / basmala / text) vs our `SuraHeader` / `Basmala` lines |
| start and end ayah of the page | our layout vs the V2 page table from quran.com, and vs the ayahs the reference image's rows end on |
| every word exactly once | all 77,000+ words of the Quran appear once, in order, across the 604 pages |
| every glyph exists | each glyph code is in its page font's cmap |

Each mismatch is listed with page, line and a side-by-side crop. Fix and re-run until clean.
Fixes go into layout data or drawing code only, never into the Quran text or fonts. If the
reference itself differs from the King Fahd V2 data (a different print), the report says so,
and I bring it to you instead of "fixing" it.

### 1.5 Sajda and waqf marks
- **Sajda**: all 15 sajda places in the Madinah mushaf (7:206, 13:15, 16:50, 17:109, 19:58,
  22:18, 22:77, 25:60, 27:26, 32:15, 38:24, 41:38, 53:62, 84:21, 96:19). For each: page and line,
  the ۩ mark and the line over the sajda word, compared with the reference crop. The report has
  a 15-row table with crops and pass/fail.
- **Waqf and other marks** (مـ، لا، ج، صلى، قلى، ∴، and so on): from the Uthmani text, list every
  word carrying each mark (counts per mark type). Check that the word's glyph is drawn in its
  line and that the mark shows in the matching reference crop. The report has a table per mark
  type (count, passed, failed with crops) plus a contact sheet.

### 1.6 Screen sizes and font size
- Layout is fixed data: 15 lines, the same words per line. Only the scale changes.
  `PageMetrics` sizes text from the page width and caps the width by the page's aspect ratio.
  On wide screens and in landscape the page is centered (tablets) or scrolls (phones in
  landscape; open question 7). Text is never stretched.
- System font scale and display size: page text is sized in px from the width, so it ignores
  font scale. Decorations (header, footer, markers) are sized relative to the page, not in sp,
  so they cannot overflow. The app's own font-size setting applies to translations and cards
  only, never to the mushaf page.
- Instrumented tests with Compose `DeviceConfigurationOverride`: pages 1, 2, 50, 77, the 10
  densest pages and the 10 widest lines (found by the verify tool), at 360×640, 393×852,
  430×932, 600×960, 800×1280 and 1024×1366 dp, portrait and landscape, font scale 0.85 / 1.0 /
  1.3 / 2.0. Each test asserts that no line is wider than the page, nothing clips, and lines
  do not overlap.
- Emulator screenshots for the proof: phone and tablet sizes via `wm size` and `wm density`
  (and a tablet AVD), at the smallest and largest font scale.

### 1.7 Tasnim design for page decorations
1. A design artifact first, for your approval: ayah-number marker, sura header frame, page
   header/footer (sura, juz, page number), juz/hizb markers, page background, night mode.
   Shown on real V2 page content at phone and tablet sizes.
2. Then build it in `ui/page/decor/`. The Quran glyphs are never altered. The ayah marker is
   drawn in the font glyph's exact box (open question 4), so line breaks don't move.

### 1.8 Proof at the end of Phase 1
Clean verification report (all 604 pages), sajda and waqf tables, size/font-scale test results,
emulator screenshots (phone + tablet, smallest + largest font), unit and instrumented tests
passing, `/code-review` of the diff.

---

## Phase 2: ayah card view (plan now, build after Phase 1)

- A second reading mode: each ayah in its own card. The ayah is drawn in the mushaf font, using
  the V2 page font of the page the ayah is on. The engine already knows each word's glyph, so the
  card reuses `feature/mushaf` and wraps words to the card width; no new font is needed.
- Under the ayah: the Persian translation (source: open question 3), then the Persian tafsir,
  which can be shown or hidden with one global toggle plus a per-card toggle.
- Phones: one column. Tablets 10" and up: two columns if the design review likes it.
- Text size follows the app's font-size setting. The ayah follows it within limits, so a long
  ayah never overflows the card.
- Card actions reuse the reader's: bookmark, share, play from here, audio highlight on the card
  being recited.
- Switching: the reader's mode toggle gets "page / cards", and both keep the same position (ayah).
- Design artifact first for approval, then code: `feature/mushaf/ui/card/` and an
  `AyahCardsFragment` in app.

---

## Order of work (each step ends with something you can check)
1. Branch `tasnim/text-mushaf`; rename `feature/tajweed` → `feature/mushaf` (no behavior change).
2. Full V2 layout + `MadaniV2DataSource` + data checks (report v0).
3. Verify tool with KSU reference: first full report, all 604 pages.
4. Fix mismatches until the report is clean; sajda and waqf report.
5. Page type `madani_v2`, `TextPageFragment`, tracker, setting, migration, font download.
6. Image mode V2 (option 1a or 1b).
7. Sizes and font-scale tests + screenshots.
8. Design artifact (decorations) → your approval → implement.
9. Remove trial code; Phase 1 proof package.
10. Phase 2: card design artifact → your approval → build → proof.

## Decisions (approved 2026-11-06)
1. Image mode: **generate V2 images from the V2 fonts** (option 1a), plus the ayahinfo DB.
2. Verification reference: **KSU V2 page images**, used by the check tool only, never shipped.
3. Card translation: **still open**. The xlsx has no ayah translation; decide before Phase 2.
4. Ayah-number marker: **our own marker drawn in the font glyph's box** (layout unchanged).
5. Fonts: **one download for the whole mushaf** (offline afterwards), Tasnim mirror, per-page
   download as fallback.
6. Tajweed: **folded into the same reader** as a third way to draw V2 pages; the separate
   screen goes away.
7. Phone landscape: **scroll the page** (larger text), as tajweed does today.
8. Existing users: **bookmarks and last page moved to V2 automatically** on update.

## Progress

| step | state |
|---|---|
| 1. branch, `feature/tajweed` → `feature/mushaf` | done |
| 2. full V2 layout, `MadaniV2DataSource`, data checks | done ([data report](reports/data-2026-11-06.md)) |
| 3–4. page check against the V2 print, fixes | done: 602/602 pages, 6 line-break corrections in `tools/mushaf/layout-corrections.mjs` ([page report](reports/pages-2026-11-07.md)) |
| 1.5 sajda and waqf marks | done: 15/15 sajda; 4,378 marked words, 35 low scores all checked by eye on `reports/marks-review-2026-11-07.png` |
| 5. page type `madani_v2` in the reader | done: text pages in `PagerActivity` (phone; tested on the emulator) and `TabletFragment` (two pages; run on a 10" tablet in step 7); selection, toolbar, tafsir panel, highlights through the reader's `AyahTrackerPresenter`; no page-image download for text pages |
| 5. stored page numbers | nothing to move: ayah bookmarks and recent pages are stored by ayah, page bookmarks in the canonical `madani` numbering that `ReadingBookmarkPageMapper` maps; the old last-page setting is no longer written. A recent page saved under V1 on one of the 25 pages whose first ayah differs may show one page earlier. |
| 5. font delivery | per page on demand for now (needs network the first time); one full download still to do |
| 6. image mode in V2 + the text/image setting | done: page type `madani_v2_images` from `tools/mushaf/images` (604 pages + ayahinfo at 1260 px, tested on the emulator with files copied in: pages, ayah selection and toolbar work). The setting is Settings → page type (V1 hidden). **Waiting on you**: the zips must go on the Tasnim server (`data/madani_v2/…`, see tools/mushaf/README.md); release builds have no server address yet, so they offer the text mode only. At 1260 px the images are ~127 MB (V1: ~74 MB). |
| 7. screen sizes and font scale | done ([sizes report](reports/sizes-2026-11-07.md)): 624 layouts pass; tablet two-page view now run (and a landscape crash fixed) |
| 8. Tasnim decorations | done: design A · gilt chosen on the [design canvas](https://claude.ai/artifact/NVFd4Gfp4DMvs8HiUUesLn). Text mode: gilt eight-pointed star and ring around the ayah number in place of the font's marker glyph (the glyph keeps its width in the line, Quran glyphs untouched), gilt double-rule sura header with a pill and two stars, sura name in Kitab, page number in a star; day and night colors in `colors.xml` (`mushaf_gilt*`, `mushaf_accent`, `mushaf_label*`). Image mode: the same shapes as line art in `RenderPages.java`, all 604 pages and the zips rebuilt (re-upload needed). Screens: [reports/design-2026-11-07](reports/design-2026-11-07/). Size test (624 layouts) and unit tests pass. |
| 9. no trial code, one reader, Phase 1 proof | done ([Phase 1 proof](reports/phase1-2026-11-07.md)): trial activity, menu entry, edition and assets removed; tajweed folded into the reader as a way to draw the same text pages (tab «تجويد», home button), separate tajweed screen removed; night mode fixed for the tajweed fonts. Open: zips upload, tajweed in image mode, V4 line breaks on 6 pages. |
| 10. Phase 2 | to do |

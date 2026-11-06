# Text mushaf (King Fahd page fonts instead of page images)

Branch: `tasnim/text-mushaf-trial`. Status: trial on 4 pages, nothing in the main reader changed.

## What the trial showed (2026-11-06)

Trial screen: home → ⋮ → «آزمایش مصحف متنی (V2)» (debug builds only). Pages 2, 50, 76, 77,
each switchable between **V2 text**, **V1 text** and **the current image**.

- Rendering works: the tajweed engine (`feature/tajweed`) draws plain King Fahd pages from
  per-page fonts, sharp and full width on any screen. Sura headers, basmala, and a header
  that sits on the page before its sura (76 → 77) all work.
- **The app's images are the 1405H edition (QCF V1), not V2.** V1 text matches the images line
  for line; V2 (1421H) breaks lines differently.
- Full scan of all 604 pages, V1 vs V2:

  | | pages |
  |---|---|
  | identical | 39 |
  | same words, different line breaks | 529 |
  | different words on the page | 36 (120–123, 144–145, 531–534, 564–600) |

- The quran.com API ignores its `mushaf` parameter. The glyph field asked for decides the
  layout it returns: `code_v1` gives 1405H lines, `code_v2` gives 1421H lines. The generator now
  takes `--edition v1|v2|v4` for this. The old V4 output is unchanged.
- Font sizes (TTF, one per page) are larger than the current image set (74 MB at width 1260):

  | edition | on disk | zipped download |
  |---|---|---|
  | V1 1405H | ~92 MB | ~52 MB |
  | V2 1421H | ~212 MB | ~135 MB |

- V1 glyphs are narrower than V4/V2, so V1 lines don't fill the width yet. Text size needs a
  value per edition (`LINE_EM` in `TajweedPageView.kt`).

## Decision needed before the full conversion

| | V1 (1405H) | V2 (1421H) |
|---|---|---|
| Same pages and lines as today's images | yes, all 604 | only 39 pages |
| Bookmarks, last page and page-based data keep working as is | yes | need ayah-based mapping (tajweed mode does this already) |
| Matches quran.com / most current apps | older print | yes, current print |
| Download | ~52 MB | ~135 MB |

## Full plan: scan and convert every page

1. **Choose the edition** (V1 or V2, above).
2. **Generate the full layout**: `node tools/tajweed/generate_pages.mjs --edition <v1|v2>`
   for all 604 pages into the edition's asset.
3. **Automated scan of every page** (a new `tools/text-mushaf/scan.mjs`, report checked in):
   - data: 15 lines per page (8 on pages 1–2); every word of the Quran exactly once, in order;
     every page's first and last ayah agree with the app's page table (V1) or with quran.com (V2).
   - font: every glyph code of a page exists in that page's font (cmap); each line's width in em
     from the font's metrics, listing lines that are too wide or too narrow (the problem
     the V4 fonts have on 46 pages).
   - visual: render every page on the emulator to PNG and build contact sheets. For V1, compare
     against the page images line by line; for V2, review by eye.
4. **Fix what the scan finds**: per-edition text size; per-line adjustments only if the
   font metrics need them. The Quran text and fonts are never edited.
5. **Font delivery**: one zipped download for the whole mushaf, like the images today (works
   offline afterwards), with the Tasnim server as mirror. Per-page download stays as a fallback.
6. **Main reader**: the text mushaf becomes the default page view in `PagerActivity`, with
   ayah selection, recitation following, bookmarks and highlights from the tajweed engine.
   Night mode uses real colors. Page images stay available in settings as a fallback.
7. **Test** on small and large phones, a tablet, and landscape; compare 20+ pages against the
   images; run the unit tests.

Steps 2–4 can run first and alone: they only produce data and a report, not app changes.

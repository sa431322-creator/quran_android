# Text mushaf: Madinah mushaf V2 only

Branch: `tasnim/text-mushaf-trial`. Status: trial on 4 pages; the main reader is unchanged.

**Decision (2026-11-06): the app uses the Madinah mushaf V2 only, for both the text and the
page images. V1 (1405H) is not supported. Every comparison is against V2.**

## Where things stand

- The app's current page images (`android.quran.com/data`, `MadaniPageProvider`) are the
  **1405H (V1)** edition. They have to be replaced by V2 images.
- V1 and V2 are identical on only 39 of 604 pages: 529 pages break lines differently and 36 pages
  hold different words (120–123, 144–145, 531–534, 564–600). So bookmarks, the last page read and
  page-based data have to be moved from V1 page numbers to V2 by ayah. The tajweed mode already
  does this.
- V2 text renders correctly: the trial screen (home → ⋮ → «آزمایش مصحف متنی V2», debug only)
  shows pages 2, 50, 76 and 77 as V2 text or as a V2 reference image. Both match line for line.
- The V2 reference images in the trial come from King Saud University's electronic mushaf
  (`quran.ksu.edu.sa/png_big/<page>.png`, 622×917). They are only in debug assets, for comparison,
  and are not for shipping: they are low resolution, and the license is not checked.
- The King Fahd Complex site (qurancomplex.gov.sa) was under maintenance, so its official files
  could not be checked.
- V2 fonts: about 212 MB on disk, about 135 MB as a zipped download. The current V1 images are
  74 MB at width 1260.
- The quran.com API returns the V2 layout when asked for `code_v2`; its `mushaf` parameter is
  ignored. The generator takes `--edition v2` (or `v4` for tajweed, the default).

## Plan

1. **V2 layout for every page**:
   `node tools/tajweed/generate_pages.mjs --edition v2 --out feature/tajweed/src/main/assets/text-mushaf/pages.json`.
2. **Automated scan of all 604 pages against V2** (new `tools/text-mushaf/scan.mjs`, report
   checked in):
   - data: 15 lines per page (8 on pages 1–2); every word of the Quran exactly once, in order;
     each page's first and last ayah agree with the V2 page table from quran.com.
   - font: every glyph code of a page exists in that page's font; each line's width in em from
     the font metrics, listing lines that are too wide or too narrow.
   - visual: render every page on the emulator and compare each one with a V2 reference image
     (line breaks, first and last word of every line). Contact sheets for review by eye.
3. **V2 page images for the image mode**. Two options (to decide):
   - **a. Generate them from the V2 fonts** (recommended), like quran.com made its images from
     the V1 fonts: render each page to PNG at the widths the app uses, and compute each ayah's
     glyph boxes at the same time for the highlight database (`ayahinfo`). The images then
     match the text mushaf exactly, and there is no third-party license to check.
   - **b. Use an official V2 image set** (King Fahd Complex when its site is back, or KSU with
     permission). This also needs an `ayahinfo` database for highlighting, built by locating ayahs
     in the images.
   Either way the images go on the Tasnim server and `MadaniPageProvider` points there.
4. **Move user data from V1 pages to V2**: bookmarks, last page, page-based tags and
   recitation start pages, converted by ayah, once, on upgrade.
5. **Font delivery**: one zipped download for the whole mushaf (offline afterwards), with the
   Tasnim server as mirror and per-page download as the fallback.
6. **Main reader**: the V2 text becomes the default page view in `PagerActivity`, with ayah
   selection, recitation following, bookmarks and highlights from the tajweed engine. Night mode
   uses real colors. V2 images remain in settings as the fallback.
7. **Test** on small and large phones, a tablet, and landscape. Compare at least 20 pages against
   the V2 reference, and run the unit tests.

Steps 1–2 only produce data and a report, and can run first.

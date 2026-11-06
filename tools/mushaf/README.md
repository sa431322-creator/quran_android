# Mushaf tools

Data and checks for the text (font) mushaf in `feature/mushaf`. Plan: `docs/text-mushaf/PLAN.md`.

| file | what it does |
|---|---|
| `generate_layout.mjs` | Writes a page layout (`feature/mushaf/src/main/assets/mushaf/<edition>/pages.json`) from the quran.com API: every line's glyph codes and every word's ayah. `--edition v2` (Madinah V2) or `v4` (tajweed, the default). |
| `layout-corrections.mjs` | Corrections to the API's V2 line data, so lines break where the print does (6 words, each confirmed by the reference image and the font metrics). Applied by `generate_layout.mjs --edition v2` and by `verify/prepare.mjs`. |
| `generate_data_source.mjs` | Writes `MadaniV2DataSource.kt` (page of each sura and juz, first ayah of each page, quarters) from the V2 layout. `--check <layout>` checks its rules against the app's V1 tables. |
| `check_qul_updates.mjs` | Checks QUL for proofread V4 tajweed fonts (see the `check-qul-tajweed` skill). |
| `review_pages.mjs`, `pages-review.html` | Visual review of tajweed pages. |

## Verifying the V2 mushaf (`verify/`)

```bash
node tools/mushaf/verify/prepare.mjs
node tools/mushaf/verify/check_data.mjs --rows tools/mushaf/verify/.cache/rows.tsv
"/c/Program Files/Android/Android Studio/jbr/bin/java" tools/mushaf/verify/PageCheck.java
```

1. `prepare.mjs` downloads into `verify/.cache` (git-ignored, about 270 MB): the 604 QPC V2 page
   fonts, the 604 V2 reference page images (King Saud University electronic mushaf, used for the
   check only and never shipped), and every word of the V2 layout (`words-api.tsv`, and
   `words.tsv` with the corrections applied).
2. `check_data.mjs` checks the layout itself: 604 pages, every row filled once (text, sura header,
   basmala), every word of the Quran once and in order, the 15 sajda places. Writes
   `docs/text-mushaf/reports/data-<date>.md`, and with `--rows` the row plan PageCheck needs.
3. `PageCheck.java` compares pages 3–604 with the reference images (pages 1–2 are checked by
   reading; their print is set small inside a frame) and writes
   `docs/text-mushaf/reports/pages-<date>.md` with crops in `pages-<date>/`:
   - every word's glyph is matched in its reference line in reading order; the line must end where
     the print's line ends, and no space may be wider than the print's (a missing word);
   - sura header and basmala rows are where the print has them;
   - every line's width from the font metrics is compared with its page's (the font was made for
     the print's lines);
   - the sajda words and the waqf marks (ۖ ۗ ۘ ۙ ۚ ۛ ۜ) are tabled with their match scores.

   Pass a page list to check only some pages: `PageCheck.java 27,177` or `PageCheck.java 3-50`.
   `-DshowGaps=true` prints the spaces measured between words.

The check was tested on deliberately broken layouts (a word moved to the next line, a word
dropped, two words swapped): each is reported.

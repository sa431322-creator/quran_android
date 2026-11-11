# Tasnim on the upstream backbone: plan

Branch: `tasnim/upstream-base`, cut from `upstream/main` (quran/quran_android, `c94ebe12d`,
2026-11-10). Not `main`: our `main` is the unrelated "Baseline" commit.

Status: **draft, waiting for the user's answers** (see "Decisions" at the end).

---

## Why a new branch

The 2026-11-11 sync of `tasnim/text-mushaf` with upstream stopped: upstream had 99 new commits,
and about 30 of them replace its whole theme system. Merging that into our branch would mean
patching upstream's new design to fit our old one, again at every sync.

The new rule: **upstream is the backbone.** Our features are added on top of upstream's design,
the way upstream would add them, so each later sync is a plain merge with few conflicts.

- `tasnim/upstream-base` follows `upstream/main`. Syncing = `git merge upstream/main` into it.
- Every Tasnim feature lands on it as its own change, built on upstream's APIs.
- Once it has everything, it replaces `tasnim/text-mushaf` as our working branch, and new feature
  branches are cut from it.

---

## What changed upstream (what our work must follow)

### 1. Themes (the big one)

| Before (what Tasnim was built on) | Upstream now |
|---|---|
| A day/night toggle in the reader menu (`night_mode`) and a "night mode" setting | Removed. **App theme** = light / dark / follow system (`ThemeUtil`, `Constants.THEME_*`). **Page appearance** = light / dark / automatic (`PREF_PAGE_APPEARANCE`) |
| One page background (the "new background" gradient, `useNewBackground`) | **Page themes** in the new module `feature/pagetheme`: `PageTheme.Light` (white) and `PageTheme.Sepia` (`#F8EBD5`), picked in a bottom sheet (`PageThemeSheet`), with an "old background" option for upgraders |
| Night text and background brightness sliders in settings | Kept as settings, now shown in the page theme sheet; `QuranSettings.pageTextBrightness` combines them |
| Page images at night: colors inverted | `NightModeColorMatrix`: lightness flipped, hue kept (tajweed images keep their colors) |
| `accent_color` / `colorPrimary` set by each screen's colors | One accent, `QuranAccent` (Compose) and `?attr/quranAccent` (XML), "NavyGold" `#1F6FB2` / `#E2AF56`, taken from the new app icon. `res/color/accent_color.xml` is now a selector on it |
| `lightPrimary…darkPrimary` in `Color.kt` | Removed: the primary colors come from `QuranAccent` |

`QuranSettings` now implements `PageThemeSettings` (`pageTheme`, `pageAppearance`, `isNightMode`,
`nightModeTextBrightness`, `nightModeBackgroundBrightness`, `pageTextBrightness`), and
`PageThemePreferences.migrate()` moves the old night-mode setting to the new appearance once.

### 2. Other upstream work we get for free

Crash fixes (WorkManager in direct boot, audio service resumed on boot, download timeouts), the
Madani→Kufi map (five suras and an off-by-one), Arabic search (alef maqsura, bare hamza), audio
updates for gapped qaris, ayah indicators in translation view, localized numbers in download
notifications, bookmark storage by canonical page name, and Gradle 9.9.1 / AGP 9.5.1 /
dependency bumps.

### 3. quran.com-only additions to keep out of Tasnim's UI

- "New icon" reveal screen (`ui/whatsnew/IconReveal*`) and the icon-based accent.
- The quran.com **Account & Sync** screens (sign in, sync activity, unsynced items warning).

---

## How our features fit upstream's design

Principle: **put Tasnim in Tasnim-owned files, and touch upstream files only with small hooks.**
Today we edit upstream's `colors.xml`, `Color.kt`, `themes.xml` and `PagerActivity` heavily,
which is why every sync conflicts there.

### A. Tasnim look

1. **A Tasnim page theme**: add `PageTheme.Tasnim` ("tasnim", our paper `#F6EEDD`) next to
   Light and Sepia in `feature/pagetheme`, with its name in the sheet. Default for new installs
   and for upgraders coming from our builds. Light and Sepia stay available, as upstream has them.
   The gilt sura headers and page ornaments (`MushafSuraHeaderDrawer`, `MushafPageOrnamentDrawer`)
   draw on every page theme, in colors that follow the theme's paper.
2. **A Tasnim accent**: `QuranAccent.Tasnim` (teal `#1F4E56`, night `#8CCADB`) and a color
   `accent_tasnim`; the app's themes point `quranAccent` at it. This replaces our edits to
   `accent_color`, `color_control_*`, `tag_chip_border` and the primary colors in `Color.kt`.
3. **Tasnim surfaces** (cream backgrounds, night `#0E1518`): kept in our own files
   (`TasnimPalette.kt`, `tasnim_colors.xml`) and applied where upstream allows (the color scheme
   copy in `QuranTheme`, window backgrounds), not by rewriting upstream's `Color.kt` values.
4. **Night mode**: Tasnim follows upstream's app theme + page appearance. The reader's own
   day/night menu item is dropped, as upstream dropped it.

### B. Text mushaf (V2 fonts) and ayah cards

- The text page frame draws the **page theme's paper** (`getPageThemeBackgroundColor()`), and its
  ink follows `isNightMode` and `pageTextBrightness`, like upstream's line-by-line pages.
- The ayah cards take their background from the page theme too (day) and the night palette at
  night; their colors move to `tasnim_colors.xml`.
- `MadaniPageProvider`: keep upstream's new `mushafType()` and our `isSelectable() = false`.
- Mushaf glyphs, fonts and line layout: unchanged (locked).

### C. Page images at night

Adopt upstream's `NightModeColorMatrix` for image pages (glyphs unchanged; only the night
colors change, and the tajweed pages keep their colors), with our sura header frames on top.
This changes how night pages look, so it needs the user's OK (decision 2).

### D. The rest of Tasnim, moved over as they are

Rebrand (name, `applicationId`, launcher icon, splash, Persian UI), bottom navigation, library,
live video and radio (two channels, YouTube), Sunnah channel, Persian tafsir panel, ads, the
admin-backed features. These mostly live in our own modules (`feature/library`,
`feature/livetv`, `feature/mushaf`, `common/persiantafsir`, …) and should come over with only
small conflicts in `settings.gradle.kts`, `PagerActivity`, `BookmarkListActivity`,
`SlidingTabLayout` and tests.

Branches to bring: `tasnim/text-mushaf` (contains library, live two channels, Sunnah, phase 2
live), plus what is only on `tasnim/ads` (3 commits), `tasnim/live-youtube` (7) and
`tasnim-rebrand-v2` (7), if still wanted (decision 4).

---

## Steps

Each step ends with a build (`:app:compileMadaniDebugJavaWithJavac`), unit tests for the touched
modules, a code review, a commit, and a push of `tasnim/upstream-base`.

1. **Bring our work in.** On a temp branch from `tasnim/upstream-base`, merge
   `tasnim/text-mushaf`. In every conflict, upstream's structure wins and our feature is re-added
   inside it. Known conflicts: `settings.gradle.kts` (keep both module lists), `colors.xml` day and
   night, `Color.kt`, `MadaniPageProvider`, `PagerActivity`, `BookmarkListActivity`,
   `SlidingTabLayout`, `LegacyBookmarksMigratorTest`. Then fix compile errors where our code calls
   removed APIs (night-mode toggle, `accent_color` values, `lightPrimary…`).
   Result: the app builds with the full Tasnim feature set and upstream's theme system, before
   the Tasnim theme exists (pages look like upstream's for now).
2. **Tasnim theme as upstream extensions** (section A): `PageTheme.Tasnim`, `QuranAccent.Tasnim`,
   our surfaces in Tasnim-owned files, the migration that makes Tasnim the default.
3. **Text mushaf and cards on page themes** (section B).
4. **Image pages at night** (section C), after decision 2.
5. **quran.com-only parts** (decision 3): hide the icon reveal screen and, if chosen, the
   Account & Sync entry; their code stays so later syncs merge cleanly.
6. **Other feature branches** (decision 4): merge each into `tasnim/upstream-base`.
7. **Check the whole app**: on a phone and a tablet, light / dark / automatic × Tasnim / Light /
   Sepia; text and image mushaf, tajweed, ayah cards, translation, audio, bookmarks, library,
   live. Screenshots in `docs/upstream-base/reports/`.
8. **Switch over**: `tasnim/upstream-base` becomes the working branch; update the
   `sync-upstream` and `save-work` skills and the memory notes to it. Keep `tasnim/text-mushaf`
   and the `backup/pre-sync-*` branches until the user confirms the app works.

---

## Progress

- **Step 1 done (2026-11-11).** `tasnim/text-mushaf` merged onto upstream. Upstream's
  `colors.xml` and `Color.kt` taken as they are; Tasnim's own colors moved to
  `res/values*/tasnim_colors.xml`; the reader's day/night menu item dropped for upstream's page
  theme sheet (the ayah cards follow its changes). App builds; unit tests of app, bookmark, mushaf,
  pagetheme and linebyline pass. The app looks like upstream's until step 2.
- **Found in step 1, to settle in step 3:** `MadaniV2PageProvider` and
  `MadaniV2ImagesPageProvider` delegate `mushafType()` to the classic provider, so mobile-sync
  files V2 page bookmarks under `MADANI_1405`. Decide the right mushaf for V2 pages (or keep page
  bookmarks in the canonical numbering) before page bookmarks go to any sync.

## Decisions (open)

1. **Default page theme**: Tasnim's paper as a new page theme and the default (recommended), or
   upstream's Light / Sepia only?
2. **Night page images**: adopt upstream's hue-keeping night colors (recommended), or keep our
   current inverted look? Glyphs are unchanged either way.
3. **quran.com Account & Sync**: hide it in Tasnim (recommended; Tasnim has its own backend), or
   show it?
4. **Other branches**: bring `tasnim/ads`, `tasnim/live-youtube` and `tasnim-rebrand-v2` too?

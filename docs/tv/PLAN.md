# Tasnim on TV (Android TV / Google TV): plan

Base branch: `tasnim/upstream-base`. Each phase is its own branch and PR, cut from it.

Status: **plan, 2026-11-17.** Order agreed with the user: live video first, then radio,
then the library, then the mushaf. Phase 1 done on `tasnim/tv-phase1`; phase 2 done on
`tasnim/tv-phase2` (stacked on phase 1); phase 3 done on `tasnim/tv-phase3` (stacked on
phase 2); phase 4 done on `tasnim/tv-phase4` (stacked on phase 3). All tried on the Google TV
emulator (API 34, 1080p), and on Android 10 TV (API 29); YouTube live, the Quran and Sunnah
channels and the Quran radio station tried on TV too (2026-11-18). Still to do: one real TV,
and the 4K screenshot review of phase 4.

Phase 4 notes: this build's mushaf is drawn from the Quran fonts, so a TV needs no page-image
download. TV opens the mushaf as two pages side by side (`values-television`
`use_tablet_interface_by_default`), which fit the screen's height; the setting can turn it off.

---

## Where we start

Already there (from upstream quran_android):

- `app/src/main/AndroidManifest.xml`: touchscreen and `android.software.leanback` are
  `required="false"`, so Play does not hide the app from TVs.
- `QuranDataActivity` is in `LEANBACK_LAUNCHER`, so the app shows on the TV home screen.
- `@mipmap/tv_banner` (320×180, xhdpi) is already the Tasnim banner.

Missing:

- No D-pad / remote handling anywhere. The only key handling is volume-key page turning in
  `PagerActivity.onKeyDown`.
- No focus states: the Compose screens (`feature/livetv`, `feature/library`) use touch
  components with no visible focus, and the start screen (`TasnimLaunchActivity`,
  `res/layout/tasnim_launch.xml`) is a View layout with no focus styling.
- Layouts are phone-first: portrait columns, 44 dp buttons, ad banners, share buttons.
- First launch asks to download the mushaf page images (`QuranDataActivity.promptForDownload`)
  before anything else; a TV viewer who only wants live video must answer it first.

Not in scope: Samsung Tizen and LG webOS (not Android, would need separate apps).
Fire TV runs the same APK; publishing to the Amazon store comes later.

---

## Rules for every phase

1. **One APK.** TV is detected at runtime, not a separate build or flavor.
   `isTv(context)` = `UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION` or
   `FEATURE_LEANBACK`. One helper in `common/ui/core`, used everywhere.
2. **Phone screens stay as they are.** TV gets its own composables beside the phone ones
   (e.g. `LiveTvScreen` → `LiveTvTvScreen`), chosen in the activity. Shared state and players
   are reused, not copied.
3. **Upstream is the backbone.** Upstream files (`PagerActivity`, `QuranActivity`,
   `QuranDataActivity`) get the smallest possible hooks; TV logic lives in Tasnim-owned files
   so syncs stay plain merges.
4. **10-foot design, Tasnim identity.** Teal/gold palette (`LocalTasnimPalette`), Vazirmatn.
   Focus = gold border + slight scale (1.05). Safe area 48 dp sides / 27 dp top-bottom
   (overscan). Body text ≥ 18 sp, titles ≥ 28 sp. Everything reachable by D-pad, one focus
   item always visible.
5. **RTL.** Screens are forced RTL; Compose focus search follows layout direction, so
   D-pad right moves to the visual right. Checked on every screen.
6. **Quran fonts stay locked**: the mushaf phase changes navigation and layout only, never
   the script or page rendering.
7. **Remote keys** handled everywhere they make sense: center/enter, back, play/pause,
   media play/pause/stop, channel up/down, and the number keys where useful.
8. **No ads on TV** (decided 2026-11-17): `AdBanner` returns nothing when `isTv`. TV ad
   formats are not planned for now.
9. **No share buttons on TV** (no share sheet there).

No new dependency: the TV screens use plain Compose with a shared focus look
(`tvFocusFrame`, `tvClickable`, `TvSafeSide`/`TvSafeTop` in `common/ui/core/Tv.kt`), and the
View screens use `-television` drawables and layouts. `androidx.tv:tv-material` was planned
but not needed.

Testing for every phase: Android TV emulator (Google TV, API 34, 1080p) driven by D-pad
keys via `adb shell input keyevent`, screenshots in `docs/tv/reports/`, plus one real TV
device before release. Phone screens rechecked so nothing regresses.

---

## Phase 1: live video on TV (with the groundwork)

Goal: from the TV home screen, the viewer reaches the live broadcast in two clicks and
controls it fully with the remote.

### 1a. Groundwork

- `isTv()` helper and the TV focus style (gold border + scale) in `common/ui/core`.
- First launch on TV: skip the page-image download prompt and go straight to the start
  screen; the download is asked only when the mushaf is opened (phase 4). Small hook in
  `QuranDataActivity`.
- Start screen (`TasnimLaunchActivity`) on TV: focusable section tiles (mushaf, library,
  live video, radio) with focus drawables, initial focus on **live video**, menu/night
  buttons reachable; social links hidden on TV. Sections the server hides stay hidden.
- `AdBanner` hidden on TV.

### 1b. Live video screen for TV (`feature/livetv`)

- Opens **full screen** directly (TV is always landscape): no header, no orientation
  changes (`FullscreenEffect` skipped on TV).
- An overlay that appears on any key and hides after ~5 s of no input:
  - channel row (our channel / Quran channel) — left/right to pick, center to switch;
  - play/pause, mute, subtitles (ExoPlayer sources);
  - program now on air + the schedule (`ScheduleSection`) in a side panel.
- Remote keys without the overlay: play/pause and media keys toggle playback,
  channel up/down (and up/down) switch channel, back hides the overlay, then leaves.
- **ExoPlayer sources (`LiveVideoPlayer`)**: our own Compose controls get focus states;
  the live badge, viewer count and watermark stay.
- **YouTube sources (`YouTubeLivePlayer`)**: YouTube's terms forbid drawing over its
  player, so on TV the overlay sits *beside/below* the video (video shrinks while the
  overlay is open), and remote keys are sent to the IFrame API by JavaScript
  (`playVideo`, `pauseVideo`, `mute`, `unMute`). The WebView itself must not take focus,
  or the remote gets stuck inside it. Risk to check on a real TV: Android TV WebView
  version and YouTube embeds on TV.
- Keep screen on while playing (already done), stop on leaving (already done).

Done when: on the TV emulator the whole flow (launcher → start screen → live → switch
channel → pause → back home) works with D-pad only, for both an HLS and a YouTube source,
and phone screens are unchanged.

---

## Phase 2: live video + radio

Goal: radio works on TV, and the viewer moves between video and audio with the remote.

- Radio screen for TV (`RadioScreen` → TV layout): two columns — the now-playing card
  (logo, station, live badge, big play button with initial focus) and the station list +
  schedule. Stations as focusable cards instead of the segmented switch.
- The video/audio switch from the phone header becomes a focusable tab row shared by both
  TV screens, so live video ↔ radio is one key press.
- Media keys and play/pause through the existing `MediaSession` (`LiveRadioService`), so
  the TV's "now playing" card and remote work.
- Background audio on TV: radio keeps playing when the viewer presses home (Google TV
  shows the now-playing card); stops when they press stop or start another stream.
  Live video still stops on leaving.
- Starting the live video stops the radio (and the other way round), so two streams never
  play at once.

Done when: radio plays, switches stations, survives home → back, and video ↔ audio works
with D-pad only.

---

## Phase 3: the library (`feature/library`)

Goal: browse, open and read books with the remote.

- Library screen for TV: categories as a focusable tab row, books as rows/grid of cover
  cards (`LazyRow`/`LazyVerticalGrid` with focus restore when coming back).
- Book details for TV: cover + info on one side, actions (read, download, continue
  reading) as focusable buttons, initial focus on the main action.
- Reader (`LibraryReaderScreen`, `PdfPages`): landscape layout with one or two pages side
  by side, left/right keys turn pages (in RTL, left = next), up/down scroll inside a page,
  zoom with a focusable +/- control, back leaves. Reading position saved as today
  (`LibraryReadingStore`).
- Downloads: progress visible and focusable; storage limits on TV checked (TVs often have
  little free space).

Done when: a book can be found, downloaded, read to the end and resumed with D-pad only.

---

## Phase 4: the mushaf

Goal: read the Quran and listen to recitation on TV. Script and page rendering unchanged.

- Download on first open (moved here from first launch, see 1a). On TV, landscape shows
  two pages; check which image set that needs (`needLandscape`) and its download size,
  and offer the text mushaf where it fits.
- Page turning with left/right on the remote, RTL-correct (left = next page), as a small
  hook in `PagerActivity.onKeyDown` next to the volume-key code, logic in a Tasnim-owned
  class.
- Focusable reader bars: jump to sura/juz/page, audio controls, page theme. The ayah
  action panel is not needed in v1.
- Home lists (`QuranActivity`: suras, juz, bookmarks) checked with D-pad; focus fixes
  where upstream's views lack them.
- Audio playback: play/pause/next/previous ayah from the remote's media keys.
- Long pressing and ayah selection (touch-only today) stay out of v1 on TV.

Done when: on the emulator, open the mushaf, jump to a sura, turn pages both ways, play
and pause recitation, all with D-pad only, and a 1080p and 4K screenshot reviewed for
script quality.

---

## Release

Decided 2026-11-17: **Google Play only, the same app and the same store listing** — no
separate TV app or listing. A separate one is made only if Google Play's policy requires it
(today it does not: TV is a form factor of the same listing).

What Google Play still asks for TV, inside the same listing:

- Play Console → *Advanced settings → Form factors*: opt the app into **Android TV**.
  Without this opt-in, Play does not offer the app to TVs, even with the leanback manifest
  entries.
- TV screenshots (1920×1080) and the TV banner (already there) in the store listing.
- A separate TV review against the
  [TV app quality checklist](https://developer.android.com/docs/quality-guidelines/tv-app-quality).
  If TV is refused, phone/tablet releases are not blocked; the app just isn't offered on TV
  until the issues are fixed.

Timing: opt into TV after phase 2 (live video + radio, the main TV use); library and mushaf
follow in normal updates. Until phase 4 lands, the start screen on TV hides the mushaf and
library tiles, or shows them as they are — decided before the opt-in.

Fire TV / Amazon Appstore: later, not in this plan.

---

## Decisions

1. Ads on TV: **off** (decided 2026-11-17).
2. Store: **same Google Play listing**, TV opt-in after phase 2 (decided 2026-11-17).
3. Fire TV / Amazon: **later** (decided 2026-11-17).
4. `androidx.tv:tv-material`: **not used** — plain Compose plus the shared helpers above was enough (phase 1).

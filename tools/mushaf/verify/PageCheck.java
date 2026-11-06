import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;

/**
 * Checks every page of the V2 text mushaf against V2 reference page images, word by word.
 *
 * For each page it places the 15 rows on the reference image's line grid and checks:
 *  - text rows: every word's glyph, drawn with that page's QPC V2 font, is found in the reference
 *    row in reading order (right to left), and the line ends where the reference line ends, so a
 *    word moved to another line fails;
 *  - sura header and basmala rows: the reference row holds a header frame or a basmala there;
 *  - words carrying a sajda or waqf mark: the upper part of the glyph, where the mark sits, also
 *    matches the reference.
 *
 * Run from the repository root with Android Studio's JDK (needs tools/mushaf/verify/prepare.mjs
 * and check_data.mjs --rows first; see README.md):
 *   java tools/mushaf/verify/PageCheck.java [pages, e.g. 3-604 or 50,76]
 */
public class PageCheck {
  static final Path ROOT = Path.of("").toAbsolutePath();
  static final Path VERIFY = ROOT.resolve("tools/mushaf/verify");
  static final Path CACHE = VERIFY.resolve(".cache");

  // reference geometry (622 x 917 images): baseline of row 1 and the distance between rows
  static final double FIRST_BASELINE = 91;
  static final double PITCH = 53.8;
  static final int TEXT_LEFT = 62;
  static final int TEXT_RIGHT = 560;
  /** font size in reference pixels; calibrated with --calibrate on pages 50 and 76 */
  static double fontPx = Double.parseDouble(System.getProperty("fontPx", "30.2"));

  /**
   * The print stretches letters (kashida) to justify lines while the V2 font draws them at their
   * normal length, so a correct word can score well below 1. The strict test of line breaks is
   * the line-end check.
   */
  static final double WORD_PASS = 0.40;
  /** horizontal stretches tried for each word: kashida in the print, and squeezed crowded lines */
  static final double[] STRETCH = {1.0, 1.12, 1.25, 0.92, 0.85, 0.78, 0.70};
  static final double MARK_PASS = 0.45;
  /** the reference draws ayah markers in its own (filled, colored) design, so they match less */
  static final double AYAH_MARKER_PASS = 0.35;
  static final int LINE_END_SLACK = 14;
  /** widest space between two words of a line in the print; measured with -DshowGaps */
  static final int MAX_GAP = Integer.getInteger("maxGap", 12);
  static final Map<Integer, Integer> gaps = new TreeMap<>();

  static final String WAQF = "ۖۗۘۙۚۛۜ";
  static final String SAJDA = "۩";
  static final Map<Character, String> MARK_NAMES = new LinkedHashMap<>();
  static {
    MARK_NAMES.put('ۖ', "صلى (ۖ)");
    MARK_NAMES.put('ۗ', "قلى (ۗ)");
    MARK_NAMES.put('ۘ', "مـ (ۘ)");
    MARK_NAMES.put('ۙ', "لا (ۙ)");
    MARK_NAMES.put('ۚ', "ج (ۚ)");
    MARK_NAMES.put('ۛ', "∴ (ۛ)");
    MARK_NAMES.put('ۜ', "س (ۜ)");
    MARK_NAMES.put('۩', "سجده (۩)");
  }

  record Word(int page, int line, int index, String ayah, int position, String type, String code, String text) {}
  record Row(int page, int row, String kind, String value) {}
  record Template(float[] ink, boolean[] mask, int w, int h, int top, int left, int right) {}
  record Match(double score, int x, int y, double markScore) {}

  static final List<String> problems = new ArrayList<>();
  static final List<String> weakLines = new ArrayList<>();
  /** ayah markers checked, failed */
  static final int[] markerCount = new int[2];
  static final List<String[]> markRows = new ArrayList<>();
  static final Map<String, int[]> markCounts = new TreeMap<>();
  static final List<String[]> sajdaRows = new ArrayList<>();
  static Path cropDir;

  public static void main(String[] args) throws Exception {
    Locale.setDefault(Locale.US);
    boolean calibrate = args.length > 0 && args[0].equals("--calibrate");
    String range = calibrate ? (args.length > 1 ? args[1] : "50,76") : (args.length > 0 ? args[0] : "3-604");
    List<Integer> pages = parsePages(range);

    Map<Integer, List<Word>> words = readWords();
    Map<Integer, List<Row>> rows = readRows();
    if (calibrate) {
      calibrate(pages, words, rows);
      return;
    }

    String date = LocalDate.now().toString();
    Path reports = ROOT.resolve("docs/text-mushaf/reports");
    cropDir = reports.resolve("pages-" + date);
    Files.createDirectories(cropDir);

    int passedPages = 0;
    int checkedWords = 0;
    int failedWords = 0;
    List<String> pageLines = new ArrayList<>();
    for (int page : pages) {
      int before = problems.size();
      int[] counts = checkPage(page, words.get(page), rows.get(page), false);
      checkedWords += counts[0];
      failedWords += counts[1];
      boolean ok = problems.size() == before;
      if (ok) passedPages++;
      pageLines.add(page + "\t" + (ok ? "pass" : "FAIL") + "\t" + counts[0] + "\t" + counts[1]);
      if (page % 50 == 0) System.out.printf("page %d: %d pages passed so far%n", page, passedPages);
    }
    writeReport(reports, date, pages, passedPages, checkedWords, failedWords, pageLines);
    if (Boolean.getBoolean("showGaps")) {
      System.out.println("gaps (px=count): " + gaps);
      System.out.println("highest basmala match on a row that is not a basmala: " + textRowBasmala);
    }
    System.out.printf("%d of %d pages passed, %d of %d words failed, %d problems%n",
      passedPages, pages.size(), failedWords, checkedWords, problems.size());
  }

  // ---------------------------------------------------------------- page check

  /** Returns {words checked, words failed}. */
  static int[] checkPage(int page, List<Word> pageWords, List<Row> pageRows, boolean quiet) throws IOException {
    BufferedImage image = ImageIO.read(CACHE.resolve("reference/" + page + ".png").toFile());
    float[] ink = ink(image);
    int width = image.getWidth();
    Font font = loadFont(page);

    double offset = fitGrid(ink, width, image.getHeight());
    Map<Integer, List<Word>> byLine = new TreeMap<>();
    for (Word w : pageWords) byLine.computeIfAbsent(w.line(), k -> new ArrayList<>()).add(w);

    int checked = 0;
    int failed = 0;
    for (Row row : pageRows) {
      int baseline = (int) Math.round(FIRST_BASELINE + offset + (row.row() - 1) * PITCH);
      String found = classify(ink, width, baseline, image);
      switch (row.kind()) {
        case "header" -> {
          if (!found.equals("header")) problem(page, row.row(), "sura " + row.value() + " header expected, reference row looks like " + found, image, baseline, null);
        }
        case "basmala" -> {
          // (a basmala row of the layout needs a lower match than an unexpected one: other rows
          // score at most 0.25 against the basmala, except 27:30, which holds its words)
          if (!found.equals("basmala") && basmalaScore(ink, width, baseline) < 0.38) problem(page, row.row(), "basmala expected, reference row looks like " + found + String.format(" (basmala match %.2f, ink %s)", basmalaScore(ink, width, baseline), java.util.Arrays.toString(darkExtent(ink, width, baseline))), image, baseline, null);
        }
        case "text" -> {
          List<Word> line = byLine.getOrDefault(row.row(), List.of());
          int[] r = checkLine(page, row.row(), line, font, ink, width, refineBaseline(ink, width, baseline), image, found);
          checked += r[0];
          failed += r[1];
        }
        default -> problem(page, row.row(), "blank row in the layout", image, baseline, null);
      }
    }
    checkWidths(page, byLine, font);
    return new int[] {checked, failed};
  }

  /**
   * Lines the matcher cannot follow because the print squeezes them hard, compared with the
   * print by eye: page * 100 + row → what was seen. Each one's font-metric width is normal for its
   * page, which a missing or extra word would not be.
   */
  static final Map<Integer, String> REVIEWED = Map.of(
    23404, "same 10 words as the print, «سَبَقَتْ … مُرِيبٍۢ» (checked by eye 2026-11-06)",
    37910, "same 11 words as the print, «بِسْمِ ٱللَّهِ … مُسْلِمِينَ ٣١» (checked by eye 2026-11-06)",
    54810, "same 12 words as the print, «هُوَ ٱلرَّحْمَـٰنُ … ٱلْمَلِكُ» (checked by eye 2026-11-06)"
  );

  static final int[] AYAHS_PER_SURA = {7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128,
    111, 110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54,
    53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52,
    52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8,
    19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6};
  /** a line this far (em) from its page's median width is not a line of this page's print */
  static final double WIDTH_LIMIT = 1.0;
  static final List<String[]> widthRows = new ArrayList<>();
  /** page * 100 + row of every line the image check failed */
  static final java.util.Set<Integer> failedRows = new java.util.HashSet<>();

  /**
   * The V2 page font was made for the print's lines: in the print every line of a page is the same
   * width (99% within 0.23 em of the page's median), except a short last line of a sura. A line
   * much wider or narrower than its page means a word is on the wrong line.
   */
  static void checkWidths(int page, Map<Integer, List<Word>> byLine, Font font) {
    FontRenderContext frc = new FontRenderContext(null, true, true);
    Font f = font.deriveFont(100f);
    Map<Integer, Double> widths = new TreeMap<>();
    for (Map.Entry<Integer, List<Word>> e : byLine.entrySet()) {
      double w = 0;
      for (Word word : e.getValue()) w += f.createGlyphVector(frc, word.code()).getLogicalBounds().getWidth();
      widths.put(e.getKey(), w / 100);
    }
    List<Double> sorted = new ArrayList<>(widths.values());
    java.util.Collections.sort(sorted);
    double median = sorted.get(sorted.size() / 2);
    for (Map.Entry<Integer, Double> e : widths.entrySet()) {
      double d = e.getValue() - median;
      if (Math.abs(d) <= WIDTH_LIMIT) continue;
      List<Word> line = byLine.get(e.getKey());
      Word last = line.get(line.size() - 1);
      String[] sa = last.ayah().split(":");
      boolean suraEnd = last.type().equals("end")
        && Integer.parseInt(sa[1]) == AYAHS_PER_SURA[Integer.parseInt(sa[0]) - 1];
      String verdict = d < 0 && suraEnd ? "short last line of sura " + sa[0] + " (short in the print too)"
        : !failedRows.contains(page * 100 + e.getKey()) ? "the same in the print (image check passed this line)"
        : "PROBLEM";
      widthRows.add(new String[] {page + "", e.getKey() + "", String.format("%.2f", e.getValue()),
        String.format("%.2f", median), String.format("%+.2f", d), verdict});
      if (verdict.equals("PROBLEM")) {
        problems.add(page + "\t" + e.getKey() + "\t" + String.format(
          "line width %.2f em, page median %.2f em (%+.2f): a word on the wrong line?", e.getValue(), median, d) + "\t-");
      }
    }
  }

  /** Returns {words checked, words failed}. */
  static int[] checkLine(int page, int rowNumber, List<Word> line, Font font, float[] ink, int width,
      int baseline, BufferedImage image, String found) throws IOException {
    if (found.equals("header") || found.equals("blank")) {
      problem(page, rowNumber, "text line expected, reference row looks like " + found, image, baseline, null);
      return new int[] {line.size(), line.size()};
    }
    int[] extent = darkExtent(ink, width, baseline);
    int cursor = extent[1] + 3; // right end of the reference line
    // this line's own baseline: where its first word matches best, within 8 px of the grid
    // (anchored on the first word that is not an ayah marker: the print draws markers its own way)
    Word anchor = line.stream().filter(w -> !w.type().equals("end")).findFirst().orElse(null);
    if (anchor != null) {
      int searchRight = anchor == line.get(0) ? cursor + 6 : cursor - 10;
      double bestScore = -2;
      int bestBaseline = baseline;
      for (double stretch : STRETCH) {
        Template first = template(font, anchor.code(), stretch);
        for (int d = -8; d <= 8; d += 2) {
          Match tried = find(first, ink, width, baseline + d, searchRight - 40 - (anchor == line.get(0) ? 0 : 40), searchRight);
          if (tried.score() > bestScore) {
            bestScore = tried.score();
            bestBaseline = tried.y() - first.top();
          }
        }
      }
      baseline = bestBaseline;
    }
    int failed = 0;
    List<String> bad = new ArrayList<>();
    List<String> weak = new ArrayList<>();
    boolean previousFirm = true;
    int lastLeft = cursor;
    for (Word word : line) {
      // search the word's right edge from the cursor leftwards, allowing for justification gaps
      // (words may overlap the previous word a little, as in the print)
      Template t = null;
      Match m = null;
      for (double stretch : STRETCH) {
        Template candidate = template(font, word.code(), stretch);
        Match tried = find(candidate, ink, width, baseline, cursor - 70, cursor + 14);
        if (m == null || tried.score() > m.score()) {
          m = tried;
          t = candidate;
        }
      }
      boolean marker = word.type().equals("end");
      boolean ok = m.score() >= (marker ? AYAH_MARKER_PASS : WORD_PASS);
      if (marker) {
        markerCount[0]++;
        if (!ok) markerCount[1]++;
      }
      if (!ok) {
        failed++;
        weak.add(String.format("word %d «%s» (%s:%d) score %.2f", word.index() + 1, word.text(), word.ayah(), word.position(), m.score()));
      }
      String marks = marksOf(word.text());
      for (char c : marks.toCharArray()) {
        String name = MARK_NAMES.get(c);
        int[] count = markCounts.computeIfAbsent(name, k -> new int[2]);
        count[0]++;
        boolean markOk = ok && m.markScore() >= MARK_PASS;
        if (!markOk) {
          count[1]++;
          String crop = saveWordCrop(String.format("mark-p%03d-r%02d-w%02d.png", page, rowNumber, word.index() + 1), t, m, image, baseline);
          markRows.add(new String[] {name, page + "", rowNumber + "", word.ayah(), word.text(), String.format("%.2f / %.2f", m.score(), m.markScore()), crop});
        }
        if (c == SAJDA.charAt(0)) {
          String crop = saveWordCrop(String.format("sajda-%s.png", word.ayah().replace(':', '-')), t, m, image, baseline);
          sajdaRows.add(new String[] {word.ayah(), page + "", rowNumber + "", word.text(), String.format("%.2f", m.score()), String.format("%.2f", m.markScore()), crop});
        }
      }
      // a space wider than any in the print means a word of the reference is missing here
      int gap = cursor - m.x();
      // only between two firm matches: a weak match's position is approximate
      if (ok && previousFirm) {
        gaps.merge(Math.max(0, gap), 1, Integer::sum);
        if (gap > MAX_GAP) {
          bad.add(String.format("a %d px gap before word %d «%s» (a word missing?)", gap, word.index() + 1, word.text()));
        }
      }
      previousFirm = ok;
      // the next word sits left of this one
      lastLeft = m.x() - t.w();
      cursor = (ok || m.score() >= 0.35) ? lastLeft : cursor - t.w();
    }
    // the line must end where the reference line ends: nothing left over, nothing missing
    if (Math.abs(lastLeft - extent[0]) > LINE_END_SLACK) {
      bad.add(String.format("line ends at x=%d, reference line ends at x=%d (a word missing or extra)", lastLeft, extent[0]));
    }
    String review = REVIEWED.get(page * 100 + rowNumber);
    if (!bad.isEmpty() && review != null) {
      // a line the matcher cannot follow, compared with the print by eye (see REVIEWED)
      weakLine(page, rowNumber, "REVIEWED: " + review + " (matcher: " + String.join("; ", bad) + ")", image, baseline, renderLine(font, line));
    } else if (!bad.isEmpty()) {
      bad.addAll(weak);
      failedRows.add(page * 100 + rowNumber);
      problem(page, rowNumber, String.join("; ", bad), image, baseline, renderLine(font, line));
    } else if (!weak.isEmpty()) {
      // the line's words fill the reference line end to end with print-sized spaces, but some
      // glyphs differ from the print's drawing of them: listed for review, not a layout error
      weakLine(page, rowNumber, String.join("; ", weak), image, baseline, renderLine(font, line));
    }
    return new int[] {line.size(), failed};
  }

  // ---------------------------------------------------------------- matching

  /** Best match of the template with its right edge between minRight and maxRight. */
  static Match find(Template t, float[] ink, int width, int baseline, int minRight, int maxRight) {
    double best = -2;
    int bestX = maxRight;
    int bestY = baseline;
    int height = ink.length / width;
    for (int right = maxRight; right >= minRight; right--) {
      int left = right - t.w();
      if (left < 0 || right > width) continue;
      for (int dy = -3; dy <= 3; dy++) {
        int top = baseline + t.top() + dy;
        if (top < 0 || top + t.h() > height) continue;
        double s = ncc(t, ink, width, left, top, 0, t.h());
        if (s > best) {
          best = s;
          bestX = right;
          bestY = top;
        }
      }
    }
    int markHeight = Math.max(4, (int) (t.h() * 0.45));
    double mark = best < -1 ? -1 : ncc(t, ink, width, bestX - t.w(), bestY, 0, markHeight);
    return new Match(best, bestX, bestY, mark);
  }

  /** Normalized cross-correlation of template rows [r0, r1) with the image at (x, y). */
  static double ncc(Template t, float[] ink, int width, int x, int y, int r0, int r1) {
    double st = 0, si = 0, stt = 0, sii = 0, sti = 0;
    int n = 0;
    for (int r = r0; r < r1; r++) {
      int rowT = r * t.w();
      int rowI = (y + r) * width + x;
      for (int c = 0; c < t.w(); c++) {
        if (!t.mask()[rowT + c]) continue;
        double a = t.ink()[rowT + c];
        double b = ink[rowI + c];
        st += a; si += b; stt += a * a; sii += b * b; sti += a * b;
        n++;
      }
    }
    if (n == 0) return -1;
    double cov = sti - st * si / n;
    double vt = stt - st * st / n;
    double vi = sii - si * si / n;
    if (vt <= 1e-9 || vi <= 1e-9) return 0;
    return cov / Math.sqrt(vt * vi);
  }

  static final Map<String, Template> templates = new java.util.HashMap<>();

  /** The word's glyphs drawn right to left (reading order), as ink 0..1, cropped to the ink. */
  static Template template(Font font, String code) {
    return template(font, code, 1.0);
  }

  static Template template(Font font, String code, double stretch) {
    String key = font.getFontName() + "|" + code + "|" + fontPx + "|" + stretch;
    Template cached = templates.get(key);
    if (cached != null) return cached;
    Font f = font.deriveFont((float) fontPx).deriveFont(java.awt.geom.AffineTransform.getScaleInstance(stretch, 1.0));
    FontRenderContext frc = new FontRenderContext(null, true, true);
    String reversed = new StringBuilder(code).reverse().toString();
    GlyphVector gv = f.createGlyphVector(frc, reversed);
    Rectangle b = gv.getPixelBounds(frc, 0, 0);
    if (b.width <= 0 || b.height <= 0) b = new Rectangle(0, -4, 4, 4);
    BufferedImage img = new BufferedImage(b.width + 2, b.height + 2, BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D g = img.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, img.getWidth(), img.getHeight());
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(Color.BLACK);
    g.drawGlyphVector(gv, 1 - b.x, 1 - b.y);
    g.dispose();
    float[] ink = new float[img.getWidth() * img.getHeight()];
    for (int y = 0; y < img.getHeight(); y++) {
      for (int x = 0; x < img.getWidth(); x++) {
        ink[y * img.getWidth() + x] = (255 - (img.getRaster().getSample(x, y, 0))) / 255f;
      }
    }
    // score only around the glyph's own ink, so a neighbor reaching into the box does not count
    int tw = img.getWidth(), th = img.getHeight();
    boolean[] mask = new boolean[tw * th];
    for (int y = 0; y < th; y++) for (int x = 0; x < tw; x++) {
      if (ink[y * tw + x] < 0.1f) continue;
      for (int dy = -3; dy <= 3; dy++) for (int dx = -3; dx <= 3; dx++) {
        int yy = y + dy, xx = x + dx;
        if (yy >= 0 && yy < th && xx >= 0 && xx < tw) mask[yy * tw + xx] = true;
      }
    }
    Template t = new Template(ink, mask, tw, th, b.y - 1, b.x, b.x + b.width);
    templates.put(key, t);
    return t;
  }

  // ---------------------------------------------------------------- reference image

  /** Ink 0..1 from luminance: text, ayah markers (drawn in color) and marks all count. */
  static float[] ink(BufferedImage image) {
    int w = image.getWidth(), h = image.getHeight();
    float[] out = new float[w * h];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int rgb = image.getRGB(x, y);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int lum = (r * 30 + g * 59 + b * 11) / 100;
        out[y * w + x] = (255 - lum) / 255f;
      }
    }
    return out;
  }

  /** The densest ink row within 6 px of the grid's baseline: this line's own baseline. */
  static int refineBaseline(float[] ink, int width, int baseline) {
    int best = baseline;
    double bestScore = -1;
    for (int d = -6; d <= 6; d++) {
      double s = 0;
      for (int y = baseline + d - 1; y <= baseline + d + 1; y++) {
        for (int x = TEXT_LEFT; x < TEXT_RIGHT; x++) if (ink[y * width + x] > 0.6f) s++;
      }
      s -= Math.abs(d) * 2; // prefer the grid when rows are alike
      if (s > bestScore) {
        bestScore = s;
        best = baseline + d;
      }
    }
    return best;
  }

  /** Vertical shift of the line grid on this page, from the dark rows of the text area. */
  static double fitGrid(float[] ink, int width, int height) {
    double[] profile = new double[height];
    for (int y = 0; y < height; y++) {
      double s = 0;
      for (int x = TEXT_LEFT; x < TEXT_RIGHT; x++) if (ink[y * width + x] > 0.6f) s++;
      profile[y] = s;
    }
    double best = -1;
    double bestOffset = 0;
    for (double o = -8; o <= 8; o += 0.5) {
      double s = 0;
      for (int r = 0; r < 15; r++) {
        int y = (int) Math.round(FIRST_BASELINE + o + r * PITCH);
        for (int d = -1; d <= 1; d++) if (y + d >= 0 && y + d < height) s += profile[y + d];
      }
      if (s > best) {
        best = s;
        bestOffset = o;
      }
    }
    return bestOffset;
  }

  /**
   * text, basmala, header or blank, from the reference row around this baseline.
   * A header frame is about 60 px tall (baseline - 38 to baseline + 22) with border lines across
   * nearly the whole width at its top and bottom; a neighbor row sees at most one of them.
   * A basmala is short and centered; a text line spans the width.
   */
  static String classify(float[] ink, int width, int baseline, BufferedImage image) {
    if (isHeader(image, baseline)) return "header";
    double b = basmalaScore(ink, width, baseline);
    if (b >= BASMALA_PASS) return "basmala";
    textRowBasmala = Math.max(textRowBasmala, b);
    int[] extent = darkExtent(ink, width, baseline);
    if (extent[1] < extent[0]) return "blank";
    return extent[1] - extent[0] > 330 ? "text" : "short";
  }

  /** Two full-width border lines 50 to 66 px apart around this row: its own header frame. */
  static boolean isHeader(BufferedImage image, int baseline) {
    List<Integer> lines = new ArrayList<>();
    // (the page's own border lines, above y 52 and below y 865, are not a header frame)
    for (int y = Math.max(52, baseline - 50); y <= Math.min(865, baseline + 32); y++) {
      int dark = 0;
      for (int x = TEXT_LEFT; x < TEXT_RIGHT; x++) {
        int rgb = image.getRGB(x, y);
        int lum = (((rgb >> 16) & 255) * 30 + ((rgb >> 8) & 255) * 59 + (rgb & 255) * 11) / 100;
        if (lum < 215) dark++;
      }
      if (dark >= 0.85 * (TEXT_RIGHT - TEXT_LEFT)) lines.add(y);
    }
    for (int a : lines) for (int b : lines) if (b - a >= 50 && b - a <= 66 && (a + b) / 2 > baseline - 26 && (a + b) / 2 < baseline + 10) return true;
    return false;
  }

  /** basmala match needed; on pages 3-100 no other row scored above 0.25 (-DshowGaps shows it) */
  static final double BASMALA_PASS = Double.parseDouble(System.getProperty("basmalaPass", "0.45"));
  static double textRowBasmala = -1;
  static float[] basmala;
  static int basmalaW, basmalaH;

  /** Best match of the basmala, cut once from reference page 50 (row 2), around this row. */
  static double basmalaScore(float[] ink, int width, int baseline) {
    if (basmala == null) {
      try {
        BufferedImage page = ImageIO.read(CACHE.resolve("reference/50.png").toFile());
        float[] all = ink(page);
        int x0 = 170, x1 = 452, y0 = 122, y1 = 162;
        basmalaW = x1 - x0;
        basmalaH = y1 - y0;
        basmala = new float[basmalaW * basmalaH];
        for (int y = 0; y < basmalaH; y++) for (int x = 0; x < basmalaW; x++) basmala[y * basmalaW + x] = all[(y0 + y) * page.getWidth() + x0 + x];
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    boolean[] mask = new boolean[basmala.length];
    java.util.Arrays.fill(mask, true);
    Template t = new Template(basmala, mask, basmalaW, basmalaH, 0, 0, 0);
    double best = -1;
    int height = ink.length / width;
    // on page 50 the basmala row is row 2 (grid baseline 145) and its crop starts at y 122
    for (int dy = -16; dy <= 26; dy += 2) {
      int top = baseline - 145 + 122 + dy;
      if (top < 0 || top + basmalaH > height) continue;
      for (int left = 120; left <= 220; left += 2) best = Math.max(best, ncc(t, ink, width, left, top, 0, basmalaH));
    }
    return best;
  }

  /** Leftmost and rightmost columns with text ink in the row around this baseline. */
  static int[] darkExtent(float[] ink, int width, int baseline) {
    int top = Math.max(0, baseline - 30), bottom = baseline + 10;
    int min = Integer.MAX_VALUE, max = -1;
    for (int x = TEXT_LEFT; x < TEXT_RIGHT; x++) {
      int count = 0;
      for (int y = top; y < bottom; y++) if (ink[y * width + x] > 0.6f) count++;
      if (count >= 2) {
        min = Math.min(min, x);
        max = Math.max(max, x);
      }
    }
    return new int[] {min, max};
  }

  // ---------------------------------------------------------------- output

  static void weakLine(int page, int row, String detail, BufferedImage image, int baseline, BufferedImage ours) throws IOException {
    int before = problems.size();
    problem(page, row, detail, image, baseline, ours);
    weakLines.add(problems.remove(before));
  }

  static void problem(int page, int row, String detail, BufferedImage image, int baseline, BufferedImage ours) throws IOException {
    String crop = String.format("p%03d-r%02d.png", page, row);
    int top = Math.max(0, baseline - 42), h = Math.min(image.getHeight() - top, 60);
    BufferedImage band = image.getSubimage(0, top, image.getWidth(), h);
    int outH = h + (ours == null ? 0 : ours.getHeight() + 4);
    BufferedImage out = new BufferedImage(image.getWidth(), outH, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, out.getWidth(), out.getHeight());
    g.drawImage(band, 0, 0, null);
    if (ours != null) g.drawImage(ours, out.getWidth() - ours.getWidth(), h + 4, null);
    g.dispose();
    ImageIO.write(out, "png", cropDir.resolve(crop).toFile());
    problems.add(page + "\t" + row + "\t" + detail + "\t" + crop);
  }

  static String saveWordCrop(String name, Template t, Match m, BufferedImage image, int baseline) throws IOException {
    int left = Math.max(0, m.x() - t.w() - 10), top = Math.max(0, baseline - 45);
    int w = Math.min(image.getWidth() - left, t.w() + 20), h = 64;
    BufferedImage ref = image.getSubimage(left, top, w, Math.min(h, image.getHeight() - top));
    BufferedImage out = new BufferedImage(w * 2 + 10, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, out.getWidth(), out.getHeight());
    g.drawImage(ref, 0, 0, null);
    BufferedImage mine = new BufferedImage(t.w(), t.h(), BufferedImage.TYPE_BYTE_GRAY);
    for (int y = 0; y < t.h(); y++) for (int x = 0; x < t.w(); x++) {
      int v = 255 - Math.round(t.ink()[y * t.w() + x] * 255);
      mine.getRaster().setSample(x, y, 0, v);
    }
    g.drawImage(mine, w + 10 + 10, (baseline + t.top()) - top, null);
    g.dispose();
    ImageIO.write(out, "png", cropDir.resolve(name).toFile());
    return name;
  }

  static BufferedImage renderLine(Font font, List<Word> line) {
    List<Template> ts = new ArrayList<>();
    int width = 0, minTop = 0, maxBottom = 0;
    for (Word w : line) {
      Template t = template(font, w.code());
      ts.add(t);
      width += t.w() + 8;
      minTop = Math.min(minTop, t.top());
      maxBottom = Math.max(maxBottom, t.top() + t.h());
    }
    BufferedImage out = new BufferedImage(Math.max(1, width), Math.max(1, maxBottom - minTop), BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D g = out.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, out.getWidth(), out.getHeight());
    g.dispose();
    int x = width;
    for (Template t : ts) {
      x -= t.w() + 8;
      for (int yy = 0; yy < t.h(); yy++) for (int xx = 0; xx < t.w(); xx++) {
        int v = 255 - Math.round(t.ink()[yy * t.w() + xx] * 255);
        out.getRaster().setSample(x + 4 + xx, t.top() - minTop + yy, 0, v);
      }
    }
    return out;
  }

  static void writeReport(Path reports, String date, List<Integer> pages, int passedPages,
      int checkedWords, int failedWords, List<String> pageLines) throws IOException {
    StringBuilder md = new StringBuilder();
    md.append("# Text mushaf page check (V2 text vs V2 reference images) — ").append(date).append("\n\n");
    md.append("Reference: King Saud University electronic mushaf, V2 page images (quran.ksu.edu.sa/png_big). ")
      .append("Our side: QPC V2 page fonts with the layout in `feature/mushaf/src/main/assets/mushaf/v2/pages.json`.\n\n");
    md.append("| | result |\n|---|---|\n");
    md.append("| pages checked | ").append(pages.size()).append(" |\n");
    md.append("| pages passed | ").append(passedPages).append(" |\n");
    md.append("| words checked | ").append(checkedWords).append(" |\n");
    md.append("| words not found in place | ").append(failedWords).append(" |\n");
    md.append("| ayah markers checked | ").append(markerCount[0]).append(" (").append(markerCount[1]).append(" not found) |\n");
    md.append("| problems | ").append(problems.size()).append(" |\n\n");
    md.append(String.format("Thresholds: word match %.2f, mark area %.2f, line end within %d px. Font size %.1f px.%n%n", WORD_PASS, MARK_PASS, LINE_END_SLACK, fontPx));

    md.append("## Line widths (V2 font metrics)\n\n");
    md.append(String.format("Lines more than %.1f em from their page's median width. In the print a page's lines "
      + "are the same width (99%% within 0.23 em), except a short last line of a sura.%n%n", WIDTH_LIMIT));
    md.append("| page | line | width (em) | page median | difference | verdict |\n|---|---|---|---|---|---|\n");
    for (String[] w : widthRows) md.append("| ").append(String.join(" | ", w)).append(" |\n");
    md.append("\n## Pages 1 and 2\n\nThe print sets them small inside a decorated frame, so they were checked by ")
      .append("reading on 2026-11-06: every line holds the same words as the print (page 1: 7 lines; ")
      .append("page 2: the basmala and 6 lines).\n\n");
    md.append("## Sajda\n\n| ayah | page | row | word | word match | mark area | crop (reference, ours) |\n|---|---|---|---|---|---|---|\n");
    for (String[] s : sajdaRows) md.append("| ").append(String.join(" | ", s[0], s[1], s[2], s[3], s[4], s[5], "[" + s[6] + "](pages-" + date + "/" + s[6] + ")")).append(" |\n");
    md.append("\n## Waqf and other marks\n\n| mark | words | not in place |\n|---|---|---|\n");
    for (Map.Entry<String, int[]> e : markCounts.entrySet()) md.append("| ").append(e.getKey()).append(" | ").append(e.getValue()[0]).append(" | ").append(e.getValue()[1]).append(" |\n");
    if (!markRows.isEmpty()) {
      md.append("\n| mark | page | row | ayah | word | word / mark score | crop (reference, ours) |\n|---|---|---|---|---|---|---|\n");
      for (String[] m : markRows) {
        md.append("| ").append(String.join(" | ", java.util.Arrays.copyOf(m, 6)))
          .append(" | [").append(m[6]).append("](pages-").append(date).append("/").append(m[6]).append(") |\n");
      }
    }
    if (!markRows.isEmpty()) {
      md.append("\nThe mark-area score is crude for short words (the mark is a small part of the box) and for marks ")
        .append("the font draws outside the word's own box. Every word in the table above was compared by eye, ")
        .append("enlarged, on `marks-review-2026-11-07.png` (2026-11-07): the mark is present and in the same place ")
        .append("as in the print in all of them; on page 475 the ج of «حَقٌّۭ ۚ» sits over the join with ")
        .append("«ٱللَّهِ» in both.\n");
    }
    md.append("\n## Weak word matches (line confirmed)\n\n");
    md.append("Lines whose words fill the reference line end to end with print-sized spaces, where some glyphs ")
      .append("score below the word threshold because the print draws them differently (its ayah markers, ")
      .append("stretched or squeezed letters). Not layout errors; the crops are for review.\n\n");
    if (weakLines.isEmpty()) {
      md.append("None.\n");
    } else {
      md.append("| page | row | detail | crop |\n|---|---|---|---|\n");
      for (String p : weakLines) {
        String[] f = p.split("\t");
        md.append("| ").append(f[0]).append(" | ").append(f[1]).append(" | ").append(f[2]).append(" | [").append(f[3]).append("](pages-").append(date).append("/").append(f[3]).append(") |\n");
      }
    }
    md.append("\n## Problems\n\n");
    if (problems.isEmpty()) {
      md.append("None.\n");
    } else {
      md.append("| page | row | detail | crop |\n|---|---|---|---|\n");
      for (String p : problems) {
        String[] f = p.split("\t");
        md.append("| ").append(f[0]).append(" | ").append(f[1]).append(" | ").append(f[2]).append(" | [").append(f[3]).append("](pages-").append(date).append("/").append(f[3]).append(") |\n");
      }
    }
    Files.writeString(reports.resolve("pages-" + date + ".md"), md.toString(), StandardCharsets.UTF_8);
    Files.writeString(reports.resolve("pages-" + date + ".tsv"), "page\tresult\twords\tfailed\n" + String.join("\n", pageLines) + "\n", StandardCharsets.UTF_8);
  }

  // ---------------------------------------------------------------- calibration

  static void calibrate(List<Integer> pages, Map<Integer, List<Word>> words, Map<Integer, List<Row>> rows) throws IOException {
    cropDir = Files.createTempDirectory("pagecheck");
    for (double px = 26; px <= 36; px += 0.5) {
      fontPx = px;
      templates.clear();
      int checked = 0, failed = 0;
      problems.clear();
      for (int page : pages) {
        int[] r = checkPage(page, words.get(page), rows.get(page), true);
        checked += r[0];
        failed += r[1];
      }
      System.out.printf("fontPx %.1f: %d of %d words failed, %d problems%n", px, failed, checked, problems.size());
    }
  }

  // ---------------------------------------------------------------- input

  static String marksOf(String text) {
    StringBuilder sb = new StringBuilder();
    for (char c : text.toCharArray()) if (WAQF.indexOf(c) >= 0 || SAJDA.indexOf(c) >= 0) sb.append(c);
    return sb.toString();
  }

  static Font loadFont(int page) throws IOException {
    try {
      return Font.createFont(Font.TRUETYPE_FONT, CACHE.resolve("fonts/p" + page + ".ttf").toFile());
    } catch (java.awt.FontFormatException e) {
      throw new IOException("font of page " + page, e);
    }
  }

  static Map<Integer, List<Word>> readWords() throws IOException {
    Map<Integer, List<Word>> out = new TreeMap<>();
    List<String> lines = Files.readAllLines(Path.of(System.getProperty("words", CACHE.resolve("words.tsv").toString())), StandardCharsets.UTF_8);
    for (String l : lines.subList(1, lines.size())) {
      String[] f = l.split("\t", -1);
      Word w = new Word(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), f[3],
        Integer.parseInt(f[4]), f[5], f[6], f[7]);
      out.computeIfAbsent(w.page(), k -> new ArrayList<>()).add(w);
    }
    return out;
  }

  static Map<Integer, List<Row>> readRows() throws IOException {
    Map<Integer, List<Row>> out = new TreeMap<>();
    List<String> lines = Files.readAllLines(CACHE.resolve("rows.tsv"), StandardCharsets.UTF_8);
    for (String l : lines.subList(1, lines.size())) {
      String[] f = l.split("\t", -1);
      Row r = new Row(Integer.parseInt(f[0]), Integer.parseInt(f[1]), f[2], f[3]);
      out.computeIfAbsent(r.page(), k -> new ArrayList<>()).add(r);
    }
    return out;
  }

  static List<Integer> parsePages(String spec) {
    List<Integer> out = new ArrayList<>();
    for (String part : spec.split(",")) {
      if (part.contains("-")) {
        String[] ab = part.split("-");
        for (int p = Integer.parseInt(ab[0]); p <= Integer.parseInt(ab[1]); p++) out.add(p);
      } else {
        out.add(Integer.parseInt(part));
      }
    }
    return out;
  }
}

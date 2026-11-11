import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/**
 * Renders the 604 pages of the Madinah mushaf V2 as page images for the reader's image mode, from
 * the same QPC V2 page fonts and layout as the text mode, and the word boxes for its ayah
 * highlighting database.
 *
 * Like the app's other page images they are black ink on a transparent background (the reader
 * decodes them as alpha and tints them for day and night). The decorations are the text mode's
 * (Tasnim's gilt design, docs/text-mushaf/PLAN.md step 8) as line art in the same ink: the sura
 * header frame, and each ayah's end marker as an eight-pointed star and ring around its number in
 * place of the font's marker glyph. Sura names, juz and page numbers around the page are drawn by
 * the reader, not in the image.
 *
 * Input (tools/mushaf/verify/.cache, from prepare.mjs and check_data.mjs --rows): words.tsv,
 * rows.tsv, fonts/p<N>.ttf. Fonts of the app: app/src/madani/assets/uthmanic_hafs_ver12.otf (the
 * basmala), app/src/main/assets/kitab.ttf (the sura title) and app/src/main/assets/UthmanTN1Ver10.otf
 * (the ayah numbers).
 *
 *   java tools/mushaf/images/RenderPages.java <width> [pages]      pages and glyphs-<width>.tsv
 *   java tools/mushaf/images/RenderPages.java --zip <width>         images_<width>.zip and
 *                                                                   ayahinfo_<width>.zip
 * Output in tools/mushaf/images/out (git-ignored); the ayahinfo database itself is built from
 * glyphs-<width>.tsv by build_ayahinfo.mjs between the two steps.
 */
public class RenderPages {
  static final Path ROOT = Path.of("").toAbsolutePath();
  static final Path CACHE = ROOT.resolve("tools/mushaf/verify/.cache");
  static final Path OUT = ROOT.resolve("tools/mushaf/images/out");
  /** the classic page images' proportions (1260 x 2038) */
  static final double ASPECT = 2038.0 / 1260.0;
  static final int ROWS = 15;
  static final int OPENING_ROWS = 8;
  /** a full line of a V2 page measures about 15.6 em: the text is sized so 16 em fill the width */
  static final double LINE_EM = 16.0;
  /** lines with less text than this (sura endings, pages 1 and 2) are centered, not justified */
  static final double JUSTIFY_THRESHOLD = 0.85;
  static final String BASMALA = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ";

  record Word(int page, int line, String ayah, int position, String code) {}

  public static void main(String[] args) throws Exception {
    Locale.setDefault(Locale.US);
    if (args.length > 0 && args[0].equals("--zip")) {
      zip(args[1]);
      return;
    }
    int width = Integer.parseInt(args[0]);
    List<Integer> pages = args.length > 1 ? parsePages(args[1]) : parsePages("1-604");
    Map<Integer, List<Word>> words = readWords();
    Map<Integer, Map<Integer, String[]>> rows = readRows();
    String[] suraNames = readSuraNames();
    Font basmalaFont = Font.createFont(Font.TRUETYPE_FONT, ROOT.resolve("app/src/madani/assets/uthmanic_hafs_ver12.otf").toFile());
    Font titleFont = Font.createFont(Font.TRUETYPE_FONT, ROOT.resolve("app/src/main/assets/kitab.ttf").toFile());
    Font numberFont = Font.createFont(Font.TRUETYPE_FONT, ROOT.resolve("app/src/main/assets/UthmanTN1Ver10.otf").toFile());

    Path dir = OUT.resolve("width_" + width);
    Files.createDirectories(dir);
    Path glyphs = OUT.resolve("glyphs-" + width + ".tsv");
    try (BufferedWriter out = Files.newBufferedWriter(glyphs, StandardCharsets.UTF_8)) {
      out.write("page\tline\tsura\tayah\tposition\tmin_x\tmax_x\tmin_y\tmax_y\n");
      for (int page : pages) {
        Font pageFont = Font.createFont(Font.TRUETYPE_FONT, CACHE.resolve("fonts/p" + page + ".ttf").toFile());
        // an ayah that goes on to the next page has its end marker there
        List<Word> next = words.get(page + 1);
        String nextPageFirstAyah = next == null || next.isEmpty() ? "" : next.get(0).ayah();
        BufferedImage image = renderPage(page, width, words.get(page), rows.get(page), nextPageFirstAyah,
          pageFont, basmalaFont, titleFont, numberFont, suraNames, out);
        ImageIO.write(toFourBit(image), "png", dir.resolve(String.format("page%03d.png", page)).toFile());
        if (page % 50 == 0) System.out.println("page " + page);
      }
    }
    System.out.println("wrote " + pages.size() + " pages to " + dir + " and " + glyphs);
  }

  static BufferedImage renderPage(int page, int width, List<Word> pageWords, Map<Integer, String[]> pageRows,
      String nextPageFirstAyah, Font pageFont, Font basmalaFont, Font titleFont, Font numberFont, String[] suraNames,
      BufferedWriter glyphsOut) throws IOException {
    int height = (int) Math.round(width * ASPECT);
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    g.setColor(Color.BLACK);

    int marginX = (int) Math.round(width * 0.035);
    int contentWidth = width - 2 * marginX;
    double top = height * 0.025;
    double pitch = (height - 2 * top) / ROWS;
    int rowCount = page <= 2 ? OPENING_ROWS : ROWS;
    // the opening pages are a short block in the middle of the page
    double firstRowTop = page <= 2 ? (height - rowCount * pitch) / 2 : top;
    double fontPx = contentWidth / LINE_EM;
    Font font = pageFont.deriveFont((float) fontPx);
    FontRenderContext frc = g.getFontRenderContext();

    Map<Integer, List<Word>> byLine = new TreeMap<>();
    Set<Word> ends = new HashSet<>();
    for (int i = 0; i < pageWords.size(); i++) {
      Word w = pageWords.get(i);
      byLine.computeIfAbsent(w.line(), k -> new ArrayList<>()).add(w);
      String nextAyah = i + 1 < pageWords.size() ? pageWords.get(i + 1).ayah() : nextPageFirstAyah;
      if (!nextAyah.equals(w.ayah())) ends.add(w);
    }

    for (int row = 1; row <= rowCount; row++) {
      String[] slot = pageRows.get(row);
      double rowTop = firstRowTop + (row - 1) * pitch;
      String kind = slot == null ? "blank" : slot[0];
      switch (kind) {
        case "header" -> drawHeader(g, titleFont, "سُورَةُ " + suraNames[Integer.parseInt(slot[1]) - 1], marginX, rowTop, contentWidth, pitch);
        case "basmala" -> drawCentered(g, basmalaFont.deriveFont((float) (fontPx * 0.95)), BASMALA, width, rowTop + pitch * 0.66);
        case "text" -> drawLine(g, font, frc, byLine.getOrDefault(row, List.of()), ends, numberFont, page <= 2,
          marginX, contentWidth, rowTop, pitch, page, row, glyphsOut);
        default -> { }
      }
    }
    g.dispose();
    return image;
  }

  /** One text line, right to left: justified edge to edge, or centered when short. */
  static void drawLine(Graphics2D g, Font font, FontRenderContext frc, List<Word> line, Set<Word> ends,
      Font numberFont, boolean opening,
      int marginX, int contentWidth, double rowTop, double pitch, int page, int row, BufferedWriter glyphsOut) throws IOException {
    if (line.isEmpty()) return;
    List<GlyphVector> vectors = new ArrayList<>();
    double natural = 0;
    for (Word w : line) {
      // a word's glyphs run right to left: the first glyph code is the rightmost
      GlyphVector gv = font.createGlyphVector(frc, new StringBuilder(w.code()).reverse().toString());
      vectors.add(gv);
      natural += gv.getLogicalBounds().getWidth();
    }
    double scale = Math.min(1.0, contentWidth / natural);
    double total = natural * scale;
    int gaps = line.size() - 1;
    boolean justify = !opening && gaps > 0 && total >= contentWidth * JUSTIFY_THRESHOLD;
    double gap = justify ? (contentWidth - total) / gaps : font.getSize2D() * 0.25;
    double used = total + gap * gaps;
    double right = justify ? marginX + contentWidth : marginX + contentWidth - (contentWidth - used) / 2;
    double baseline = rowTop + pitch * 0.66;
    for (int i = 0; i < line.size(); i++) {
      GlyphVector gv = vectors.get(i);
      double advance = gv.getLogicalBounds().getWidth() * scale;
      double left = right - advance;
      AffineTransform at = new AffineTransform();
      at.translate(left, baseline);
      at.scale(scale, 1);
      Rectangle ink = gv.getPixelBounds(null, 0, 0);
      if (ends.contains(line.get(i))) {
        // the marker glyph keeps its place in the line; the star is drawn there instead of it
        // the image's lines are closer than the screen's, so the star stays within its glyph's width
        double diameter = Math.min(advance * 1.1, pitch * 0.8);
        double cy = baseline + ink.y + ink.height / 2.0;
        drawAyahMarker(g, numberFont, Integer.parseInt(line.get(i).ayah().split(":")[1]), left + advance / 2, cy, diameter);
      } else {
        Graphics2D gw = (Graphics2D) g.create();
        gw.transform(at);
        gw.drawGlyphVector(gv, 0, 0);
        gw.dispose();
      }
      int minX = (int) Math.floor(left + Math.min(ink.x, 0) * scale);
      int maxX = (int) Math.ceil(left + Math.max(ink.x + ink.width, gv.getLogicalBounds().getWidth()) * scale);
      Word w = line.get(i);
      String[] sa = w.ayah().split(":");
      glyphsOut.write(page + "\t" + row + "\t" + sa[0] + "\t" + sa[1] + "\t" + w.position() + "\t"
        + minX + "\t" + maxX + "\t" + (int) Math.round(rowTop) + "\t" + (int) Math.round(rowTop + pitch) + "\n");
      right = left - gap;
    }
  }

  /**
   * The sura header as the text mode draws it (MushafPageView's SuraHeader), as line art: a double
   * rule around a pill with the sura's name, between two filled eight-pointed stars.
   */
  static void drawHeader(Graphics2D g, Font titleFont, String title, int marginX, double rowTop, int contentWidth, double pitch) {
    double y = rowTop + pitch * 0.09;
    double h = pitch * 0.82;
    double x = marginX;
    double w = contentWidth;
    float stroke = (float) Math.max(1.5, pitch * 0.025);
    Graphics2D gg = (Graphics2D) g.create();
    gg.setStroke(new BasicStroke(stroke));
    double corner = h * 0.16;
    gg.draw(new RoundRectangle2D.Double(x, y, w, h, corner, corner));
    double inset = h * 0.08;
    gg.setStroke(new BasicStroke(stroke * 0.66f));
    gg.draw(new RoundRectangle2D.Double(x + inset, y + inset, w - 2 * inset, h - 2 * inset, corner / 2, corner / 2));

    Font f = titleFont.deriveFont((float) (pitch * 0.36));
    TextLayout layout = new TextLayout(title, f, g.getFontRenderContext());
    double cy = y + h / 2;
    double pillH = pitch * 0.56;
    double pillW = layout.getAdvance() + pitch * 0.9;
    double pillX = x + (w - pillW) / 2;
    gg.setStroke(new BasicStroke(stroke));
    gg.draw(new RoundRectangle2D.Double(pillX, cy - pillH / 2, pillW, pillH, pillH, pillH));
    double star = pitch * 0.26;
    double spacing = pitch * 0.22;
    for (double cx : new double[] {pillX - spacing - star / 2, pillX + pillW + spacing + star / 2}) {
      drawStar(gg, cx, cy, star / 2, 0, true);
    }
    gg.dispose();
    layout.draw(g, (float) (pillX + (pillW - layout.getAdvance()) / 2), (float) (cy + (layout.getAscent() - layout.getDescent()) / 2));
  }

  /** An eight-pointed star: two squares, one turned 45 degrees (MushafPageView's drawStar). */
  static void drawStar(Graphics2D g, double cx, double cy, double radius, float stroke, boolean filled) {
    double half = radius * 0.55;
    Rectangle2D square = new Rectangle2D.Double(cx - half, cy - half, half * 2, half * 2);
    for (int turn = 0; turn < 2; turn++) {
      Shape shape = AffineTransform.getRotateInstance(Math.toRadians(45 * turn), cx, cy).createTransformedShape(square);
      if (filled) {
        g.fill(shape);
      } else {
        Graphics2D gs = (Graphics2D) g.create();
        gs.setStroke(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        gs.draw(shape);
        gs.dispose();
      }
    }
  }

  /** An ayah's end marker as the text mode draws it (MushafPageView's drawAyahMarker). */
  static void drawAyahMarker(Graphics2D g, Font numberFont, int number, double cx, double cy, double diameter) {
    double radius = diameter / 2;
    drawStar(g, cx, cy, radius, (float) (diameter * 0.04), false);
    Graphics2D gs = (Graphics2D) g.create();
    gs.setStroke(new BasicStroke((float) (diameter * 0.025)));
    double ring = radius * 0.62;
    gs.draw(new Ellipse2D.Double(cx - ring, cy - ring, ring * 2, ring * 2));
    gs.dispose();
    StringBuilder digits = new StringBuilder();
    for (char c : String.valueOf(number).toCharArray()) digits.append((char) ('٠' + (c - '0')));
    Font f = numberFont.deriveFont((float) (diameter * (number > 99 ? 0.36 : 0.5)));
    TextLayout layout = new TextLayout(digits.toString(), f, g.getFontRenderContext());
    Rectangle2D bounds = layout.getBounds();
    layout.draw(g, (float) (cx - bounds.getCenterX()), (float) (cy - bounds.getCenterY()));
  }

  static void drawCentered(Graphics2D g, Font font, String text, int width, double baseline) {
    TextLayout layout = new TextLayout(text, font, g.getFontRenderContext());
    layout.draw(g, (float) ((width - layout.getAdvance()) / 2), (float) baseline);
  }

  /**
   * Black ink in 16 levels of coverage, as a 4-bit palette PNG like the classic page images: the
   * reader only reads the alpha, and the file is about a third of a full ARGB one.
   */
  static BufferedImage toFourBit(BufferedImage argb) {
    byte[] r = new byte[16], gr = new byte[16], b = new byte[16], a = new byte[16];
    for (int i = 0; i < 16; i++) a[i] = (byte) (i * 17);
    java.awt.image.IndexColorModel model = new java.awt.image.IndexColorModel(4, 16, r, gr, b, a);
    BufferedImage out = new BufferedImage(argb.getWidth(), argb.getHeight(), BufferedImage.TYPE_BYTE_BINARY, model);
    java.awt.image.WritableRaster raster = out.getRaster();
    for (int y = 0; y < argb.getHeight(); y++) {
      for (int x = 0; x < argb.getWidth(); x++) {
        int alpha = (argb.getRGB(x, y) >>> 24) & 255;
        raster.setSample(x, y, 0, (alpha + 8) / 17);
      }
    }
    return out;
  }

  // ---------------------------------------------------------------- zips

  static void zip(String width) throws IOException {
    Path images = OUT.resolve("images_" + width + ".zip");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(images))) {
      Path dir = OUT.resolve("width_" + width);
      try (var files = Files.list(dir)) {
        for (Path p : files.sorted().toList()) {
          zip.putNextEntry(new ZipEntry("width_" + width + "/" + p.getFileName()));
          Files.copy(p, zip);
          zip.closeEntry();
        }
      }
    }
    Path db = OUT.resolve("ayahinfo_" + width + ".db");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(OUT.resolve("ayahinfo_" + width + ".zip")))) {
      zip.putNextEntry(new ZipEntry("ayahinfo_" + width + ".db"));
      Files.copy(db, zip);
      zip.closeEntry();
    }
    System.out.println("wrote " + images + " and ayahinfo_" + width + ".zip");
  }

  // ---------------------------------------------------------------- input

  static Map<Integer, List<Word>> readWords() throws IOException {
    Map<Integer, List<Word>> out = new TreeMap<>();
    List<String> lines = Files.readAllLines(CACHE.resolve("words.tsv"), StandardCharsets.UTF_8);
    for (String l : lines.subList(1, lines.size())) {
      String[] f = l.split("\t", -1);
      Word w = new Word(Integer.parseInt(f[0]), Integer.parseInt(f[1]), f[3], Integer.parseInt(f[4]), f[6]);
      out.computeIfAbsent(w.page(), k -> new ArrayList<>()).add(w);
    }
    return out;
  }

  static Map<Integer, Map<Integer, String[]>> readRows() throws IOException {
    Map<Integer, Map<Integer, String[]>> out = new TreeMap<>();
    List<String> lines = Files.readAllLines(CACHE.resolve("rows.tsv"), StandardCharsets.UTF_8);
    for (String l : lines.subList(1, lines.size())) {
      String[] f = l.split("\t", -1);
      out.computeIfAbsent(Integer.parseInt(f[0]), k -> new TreeMap<>()).put(Integer.parseInt(f[1]), new String[] {f[2], f[3]});
    }
    return out;
  }

  static String[] readSuraNames() throws IOException {
    String xml = Files.readString(ROOT.resolve("common/ui/core/src/main/res/values-ar/sura_names.xml"), StandardCharsets.UTF_8);
    String array = xml.substring(xml.indexOf("name=\"sura_names\""));
    array = array.substring(0, array.indexOf("</string-array>"));
    List<String> names = new ArrayList<>();
    Matcher m = Pattern.compile("<item>(.*?)</item>").matcher(array);
    while (m.find()) names.add(m.group(1));
    if (names.size() != 114) throw new IOException("expected 114 sura names, found " + names.size());
    return names.toArray(new String[0]);
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

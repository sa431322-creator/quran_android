package com.quran.labs.androidquran.data;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import com.quran.labs.androidquran.database.DatabaseUtils;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.page.common.data.AyahBounds;
import com.quran.page.common.data.AyahCoordinates;
import com.quran.page.common.data.AyahMarkerLocation;
import com.quran.page.common.data.PageCoordinates;
import com.quran.page.common.data.SuraHeaderLocation;
import com.quran.page.common.data.coordinates.PageGlyphsCoords;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class AyahInfoDatabaseHandler {
  private static final RectF EMPTY_BOUNDS = new RectF();
  private static final String COL_PAGE = "page_number";
  private static final String COL_LINE = "line_number";
  private static final int LINES_PER_PAGE = 15;
  private static final String COL_SURA = "sura_number";
  private static final String COL_AYAH = "ayah_number";
  private static final String COL_POSITION = "position";
  private static final String MIN_X = "min_x";
  private static final String MIN_Y = "min_y";
  private static final String MAX_X = "max_x";
  private static final String MAX_Y = "max_y";
  private static final String GLYPH_TYPE = "glyph_type";
  private static final String GLYPHS_TABLE = "glyphs";

  private static final Map<String, AyahInfoDatabaseHandler> ayahInfoCache = new HashMap<>();
  private static File quranDatabaseDirectory = null;

  private final SQLiteDatabase database;
  private final boolean hasGlyphData;

  static AyahInfoDatabaseHandler getAyahInfoDatabaseHandler(
      String databaseName, QuranFileUtils quranFileUtils) {
    final File currentAyahDatabaseDirectory = quranFileUtils.getQuranAyahDatabaseDirectory();
    if (!currentAyahDatabaseDirectory.equals(quranDatabaseDirectory)) {
      quranDatabaseDirectory = currentAyahDatabaseDirectory;
      clearAyahInfoCache();
    }

    AyahInfoDatabaseHandler handler = ayahInfoCache.get(databaseName);
    if (handler == null) {
      try {
        AyahInfoDatabaseHandler db = new AyahInfoDatabaseHandler(databaseName, quranFileUtils);
        if (db.validDatabase()) {
          ayahInfoCache.put(databaseName, db);
          handler = db;
        }
      } catch (SQLException sqlException) {
        // it's okay, we'll try again later
      }
    }
    return handler;
  }

  private static void clearAyahInfoCache() {
    final Set<String> keys = ayahInfoCache.keySet();
    for (String key : keys) {
      final AyahInfoDatabaseHandler handler = ayahInfoCache.get(key);
      try {
        if (handler != null) {
          handler.database.close();
        }
      } catch (Exception e) {
        // ignore
      }
    }
    ayahInfoCache.clear();
  }

  private AyahInfoDatabaseHandler(String databaseName,
                                  QuranFileUtils quranFileUtils) throws SQLException {
    File base = quranFileUtils.getQuranAyahDatabaseDirectory();
    File path = new File(base, databaseName);
    database = SQLiteDatabase.openDatabase(path.getAbsolutePath(), null, SQLiteDatabase.NO_LOCALIZED_COLLATORS);
    hasGlyphData = quranFileUtils.getAyahInfoDbHasGlyphData();
  }

  private boolean validDatabase() {
    return database != null && database.isOpen();
  }

  @NonNull
  public PageCoordinates getPageInfo(int page, boolean wantBounds) {
    final RectF bounds = wantBounds ? getPageBounds(page) : EMPTY_BOUNDS;
    if (haveVerseMarkerData()) {
      return new PageCoordinates(page,
          bounds,
          getSuraHeadersForPage(page),
          getVerseMarkersForPage(page));
    } else {
      return new PageCoordinates(page, bounds,
          getEstimatedSuraHeadersForPage(page), new ArrayList<>());
    }
  }

  @NonNull
  private RectF getPageBounds(int page) {
    Cursor c = null;
    try {
      String[] colNames = new String[]{
          "MIN(" + MIN_X + ")", "MIN(" + MIN_Y + ")",
          "MAX(" + MAX_X + ")", "MAX(" + MAX_Y + ")" };
      c = database.query(GLYPHS_TABLE, colNames, COL_PAGE + "=" + page, null, null, null, null);
      if (c.moveToFirst()) {
        return new RectF(c.getInt(0), c.getInt(1), c.getInt(2), c.getInt(3));
      } else {
        throw new IllegalArgumentException("getPageBounds() on a non-existent page: " + page);
      }
    } finally {
      DatabaseUtils.closeCursor(c);
    }
  }

  @NonNull
  public AyahCoordinates getVersesBoundsForPage(int page) {
    boolean includeGlyphData = hasGlyphData && haveGlyphData();
    GlyphsBuilder glyphsBuilder = new GlyphsBuilder();
    Map<String, List<AyahBounds>> ayahBounds = new HashMap<>();
    Cursor cursor = null;
    try {
      cursor = getVersesBoundsCursorForPage(page, includeGlyphData);
      while (cursor.moveToNext()) {
        int line = cursor.getInt(1);
        int sura = cursor.getInt(2);
        int ayah = cursor.getInt(3);
        int position = cursor.getInt(4);
        int minX = cursor.getInt(5);
        int minY = cursor.getInt(6);
        int maxX = cursor.getInt(7);
        int maxY = cursor.getInt(8);

        if (includeGlyphData) {
          String glyphType = cursor.getString(9);
          RectF r = new RectF(minX, minY, maxX, maxY);
          glyphsBuilder.append(sura, ayah, position, line, glyphType, r);
        }

        String key = sura + ":" + ayah;
        List<AyahBounds> bounds = ayahBounds.get(key);
        if (bounds == null) {
          bounds = new ArrayList<>();
        }

        AyahBounds last = null;
        if (bounds.size() > 0) {
          last = bounds.get(bounds.size() - 1);
        }

        AyahBounds bound = new AyahBounds(line, position, minX, minY, maxX, maxY);
        if (last != null && last.getLine() == bound.getLine()) {
          last.engulf(bound);
        } else {
          bounds.add(bound);
        }
        ayahBounds.put(key, bounds);
      }
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }

    PageGlyphsCoords glyphCoords = includeGlyphData ?
        new PageGlyphsCoords(page, glyphsBuilder.build()) : null;

    return new AyahCoordinates(page, ayahBounds, glyphCoords);
  }

  private boolean haveVerseMarkerData() {
    Cursor cursor = null;
    try {
      cursor = database.rawQuery("SELECT count(1) from sqlite_master WHERE name = ?",
          new String[] { "ayah_markers" });
      return cursor.moveToFirst() && cursor.getInt(0) > 0;
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }
  }

  private boolean haveGlyphData() {
    Cursor cursor = null;
    try {
      cursor = database.query(GLYPHS_TABLE,
          new String[]{GLYPH_TYPE}, null, null, null, null, null, "1");
      return cursor.moveToFirst() && cursor.getString(0) != null;
    } catch (Exception e) {
      // we don't have glyph data (the column doesn't exist)
      return false;
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }
  }

  private List<AyahMarkerLocation> getVerseMarkersForPage(int page) {
    final List<AyahMarkerLocation> markers = new ArrayList<>();
    Cursor cursor = null;
    try {
      cursor = database.query("ayah_markers",
          new String[] { "sura_number", "ayah_number", "x", "y" },
          "page_number = ?", new String[] { String.valueOf(page) }, null,
          null, "sura_number, ayah_number ASC");
      while (cursor.moveToNext()) {
        final int sura = cursor.getInt(0);
        final int ayah = cursor.getInt(1);
        final int x = cursor.getInt(2);
        final int y = cursor.getInt(3);
        markers.add(new AyahMarkerLocation(sura, ayah, x, y));
      }
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }
    return markers;
  }

  /**
   * For databases without a sura_headers table, estimate where each sura header on this page is.
   * A header takes the line two above the first line of its sura (one above for al-Fatiha and
   * at-Tawbah, which have no separate basmala line), wrapping onto the last line of the previous
   * page. Only y (the estimated centre of the header line) is set; callers should confirm the
   * header in the page image before using it.
   */
  private List<SuraHeaderLocation> getEstimatedSuraHeadersForPage(int page) {
    final List<SuraHeaderLocation> headers = new ArrayList<>();
    Cursor cursor = null;
    try {
      final Map<Integer, Integer> headerLines = new HashMap<>();
      cursor = database.query(GLYPHS_TABLE, new String[] { COL_SURA, COL_PAGE, COL_LINE },
          COL_AYAH + " = 1 AND " + COL_POSITION + " = 1 AND " + COL_PAGE + " IN (?, ?)",
          new String[] { String.valueOf(page), String.valueOf(page + 1) },
          null, null, COL_SURA);
      while (cursor.moveToNext()) {
        final int sura = cursor.getInt(0);
        int headerPage = cursor.getInt(1);
        int headerLine = cursor.getInt(2) - ((sura == 1 || sura == 9) ? 1 : 2);
        if (headerLine < 1) {
          headerPage--;
          headerLine += LINES_PER_PAGE;
        }
        if (headerPage == page) {
          headerLines.put(sura, headerLine);
        }
      }
      DatabaseUtils.closeCursor(cursor);
      cursor = null;
      if (headerLines.isEmpty()) {
        return headers;
      }

      final Map<Integer, List<int[]>> lineBounds = new TreeMap<>();
      cursor = database.query(GLYPHS_TABLE, new String[] { COL_LINE, MIN_Y, MAX_Y },
          COL_PAGE + " = ?", new String[] { String.valueOf(page) }, null, null, null);
      while (cursor.moveToNext()) {
        List<int[]> bounds = lineBounds.get(cursor.getInt(0));
        if (bounds == null) {
          bounds = new ArrayList<>();
          lineBounds.put(cursor.getInt(0), bounds);
        }
        bounds.add(new int[] { cursor.getInt(1), cursor.getInt(2) });
      }
      if (lineBounds.size() < 2) {
        return headers;
      }

      // the centre of each line, from the median glyph top and bottom (tall glyphs skew the mean)
      final List<Integer> lines = new ArrayList<>(lineBounds.keySet());
      final float[] centers = new float[lines.size()];
      for (int i = 0; i < lines.size(); i++) {
        final List<int[]> bounds = lineBounds.get(lines.get(i));
        final int[] tops = new int[bounds.size()];
        final int[] bottoms = new int[bounds.size()];
        for (int j = 0; j < bounds.size(); j++) {
          tops[j] = bounds.get(j)[0];
          bottoms[j] = bounds.get(j)[1];
        }
        Arrays.sort(tops);
        Arrays.sort(bottoms);
        centers[i] = (tops[tops.length / 2] + bottoms[bottoms.length / 2]) / 2.0f;
      }

      for (Map.Entry<Integer, Integer> entry : headerLines.entrySet()) {
        final int line = entry.getValue();
        int after = 0;
        while (after < lines.size() && lines.get(after) < line) {
          after++;
        }
        // interpolate between the lines around the header, or extrapolate from the nearest two
        final int a = Math.max(0, Math.min(after - 1, lines.size() - 2));
        final int b = a + 1;
        final float pitch = (centers[b] - centers[a]) / (lines.get(b) - lines.get(a));
        final float y = centers[a] + pitch * (line - lines.get(a));
        headers.add(new SuraHeaderLocation(entry.getKey(), 0, Math.round(y), 0, 0));
      }
    } catch (Exception e) {
      // no usable glyph data, so no headers
      headers.clear();
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }
    return headers;
  }

  private List<SuraHeaderLocation> getSuraHeadersForPage(int page) {
    final List<SuraHeaderLocation> headers = new ArrayList<>();
    Cursor cursor = null;
    try {
      cursor = database.query("sura_headers",
          new String[] { "sura_number", "x", "y", "width", "height" },
          "page_number = ?", new String[] { String.valueOf(page) }, null,
          null, "sura_number ASC");
      while (cursor.moveToNext()) {
        final int sura = cursor.getInt(0);
        final int x = cursor.getInt(1);
        final int y = cursor.getInt(2);
        final int width = cursor.getInt(3);
        final int height = cursor.getInt(4);
        headers.add(new SuraHeaderLocation(sura, x, y, width, height));
      }
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }
    return headers;
  }

  private Cursor getVersesBoundsCursorForPage(int page, boolean withGlyphData) {
    String[] columns = withGlyphData ?
        new String[]{COL_PAGE, COL_LINE, COL_SURA, COL_AYAH, COL_POSITION, MIN_X, MIN_Y, MAX_X, MAX_Y, GLYPH_TYPE} :
        new String[]{COL_PAGE, COL_LINE, COL_SURA, COL_AYAH, COL_POSITION, MIN_X, MIN_Y, MAX_X, MAX_Y};

    return database.query(GLYPHS_TABLE,
        columns,
        COL_PAGE + "=" + page,
        null, null, null,
        COL_SURA + "," + COL_AYAH + "," + COL_POSITION);
  }
}

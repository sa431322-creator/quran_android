package com.quran.labs.androidquran.base;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.io.File;

/**
 * FileProvider matches a file to its configured root by checking for the root path followed by
 * a '/', so under Robolectric on Windows (whose paths use '\') no file ever matches. This builds
 * the same uri as FileProvider does for the app's single root, external-files-path "backups"
 * (res/xml/file_paths.xml), comparing paths with either separator.
 */
@Implements(FileProvider.class)
public class SeparatorAgnosticFileProvider {

  @Implementation
  public static Uri getUriForFile(Context context, String authority, File file) {
    String root = normalized(new File(context.getExternalFilesDir(null), "backups"));
    String path = normalized(file);
    if (!path.startsWith(root + "/")) {
      throw new IllegalArgumentException("Failed to find configured root that contains " + file);
    }
    String relative = path.substring(root.length() + 1);
    return new Uri.Builder()
        .scheme("content")
        .authority(authority)
        .encodedPath(Uri.encode("backups") + '/' + Uri.encode(relative, "/"))
        .build();
  }

  private static String normalized(File file) {
    try {
      return file.getCanonicalPath().replace('\\', '/');
    } catch (java.io.IOException e) {
      return file.getAbsolutePath().replace('\\', '/');
    }
  }
}

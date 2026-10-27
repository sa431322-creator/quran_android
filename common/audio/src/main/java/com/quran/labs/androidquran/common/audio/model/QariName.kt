package com.quran.labs.androidquran.common.audio.model

import android.content.Context
import com.quran.data.model.audio.Qari

/** The name to show: the server's name when there is one, otherwise the built-in string. */
fun Qari.displayName(context: Context): String =
  name ?: runCatching { context.getString(nameResource) }.getOrDefault("")

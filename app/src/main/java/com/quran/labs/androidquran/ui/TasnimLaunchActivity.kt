package com.quran.labs.androidquran.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.PreferenceManager
import com.quran.labs.androidquran.AboutUsActivity
import com.quran.labs.androidquran.HelpActivity
import com.quran.labs.androidquran.QuranPreferenceActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.util.ThemeUtil

/**
 * The Tasnim start screen: the logo, the main sections (mushaf, live video, radio) and the
 * social links. The mushaf opens [QuranActivity]; live video and radio are phase 2 stubs.
 */
class TasnimLaunchActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    // light icons on the dark teal background, in day and night mode
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )
    super.onCreate(savedInstanceState)
    setContentView(R.layout.tasnim_launch)

    val content = findViewById<ViewGroup>(R.id.launch_content)
    ViewCompat.setOnApplyWindowInsetsListener(content) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      view.updatePadding(
        left = insets.left, top = insets.top, right = insets.right, bottom = insets.bottom
      )
      windowInsets
    }

    findViewById<View>(R.id.launch_mushaf).setOnClickListener { openMushaf() }
    findViewById<View>(R.id.launch_live).setOnClickListener { comingSoon() }
    findViewById<View>(R.id.launch_radio).setOnClickListener { comingSoon() }
    findViewById<View>(R.id.launch_menu).setOnClickListener { showMenu(it) }
    findViewById<View>(R.id.launch_night).setOnClickListener { toggleNightMode() }

    findViewById<View>(R.id.launch_website).setOnClickListener { openLink(R.string.tasnim_url_website) }
    findViewById<View>(R.id.launch_email).setOnClickListener { openLink(R.string.tasnim_url_email) }
    findViewById<View>(R.id.launch_video).setOnClickListener { openLink(R.string.tasnim_url_video) }
    findViewById<View>(R.id.launch_messenger).setOnClickListener { openLink(R.string.tasnim_url_messenger) }
  }

  private fun openMushaf() {
    val target = Intent(this, QuranActivity::class.java)
    intent?.extras?.let { target.putExtras(it) }
    startActivity(target)
  }

  private fun showMenu(anchor: View) {
    val popup = PopupMenu(this, anchor)
    popup.menu.add(0, R.id.settings, 0, R.string.menu_settings)
    popup.menu.add(0, R.id.help, 1, R.string.menu_help)
    popup.menu.add(0, R.id.about, 2, R.string.menu_about)
    popup.setOnMenuItemClickListener { item ->
      val target = when (item.itemId) {
        R.id.settings -> QuranPreferenceActivity::class.java
        R.id.help -> HelpActivity::class.java
        else -> AboutUsActivity::class.java
      }
      startActivity(Intent(this, target))
      true
    }
    popup.show()
  }

  private fun toggleNightMode() {
    val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val theme = if (isNight) Constants.THEME_LIGHT else Constants.THEME_DARK
    PreferenceManager.getDefaultSharedPreferences(this).edit {
      putString(Constants.PREF_APP_THEME, theme)
    }
    ThemeUtil.setTheme(theme)
  }

  private fun openLink(@StringRes urlRes: Int) {
    val url = getString(urlRes)
    if (url.isBlank()) {
      comingSoon()
      return
    }

    val uri = if (urlRes == R.string.tasnim_url_email && !url.startsWith("mailto:")) {
      "mailto:$url".toUri()
    } else {
      url.toUri()
    }
    try {
      startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
      comingSoon()
    }
  }

  private fun comingSoon() {
    Toast.makeText(this, R.string.tasnim_coming_soon, Toast.LENGTH_SHORT).show()
  }
}

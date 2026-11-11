package com.quran.labs.androidquran.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.quran.labs.androidquran.AboutUsActivity
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.tasnim.Announcement
import com.quran.labs.androidquran.tasnim.AppConfig
import com.quran.labs.androidquran.tasnim.AppUpdate
import com.quran.labs.androidquran.tasnim.TasnimConfigRepository
import com.quran.labs.androidquran.tasnim.TasnimReciterRepository
import com.quran.labs.androidquran.tasnim.UpdateNeed
import com.quran.labs.androidquran.tasnim.firstUnseen
import com.quran.labs.androidquran.tasnim.updateNeed
import com.quran.mobile.feature.library.data.LibraryCovers
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch
import com.quran.labs.androidquran.HelpActivity
import com.quran.labs.androidquran.QuranPreferenceActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.mobile.feature.library.LibraryActivity
import com.quran.mobile.feature.livetv.LiveTvActivity
import com.quran.mobile.feature.livetv.radio.RadioActivity

/**
 * The Tasnim start screen: the logo, the main sections (mushaf, library, live video, radio)
 * and the social links. The mushaf opens [QuranActivity]; live video and radio are phase 2
 * stubs. Links, visible sections, update rules and announcements come from the Tasnim server
 * ([TasnimConfigRepository]), with the last copy saved for offline use.
 */
class TasnimLaunchActivity : AppCompatActivity() {

  @Inject lateinit var server: TasnimConfigRepository
  @Inject lateinit var reciters: TasnimReciterRepository

  private var updateDialog: AlertDialog? = null

  override fun onDestroy() {
    // a dialog left open would leak this activity's window (e.g. on rotation)
    updateDialog?.dismiss()
    updateDialog = null
    super.onDestroy()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    // light icons on the dark teal background, in day and night mode
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
    )
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)
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
    findViewById<View>(R.id.launch_library).setOnClickListener {
      startActivity(Intent(this, LibraryActivity::class.java))
    }
    findViewById<View>(R.id.launch_live).setOnClickListener {
      startActivity(Intent(this, LiveTvActivity::class.java))
    }
    findViewById<View>(R.id.launch_radio).setOnClickListener {
      startActivity(Intent(this, RadioActivity::class.java))
    }
    findViewById<View>(R.id.launch_menu).setOnClickListener { showMenu(it) }
    findViewById<ImageButton>(R.id.launch_night).apply {
      // the icon shows the current mode (moon in dark, sun in light); tapping toggles it
      if (isNightMode()) {
        setImageResource(R.drawable.ic_tasnim_moon)
        contentDescription = getString(R.string.tasnim_day_mode)
      } else {
        setImageResource(R.drawable.ic_tasnim_sun)
        contentDescription = getString(R.string.tasnim_night_mode)
      }
      setOnClickListener { toggleNightMode() }
    }

    // Settings from the Tasnim server: the saved copy at once, then a fresh one.
    applyConfig(server.cached())
    lifecycleScope.launch {
      val config = server.refreshConfig()
      applyConfig(config)
      if (!showUpdateIfNeeded(config.update)) {
        firstUnseen(server.announcements(BuildConfig.VERSION_CODE), server.seenAnnouncements())
          ?.let(::showAnnouncement)
      }
    }
    // The reciter list from the server; saved for the next start (the audio code reads it once).
    if (savedInstanceState == null) lifecycleScope.launch { reciters.refresh() }
  }

  /** Links, visible sections and the maintenance note, from the server (or the built-in links). */
  private fun applyConfig(config: AppConfig) {
    bindLink(R.id.launch_website, config.links.website, R.string.tasnim_url_website)
    bindLink(R.id.launch_email, config.links.email, R.string.tasnim_url_email)
    bindLink(R.id.launch_video, config.links.videoChannel, R.string.tasnim_url_video)
    bindLink(R.id.launch_messenger, config.links.messenger, R.string.tasnim_url_messenger)

    findViewById<View>(R.id.launch_library).isVisible = config.features.library
    // live and radio always show; while switched off their screens say they are unavailable

    findViewById<TextView>(R.id.launch_maintenance).apply {
      isVisible = config.maintenance.enabled
      text = config.maintenance.message?.takeIf { it.isNotBlank() }
        ?: getString(R.string.tasnim_maintenance_default)
    }
  }

  /** A server link wins; without one the built-in link is used; with neither the button hides. */
  private fun bindLink(viewId: Int, serverUrl: String?, @StringRes fallbackRes: Int) {
    val url = serverUrl?.takeIf { it.isNotBlank() } ?: getString(fallbackRes).takeIf { it.isNotBlank() }
    findViewById<View>(viewId).apply {
      isVisible = url != null
      setOnClickListener { url?.let(::openUrl) }
    }
  }

  /** Returns true when an update dialog is shown (an announcement then waits for next time). */
  private fun showUpdateIfNeeded(update: AppUpdate): Boolean {
    val need = updateNeed(update, BuildConfig.VERSION_CODE, server.dismissedUpdate())
    if (need == UpdateNeed.NONE || isFinishing) return false
    val required = need == UpdateNeed.REQUIRED
    val message = listOfNotNull(
      getString(if (required) R.string.tasnim_update_required_body else R.string.tasnim_update_available_body),
      update.notes?.takeIf { it.isNotBlank() }
    ).joinToString("\n\n")
    // Without a link from the server, the app's own store page is the way to update.
    val url = update.url?.takeIf { it.isNotBlank() } ?: "market://details?id=$packageName"
    updateDialog = AlertDialog.Builder(this)
      .setTitle(if (required) R.string.tasnim_update_required_title else R.string.tasnim_update_available_title)
      .setMessage(message)
      .setCancelable(!required)
      .setPositiveButton(R.string.tasnim_update_now) { _, _ ->
        openUrl(url)
        // a required update keeps the app closed until it is installed
        if (required) finish()
      }
      .apply {
        if (!required) {
          setNegativeButton(R.string.tasnim_update_later) { _, _ ->
            update.latestVersionCode?.let(server::dismissUpdate)
          }
        }
      }
      .show()
    return true
  }

  private fun showAnnouncement(announcement: Announcement) {
    val card = findViewById<View>(R.id.launch_announcement)
    findViewById<TextView>(R.id.announcement_title).text = announcement.title
    findViewById<TextView>(R.id.announcement_body).apply {
      text = announcement.body
      isVisible = announcement.body.isNotBlank()
    }
    val list = findViewById<LinearLayout>(R.id.announcement_items)
    list.removeAllViews()
    announcement.items.forEach { item ->
      list.addView(TextView(this).apply {
        text = if (item.body.isBlank()) item.title else "${item.title}: ${item.body}"
        setTextColor(ContextCompat.getColor(context, R.color.tasnim_card_ink))
        textSize = 13f
        setPadding(0, 6.dp, 0, 0)
        gravity = Gravity.CENTER_VERTICAL
        compoundDrawablePadding = 8.dp
        ContextCompat.getDrawable(context, announcementIcon(item.icon))?.mutate()?.let { icon ->
          icon.setTint(ContextCompat.getColor(context, R.color.tasnim_card_teal))
          icon.setBounds(0, 0, 18.dp, 18.dp)
          setCompoundDrawablesRelative(icon, null, null, null)
        }
      })
    }
    findViewById<TextView>(R.id.announcement_action).apply {
      val url = announcement.actionUrl
      isVisible = !announcement.actionText.isNullOrBlank() && url != null
      text = announcement.actionText
      setOnClickListener { url?.let(::openUrl) }
    }
    findViewById<ImageView>(R.id.announcement_image).apply {
      isVisible = false
      announcement.imageUrl?.let { url ->
        lifecycleScope.launch {
          LibraryCovers.load(context, url, resources.displayMetrics.widthPixels)?.let {
            setImageBitmap(it)
            isVisible = true
          }
        }
      }
    }
    findViewById<View>(R.id.announcement_close).setOnClickListener {
      server.markSeen(announcement.id)
      card.isVisible = false
    }
    card.isVisible = true
  }

  private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

  /** The admin's icon choice for an announcement item (unknown names fall back to "info"). */
  private fun announcementIcon(name: String): Int = when (name) {
    "library" -> R.drawable.ic_tasnim_library
    "live" -> R.drawable.ic_tasnim_live
    "radio" -> R.drawable.ic_tasnim_radio
    "book" -> R.drawable.ic_tasnim_mushaf
    else -> R.drawable.ic_tasnim_info
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

  private fun isNightMode(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

  private fun toggleNightMode() {
    val theme = if (isNightMode()) Constants.THEME_LIGHT else Constants.THEME_DARK
    PreferenceManager.getDefaultSharedPreferences(this).edit {
      putString(Constants.PREF_APP_THEME, theme)
    }
    ThemeUtil.setTheme(theme)
  }

  /** Opens a web page, an email (a plain address becomes mailto:) or a Telegram link. */
  private fun openUrl(url: String) {
    val uri = if ('@' in url && !url.contains(':')) "mailto:$url".toUri() else url.toUri()
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

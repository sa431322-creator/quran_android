package com.quran.labs.androidquran.ui.fragment

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.R

/**
 * Tasnim: the ayah share sheet, opened from the share button of the ayah toolbar. Offers the ayah
 * text (with its sura, ayah and page), a framed image, the ayah with its recitation (video or
 * audio), copying the text, or a link (`docs/share-feature-plan.md`).
 */
class AyahShareSheet : BottomSheetDialogFragment() {

  enum class Option { TEXT, IMAGE, AUDIO, COPY, LINK }

  /** The reading screen, which builds and sends what the reader picked. */
  interface Host {
    fun ayahShareTitle(start: SuraAyah, end: SuraAyah): String
    fun onAyahShareOption(option: Option, start: SuraAyah, end: SuraAyah)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View = inflater.inflate(R.layout.tasnim_share_sheet, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val args = requireArguments()
    val start = SuraAyah(args.getInt(ARG_START_SURA), args.getInt(ARG_START_AYAH))
    val end = SuraAyah(args.getInt(ARG_END_SURA), args.getInt(ARG_END_AYAH))
    val host = activity as? Host ?: run {
      dismissAllowingStateLoss()
      return
    }

    view.findViewById<TextView>(R.id.share_sheet_title).text = host.ayahShareTitle(start, end)

    fun bind(id: Int, option: Option) {
      view.findViewById<View>(id).setOnClickListener {
        dismiss()
        host.onAyahShareOption(option, start, end)
      }
    }
    bind(R.id.share_sheet_text, Option.TEXT)
    bind(R.id.share_sheet_image, Option.IMAGE)
    bind(R.id.share_sheet_audio, Option.AUDIO)
    bind(R.id.share_sheet_copy, Option.COPY)
    bind(R.id.share_sheet_link, Option.LINK)
  }

  override fun onStart() {
    super.onStart()
    val sheet = (dialog as? BottomSheetDialog)
      ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
    // the layout draws its own rounded background
    sheet.setBackgroundColor(Color.TRANSPARENT)
    BottomSheetBehavior.from(sheet).apply {
      state = BottomSheetBehavior.STATE_EXPANDED
      skipCollapsed = true
    }
  }

  companion object {
    const val TAG = "AyahShareSheet"
    private const val ARG_START_SURA = "start_sura"
    private const val ARG_START_AYAH = "start_ayah"
    private const val ARG_END_SURA = "end_sura"
    private const val ARG_END_AYAH = "end_ayah"

    fun newInstance(start: SuraAyah, end: SuraAyah) = AyahShareSheet().apply {
      arguments = Bundle().apply {
        putInt(ARG_START_SURA, start.sura)
        putInt(ARG_START_AYAH, start.ayah)
        putInt(ARG_END_SURA, end.sura)
        putInt(ARG_END_AYAH, end.ayah)
      }
    }
  }
}

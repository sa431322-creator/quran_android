package com.quran.labs.androidquran.ui.fragment

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.ui.helpers.JumpDestination
import com.quran.labs.androidquran.util.QuranUtils

/**
 * Tasnim: sura / juz list opened from the reader title. Lists every sura (or juz) with search,
 * highlights the one being read, and jumps to its first page.
 */
class SuraJumpSheet : BottomSheetDialogFragment() {

  /** What the sheet needs from its host, which is the reading screen. */
  interface Host : JumpDestination {
    val quranInfo: QuranInfo
    val quranDisplayData: QuranDisplayData
  }

  private data class Row(val number: Int, val name: String, val meta: String, val page: Int)

  private lateinit var suraRows: List<Row>
  private lateinit var juzRows: List<Row>
  private var showingJuz = false
  private var currentPage = 1

  private val adapter = RowAdapter()
  private lateinit var list: RecyclerView
  private lateinit var search: EditText
  private lateinit var tabSuras: TextView
  private lateinit var tabJuz: TextView

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View = inflater.inflate(R.layout.tasnim_sura_sheet, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val host = requireActivity() as Host
    val context = requireContext()
    val quranInfo = host.quranInfo
    val displayData = host.quranDisplayData
    currentPage = arguments?.getInt(ARG_PAGE, 1) ?: 1

    suraRows = (1..114).map { sura ->
      val makki = getString(if (quranInfo.isMakki(sura)) R.string.makki else R.string.madani)
      val ayahs = getString(
        R.string.tasnim_sura_sheet_ayahs,
        QuranUtils.getLocalizedNumber(quranInfo.getNumberOfAyahs(sura))
      )
      Row(
        sura,
        displayData.getSuraName(context, sura, wantPrefix = false, wantTranslation = false),
        "$ayahs · $makki",
        quranInfo.getPageNumberForSura(sura)
      )
    }
    juzRows = (1..30).map { juz ->
      val page = quranInfo.getStartingPageForJuz(juz)
      Row(
        juz,
        getString(R.string.juz2_description, QuranUtils.getLocalizedNumber(juz)),
        displayData.getSuraNameFromPage(context, page, false) + " · " +
            getString(R.string.tasnim_sura_sheet_page, QuranUtils.getLocalizedNumber(page)),
        page
      )
    }

    list = view.findViewById(R.id.sura_sheet_list)
    list.layoutManager = LinearLayoutManager(context)
    list.adapter = adapter

    search = view.findViewById(R.id.sura_sheet_search)
    search.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
      override fun afterTextChanged(s: Editable?) = refresh(scrollToCurrent = false)
    })

    tabSuras = view.findViewById(R.id.sura_sheet_tab_suras)
    tabJuz = view.findViewById(R.id.sura_sheet_tab_juz)
    tabSuras.setOnClickListener { selectTab(juz = false) }
    tabJuz.setOnClickListener { selectTab(juz = true) }
    selectTab(juz = false)
  }

  override fun onStart() {
    super.onStart()
    val sheet = (dialog as? BottomSheetDialog)
      ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
    // the layout draws its own rounded background
    sheet.setBackgroundColor(Color.TRANSPARENT)
    sheet.layoutParams.height = (resources.displayMetrics.heightPixels * 0.8f).toInt()
    BottomSheetBehavior.from(sheet).apply {
      state = BottomSheetBehavior.STATE_EXPANDED
      skipCollapsed = true
    }
  }

  private fun selectTab(juz: Boolean) {
    showingJuz = juz
    val selectedText = ContextCompat.getColor(requireContext(), R.color.tasnim_on_primary)
    val normalText = ContextCompat.getColor(requireContext(), R.color.title_color)
    listOf(tabSuras to !juz, tabJuz to juz).forEach { (tab, selected) ->
      tab.isSelected = selected
      tab.setBackgroundResource(if (selected) R.drawable.tasnim_segment_selected else 0)
      tab.setTextColor(if (selected) selectedText else normalText)
    }
    refresh(scrollToCurrent = true)
  }

  private fun refresh(scrollToCurrent: Boolean) {
    val query = normalize(search.text.toString())
    val source = if (showingJuz) juzRows else suraRows
    val rows = if (query.isEmpty()) source else source.filter { row ->
      normalize(row.name).contains(query) || row.number.toString() == query
    }
    // find the current row in the full list, so a search never marks the wrong row
    val currentNumber = source.lastOrNull { it.page <= currentPage }?.number
    val current = rows.indexOfFirst { it.number == currentNumber }
    adapter.submit(rows, current)
    if (scrollToCurrent && current >= 0) {
      (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(current, list.height / 3)
    }
  }

  private fun onRowClicked(row: Row) {
    (requireActivity() as Host).jumpTo(row.page)
    dismiss()
  }

  private inner class RowAdapter : RecyclerView.Adapter<RowHolder>() {
    private var rows: List<Row> = emptyList()
    private var current = -1

    fun submit(rows: List<Row>, current: Int) {
      this.rows = rows
      this.current = current
      notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = RowHolder(
      LayoutInflater.from(parent.context).inflate(R.layout.tasnim_sura_sheet_row, parent, false)
    )

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
      val row = rows[position]
      holder.number.text = QuranUtils.getLocalizedNumber(row.number)
      holder.name.text = row.name
      holder.meta.text = row.meta
      if (position == current) {
        holder.itemView.setBackgroundResource(R.drawable.tasnim_sura_row_current)
      } else {
        holder.itemView.background = holder.defaultBackground
      }
      holder.itemView.setOnClickListener { onRowClicked(row) }
    }
  }

  private class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
    val number: TextView = view.findViewById(R.id.sura_row_number)
    val name: TextView = view.findViewById(R.id.sura_row_name)
    val meta: TextView = view.findViewById(R.id.sura_row_meta)
    val defaultBackground = view.background
  }

  companion object {
    const val TAG = "SuraJumpSheet"
    private const val ARG_PAGE = "page"

    fun newInstance(page: Int) = SuraJumpSheet().apply {
      arguments = Bundle().apply { putInt(ARG_PAGE, page) }
    }

    private val DIACRITICS = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")

    /**
     * Lets "بقره", "البقرة" and "بقرة" all match, regardless of Arabic or Persian letters,
     * and turns Arabic-Indic or Persian digits into plain ones.
     */
    private fun normalize(text: String): String = DIACRITICS.replace(text, "")
      .map { c ->
        when (c) {
          in '٠'..'٩' -> '0' + (c - '٠')
          in '۰'..'۹' -> '0' + (c - '۰')
          else -> c
        }
      }.joinToString("")
      .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
      .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
      .replace('ة', 'ه')
      .let { if (it.startsWith("ال")) it.substring(2) else it }
      .trim()
  }
}

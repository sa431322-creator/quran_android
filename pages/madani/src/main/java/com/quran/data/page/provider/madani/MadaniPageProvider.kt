package com.quran.data.page.provider.madani

import com.quran.data.model.audio.Qari
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.labs.androidquran.pages.common.madani.size.DefaultPageSizeCalculator
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.pages.madani.R
import com.quran.labs.androidquran.common.audio.R as audioR

class MadaniPageProvider : PageProvider {

  override fun getDataSource() = dataSource

  override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator =
      DefaultPageSizeCalculator(displaySize)

  override fun getImageVersion() = 8

  override fun getImagesBaseUrl() = "$baseUrl/"

  override fun getImagesZipBaseUrl() = "$baseUrl/zips/"

  override fun getPatchBaseUrl() = "$baseUrl/patches/v"

  override fun getAyahInfoBaseUrl() = "$baseUrl/databases/ayahinfo/"

  override fun getAudioDirectoryName() = "audio"

  override fun getDatabaseDirectoryName() = "databases"

  override fun getAyahInfoDirectoryName() = getDatabaseDirectoryName()

  override fun getDatabasesBaseUrl() = "$baseUrl/databases/"

  override fun getAudioDatabasesBaseUrl() = getDatabasesBaseUrl() + "audio/"

  override fun getImagesDirectoryName() = ""

  override fun getPreviewTitle() = R.string.madani_title

  override fun getPreviewDescription() = R.string.madani_description

  override fun getDefaultQariId(): Int = 0

  override fun getQaris(): List<Qari> {
    return listOf(
      Qari(
        0,
        audioR.string.qari_minshawi_murattal_gapless,
        url = "https://download.quranicaudio.com/quran/muhammad_siddeeq_al-minshaawee/",
        path = "minshawi_murattal",
        hasGaplessAlternative = false,
        db = "minshawi_murattal"
      ),
      Qari(
        1,
        audioR.string.qari_husary_gapless,
        url = "https://download.quranicaudio.com/quran/mahmood_khaleel_al-husaree/",
        path = "husary",
        hasGaplessAlternative = false,
        db = "husary"
      ),
      Qari(
        2,
        audioR.string.qari_basfar,
        url = "https://mirrors.quranicaudio.com/everyayah/Abdullah_Basfar_192kbps/",
        path = "2",
        hasGaplessAlternative = false,
        db = null
      ),
      everyAyah(100, audioR.string.qari_abdulbaset, "Abdul_Basit_Murattal_192kbps", onMirror = true),
      everyAyah(101, audioR.string.qari_abdulbaset_mujawwad, "Abdul_Basit_Mujawwad_128kbps", onMirror = true),
      everyAyah(102, audioR.string.qari_suadis, "Abdurrahmaan_As-Sudais_192kbps", onMirror = true),
      everyAyah(103, audioR.string.qari_shatri, "Abu_Bakr_Ash-Shaatree_128kbps", onMirror = true),
      everyAyah(104, audioR.string.qari_ajamy_gapless, "ahmed_ibn_ali_al_ajamy_128kbps"),
      everyAyah(105, audioR.string.qari_afasy, "Alafasy_128kbps", onMirror = true),
      everyAyah(106, audioR.string.qari_saad_al_ghamdi, "Ghamadi_40kbps", onMirror = true),
      everyAyah(107, audioR.string.qari_hani_rifai, "Hani_Rifai_192kbps", onMirror = true),
      everyAyah(108, audioR.string.qari_husary_mujawwad, "Husary_128kbps_Mujawwad", onMirror = true),
      everyAyah(109, audioR.string.qari_husary_muallim, "Husary_Muallim_128kbps", onMirror = true),
      everyAyah(110, audioR.string.qari_hudhayfi, "Hudhaify_128kbps", onMirror = true),
      everyAyah(111, audioR.string.qari_ibrahim_alakhdar_gapless, "Ibrahim_Akhdar_32kbps"),
      everyAyah(112, audioR.string.qari_muaiqly, "MaherAlMuaiqly128kbps"),
      everyAyah(113, audioR.string.qari_minshawi_mujawwad, "Minshawy_Mujawwad_192kbps", onMirror = true),
      everyAyah(114, audioR.string.qari_tablawy, "Mohammad_al_Tablaway_128kbps", onMirror = true),
      everyAyah(115, audioR.string.qari_ayyoub, "Muhammad_Ayyoub_128kbps", onMirror = true),
      everyAyah(116, audioR.string.qari_jibreel, "Muhammad_Jibreel_128kbps", onMirror = true),
      everyAyah(117, audioR.string.qari_mostafa_ismaeel_gapless, "Mustafa_Ismail_48kbps"),
      everyAyah(118, audioR.string.qari_shuraym, "Saood_ash-Shuraym_128kbps", onMirror = true),
      everyAyah(119, audioR.string.qari_salah_bukhatir_gapless, "Salaah_AbdulRahman_Bukhatir_128kbps"),
      everyAyah(120, audioR.string.qari_abdulmuhsin_qasim_gapless, "Muhsin_Al_Qasim_192kbps"),
      everyAyah(121, audioR.string.qari_juhany_gapless, "Abdullaah_3awwaad_Al-Juhaynee_128kbps"),
      everyAyah(122, audioR.string.qari_salah_budair_gapless, "Salah_Al_Budair_128kbps"),
      everyAyah(123, audioR.string.qari_abdullah_matroud_gapless, "Abdullah_Matroud_128kbps"),
      everyAyah(124, audioR.string.qari_ahmad_nauina_gapless, "Ahmed_Neana_128kbps"),
      everyAyah(125, audioR.string.qari_muhammad_abdulkareem, "Muhammad_AbdulKareem_128kbps"),
      everyAyah(126, audioR.string.qari_khalifa_taniji_gapless, "khalefa_al_tunaiji_64kbps"),
      everyAyah(127, audioR.string.qari_mahmoud_ali_albana_gapless, "mahmoud_ali_al_banna_32kbps"),
      everyAyah(128, audioR.string.qari_karim_mansoori, "Karim_Mansoori_40kbps"),
      everyAyah(129, audioR.string.qari_parhizgar, "Parhizgar_48kbps"),
      everyAyah(130, audioR.string.qari_khalid_qahtani_gapless, "Khaalid_Abdullaah_al-Qahtaanee_192kbps"),
      everyAyah(131, audioR.string.qari_dussary, "Yasser_Ad-Dussary_128kbps", onMirror = true),
      everyAyah(132, audioR.string.qari_qatami_gapless, "Nasser_Alqatami_128kbps"),
      everyAyah(133, audioR.string.qari_ali_hajjaj_alsouasi_gapless, "Ali_Hajjaj_AlSuesy_128kbps"),
      everyAyah(134, audioR.string.qari_sahl_yaseen_gapless, "Sahl_Yassin_128kbps"),
      everyAyah(135, audioR.string.qari_aziz_alili_gapless, "aziz_alili_128kbps"),
      everyAyah(136, audioR.string.qari_yasser_salama, "Yaser_Salamah_128kbps", onMirror = true),
      everyAyah(137, audioR.string.qari_akram_al_alaqmi, "Akram_AlAlaqimy_128kbps"),
      everyAyah(138, audioR.string.qari_ali_jaber_gapless, "Ali_Jaber_64kbps"),
      everyAyah(139, audioR.string.qari_fares_abbad_gapless, "Fares_Abbad_64kbps"),
      everyAyah(140, audioR.string.qari_ayman_suwaid_gapped, "Ayman_Sowaid_64kbps", onMirror = true),
      everyAyah(141, audioR.string.qari_makarem_kabiri, "translations/Makarem_Kabiri_16Kbps"),
      everyAyah(142, audioR.string.qari_fooladvand_hedayatfar, "translations/Fooladvand_Hedayatfar_40Kbps")
    )
  }

  // Gapped (one file per ayah) reciters from EveryAyah. Ids start at 100 to stay clear of
  // any qaris upstream adds after its own ids 0-2.
  private fun everyAyah(id: Int, nameResource: Int, folder: String, onMirror: Boolean = false) =
    Qari(
      id,
      nameResource,
      url = (if (onMirror) everyAyahMirrorUrl else everyAyahUrl) + folder + "/",
      path = folder.substringAfterLast('/'),
      hasGaplessAlternative = false,
      db = null
    )

  companion object {
    private const val baseUrl = "https://android.quran.com/data"
    private const val everyAyahUrl = "https://everyayah.com/data/"
    private const val everyAyahMirrorUrl = "https://mirrors.quranicaudio.com/everyayah/"
    private val dataSource by lazy { MadaniDataSource() }
  }
}

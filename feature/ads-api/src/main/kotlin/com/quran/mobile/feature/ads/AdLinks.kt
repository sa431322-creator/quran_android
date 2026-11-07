package com.quran.mobile.feature.ads

import java.net.IDN

// Same rules as `adLinkProblem` in tasneem-backend packages/contracts/src/ads.ts. Both sides run
// the shared cases in test-cases/ads.json (copied to src/test/resources/ads-cases.json).

const val AD_LINK_MAX = 2048

private val TG_LINK = Regex("""^tg://resolve\?domain=[A-Za-z0-9_]{5,32}$""")
private val BOOK_LINK = Regex("""^tasneem://library/book/[1-9][0-9]{0,9}$""")
private val MAILTO_LINK = Regex(
  """^mailto:[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}(\?subject=[^&#]*)?$""",
  RegexOption.IGNORE_CASE,
)
private val HTTPS_PREFIX = Regex("^https://", RegexOption.IGNORE_CASE)
private val PORT = Regex("^[0-9]{1,5}$")
private val HOST_LABEL = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
private val TLD = Regex("^[a-z][a-z0-9-]+$")
private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

private fun Char.isRefused(): Boolean {
  val type = Character.getType(this)
  return isWhitespace() || this == '\\' || type == Character.CONTROL.toInt() ||
    type == Character.FORMAT.toInt() || this == ' '
}

/**
 * Why an ad link must not be opened, or null when it may: https (no user info, no IP host, a plain
 * domain), mailto: with one address, `tg://resolve?domain=…`, or `tasneem://library/book/{id}`.
 */
fun adLinkProblem(raw: String): String? {
  if (raw.length > AD_LINK_MAX) return "too long"
  if (raw.any { it.isRefused() }) return "spaces, invisible characters or a backslash"
  if (TG_LINK.matches(raw) || BOOK_LINK.matches(raw)) return null
  if (raw.startsWith("mailto:", ignoreCase = true)) {
    return if (MAILTO_LINK.matches(raw)) null else "not a valid email link"
  }
  if (!HTTPS_PREFIX.containsMatchIn(raw)) return "not an allowed scheme"

  val authority = raw.substring("https://".length).split('/', '?', '#').first()
  if ('@' in authority) return "user info"
  if ('%' in authority) return "not a valid domain"
  var host = authority
  if (host.startsWith("[")) return "IP address"
  val colon = host.lastIndexOf(':')
  if (colon >= 0) {
    val port = host.substring(colon + 1)
    if (port.isNotEmpty() && (!PORT.matches(port) || port.toInt() > 65535)) return "not a valid port"
    host = host.substring(0, colon)
  }
  host = try {
    IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase()
  } catch (_: IllegalArgumentException) {
    return "not a valid domain"
  }
  if (IPV4.matches(host)) return "IP address"
  val labels = host.split('.')
  if (labels.size < 2 || !labels.all { HOST_LABEL.matches(it) } || !TLD.matches(labels.last())) {
    return "not a valid domain"
  }
  return null
}

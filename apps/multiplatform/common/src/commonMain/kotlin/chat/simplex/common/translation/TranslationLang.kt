package chat.simplex.common.translation

/**
 * Lightweight, dependency-free language detection and the list of languages offered in the UI.
 *
 * This is intentionally a heuristic (script based): it is enough to (a) pick the target language for
 * "auto" translation and (b) skip translating a message that is already in the target language. It is a
 * pure function so it can be unit-tested without any platform / network dependency.
 */

data class Language(val code: String, val displayName: String)

/** Languages offered in the target-language picker and the per-message language menu. */
val translationLanguages: List<Language> = listOf(
  Language("en", "English"),
  Language("zh", "中文 (Chinese)"),
  Language("ja", "日本語 (Japanese)"),
  Language("ko", "한국어 (Korean)"),
  Language("ru", "Русский (Russian)"),
  Language("fr", "Français (French)"),
  Language("de", "Deutsch (German)"),
  Language("es", "Español (Spanish)"),
  Language("pt", "Português (Portuguese)"),
  Language("it", "Italiano (Italian)"),
  Language("ar", "العربية (Arabic)"),
  Language("hi", "हिन्दी (Hindi)"),
  Language("th", "ไทย (Thai)"),
  Language("vi", "Tiếng Việt (Vietnamese)"),
  Language("id", "Bahasa Indonesia"),
  Language("tr", "Türkçe (Turkish)"),
  Language("nl", "Nederlands (Dutch)"),
  Language("pl", "Polski (Polish)"),
  Language("uk", "Українська (Ukrainian)"),
  Language("he", "עברית (Hebrew)"),
  Language("el", "Ελληνικά (Greek)"),
)

fun languageDisplayName(code: String): String =
  translationLanguages.firstOrNull { it.code == code }?.displayName ?: code

/**
 * Best-effort detection of the dominant script/language of [text]. Returns a language code from
 * [translationLanguages], defaulting to "en" for Latin-script text (European languages can't be
 * separated cheaply and English is a safe default for the skip check).
 */
fun detectLanguage(text: String): String = detectLanguageOrNull(text) ?: "en"

/**
 * Same detection as [detectLanguage], but returns `null` when the script is inconclusive (Latin
 * script, where European languages cannot be separated cheaply). Providers that can detect the
 * source language themselves are better left to do so in that case.
 */
fun detectLanguageOrNull(text: String): String? {
  if (text.isBlank()) return null
  var cjk = 0
  var hiraKata = 0
  var hangul = 0
  var cyrillic = 0
  var arabic = 0
  var hebrew = 0
  var greek = 0
  var thai = 0
  var devanagari = 0
  var latin = 0
  var other = 0
  for (ch in text) {
    val c = ch.code
    when {
      c in 0x3040..0x30FF -> hiraKata++
      c in 0x4E00..0x9FFF || c in 0x3400..0x4DBF -> cjk++
      c in 0xAC00..0xD7A3 || c in 0x1100..0x11FF -> hangul++
      c in 0x0400..0x04FF -> cyrillic++
      c in 0x0600..0x06FF || c in 0x0750..0x077F -> arabic++
      c in 0x0590..0x05FF -> hebrew++
      c in 0x0370..0x03FF -> greek++
      c in 0x0E00..0x0E7F -> thai++
      c in 0x0900..0x097F -> devanagari++
      c in 0x0041..0x005A || c in 0x0061..0x007A || c in 0x00C0..0x024F -> latin++
      else -> if (!ch.isWhitespace() && !ch.isDigit() && ch.isLetterOrDigit()) other++
    }
  }
  // Japanese uses kana alongside kanji; presence of kana is decisive.
  if (hiraKata > 0) return "ja"
  if (hangul > 0) return "ko"
  if (cyrillic > 0 && cyrillic >= latin) return "ru"
  if (arabic > 0) return "ar"
  if (hebrew > 0) return "he"
  if (greek > 0) return "el"
  if (thai > 0) return "th"
  if (devanagari > 0) return "hi"
  if (cjk > 0) return "zh"
  return null
}

/**
 * Whether [text] already appears to be in [targetLang], in which case automatic translation should
 * skip it. Only the strong signals (non-Latin scripts and CJK) are used; Latin-script target
 * languages are always translated because we cannot reliably detect them.
 */
fun isLikelyAlreadyInLanguage(text: String, targetLang: String): Boolean {
  val detected = detectLanguage(text)
  return detected == targetLang && detected != "en"
}

package chat.simplex.app

import chat.simplex.common.translation.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranslationApiTest {
  // ---- MD5 (used for Baidu signing) ----
  @Test
  fun testMd5KnownVectors() {
    assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5Hex(""))
    assertEquals("900150983cd24fb0d6963f7d28e17f72", md5Hex("abc"))
    assertEquals("9e107d9d372bb6826bd81d3542a419d6", md5Hex("The quick brown fox jumps over the lazy dog"))
    assertEquals("e4d909c290d0fb1ca068ffaddf22cbd0", md5Hex("The quick brown fox jumps over the lazy dog."))
  }

  // ---- URL encoding ----
  @Test
  fun testUrlEncode() {
    assertEquals("hello%20world", urlEncode("hello world"))
    assertEquals("%E4%BD%A0%E5%A5%BD", urlEncode("你好"))
    assertEquals("a-_.~", urlEncode("a-_.~"))
    assertEquals("%26%3D%3F", urlEncode("&=?"))
  }

  // ---- Language detection ----
  @Test
  fun testDetectLanguage() {
    assertEquals("en", detectLanguage("hello world"))
    assertEquals("zh", detectLanguage("你好世界"))
    assertEquals("ja", detectLanguage("こんにちは"))
    assertEquals("ja", detectLanguage("日本語です"))
    assertEquals("ko", detectLanguage("안녕하세요"))
    assertEquals("ru", detectLanguage("Привет мир"))
    assertEquals("ar", detectLanguage("مرحبا"))
    assertEquals("he", detectLanguage("שלום"))
    assertEquals("el", detectLanguage("Γειά σου"))
    assertEquals("th", detectLanguage("สวัสดี"))
    assertEquals("hi", detectLanguage("नमस्ते"))
    assertEquals("en", detectLanguage(""))
  }

  @Test
  fun testIsLikelyAlreadyInLanguage() {
    assertTrue(isLikelyAlreadyInLanguage("你好世界", "zh"))
    assertTrue(isLikelyAlreadyInLanguage("こんにちは", "ja"))
    // English is never skipped (Latin scripts are not reliably detectable).
    assertEquals(false, isLikelyAlreadyInLanguage("hello", "en"))
    // Wrong target is not skipped.
    assertEquals(false, isLikelyAlreadyInLanguage("你好", "ja"))
  }

  // ---- Rate config normalization ----
  @Test
  fun testRateConfigNormalized() {
    val extreme = TranslationRateConfig(requestIntervalMs = -5, maxConcurrent = 999, batchLimit = 9999).normalized()
    assertEquals(0L, extreme.requestIntervalMs)
    assertEquals(8, extreme.maxConcurrent)
    assertEquals(100, extreme.batchLimit)
    val ok = TranslationRateConfig(requestIntervalMs = 500, maxConcurrent = 2, batchLimit = 20).normalized()
    assertEquals(TranslationRateConfig(500, 2, 20), ok)
  }

  // ---- Google ----
  @Test
  fun testGoogleUrl() {
    val url = googleTranslateUrl("hello world", "zh")
    assertTrue(url.startsWith("https://translate.googleapis.com/translate_a/single"))
    assertTrue(url.contains("tl=zh"))
    assertTrue(url.contains("q=hello%20world"))
  }

  @Test
  fun testParseGoogleResponse() {
    val body = """[[["你好世界","hello world",null,null]],null,"en"]"""
    val e = parseGoogleResponse(body)
    assertEquals(TranslationStatus.DONE, e.status)
    assertEquals("你好世界", e.translatedText)
    assertEquals("en", e.detectedLang)
  }

  @Test
  fun testParseGoogleMultiSegment() {
    val body = """[[["Hello ","hi ",null,null],["world","there",null,null]],null,"en"]"""
    assertEquals("Hello world", parseGoogleResponse(body).translatedText)
  }

  // ---- Bing ----
  @Test
  fun testParseBingResponse() {
    val body = """[{"detectedLanguage":{"language":"en","score":1.0},"translations":[{"text":"你好","to":"zh-Hans"}]}]"""
    val e = parseBingResponse(body)
    assertEquals(TranslationStatus.DONE, e.status)
    assertEquals("你好", e.translatedText)
    assertEquals("en", e.detectedLang)
  }

  @Test
  fun testBingBody() {
    // The free edge endpoint takes a bare array of strings (the old [{"Text": ...}] shape is rejected).
    assertEquals("""["hi"]""", bingTranslateBody("hi"))
  }

  @Test
  fun testBingUrl() {
    // No token endpoint any more, and Chinese must carry the script.
    assertEquals("https://edge.microsoft.com/translate/translatetext?from=&to=zh-Hans", bingTranslateUrl("zh"))
    assertEquals("https://edge.microsoft.com/translate/translatetext?from=&to=en", bingTranslateUrl("en"))
  }

  @Test
  fun testMsLangCode() {
    assertEquals("zh-Hans", msLangCode("zh"))
    assertEquals("zh-Hant", msLangCode("zh-TW"))
    assertEquals("ja", msLangCode("ja"))
  }

  // ---- Baidu ----
  @Test
  fun testBaiduSign() {
    // sign = md5(appId + q + salt + key)
    assertEquals(md5Hex("appq123key"), baiduSign("app", "q", "123", "key"))
  }

  @Test
  fun testBaiduLangCode() {
    assertEquals("jp", baiduLangCode("ja"))
    assertEquals("kor", baiduLangCode("ko"))
    assertEquals("fra", baiduLangCode("fr"))
    assertEquals("spa", baiduLangCode("es"))
    assertEquals("en", baiduLangCode("en"))
    assertEquals("de", baiduLangCode("de"))
  }

  @Test
  fun testBaiduUrlUsesDetectedSource() {
    val withSource = baiduTranslateUrl("app", "你好", "123", "key", "en", "zh")
    assertTrue(withSource.contains("&from=zh&"))
    // Latin script is inconclusive locally, so Baidu is left to detect it.
    val auto = baiduTranslateUrl("app", "hello", "123", "key", "zh", null)
    assertTrue(auto.contains("&from=auto&"))
    assertTrue(auto.contains("&to=zh&"))
  }

  @Test
  fun testDetectLanguageOrNull() {
    assertEquals("zh", detectLanguageOrNull("你好"))
    assertEquals("ja", detectLanguageOrNull("こんにちは"))
    assertEquals("ru", detectLanguageOrNull("Привет"))
    assertNull(detectLanguageOrNull("hello"))
    assertNull(detectLanguageOrNull(""))
    // detectLanguage still defaults to English.
    assertEquals("en", detectLanguage("hello"))
  }

  @Test
  fun testParseBaiduMultiline() {
    val body = """{"from":"en","to":"zh","trans_result":[{"src":"a","dst":"甲"},{"src":"b","dst":"乙"}]}"""
    assertEquals("甲\n乙", parseBaiduResponse(body).translatedText)
  }

  @Test
  fun testParseBaiduEmptyResult() {
    val body = """{"from":"en","to":"zh","trans_result":[]}"""
    assertEquals(TranslationStatus.ERROR, parseBaiduResponse(body).status)
  }

  @Test
  fun testParseBaiduResponse() {
    val body = """{"from":"en","to":"zh","trans_result":[{"src":"hello","dst":"你好"}]}"""
    val e = parseBaiduResponse(body)
    assertEquals(TranslationStatus.DONE, e.status)
    assertEquals("你好", e.translatedText)
    assertEquals("en", e.detectedLang)
  }

  @Test
  fun testParseBaiduError() {
    val body = """{"error_code":"54001","error_msg":"Invalid Sign"}"""
    val e = parseBaiduResponse(body)
    assertEquals(TranslationStatus.ERROR, e.status)
    assertTrue(e.error!!.contains("54001"))
  }

  // ---- AI / Custom (OpenAI Responses) ----
  @Test
  fun testAiResponsesUrl() {
    assertEquals("https://api.deepseek.com/v1/responses", aiResponsesUrl("https://api.deepseek.com/v1"))
    assertEquals("https://api.deepseek.com/v1/responses", aiResponsesUrl("https://api.deepseek.com/v1/"))
    // Already ends with /responses: keep as-is.
    assertEquals("https://x/responses", aiResponsesUrl("https://x/responses"))
  }

  @Test
  fun testParseAiResponsesOutputText() {
    val body = """{"output_text":"你好"}"""
    assertEquals("你好", parseAiResponsesResponse(body).translatedText)
  }

  @Test
  fun testParseAiResponsesOutputArray() {
    val body = """{"output":[{"type":"message","content":[{"type":"output_text","text":"你好世界"}]}]}"""
    val e = parseAiResponsesResponse(body)
    assertEquals(TranslationStatus.DONE, e.status)
    assertEquals("你好世界", e.translatedText)
  }

  @Test
  fun testParseAiResponsesChatCompletionsFallback() {
    val body = """{"choices":[{"message":{"content":"你好"}}]}"""
    assertEquals("你好", parseAiResponsesResponse(body).translatedText)
  }

  @Test
  fun testParseAiResponsesError() {
    val body = """{"error":{"message":"invalid api key"}}"""
    val e = parseAiResponsesResponse(body)
    assertEquals(TranslationStatus.ERROR, e.status)
    assertEquals("invalid api key", e.error)
  }

  @Test
  fun testAiBodyContainsInput() {
    val body = aiResponsesBody("deepseek-chat", "hello", "zh")
    assertTrue(body.contains("\"model\":\"deepseek-chat\""))
    assertTrue(body.contains("\"role\":\"system\""))
    assertTrue(body.contains("\"role\":\"user\""))
    assertTrue(body.contains("hello"))
  }

  // ---- Compose translation undo logic ----
  @Test
  fun testCanUndoTranslation() {
    // No translation -> nothing to undo.
    assertNull(canUndoTranslation(null, null, "hello"))
    // Translate "hi" -> "你好"; text still equals the translation -> can undo to "hi".
    assertEquals("hi", canUndoTranslation("hi", "你好", "你好"))
    // User edited after translating -> cannot undo.
    assertNull(canUndoTranslation("hi", "你好", "你好 world"))
    // Result empty -> cannot undo.
    assertNull(canUndoTranslation("hi", "", ""))
  }

  @Test
  fun testTranslationEntryFlags() {
    assertTrue(TranslationEntry(TranslationStatus.LOADING).isLoading)
    assertTrue(TranslationEntry(TranslationStatus.DONE, "x").isDone)
    assertEquals(false, TranslationEntry(TranslationStatus.ERROR).isDone)
  }
}

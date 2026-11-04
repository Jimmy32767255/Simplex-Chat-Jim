package chat.simplex.common.translation

import kotlinx.serialization.json.*

/**
 * Pure functions that build provider request URLs/bodies and parse provider responses.
 *
 * Everything here is deliberately free of platform/network dependencies so it can be unit-tested on
 * `commonTest` (see `TranslationApiTest`). The network layer lives in [TranslationHttp] and the
 * orchestration in [TranslationService].
 */

// region ---- URL encoding (no java.net in commonMain) ----

internal fun urlEncode(s: String): String {
  val sb = StringBuilder()
  for (b in s.encodeToByteArray()) {
    val c = b.toInt() and 0xFF
    if ((c in 'A'.code..'Z'.code) || (c in 'a'.code..'z'.code) || (c in '0'.code..'9'.code) ||
        c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code) {
      sb.append(c.toChar())
    } else {
      sb.append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
    }
  }
  return sb.toString()
}

private val HEX = "0123456789ABCDEF".toCharArray()

/** 32-bit left rotation (Kotlin stdlib lacks Integer.rotateLeft in commonMain). */
private fun rotl(x: Int, n: Int): Int = (x shl (n and 31)) or (x ushr (32 - (n and 31)).let { if (it == 32) 0 else it })

// endregion

// region ---- MD5 (Baidu signing; no java.security in commonMain) ----

/** Standard MD5 hex digest of the UTF-8 bytes of [input] (lower-case hex). */
internal fun md5Hex(input: String): String {
  val msg = input.encodeToByteArray()
  val originalLenBits = msg.size.toLong() * 8
  // Padding: append 0x80, then zeros until length % 64 == 56, then 8-byte little-endian bit length.
  val padLen = ((56 - (msg.size + 1) % 64) + 64) % 64
  val total = msg.size + 1 + padLen + 8
  val buf = ByteArray(total)
  msg.copyInto(buf)
  buf[msg.size] = 0x80.toByte()
  for (i in 0 until 8) {
    buf[total - 8 + i] = ((originalLenBits ushr (8 * i)) and 0xFF).toByte()
  }

  var a0 = 0x67452301
  var b0 = 0xefcdab89.toInt()
  var c0 = 0x98badcfe.toInt()
  var d0 = 0x10325476

  val s = intArrayOf(
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
  )
  val k = IntArray(64) { i -> (kotlin.math.floor(kotlin.math.abs(kotlin.math.sin(i + 1.0)) * 4294967296.0).toLong() and 0xFFFFFFFFL).toInt() }

  var offset = 0
  while (offset < total) {
    val m = IntArray(16)
    for (i in 0 until 16) {
      m[i] = (buf[offset + i * 4].toInt() and 0xFF) or
        ((buf[offset + i * 4 + 1].toInt() and 0xFF) shl 8) or
        ((buf[offset + i * 4 + 2].toInt() and 0xFF) shl 16) or
        ((buf[offset + i * 4 + 3].toInt() and 0xFF) shl 24)
    }
    var a = a0
    var b = b0
    var c = c0
    var d = d0
    for (i in 0 until 64) {
      var f: Int
      var g: Int
      when {
        i < 16 -> { f = (b and c) or (b.inv() and d); g = i }
        i < 32 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
        i < 48 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
        else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
      }
      val tmp = d
      d = c
      c = b
      val sum = a + f + k[i] + m[g]
      b = b + rotl(sum, s[i])
      a = tmp
    }
    a0 += a; b0 += b; c0 += c; d0 += d
    offset += 64
  }

  fun le(x: Int): String {
    val sb = StringBuilder()
    for (i in 0 until 4) sb.append(HEX[(x ushr (8 * i + 4)) and 0x0F]).append(HEX[(x ushr (8 * i)) and 0x0F])
    return sb.toString()
  }
  return (le(a0) + le(b0) + le(c0) + le(d0)).lowercase()
}

// endregion

// region ---- Google (free endpoint) ----

internal fun googleTranslateUrl(text: String, targetLang: String): String =
  "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=${urlEncode(targetLang)}&dt=t&q=${urlEncode(text)}"

internal fun parseGoogleResponse(body: String): TranslationEntry {
  return try {
    val root = Json.parseToJsonElement(body).jsonArray
    val segments = root[0].jsonArray
    val sb = StringBuilder()
    for (seg in segments) {
      val arr = seg.jsonArray
      if (arr.size > 0 && arr[0] is JsonPrimitive && arr[0].jsonPrimitive.isString) {
        sb.append(arr[0].jsonPrimitive.content)
      }
    }
    val detected = if (root.size > 2 && root[2] is JsonPrimitive) root[2].jsonPrimitive.contentOrNull else null
    TranslationEntry(TranslationStatus.DONE, sb.toString(), detectedLang = detected)
  } catch (e: Exception) {
    TranslationEntry(TranslationStatus.ERROR, error = "Google response parse error: ${e.message}")
  }
}

// endregion

// region ---- Bing (free edge endpoint) ----

/**
 * The legacy `edge.microsoft.com/translate/auth` token endpoint was retired in 2026, which made every
 * Bing translation fail. The free Edge endpoint is now called directly: it needs no token and takes a
 * bare JSON array of strings.
 */
internal fun bingTranslateUrl(targetLang: String): String =
  "https://edge.microsoft.com/translate/translatetext?from=&to=${urlEncode(msLangCode(targetLang))}"

/**
 * Microsoft's own language codes: Chinese has to carry the script (`zh-Hans` / `zh-Hant`), otherwise
 * the request is rejected.
 */
internal fun msLangCode(code: String): String = when (code) {
  "zh" -> "zh-Hans"
  "zh-TW", "zh-Hant" -> "zh-Hant"
  else -> code
}

internal fun bingTranslateBody(text: String): String = buildJsonArray { add(text) }.toString()

internal fun parseBingResponse(body: String): TranslationEntry {
  return try {
    val root = Json.parseToJsonElement(body).jsonArray
    val first = root[0].jsonObject
    val translated = first["translations"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
    val detected = first["detectedLanguage"]?.jsonObject?.get("language")?.jsonPrimitive?.contentOrNull
    if (translated != null) TranslationEntry(TranslationStatus.DONE, translated, detectedLang = detected)
    else TranslationEntry(TranslationStatus.ERROR, error = "Bing response has no translation")
  } catch (e: Exception) {
    TranslationEntry(TranslationStatus.ERROR, error = "Bing response parse error: ${e.message}")
  }
}

// endregion

// region ---- Baidu (API key; MD5 signed) ----

internal fun baiduSign(appId: String, q: String, salt: String, key: String): String = md5Hex(appId + q + salt + key)

/**
 * Builds a Baidu request. [sourceLang] is an already detected language code (or `null` when the
 * script is inconclusive); when it is known it is sent explicitly as `from`, because Baidu's own
 * detection (`from=auto`) mislabels some inputs and then returns the text unchanged.
 */
internal fun baiduTranslateUrl(
  appId: String,
  q: String,
  salt: String,
  key: String,
  targetLang: String,
  sourceLang: String? = null,
): String {
  val sign = baiduSign(appId, q, salt, key)
  // Baidu uses its own language codes: zh, en, jp, kor, fra, de, spa, ru, ara, th, vie, ...
  val baiduLang = baiduLangCode(targetLang)
  val from = sourceLang?.let { baiduLangCode(it) } ?: "auto"
  return "https://fanyi-api.baidu.com/api/trans/vip/translate?q=${urlEncode(q)}&from=${urlEncode(from)}&to=${urlEncode(baiduLang)}&appid=${urlEncode(appId)}&salt=${urlEncode(salt)}&sign=$sign"
}

internal fun baiduLangCode(code: String): String = when (code) {
  "ja" -> "jp"
  "ko" -> "kor"
  "fr" -> "fra"
  "es" -> "spa"
  "vi" -> "vie"
  "ar" -> "ara"
  "th" -> "th"
  "ru" -> "ru"
  "en" -> "en"
  "zh" -> "zh"
  else -> code
}

internal fun parseBaiduResponse(body: String): TranslationEntry {
  return try {
    val root = Json.parseToJsonElement(body).jsonObject
    val errCode = root["error_code"]?.jsonPrimitive?.contentOrNull
    if (errCode != null && errCode != "0" && errCode != "52000") {
      return TranslationEntry(TranslationStatus.ERROR, error = "Baidu error $errCode: ${root["error_msg"]?.jsonPrimitive?.contentOrNull ?: ""}")
    }
    val from = root["from"]?.jsonPrimitive?.contentOrNull
    // Baidu returns one entry per input line, so the translation has to be re-joined with newlines.
    val lines = root["trans_result"]?.jsonArray?.mapNotNull { it.jsonObject["dst"]?.jsonPrimitive?.contentOrNull }
    if (lines.isNullOrEmpty()) return TranslationEntry(TranslationStatus.ERROR, error = "Baidu response has no translation")
    TranslationEntry(TranslationStatus.DONE, lines.joinToString("\n"), detectedLang = from)
  } catch (e: Exception) {
    TranslationEntry(TranslationStatus.ERROR, error = "Baidu response parse error: ${e.message}")
  }
}

// endregion

// region ---- AI / Custom (OpenAI Responses protocol) ----

internal fun aiResponsesUrl(baseUrl: String): String {
  val b = baseUrl.trim().trimEnd('/')
  val path = if (b.endsWith("/responses")) b else "$b/responses"
  return path
}

internal fun aiSystemPrompt(targetLang: String): String {
  val langName = translationLanguages.firstOrNull { it.code == targetLang }?.displayName ?: targetLang
  return "You are a professional translation engine. Translate the user's text into $langName. " +
    "Preserve the original meaning, tone and formatting. Output ONLY the translation, without quotes, " +
    "explanations or any extra text."
}

internal fun aiResponsesBody(model: String, text: String, targetLang: String): String =
  buildJsonObject {
    put("model", model)
    put("input", buildJsonArray {
      add(buildJsonObject {
        put("role", "system")
        put("content", aiSystemPrompt(targetLang))
      })
      add(buildJsonObject {
        put("role", "user")
        put("content", text)
      })
    })
  }.toString()

/** Parse an OpenAI Responses payload, tolerating chat-completions-style fallbacks. */
internal fun parseAiResponsesResponse(body: String): TranslationEntry {
  return try {
    val root = Json.parseToJsonElement(body).jsonObject
    // 1. top-level convenience field
    (root["output_text"]?.jsonPrimitive?.contentOrNull ?: "").let { if (it.isNotBlank()) return TranslationEntry(TranslationStatus.DONE, it) }
    // 2. Responses API: output[].content[].text (type output_text)
    val output = root["output"]?.jsonArray
    if (output != null) {
      val sb = StringBuilder()
      for (item in output) {
        val content = item.jsonObject["content"]?.jsonArray ?: continue
        for (part in content) {
          val t = part.jsonObject["text"]?.jsonPrimitive?.contentOrNull
          if (!t.isNullOrBlank()) sb.append(t)
        }
      }
      if (sb.isNotBlank()) return TranslationEntry(TranslationStatus.DONE, sb.toString())
    }
    // 3. chat completions fallback: choices[].message.content
    val choices = root["choices"]?.jsonArray
    if (choices != null && choices.isNotEmpty()) {
      val t = choices[0].jsonObject["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
      if (!t.isNullOrBlank()) return TranslationEntry(TranslationStatus.DONE, t)
    }
    // 4. surface an API error if present
    val err = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    TranslationEntry(TranslationStatus.ERROR, error = err ?: "AI response has no text")
  } catch (e: Exception) {
    TranslationEntry(TranslationStatus.ERROR, error = "AI response parse error: ${e.message}")
  }
}

// endregion

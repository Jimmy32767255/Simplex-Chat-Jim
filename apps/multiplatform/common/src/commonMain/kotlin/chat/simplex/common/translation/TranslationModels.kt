package chat.simplex.common.translation

import kotlinx.serialization.Serializable

/**
 * Translation feature: models and configuration.
 *
 * This is a purely client-side feature: the configuration is stored only in the local
 * [chat.simplex.common.model.AppPreferences] (never in the exported `AppSettings`), and the
 * API keys are encrypted with the platform [chat.simplex.common.platform.cryptor] before being saved.
 */

/** Machine-translation / AI providers supported by the translation feature. */
@Serializable
enum class TranslationProvider {
  /** Google Translate free public endpoint (no key). */
  GOOGLE,
  /** Bing / Microsoft Translator free public endpoint (no key). */
  BING,
  /** Baidu Translate API (requires AppID + secret key). */
  BAIDU,
  /** AI translation over the OpenAI Responses protocol (default provider: DeepSeek). */
  AI,
  /** User-defined OpenAI-compatible Responses endpoint. */
  CUSTOM;

  val displayName: String
    get() = when (this) {
      GOOGLE -> "Google (free)"
      BING -> "Bing (free)"
      BAIDU -> "Baidu (API key)"
      AI -> "AI (OpenAI Responses)"
      CUSTOM -> "Custom (OpenAI-compatible)"
    }
}

/** Rate-limiting parameters for automatic (and manual) translation requests. */
@Serializable
data class TranslationRateConfig(
  /** Minimum delay between two consecutive requests, in milliseconds. */
  val requestIntervalMs: Long = 600,
  /** Maximum number of requests that may be in flight at the same time. */
  val maxConcurrent: Int = 2,
  /** Maximum number of messages translated in one automatic batch. */
  val batchLimit: Int = 20,
) {
  fun normalized(): TranslationRateConfig = TranslationRateConfig(
    requestIntervalMs = requestIntervalMs.coerceIn(0, 60_000),
    maxConcurrent = maxConcurrent.coerceIn(1, 8),
    batchLimit = batchLimit.coerceIn(1, 100),
  )
}

/**
 * Translation configuration, serialized to JSON and stored in the local preferences.
 * Non-secret fields only; secrets live in [TranslationSecrets].
 */
@Serializable
data class TranslationConfig(
  val provider: TranslationProvider = TranslationProvider.GOOGLE,
  /** Default target language code, e.g. "en". */
  val targetLang: String = "en",
  val rate: TranslationRateConfig = TranslationRateConfig(),
  /** Baidu AppID (not secret, but provider-specific). */
  val baiduAppId: String = "",
  /** AI (Responses protocol) base URL, e.g. https://api.deepseek.com/v1 */
  val aiBaseUrl: String = "https://api.deepseek.com/v1",
  val aiModel: String = "deepseek-chat",
  /** Custom OpenAI-compatible base URL / model. */
  val customBaseUrl: String = "",
  val customModel: String = "",
)

/** Secret material, encrypted as a whole and stored in the local preferences. */
@Serializable
data class TranslationSecrets(
  val baiduKey: String = "",
  val aiApiKey: String = "",
  val customApiKey: String = "",
)

/** Status of a single translation entry (per chat item / per compose text). */
@Serializable
enum class TranslationStatus { IDLE, LOADING, DONE, ERROR }

/** Result of translating one piece of text. */
@Serializable
data class TranslationEntry(
  val status: TranslationStatus = TranslationStatus.IDLE,
  val translatedText: String? = null,
  val targetLang: String? = null,
  val detectedLang: String? = null,
  val error: String? = null,
) {
  val isLoading: Boolean get() = status == TranslationStatus.LOADING
  val isDone: Boolean get() = status == TranslationStatus.DONE && !translatedText.isNullOrBlank()
}

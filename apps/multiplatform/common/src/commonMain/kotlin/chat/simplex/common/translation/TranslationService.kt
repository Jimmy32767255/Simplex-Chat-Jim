package chat.simplex.common.translation

import androidx.compose.runtime.mutableStateMapOf
import chat.simplex.common.model.*
import chat.simplex.common.platform.Log
import chat.simplex.common.platform.cryptor
import chat.simplex.common.views.helpers.toBase64StringForPassphrase
import chat.simplex.common.views.helpers.toByteArrayFromBase64ForPassphrase
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Orchestrates translation: holds the observable per-chat-item result registry, dispatches requests
 * to the configured provider, and enforces the rate limit for automatic translation.
 *
 * This is a singleton object backed by Compose snapshot state so the UI recomposes when a translation
 * arrives. It is a purely client-side feature and never talks to anything but the user-configured
 * translation provider.
 */
object TranslationService {
  private const val TAG = "TranslationService"

  private val prefs: AppPreferences get() = ChatController.appPrefs

  /** Observable registry of translations keyed by chat item id. */
  private val results = mutableStateMapOf<Long, TranslationEntry>()

  private val rateMutex = Mutex()
  private var lastRequestAt: Long = 0

  /**
   * In-flight automatic-translation jobs keyed by chat item id. Tracked so a request can be cancelled
   * the moment its message scrolls out of the current viewport ("visible area only" contract). Only
   * touched from the main thread (Compose callers), so a plain map is enough.
   */
  private val autoJobs = mutableMapOf<Long, Job>()

  fun entry(itemId: Long): TranslationEntry? = results[itemId]

  fun clear(itemId: Long) { results.remove(itemId) }

  fun clearAll() { results.clear() }

  // region ---- configuration access ----

  fun config(): TranslationConfig = prefs.translationConfig.get()

  fun secrets(): TranslationSecrets = decodeSecrets(prefs.translationSecrets.get())

  fun saveConfig(c: TranslationConfig) {
    prefs.translationConfig.set(c)
  }

  fun saveSecrets(s: TranslationSecrets) {
    prefs.translationSecrets.set(encodeSecrets(s))
  }

  /** Whether the current provider already has everything it needs to translate. */
  fun isConfigured(c: TranslationConfig = config(), s: TranslationSecrets = secrets()): Boolean =
    when (c.provider) {
      TranslationProvider.GOOGLE -> true
      TranslationProvider.BING -> true
      TranslationProvider.BAIDU -> c.baiduAppId.isNotBlank() && s.baiduKey.isNotBlank()
      TranslationProvider.AI -> c.aiBaseUrl.isNotBlank() && c.aiModel.isNotBlank() && s.aiApiKey.isNotBlank()
    }

  // endregion

  // region ---- secrets encryption (best-effort; falls back to plaintext if cryptor unavailable) ----

  private const val SECRETS_ALIAS = "translationSecrets"

  private fun encodeSecrets(s: TranslationSecrets): String {
    val plain = jsonShort.encodeToString(s)
    return try {
      val (data, iv) = cryptor.encryptText(plain, SECRETS_ALIAS)
      val ivB64 = iv.toBase64StringForPassphrase()
      prefs.translationSecretsIv.set(ivB64)
      data.toBase64StringForPassphrase()
    } catch (e: Exception) {
      Log.e(TAG, "secrets encryption failed, storing plaintext: ${e.message}")
      prefs.translationSecretsIv.set(null)
      plain
    }
  }

  private fun decodeSecrets(stored: String?): TranslationSecrets {
    if (stored.isNullOrBlank()) return TranslationSecrets()
    val iv = prefs.translationSecretsIv.get()
    if (iv != null) {
      try {
        val plain = cryptor.decryptData(
          stored.toByteArrayFromBase64ForPassphrase(),
          iv.toByteArrayFromBase64ForPassphrase(),
          SECRETS_ALIAS,
        )
        if (plain != null) return jsonShort.decodeFromString<TranslationSecrets>(plain)
      } catch (e: Exception) {
        Log.e(TAG, "secrets decryption failed: ${e.message}")
      }
    }
    // Backward/fallback: maybe plaintext JSON
    return runCatching { jsonShort.decodeFromString<TranslationSecrets>(stored) }.getOrElse { TranslationSecrets() }
  }

  // endregion

  // region ---- rate limiting ----

  private suspend fun awaitRateSlot(cfg: TranslationRateConfig) {
    rateMutex.withLock {
      val interval = cfg.normalized().requestIntervalMs
      if (interval > 0) {
        val now = Clock.System.now().toEpochMilliseconds()
        val wait = lastRequestAt + interval - now
        if (wait > 0) delay(wait)
        lastRequestAt = Clock.System.now().toEpochMilliseconds()
      }
    }
  }

  // endregion

  // region ---- public translation entry points ----

  /** Translate a chat item, storing the result in the observable registry under its id. */
  fun translateChatItem(itemId: Long, text: String, targetLang: String? = null, scope: CoroutineScope) {
    if (text.isBlank()) return
    val cfg = config()
    val target = targetLang ?: cfg.targetLang
    results[itemId] = TranslationEntry(TranslationStatus.LOADING, targetLang = target)
    scope.launch {
      val entry = try {
        translateNow(text, target, cfg, secrets())
      } catch (e: Exception) {
        TranslationEntry(TranslationStatus.ERROR, error = e.message ?: e.toString(), targetLang = target)
      }
      results[itemId] = entry
    }
  }

  /** Suspend translation used by the compose translation key (returns the entry directly). */
  suspend fun translateTextNow(text: String, targetLang: String? = null): TranslationEntry {
    if (text.isBlank()) return TranslationEntry(TranslationStatus.ERROR, error = "empty text")
    val cfg = config()
    val target = targetLang ?: cfg.targetLang
    return translateNow(text, target, cfg, secrets())
  }

  /** Sequentially translate the currently visible messages with rate limiting (automatic mode). */
  fun autoTranslateVisible(items: List<Pair<Long, String>>, scope: CoroutineScope, targetLang: String? = null) {
    val cfg = config()
    val rate = cfg.rate.normalized()
    val target = targetLang ?: cfg.targetLang
    if (!isConfigured(cfg, secrets())) return
    val queue = items
      .filter { (id, text) -> text.isNotBlank() && results[id]?.isDone != true && results[id]?.isLoading != true && autoJobs[id]?.isActive != true }
      .filter { (_, text) -> !isLikelyAlreadyInLanguage(text, target) }
      .take(rate.batchLimit)
    if (queue.isEmpty()) return
    val visibleIds = items.map { it.first }.toSet()
    // A message that already scrolled away must not keep an in-flight automatic request alive.
    cancelAutomaticOutside(visibleIds)
    val semaphore = Semaphore(rate.maxConcurrent)
    for ((id, text) in queue) {
      autoJobs[id] = scope.launch {
        try {
          semaphore.withPermit {
            awaitRateSlot(rate)
            // Re-check visibility right before spending a request: the viewport may have moved while
            // this coroutine was queued behind the semaphore or the rate limiter.
            if (id !in currentVisibleIds) return@withPermit
            results[id] = TranslationEntry(TranslationStatus.LOADING, targetLang = target)
            val entry = try {
              translateNow(text, target, cfg, secrets())
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              TranslationEntry(TranslationStatus.ERROR, error = e.message ?: e.toString(), targetLang = target)
            }
            // If the item left the viewport while the request was in flight, drop the result.
            if (id in currentVisibleIds) results[id] = entry else results.remove(id)
          }
        } finally {
          if (autoJobs[id] === coroutineContext[Job]) autoJobs.remove(id)
        }
      }
    }
  }

  /** Ids currently considered visible by the latest automatic-translation pass. */
  private var currentVisibleIds: Set<Long> = emptySet()

  /** Cancel every in-flight automatic translation whose item is no longer visible. */
  fun cancelAutomaticOutside(visibleIds: Set<Long>) {
    currentVisibleIds = visibleIds
    val iterator = autoJobs.entries.iterator()
    while (iterator.hasNext()) {
      val (id, job) = iterator.next()
      if (id !in visibleIds) {
        job.cancel()
        iterator.remove()
        // Don't leave a permanent "translating" spinner for a message that is off-screen.
        if (results[id]?.isLoading == true) results.remove(id)
      }
    }
  }

  /** Cancel all in-flight automatic translations (e.g. when leaving the chat or turning the switch off). */
  fun cancelAllAutomatic() {
    currentVisibleIds = emptySet()
    autoJobs.values.forEach { it.cancel() }
    autoJobs.clear()
  }

  // endregion

  // region ---- provider dispatch ----

  private suspend fun translateNow(text: String, target: String, cfg: TranslationConfig, s: TranslationSecrets): TranslationEntry {
    return when (cfg.provider) {
      TranslationProvider.GOOGLE -> {
        val res = httpGetText(googleTranslateUrl(text, target))
        if (!res.success) TranslationEntry(TranslationStatus.ERROR, error = res.error ?: res.body)
        else parseGoogleResponse(res.body).copy(targetLang = target)
      }
      TranslationProvider.BING -> {
        val res = httpPostJson(
          bingTranslateUrl(target),
          mapOf("Content-Type" to "application/json"),
          bingTranslateBody(text),
        )
        if (!res.success) TranslationEntry(TranslationStatus.ERROR, error = res.error ?: res.body, targetLang = target)
        else parseBingResponse(res.body).copy(targetLang = target)
      }
      TranslationProvider.BAIDU -> {
        val salt = Clock.System.now().toEpochMilliseconds().toString()
        // Pass the script-detected source language instead of relying on Baidu's own detection.
        val url = baiduTranslateUrl(cfg.baiduAppId, text, salt, s.baiduKey, target, detectLanguageOrNull(text))
        val res = httpGetText(url)
        if (!res.success) TranslationEntry(TranslationStatus.ERROR, error = res.error ?: res.body, targetLang = target)
        else parseBaiduResponse(res.body).copy(targetLang = target)
      }
      TranslationProvider.AI -> {
        val url = aiResponsesUrl(cfg.aiBaseUrl)
        val res = httpPostJson(
          url,
          mapOf(
            "Authorization" to "Bearer ${s.aiApiKey}",
            "Content-Type" to "application/json",
          ),
          aiResponsesBody(cfg.aiModel, text, target),
        )
        if (!res.success) TranslationEntry(TranslationStatus.ERROR, error = res.error ?: res.body, targetLang = target)
        else parseAiResponsesResponse(res.body).copy(targetLang = target)
      }
    }
  }

  // endregion
}

/**
 * Decide whether the compose translation key can undo a previous translation back to the original.
 *
 * Undo is possible only when:
 *  - a translation was just inserted into the compose box (`translateResult != null`), AND
 *  - the user has not edited the text since (current text still equals the translated text).
 * Returns the original text to restore, or null if undo is not available.
 */
internal fun canUndoTranslation(translateOriginal: String?, translateResult: String?, currentMessageText: String): String? {
  val original = translateOriginal ?: return null
  val result = translateResult ?: return null
  if (result.isBlank()) return null
  return if (currentMessageText == result) original else null
}

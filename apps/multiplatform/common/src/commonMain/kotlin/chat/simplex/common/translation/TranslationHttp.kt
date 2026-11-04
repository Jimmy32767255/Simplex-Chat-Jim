package chat.simplex.common.translation

/**
 * Minimal cross-platform HTTP surface used by the translation service.
 *
 * `desktopMain` implements it with OkHttp (already a dependency in the desktop source set);
 * `androidMain` implements it with `java.net.HttpURLConnection` (no new dependency). Both are
 * `suspend` and run off the main thread.
 */

data class HttpResult(
  val success: Boolean,
  val body: String,
  val error: String? = null,
) {
  companion object {
    fun ok(body: String) = HttpResult(true, body)
    fun fail(error: String, body: String = "") = HttpResult(false, body, error)
  }
}

expect suspend fun httpGetText(url: String, headers: Map<String, String> = emptyMap()): HttpResult

expect suspend fun httpPostJson(url: String, headers: Map<String, String>, body: String): HttpResult

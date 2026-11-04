package chat.simplex.common.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

actual suspend fun httpGetText(url: String, headers: Map<String, String>): HttpResult = withContext(Dispatchers.IO) {
  request("GET", url, headers, null)
}

actual suspend fun httpPostJson(url: String, headers: Map<String, String>, body: String): HttpResult = withContext(Dispatchers.IO) {
  request("POST", url, headers, body.toByteArray(Charsets.UTF_8))
}

private fun request(method: String, url: String, headers: Map<String, String>, body: ByteArray?): HttpResult {
  var conn: HttpURLConnection? = null
  return try {
    conn = (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = method
      connectTimeout = 20_000
      readTimeout = 30_000
      instanceFollowRedirects = true
      headers.forEach { (k, v) -> setRequestProperty(k, v) }
      if (body != null) {
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        outputStream.use { it.write(body) }
      }
    }
    val code = conn.responseCode
    val stream: InputStream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
    val text = stream.use { it.readBytesToString() }
    if (code in 200..299) HttpResult.ok(text) else HttpResult.fail("HTTP $code", text)
  } catch (e: Exception) {
    HttpResult.fail(e.message ?: e.toString())
  } finally {
    conn?.disconnect()
  }
}

private fun InputStream.readBytesToString(): String {
  val out = ByteArrayOutputStream()
  val buf = ByteArray(8192)
  while (true) {
    val n = read(buf)
    if (n < 0) break
    out.write(buf, 0, n)
  }
  return out.toString("UTF-8")
}

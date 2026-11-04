package chat.simplex.common.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val translationHttpClient: OkHttpClient by lazy {
  OkHttpClient.Builder()
    .followRedirects(true)
    .followSslRedirects(true)
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()
}

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

actual suspend fun httpGetText(url: String, headers: Map<String, String>): HttpResult = withContext(Dispatchers.IO) {
  try {
    val builder = Request.Builder().url(url).get()
    headers.forEach { (k, v) -> builder.header(k, v) }
    translationHttpClient.newCall(builder.build()).execute().use { resp ->
      val body = resp.body?.string() ?: ""
      if (resp.isSuccessful) HttpResult.ok(body)
      else HttpResult.fail("HTTP ${resp.code}", body)
    }
  } catch (e: Exception) {
    HttpResult.fail(e.message ?: e.toString())
  }
}

actual suspend fun httpPostJson(url: String, headers: Map<String, String>, body: String): HttpResult = withContext(Dispatchers.IO) {
  try {
    val builder = Request.Builder().url(url).post(body.toRequestBody(JSON_MEDIA))
    headers.forEach { (k, v) -> builder.header(k, v) }
    translationHttpClient.newCall(builder.build()).execute().use { resp ->
      val respBody = resp.body?.string() ?: ""
      if (resp.isSuccessful) HttpResult.ok(respBody)
      else HttpResult.fail("HTTP ${resp.code}", respBody)
    }
  } catch (e: Exception) {
    HttpResult.fail(e.message ?: e.toString())
  }
}

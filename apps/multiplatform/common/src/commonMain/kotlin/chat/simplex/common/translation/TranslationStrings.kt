package chat.simplex.common.translation

import chat.simplex.common.model.ChatController

/**
 * Localized strings for the in-app translation feature.
 *
 * They are deliberately kept out of the shared `MR.strings` resources: the moko-resources plugin
 * generates one giant `MR$strings.<clinit>` method holding every string, and at ~3000 strings it is
 * already close to the JVM 64 KiB per-method limit — adding more there breaks the whole build with
 * "Method too large". Keeping the (few dozen) translation strings here avoids that and keeps the
 * feature self-contained. Language is inferred from the app's interface language preference.
 */
object TranslationStrings {
  private fun isZh(): Boolean {
    val lang = runCatching { ChatController.appPrefs.appLanguage.get() }.getOrNull()
    return lang?.startsWith("zh") == true
  }

  fun t(en: String, zh: String): String = if (isZh()) zh else en

  // Generic
  val translateVerb get() = t("Translate", "翻译")
  val translating get() = t("Translating…", "正在翻译…")
  val translationFailed get() = t("Translation failed", "翻译失败")
  val retry get() = t("Retry", "重试")
  val hideTranslation get() = t("Hide translation", "隐藏译文")
  val showOriginal get() = t("Show original", "显示原文")
  val undoTranslation get() = t("Undo translation", "撤销翻译")
  val chooseLanguage get() = t("Translate to…", "翻译为…")
  val original get() = t("Original", "原文")
  val notConfigured get() = t("Translation is not configured. Set it up in Settings.", "翻译尚未配置，请在设置中配置。")
  val apiKeyRequired get() = t("API key required", "需要 API 密钥")

  // Settings screen
  val settingsSummary get() = t("Provider, API keys, languages and automatic translation", "服务提供商、API 密钥、语言与自动翻译")
  val provider get() = t("Service provider", "服务提供商")
  val targetLanguage get() = t("Translate to", "翻译为")
  val autoTranslate get() = t("Auto-translate messages in view", "自动翻译当前视图中的消息")
  val autoTranslateFooter get() = t(
    "When on, messages in the current view are translated automatically. Requests are rate-limited to avoid bans or draining your balance.",
    "开启后，当前视图中的消息会被自动翻译。请求已限速，以避免被封禁或耗尽余额。",
  )
  val rateLimits get() = t("Rate limits", "速率限制")
  val requestInterval get() = t("Interval between requests (ms)", "请求间隔（毫秒）")
  val maxConcurrent get() = t("Max concurrent requests", "最大并发请求数")
  val batchLimit get() = t("Max messages per batch", "每批最多消息数")
  val baiduAppId get() = t("Baidu App ID", "百度 App ID")
  val baiduKey get() = t("Baidu secret key", "百度密钥")
  val aiBaseUrl get() = t("API base URL", "API 地址")
  val aiModel get() = t("Model", "模型")
  val aiApiKey get() = t("API key", "API 密钥")
  val customBaseUrl get() = t("Custom API base URL", "自定义 API 地址")
  val customModel get() = t("Custom model", "自定义模型")
  val customApiKey get() = t("Custom API key", "自定义 API 密钥")
  val providerSetup get() = t("Configure the fields below for the selected provider.", "请为所选提供商配置下方字段。")
  val googleFreeNote get() = t("Google Translate (free) uses a public endpoint and needs no key.", "谷歌翻译（免费）使用公开接口，无需密钥。")
  val bingFreeNote get() = t("Bing Translator (free) uses a public endpoint and needs no key.", "必应翻译（免费）使用公开接口，无需密钥。")
  val deepseekDefault get() = t("Default endpoint is DeepSeek (OpenAI Responses protocol).", "默认端点为 DeepSeek（OpenAI Responses 协议）。")
  val detectLanguageAuto get() = t("Detect language", "自动识别语言")
}

# Chat Translation Specification

> In-app chat translation, implemented entirely in the Kotlin client (no Haskell core / protocol change).
>
> **Related product:** [product/views/chat.md](../../product/views/chat.md) (message menu, compose bar),
> [product/views/settings.md](../../product/views/settings.md) (translation settings page),
> [product/rules.md](../../product/rules.md) (RULE-31) · **Concept:** PC46 · **State:** [spec/state.md](../state.md)

## Table of Contents

1. [Overview](#1-overview)
2. [Providers](#2-providers)
3. [Configuration and Secrets](#3-configuration-and-secrets)
4. [TranslationService](#4-translationservice)
5. [Provider Requests and Responses](#5-provider-requests-and-responses)
6. [HTTP Abstraction](#6-http-abstraction)
7. [Language Detection](#7-language-detection)
8. [Message-Menu Translation](#8-message-menu-translation)
9. [Compose-Bar Translation](#9-compose-bar-translation)
10. [Automatic Translation](#10-automatic-translation)
11. [Rate Limiting](#11-rate-limiting)
12. [Source Files](#12-source-files)

## 1. Overview

Translation turns a message (or the text being composed) into another language by calling an external
translation provider. It is a **pure-client** feature: it lives in
`common/src/commonMain/kotlin/chat/simplex/common/translation/`, never sends anything to the SimpleX core
and adds no protocol message. The translated text is shown as a two-line bubble below the original; the
`ChatItem` content is never rewritten (RULE-31 invariants 1 and 4).

Three entry points share one service:

| Entry point | Trigger | Result |
|---|---|---|
| Message menu | Long-press / right-click a message bubble → **Translate** | Auto-detect source language, append the translation under the bubble |
| Compose button | `ic_translate` button left of the send button (tap / long-press) | Translate the compose text; tap = default target language, long-press = pick one for this message |
| Automatic | `translationAutoTranslate` on | Translate the messages currently visible in the viewport, rate-limited and cancelled on scroll-out |

## 2. Providers

`TranslationProvider` (`TranslationModels.kt`) is a pluggable enum dispatched by `TranslationService`:

| Provider | Cost | Auth | Notes |
|---|---|---|---|
| `GOOGLE` | free | none | Default provider |
| `BING` | free | none (short-lived bearer token) | Free edge endpoint |
| `BAIDU` | free tier | AppID + secret key | Needs the Baidu AppID + secret key |
| `AI` | paid | API Key | OpenAI **Responses** protocol; defaults to DeepSeek (`https://api.deepseek.com/v1`, model `deepseek-chat`) |
| `CUSTOM` | user's | API Key | OpenAI-compatible endpoint with a user-supplied base URL / model / key (defaults empty) |

Adding a provider is a client-only change: describe its request/response in `TranslationApi` and (if it
needs a non-trivial HTTP shape) its call in `TranslationHttp`. The core is never touched.

## 3. Configuration and Secrets

All configuration lives in `AppPreferences` and NEVER enters the `AppSettings` export (RULE-31 invariants 2
and 3). See [spec/state.md](../state.md) §5 "Chat Translation (client-only)".

| Key | Type | Contents |
|---|---|---|
| `translationConfig` | `SharedPreference<TranslationConfig>` (stored as a JSON string) | `provider`, `targetLang`, `rate` (`requestIntervalMs` 600, `maxConcurrent` 2, `batchLimit` 20), plus the non-secret provider fields `baiduAppId`, `aiBaseUrl`/`aiModel`, `customBaseUrl`/`customModel` |
| `translationSecrets` | `SharedPreference<String?>` | The whole `TranslationSecrets` JSON (`baiduKey`, `aiApiKey`, `customApiKey`), encrypted with `Cryptor.encryptText`; falls back to plaintext only if the platform cryptor is unavailable |
| `translationSecretsIv` | `SharedPreference<String?>` | Base64 IV used to encrypt `translationSecrets` |
| `translationAutoTranslate` | `SharedPreference<Boolean>` | Viewport auto-translation switch (default `false`) |

The `SHARED_PREFS_TRANSLATION_*` keys are `"TranslationConfig"`, `"TranslationSecrets"`,
`"TranslationSecretsIv"` and `"TranslationAutoTranslate"`. Keys are encrypted with `Cryptor.encryptText`
before they are written; the plaintext exists only while a request is built. Because neither the config
nor the secrets are in `AppSettings`, a device migration or backup never carries them.

## 4. TranslationService

`TranslationService` (`TranslationService.kt`) is a Compose-observable `object` that owns an observable
registry `results = mutableStateMapOf<Long, TranslationEntry>()` (keyed by chat item id) and exposes:

- **`translateChatItem(itemId: Long, text: String, targetLang: String? = null, scope: CoroutineScope)`** —
  the message-menu entry point. It immediately stores a `LOADING` `TranslationEntry` under `itemId`, then
  launches `translateNow(...)` and replaces the entry with the result (`DONE` or `ERROR`). `targetLang`
  defaults to `config().targetLang`.
- **`suspend fun translateTextNow(text: String, targetLang: String? = null): TranslationEntry`** — the
  compose-key entry point; returns the `TranslationEntry` directly instead of storing it.
- **`autoTranslateVisible(items: List<Pair<Long, String>>, scope: CoroutineScope, targetLang: String? = null)`** —
  the automatic mode. When the provider is configured it filters out blanks, already-`DONE`/`LOADING`
  items and text already in the target language (`isLikelyAlreadyInLanguage`), takes at most `batchLimit`
  items, and runs them concurrently under a `Semaphore(maxConcurrent)` with `awaitRateSlot(rate)` spacing.
- **`canUndoTranslation(translateOriginal: String?, translateResult: String?, currentMessageText: String): String?`**
  (internal) — returns the `translateOriginal` text to restore when undo is valid (`translateResult` is
  non-blank and the current text still equals it), otherwise `null`.
- **Configuration access** — `config()`, `secrets()` (decrypts `translationSecrets` with `translationSecretsIv`
  via `cryptor.decryptData`), `saveConfig(...)`, `saveSecrets(...)` (encrypts via `cryptor.encryptText`,
  falling back to plaintext if the platform cryptor is unavailable), `isConfigured(...)`, plus
  `entry(itemId)` / `clear(itemId)` / `clearAll()` for the registry.

The provider dispatch happens in a private `translateNow(text, target, cfg, s)` that branches on
`cfg.provider` and calls the matching `TranslationApi` builder plus `TranslationHttp`.

## 5. Provider Requests and Responses

`TranslationApi.kt` holds pure functions that build each provider's request body and parse its response.
Keeping them pure lets them be unit-tested without network access.

- **Google** (`googleTranslateUrl` / `parseGoogleResponse`) — GET `translate_a/single?...&sl=auto&tl=<target>&q=<text>`,
  concatenate the `[0][*][0]` segments and read the detected language from index `[2]`.
- **Bing** (`bingAuthUrl` / `bingTranslateUrl` / `bingTranslateBody` / `parseBingResponse`) — GET a
  short-lived bearer token from `edge.microsoft.com/translate/auth`, then POST the text array to
  `api-edge.cognitive.microsofttranslator.com/translate`; parse `[0].translations[0].text` (and
  `detectedLanguage.language`).
- **Baidu** (`baiduSign` / `baiduTranslateUrl` / `baiduLangCode` / `parseBaiduResponse`) — sign with
  `md5Hex(appId + q + salt + key)`, map the language to Baidu's code and parse `trans_result[].dst`.
- **AI / Custom** (`aiResponsesUrl` / `aiSystemPrompt` / `aiResponsesBody` / `parseAiResponsesResponse`) —
  OpenAI **Responses** protocol: POST `{baseUrl}/responses` (the `/responses` suffix is appended unless
  already present) with `model` and a system instruction to translate into the target language; parse
  `output_text` or `output[].content[].text`, with chat-completions fallbacks.

## 6. HTTP Abstraction

`TranslationHttp.kt` declares a small result type `HttpResult(success, body, error)` and two `expect`
functions:

```kotlin
expect suspend fun httpGetText(url: String, headers: Map<String, String> = emptyMap()): HttpResult
expect suspend fun httpPostJson(url: String, headers: Map<String, String>, body: String): HttpResult
```

Platform actuals live in `common/src/androidMain/.../translation/TranslationHttp.android.kt` and
`common/src/desktopMain/.../translation/TranslationHttp.desktop.kt`. Keeping HTTP behind an `expect`
interface keeps `TranslationService`/`TranslationApi` testable and lets each platform use its natural HTTP
stack.

## 7. Language Detection

`TranslationLang.kt` provides the supported language table and the pure `detectLanguage` function used by
the message-menu flow when the source language is not known (translate as `auto` where the provider
supports it). The target language comes from `translationConfig.targetLang` (default English) or, for a
long-press compose translation, from the language chosen in the menu for that one message.

## 8. Message-Menu Translation

Long-press / right-click a message bubble → the context menu (`ChatItemView`) shows a **Translate** entry
with the `ic_translate` icon. Selecting it:

1. reads the message text from `ChatItem.content.text`,
2. calls `TranslationService.translateChatItem(itemId, text)` (the source language is detected and the
   target comes from `translationConfig.targetLang`),
3. appends the result as a bilingual line (原文 + 译文) rendered inside `FramedItemView` under the bubble.

The original message is untouched — this is a display-only decoration of the item, not an edit.

## 9. Compose-Bar Translation

The `ic_translate` button (36 dp, circular) sits left of the send button in `SendMsgView`. See
[compose.md](compose.md) §9 for the button behaviour. In short: tap translates to the default target
language, long-press opens a language menu for a one-off target; the original text is kept in
`ComposeState.translateOriginal` and the translation in `translateResult`, and a second tap restores the
original only while `canUndoTranslation` holds. Editing the text after translating disables undo and the
tap re-translates.

## 10. Automatic Translation

When `translationAutoTranslate` is on, the chat view collects the items in `listState.layoutInfo.visibleItemsInfo`
and hands them to the auto-translate batch entry. Only visible messages are translated; a message that
leaves the viewport has its pending request cancelled. This keeps provider load bounded and matches the
"visible area only" contract (RULE-31 invariant 5).

## 11. Rate Limiting

`TranslationRateConfig` (inside `translationConfig`) tunes the rate limiter (`awaitRateSlot` spacing +
`Semaphore(maxConcurrent)`), shared by automatic and manual translation:

| Parameter | Default | Meaning |
|---|---|---|
| `requestIntervalMs` | 600 | Minimum spacing between requests |
| `maxConcurrent` | 2 | Maximum requests in flight |
| `batchLimit` | 20 | Maximum visible messages handled per batch |

Both automatic and manual translation share the limiter, so neither can flood a provider, get the client
banned, or drain a paid balance.

## 12. Source Files

| File | Description |
|---|---|
| `translation/TranslationService.kt` | Compose-observable registry: `translateChatItem`, `translateTextNow`, `autoTranslateVisible`, `canUndoTranslation`, config/secrets access, rate limiting |
| `translation/TranslationModels.kt` | `TranslationProvider`, `TranslationConfig`, `TranslationSecrets`, `TranslationRateConfig`, `TranslationEntry`/status |
| `translation/TranslationLang.kt` | Language table and `detectLanguage` |
| `translation/TranslationApi.kt` | Per-provider request-body builders and response parsers (pure, incl. Baidu MD5 signing) |
| `translation/TranslationHttp.kt` | `HttpResult` + `expect suspend httpGetText/httpPostJson` (+ Android/desktop actuals) |
| `views/chat/SendMsgView.kt` | Compose translate button placement |
| `views/chat/ComposeView.kt` | `ComposeState.translateOriginal` / `translateResult` |
| `views/chat/item/ChatItemView.kt` | Message-menu Translate action |
| `views/chat/item/FramedItemView.kt` | Bilingual translation line under a translated bubble |
| `views/usersettings/TranslationSettingsView.kt` | Chat-translation settings page |
| `model/SimpleXAPI.kt` | `AppPreferences.translationConfig` / `translationSecrets` / `translationSecretsIv` / `translationAutoTranslate` |

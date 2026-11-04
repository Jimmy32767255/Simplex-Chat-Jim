# Fix the in-app translation feature: localized copy, working Bing/Baidu, no "Custom" provider

Date: 2026-11-04 · Baseline: SimpleX Chat 7.0.4 · Scope: `apps/multiplatform` (client only)

## 1. Problem statement

The in-app chat translation feature (commit `0fcdccd14`) shipped with three defects:

1. **Copy is not localized.** Every label of the feature comes from `TranslationStrings`, which decides the
   language with `appLanguage.get()?.startsWith("zh")`. The preference is `null` by default and the literal
   `"system"` when the user follows the system language, so a Chinese system showed English copy even though
   the rest of the UI was Chinese.
2. **Every provider except AI fails.** Bing called `edge.microsoft.com/translate/auth` for a bearer token and
   then posted to `api-edge.cognitive.microsofttranslator.com/translate`; both were retired in 2026 (the auth
   endpoint answers 404), so the provider could never succeed. Baidu always sent `from=auto`, delegating
   detection to Baidu, which mislabels some inputs and then returns the source text unchanged — the
   translation looked like a no-op. Google's public endpoint is unreachable from mainland China, but that is
   a network restriction rather than a defect and is out of scope.
3. **A redundant provider.** `TranslationProvider.CUSTOM` duplicated `AI`: same Responses-protocol body and
   parser, only a second set of base-URL/model/key fields.

## 2. Solution summary

- Add `expect val systemLanguageCode` (desktop/Android: `Locale.getDefault().language`) and let
  `TranslationStrings` fall back to it when the preference is unset or `"system"`.
- Rewrite the Bing request for the current free Edge endpoint (no token, bare JSON array body, script-qualified
  Chinese codes) and remove the token flow.
- Send Baidu an explicit `from` whenever the script identifies the source language, keep `auto` for Latin
  text, and re-join multi-line `trans_result` entries with newlines.
- Delete `TranslationProvider.CUSTOM` and its config/secrets/UI/strings.

Full 27-language coverage is deliberately **not** attempted: moko-resources 0.23.0 supports only its fixed
resource types, so no extra `translation.xml` is generated, and `MR$strings.<clinit>` already sits at offset
~65 390 of the JVM's 65 535-byte method limit (measured on desktop), which leaves room for roughly six more
strings. This is recorded as GAP-08 in `product/gaps.md`.

## 3. Detailed technical design

### 3.1 Locale resolution

```kotlin
// commonMain/.../platform/AppCommon.kt
expect val systemLanguageCode: String          // desktop/android actual: Locale.getDefault().language
```

`TranslationStrings.effectiveLanguage()` returns the preference when it is set and not `"system"`, otherwise
`systemLanguageCode`; `isZh()` tests that value. On desktop `applyAppLocale()` has already called
`Locale.setDefault(pref)`, so both paths agree.

### 3.2 Bing

| Item | Before | After |
|---|---|---|
| Token | `GET edge.microsoft.com/translate/auth` | none |
| Endpoint | `api-edge.cognitive.microsofttranslator.com/translate?api-version=3.0&from=&to=` | `edge.microsoft.com/translate/translatetext?from=&to=` |
| Body | `[{"Text":"..."}]` | `["..."]` |
| Language code | raw code | `msLangCode()`: `zh → zh-Hans`, `zh-TW/zh-Hant → zh-Hant` |

`parseBingResponse` is unchanged — the new endpoint returns the same array of
`{detectedLanguage, translations[{text}]}` objects.

### 3.3 Baidu

`detectLanguageOrNull(text)` returns the same script verdict as `detectLanguage` but `null` for Latin input;
`detectLanguage` is now `detectLanguageOrNull(text) ?: "en"`, so the skip check (`isLikelyAlreadyInLanguage`)
keeps its old behaviour. `baiduTranslateUrl(..., sourceLang: String?)` maps that value through
`baiduLangCode()` into `from`, falling back to `auto`. `parseBaiduResponse` joins one `dst` per line with
`\n` and reports `ERROR` when the array is empty.

### 3.4 Provider removal

Removed: the `CUSTOM` enum constant and its `displayName`, `TranslationConfig.customBaseUrl/customModel`,
`TranslationSecrets.customApiKey`, the `isConfigured`/`translateNow` branches, the settings-page field group
and three `TranslationStrings` entries. Stored configs still parse because `jsonShort` uses
`ignoreUnknownKeys = true` (`SimpleXAPI.kt`), so no migration is needed.

## 4. Implementation steps

1. `platform/AppCommon.kt` + both actuals: `systemLanguageCode`.
2. `TranslationStrings.kt`: `effectiveLanguage()` / `isZh()`.
3. `TranslationApi.kt`: new Bing URL/body/`msLangCode`, drop `bingAuthUrl`, Baidu `sourceLang` parameter and
   multi-line join.
4. `TranslationLang.kt`: `detectLanguageOrNull`, `detectLanguage` delegating to it.
5. `TranslationService.kt`: single-request Bing branch, Baidu `detectLanguageOrNull(text)`, delete CUSTOM.
6. `TranslationModels.kt`, `TranslationSettingsView.kt`: delete CUSTOM and its fields.
7. `TranslationApiTest.kt`: update the Bing body/URL expectations, add `msLangCode`, Baidu `from`, multi-line,
   empty-result and `detectLanguageOrNull` cases (22 → 28).
8. Docs: `spec/client/translation.md`, `spec/state.md`, `spec/architecture.md`, `product/views/settings.md`,
   `product/rules.md`, `product/gaps.md`, `TODO.md`.

## 5. Verification

- `./gradlew :common:compileKotlinDesktop` — success.
- `./gradlew :common:compileDebugKotlinAndroid` — success.
- `./gradlew :common:desktopTest --tests "chat.simplex.app.TranslationApiTest"` — 28/28 pass.
- Manual network probes (this host, mainland China): new Bing endpoint answers 200 for `en → zh-Hans`,
  `en → zh-Hant` and `zh-Hans → en`; `edge.microsoft.com/translate/auth` answers 404; Google hosts and
  MyMemory time out without a proxy.

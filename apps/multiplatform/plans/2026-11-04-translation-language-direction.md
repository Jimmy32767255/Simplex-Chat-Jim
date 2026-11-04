# Split the translation target by direction: received messages follow the interface language

Date: 2026-11-04 · Baseline: SimpleX Chat 7.0.4 · Scope: `apps/multiplatform` (client only)

## 1. Problem statement

The single `translationConfig.targetLang` setting (default `"en"`) was used for both directions:

- text the user **receives** (`translateChatItem`, `autoTranslateVisible`), and
- text the user **writes** (the compose translate key, `translateTextNow`).

With `targetLang = "en"` and English source text, a provider is asked to translate English into English and
legitimately returns the input unchanged, so the feature looks like "it only shows the original text". The
setting also reads like a global "translate to" switch, which makes users think it controls received messages.

## 2. Solution summary

- Received messages are translated into the **interface language**; the setting keeps its value but now only
  describes the compose translation key.
- Add a pure `translationLangForUiLanguage(uiLanguage: String?): String` that maps appearance-language codes
  onto `translationLanguages`: `zh-CN`/`zh-TW`/`zh-HK`/`zh-SG`/`zh-Hant` → `zh`, `pt-BR`/`pt-PT` → `pt`,
  `in` → `id`, `iw` → `he`, other region suffixes stripped (`en-GB` → `en`, `de_DE` → `de`), and interface
  languages without a translation language (`bg`, `ca`, `cs`, `fa`, `fi`, `hu`, `lt`, `ro`) fall back to `en`.
- `TranslationService.incomingTargetLang()` combines that with `TranslationStrings.effectiveLanguage()` (which
  already handles the `null`/`"system"` preference) and becomes the default target of `translateChatItem`
  and `autoTranslateVisible`. `translateTextNow` keeps using `config().targetLang`.
- Rename the setting label to "Translate typed text to" / "输入框内容翻译为" and add a footer explaining that
  received messages always follow the interface language.

## 3. Detailed technical design

### 3.1 Resolution chain

```
Appearance language preference ("zh-CN", "system", null)
  → TranslationStrings.effectiveLanguage()   // "system"/null → platform locale
  → translationLangForUiLanguage(...)        // alias map, then base code, then "en"
  → TranslationService.incomingTargetLang()
```

No new preference field is written, so existing configs keep parsing unchanged; only the meaning of
`targetLang` narrows, and it stays serialized as before.

### 3.2 Why not ask the provider to detect the source

Choices made earlier stay in place: Baidu receives an explicit `from` when `detectLanguageOrNull` recognizes
the script, and the provider's own detection is used for Latin text. Received-message targets are now known
explicitly, so no provider has to guess the target anymore.

## 4. Implementation steps

1. `TranslationLang.kt`: add `translationLangForUiLanguage` + `uiAliases`.
2. `TranslationStrings.kt`: make `effectiveLanguage()` internal, replace `targetLanguage` with
   `composeTargetLanguage`, add `incomingFollowsInterface(languageName)`.
3. `TranslationService.kt`: add `incomingTargetLang()`, use it in `translateChatItem`/`autoTranslateVisible`.
4. `TranslationSettingsView.kt`: new title + footer note.
5. `TranslationModels.kt`: document that `targetLang` is the compose target.
6. Tests, then `spec/client/translation.md`, `spec/state.md`, `product/views/settings.md`, `TODO.md`.

## 5. Verification

- `./gradlew :common:desktopTest --tests "chat.simplex.app.TranslationApiTest"` → 29/29 pass, including the new
  `testTranslationLangForUiLanguage` (null/blank/`system`/case/region/underscore/`zh-*`/`pt-BR`/`in`/`iw`/
  unsupported-language fallback).
- `:common:compileKotlinDesktop` and `:common:compileDebugKotlinAndroid` build successfully.
- Manual check of the packaged desktop app: with a Chinese interface, received English messages are requested
  with `to=zh` (Bing `to=zh-Hans`), while the compose key still uses `targetLang`.

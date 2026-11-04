# In-app chat translation (feature of the 2026-11-04 request)

## Problem

SimpleX users routinely chat across languages but have no way to read or write another language without
leaving the app: copying a message into an external translator loses the conversation context and leaks
message text to a third party through the clipboard. Conversely, when composing in a second language the
user wants a quick check before sending. The request was an in-app translation feature: translate any
message from its menu (non-destructively), translate the composed text from a button next to the send
button, choose a translation service in Settings, and optionally auto-translate the messages on screen —
all without changing the SimpleX core or protocol.

## Solution

A new pure-client `translation` package plus thin UI hooks:

- **`translation/` package** (new): `TranslationModels.kt` (`TranslationProvider` `GOOGLE`/`BING`/`BAIDU`/
  `AI`/`CUSTOM`, `TranslationConfig`, `TranslationSecrets`, `TranslationRateConfig`, `TranslationEntry`
  and status), `TranslationLang.kt` (language table + `detectLanguage`), `TranslationApi.kt` (per-provider
  request-body builders and response parsers, including Baidu MD5 signing), `TranslationHttp.kt`
  (`HttpResult` + `expect suspend httpGetText/httpPostJson` + desktop/android actuals), `TranslationService.kt`
  (Compose-observable registry, `translateChatItem`/`translateTextNow`/`autoTranslateVisible`, rate limiting,
  `canUndoTranslation`).
- **`SimpleXAPI.kt`**: four new `AppPreferences` values — `translationConfig` (JSON string),
  `translationSecrets`, `translationSecretsIv` (encrypted blob + IV) and `translationAutoTranslate`
  (Boolean) — plus their `SHARED_PREFS_*` keys. None is added to `AppSettings`.
- **`ComposeView.kt`**: two new `ComposeState` fields, `translateOriginal` and `translateResult`.
- **Chat UI**: a **Translate** context-menu item in `ChatItemView`, the bilingual line rendered in
  `FramedItemView`, the `ic_translate` button left of the send button in `SendMsgView`, and the viewport
  auto-translation in `ChatView`.
- **Settings UI**: `TranslationSettingsView` (new) reached from `SettingsView`.
- **Strings**: provider/language/rate-limit labels and menu strings in `MR/base/strings.xml` and
  `MR/zh-rCN/strings.xml`.

## Design decisions

- **Pure client, no core.** Translation is an outbound HTTP call from the app; it never enters the SimpleX
  message flow. So the Haskell core and the protocol are untouched and the feature stays easy to rebase
  onto a newer official version (matches the repo's portability rule). A provider is added purely by
  extending `TranslationApi`.
- **Pluggable provider enum.** `TranslationProvider` is a sealed-ish enum dispatched through one
  `translateNow`, so the UI never branches on provider. Google/Bing are free and need no key; Baidu needs
  credentials; `AI` is OpenAI **Response** protocol defaulting to DeepSeek; `CUSTOM` is an
  OpenAI-compatible endpoint with empty defaults the user fills in.
- **Secrets encrypted, config local.** Keys are encrypted with `Cryptor` and stored as
  `translationSecrets` + `translationSecretsIv`; the rest of the config is a JSON string in
  `translationConfig`. Neither goes into `AppSettings`, so a backup/migration never carries credentials or
  service choice (RULE-31 invariants 2–3).
- **Non-destructive message translation.** The message-menu path appends a bilingual line under the bubble
  and never rewrites `ChatItem` content. This avoids fighting the core's edit pipeline and keeps the
  original text intact for the user and for future edits.
- **Undo via ComposeState, not a stack.** `translateOriginal` + `translateResult` make undo a simple
  comparison: undo is allowed only while the field still equals `translateResult`. Editing the text breaks
  that equality, so the button gracefully degrades to "translate again" instead of restoring stale text.
- **Viewport-scoped, rate-limited auto-translation.** Only visible messages are translated, and a message
  that scrolls out has its request cancelled. A single limiter (`awaitRateSlot` spacing + a
  `Semaphore(maxConcurrent)`, tuned by `requestIntervalMs` 600,
  `maxConcurrent` 2, `batchLimit` 20) covers both automatic and manual translation, so the client cannot
  flood a provider, get banned, or burn a paid balance.
- **HTTP behind `expect`/`actual`.** `TranslationHttp` keeps `TranslationService`/`TranslationApi` testable
  with injected/faked HTTP and lets each platform use its own client.

## Rejected alternatives

- Translating by rewriting the message with `APIUpdateChatItem`: it would mutate the user's message and
  collide with the edit window / feature negotiation, and the original text would be lost.
- Folding translation into the Haskell core: it is a client-only outbound call, and a core change cannot be
  built or tested here (no cabal/stack/ghcup) and would need a protocol change — against the portability
  rule.
- A global "translate everything" switch with no viewport scope: it would hammer providers and cost money
  on long histories; the visible-area contract keeps load bounded.
- Storing the undo history as a stack of strings: unnecessary; the two-field comparison already gives the
  exact semantics the request asked for ("editing after translating disables undo").

## Steps

1. `translation/` package: models, language table, request/response builders, `expect` HTTP (+ actuals),
   `TranslationService` + rate limiting + `canUndoTranslation`.
2. `AppPreferences` translation keys in `SimpleXAPI.kt`; `ComposeState.translateOriginal`/`translateResult`.
3. Chat UI: context-menu Translate, bilingual bubble, compose translate button, viewport auto-translate.
4. Settings UI: `TranslationSettingsView` + `SettingsView` entry.
5. Strings (base + zh-rCN).
6. Tests: `common/src/commonTest/kotlin/chat/simplex/app/TranslationApiTest.kt` (MD5 vectors, URL encoding,
   `detectLanguage`, `isLikelyAlreadyInLanguage`, rate-config normalization, each provider's URL/body/parser
   incl. Baidu signing and AI Responses fallbacks, `canUndoTranslation`, `TranslationEntry` flags) —
   `./gradlew :common:desktopTest`; `./gradlew :common:compileDebugKotlinAndroid`.
7. Docs: `product/concepts.md` (PC46), `product/rules.md` (RULE-31), `product/README.md`,
   `product/views/{chat,settings}.md`, `spec/impact.md` (PC46 + translation package rows),
   `spec/state.md`, `spec/client/{chat-view,compose,translation}.md`, `TODO.md`.
8. Write the diff patch and commit (GPG-signed).

## Risks

- **Third-party terms / rate limits.** Free endpoints (Google/Bing) can change or throttle without notice;
  the rate limiter mitigates bans but the providers can still break. Mitigation: providers are pluggable,
  and users can pick Baidu or an OpenAI-compatible endpoint.
- **Key leakage.** Keys live on the device encrypted; the main risk is the plaintext key being in memory
  during a request. Mitigation: encrypt at rest with `Cryptor`, never export, never log.
- **Auto-translation cost.** A paid provider plus auto-translate can spend money; mitigated by the
  viewport scope and the modest defaults, and by the feature being off by default (`translationAutoTranslate`
  false).
- **UI drift from the message bubble.** The bilingual line is rendered inside the existing framed bubble;
  restart/scroll must not duplicate it. Mitigation: results are keyed per chat item in `TranslationService`.
- **No core test coverage.** Because this is client-only, correctness rests on `:common:desktopTest`; the
  network layer is isolated behind `TranslationHttp` so the pure parts are fully covered.

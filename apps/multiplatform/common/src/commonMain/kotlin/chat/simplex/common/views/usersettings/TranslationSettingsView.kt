package chat.simplex.common.views.usersettings

import SectionBottomSpacer
import SectionDividerSpaced
import SectionTextFooter
import SectionView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import chat.simplex.common.model.ChatController.appPrefs
import chat.simplex.common.platform.ColumnWithScrollBar
import chat.simplex.common.translation.*
import chat.simplex.common.ui.theme.DEFAULT_PADDING
import chat.simplex.common.views.helpers.*
import chat.simplex.res.*
import dev.icerock.moko.resources.compose.painterResource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * Settings screen for the in-app translation feature.
 *
 * All copy comes from [TranslationStrings] (deliberately kept out of `MR.strings`, whose generated
 * `MR$strings.<clinit>` is already near the JVM 64 KiB per-method limit). Every edit is persisted
 * immediately through [TranslationService.saveConfig] / [TranslationService.saveSecrets].
 *
 * The shell is provided by the caller: [close] is invoked by the app bar / back gesture.
 */
@Composable
fun TranslationSettingsView(close: () -> Unit) {
  val initialConfig = remember { TranslationService.config() }
  val initialSecrets = remember { TranslationService.secrets() }

  val provider = remember { mutableStateOf(initialConfig.provider) }
  val targetLang = remember { mutableStateOf(initialConfig.targetLang) }

  val requestInterval = remember { mutableStateOf(TextFieldValue(initialConfig.rate.requestIntervalMs.toString())) }
  val maxConcurrent = remember { mutableStateOf(TextFieldValue(initialConfig.rate.maxConcurrent.toString())) }
  val batchLimit = remember { mutableStateOf(TextFieldValue(initialConfig.rate.batchLimit.toString())) }

  val baiduAppId = remember { mutableStateOf(TextFieldValue(initialConfig.baiduAppId)) }
  val aiBaseUrl = remember { mutableStateOf(TextFieldValue(initialConfig.aiBaseUrl)) }
  val aiModel = remember { mutableStateOf(TextFieldValue(initialConfig.aiModel)) }

  val baiduKey = remember { mutableStateOf(TextFieldValue(initialSecrets.baiduKey)) }
  val aiApiKey = remember { mutableStateOf(TextFieldValue(initialSecrets.aiApiKey)) }

  // Parse the numeric rate fields, falling back to the last saved value on empty/invalid input,
  // then clamp to the valid range with normalized().
  fun currentRate(): TranslationRateConfig {
    val saved = TranslationService.config().rate
    return TranslationRateConfig(
      requestIntervalMs = requestInterval.value.text.toLongOrNull() ?: saved.requestIntervalMs,
      maxConcurrent = maxConcurrent.value.text.toIntOrNull() ?: saved.maxConcurrent,
      batchLimit = batchLimit.value.text.toIntOrNull() ?: saved.batchLimit,
    ).normalized()
  }

  fun saveAll() {
    TranslationService.saveConfig(
      TranslationConfig(
        provider = provider.value,
        targetLang = targetLang.value,
        rate = currentRate(),
        baiduAppId = baiduAppId.value.text.trim(),
        aiBaseUrl = aiBaseUrl.value.text.trim(),
        aiModel = aiModel.value.text.trim(),
      )
    )
    TranslationService.saveSecrets(
      TranslationSecrets(
        baiduKey = baiduKey.value.text.trim(),
        aiApiKey = aiApiKey.value.text.trim(),
      )
    )
  }

  // Persist on every change (skip the initial composition snapshot).
  LaunchedEffect(Unit) {
    snapshotFlow {
      listOf(
        provider.value, targetLang.value,
        requestInterval.value.text, maxConcurrent.value.text, batchLimit.value.text,
        baiduAppId.value.text, aiBaseUrl.value.text, aiModel.value.text,
        baiduKey.value.text, aiApiKey.value.text,
      )
    }
      .drop(1)
      .distinctUntilChanged()
      .collect { saveAll() }
  }

  ModalView(close = close, cardScreen = true) {
    ColumnWithScrollBar {
      AppBarTitle(TranslationStrings.translateVerb)

      // ---- Provider ----
      SectionView(TranslationStrings.provider) {
        val providers = remember { TranslationProvider.entries.map { it to it.displayName } }
        ExposedDropDownSettingRow(TranslationStrings.provider, providers, provider) {
          provider.value = it
        }
      }
      SectionDividerSpaced()

      // ---- Target language for text the user writes ----
      // Received messages are translated into the interface language instead (see TranslationService).
      // Read it during composition so the note follows an interface-language change immediately.
      val incomingLang = TranslationService.incomingTargetLang()
      SectionView(TranslationStrings.composeTargetLanguage) {
        val langs = remember { translationLanguages.map { it.code to it.displayName } }
        ExposedDropDownSettingRow(TranslationStrings.composeTargetLanguage, langs, targetLang) {
          targetLang.value = it
        }
        SectionTextFooter(TranslationStrings.incomingFollowsInterface(languageDisplayName(incomingLang)))
      }
      SectionDividerSpaced()

      // ---- Automatic translation ----
      SectionView(TranslationStrings.autoTranslate) {
        SettingsPreferenceItem(
          painterResource(MR.images.ic_translate),
          TranslationStrings.autoTranslate,
          appPrefs.translationAutoTranslate,
        )
        SectionTextFooter(TranslationStrings.autoTranslateFooter)
      }
      SectionDividerSpaced()

      // ---- Rate limits ----
      SectionView(TranslationStrings.rateLimits) {
        Column(Modifier.padding(horizontal = DEFAULT_PADDING)) {
          DefaultConfigurableTextField(
            requestInterval,
            TranslationStrings.requestInterval,
            modifier = Modifier.fillMaxWidth(),
            isValid = { it.toLongOrNull()?.let { v -> v in 0..60_000 } == true },
            keyboardActions = KeyboardActions(onDone = { defaultKeyboardAction(ImeAction.Done) }),
            keyboardType = KeyboardType.Number,
          )
          DefaultConfigurableTextField(
            maxConcurrent,
            TranslationStrings.maxConcurrent,
            modifier = Modifier.fillMaxWidth(),
            isValid = { it.toIntOrNull()?.let { v -> v in 1..8 } == true },
            keyboardActions = KeyboardActions(onDone = { defaultKeyboardAction(ImeAction.Done) }),
            keyboardType = KeyboardType.Number,
          )
          DefaultConfigurableTextField(
            batchLimit,
            TranslationStrings.batchLimit,
            modifier = Modifier.fillMaxWidth(),
            isValid = { it.toIntOrNull()?.let { v -> v in 1..100 } == true },
            keyboardActions = KeyboardActions(onDone = { defaultKeyboardAction(ImeAction.Done) }),
            keyboardType = KeyboardType.Number,
          )
        }
      }
      SectionDividerSpaced()

      // ---- Provider-specific fields ----
      when (provider.value) {
        TranslationProvider.GOOGLE -> {
          SectionView(TranslationStrings.provider) {
            SectionTextFooter(TranslationStrings.googleFreeNote)
          }
        }
        TranslationProvider.BING -> {
          SectionView(TranslationStrings.provider) {
            SectionTextFooter(TranslationStrings.bingFreeNote)
          }
        }
        TranslationProvider.BAIDU -> {
          SectionView(TranslationStrings.providerSetup) {
            Column(Modifier.padding(horizontal = DEFAULT_PADDING)) {
              DefaultConfigurableTextField(
                baiduAppId,
                TranslationStrings.baiduAppId,
                modifier = Modifier.fillMaxWidth(),
                isValid = { true },
                keyboardActions = KeyboardActions(onNext = { defaultKeyboardAction(ImeAction.Next) }),
                keyboardType = KeyboardType.Text,
              )
              DefaultConfigurableTextField(
                baiduKey,
                TranslationStrings.baiduKey,
                modifier = Modifier.fillMaxWidth(),
                isValid = { true },
                keyboardActions = KeyboardActions(onDone = { defaultKeyboardAction(ImeAction.Done) }),
                keyboardType = KeyboardType.Password,
              )
            }
          }
        }
        TranslationProvider.AI -> {
          SectionView(TranslationStrings.providerSetup) {
            Column(Modifier.padding(horizontal = DEFAULT_PADDING)) {
              DefaultConfigurableTextField(
                aiBaseUrl,
                TranslationStrings.aiBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                isValid = { true },
                keyboardActions = KeyboardActions(onNext = { defaultKeyboardAction(ImeAction.Next) }),
                keyboardType = KeyboardType.Text,
              )
              DefaultConfigurableTextField(
                aiModel,
                TranslationStrings.aiModel,
                modifier = Modifier.fillMaxWidth(),
                isValid = { true },
                keyboardActions = KeyboardActions(onNext = { defaultKeyboardAction(ImeAction.Next) }),
                keyboardType = KeyboardType.Text,
              )
              DefaultConfigurableTextField(
                aiApiKey,
                TranslationStrings.aiApiKey,
                modifier = Modifier.fillMaxWidth(),
                isValid = { true },
                keyboardActions = KeyboardActions(onDone = { defaultKeyboardAction(ImeAction.Done) }),
                keyboardType = KeyboardType.Password,
              )
            }
            SectionTextFooter(TranslationStrings.deepseekDefault)
          }
        }
      }
      SectionBottomSpacer()
    }
  }
}

package com.whatschat.app.ui.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.whatschat.app.R
import com.whatschat.app.data.translate.AppLanguage
import com.whatschat.app.data.translate.VoiceTranslator
import com.whatschat.app.ui.settings.AppUiLanguage
import kotlinx.coroutines.launch

private enum class DownloadState { IDLE, DOWNLOADING, DONE, ERROR }

/**
 * App settings: the app's own display language, and pre-downloading
 * translation language packs for offline use (handy to do once before a
 * trip, while still on wifi).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf(currentAppUiLanguage()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceTranslator = remember { VoiceTranslator(context.applicationContext) }
    var downloadStates by remember { mutableStateOf<Map<AppLanguage, DownloadState>>(emptyMap()) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(AppUiLanguage.entries) { language ->
                ListItem(
                    headlineContent = { Text("${language.flag} ${stringResource(language.labelRes)}") },
                    trailingContent = {
                        if (language == selected) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        selected = language
                        val locales = language.languageTag
                            ?.let { LocaleListCompat.forLanguageTags(it) }
                            ?: LocaleListCompat.getEmptyLocaleList()
                        AppCompatDelegate.setApplicationLocales(locales)
                    }
                )
            }
            item {
                Text(
                    text = stringResource(R.string.settings_language_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item {
                Text(
                    text = "Download languages for offline use",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                Text(
                    text = "Translation already downloads a language automatically the first " +
                        "time you use it, but that needs a connection. Pre-download the ones " +
                        "you'll need before a trip, while you still have wifi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            items(AppLanguage.entries) { language ->
                val state = downloadStates[language] ?: DownloadState.IDLE
                ListItem(
                    headlineContent = { Text("${language.flag} ${language.label}") },
                    trailingContent = {
                        when (state) {
                            DownloadState.DOWNLOADING -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            DownloadState.DONE -> Icon(
                                Icons.Filled.Check,
                                contentDescription = "Downloaded",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            DownloadState.ERROR -> Icon(
                                Icons.Filled.Error,
                                contentDescription = "Download failed, tap to retry",
                                tint = MaterialTheme.colorScheme.error
                            )
                            DownloadState.IDLE -> Icon(Icons.Filled.CloudDownload, contentDescription = "Download")
                        }
                    },
                    modifier = Modifier.clickable(enabled = state != DownloadState.DOWNLOADING) {
                        downloadStates = downloadStates + (language to DownloadState.DOWNLOADING)
                        scope.launch {
                            val result = runCatching { voiceTranslator.downloadForOffline(language) }
                            downloadStates = downloadStates + (
                                language to if (result.isSuccess) DownloadState.DONE else DownloadState.ERROR
                                )
                        }
                    }
                )
            }
        }
    }
}

private fun currentAppUiLanguage(): AppUiLanguage {
    val current = AppCompatDelegate.getApplicationLocales()
    if (current.isEmpty) return AppUiLanguage.SYSTEM_DEFAULT
    val tag = current[0]?.language
    return AppUiLanguage.entries.firstOrNull { it.languageTag == tag } ?: AppUiLanguage.SYSTEM_DEFAULT
}

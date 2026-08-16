package com.whatschat.app.ui.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.whatschat.app.R
import com.whatschat.app.ui.settings.AppUiLanguage

/**
 * Lets the user pick the app's own display language, independent of the
 * per-message translation feature. Uses Android's per-app language API
 * ([AppCompatDelegate.setApplicationLocales]) which persists the choice
 * automatically and re-applies it on every future launch — no extra storage
 * needed here. Picking a language recreates the current activity to apply
 * it immediately, which is normal Android behavior for a locale change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf(currentAppUiLanguage()) }

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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            AppUiLanguage.entries.forEach { language ->
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
            Text(
                text = stringResource(R.string.settings_language_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

private fun currentAppUiLanguage(): AppUiLanguage {
    val current = AppCompatDelegate.getApplicationLocales()
    if (current.isEmpty) return AppUiLanguage.SYSTEM_DEFAULT
    val tag = current[0]?.language
    return AppUiLanguage.entries.firstOrNull { it.languageTag == tag } ?: AppUiLanguage.SYSTEM_DEFAULT
}

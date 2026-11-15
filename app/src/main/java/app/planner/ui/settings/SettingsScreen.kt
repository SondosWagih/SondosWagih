package app.planner.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.planner.R
import app.planner.domain.model.ThemeMode

@StringRes private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes private fun AppLanguage.label() = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.ARABIC -> R.string.language_arabic
    AppLanguage.ENGLISH -> R.string.language_english
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, notifications: NotificationsViewModel, modifier: Modifier = Modifier) {
    val mode by viewModel.themeMode.collectAsStateWithLifecycle()
    var language by remember { mutableStateOf(AppLanguage.current()) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("screen_settings")
    ) {
        NotificationSettingsSection(notifications)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Header(R.string.theme)
        ThemeMode.entries.forEach { m ->
            RadioRow(stringResource(m.label()), selected = mode == m) { viewModel.setThemeMode(m) }
        }
        Spacer(Modifier.height(24.dp))
        Header(R.string.language)
        AppLanguage.entries.forEach { l ->
            RadioRow(stringResource(l.label()), selected = language == l) {
                language = l
                AppLanguage.apply(l)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Header(@StringRes id: Int) {
    Text(stringResource(id), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

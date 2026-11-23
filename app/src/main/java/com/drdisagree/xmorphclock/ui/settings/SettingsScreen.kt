package com.drdisagree.xmorphclock.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drdisagree.xmorphclock.R
import com.drdisagree.xmorphclock.domain.model.WeatherProvider
import com.drdisagree.xmorphclock.ui.components.AppIcon
import com.drdisagree.xmorphclock.ui.components.ExpressiveSwitch
import com.drdisagree.xmorphclock.ui.components.FitText
import com.drdisagree.xmorphclock.ui.components.expressivePillShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpen: (SettingsSection) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val transfer by viewModel.presetTransfer.collectAsStateWithLifecycle()
    val pendingImport by viewModel.presetImport.collectAsStateWithLifecycle()
    var exporting by rememberSaveable { mutableStateOf(false) }
    var exportStyles by remember { mutableStateOf(emptyList<Int>()) }
    var lastImport by remember { mutableStateOf(pendingImport) }
    LaunchedEffect(pendingImport) { if (pendingImport != null) lastImport = pendingImport }
    val shownImport = pendingImport ?: lastImport
    val exportItems = remember(state.ownPresets, state.ownPreviews) {
        state.ownPresets.map { preset -> PresetPickerItem(preset.style, state.ownPreviews[preset.style] ?: preset.face) }
    }
    val importItems = remember(shownImport) {
        shownImport?.entries.orEmpty().map { PresetPickerItem(it.style, it.preview) }
    }
    val importSelection = remember(importItems) { importItems.mapTo(HashSet()) { it.key } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PRESET_MIME)) { uri ->
        uri?.let { viewModel.exportPresets(exportStyles, it.toString()) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.openPresetFile(it.toString()) }
    }
    SettingsPage(
        title = stringResource(R.string.tab_settings),
        loaded = state.loaded,
        onBack = onBack,
        overlay = {
            PresetPickerDialog(
                visible = exporting,
                title = stringResource(R.string.export_dialog_title),
                message = stringResource(if (exportItems.isEmpty()) R.string.export_empty else R.string.export_dialog_message),
                items = exportItems,
                initialSelection = emptySet(),
                confirmLabel = R.string.action_export_count,
                onConfirm = { styles ->
                    exportStyles = styles
                    exporting = false
                    exportLauncher.launch(presetFileName())
                },
                onDismiss = { exporting = false }
            )
            PresetPickerDialog(
                visible = pendingImport != null,
                title = stringResource(R.string.import_dialog_title),
                message = stringResource(R.string.import_dialog_message),
                items = importItems,
                initialSelection = importSelection,
                confirmLabel = R.string.action_import_count,
                onConfirm = viewModel::importPresets,
                onDismiss = viewModel::onImportDismiss
            )
            PresetTransferDialog(transfer, viewModel::onTransferDismiss)
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleTile(
                icon = R.drawable.ic_faces,
                title = stringResource(R.string.setting_clock),
                summary = stringResource(R.string.setting_clock_summary),
                checked = state.clockEnabled,
                onCheckedChange = viewModel::onClockEnabledChange,
                modifier = Modifier.weight(1f)
            )
            ToggleTile(
                icon = R.drawable.ic_shield,
                title = stringResource(R.string.setting_burn_in),
                summary = stringResource(R.string.setting_burn_in_summary),
                checked = state.burnInProtection,
                onCheckedChange = viewModel::onBurnInChange,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsTile(
                icon = R.drawable.ic_depth,
                title = stringResource(R.string.section_depth),
                state = stringResource(if (state.depth.enabled) depthStatusLabel(state.depthStatus) else R.string.toggle_off),
                onClick = { onOpen(SettingsSection.DEPTH) },
                modifier = Modifier.weight(1f)
            )
            SettingsTile(
                icon = R.drawable.ic_weather,
                title = stringResource(R.string.section_weather),
                state = stringResource(
                    R.string.settings_weather_state,
                    stringResource(if (state.weather.settings.provider == WeatherProvider.OPEN_METEO) R.string.weather_open_meteo else R.string.weather_open_weather_map),
                    stringResource(if (state.weather.settings.metric) R.string.weather_celsius else R.string.weather_fahrenheit)
                ),
                onClick = { onOpen(SettingsSection.WEATHER) },
                modifier = Modifier.weight(1f)
            )
        }
        PresetsCard(
            ownCount = state.ownPresets.size,
            onExport = { exporting = true },
            onImport = { importLauncher.launch(arrayOf(ANY_MIME)) }
        )
        ThemeCard(state.themeMode, viewModel::onThemeModeChange)
    }
}

@Composable
private fun ToggleTile(
    icon: Int,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = expressivePillShape(interaction, restCorner = 28.dp, pressedCorner = 20.dp)
    val containerColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        tween(320),
        label = "tileTint"
    )
    val badgeColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        tween(320),
        label = "badge"
    )
    val glyphColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        tween(320),
        label = "glyph"
    )
    val titleColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val summaryColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = { onCheckedChange(!checked) },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        interactionSource = interaction,
        modifier = modifier.semantics { role = Role.Switch }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = badgeColor,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AppIcon(icon, tint = glyphColor, size = 22.dp)
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
            FitText(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
            Spacer(Modifier.height(2.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = summaryColor, minLines = 2, maxLines = 2)
            Spacer(Modifier.height(12.dp))
            ExpressiveSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun presetFileName(): String =
    PRESET_PREFIX + SimpleDateFormat(PRESET_STAMP, Locale.US).format(Date()) + PRESET_EXTENSION

private const val PRESET_MIME = "application/octet-stream"
private const val ANY_MIME = "*/*"
private const val PRESET_PREFIX = "xmorphclock-presets-"
private const val PRESET_STAMP = "yyyyMMdd"
private const val PRESET_EXTENSION = ".xmcpreset"

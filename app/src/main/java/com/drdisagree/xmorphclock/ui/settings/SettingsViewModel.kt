package com.drdisagree.xmorphclock.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drdisagree.xmorphclock.domain.model.FaceKind
import com.drdisagree.xmorphclock.domain.model.PresetFileError
import com.drdisagree.xmorphclock.domain.model.ThemeMode
import com.drdisagree.xmorphclock.domain.model.WeatherCity
import com.drdisagree.xmorphclock.domain.model.WeatherSettings
import com.drdisagree.xmorphclock.domain.usecase.ClosePresetFileUseCase
import com.drdisagree.xmorphclock.domain.usecase.ExportPresetsUseCase
import com.drdisagree.xmorphclock.domain.usecase.ImportPresetsUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveDepthSettingsUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveDepthStatusUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveOwnPresetsUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveSettingsUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveWeatherUseCase
import com.drdisagree.xmorphclock.domain.usecase.ReadPresetFileUseCase
import com.drdisagree.xmorphclock.domain.usecase.RefreshWeatherUseCase
import com.drdisagree.xmorphclock.domain.usecase.RetryDepthUseCase
import com.drdisagree.xmorphclock.domain.usecase.SaveWeatherSettingsUseCase
import com.drdisagree.xmorphclock.domain.usecase.SearchWeatherCitiesUseCase
import com.drdisagree.xmorphclock.domain.usecase.SetBurnInProtectionUseCase
import com.drdisagree.xmorphclock.domain.usecase.SetClockEnabledUseCase
import com.drdisagree.xmorphclock.domain.usecase.SetDepthEnabledUseCase
import com.drdisagree.xmorphclock.domain.usecase.ComposePresetPreviewsUseCase
import com.drdisagree.xmorphclock.domain.usecase.ObserveFacesUseCase
import com.drdisagree.xmorphclock.domain.usecase.SetDepthOpacityUseCase
import com.drdisagree.xmorphclock.domain.usecase.SetDepthWidgetsFrontUseCase
import com.drdisagree.xmorphclock.domain.usecase.SetThemeModeUseCase
import com.drdisagree.xmorphclock.domain.usecase.SetWeatherCityUseCase
import com.drdisagree.xmorphclock.domain.usecase.SetWeatherLocationUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    observeSettings: ObserveSettingsUseCase,
    private val setClockEnabled: SetClockEnabledUseCase,
    private val setBurnInProtection: SetBurnInProtectionUseCase,
    private val setThemeMode: SetThemeModeUseCase,
    observeWeather: ObserveWeatherUseCase,
    private val saveWeatherSettings: SaveWeatherSettingsUseCase,
    private val refreshWeather: RefreshWeatherUseCase,
    private val setWeatherLocation: SetWeatherLocationUseCase,
    private val searchWeatherCities: SearchWeatherCitiesUseCase,
    private val setWeatherCity: SetWeatherCityUseCase,
    observeDepthSettings: ObserveDepthSettingsUseCase,
    observeDepthStatus: ObserveDepthStatusUseCase,
    private val retryDepth: RetryDepthUseCase,
    private val setDepthEnabled: SetDepthEnabledUseCase,
    private val setDepthOpacity: SetDepthOpacityUseCase,
    private val setDepthWidgetsFront: SetDepthWidgetsFrontUseCase,
    observeOwnPresets: ObserveOwnPresetsUseCase,
    observeFaces: ObserveFacesUseCase,
    composePresetPreviews: ComposePresetPreviewsUseCase,
    private val exportPresetFile: ExportPresetsUseCase,
    private val readPresetFile: ReadPresetFileUseCase,
    private val importPresetFile: ImportPresetsUseCase,
    private val closePresetFile: ClosePresetFileUseCase
) : ViewModel() {

    private val notFound = MutableStateFlow(false)
    private val cityResults = MutableStateFlow<List<WeatherCity>>(emptyList())
    private val citySearching = MutableStateFlow(false)
    private var citySearchJob: Job? = null
    private val transfer = MutableStateFlow<PresetTransfer?>(null)
    private val pendingImport = MutableStateFlow<PendingPresetImport?>(null)

    val presetTransfer: StateFlow<PresetTransfer?> = transfer.asStateFlow()
    val presetImport: StateFlow<PendingPresetImport?> = pendingImport.asStateFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        observeSettings(),
        combine(observeWeather(), notFound, cityResults, citySearching) { weather, missing, results, searching ->
            WeatherSearchUi(weather, missing, results, searching)
        },
        combine(observeDepthSettings(), observeDepthStatus()) { depth, status -> depth to status },
        combine(observeOwnPresets(), observeFaces(FaceKind.WEATHER), observeFaces(FaceKind.WIDGET)) { own, weathers, widgets ->
            own to composePresetPreviews(mapOf(FaceKind.CLOCK to own, FaceKind.WEATHER to weathers, FaceKind.WIDGET to widgets))
        }
    ) { settings, weatherUi, (depth, depthStatus), (own, previews) ->
        SettingsUiState(
            clockEnabled = settings.clockEnabled,
            burnInProtection = settings.burnInProtection,
            themeMode = settings.themeMode,
            weather = weatherUi.weather,
            locationNotFound = weatherUi.missing,
            cityResults = weatherUi.results,
            citySearching = weatherUi.searching,
            depth = depth,
            depthStatus = depthStatus,
            ownPresets = own,
            ownPreviews = previews,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun onDepthEnabledChange(enabled: Boolean) {
        viewModelScope.launch { setDepthEnabled(enabled) }
    }

    fun onDepthRetry() {
        viewModelScope.launch { retryDepth() }
    }

    fun onDepthOpacityChange(opacity: Float) {
        viewModelScope.launch { setDepthOpacity(opacity) }
    }

    fun onDepthWidgetsFrontChange(front: Boolean) {
        viewModelScope.launch { setDepthWidgetsFront(front) }
    }

    fun onClockEnabledChange(enabled: Boolean) {
        viewModelScope.launch { setClockEnabled(enabled) }
    }

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { setThemeMode(mode) }
    }

    fun onWeatherSettingsChange(settings: WeatherSettings) {
        viewModelScope.launch { saveWeatherSettings(settings) }
    }

    fun onWeatherLocation(query: String?) {
        viewModelScope.launch { notFound.value = !setWeatherLocation(query) }
    }

    fun onSearchWeatherCities(query: String, immediate: Boolean = false) {
        citySearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            cityResults.value = emptyList()
            citySearching.value = false
            return
        }
        citySearching.value = true
        citySearchJob = viewModelScope.launch {
            if (!immediate) delay(CITY_SEARCH_DEBOUNCE_MS)
            cityResults.value = searchWeatherCities(trimmed)
            citySearching.value = false
        }
    }

    fun onSelectWeatherCity(city: WeatherCity) {
        notFound.value = false
        citySearchJob?.cancel()
        citySearching.value = false
        viewModelScope.launch { setWeatherCity(city) }
    }

    fun onRefreshWeather() {
        viewModelScope.launch { refreshWeather() }
    }

    fun onBurnInChange(enabled: Boolean) {
        viewModelScope.launch { setBurnInProtection(enabled) }
    }

    fun exportPresets(styles: List<Int>, uri: String) {
        viewModelScope.launch {
            val saved = exportPresetFile(styles, uri)
            transfer.value = PresetTransfer(if (saved) PresetTransferStatus.EXPORTED else PresetTransferStatus.SAVE_FAILED)
        }
    }

    fun openPresetFile(uri: String) {
        viewModelScope.launch {
            val preview = readPresetFile(uri)
            val error = preview.error
            if (error == null) {
                pendingImport.value = PendingPresetImport(uri, preview.entries)
            } else {
                closePresetFile()
                transfer.value = PresetTransfer(statusOf(error))
            }
        }
    }

    fun importPresets(styles: List<Int>) {
        val pending = pendingImport.value ?: return
        pendingImport.value = null
        viewModelScope.launch {
            val result = importPresetFile(pending.uri, styles.toSet())
            closePresetFile()
            transfer.value = result.error?.let { PresetTransfer(statusOf(it)) }
                ?: PresetTransfer(PresetTransferStatus.IMPORTED, result.imported)
        }
    }

    fun onImportDismiss() {
        pendingImport.value = null
        closePresetFile()
    }

    fun onTransferDismiss() {
        transfer.value = null
    }

    override fun onCleared() {
        closePresetFile()
    }

    private fun statusOf(error: PresetFileError): PresetTransferStatus = when (error) {
        PresetFileError.NOT_PRESET_FILE -> PresetTransferStatus.NOT_PRESET_FILE
        PresetFileError.UNSUPPORTED_VERSION -> PresetTransferStatus.UNSUPPORTED_VERSION
        PresetFileError.DAMAGED -> PresetTransferStatus.DAMAGED
        PresetFileError.READ_FAILED -> PresetTransferStatus.READ_FAILED
    }

    private data class WeatherSearchUi(
        val weather: com.drdisagree.xmorphclock.domain.model.WeatherState,
        val missing: Boolean,
        val results: List<WeatherCity>,
        val searching: Boolean
    )

    private companion object {
        const val CITY_SEARCH_DEBOUNCE_MS = 280L
    }
}


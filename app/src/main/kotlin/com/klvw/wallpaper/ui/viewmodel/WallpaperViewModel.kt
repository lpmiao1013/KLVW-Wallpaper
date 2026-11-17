package com.klvw.wallpaper.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klvw.wallpaper.aer.AerFolder
import com.klvw.wallpaper.data.db.FolderEntity
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperItem
import com.klvw.wallpaper.data.model.WallpaperTarget
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.StaticImageRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import com.klvw.wallpaper.preset.PresetManager
import com.klvw.wallpaper.schedule.ScheduleManager
import com.klvw.wallpaper.tile.FolderHideTileService
import com.klvw.wallpaper.tile.PresetTileService
import com.klvw.wallpaper.tile.ScheduleTileService
import com.klvw.wallpaper.tile.requestTileListening
import com.klvw.wallpaper.util.SharpnessLogStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

enum class MediaTab { IMAGE, STATIC, VIDEO }

data class WallpaperUiState(
    val isLoading: Boolean = false,
    val isLoadingPreview: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val selectedItem: WallpaperItem? = null,
    val mediaTab: MediaTab = MediaTab.IMAGE,
    val target: WallpaperTarget = WallpaperTarget.HOME,
    val previewItems: List<WallpaperItem> = emptyList(),
    val staticImages: List<WallpaperItem> = emptyList(),
    val imageFolders: List<FolderEntity> = emptyList(),
    val videoFolders: List<FolderEntity> = emptyList(),
    val useVulkan: Boolean = true,
    val wallpaperScaleMode: String = "fill",
    val rotateMismatchEnabled: Boolean = false,
    val rotateMismatchCw: Boolean = true,
    val videoSharpnessMode: String = "standard",
    val doubleTapEnabled: Boolean = false,
    val forceIconColor: String = "auto",
    val tileRandomImageTarget: String = "both",
    val tileRandomVideoTarget: String = "both",
    val tileRestoreTarget: String = "both",
    val tileStaticImageUri: String? = null,
    val tileStaticImageName: String = "",
    val tileStaticImageTarget: String = "both",
    val promptActivateServiceClass: String? = null,
    val pendingPromptLock: Boolean = false,
    val selectedFolderUri: String? = null,
    val defaultHomeImageFolderUri: String? = null,
    val defaultLockImageFolderUri: String? = null,
    val defaultHomeVideoFolderUri: String? = null,
    val defaultLockVideoFolderUri: String? = null,
    /** Wallpaper tabbar “指定” folders (persist across schedule/preset). */
    val activeImageFolderUri: String? = null,
    val activeVideoFolderUri: String? = null,
    val scheduleEnabled: Boolean = false,
    /** Currently applied home / lock wallpaper content URIs (from prefs). */
    val homeAppliedUri: String? = null,
    val lockAppliedUri: String? = null,
    val presetEnabled: Boolean = false,
    val presetHomeUri: String? = null,
    val presetHomeIsVideo: Boolean = false,
    val presetHomeSource: String = "file",
    val presetLockUri: String? = null,
    val presetLockIsVideo: Boolean = false,
    val presetLockSource: String = "file",
    /** QS tile: hide privacy-marked folders from UI. */
    val folderHideMode: Boolean = false,
    /** After About 8-tap switch: show marked folders even in hide mode. */
    val showHiddenFolders: Boolean = false,
    val markedFolders: List<FolderEntity> = emptyList(),
)

@HiltViewModel
class WallpaperViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderRepository: FolderRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val staticImageRepository: StaticImageRepository,
    private val prefs: SettingsPreferences,
    private val scheduleManager: ScheduleManager,
    private val sharpnessLogStore: SharpnessLogStore,
    private val presetManager: PresetManager
) : ViewModel() {

    val sharpnessLogs = sharpnessLogStore.entries

    private val _state = MutableStateFlow(WallpaperUiState())
    val state: StateFlow<WallpaperUiState> = _state.asStateFlow()

    // In-memory cache — avoids async prefs reads on every tab switch
    private var savedImageFolderUri: String? = null
    private var savedVideoFolderUri: String? = null
    /** Drops stale folder scans when the user switches tabs/folders quickly. */
    private val previewLoadGeneration = AtomicInteger(0)

    init {
        viewModelScope.launch {
            // Load persisted folder selections once before first emit
            savedImageFolderUri = prefs.activeImageFolderUri.first()
            savedVideoFolderUri = prefs.activeVideoFolderUri.first()
            _state.update {
                it.copy(
                    selectedFolderUri = savedImageFolderUri,
                    isLoadingPreview = true,
                    previewItems = emptyList()
                )
            }

            combine(
                folderRepository.getFoldersByType(FolderType.IMAGE),
                folderRepository.getFoldersByType(FolderType.VIDEO),
                prefs.useVulkan
            ) { imgFolders, vidFolders, useVulkan ->
                Triple(imgFolders, vidFolders, useVulkan)
            }.collect { (img, vid, vulkan) ->
                _state.update { it.copy(imageFolders = img, videoFolders = vid, useVulkan = vulkan) }
                loadPreviewItems(clearFirst = false)
            }
        }

        viewModelScope.launch {
            prefs.forceIconColor.collect { color ->
                _state.update { it.copy(forceIconColor = color) }
            }
        }

        viewModelScope.launch {
            prefs.doubleTapEnabled.collect { enabled ->
                _state.update { it.copy(doubleTapEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            combine(
                prefs.wallpaperScaleMode,
                prefs.rotateMismatchEnabled,
                prefs.rotateMismatchCw
            ) { scale, rotateEn, rotateCw ->
                Triple(scale, rotateEn, rotateCw)
            }.collect { (scale, rotateEn, rotateCw) ->
                _state.update {
                    it.copy(
                        wallpaperScaleMode = scale,
                        rotateMismatchEnabled = rotateEn,
                        rotateMismatchCw = rotateCw
                    )
                }
            }
        }

        viewModelScope.launch {
            prefs.videoSharpnessMode.collect { sharpness ->
                _state.update { it.copy(videoSharpnessMode = sharpness) }
            }
        }

        viewModelScope.launch {
            combine(
                prefs.tileRandomImageTarget,
                prefs.tileRandomVideoTarget,
                prefs.tileRestoreTarget
            ) { img, vid, restore -> Triple(img, vid, restore) }
                .collect { (img, vid, restore) ->
                    _state.update { it.copy(tileRandomImageTarget = img, tileRandomVideoTarget = vid, tileRestoreTarget = restore) }
                }
        }

        viewModelScope.launch {
            combine(
                prefs.tileStaticImageUri,
                prefs.tileStaticImageName,
                prefs.tileStaticImageTarget
            ) { uri, name, target -> Triple(uri, name, target) }
                .collect { (uri, name, target) ->
                    _state.update { it.copy(tileStaticImageUri = uri, tileStaticImageName = name, tileStaticImageTarget = target) }
                }
        }

        viewModelScope.launch {
            staticImageRepository.getAll().collect { items ->
                _state.update { s ->
                    val preview = if (s.mediaTab == MediaTab.STATIC) items else s.previewItems
                    s.copy(staticImages = items, previewItems = preview)
                }
            }
        }

        viewModelScope.launch {
            combine(
                prefs.defaultHomeImageFolderUri,
                prefs.defaultLockImageFolderUri,
                prefs.defaultHomeVideoFolderUri,
                prefs.defaultLockVideoFolderUri
            ) { homeImg, lockImg, homeVid, lockVid ->
                listOf(homeImg, lockImg, homeVid, lockVid)
            }.collect { d ->
                _state.update { it.copy(
                    defaultHomeImageFolderUri = d[0],
                    defaultLockImageFolderUri = d[1],
                    defaultHomeVideoFolderUri = d[2],
                    defaultLockVideoFolderUri = d[3]
                )}
            }
        }

        viewModelScope.launch {
            combine(prefs.activeImageFolderUri, prefs.activeVideoFolderUri) { img, vid ->
                img to vid
            }.collect { (img, vid) ->
                _state.update { it.copy(activeImageFolderUri = img, activeVideoFolderUri = vid) }
            }
        }

        viewModelScope.launch {
            combine(prefs.scheduleEnabled, prefs.presetEnabled) { scheduleOn, presetOn ->
                scheduleOn to presetOn
            }.collect { (scheduleOn, presetOn) ->
                _state.update { it.copy(scheduleEnabled = scheduleOn, presetEnabled = presetOn) }
                when {
                    presetOn -> syncFolderToPreset()
                    scheduleOn -> syncFolderToSchedule()
                    else -> restorePickerFromActiveFolders()
                }
            }
        }

        viewModelScope.launch {
            combine(prefs.homeWallpaperUri, prefs.lockWallpaperUri) { home, lock ->
                home to lock
            }.collect { (home, lock) ->
                _state.update { it.copy(homeAppliedUri = home, lockAppliedUri = lock) }
            }
        }

        viewModelScope.launch {
            combine(
                prefs.presetHomeUri,
                prefs.presetHomeIsVideo,
                prefs.presetHomeSource
            ) { uri, isVideo, source -> Triple(uri, isVideo, source) }
                .collect { (uri, isVideo, source) ->
                    _state.update {
                        it.copy(
                            presetHomeUri = uri,
                            presetHomeIsVideo = isVideo,
                            presetHomeSource = source
                        )
                    }
                }
        }
        viewModelScope.launch {
            combine(
                prefs.presetLockUri,
                prefs.presetLockIsVideo,
                prefs.presetLockSource
            ) { uri, isVideo, source -> Triple(uri, isVideo, source) }
                .collect { (uri, isVideo, source) ->
                    _state.update {
                        it.copy(
                            presetLockUri = uri,
                            presetLockIsVideo = isVideo,
                            presetLockSource = source
                        )
                    }
                }
        }

        viewModelScope.launch {
            combine(prefs.folderHideMode, prefs.showHiddenFolders) { hide, show ->
                hide to show
            }.collect { (hide, show) ->
                _state.update { it.copy(folderHideMode = hide, showHiddenFolders = show) }
            }
        }
        viewModelScope.launch {
            folderRepository.getMarkedFolders().collect { list ->
                _state.update { it.copy(markedFolders = list) }
            }
        }
    }

    /** True when schedule (not preset) is driving folder resolution. */
    private fun isScheduleDrivingUi(): Boolean =
        _state.value.scheduleEnabled && !_state.value.presetEnabled

    private fun isPresetDrivingUi(): Boolean = _state.value.presetEnabled

    /** Lock picker to the active schedule folder for the current target (UI only — does not overwrite active prefs). */
    private suspend fun syncFolderToSchedule() {
        if (!scheduleManager.isEnabled()) return
        val screen = when (_state.value.target) {
            WallpaperTarget.LOCK -> WallpaperTarget.LOCK
            else -> WallpaperTarget.HOME
        }
        val rule = scheduleManager.currentRule(screen) ?: return
        val tab = if (rule.media == FolderType.VIDEO) MediaTab.VIDEO else MediaTab.IMAGE
        _state.update {
            it.copy(
                mediaTab = tab,
                selectedFolderUri = rule.folderUri,
                selectedItem = null,
                previewItems = emptyList(),
                isLoadingPreview = true
            )
        }
        loadPreviewItems(clearFirst = false)
    }

    private suspend fun syncFolderToPreset() {
        if (!_state.value.presetEnabled) return
        val screen = when (_state.value.target) {
            WallpaperTarget.LOCK -> WallpaperTarget.LOCK
            else -> WallpaperTarget.HOME
        }
        val isVideo = if (screen == WallpaperTarget.LOCK) {
            _state.value.presetLockIsVideo
        } else {
            _state.value.presetHomeIsVideo
        }
        val source = if (screen == WallpaperTarget.LOCK) {
            _state.value.presetLockSource
        } else {
            _state.value.presetHomeSource
        }
        val uri = if (screen == WallpaperTarget.LOCK) {
            _state.value.presetLockUri
        } else {
            _state.value.presetHomeUri
        }
        if (source != "folder" || uri.isNullOrBlank()) return
        val tab = if (isVideo) MediaTab.VIDEO else MediaTab.IMAGE
        _state.update {
            it.copy(
                mediaTab = tab,
                selectedFolderUri = uri,
                selectedItem = null,
                previewItems = emptyList(),
                isLoadingPreview = true
            )
        }
        loadPreviewItems(clearFirst = false)
    }

    /** After schedule/preset release, restore tabbar from persisted active folders. */
    private suspend fun restorePickerFromActiveFolders() {
        savedImageFolderUri = prefs.activeImageFolderUri.first()
        savedVideoFolderUri = prefs.activeVideoFolderUri.first()
        val tab = _state.value.mediaTab
        val folderUri = when (tab) {
            MediaTab.IMAGE -> savedImageFolderUri
            MediaTab.VIDEO -> savedVideoFolderUri
            MediaTab.STATIC -> null
        }
        _state.update {
            it.copy(
                selectedFolderUri = folderUri,
                selectedItem = null,
                previewItems = emptyList(),
                isLoadingPreview = tab != MediaTab.STATIC
            )
        }
        if (tab != MediaTab.STATIC) loadPreviewItems(clearFirst = false)
    }

    fun setMediaTab(tab: MediaTab) {
        if (_state.value.isLoadingPreview && _state.value.mediaTab == tab) return
        if (isPresetDrivingUi()) {
            _state.update { it.copy(errorMessage = "预设方案已启用，请先关闭预设") }
            viewModelScope.launch { syncFolderToPreset() }
            return
        }
        if (isScheduleDrivingUi() && tab == MediaTab.STATIC) {
            _state.update { it.copy(errorMessage = "时段方案已启用，请使用当前时段文件夹") }
            return
        }
        if (isScheduleDrivingUi()) {
            viewModelScope.launch { syncFolderToSchedule() }
            return
        }
        val folderUri = when (tab) {
            MediaTab.IMAGE -> savedImageFolderUri
            MediaTab.VIDEO -> savedVideoFolderUri
            MediaTab.STATIC -> null
        }
        // Clear grid in the same frame as the tab change so image thumbs never
        // sit under the video tab (or vice versa) while SAF scan is slow.
        _state.update {
            it.copy(
                mediaTab = tab,
                selectedItem = null,
                selectedFolderUri = folderUri,
                previewItems = emptyList(),
                isLoadingPreview = true
            )
        }
        loadPreviewItems(clearFirst = false)
    }

    fun setTarget(target: WallpaperTarget) {
        _state.update { it.copy(target = target) }
        when {
            isPresetDrivingUi() -> viewModelScope.launch { syncFolderToPreset() }
            isScheduleDrivingUi() -> viewModelScope.launch { syncFolderToSchedule() }
        }
    }

    fun selectItem(item: WallpaperItem) {
        if (_state.value.isLoadingPreview) return
        _state.update { it.copy(selectedItem = item) }
    }

    fun selectFolder(folderUri: String?) {
        if (_state.value.isLoadingPreview) return
        if (isPresetDrivingUi()) {
            _state.update { it.copy(errorMessage = "预设方案已启用，只能使用预设文件夹") }
            return
        }
        if (isScheduleDrivingUi()) {
            _state.update { it.copy(errorMessage = "时段方案已启用，只能使用当前时段文件夹") }
            return
        }
        val tab = _state.value.mediaTab
        if (tab == MediaTab.IMAGE) savedImageFolderUri = folderUri
        else if (tab == MediaTab.VIDEO) savedVideoFolderUri = folderUri
        _state.update {
            it.copy(
                selectedFolderUri = folderUri,
                selectedItem = null,
                previewItems = emptyList(),
                isLoadingPreview = true
            )
        }
        loadPreviewItems(clearFirst = false)
        viewModelScope.launch {
            if (tab == MediaTab.IMAGE) prefs.setActiveImageFolder(folderUri)
            else if (tab == MediaTab.VIDEO) prefs.setActiveVideoFolder(folderUri)
        }
    }

    fun setWallpaper() {
        val item = _state.value.selectedItem ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val target = _state.value.target
                if (isPresetDrivingUi()) {
                    _state.update {
                        it.copy(isLoading = false, errorMessage = "预设方案已启用，请先关闭预设再手动指定")
                    }
                    return@launch
                }
                if (scheduleManager.isEnabled()) {
                    val screens = when (target) {
                        WallpaperTarget.BOTH -> listOf(WallpaperTarget.HOME, WallpaperTarget.LOCK)
                        WallpaperTarget.LOCK -> listOf(WallpaperTarget.LOCK)
                        else -> listOf(WallpaperTarget.HOME)
                    }
                    for (screen in screens) {
                        val rule = scheduleManager.currentRule(screen)
                        if (rule == null || item.type != rule.media || item.folderUri != rule.folderUri) {
                            _state.update {
                                it.copy(isLoading = false, errorMessage = "时段方案已启用，请从当前时段文件夹选择")
                            }
                            return@launch
                        }
                    }
                }
                wallpaperRepository.setWallpaper(item, target)

                val homeNeeded = (target == WallpaperTarget.HOME || target == WallpaperTarget.BOTH)
                    && !wallpaperRepository.isHomeServiceActive()
                val lockNeeded = (target == WallpaperTarget.LOCK || target == WallpaperTarget.BOTH)
                    && !wallpaperRepository.isLockServiceActive()

                when {
                    homeNeeded -> _state.update {
                        it.copy(
                            isLoading = false,
                            promptActivateServiceClass = "com.klvw.wallpaper.service.KLVWallpaperService",
                            pendingPromptLock = lockNeeded
                        )
                    }
                    lockNeeded -> _state.update {
                        it.copy(
                            isLoading = false,
                            promptActivateServiceClass = "com.klvw.wallpaper.service.KLVWallpaperServiceLock",
                            pendingPromptLock = false
                        )
                    }
                    else -> _state.update {
                        it.copy(isLoading = false, successMessage = "Wallpaper set!")
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to set wallpaper") }
            }
        }
    }

    fun dismissLiveWallpaperPrompt() {
        viewModelScope.launch {
            val needsLock = _state.value.pendingPromptLock
            _state.update { it.copy(promptActivateServiceClass = null, pendingPromptLock = false) }
            if (needsLock && !wallpaperRepository.isLockServiceActive()) {
                _state.update { it.copy(promptActivateServiceClass = "com.klvw.wallpaper.service.KLVWallpaperServiceLock") }
            } else if (!needsLock) {
                _state.update { it.copy(successMessage = "Wallpaper set!") }
            }
        }
    }

    fun addFolder(uri: Uri, type: FolderType) {
        viewModelScope.launch {
            folderRepository.addFolder(uri, type)
            // combine collector handles loadPreviewItems when folders change
        }
    }

    fun removeFolder(folder: FolderEntity) {
        viewModelScope.launch { folderRepository.removeFolder(folder) }
    }

    fun setFolderPrivacyMark(folder: FolderEntity, marked: Boolean) {
        viewModelScope.launch {
            folderRepository.setFolderPrivacyMark(folder, marked)
            // If hide-mode is on and we just marked the current picker folder, leave it.
            if (marked && _state.value.folderHideMode &&
                _state.value.selectedFolderUri == folder.uri
            ) {
                clearPickerIfSelected(folder)
            }
        }
    }

    fun setFolderHideMode(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setFolderHideMode(enabled)
            if (enabled) prefs.setShowHiddenFolders(false)
            requestTileListening(context, FolderHideTileService::class.java)
            if (enabled) {
                val sel = _state.value.selectedFolderUri
                val marked = _state.value.markedFolders.find { it.uri == sel }
                if (marked != null) clearPickerIfSelected(marked)
            }
        }
    }

    fun setShowHiddenFolders(show: Boolean) {
        viewModelScope.launch { prefs.setShowHiddenFolders(show) }
    }

    private suspend fun clearPickerIfSelected(folder: FolderEntity) {
        if (_state.value.selectedFolderUri != folder.uri) return
        if (folder.type == FolderType.STATIC) return
        val next = when (folder.type) {
            FolderType.IMAGE -> _state.value.imageFolders.firstOrNull { it.uri != folder.uri && !it.hidden }
            FolderType.VIDEO -> _state.value.videoFolders.firstOrNull { it.uri != folder.uri && !it.hidden }
            FolderType.STATIC -> null
        }
        if (folder.type == FolderType.IMAGE) {
            savedImageFolderUri = next?.uri
            prefs.setActiveImageFolder(next?.uri)
        } else {
            savedVideoFolderUri = next?.uri
            prefs.setActiveVideoFolder(next?.uri)
        }
        _state.update {
            it.copy(
                selectedFolderUri = next?.uri,
                previewItems = emptyList(),
                isLoadingPreview = next != null
            )
        }
        if (next != null) loadPreviewItems(clearFirst = true)
    }

    fun addStaticImage(uri: Uri, displayName: String) {
        viewModelScope.launch { staticImageRepository.add(uri, displayName) }
    }

    fun removeStaticImage(uri: Uri) {
        viewModelScope.launch { staticImageRepository.remove(uri) }
    }

    fun clearMessage() {
        _state.update { it.copy(successMessage = null, errorMessage = null) }
    }

    // ── Daily schedule ──────────────────────────────────────────────

    val scheduleGroupsJson: StateFlow<String> = prefs.scheduleGroupsJson
        .stateIn(viewModelScope, SharingStarted.Eagerly, "[]")
    val scheduleActiveGroupId: StateFlow<String?> = prefs.scheduleActiveGroupId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val scheduleEnabledFlow: StateFlow<Boolean> = prefs.scheduleEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setScheduleEnabled(enabled: Boolean, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val err = scheduleManager.setEnabled(enabled)
            requestTileListening(context, ScheduleTileService::class.java)
            onResult(err)
        }
    }

    fun setScheduleActiveGroup(id: String?, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(scheduleManager.setActiveGroupId(id))
        }
    }

    fun saveScheduleGroups(
        groups: List<com.klvw.wallpaper.data.model.ScheduleGroup>,
        onResult: (String?) -> Unit
    ) {
        viewModelScope.launch {
            onResult(scheduleManager.saveGroups(groups))
        }
    }

    // ── Scene preset ────────────────────────────────────────────────

    fun setPresetHome(uri: String?, isVideo: Boolean, source: String? = null) {
        viewModelScope.launch { prefs.setPresetHome(uri, isVideo, source) }
    }

    fun setPresetLock(uri: String?, isVideo: Boolean, source: String? = null) {
        viewModelScope.launch { prefs.setPresetLock(uri, isVideo, source) }
    }

    fun setPresetHomeMedia(isVideo: Boolean) {
        viewModelScope.launch {
            val src = prefs.presetHomeSource.first()
            prefs.setPresetHome(null, isVideo, src)
        }
    }

    fun setPresetLockMedia(isVideo: Boolean) {
        viewModelScope.launch {
            val src = prefs.presetLockSource.first()
            prefs.setPresetLock(null, isVideo, src)
        }
    }

    fun setPresetHomeSource(source: String) {
        viewModelScope.launch { prefs.setPresetHomeSource(source) }
    }

    fun setPresetLockSource(source: String) {
        viewModelScope.launch { prefs.setPresetLockSource(source) }
    }

    fun enablePreset(onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val err = presetManager.enable()
            requestTileListening(context, PresetTileService::class.java)
            requestTileListening(context, ScheduleTileService::class.java)
            onResult(err)
        }
    }

    fun disablePreset(onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val err = presetManager.disable()
            requestTileListening(context, PresetTileService::class.java)
            requestTileListening(context, ScheduleTileService::class.java)
            onResult(err)
        }
    }

    fun setUseVulkan(enabled: Boolean) {
        viewModelScope.launch { prefs.setUseVulkan(enabled) }
    }

    fun setWallpaperScaleMode(value: String) {
        viewModelScope.launch { prefs.setWallpaperScaleMode(value) }
    }

    fun setRotateMismatchEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setRotateMismatchEnabled(enabled) }
    }

    fun setRotateMismatchCw(clockwise: Boolean) {
        viewModelScope.launch { prefs.setRotateMismatchCw(clockwise) }
    }

    fun setVideoSharpnessMode(value: String) {
        viewModelScope.launch { prefs.setVideoSharpnessMode(value) }
    }

    fun clearSharpnessLogs() {
        sharpnessLogStore.clear()
    }

    fun setDoubleTapEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.setDoubleTapEnabled(enabled) }
    }

    fun setForceIconColor(value: String) {
        viewModelScope.launch { prefs.setForceIconColor(value) }
    }

    fun setTileRandomImageTarget(v: String) { viewModelScope.launch { prefs.setTileRandomImageTarget(v) } }
    fun setTileRandomVideoTarget(v: String) { viewModelScope.launch { prefs.setTileRandomVideoTarget(v) } }
    fun setTileRestoreTarget(v: String) { viewModelScope.launch { prefs.setTileRestoreTarget(v) } }
    fun setTileStaticImage(uri: String?, name: String) { viewModelScope.launch { prefs.setTileStaticImage(uri, name) } }
    fun setTileStaticImageTarget(v: String) { viewModelScope.launch { prefs.setTileStaticImageTarget(v) } }

    fun setDefaultFolder(target: String, mediaType: String, uri: String?) {
        viewModelScope.launch { prefs.setDefaultFolderUri(target, mediaType, uri) }
    }

    fun getAvailableAerFolders(relPath: String? = null): List<AerFolder> = folderRepository.getAerFolders(relPath)

    fun addAerFolder(aerFolder: AerFolder, type: FolderType) {
        viewModelScope.launch { folderRepository.addAerFolder(aerFolder, type) }
    }

    /** Manual rescan of the current folder(s); use after the user added files on disk. */
    fun refreshPreviewList() {
        val tab = _state.value.mediaTab
        if (tab == MediaTab.STATIC) return
        if (_state.value.isLoadingPreview) return
        loadPreviewItems(clearFirst = true, forceRefresh = true)
    }

    private fun loadPreviewItems(clearFirst: Boolean = false, forceRefresh: Boolean = false) {
        val gen = previewLoadGeneration.incrementAndGet()
        viewModelScope.launch(Dispatchers.IO) {
            if (clearFirst) {
                _state.update { it.copy(isLoadingPreview = true, previewItems = emptyList()) }
            }
            val s = _state.value
            val tab = s.mediaTab
            val selUri = s.selectedFolderUri
            val items = when (tab) {
                MediaTab.STATIC -> s.staticImages
                MediaTab.IMAGE -> when {
                    selUri == null ->
                        s.imageFolders.flatMap {
                            folderRepository.getItemsFromFolder(it.uri, FolderType.IMAGE, forceRefresh)
                        }
                    else ->
                        s.imageFolders.find { it.uri == selUri }
                            ?.let {
                                folderRepository.getItemsFromFolder(it.uri, FolderType.IMAGE, forceRefresh)
                            }
                            ?: emptyList()
                }
                MediaTab.VIDEO -> when {
                    selUri == null ->
                        s.videoFolders.flatMap {
                            folderRepository.getItemsFromFolder(it.uri, FolderType.VIDEO, forceRefresh)
                        }
                    else ->
                        s.videoFolders.find { it.uri == selUri }
                            ?.let {
                                folderRepository.getItemsFromFolder(it.uri, FolderType.VIDEO, forceRefresh)
                            }
                            ?: emptyList()
                }
            }
            if (gen != previewLoadGeneration.get()) return@launch
            // Tab/folder may have changed while scanning — only apply matching results.
            val latest = _state.value
            if (latest.mediaTab != tab || latest.selectedFolderUri != selUri) return@launch
            _state.update { it.copy(previewItems = items, isLoadingPreview = false) }
        }
    }
}

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
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import com.klvw.wallpaper.data.prefs.SettingsPreferences
import com.klvw.wallpaper.data.prefs.ShuffleHistoryManager
import com.klvw.wallpaper.data.repository.FolderRepository
import com.klvw.wallpaper.data.repository.StaticImageRepository
import com.klvw.wallpaper.data.repository.WallpaperRepository
import com.klvw.wallpaper.preset.PresetManager
import com.klvw.wallpaper.schedule.ScheduleManager
import com.klvw.wallpaper.scheme.WallpaperScheme
import com.klvw.wallpaper.scheme.WallpaperSchemeResolver
import com.klvw.wallpaper.service.renderer.VideoPlaybackPositions
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

/** Result of checking whether a source file may be deleted from the preview dialog. */
sealed class DeleteSourcePrep {
    data class Blocked(val message: String) : DeleteSourcePrep()
    data class NeedSwitchFirst(val item: WallpaperItem) : DeleteSourcePrep()
    data class ReadyToConfirm(val item: WallpaperItem) : DeleteSourcePrep()
}

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
    /** Wallpaper tabbar “指定” — home/lock × image/video, independent stores. */
    val activeHomeImageFolderUri: String? = null,
    val activeHomeVideoFolderUri: String? = null,
    val activeLockImageFolderUri: String? = null,
    val activeLockVideoFolderUri: String? = null,
    val scheduleEnabled: Boolean = false,
    /** Currently applied home / lock wallpaper content URIs (from prefs). */
    val homeAppliedUri: String? = null,
    val lockAppliedUri: String? = null,
    val homeIsVideo: Boolean = false,
    val lockIsVideo: Boolean = false,
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
    private val presetManager: PresetManager,
    private val schemeResolver: WallpaperSchemeResolver,
    private val shuffleHistoryManager: ShuffleHistoryManager
) : ViewModel() {

    val sharpnessLogs = sharpnessLogStore.entries

    private val _state = MutableStateFlow(WallpaperUiState())
    val state: StateFlow<WallpaperUiState> = _state.asStateFlow()

    /** In-memory tabbar picks: home/lock × image/video — never shared with schedule/preset. */
    private var savedHomeImage: String? = null
    private var savedHomeVideo: String? = null
    private var savedLockImage: String? = null
    private var savedLockVideo: String? = null
    /** Drops stale folder scans when the user switches tabs/folders quickly. */
    private val previewLoadGeneration = AtomicInteger(0)

    init {
        viewModelScope.launch {
            savedHomeImage = prefs.activeFolderUri(WallpaperTarget.HOME, FolderType.IMAGE).first()
            savedHomeVideo = prefs.activeFolderUri(WallpaperTarget.HOME, FolderType.VIDEO).first()
            savedLockImage = prefs.activeFolderUri(WallpaperTarget.LOCK, FolderType.IMAGE).first()
            savedLockVideo = prefs.activeFolderUri(WallpaperTarget.LOCK, FolderType.VIDEO).first()
            val homeVid = prefs.homeIsVideo.first()
            val lockVid = prefs.lockIsVideo.first()
            val img = folderRepository.getFoldersByType(FolderType.IMAGE).first()
            val vid = folderRepository.getFoldersByType(FolderType.VIDEO).first()
            _state.update {
                it.copy(
                    imageFolders = img,
                    videoFolders = vid,
                    homeIsVideo = homeVid,
                    lockIsVideo = lockVid,
                    isLoadingPreview = true,
                    previewItems = emptyList()
                )
            }
            applyPickerDefaultSelection()

            combine(
                folderRepository.getFoldersByType(FolderType.IMAGE),
                folderRepository.getFoldersByType(FolderType.VIDEO),
                prefs.useVulkan
            ) { imgFolders, vidFolders, useVulkan ->
                Triple(imgFolders, vidFolders, useVulkan)
            }.collect { (imgFolders, vidFolders, vulkan) ->
                _state.update { s ->
                    val sel = s.selectedFolderUri
                    val list = if (s.mediaTab == MediaTab.VIDEO) vidFolders else imgFolders
                    val schemeLocked = s.presetEnabled || (s.scheduleEnabled && !s.presetEnabled)
                    val dropHidden = sel != null &&
                        !schemeLocked &&
                        s.mediaTab != MediaTab.STATIC &&
                        list.none { it.uri == sel }
                    s.copy(
                        imageFolders = imgFolders,
                        videoFolders = vidFolders,
                        useVulkan = vulkan,
                        selectedFolderUri = if (dropHidden) null else sel,
                        previewItems = if (dropHidden) emptyList() else s.previewItems,
                        isLoadingPreview = if (dropHidden) true else s.isLoadingPreview
                    )
                }
                loadPreviewItems(clearFirst = false)
            }
        }

        viewModelScope.launch {
            combine(prefs.homeIsVideo, prefs.lockIsVideo) { home, lock -> home to lock }
                .collect { (home, lock) ->
                    _state.update { it.copy(homeIsVideo = home, lockIsVideo = lock) }
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
            combine(
                prefs.activeFolderUri(WallpaperTarget.HOME, FolderType.IMAGE),
                prefs.activeFolderUri(WallpaperTarget.HOME, FolderType.VIDEO),
                prefs.activeFolderUri(WallpaperTarget.LOCK, FolderType.IMAGE),
                prefs.activeFolderUri(WallpaperTarget.LOCK, FolderType.VIDEO)
            ) { hImg, hVid, lImg, lVid ->
                listOf(hImg, hVid, lImg, lVid)
            }.collect { d ->
                savedHomeImage = d[0]
                savedHomeVideo = d[1]
                savedLockImage = d[2]
                savedLockVideo = d[3]
                _state.update {
                    it.copy(
                        activeHomeImageFolderUri = d[0],
                        activeHomeVideoFolderUri = d[1],
                        activeLockImageFolderUri = d[2],
                        activeLockVideoFolderUri = d[3]
                    )
                }
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
                    else -> applyPickerDefaultSelection()
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

    private fun screenTarget(): WallpaperTarget =
        if (_state.value.target == WallpaperTarget.LOCK) WallpaperTarget.LOCK else WallpaperTarget.HOME

    private fun putSaved(target: WallpaperTarget, tab: MediaTab, uri: String?) {
        when {
            target == WallpaperTarget.LOCK && tab == MediaTab.IMAGE -> savedLockImage = uri
            target == WallpaperTarget.LOCK && tab == MediaTab.VIDEO -> savedLockVideo = uri
            tab == MediaTab.IMAGE -> savedHomeImage = uri
            tab == MediaTab.VIDEO -> savedHomeVideo = uri
        }
    }

    /**
     * Picker dropdown default (browse only — does not write 指定仓):
     * type matches current screen wallpaper → resolve folder if visible;
     * else / hidden / miss → null = 「全部文件夹」.
     */
    private suspend fun defaultPickerFolderUri(
        target: WallpaperTarget,
        tab: MediaTab
    ): String? {
        if (tab != MediaTab.IMAGE && tab != MediaTab.VIDEO) return null
        val wantVideo = tab == MediaTab.VIDEO
        val screenIsVideo = if (target == WallpaperTarget.LOCK) {
            _state.value.lockIsVideo
        } else {
            _state.value.homeIsVideo
        }
        if (wantVideo != screenIsVideo) return null
        val type = if (wantVideo) FolderType.VIDEO else FolderType.IMAGE
        val uri = schemeResolver.resolveFolderUri(target, type) ?: return null
        val visible = if (wantVideo) _state.value.videoFolders else _state.value.imageFolders
        return uri.takeIf { u -> visible.any { it.uri == u } }
    }

    /** After schedule/preset release, or tab/target change — browse default only. */
    private suspend fun applyPickerDefaultSelection() {
        when {
            isPresetDrivingUi() -> {
                syncFolderToPreset()
                return
            }
            isScheduleDrivingUi() -> {
                syncFolderToSchedule()
                return
            }
        }
        savedHomeImage = prefs.activeFolderUri(WallpaperTarget.HOME, FolderType.IMAGE).first()
        savedHomeVideo = prefs.activeFolderUri(WallpaperTarget.HOME, FolderType.VIDEO).first()
        savedLockImage = prefs.activeFolderUri(WallpaperTarget.LOCK, FolderType.IMAGE).first()
        savedLockVideo = prefs.activeFolderUri(WallpaperTarget.LOCK, FolderType.VIDEO).first()
        val tab = _state.value.mediaTab
        if (tab == MediaTab.STATIC) return
        val folderUri = defaultPickerFolderUri(screenTarget(), tab)
        _state.update {
            it.copy(
                selectedFolderUri = folderUri,
                selectedItem = null,
                previewItems = emptyList(),
                isLoadingPreview = true
            )
        }
        loadPreviewItems(clearFirst = false)
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
        // Clear grid in the same frame as the tab change so image thumbs never
        // sit under the video tab (or vice versa) while SAF scan is slow.
        _state.update {
            it.copy(
                mediaTab = tab,
                selectedItem = null,
                selectedFolderUri = null,
                previewItems = emptyList(),
                isLoadingPreview = true
            )
        }
        viewModelScope.launch {
            val folderUri = defaultPickerFolderUri(screenTarget(), tab)
            if (_state.value.mediaTab != tab) return@launch
            _state.update {
                it.copy(
                    selectedFolderUri = folderUri,
                    previewItems = emptyList(),
                    isLoadingPreview = true
                )
            }
            loadPreviewItems(clearFirst = false)
        }
    }

    fun setTarget(target: WallpaperTarget) {
        _state.update { it.copy(target = target) }
        viewModelScope.launch { applyPickerDefaultSelection() }
    }

    fun selectItem(item: WallpaperItem) {
        if (_state.value.isLoadingPreview) return
        _state.update { it.copy(selectedItem = item) }
    }

    /**
     * Browse-only: changes the picker preview folder.
     * Does NOT write 指定仓 — that happens when the user actually sets a wallpaper
     * (see [rememberActiveFolderFromItem]), so merely opening/refreshing folder B
     * cannot steal rotation away from folder A.
     */
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
        if (tab != MediaTab.IMAGE && tab != MediaTab.VIDEO) return
        _state.update {
            it.copy(
                selectedFolderUri = folderUri,
                selectedItem = null,
                previewItems = emptyList(),
                isLoadingPreview = true
            )
        }
        loadPreviewItems(clearFirst = false)
    }

    /** Persist 指定仓 from a wallpaper that was actually applied. */
    private suspend fun rememberActiveFolderFromItem(
        item: WallpaperItem,
        target: WallpaperTarget
    ) {
        if (item.folderUri.isBlank()) return
        if (item.type != FolderType.IMAGE && item.type != FolderType.VIDEO) return
        val screens = when (target) {
            WallpaperTarget.BOTH -> listOf(WallpaperTarget.HOME, WallpaperTarget.LOCK)
            WallpaperTarget.LOCK -> listOf(WallpaperTarget.LOCK)
            else -> listOf(WallpaperTarget.HOME)
        }
        val tab = if (item.type == FolderType.VIDEO) MediaTab.VIDEO else MediaTab.IMAGE
        for (screen in screens) {
            putSaved(screen, tab, item.folderUri)
            prefs.setActiveFolder(screen, item.type, item.folderUri)
        }
    }

    /** Button-state scheme for banners: preset > schedule > tabbar > default. */
    suspend fun currentScheme(): WallpaperScheme = schemeResolver.currentScheme()

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
                // Only an actual set updates 指定仓 — browsing/refresh must not.
                if (!isPresetDrivingUi() && !scheduleManager.isEnabled()) {
                    rememberActiveFolderFromItem(item, target)
                }

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
        val target = screenTarget()
        val tab = if (folder.type == FolderType.VIDEO) MediaTab.VIDEO else MediaTab.IMAGE
        // Hidden / unmarked current folder → 「全部文件夹」, do not force first row.
        putSaved(target, tab, null)
        prefs.setActiveFolder(target, folder.type, null)
        _state.update {
            it.copy(
                selectedFolderUri = null,
                previewItems = emptyList(),
                isLoadingPreview = true
            )
        }
        loadPreviewItems(clearFirst = true)
    }

    fun addStaticImage(uri: Uri, displayName: String) {
        viewModelScope.launch { staticImageRepository.add(uri, displayName) }
    }

    fun removeStaticImage(uri: Uri) {
        viewModelScope.launch { staticImageRepository.remove(uri) }
    }

    /**
     * Gate before delete confirm:
     * - last file in folder/path → [DeleteSourcePrep.Blocked]
     * - currently applied on home/lock → [DeleteSourcePrep.NeedSwitchFirst]
     * - otherwise → [DeleteSourcePrep.ReadyToConfirm]
     */
    fun prepareDeleteSource(item: WallpaperItem, onResult: (DeleteSourcePrep) -> Unit) {
        viewModelScope.launch {
            val peers = peerItemsForDelete(item)
            if (peers.size <= 1) {
                onResult(
                    DeleteSourcePrep.Blocked("至少需要一个文件，请先切换到其他资源后再删除。")
                )
                return@launch
            }
            if (isSourceCurrentlyApplied(item)) {
                onResult(DeleteSourcePrep.NeedSwitchFirst(item))
            } else {
                onResult(DeleteSourcePrep.ReadyToConfirm(item))
            }
        }
    }

    /** Rotate home/lock off [item], then caller may show the normal delete confirm. */
    fun switchAwayFromSource(item: WallpaperItem, onDone: (ok: Boolean, error: String?) -> Unit) {
        viewModelScope.launch {
            val uriStr = item.uri.toString()
            val homeUri = prefs.homeWallpaperUri.first()
            val lockUri = prefs.lockWallpaperUri.first()
            val screens = buildList {
                if (homeUri == uriStr) add(WallpaperTarget.HOME)
                if (lockUri == uriStr) add(WallpaperTarget.LOCK)
            }
            if (screens.isEmpty()) {
                onDone(true, null)
                return@launch
            }
            for (screen in screens) {
                val next = pickReplacementForDelete(item) ?: run {
                    onDone(false, "没有可切换的其他资源")
                    return@launch
                }
                try {
                    wallpaperRepository.setWallpaper(next, screen)
                } catch (e: Exception) {
                    onDone(false, e.message ?: "切换资源失败")
                    return@launch
                }
            }
            // Keep UI "使用中" badge in sync without waiting for prefs Flow.
            _state.update {
                it.copy(
                    homeAppliedUri = prefs.homeWallpaperUri.first(),
                    lockAppliedUri = prefs.lockWallpaperUri.first(),
                    homeIsVideo = prefs.homeIsVideo.first(),
                    lockIsVideo = prefs.lockIsVideo.first()
                )
            }
            onDone(true, null)
        }
    }

    private suspend fun isSourceCurrentlyApplied(item: WallpaperItem): Boolean {
        val uriStr = item.uri.toString()
        return prefs.homeWallpaperUri.first() == uriStr ||
            prefs.lockWallpaperUri.first() == uriStr
    }

    private suspend fun peerItemsForDelete(item: WallpaperItem): List<WallpaperItem> {
        return when (item.type) {
            FolderType.STATIC -> _state.value.staticImages
            FolderType.IMAGE, FolderType.VIDEO -> {
                val folder = item.folderUri
                if (folder.isNotBlank()) {
                    folderRepository.getItemsFromFolder(folder, item.type)
                } else {
                    _state.value.previewItems.filter { it.type == item.type }
                }
            }
        }
    }

    private suspend fun pickReplacementForDelete(item: WallpaperItem): WallpaperItem? {
        val peers = peerItemsForDelete(item).filter { it.uri != item.uri }
        if (peers.isEmpty()) return null
        val folder = item.folderUri
        return if (folder.isNotBlank() && item.type != FolderType.STATIC) {
            shuffleHistoryManager.pickNext(peers, folder, item.type) ?: peers.random()
        } else {
            peers.random()
        }
    }

    /** Deletes the on-disk / SAF source file, related caches, then refreshes the picker grid. */
    fun deleteSourceFile(item: WallpaperItem, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            // Re-check guards so callers cannot bypass UI.
            val peers = peerItemsForDelete(item)
            if (peers.size <= 1) {
                _state.update {
                    it.copy(errorMessage = "至少需要一个文件，请先切换到其他资源后再删除。")
                }
                onDone(false)
                return@launch
            }
            if (isSourceCurrentlyApplied(item)) {
                _state.update { it.copy(errorMessage = "请先切换资源") }
                onDone(false)
                return@launch
            }
            val ok = folderRepository.deleteSourceFile(item)
            if (ok) {
                val uriKey = item.uri.toString()
                VideoPlaybackPositions.clear(uriKey)
                shuffleHistoryManager.forgetUri(uriKey)
                if (item.type == FolderType.STATIC) {
                    staticImageRepository.remove(item.uri)
                }
                runCatching {
                    val loader = SingletonImageLoader.get(context)
                    val cacheKey = "${item.uri}_${item.lastModified}"
                    loader.memoryCache?.remove(MemoryCache.Key(cacheKey))
                    loader.diskCache?.remove(cacheKey)
                }
                _state.update { s ->
                    s.copy(
                        selectedItem = if (s.selectedItem?.uri == item.uri) null else s.selectedItem,
                        previewItems = s.previewItems.filterNot { it.uri == item.uri }
                    )
                }
                loadPreviewItems(clearFirst = false, forceRefresh = true)
                _state.update { it.copy(successMessage = "源文件已删除") }
            } else {
                _state.update { it.copy(errorMessage = "删除源文件失败（无权限或文件不可删）") }
            }
            onDone(ok)
        }
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

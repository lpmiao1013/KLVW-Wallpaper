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
)

@HiltViewModel
class WallpaperViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderRepository: FolderRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val staticImageRepository: StaticImageRepository,
    private val prefs: SettingsPreferences
) : ViewModel() {

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
    }

    fun setMediaTab(tab: MediaTab) {
        if (_state.value.isLoadingPreview && _state.value.mediaTab == tab) return
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
    }

    fun selectItem(item: WallpaperItem) {
        if (_state.value.isLoadingPreview) return
        _state.update { it.copy(selectedItem = item) }
    }

    fun selectFolder(folderUri: String?) {
        if (_state.value.isLoadingPreview) return
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

    fun addStaticImage(uri: Uri, displayName: String) {
        viewModelScope.launch { staticImageRepository.add(uri, displayName) }
    }

    fun removeStaticImage(uri: Uri) {
        viewModelScope.launch { staticImageRepository.remove(uri) }
    }

    fun clearMessage() {
        _state.update { it.copy(successMessage = null, errorMessage = null) }
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

    private fun loadPreviewItems(clearFirst: Boolean = false) {
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
                        s.imageFolders.flatMap { folderRepository.getItemsFromFolder(it.uri, FolderType.IMAGE) }
                    else ->
                        s.imageFolders.find { it.uri == selUri }
                            ?.let { folderRepository.getItemsFromFolder(it.uri, FolderType.IMAGE) }
                            ?: emptyList()
                }
                MediaTab.VIDEO -> when {
                    selUri == null ->
                        s.videoFolders.flatMap { folderRepository.getItemsFromFolder(it.uri, FolderType.VIDEO) }
                    else ->
                        s.videoFolders.find { it.uri == selUri }
                            ?.let { folderRepository.getItemsFromFolder(it.uri, FolderType.VIDEO) }
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

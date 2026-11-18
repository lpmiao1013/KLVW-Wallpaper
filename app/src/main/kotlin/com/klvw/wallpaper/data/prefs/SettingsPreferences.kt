package com.klvw.wallpaper.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.klvw.wallpaper.data.model.FolderType
import com.klvw.wallpaper.data.model.WallpaperTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "klvw_settings")

@Singleton
class SettingsPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val HOME_WALLPAPER_URI = stringPreferencesKey("home_wallpaper_uri")
        val LOCK_WALLPAPER_URI = stringPreferencesKey("lock_wallpaper_uri")
        val HOME_IS_VIDEO = booleanPreferencesKey("home_is_video")
        val LOCK_IS_VIDEO = booleanPreferencesKey("lock_is_video")
        val USE_VULKAN = booleanPreferencesKey("use_vulkan")
        val WALLPAPER_SCALE_MODE = stringPreferencesKey("wallpaper_scale_mode")
        val ROTATE_MISMATCH_ENABLED = booleanPreferencesKey("rotate_mismatch_enabled")
        val ROTATE_MISMATCH_CW = booleanPreferencesKey("rotate_mismatch_cw")
        /** Legacy shared tabbar keys — migrated into HOME_* on read. */
        val ACTIVE_IMAGE_FOLDER_URI = stringPreferencesKey("active_image_folder_uri")
        val ACTIVE_VIDEO_FOLDER_URI = stringPreferencesKey("active_video_folder_uri")
        val ACTIVE_HOME_IMAGE_FOLDER_URI = stringPreferencesKey("active_home_image_folder_uri")
        val ACTIVE_HOME_VIDEO_FOLDER_URI = stringPreferencesKey("active_home_video_folder_uri")
        val ACTIVE_LOCK_IMAGE_FOLDER_URI = stringPreferencesKey("active_lock_image_folder_uri")
        val ACTIVE_LOCK_VIDEO_FOLDER_URI = stringPreferencesKey("active_lock_video_folder_uri")
        val LOCK_SERVICE_ACTIVATED = booleanPreferencesKey("lock_service_activated")
        val FORCE_ICON_COLOR = stringPreferencesKey("force_icon_color")
        val PREV_HOME_WALLPAPER_URI = stringPreferencesKey("prev_home_wallpaper_uri")
        val PREV_HOME_IS_VIDEO = booleanPreferencesKey("prev_home_is_video")
        val PREV_LOCK_WALLPAPER_URI = stringPreferencesKey("prev_lock_wallpaper_uri")
        val PREV_LOCK_IS_VIDEO = booleanPreferencesKey("prev_lock_is_video")
        val TILE_RANDOM_IMAGE_TARGET = stringPreferencesKey("tile_random_image_target")
        val TILE_RANDOM_VIDEO_TARGET = stringPreferencesKey("tile_random_video_target")
        val TILE_RESTORE_TARGET = stringPreferencesKey("tile_restore_target")
        val TILE_STATIC_IMAGE_URI = stringPreferencesKey("tile_static_image_uri")
        val TILE_STATIC_IMAGE_NAME = stringPreferencesKey("tile_static_image_name")
        val TILE_STATIC_IMAGE_TARGET = stringPreferencesKey("tile_static_image_target")
        val TILE_RANDOM_IMAGE_FOLDER_URI = stringPreferencesKey("tile_random_image_folder_uri")
        val TILE_RANDOM_VIDEO_FOLDER_URI = stringPreferencesKey("tile_random_video_folder_uri")
        val HOME_WALLPAPER_IS_LIGHT = booleanPreferencesKey("home_wallpaper_is_light")
        val LOCK_WALLPAPER_IS_LIGHT = booleanPreferencesKey("lock_wallpaper_is_light")
        val POPUP_ITEMS_JSON = stringPreferencesKey("popup_items_json")
        val DEFAULT_HOME_IMAGE_FOLDER_URI = stringPreferencesKey("default_home_image_folder_uri")
        val DEFAULT_HOME_VIDEO_FOLDER_URI = stringPreferencesKey("default_home_video_folder_uri")
        val DEFAULT_LOCK_IMAGE_FOLDER_URI = stringPreferencesKey("default_lock_image_folder_uri")
        val DEFAULT_LOCK_VIDEO_FOLDER_URI = stringPreferencesKey("default_lock_video_folder_uri")
        val POPUP_BG_COLOR = stringPreferencesKey("popup_bg_color")
        val POPUP_PRIMARY_TEXT_COLOR = stringPreferencesKey("popup_primary_text_color")
        val POPUP_SECONDARY_TEXT_COLOR = stringPreferencesKey("popup_secondary_text_color")
        val POPUP_LAYOUT = stringPreferencesKey("popup_layout")
        val POPUP_GRID_COLUMNS = intPreferencesKey("popup_grid_columns")
        val POPUP_WIDTH_FRACTION = floatPreferencesKey("popup_width_fraction")
        val POPUP_AUTO_SCALE = booleanPreferencesKey("popup_auto_scale")
        val POPUP_ITEM_SIZE_DP = intPreferencesKey("popup_item_size_dp")
        val POPUP_SCALE_ICON_COLOR = booleanPreferencesKey("popup_scale_icon_color")
        val POPUP_SCALE_FOLDER_SELECT = booleanPreferencesKey("popup_scale_folder_select")
        val POPUP_SCALE_TIMER = booleanPreferencesKey("popup_scale_timer")
        val POPUP_SCALE_FOLDER_SELECT_ALL = booleanPreferencesKey("popup_scale_folder_select_all")
        // Kill switch
        val APP_ENABLED = booleanPreferencesKey("app_enabled")
        // Quick Set tile config
        val QUICK_SET_HOME_ACTION = stringPreferencesKey("quick_set_home_action")
        val QUICK_SET_LOCK_ACTION = stringPreferencesKey("quick_set_lock_action")
        val QUICK_SET_HOME_STATIC_URI = stringPreferencesKey("quick_set_home_static_uri")
        val QUICK_SET_LOCK_STATIC_URI = stringPreferencesKey("quick_set_lock_static_uri")
        val QUICK_SET_WATCH_PRESET_ID = stringPreferencesKey("quick_set_watch_preset_id")
        // Double-tap on home live wallpaper to advance
        val DOUBLE_TAP_ENABLED = booleanPreferencesKey("double_tap_enabled")
        // Display Control (on-unlock wallpaper cycling) — screen-level; follows current media type.
        val DISPLAY_CONTROL_HOME = booleanPreferencesKey("display_control_home")
        val DISPLAY_CONTROL_LOCK = booleanPreferencesKey("display_control_lock")
        // Legacy image/video unlock toggles (migrated into HOME/LOCK above).
        val DISPLAY_CONTROL_HOME_IMAGE = booleanPreferencesKey("display_control_home_image")
        val DISPLAY_CONTROL_HOME_VIDEO = booleanPreferencesKey("display_control_home_video")
        val DISPLAY_CONTROL_LOCK_IMAGE = booleanPreferencesKey("display_control_lock_image")
        val DISPLAY_CONTROL_LOCK_VIDEO = booleanPreferencesKey("display_control_lock_video")
        val DISPLAY_CONTROL_RESET_HOME_TIMER = booleanPreferencesKey("display_control_reset_home_timer")
        val DISPLAY_CONTROL_RESET_LOCK_TIMER = booleanPreferencesKey("display_control_reset_lock_timer")
        val POPUP_SCALE_DISPLAY_CONTROL = booleanPreferencesKey("popup_scale_display_control")
        // Pujie Watch Face presets
        val PUJIE_WATCH_FACES_JSON = stringPreferencesKey("pujie_watch_faces_json")
        // Timers — screen-level; fire uses current homeIsVideo / lockIsVideo.
        val TIMER_UNLOCK_NOTIFICATION = booleanPreferencesKey("timer_unlock_notification")
        val TIMER_NOTIFICATION_ONLY_WHEN_RUNNING = booleanPreferencesKey("timer_notification_only_when_running")
        val PAUSE_TIMERS_ON_GLOBAL_OFF = booleanPreferencesKey("pause_timers_on_global_off")
        val GLOBAL_OFF_PAUSED_TIMERS = stringPreferencesKey("global_off_paused_timers")
        val KLVW_WATCH_ITEMS_JSON = stringPreferencesKey("klvw_watch_items_json")
        val WATCH_GLOBAL_OFF_VIBRATE = booleanPreferencesKey("watch_global_off_vibrate")
        val HOME_TIMER_ENABLED = booleanPreferencesKey("home_timer_enabled")
        val HOME_TIMER_INTERVAL_MIN = intPreferencesKey("home_timer_interval_min")
        val HOME_TIMER_POL = booleanPreferencesKey("home_timer_pol")
        val LOCK_TIMER_ENABLED = booleanPreferencesKey("lock_timer_enabled")
        val LOCK_TIMER_INTERVAL_MIN = intPreferencesKey("lock_timer_interval_min")
        val LOCK_TIMER_POL = booleanPreferencesKey("lock_timer_pol")
        // Legacy per-media timers (migrated into HOME_/LOCK_ above).
        val HOME_IMAGE_TIMER_ENABLED = booleanPreferencesKey("home_image_timer_enabled")
        val HOME_IMAGE_TIMER_INTERVAL_MIN = intPreferencesKey("home_image_timer_interval_min")
        val HOME_VIDEO_TIMER_ENABLED = booleanPreferencesKey("home_video_timer_enabled")
        val HOME_VIDEO_TIMER_INTERVAL_MIN = intPreferencesKey("home_video_timer_interval_min")
        val LOCK_IMAGE_TIMER_ENABLED = booleanPreferencesKey("lock_image_timer_enabled")
        val LOCK_IMAGE_TIMER_INTERVAL_MIN = intPreferencesKey("lock_image_timer_interval_min")
        val LOCK_VIDEO_TIMER_ENABLED = booleanPreferencesKey("lock_video_timer_enabled")
        val LOCK_VIDEO_TIMER_INTERVAL_MIN = intPreferencesKey("lock_video_timer_interval_min")
        // Aer auto-lock settings
        val AER_UNMOUNT_ON_LOCK = booleanPreferencesKey("aer_unmount_on_lock")
        val AER_AUTO_UNMOUNT_MINUTES = intPreferencesKey("aer_auto_unmount_minutes")
        // Pause on Lock (P.o.L) — legacy per-media; migrated to HOME_TIMER_POL / LOCK_TIMER_POL
        val HOME_IMAGE_TIMER_POL = booleanPreferencesKey("home_image_timer_pol")
        val HOME_VIDEO_TIMER_POL = booleanPreferencesKey("home_video_timer_pol")
        val LOCK_IMAGE_TIMER_POL = booleanPreferencesKey("lock_image_timer_pol")
        val LOCK_VIDEO_TIMER_POL = booleanPreferencesKey("lock_video_timer_pol")
        val POL_PAUSED_TIMERS = stringPreferencesKey("pol_paused_timers")
        val POL_PAUSED_FIRE_TIMES = stringPreferencesKey("pol_paused_fire_times")
        // All currently paused timers (P.o.L + manual/notification) — survives process restart.
        val GENERAL_PAUSED_TIMERS = stringPreferencesKey("general_paused_timers")
        val POPUP_TIMER_HIGHLIGHT_COLOR = stringPreferencesKey("popup_timer_highlight_color")
        val POPUP_TIMER_BORDER_COLOR = stringPreferencesKey("popup_timer_border_color")
        // Daily time-window schedule (folder groups)
        val SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        val SCHEDULE_ACTIVE_GROUP_ID = stringPreferencesKey("schedule_active_group_id")
        val SCHEDULE_GROUPS_JSON = stringPreferencesKey("schedule_groups_json")
        // Video sharpness: power | standard | quality
        val VIDEO_SHARPNESS_MODE = stringPreferencesKey("video_sharpness_mode")
        // Scene preset (highest priority override)
        val PRESET_ENABLED = booleanPreferencesKey("preset_enabled")
        val PRESET_HOME_URI = stringPreferencesKey("preset_home_uri")
        val PRESET_HOME_IS_VIDEO = booleanPreferencesKey("preset_home_is_video")
        /** file | folder */
        val PRESET_HOME_SOURCE = stringPreferencesKey("preset_home_source")
        val PRESET_LOCK_URI = stringPreferencesKey("preset_lock_uri")
        val PRESET_LOCK_IS_VIDEO = booleanPreferencesKey("preset_lock_is_video")
        /** file | folder */
        val PRESET_LOCK_SOURCE = stringPreferencesKey("preset_lock_source")
        val PRESET_HISTORY_JSON = stringPreferencesKey("preset_history_json")
        // When true, privacy-marked folders are hidden from UI (QS tile).
        val FOLDER_HIDE_MODE = booleanPreferencesKey("folder_hide_mode")
        // After About 8-tap: show marked folders even while hide mode is on.
        val SHOW_HIDDEN_FOLDERS = booleanPreferencesKey("show_hidden_folders")
    }

    // Raw flows — distinctUntilChanged so writes to unrelated keys don't trigger re-emission
    val homeWallpaperUri: Flow<String?> = context.dataStore.data.map { it[Keys.HOME_WALLPAPER_URI] }.distinctUntilChanged()
    val lockWallpaperUri: Flow<String?> = context.dataStore.data.map { it[Keys.LOCK_WALLPAPER_URI] }.distinctUntilChanged()
    val homeIsVideo: Flow<Boolean> = context.dataStore.data.map { it[Keys.HOME_IS_VIDEO] ?: false }.distinctUntilChanged()
    val lockIsVideo: Flow<Boolean> = context.dataStore.data.map { it[Keys.LOCK_IS_VIDEO] ?: false }.distinctUntilChanged()
    val useVulkan: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_VULKAN] ?: true }.distinctUntilChanged()
    /** fill | fit | stretch — default fill (legacy behavior). */
    val wallpaperScaleMode: Flow<String> = context.dataStore.data
        .map { it[Keys.WALLPAPER_SCALE_MODE] ?: "fill" }
        .distinctUntilChanged()
    /** When true, rotate media 90° if its orientation mismatches the screen. */
    val rotateMismatchEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.ROTATE_MISMATCH_ENABLED] ?: false }
        .distinctUntilChanged()
    /** true = clockwise, false = counter-clockwise. */
    val rotateMismatchCw: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.ROTATE_MISMATCH_CW] ?: true }
        .distinctUntilChanged()
    val lockServiceActivated: Flow<Boolean> = context.dataStore.data.map { it[Keys.LOCK_SERVICE_ACTIVATED] ?: false }.distinctUntilChanged()
    /** @deprecated Prefer [activeFolderUri] with target; kept for migration/UI summary. */
    val activeImageFolderUri: Flow<String?> = activeFolderUri(
        com.klvw.wallpaper.data.model.WallpaperTarget.HOME,
        com.klvw.wallpaper.data.model.FolderType.IMAGE
    )
    /** @deprecated Prefer [activeFolderUri] with target. */
    val activeVideoFolderUri: Flow<String?> = activeFolderUri(
        com.klvw.wallpaper.data.model.WallpaperTarget.HOME,
        com.klvw.wallpaper.data.model.FolderType.VIDEO
    )

    fun activeFolderUri(
        target: com.klvw.wallpaper.data.model.WallpaperTarget,
        type: com.klvw.wallpaper.data.model.FolderType
    ): Flow<String?> = context.dataStore.data.map { prefs ->
        val primary = when {
            target == com.klvw.wallpaper.data.model.WallpaperTarget.HOME &&
                type == com.klvw.wallpaper.data.model.FolderType.IMAGE ->
                prefs[Keys.ACTIVE_HOME_IMAGE_FOLDER_URI]
            target == com.klvw.wallpaper.data.model.WallpaperTarget.HOME &&
                type == com.klvw.wallpaper.data.model.FolderType.VIDEO ->
                prefs[Keys.ACTIVE_HOME_VIDEO_FOLDER_URI]
            target == com.klvw.wallpaper.data.model.WallpaperTarget.LOCK &&
                type == com.klvw.wallpaper.data.model.FolderType.IMAGE ->
                prefs[Keys.ACTIVE_LOCK_IMAGE_FOLDER_URI]
            target == com.klvw.wallpaper.data.model.WallpaperTarget.LOCK &&
                type == com.klvw.wallpaper.data.model.FolderType.VIDEO ->
                prefs[Keys.ACTIVE_LOCK_VIDEO_FOLDER_URI]
            else -> null
        }
        if (!primary.isNullOrBlank()) return@map primary
        // Migrate legacy shared keys → home only.
        if (target == com.klvw.wallpaper.data.model.WallpaperTarget.HOME) {
            when (type) {
                com.klvw.wallpaper.data.model.FolderType.IMAGE -> prefs[Keys.ACTIVE_IMAGE_FOLDER_URI]
                com.klvw.wallpaper.data.model.FolderType.VIDEO -> prefs[Keys.ACTIVE_VIDEO_FOLDER_URI]
                else -> null
            }
        } else null
    }.distinctUntilChanged()

    fun defaultFolderUri(
        target: com.klvw.wallpaper.data.model.WallpaperTarget,
        type: com.klvw.wallpaper.data.model.FolderType
    ): Flow<String?> = context.dataStore.data.map { prefs ->
        when {
            target == com.klvw.wallpaper.data.model.WallpaperTarget.HOME &&
                type == com.klvw.wallpaper.data.model.FolderType.IMAGE ->
                prefs[Keys.DEFAULT_HOME_IMAGE_FOLDER_URI]
            target == com.klvw.wallpaper.data.model.WallpaperTarget.HOME &&
                type == com.klvw.wallpaper.data.model.FolderType.VIDEO ->
                prefs[Keys.DEFAULT_HOME_VIDEO_FOLDER_URI]
            target == com.klvw.wallpaper.data.model.WallpaperTarget.LOCK &&
                type == com.klvw.wallpaper.data.model.FolderType.IMAGE ->
                prefs[Keys.DEFAULT_LOCK_IMAGE_FOLDER_URI]
            target == com.klvw.wallpaper.data.model.WallpaperTarget.LOCK &&
                type == com.klvw.wallpaper.data.model.FolderType.VIDEO ->
                prefs[Keys.DEFAULT_LOCK_VIDEO_FOLDER_URI]
            else -> null
        }
    }.distinctUntilChanged()
    val forceIconColor: Flow<String> = context.dataStore.data.map { it[Keys.FORCE_ICON_COLOR] ?: "auto" }.distinctUntilChanged()
    val prevHomeWallpaperUri: Flow<String?> = context.dataStore.data.map { it[Keys.PREV_HOME_WALLPAPER_URI] }.distinctUntilChanged()
    val prevHomeIsVideo: Flow<Boolean> = context.dataStore.data.map { it[Keys.PREV_HOME_IS_VIDEO] ?: false }.distinctUntilChanged()
    val prevLockWallpaperUri: Flow<String?> = context.dataStore.data.map { it[Keys.PREV_LOCK_WALLPAPER_URI] }.distinctUntilChanged()
    val prevLockIsVideo: Flow<Boolean> = context.dataStore.data.map { it[Keys.PREV_LOCK_IS_VIDEO] ?: false }.distinctUntilChanged()
    val tileRandomImageTarget: Flow<String> = context.dataStore.data.map { it[Keys.TILE_RANDOM_IMAGE_TARGET] ?: "both" }.distinctUntilChanged()
    val tileRandomVideoTarget: Flow<String> = context.dataStore.data.map { it[Keys.TILE_RANDOM_VIDEO_TARGET] ?: "both" }.distinctUntilChanged()
    val tileRestoreTarget: Flow<String> = context.dataStore.data.map { it[Keys.TILE_RESTORE_TARGET] ?: "both" }.distinctUntilChanged()
    val tileStaticImageUri: Flow<String?> = context.dataStore.data.map { it[Keys.TILE_STATIC_IMAGE_URI] }.distinctUntilChanged()
    val tileStaticImageName: Flow<String> = context.dataStore.data.map { it[Keys.TILE_STATIC_IMAGE_NAME] ?: "" }.distinctUntilChanged()
    val tileStaticImageTarget: Flow<String> = context.dataStore.data.map { it[Keys.TILE_STATIC_IMAGE_TARGET] ?: "both" }.distinctUntilChanged()
    val tileRandomImageFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.TILE_RANDOM_IMAGE_FOLDER_URI] }.distinctUntilChanged()
    val tileRandomVideoFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.TILE_RANDOM_VIDEO_FOLDER_URI] }.distinctUntilChanged()
    val popupItemsJson: Flow<String> = context.dataStore.data.map { it[Keys.POPUP_ITEMS_JSON] ?: "" }.distinctUntilChanged()
    val defaultHomeImageFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.DEFAULT_HOME_IMAGE_FOLDER_URI] }.distinctUntilChanged()
    val defaultHomeVideoFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.DEFAULT_HOME_VIDEO_FOLDER_URI] }.distinctUntilChanged()
    val defaultLockImageFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.DEFAULT_LOCK_IMAGE_FOLDER_URI] }.distinctUntilChanged()
    val defaultLockVideoFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.DEFAULT_LOCK_VIDEO_FOLDER_URI] }.distinctUntilChanged()
    val popupBgColor: Flow<String?> = context.dataStore.data.map { it[Keys.POPUP_BG_COLOR] }.distinctUntilChanged()
    val popupPrimaryTextColor: Flow<String?> = context.dataStore.data.map { it[Keys.POPUP_PRIMARY_TEXT_COLOR] }.distinctUntilChanged()
    val popupSecondaryTextColor: Flow<String?> = context.dataStore.data.map { it[Keys.POPUP_SECONDARY_TEXT_COLOR] }.distinctUntilChanged()
    val popupTimerHighlightColor: Flow<String?> = context.dataStore.data.map { it[Keys.POPUP_TIMER_HIGHLIGHT_COLOR] }.distinctUntilChanged()
    val popupTimerBorderColor: Flow<String?> = context.dataStore.data.map { it[Keys.POPUP_TIMER_BORDER_COLOR] }.distinctUntilChanged()
    val scheduleEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SCHEDULE_ENABLED] ?: false }.distinctUntilChanged()
    val scheduleActiveGroupId: Flow<String?> = context.dataStore.data.map { it[Keys.SCHEDULE_ACTIVE_GROUP_ID] }.distinctUntilChanged()
    val scheduleGroupsJson: Flow<String> = context.dataStore.data.map { it[Keys.SCHEDULE_GROUPS_JSON] ?: "[]" }.distinctUntilChanged()
    /** power | standard | quality — default standard. */
    val videoSharpnessMode: Flow<String> = context.dataStore.data
        .map { it[Keys.VIDEO_SHARPNESS_MODE] ?: "standard" }
        .distinctUntilChanged()
    val presetEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.PRESET_ENABLED] ?: false }
        .distinctUntilChanged()
    val presetHomeUri: Flow<String?> = context.dataStore.data
        .map { it[Keys.PRESET_HOME_URI] }
        .distinctUntilChanged()
    val presetHomeIsVideo: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.PRESET_HOME_IS_VIDEO] ?: false }
        .distinctUntilChanged()
    val presetHomeSource: Flow<String> = context.dataStore.data
        .map { it[Keys.PRESET_HOME_SOURCE] ?: "file" }
        .distinctUntilChanged()
    val presetLockUri: Flow<String?> = context.dataStore.data
        .map { it[Keys.PRESET_LOCK_URI] }
        .distinctUntilChanged()
    val presetLockIsVideo: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.PRESET_LOCK_IS_VIDEO] ?: false }
        .distinctUntilChanged()
    val presetLockSource: Flow<String> = context.dataStore.data
        .map { it[Keys.PRESET_LOCK_SOURCE] ?: "file" }
        .distinctUntilChanged()
    val presetHistoryJson: Flow<String?> = context.dataStore.data
        .map { it[Keys.PRESET_HISTORY_JSON] }
        .distinctUntilChanged()
    val folderHideMode: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.FOLDER_HIDE_MODE] ?: false }
        .distinctUntilChanged()
    val showHiddenFolders: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.SHOW_HIDDEN_FOLDERS] ?: false }
        .distinctUntilChanged()
    val popupLayout: Flow<String> = context.dataStore.data.map { it[Keys.POPUP_LAYOUT] ?: "list" }.distinctUntilChanged()
    val popupGridColumns: Flow<Int> = context.dataStore.data.map { it[Keys.POPUP_GRID_COLUMNS] ?: 3 }.distinctUntilChanged()
    val popupWidthFraction: Flow<Float> = context.dataStore.data.map { it[Keys.POPUP_WIDTH_FRACTION] ?: 0.92f }.distinctUntilChanged()
    val popupAutoScale: Flow<Boolean> = context.dataStore.data.map { it[Keys.POPUP_AUTO_SCALE] ?: true }.distinctUntilChanged()
    val popupItemSizeDp: Flow<Int> = context.dataStore.data.map { it[Keys.POPUP_ITEM_SIZE_DP] ?: 72 }.distinctUntilChanged()
    val popupScaleIconColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.POPUP_SCALE_ICON_COLOR] ?: true }.distinctUntilChanged()
    val popupScaleFolderSelect: Flow<Boolean> = context.dataStore.data.map { it[Keys.POPUP_SCALE_FOLDER_SELECT] ?: true }.distinctUntilChanged()
    val popupScaleTimer: Flow<Boolean> = context.dataStore.data.map { it[Keys.POPUP_SCALE_TIMER] ?: true }.distinctUntilChanged()
    val popupScaleFolderSelectAll: Flow<Boolean> = context.dataStore.data.map { it[Keys.POPUP_SCALE_FOLDER_SELECT_ALL] ?: true }.distinctUntilChanged()
    val doubleTapEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.DOUBLE_TAP_ENABLED] ?: false }.distinctUntilChanged()
    /** Screen-level unlock rotate (follows current media type). Legacy image|video OR-migrated. */
    val displayControlHome: Flow<Boolean> = context.dataStore.data.map { p ->
        p[Keys.DISPLAY_CONTROL_HOME]
            ?: ((p[Keys.DISPLAY_CONTROL_HOME_IMAGE] == true) || (p[Keys.DISPLAY_CONTROL_HOME_VIDEO] == true))
    }.distinctUntilChanged()
    val displayControlLock: Flow<Boolean> = context.dataStore.data.map { p ->
        p[Keys.DISPLAY_CONTROL_LOCK]
            ?: ((p[Keys.DISPLAY_CONTROL_LOCK_IMAGE] == true) || (p[Keys.DISPLAY_CONTROL_LOCK_VIDEO] == true))
    }.distinctUntilChanged()
    val displayControlResetHomeTimer: Flow<Boolean> = context.dataStore.data.map { it[Keys.DISPLAY_CONTROL_RESET_HOME_TIMER] ?: false }.distinctUntilChanged()
    val displayControlResetLockTimer: Flow<Boolean> = context.dataStore.data.map { it[Keys.DISPLAY_CONTROL_RESET_LOCK_TIMER] ?: false }.distinctUntilChanged()
    val popupScaleDisplayControl: Flow<Boolean> = context.dataStore.data.map { it[Keys.POPUP_SCALE_DISPLAY_CONTROL] ?: true }.distinctUntilChanged()
    val appEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.APP_ENABLED] ?: true }.distinctUntilChanged()
    val quickSetHomeAction: Flow<String> = context.dataStore.data.map { it[Keys.QUICK_SET_HOME_ACTION] ?: "random_image" }.distinctUntilChanged()
    val quickSetLockAction: Flow<String> = context.dataStore.data.map { it[Keys.QUICK_SET_LOCK_ACTION] ?: "random_image" }.distinctUntilChanged()
    val quickSetHomeStaticUri: Flow<String?> = context.dataStore.data.map { it[Keys.QUICK_SET_HOME_STATIC_URI] }.distinctUntilChanged()
    val quickSetLockStaticUri: Flow<String?> = context.dataStore.data.map { it[Keys.QUICK_SET_LOCK_STATIC_URI] }.distinctUntilChanged()
    val quickSetWatchPresetId: Flow<String?> = context.dataStore.data.map { it[Keys.QUICK_SET_WATCH_PRESET_ID] }.distinctUntilChanged()
    val timerUnlockNotification: Flow<Boolean> = context.dataStore.data.map { it[Keys.TIMER_UNLOCK_NOTIFICATION] ?: false }.distinctUntilChanged()
    val timerNotificationOnlyWhenRunning: Flow<Boolean> = context.dataStore.data.map { it[Keys.TIMER_NOTIFICATION_ONLY_WHEN_RUNNING] ?: false }.distinctUntilChanged()
    val pauseTimersOnGlobalOff: Flow<Boolean> = context.dataStore.data.map { it[Keys.PAUSE_TIMERS_ON_GLOBAL_OFF] ?: false }.distinctUntilChanged()
    val globalOffPausedTimers: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.GLOBAL_OFF_PAUSED_TIMERS]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    }.distinctUntilChanged()
    val klvwWatchItemsJson: Flow<String> = context.dataStore.data
        .map { it[Keys.KLVW_WATCH_ITEMS_JSON] ?: "[]" }.distinctUntilChanged()
    val watchGlobalOffVibrate: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.WATCH_GLOBAL_OFF_VIBRATE] ?: false }.distinctUntilChanged()
    val homeTimerEnabled: Flow<Boolean> = context.dataStore.data.map { p ->
        p[Keys.HOME_TIMER_ENABLED]
            ?: ((p[Keys.HOME_IMAGE_TIMER_ENABLED] == true) || (p[Keys.HOME_VIDEO_TIMER_ENABLED] == true))
    }.distinctUntilChanged()
    val homeTimerIntervalMin: Flow<Int> = context.dataStore.data.map { p ->
        p[Keys.HOME_TIMER_INTERVAL_MIN]
            ?: when {
                p[Keys.HOME_VIDEO_TIMER_ENABLED] == true -> p[Keys.HOME_VIDEO_TIMER_INTERVAL_MIN]
                else -> p[Keys.HOME_IMAGE_TIMER_INTERVAL_MIN]
            }
            ?: 60
    }.distinctUntilChanged()
    val homeTimerPauseOnLock: Flow<Boolean> = context.dataStore.data.map { p ->
        p[Keys.HOME_TIMER_POL]
            ?: ((p[Keys.HOME_IMAGE_TIMER_POL] == true) || (p[Keys.HOME_VIDEO_TIMER_POL] == true))
    }.distinctUntilChanged()
    val lockTimerEnabled: Flow<Boolean> = context.dataStore.data.map { p ->
        p[Keys.LOCK_TIMER_ENABLED]
            ?: ((p[Keys.LOCK_IMAGE_TIMER_ENABLED] == true) || (p[Keys.LOCK_VIDEO_TIMER_ENABLED] == true))
    }.distinctUntilChanged()
    val lockTimerIntervalMin: Flow<Int> = context.dataStore.data.map { p ->
        p[Keys.LOCK_TIMER_INTERVAL_MIN]
            ?: when {
                p[Keys.LOCK_VIDEO_TIMER_ENABLED] == true -> p[Keys.LOCK_VIDEO_TIMER_INTERVAL_MIN]
                else -> p[Keys.LOCK_IMAGE_TIMER_INTERVAL_MIN]
            }
            ?: 60
    }.distinctUntilChanged()
    val lockTimerPauseOnLock: Flow<Boolean> = context.dataStore.data.map { p ->
        p[Keys.LOCK_TIMER_POL]
            ?: ((p[Keys.LOCK_IMAGE_TIMER_POL] == true) || (p[Keys.LOCK_VIDEO_TIMER_POL] == true))
    }.distinctUntilChanged()
    val pujieWatchFacesJson: Flow<String> = context.dataStore.data.map { it[Keys.PUJIE_WATCH_FACES_JSON] ?: "[]" }.distinctUntilChanged()
    val aerUnmountOnLock: Flow<Boolean> = context.dataStore.data.map { it[Keys.AER_UNMOUNT_ON_LOCK] ?: false }.distinctUntilChanged()
    val aerAutoUnmountMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.AER_AUTO_UNMOUNT_MINUTES] ?: 0 }.distinctUntilChanged()
    val polPausedTimers: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.POL_PAUSED_TIMERS]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    }.distinctUntilChanged()

    val polPausedFireTimes: Flow<Map<String, Long>> = context.dataStore.data.map { prefs ->
        prefs[Keys.POL_PAUSED_FIRE_TIMES]?.split(",")?.filter { it.isNotEmpty() }
            ?.mapNotNull { entry ->
                val i = entry.lastIndexOf(':')
                if (i > 0) entry.substring(0, i) to (entry.substring(i + 1).toLongOrNull() ?: return@mapNotNull null)
                else null
            }?.toMap() ?: emptyMap()
    }.distinctUntilChanged()

    val generalPausedTimers: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.GENERAL_PAUSED_TIMERS]?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    }.distinctUntilChanged()

    // Long-lived scope backed by the singleton's lifetime — safe because this is a @Singleton
    private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Combined StateFlow for the home wallpaper used by the live wallpaper service.
     * Using StateFlow (via stateIn) instead of combine+collectLatest in the service avoids
     * intermediate emissions and complex coroutine-cancellation races.
     */
    // Cached last-computed brightness for each wallpaper — stored so onComputeColors() can answer
    // correctly on the first call, before loadColorsAsync() has had a chance to run.
    val homeWallpaperIsLightCache: StateFlow<Boolean?> = context.dataStore.data
        .map { it[Keys.HOME_WALLPAPER_IS_LIGHT] }.distinctUntilChanged()
        .stateIn(stateScope, SharingStarted.Eagerly, null)

    val lockWallpaperIsLightCache: StateFlow<Boolean?> = context.dataStore.data
        .map { it[Keys.LOCK_WALLPAPER_IS_LIGHT] }.distinctUntilChanged()
        .stateIn(stateScope, SharingStarted.Eagerly, null)

    val homeWallpaperState: StateFlow<Pair<String?, Boolean>> =
        homeWallpaperUri.combine(homeIsVideo) { uri, isVid -> Pair(uri, isVid) }
            .stateIn(stateScope, SharingStarted.Eagerly, Pair(null, false))

    /**
     * Combined StateFlow for the lock wallpaper.
     * Falls back to the home wallpaper when no explicit lock URI is configured.
     */
    val lockWallpaperState: StateFlow<Pair<String?, Boolean>> =
        combine(lockWallpaperUri, lockIsVideo, homeWallpaperUri, homeIsVideo) { lockUri, lockIsVid, homeUri, homeIsVid ->
            if (lockUri != null) Pair(lockUri, lockIsVid) else Pair(homeUri, homeIsVid)
        }.stateIn(stateScope, SharingStarted.Eagerly, Pair(null, false))

    suspend fun setHomeWallpaper(uri: String, isVideo: Boolean) {
        context.dataStore.edit {
            it[Keys.HOME_WALLPAPER_URI] = uri
            it[Keys.HOME_IS_VIDEO] = isVideo
        }
    }

    suspend fun setLockWallpaper(uri: String, isVideo: Boolean) {
        context.dataStore.edit {
            it[Keys.LOCK_WALLPAPER_URI] = uri
            it[Keys.LOCK_IS_VIDEO] = isVideo
        }
    }

    suspend fun setLockServiceActivated(active: Boolean) {
        context.dataStore.edit { it[Keys.LOCK_SERVICE_ACTIVATED] = active }
    }

    suspend fun setUseVulkan(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_VULKAN] = enabled }
    }

    suspend fun setWallpaperScaleMode(value: String) {
        context.dataStore.edit { it[Keys.WALLPAPER_SCALE_MODE] = value }
    }

    suspend fun setRotateMismatchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ROTATE_MISMATCH_ENABLED] = enabled }
    }

    suspend fun setRotateMismatchCw(clockwise: Boolean) {
        context.dataStore.edit { it[Keys.ROTATE_MISMATCH_CW] = clockwise }
    }

    suspend fun setActiveImageFolder(uri: String?) {
        setActiveFolder(
            com.klvw.wallpaper.data.model.WallpaperTarget.HOME,
            com.klvw.wallpaper.data.model.FolderType.IMAGE,
            uri
        )
    }

    suspend fun setActiveVideoFolder(uri: String?) {
        setActiveFolder(
            com.klvw.wallpaper.data.model.WallpaperTarget.HOME,
            com.klvw.wallpaper.data.model.FolderType.VIDEO,
            uri
        )
    }

    suspend fun setActiveFolder(
        target: com.klvw.wallpaper.data.model.WallpaperTarget,
        type: com.klvw.wallpaper.data.model.FolderType,
        uri: String?
    ) {
        context.dataStore.edit { prefs ->
            val key = when {
                target == com.klvw.wallpaper.data.model.WallpaperTarget.HOME &&
                    type == com.klvw.wallpaper.data.model.FolderType.IMAGE ->
                    Keys.ACTIVE_HOME_IMAGE_FOLDER_URI
                target == com.klvw.wallpaper.data.model.WallpaperTarget.HOME &&
                    type == com.klvw.wallpaper.data.model.FolderType.VIDEO ->
                    Keys.ACTIVE_HOME_VIDEO_FOLDER_URI
                target == com.klvw.wallpaper.data.model.WallpaperTarget.LOCK &&
                    type == com.klvw.wallpaper.data.model.FolderType.IMAGE ->
                    Keys.ACTIVE_LOCK_IMAGE_FOLDER_URI
                target == com.klvw.wallpaper.data.model.WallpaperTarget.LOCK &&
                    type == com.klvw.wallpaper.data.model.FolderType.VIDEO ->
                    Keys.ACTIVE_LOCK_VIDEO_FOLDER_URI
                else -> null
            } ?: return@edit
            if (uri.isNullOrBlank()) prefs.remove(key) else prefs[key] = uri
            // Clear legacy shared keys once per-target home is written.
            if (target == com.klvw.wallpaper.data.model.WallpaperTarget.HOME) {
                when (type) {
                    com.klvw.wallpaper.data.model.FolderType.IMAGE ->
                        prefs.remove(Keys.ACTIVE_IMAGE_FOLDER_URI)
                    com.klvw.wallpaper.data.model.FolderType.VIDEO ->
                        prefs.remove(Keys.ACTIVE_VIDEO_FOLDER_URI)
                    else -> Unit
                }
            }
        }
    }

    suspend fun setForceIconColor(value: String) {
        context.dataStore.edit { it[Keys.FORCE_ICON_COLOR] = value }
    }

    suspend fun savePreviousHome(uri: String, isVideo: Boolean) {
        context.dataStore.edit {
            it[Keys.PREV_HOME_WALLPAPER_URI] = uri
            it[Keys.PREV_HOME_IS_VIDEO] = isVideo
        }
    }

    suspend fun savePreviousLock(uri: String, isVideo: Boolean) {
        context.dataStore.edit {
            it[Keys.PREV_LOCK_WALLPAPER_URI] = uri
            it[Keys.PREV_LOCK_IS_VIDEO] = isVideo
        }
    }

    suspend fun setTileRandomImageTarget(value: String) {
        context.dataStore.edit { it[Keys.TILE_RANDOM_IMAGE_TARGET] = value }
    }

    suspend fun setTileRandomVideoTarget(value: String) {
        context.dataStore.edit { it[Keys.TILE_RANDOM_VIDEO_TARGET] = value }
    }

    suspend fun setTileRestoreTarget(value: String) {
        context.dataStore.edit { it[Keys.TILE_RESTORE_TARGET] = value }
    }

    suspend fun setTileStaticImage(uri: String?, name: String) {
        context.dataStore.edit {
            if (uri != null) {
                it[Keys.TILE_STATIC_IMAGE_URI] = uri
                it[Keys.TILE_STATIC_IMAGE_NAME] = name
            } else {
                it.remove(Keys.TILE_STATIC_IMAGE_URI)
                it.remove(Keys.TILE_STATIC_IMAGE_NAME)
            }
        }
    }

    suspend fun setTileStaticImageTarget(value: String) {
        context.dataStore.edit { it[Keys.TILE_STATIC_IMAGE_TARGET] = value }
    }

    suspend fun setHomeWallpaperIsLightCache(isLight: Boolean) {
        context.dataStore.edit { it[Keys.HOME_WALLPAPER_IS_LIGHT] = isLight }
    }

    suspend fun setLockWallpaperIsLightCache(isLight: Boolean) {
        context.dataStore.edit { it[Keys.LOCK_WALLPAPER_IS_LIGHT] = isLight }
    }

    suspend fun setTileRandomImageFolder(uri: String?) {
        context.dataStore.edit {
            if (uri != null) it[Keys.TILE_RANDOM_IMAGE_FOLDER_URI] = uri
            else it.remove(Keys.TILE_RANDOM_IMAGE_FOLDER_URI)
        }
    }

    suspend fun setTileRandomVideoFolder(uri: String?) {
        context.dataStore.edit {
            if (uri != null) it[Keys.TILE_RANDOM_VIDEO_FOLDER_URI] = uri
            else it.remove(Keys.TILE_RANDOM_VIDEO_FOLDER_URI)
        }
    }

    suspend fun setPopupItemsJson(json: String) {
        context.dataStore.edit { it[Keys.POPUP_ITEMS_JSON] = json }
    }

    suspend fun setPopupBgColor(hex: String?) {
        context.dataStore.edit { if (hex != null) it[Keys.POPUP_BG_COLOR] = hex else it.remove(Keys.POPUP_BG_COLOR) }
    }

    suspend fun setPopupPrimaryTextColor(hex: String?) {
        context.dataStore.edit { if (hex != null) it[Keys.POPUP_PRIMARY_TEXT_COLOR] = hex else it.remove(Keys.POPUP_PRIMARY_TEXT_COLOR) }
    }

    suspend fun setPopupSecondaryTextColor(hex: String?) {
        context.dataStore.edit { if (hex != null) it[Keys.POPUP_SECONDARY_TEXT_COLOR] = hex else it.remove(Keys.POPUP_SECONDARY_TEXT_COLOR) }
    }

    suspend fun setPopupTimerHighlightColor(hex: String?) {
        context.dataStore.edit { if (hex != null) it[Keys.POPUP_TIMER_HIGHLIGHT_COLOR] = hex else it.remove(Keys.POPUP_TIMER_HIGHLIGHT_COLOR) }
    }

    suspend fun setPopupTimerBorderColor(hex: String?) {
        context.dataStore.edit { if (hex != null) it[Keys.POPUP_TIMER_BORDER_COLOR] = hex else it.remove(Keys.POPUP_TIMER_BORDER_COLOR) }
    }

    suspend fun setPopupLayout(layout: String) {
        context.dataStore.edit { it[Keys.POPUP_LAYOUT] = layout }
    }

    suspend fun setPopupGridColumns(columns: Int) {
        context.dataStore.edit { it[Keys.POPUP_GRID_COLUMNS] = columns }
    }

    suspend fun setPopupWidthFraction(fraction: Float) {
        context.dataStore.edit { it[Keys.POPUP_WIDTH_FRACTION] = fraction }
    }

    suspend fun setPopupAutoScale(auto: Boolean) {
        context.dataStore.edit { it[Keys.POPUP_AUTO_SCALE] = auto }
    }

    suspend fun setPopupItemSizeDp(sizeDp: Int) {
        context.dataStore.edit { it[Keys.POPUP_ITEM_SIZE_DP] = sizeDp }
    }

    suspend fun setPopupScaleIconColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.POPUP_SCALE_ICON_COLOR] = enabled }
    }

    suspend fun setPopupScaleFolderSelect(enabled: Boolean) {
        context.dataStore.edit { it[Keys.POPUP_SCALE_FOLDER_SELECT] = enabled }
    }

    suspend fun setPopupScaleTimer(enabled: Boolean) {
        context.dataStore.edit { it[Keys.POPUP_SCALE_TIMER] = enabled }
    }

    suspend fun setPopupScaleFolderSelectAll(enabled: Boolean) {
        context.dataStore.edit { it[Keys.POPUP_SCALE_FOLDER_SELECT_ALL] = enabled }
    }

    suspend fun setDoubleTapEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DOUBLE_TAP_ENABLED] = enabled }
    }

    suspend fun setDisplayControlResetHomeTimer(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DISPLAY_CONTROL_RESET_HOME_TIMER] = enabled }
    }

    suspend fun setDisplayControlResetLockTimer(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DISPLAY_CONTROL_RESET_LOCK_TIMER] = enabled }
    }

    /**
     * Unlock Display Control — screen-level (`home` / `lock`).
     * Legacy `home_image` etc. map to the same screen flag.
     */
    suspend fun setDisplayControl(key: String, enabled: Boolean) {
        val screen = when (key) {
            "home", "home_image", "home_video" -> "home"
            "lock", "lock_image", "lock_video" -> "lock"
            else -> return
        }
        context.dataStore.edit {
            if (screen == "home") {
                it[Keys.DISPLAY_CONTROL_HOME] = enabled
                it.remove(Keys.DISPLAY_CONTROL_HOME_IMAGE)
                it.remove(Keys.DISPLAY_CONTROL_HOME_VIDEO)
            } else {
                it[Keys.DISPLAY_CONTROL_LOCK] = enabled
                it.remove(Keys.DISPLAY_CONTROL_LOCK_IMAGE)
                it.remove(Keys.DISPLAY_CONTROL_LOCK_VIDEO)
            }
        }
    }

    /**
     * Migrate legacy image/video unlock+timer flags into screen-level keys.
     * Safe to call on every process start.
     */
    suspend fun reconcileImageVideoMutex() {
        context.dataStore.edit {
            if (it[Keys.DISPLAY_CONTROL_HOME] == null) {
                val on = (it[Keys.DISPLAY_CONTROL_HOME_IMAGE] == true) ||
                    (it[Keys.DISPLAY_CONTROL_HOME_VIDEO] == true)
                it[Keys.DISPLAY_CONTROL_HOME] = on
            }
            if (it[Keys.DISPLAY_CONTROL_LOCK] == null) {
                val on = (it[Keys.DISPLAY_CONTROL_LOCK_IMAGE] == true) ||
                    (it[Keys.DISPLAY_CONTROL_LOCK_VIDEO] == true)
                it[Keys.DISPLAY_CONTROL_LOCK] = on
            }
            if (it[Keys.HOME_TIMER_ENABLED] == null) {
                val img = it[Keys.HOME_IMAGE_TIMER_ENABLED] == true
                val vid = it[Keys.HOME_VIDEO_TIMER_ENABLED] == true
                it[Keys.HOME_TIMER_ENABLED] = img || vid
                if (it[Keys.HOME_TIMER_INTERVAL_MIN] == null) {
                    it[Keys.HOME_TIMER_INTERVAL_MIN] = when {
                        vid -> it[Keys.HOME_VIDEO_TIMER_INTERVAL_MIN] ?: 60
                        else -> it[Keys.HOME_IMAGE_TIMER_INTERVAL_MIN] ?: 60
                    }
                }
                if (it[Keys.HOME_TIMER_POL] == null) {
                    it[Keys.HOME_TIMER_POL] =
                        (it[Keys.HOME_IMAGE_TIMER_POL] == true) || (it[Keys.HOME_VIDEO_TIMER_POL] == true)
                }
            }
            if (it[Keys.LOCK_TIMER_ENABLED] == null) {
                val img = it[Keys.LOCK_IMAGE_TIMER_ENABLED] == true
                val vid = it[Keys.LOCK_VIDEO_TIMER_ENABLED] == true
                it[Keys.LOCK_TIMER_ENABLED] = img || vid
                if (it[Keys.LOCK_TIMER_INTERVAL_MIN] == null) {
                    it[Keys.LOCK_TIMER_INTERVAL_MIN] = when {
                        vid -> it[Keys.LOCK_VIDEO_TIMER_INTERVAL_MIN] ?: 60
                        else -> it[Keys.LOCK_IMAGE_TIMER_INTERVAL_MIN] ?: 60
                    }
                }
                if (it[Keys.LOCK_TIMER_POL] == null) {
                    it[Keys.LOCK_TIMER_POL] =
                        (it[Keys.LOCK_IMAGE_TIMER_POL] == true) || (it[Keys.LOCK_VIDEO_TIMER_POL] == true)
                }
            }
        }
    }

    suspend fun setPopupScaleDisplayControl(enabled: Boolean) {
        context.dataStore.edit { it[Keys.POPUP_SCALE_DISPLAY_CONTROL] = enabled }
    }

    suspend fun setAppEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.APP_ENABLED] = enabled }
    }

    suspend fun setQuickSetHomeAction(action: String) {
        context.dataStore.edit { it[Keys.QUICK_SET_HOME_ACTION] = action }
    }

    suspend fun setQuickSetLockAction(action: String) {
        context.dataStore.edit { it[Keys.QUICK_SET_LOCK_ACTION] = action }
    }

    suspend fun setQuickSetHomeStaticUri(uri: String?) {
        context.dataStore.edit {
            if (uri != null) it[Keys.QUICK_SET_HOME_STATIC_URI] = uri
            else it.remove(Keys.QUICK_SET_HOME_STATIC_URI)
        }
    }

    suspend fun setQuickSetLockStaticUri(uri: String?) {
        context.dataStore.edit {
            if (uri != null) it[Keys.QUICK_SET_LOCK_STATIC_URI] = uri
            else it.remove(Keys.QUICK_SET_LOCK_STATIC_URI)
        }
    }

    suspend fun setQuickSetWatchPresetId(id: String?) {
        context.dataStore.edit {
            if (id != null) it[Keys.QUICK_SET_WATCH_PRESET_ID] = id
            else it.remove(Keys.QUICK_SET_WATCH_PRESET_ID)
        }
    }

    suspend fun setTimerUnlockNotification(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TIMER_UNLOCK_NOTIFICATION] = enabled }
    }

    suspend fun setTimerNotificationOnlyWhenRunning(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TIMER_NOTIFICATION_ONLY_WHEN_RUNNING] = enabled }
    }

    suspend fun setPauseTimersOnGlobalOff(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PAUSE_TIMERS_ON_GLOBAL_OFF] = enabled }
    }

    suspend fun setGlobalOffPausedTimers(keys: Set<String>) {
        context.dataStore.edit {
            if (keys.isEmpty()) it.remove(Keys.GLOBAL_OFF_PAUSED_TIMERS)
            else it[Keys.GLOBAL_OFF_PAUSED_TIMERS] = keys.joinToString(",")
        }
    }

    suspend fun setKlvwWatchItemsJson(json: String) {
        context.dataStore.edit { it[Keys.KLVW_WATCH_ITEMS_JSON] = json }
    }

    suspend fun setWatchGlobalOffVibrate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WATCH_GLOBAL_OFF_VIBRATE] = enabled }
    }

    /** Timers are screen-level (`home` / `lock`); media type is chosen at fire time. */
    suspend fun setTimerEnabled(key: String, enabled: Boolean) {
        val screen = when (key) {
            "home", "home_image", "home_video" -> "home"
            "lock", "lock_image", "lock_video" -> "lock"
            else -> return
        }
        context.dataStore.edit {
            if (screen == "home") {
                it[Keys.HOME_TIMER_ENABLED] = enabled
                it.remove(Keys.HOME_IMAGE_TIMER_ENABLED)
                it.remove(Keys.HOME_VIDEO_TIMER_ENABLED)
            } else {
                it[Keys.LOCK_TIMER_ENABLED] = enabled
                it.remove(Keys.LOCK_IMAGE_TIMER_ENABLED)
                it.remove(Keys.LOCK_VIDEO_TIMER_ENABLED)
            }
        }
    }

    suspend fun setTimerInterval(key: String, minutes: Int) {
        val screen = when (key) {
            "home", "home_image", "home_video" -> "home"
            "lock", "lock_image", "lock_video" -> "lock"
            else -> return
        }
        context.dataStore.edit {
            if (screen == "home") it[Keys.HOME_TIMER_INTERVAL_MIN] = minutes
            else it[Keys.LOCK_TIMER_INTERVAL_MIN] = minutes
        }
    }

    suspend fun setPujieWatchFacesJson(json: String) {
        context.dataStore.edit { it[Keys.PUJIE_WATCH_FACES_JSON] = json }
    }

    suspend fun setAerUnmountOnLock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AER_UNMOUNT_ON_LOCK] = enabled }
    }

    suspend fun setAerAutoUnmountMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.AER_AUTO_UNMOUNT_MINUTES] = minutes }
    }

    suspend fun setTimerPauseOnLock(key: String, enabled: Boolean) {
        val screen = when (key) {
            "home", "home_image", "home_video" -> "home"
            "lock", "lock_image", "lock_video" -> "lock"
            else -> return
        }
        context.dataStore.edit {
            if (screen == "home") {
                it[Keys.HOME_TIMER_POL] = enabled
                it.remove(Keys.HOME_IMAGE_TIMER_POL)
                it.remove(Keys.HOME_VIDEO_TIMER_POL)
            } else {
                it[Keys.LOCK_TIMER_POL] = enabled
                it.remove(Keys.LOCK_IMAGE_TIMER_POL)
                it.remove(Keys.LOCK_VIDEO_TIMER_POL)
            }
        }
    }

    suspend fun setPolPausedTimers(keys: Set<String>) {
        context.dataStore.edit {
            if (keys.isEmpty()) it.remove(Keys.POL_PAUSED_TIMERS)
            else it[Keys.POL_PAUSED_TIMERS] = keys.joinToString(",")
        }
    }

    suspend fun setPolPausedFireTimes(map: Map<String, Long>) {
        context.dataStore.edit {
            if (map.isEmpty()) it.remove(Keys.POL_PAUSED_FIRE_TIMES)
            else it[Keys.POL_PAUSED_FIRE_TIMES] = map.entries.joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    suspend fun addGeneralPausedTimer(key: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.GENERAL_PAUSED_TIMERS]?.split(",")?.filter { it.isNotEmpty() }?.toMutableSet() ?: mutableSetOf()
            current.add(key)
            prefs[Keys.GENERAL_PAUSED_TIMERS] = current.joinToString(",")
        }
    }

    suspend fun removeGeneralPausedTimer(key: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.GENERAL_PAUSED_TIMERS]?.split(",")?.filter { it.isNotEmpty() }?.toMutableSet() ?: mutableSetOf()
            current.remove(key)
            if (current.isEmpty()) prefs.remove(Keys.GENERAL_PAUSED_TIMERS)
            else prefs[Keys.GENERAL_PAUSED_TIMERS] = current.joinToString(",")
        }
    }

    suspend fun setDefaultFolderUri(target: String, mediaType: String, uri: String?) {
        // While the daily schedule is on, default folder URIs stay frozen.
        context.dataStore.edit { prefs ->
            if (prefs[Keys.SCHEDULE_ENABLED] == true) return@edit
            fun set(key: androidx.datastore.preferences.core.Preferences.Key<String>) {
                if (uri != null) prefs[key] = uri else prefs.remove(key)
            }
            when (mediaType) {
                "image" -> when (target) {
                    "home" -> set(Keys.DEFAULT_HOME_IMAGE_FOLDER_URI)
                    "lock" -> set(Keys.DEFAULT_LOCK_IMAGE_FOLDER_URI)
                    else   -> { set(Keys.DEFAULT_HOME_IMAGE_FOLDER_URI); set(Keys.DEFAULT_LOCK_IMAGE_FOLDER_URI) }
                }
                else -> when (target) {
                    "home" -> set(Keys.DEFAULT_HOME_VIDEO_FOLDER_URI)
                    "lock" -> set(Keys.DEFAULT_LOCK_VIDEO_FOLDER_URI)
                    else   -> { set(Keys.DEFAULT_HOME_VIDEO_FOLDER_URI); set(Keys.DEFAULT_LOCK_VIDEO_FOLDER_URI) }
                }
            }
        }
    }

    suspend fun setScheduleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SCHEDULE_ENABLED] = enabled }
    }

    suspend fun setScheduleActiveGroupId(id: String?) {
        context.dataStore.edit {
            if (id.isNullOrBlank()) it.remove(Keys.SCHEDULE_ACTIVE_GROUP_ID)
            else it[Keys.SCHEDULE_ACTIVE_GROUP_ID] = id
        }
    }

    suspend fun setVideoSharpnessMode(value: String) {
        context.dataStore.edit { it[Keys.VIDEO_SHARPNESS_MODE] = value }
    }

    suspend fun setScheduleGroupsJson(json: String) {
        context.dataStore.edit { it[Keys.SCHEDULE_GROUPS_JSON] = json }
    }

    suspend fun setPresetEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PRESET_ENABLED] = enabled }
    }

    suspend fun setPresetHome(uri: String?, isVideo: Boolean, source: String? = null) {
        context.dataStore.edit {
            if (uri.isNullOrBlank()) it.remove(Keys.PRESET_HOME_URI)
            else it[Keys.PRESET_HOME_URI] = uri
            it[Keys.PRESET_HOME_IS_VIDEO] = isVideo
            if (source != null) it[Keys.PRESET_HOME_SOURCE] = source
        }
    }

    suspend fun setPresetLock(uri: String?, isVideo: Boolean, source: String? = null) {
        context.dataStore.edit {
            if (uri.isNullOrBlank()) it.remove(Keys.PRESET_LOCK_URI)
            else it[Keys.PRESET_LOCK_URI] = uri
            it[Keys.PRESET_LOCK_IS_VIDEO] = isVideo
            if (source != null) it[Keys.PRESET_LOCK_SOURCE] = source
        }
    }

    suspend fun setPresetHomeSource(source: String) {
        context.dataStore.edit {
            it[Keys.PRESET_HOME_SOURCE] = source
            it.remove(Keys.PRESET_HOME_URI)
        }
    }

    suspend fun setPresetLockSource(source: String) {
        context.dataStore.edit {
            it[Keys.PRESET_LOCK_SOURCE] = source
            it.remove(Keys.PRESET_LOCK_URI)
        }
    }

    suspend fun setPresetHistoryJson(json: String?) {
        context.dataStore.edit {
            if (json.isNullOrBlank()) it.remove(Keys.PRESET_HISTORY_JSON)
            else it[Keys.PRESET_HISTORY_JSON] = json
        }
    }

    suspend fun setFolderHideMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FOLDER_HIDE_MODE] = enabled }
    }

    suspend fun setShowHiddenFolders(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_HIDDEN_FOLDERS] = show }
    }

    /** Single DataStore read for scheme resolution / banners. */
    suspend fun snapshot(): PrefsSnapshot {
        val p = context.dataStore.data.first()
        return PrefsSnapshot(
            presetEnabled = p[Keys.PRESET_ENABLED] ?: false,
            scheduleEnabled = p[Keys.SCHEDULE_ENABLED] ?: false,
            scheduleActiveGroupId = p[Keys.SCHEDULE_ACTIVE_GROUP_ID],
            scheduleGroupsJson = p[Keys.SCHEDULE_GROUPS_JSON] ?: "[]",
            activeHomeImage = p[Keys.ACTIVE_HOME_IMAGE_FOLDER_URI] ?: p[Keys.ACTIVE_IMAGE_FOLDER_URI],
            activeHomeVideo = p[Keys.ACTIVE_HOME_VIDEO_FOLDER_URI] ?: p[Keys.ACTIVE_VIDEO_FOLDER_URI],
            activeLockImage = p[Keys.ACTIVE_LOCK_IMAGE_FOLDER_URI],
            activeLockVideo = p[Keys.ACTIVE_LOCK_VIDEO_FOLDER_URI],
            defaultHomeImage = p[Keys.DEFAULT_HOME_IMAGE_FOLDER_URI],
            defaultHomeVideo = p[Keys.DEFAULT_HOME_VIDEO_FOLDER_URI],
            defaultLockImage = p[Keys.DEFAULT_LOCK_IMAGE_FOLDER_URI],
            defaultLockVideo = p[Keys.DEFAULT_LOCK_VIDEO_FOLDER_URI]
        )
    }
}

/** Immutable prefs slice for [com.klvw.wallpaper.scheme.WallpaperSchemeResolver]. */
data class PrefsSnapshot(
    val presetEnabled: Boolean,
    val scheduleEnabled: Boolean,
    val scheduleActiveGroupId: String?,
    val scheduleGroupsJson: String,
    val activeHomeImage: String?,
    val activeHomeVideo: String?,
    val activeLockImage: String?,
    val activeLockVideo: String?,
    val defaultHomeImage: String?,
    val defaultHomeVideo: String?,
    val defaultLockImage: String?,
    val defaultLockVideo: String?
) {
    fun activeFolder(target: WallpaperTarget, type: FolderType): String? = when {
        target == WallpaperTarget.HOME && type == FolderType.IMAGE -> activeHomeImage
        target == WallpaperTarget.HOME && type == FolderType.VIDEO -> activeHomeVideo
        target == WallpaperTarget.LOCK && type == FolderType.IMAGE -> activeLockImage
        target == WallpaperTarget.LOCK && type == FolderType.VIDEO -> activeLockVideo
        else -> null
    }?.takeIf { it.isNotBlank() }

    fun defaultFolder(target: WallpaperTarget, type: FolderType): String? = when {
        target == WallpaperTarget.HOME && type == FolderType.IMAGE -> defaultHomeImage
        target == WallpaperTarget.HOME && type == FolderType.VIDEO -> defaultHomeVideo
        target == WallpaperTarget.LOCK && type == FolderType.IMAGE -> defaultLockImage
        target == WallpaperTarget.LOCK && type == FolderType.VIDEO -> defaultLockVideo
        else -> null
    }?.takeIf { it.isNotBlank() }
}

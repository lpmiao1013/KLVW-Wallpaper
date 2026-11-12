package com.klvw.wallpaper.data.model

/**
 * How wallpaper media is fitted to the screen.
 *
 * Matches WallpaperSwitcher semantics:
 * - [FILL]: cover the screen, crop overflow, keep aspect
 * - [FIT]: show the whole media, letterbox if needed
 * - [STRETCH]: force-fill the screen (may distort)
 */
enum class WallpaperScaleMode(val prefValue: String) {
    FILL("fill"),
    FIT("fit"),
    STRETCH("stretch");

    companion object {
        fun fromPref(value: String?): WallpaperScaleMode =
            entries.firstOrNull { it.prefValue == value } ?: FILL
    }
}

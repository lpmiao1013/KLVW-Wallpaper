package com.klvw.wallpaper.data.model

/**
 * Video live-wallpaper sharpness vs power trade-off.
 *
 * Does not call [android.view.SurfaceHolder.setFixedSize] (unsafe on some OEMs).
 */
enum class VideoSharpnessMode(val prefValue: String, val labelZh: String) {
    /** Smaller OES buffer + mediump — softer, lower bandwidth. */
    POWER("power", "省电"),
    /** Decode at source size + highp + suggestDesiredDimensions. */
    STANDARD("standard", "标准"),
    /** Prefer larger decode buffer (max of source / display) + highp + suggest. */
    QUALITY("quality", "高清");

    companion object {
        fun fromPref(value: String?): VideoSharpnessMode =
            entries.firstOrNull { it.prefValue == value } ?: STANDARD
    }
}

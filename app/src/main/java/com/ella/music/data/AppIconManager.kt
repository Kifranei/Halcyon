package com.ella.music.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object AppIconManager {

    // Launcher aliases are declared in the source namespace even when a build is repackaged.
    // Do not derive this through Class.packageName: it compiles to Class.getPackageName(), which
    // only exists on Android 12+ and crashes Android 10/11 during Application startup.
    private const val LAUNCHER_ALIAS_PACKAGE = "com.ella.music"
    private const val DEFAULT_ALIAS = ".DefaultLauncherAlias"
    private const val ANIME_ALIAS = ".AnimeLauncherAlias"
    private const val LEGACY_BLACK_HAIR_ALIAS = ".BlackHairLauncherAlias"
    private const val LOLI_ALIAS = ".LoliLauncherAlias"
    private const val TRADITIONAL_ALIAS = ".TraditionalLauncherAlias"

    @Volatile private var activeStyle = SettingsManager.APP_ICON_STYLE_DEFAULT
    fun selectedIconRes(style: String): Int = when (normalize(style)) {
        SettingsManager.APP_ICON_STYLE_ANIME -> com.ella.music.R.mipmap.ic_launcher_anime
        SettingsManager.APP_ICON_STYLE_LOLI -> com.ella.music.R.mipmap.ic_launcher_loli
        SettingsManager.APP_ICON_STYLE_TRADITIONAL -> com.ella.music.R.mipmap.ic_launcher_traditional
        else -> com.ella.music.R.mipmap.ic_launcher
    }
    // System status-bar icons must be monochrome; full-color launcher artwork is used in recents.
    fun notificationIconRes(): Int = if (activeStyle == SettingsManager.APP_ICON_STYLE_TRADITIONAL)
        com.ella.music.R.drawable.ic_launcher_traditional_foreground else com.ella.music.R.drawable.ic_flyme_ticker

    fun updateTaskIcon(context: Context, style: String, followSystemTheme: Boolean = true) {
        var current = context
        while (current is android.content.ContextWrapper && current !is android.app.Activity) {
            val next = current.baseContext
            if (next === current) return
            current = next
        }
        val activity = current as? android.app.Activity ?: return
        runCatching {
            val label = context.getString(com.ella.music.R.string.app_name)
            // A supplied bitmap bypasses MIUI's themed/adaptive app-icon pipeline. Clearing
            // it lets recents resolve the activity/application icon through the system theme.
            if (followSystemTheme) {
                @Suppress("DEPRECATION")
                activity.setTaskDescription(android.app.ActivityManager.TaskDescription(label))
                return
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                activity.setTaskDescription(android.app.ActivityManager.TaskDescription.Builder()
                    .setLabel(label).setIcon(selectedIconRes(style)).build())
                return
            }
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, selectedIconRes(style)) ?: return
            val bitmap = android.graphics.Bitmap.createBitmap(192, 192, android.graphics.Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 192, 192)
            drawable.draw(android.graphics.Canvas(bitmap))
            @Suppress("DEPRECATION")
            activity.setTaskDescription(android.app.ActivityManager.TaskDescription(context.getString(com.ella.music.R.string.app_name), bitmap))
        }.onFailure { Log.w(TAG, "Cannot update task icon", it) }
    }

    fun apply(context: Context, style: String) {
        val normalizedStyle = normalize(style)
        if (activeStyle != normalizedStyle) {
            activeStyle = normalizedStyle
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                com.ella.music.player.PlaybackTickerState.refresh()
            }
        }
        val packageName = context.packageName
        val packageManager = context.packageManager
        val aliases = listOf(
            SettingsManager.APP_ICON_STYLE_DEFAULT to DEFAULT_ALIAS,
            SettingsManager.APP_ICON_STYLE_ANIME to ANIME_ALIAS,
            "black_hair" to LEGACY_BLACK_HAIR_ALIAS,
            SettingsManager.APP_ICON_STYLE_LOLI to LOLI_ALIAS,
            SettingsManager.APP_ICON_STYLE_TRADITIONAL to TRADITIONAL_ALIAS
        )
        val selected = aliases.first { it.first == normalizedStyle }

        // Some launchers ignore DONT_KILL_APP when the active launcher alias is disabled.
        // Always make the requested entry effective first; if that fails, preserve the current
        // launcher entry instead of risking an application with no usable launch component.
        val selectedEnabled = setAliasEnabled(
            packageManager = packageManager,
            componentName = launcherAliasComponent(packageName, selected.second),
            enabled = true
        )
        if (!selectedEnabled) return

        aliases.asSequence()
            .filterNot { it == selected }
            .forEach { (_, aliasSuffix) ->
                setAliasEnabled(
                    packageManager = packageManager,
                    componentName = launcherAliasComponent(packageName, aliasSuffix),
                    enabled = false
                )
            }
    }

    fun normalize(style: String?): String =
        when (style) {
            SettingsManager.APP_ICON_STYLE_ANIME -> SettingsManager.APP_ICON_STYLE_ANIME
            SettingsManager.APP_ICON_STYLE_LOLI -> SettingsManager.APP_ICON_STYLE_LOLI
            SettingsManager.APP_ICON_STYLE_TRADITIONAL -> SettingsManager.APP_ICON_STYLE_TRADITIONAL
            else -> SettingsManager.APP_ICON_STYLE_DEFAULT
        }

    private fun setAliasEnabled(
        packageManager: PackageManager,
        componentName: ComponentName,
        enabled: Boolean
    ): Boolean {
        val targetState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        return runCatching {
            // A repackaged build can have a different applicationId while the component class
            // remains in Halcyon's source namespace. Missing/rewritten aliases must never crash
            // Application.onCreate; icon switching simply becomes unavailable for that package.
            val activityInfo =
                packageManager.getActivityInfo(componentName, PackageManager.MATCH_DISABLED_COMPONENTS)
            val componentState = packageManager.getComponentEnabledSetting(componentName)
            if (needsAliasStateChange(componentState, activityInfo.enabled, enabled)) {
                packageManager.setComponentEnabledSetting(
                    componentName,
                    targetState,
                    PackageManager.DONT_KILL_APP
                )
            }
            true
        }.onFailure { error ->
            Log.w(TAG, "Launcher alias unavailable: ${componentName.className}", error)
        }.getOrDefault(false)
    }

    internal fun needsAliasStateChange(
        componentState: Int,
        manifestEnabled: Boolean,
        desiredEnabled: Boolean
    ): Boolean = isAliasEffectivelyEnabled(componentState, manifestEnabled) != desiredEnabled

    internal fun isAliasEffectivelyEnabled(
        componentState: Int,
        manifestEnabled: Boolean
    ): Boolean = when (componentState) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
        else -> manifestEnabled
    }

    private fun launcherAliasComponent(applicationId: String, aliasSuffix: String): ComponentName =
        ComponentName(applicationId, launcherAliasClassName(aliasSuffix))

    internal fun launcherAliasClassName(aliasSuffix: String): String =
        "$LAUNCHER_ALIAS_PACKAGE$aliasSuffix"

    private const val TAG = "AppIconManager"
}

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
    private const val BLACK_HAIR_ALIAS = ".BlackHairLauncherAlias"
    private const val LOLI_ALIAS = ".LoliLauncherAlias"

    fun apply(context: Context, style: String) {
        val normalizedStyle = normalize(style)
        val packageName = context.packageName
        val packageManager = context.packageManager
        val aliases = listOf(
            SettingsManager.APP_ICON_STYLE_DEFAULT to DEFAULT_ALIAS,
            SettingsManager.APP_ICON_STYLE_ANIME to ANIME_ALIAS,
            SettingsManager.APP_ICON_STYLE_BLACK_HAIR to BLACK_HAIR_ALIAS,
            SettingsManager.APP_ICON_STYLE_LOLI to LOLI_ALIAS
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
            SettingsManager.APP_ICON_STYLE_BLACK_HAIR -> SettingsManager.APP_ICON_STYLE_BLACK_HAIR
            SettingsManager.APP_ICON_STYLE_LOLI -> SettingsManager.APP_ICON_STYLE_LOLI
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

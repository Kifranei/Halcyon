package com.ella.music.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ella.music.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Result of comparing the last-run app version with the running one. */
sealed interface AppUpgradeCheck {
    /** No previous marker and no existing app data: a clean install. */
    data object FreshInstall : AppUpgradeCheck

    /** The same version already ran on this install. */
    data object SameVersion : AppUpgradeCheck

    /**
     * Installed over a different version without clearing data. [previousVersionName] is null
     * when the install predates the version marker (only existing app data was found).
     */
    data class Upgraded(val previousVersionName: String?) : AppUpgradeCheck
}

/**
 * Pure decision used on every cold start. Version names (not codes) are compared because
 * beta and release builds of one version share a versionCode.
 */
fun evaluateAppUpgrade(
    previousVersionName: String?,
    currentVersionName: String,
    hasExistingInstallData: Boolean
): AppUpgradeCheck {
    val previous = previousVersionName?.trim()?.takeIf(String::isNotEmpty)
    return when {
        previous == null && !hasExistingInstallData -> AppUpgradeCheck.FreshInstall
        previous == null -> AppUpgradeCheck.Upgraded(previousVersionName = null)
        previous == currentVersionName.trim() -> AppUpgradeCheck.SameVersion
        else -> AppUpgradeCheck.Upgraded(previousVersionName = previous)
    }
}

/**
 * Tracks the last-run version in a tiny SharedPreferences file that is intentionally outside the
 * DataStore settings, so restoring a settings backup can neither create nor clear it.
 */
object AppUpgradeNotice {
    private const val PREFS_NAME = "app_version_marker"
    private const val KEY_LAST_RUN_VERSION_NAME = "last_run_version_name"
    private const val SETTINGS_DATASTORE_NAME = "ella_settings"

    private val _pending = MutableStateFlow<AppUpgradeCheck.Upgraded?>(null)

    /** Non-null while the cross-version install notice still has to be answered. */
    val pending: StateFlow<AppUpgradeCheck.Upgraded?> = _pending.asStateFlow()

    /**
     * Must run at the very start of Application.onCreate, before anything writes DataStore, so a
     * fresh install is not mistaken for an old install by the presence of the settings file.
     */
    fun capture(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasExistingData = runCatching {
            appContext.preferencesDataStoreFile(SETTINGS_DATASTORE_NAME).let { it.isFile && it.length() > 0L }
        }.getOrDefault(false)
        when (
            val check = evaluateAppUpgrade(
                previousVersionName = prefs.getString(KEY_LAST_RUN_VERSION_NAME, null),
                currentVersionName = BuildConfig.VERSION_NAME,
                hasExistingInstallData = hasExistingData
            )
        ) {
            AppUpgradeCheck.FreshInstall -> recordCurrentVersion(appContext)
            AppUpgradeCheck.SameVersion -> Unit
            // The marker is written only after the user answers the notice.
            is AppUpgradeCheck.Upgraded -> _pending.value = check
        }
    }

    /** Called after either dialog choice so the notice appears once per upgrade. */
    fun acknowledge(context: Context) {
        recordCurrentVersion(context.applicationContext)
        _pending.value = null
    }

    private fun recordCurrentVersion(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_RUN_VERSION_NAME, BuildConfig.VERSION_NAME)
            .apply()
    }
}

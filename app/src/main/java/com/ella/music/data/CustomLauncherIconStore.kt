package com.ella.music.data

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import com.ella.music.MainActivity
import com.ella.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class CustomLauncherIconItem(
    val id: String,
    val filePath: String,
    val label: String,
    val createdAt: Long
)

object CustomLauncherIconStore {
    const val DIR_NAME = "custom_launcher_icons"
    private const val MANIFEST_FILE_NAME = "manifest.json"

    fun getIconDir(context: Context): File {
        return File(context.filesDir, DIR_NAME).apply { mkdirs() }
    }

    private fun getManifestFile(context: Context): File {
        return File(getIconDir(context), MANIFEST_FILE_NAME)
    }

    suspend fun loadIcons(context: Context): List<CustomLauncherIconItem> = withContext(Dispatchers.IO) {
        val dir = getIconDir(context)
        val manifestFile = getManifestFile(context)
        val items = mutableListOf<CustomLauncherIconItem>()
        val knownIds = mutableSetOf<String>()

        if (manifestFile.exists()) {
            runCatching {
                val jsonStr = manifestFile.readText(Charsets.UTF_8)
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id")
                    val fileName = obj.optString("fileName", "$id.png")
                    val file = File(dir, fileName)
                    if (id.isNotBlank() && file.isFile && file.length() > 0) {
                        items.add(
                            CustomLauncherIconItem(
                                id = id,
                                filePath = file.absolutePath,
                                label = obj.optString("label", context.getString(R.string.app_name)),
                                createdAt = obj.optLong("createdAt", file.lastModified())
                            )
                        )
                        knownIds.add(id)
                    }
                }
            }
        }

        // Auto-discover any orphaned pngs (e.g. from restored backup or manual copy)
        dir.listFiles { f -> f.isFile && f.extension.equals("png", ignoreCase = true) }?.forEach { f ->
            val id = f.nameWithoutExtension
            if (id !in knownIds && id.isNotBlank()) {
                items.add(
                    CustomLauncherIconItem(
                        id = id,
                        filePath = f.absolutePath,
                        label = context.getString(R.string.app_name),
                        createdAt = f.lastModified()
                    )
                )
            }
        }

        items.sortedByDescending { it.createdAt }
    }

    suspend fun saveIcon(context: Context, bitmap: Bitmap, label: String = ""): CustomLauncherIconItem = withContext(Dispatchers.IO) {
        val dir = getIconDir(context)
        val id = UUID.randomUUID().toString()
        val file = File(dir, "$id.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val resolvedLabel = label.ifBlank { context.getString(R.string.app_name) }
        val item = CustomLauncherIconItem(
            id = id,
            filePath = file.absolutePath,
            label = resolvedLabel,
            createdAt = System.currentTimeMillis()
        )
        val currentIcons = loadIcons(context).toMutableList()
        currentIcons.removeAll { it.id == id }
        currentIcons.add(0, item)
        saveManifest(context, currentIcons)
        item
    }

    suspend fun deleteIcon(context: Context, id: String) = withContext(Dispatchers.IO) {
        val dir = getIconDir(context)
        val file = File(dir, "$id.png")
        if (file.exists()) {
            file.delete()
        }
        val currentIcons = loadIcons(context).filter { it.id != id }
        saveManifest(context, currentIcons)
    }

    private fun saveManifest(context: Context, items: List<CustomLauncherIconItem>) {
        val manifestFile = getManifestFile(context)
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("fileName", File(item.filePath).name)
                put("label", item.label)
                put("createdAt", item.createdAt)
            }
            array.put(obj)
        }
        manifestFile.writeText(array.toString(2), Charsets.UTF_8)
    }

    fun pinShortcut(context: Context, item: CustomLauncherIconItem, bitmap: Bitmap? = null): Boolean {
        val manager = context.getSystemService(ShortcutManager::class.java)
        if (manager == null || !manager.isRequestPinShortcutSupported) {
            return false
        }
        val bmp = bitmap ?: BitmapFactory.decodeFile(item.filePath) ?: return false
        val label = item.label.ifBlank { context.getString(R.string.app_name) }
        val shortcut = ShortcutInfo.Builder(context, "custom_launcher_${item.id}")
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            })
            .build()
        return manager.requestPinShortcut(shortcut, null)
    }
}

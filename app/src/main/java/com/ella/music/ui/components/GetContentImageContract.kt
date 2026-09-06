package com.ella.music.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * MIUI / HyperOS Gallery exposes its album sidebar through ACTION_GET_CONTENT.
 * OpenDocument hides that provider on some builds, so cover and wallpaper pickers
 * share this contract and copy/decode the image immediately.
 */
internal class GetContentImageContract : ActivityResultContract<Array<String>, Uri?>() {
    override fun createIntent(context: Context, input: Array<String>): Intent = Intent(
        Intent.ACTION_GET_CONTENT
    ).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = input.singleOrNull() ?: "image/*"
        putExtra(Intent.EXTRA_MIME_TYPES, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

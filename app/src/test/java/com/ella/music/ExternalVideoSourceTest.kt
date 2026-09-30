package com.ella.music

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ExternalVideoSourceTest {
    @Test fun contentDocumentViewAndShareKeepTheGrantedUri() {
        val uri = Uri.parse("content://documents/document/primary%3AMovies%2Fdemo.mp4")
        assertEquals(uri.toString(), externalVideoSource(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")))
        assertEquals(uri.toString(), externalVideoSource(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri)))
        val clips = ClipData.newRawUri("Video", uri)
        assertEquals(uri.toString(), externalVideoSource(Intent(Intent.ACTION_SEND).apply { clipData = clips }))
    }
    @Test fun sharedUrlsAreAcceptedAndExecutableSourcesAreRejected() {
        assertEquals("https://cdn.example/video.mp4", externalVideoSource(Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, " https://cdn.example/video.mp4 ")))
        assertNull(externalVideoSource(Intent(Intent.ACTION_VIEW, Uri.parse("javascript:alert(1)"))))
    }
}

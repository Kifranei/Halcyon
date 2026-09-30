package com.ella.music.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseArtistBiographyTest {
    @Test fun kugouPrefersLISAAndFallsBackOnlyWhenItsBiographyIsEmpty() = runBlocking {
        for (emptyExact in listOf(false, true)) {
            val ids = mutableListOf<String?>()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val body = if (request.url.encodedPath.contains("search")) {
                    """{"data":{"info":[{"singerid":1,"singername":"LiSA"},{"singerid":2,"singername":"LISA"}]}}"""
                } else {
                    val id = request.url.queryParameter("singerid")
                    ids += id
                    if (id == "2" && emptyExact) """{"data":{"profile":""}}""" else """{"data":{"profile":"Biography $id"}}"""
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
            }.build()
            assertEquals(if (emptyExact) "Biography 1" else "Biography 2", ChineseMusicMetadata.artist("LISA", AlbumInfoSource.Kugou, client)?.text)
            assertEquals(if (emptyExact) listOf("2", "1") else listOf("2"), ids)
        }
    }
}

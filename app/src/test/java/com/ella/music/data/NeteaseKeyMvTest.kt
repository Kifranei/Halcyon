package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseKeyMvTest {
    @Test
    fun decodesMvidFromEncrypted163KeyWithoutChangingTheValue() {
        val value = "163 key(Don't modify):Mb+q/FJqi4QRbGGF28goe8Bc02PA76zGN0rqzcTUSD8nFMbMAxdp2jC0PYFnY0rUrFB4jPVMw1pceYQ61bSGMzfJumvjRUzQ9mxMsRmCnUCbHP/zSrAax2Qbyl3h0CsiKZum7nLLVkqyUQM2c39BKDKmqcXyK1E/X0xwUSLoycI3s76AgCkAwhUD7WNhqg+yirZ7Rc+5P4oNSoBZfY4X50tjGOESpkhp7bXBaoKCzU2mQCyvCE+iIftr9/0vX+UZFL5hUN6MCj7dDGZM2mGrtbIsGVNSUkp8/odCyXq//4r8oQcUvaaQDSDJmR9eMJKDDNHP+Z77uhjcuikLTE04IhL/8C7EkySvmSyPBvymFt8byLg8ck6lSJ/QQhQdyZVMS7iEnN3iXLMgj16HlO6N6lE/03TAsVJsrVJzNrZD6lD9pj10HwuO8+tOSS+SuhgTbmw6CDc2ox3JCcg7u9d2IlruqiA8Xv/5uMcFx6mKI48Y3Jtqz1p62+1Fhi+xFgVlKCPfckzmGPFhd5yDJrxjLct3CYgmsaGXs0lsB0+/j7bs7bd1hXqx+2phRtz4XAoy0ze/89fxIFtDuYrEA/Tj15VhL/aqMpU5UBZPSEHFhko="

        val info = decodeNeteaseKey(value)

        assertEquals(value, info?.raw)
        assertEquals("419444", info?.mvId)
    }

    @Test
    fun decodesMvidFrom163KeyJson() {
        val info = decodeNeteaseKey(
            """{"musicId":123456,"musicName":"Test song","mvid":34780572}"""
        )

        assertEquals("34780572", info?.mvId)
    }

    @Test
    fun buildsMobileNeteaseMvUrl() {
        assertEquals(
            "https://y.music.163.com/m/mv?id=34780572",
            neteaseMvUrl("34780572")
        )
    }

    @Test
    fun buildsShortNeteaseShareSongUrl() {
        assertEquals(
            "https://y.163.com/m/song?id=123456",
            neteaseShareSongUrl("123456")
        )
    }

    @Test
    fun mvIdCountsAsDecodedContent() {
        assertTrue(
            NeteaseKeyInfo(
                raw = "163 key",
                mvId = "34780572"
            ).hasDecodedContent
        )
    }

    @Test
    fun decodesTranslatedNamesFrom163KeyJson() {
        val info = decodeNeteaseKey(
            """{"musicId":123456,"musicName":"Original","transNames":["译名一","译名二"]}"""
        )

        assertEquals(listOf("译名一", "译名二"), info?.translatedNames)
        assertTrue(info?.hasDecodedContent == true)
    }
}

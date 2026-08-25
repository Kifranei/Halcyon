package com.ella.music.data.lx

import org.junit.Assert.assertEquals
import org.junit.Test

class LxOnlineServicePolicyTest {
    @Test
    fun `migu search signature matches lx music protocol`() {
        assertEquals(
            "01704e9ed9d7ce24f28e21c4d60ce681",
            createMiguSearchSignature(
                keyword = "See You Again",
                timestamp = "1720000000000",
                deviceId = "963B7AA0D21511ED807EE5846EC87D20"
            )
        )
    }
}

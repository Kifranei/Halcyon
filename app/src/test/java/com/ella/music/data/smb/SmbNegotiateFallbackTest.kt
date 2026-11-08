package com.ella.music.data.smb

import com.hierynomus.mssmb2.SMB2Dialect
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SmbNegotiateFallbackTest {
    @Test fun smb311FailureRetriesWithoutThatDialect() {
        val fallback = smb311FallbackDialects(SmbProtocol.Auto, RuntimeException("Unknown SMB2NegotiateContextType encountered: 8"))
        assertFalse(fallback!!.contains(SMB2Dialect.SMB_3_1_1))
        assertEquals(SMB2Dialect.SMB_3_0_2, fallback.first())
        assertFalse(smb311FallbackDialects(SmbProtocol.Smb3, NullPointerException())!!.contains(SMB2Dialect.SMB_3_1_1))
    }

    @Test fun tcpFailuresAndSmb2DoNotOpenASecondConnection() {
        assertNull(smb311FallbackDialects(SmbProtocol.Smb2, RuntimeException("Unknown SMB2NegotiateContextType")))
        assertNull(smb311FallbackDialects(SmbProtocol.Auto, UnknownHostException("nas")))
        assertNull(smb311FallbackDialects(SmbProtocol.Auto, ConnectException("refused")))
        assertNull(smb311FallbackDialects(SmbProtocol.Auto, SocketTimeoutException("connect")))
    }
}

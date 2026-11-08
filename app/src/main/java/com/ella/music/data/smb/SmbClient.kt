package com.ella.music.data.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.auth.NtlmAuthenticator
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.Closeable
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.util.EnumSet
import java.util.concurrent.TimeUnit

internal data class SmbLocation(val host: String, val port: Int, val share: String, val segments: List<String>) {
    val filePath get() = segments.joinToString("\\")
    fun child(name: String): SmbLocation {
        require(name.isNotBlank() && name !in listOf(".", "..") && name.none { it == '/' || it == '\\' || it == '\u0000' })
        return copy(segments = segments + name)
    }
    fun url(): String = URI("smb", null, host, if (port == 445) -1 else port,
        "/" + (listOf(share) + segments).joinToString("/"), null, null).toASCIIString()
    fun within(root: SmbLocation) = host.equals(root.host, true) && port == root.port && share.equals(root.share, true) &&
        segments.size >= root.segments.size && segments.take(root.segments.size).zip(root.segments).all { (a, b) -> a.equals(b, true) }
    companion object {
        fun parse(url: String): SmbLocation {
            val uri = URI(url.trim())
            require(uri.scheme.equals("smb", true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
            val parts = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty).map {
                URLDecoder.decode(it.replace("+", "%2B"), "UTF-8")
            }
            require(parts.isNotEmpty() && parts.all { part -> part !in listOf(".", "..") && part.none { it == '/' || it == '\\' || it == '\u0000' } })
            val port = if (uri.port == -1) 445 else uri.port
            require(port in 1..65535)
            return SmbLocation(uri.host, port, parts.first(), parts.drop(1))
        }
    }
}

internal enum class SmbProtocol(val id: String, val dialects: List<SMB2Dialect>) {
    Auto("auto", listOf(SMB2Dialect.SMB_3_1_1, SMB2Dialect.SMB_3_0_2, SMB2Dialect.SMB_3_0, SMB2Dialect.SMB_2_1, SMB2Dialect.SMB_2_0_2)),
    Smb2("smb2", listOf(SMB2Dialect.SMB_2_1, SMB2Dialect.SMB_2_0_2)),
    Smb3("smb3", listOf(SMB2Dialect.SMB_3_1_1, SMB2Dialect.SMB_3_0_2, SMB2Dialect.SMB_3_0));
    companion object { fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: Auto }
}

/** SMBJ 0.14 aborts SMB 3.1.1 when the server adds a context it does not know
 * (signing, transport, POSIX). Retry the same protocol without 3.1.1. TCP failures
 * are not a dialect problem and must not open a second connection. */
internal fun smb311FallbackDialects(protocol: SmbProtocol, error: Throwable): List<SMB2Dialect>? {
    if (SMB2Dialect.SMB_3_1_1 !in protocol.dialects) return null
    val chain = generateSequence(error) { it.cause }.toList()
    val tcp = chain.any {
        it is java.net.UnknownHostException || it is java.net.ConnectException ||
            it is java.net.NoRouteToHostException || it is java.net.PortUnreachableException ||
            it is java.net.SocketTimeoutException
    }
    if (tcp) return null
    return protocol.dialects.filter { it != SMB2Dialect.SMB_3_1_1 }
}
internal data class SmbAccount(val root: SmbLocation, val username: String, val password: String, val domain: String = "", val protocol: SmbProtocol = SmbProtocol.Auto)
internal fun SmbAccount.authentication(): AuthenticationContext {
    if (username.isBlank()) return AuthenticationContext.anonymous()
    val separator = username.indexOf('\\')
    val user = if (separator > 0) username.substring(separator + 1) else username
    val userDomain = domain.ifBlank { if (separator > 0) username.substring(0, separator) else "" }
    return AuthenticationContext(user, password.toCharArray(), userDomain)
}
internal data class SmbEntry(val location: SmbLocation, val name: String, val directory: Boolean, val bytes: Long, val modified: Long = 0)

internal fun SmbEntry.toLibrarySong(): com.ella.music.data.model.Song {
    val stem = name.substringBeforeLast('.')
    val artist = if (" - " in stem) stem.substringBefore(" - ") else ""
    val title = if (artist.isNotBlank()) stem.substringAfter(" - ") else stem
    val url = location.url()
    val id = java.nio.ByteBuffer.wrap(java.security.MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8))).long and Long.MAX_VALUE
    return com.ella.music.data.model.Song(id, title, artist,
        location.segments.dropLast(1).lastOrNull() ?: location.share, 0, 0,
        url, name, fileSize = bytes, dateModified = modified, onlineSource = "smb", onlineId = url)
}

internal class SmbConnectionException(val stage: String, cause: Exception) : IOException("SMB $stage failed", cause)

internal fun smbFailureCode(error: Throwable): String? = generateSequence(error) { it.cause }
    .mapNotNull { Regex("STATUS_[A-Z_]+").find(it.message.orEmpty())?.value }.firstOrNull()

/** A session owns its connection and share, and never writes to the remote server. */
internal class SmbSession(private val account: SmbAccount) : Closeable {
    private var client = newClient(account.protocol.dialects)
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null
    private fun connect(): Connection {
        try {
            return client.connect(account.root.host, account.root.port)
        } catch (error: Exception) {
            val fallback = smb311FallbackDialects(account.protocol, error) ?: throw error
            android.util.Log.w("SMB", "SMB 3.1.1 negotiate failed; retrying without it", error)
            runCatching { client.close() }
            client = newClient(fallback)
            return client.connect(account.root.host, account.root.port)
        }
    }
    private fun share(): DiskShare {
        share?.let { return it }
        var stage = "connect"
        try {
            val connected = connect().also { connection = it }
            android.util.Log.i("SMB", "Negotiated ${connected.negotiatedProtocol.dialect}; configured=${account.protocol.id}")
            stage = "authenticate"
            val auth = account.authentication()
            val authenticated = try { connected.authenticate(auth) } catch (error: SMBApiException) {
                // Guest-enabled shares may reject a null session while accepting Guest. Retry
                // only a credential rejection; never mask negotiation/signature failures.
                if (account.username.isNotBlank() || smbFailureCode(error) !in setOf("STATUS_LOGON_FAILURE", "STATUS_ACCESS_DENIED")) throw error
                connected.authenticate(AuthenticationContext.guest())
            }.also { session = it }
            stage = "share"
            return (authenticated.connectShare(account.root.share) as? DiskShare
                ?: throw IOException("SMB share is not a disk share")).also { share = it }
        } catch (error: Exception) { close(); throw SmbConnectionException(stage, error) }
    }
    fun list(location: SmbLocation): List<SmbEntry> {
        require(location.within(account.root))
        return share().list(location.filePath).filter { it.fileName !in listOf(".", "..") }.map {
            SmbEntry(location.child(it.fileName), it.fileName,
                it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L, it.endOfFile, it.lastWriteTime.toEpochMillis() / 1000)
        }.sortedWith(compareByDescending<SmbEntry> { it.directory }.thenBy { it.name.lowercase() })
    }
    fun open(location: SmbLocation): com.hierynomus.smbj.share.File {
        require(location.within(account.root))
        return share().openFile(location.filePath, EnumSet.of(AccessMask.FILE_READ_DATA, AccessMask.FILE_READ_ATTRIBUTES), EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            EnumSet.allOf(SMB2ShareAccess::class.java), SMB2CreateDisposition.FILE_OPEN,
            EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE))
    }
    override fun close() {
        runCatching { share?.close() }; share = null
        runCatching { session?.close() }; session = null
        runCatching { connection?.close() }; connection = null
        runCatching { client.close() }
    }
    private fun newClient(dialects: List<SMB2Dialect>) = SMBClient(SmbConfig.builder()
        .withAuthenticators(NtlmAuthenticator.Factory())
        .withDialects(*dialects.toTypedArray())
        // Windows advertises DFS even for a normal disk share. Following referrals
        // then stalls listing on an IPC$ lookup the share never answers.
        .withDfsEnabled(false)
        .withEncryptData(true)
        .withTimeout(15, TimeUnit.SECONDS)
        // The packet reader treats a socket read timeout as fatal and exits.
        // An idle gap after the player buffer fills, or a pause, used to leave
        // the next read waiting on a dead session. Request deadlines stay above.
        .withSoTimeout(0)
        .build())
}

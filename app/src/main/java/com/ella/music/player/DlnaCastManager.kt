package com.ella.music.player

import android.content.Context
import android.net.wifi.WifiManager
import androidx.media3.common.MediaItem
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource

internal data class DlnaRenderer(
    val name: String,
    val location: String,
    val avTransportControlUrl: String
)

internal object DlnaCastManager {
    private const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    private val httpClient = OkHttpClient.Builder().build()
    private val _devices = MutableStateFlow<List<DlnaRenderer>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()
    private var mediaServer: LocalCastMediaServer? = null

    suspend fun discover(context: Context) = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext
        _isScanning.value = true
        _status.value = null
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("halcyon-dlna-discovery")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            val locations = linkedSetOf<String>()
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 700
                val request = buildString {
                    append("M-SEARCH * HTTP/1.1\r\n")
                    append("HOST: 239.255.255.250:1900\r\n")
                    append("MAN: \"ssdp:discover\"\r\n")
                    append("MX: 2\r\n")
                    append("ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n")
                }.toByteArray(Charsets.US_ASCII)
                val target = InetAddress.getByName("239.255.255.250")
                repeat(3) {
                    socket.send(DatagramPacket(request, request.size, target, 1900))
                }
                val deadline = System.currentTimeMillis() + 3_200L
                while (System.currentTimeMillis() < deadline) {
                    val buffer = ByteArray(8_192)
                    val packet = DatagramPacket(buffer, buffer.size)
                    val response = runCatching {
                        socket.receive(packet)
                        String(packet.data, 0, packet.length, Charsets.US_ASCII)
                    }.getOrNull() ?: continue
                    response.lineSequence()
                        .firstOrNull { it.startsWith("location:", ignoreCase = true) }
                        ?.substringAfter(':')
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?.let(locations::add)
                }
            }
            _devices.value = locations.mapNotNull(::loadRenderer).distinctBy { it.avTransportControlUrl }
        } catch (error: Exception) {
            _devices.value = emptyList()
            _status.value = error.message
        } finally {
            runCatching { multicastLock?.release() }
            _isScanning.value = false
        }
    }

    suspend fun play(
        context: Context,
        renderer: DlnaRenderer,
        mediaItem: MediaItem,
        positionMs: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val server = mediaServer ?: LocalCastMediaServer(context).also { mediaServer = it }
            val rendererHost = runCatching { URL(renderer.location).host }.getOrNull()
            val mediaUrl = server.urlFor(mediaItem, targetHost = rendererHost)
                ?: error("无法生成局域网媒体地址")
            val title = mediaItem.mediaMetadata.title?.toString().orEmpty().ifBlank { mediaItem.mediaId }
            val artist = mediaItem.mediaMetadata.artist?.toString().orEmpty()
            val mime = normalizeDlnaMimeType(mediaItem.localConfiguration?.mimeType.orEmpty())
            val metadata = didlMetadata(title, artist, mediaUrl, mime)
            sendSoap(
                renderer,
                "SetAVTransportURI",
                "<InstanceID>0</InstanceID><CurrentURI>${mediaUrl.xmlEscape()}</CurrentURI>" +
                    "<CurrentURIMetaData>${metadata.xmlEscape()}</CurrentURIMetaData>"
            )
            if (positionMs > 1_000L) {
                runCatching {
                    sendSoap(
                        renderer,
                        "Seek",
                        "<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>${positionMs.toDlnaTime()}</Target>"
                    )
                }
            }
            sendSoap(renderer, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
            _status.value = renderer.name
        }
    }

    suspend fun pause(renderer: DlnaRenderer): Result<Unit> = transport(renderer, "Pause")
    suspend fun stop(renderer: DlnaRenderer): Result<Unit> = transport(renderer, "Stop")

    private suspend fun transport(renderer: DlnaRenderer, action: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching { sendSoap(renderer, action, "<InstanceID>0</InstanceID>") }
        }

    private fun loadRenderer(location: String): DlnaRenderer? = runCatching {
        val xml = httpClient.newCall(Request.Builder().url(location).get().build()).execute().use { response ->
            check(response.isSuccessful) { "DLNA description HTTP ${response.code}" }
            response.body?.string() ?: error("Empty DLNA description")
        }
        val factory = safeDocumentBuilderFactory()
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val friendlyName = document.getElementsByTagNameNS("*", "friendlyName")
            .item(0)?.textContent?.trim().orEmpty().ifBlank { URL(location).host }
        val services = document.getElementsByTagNameNS("*", "service")
        var controlUrl: String? = null
        for (index in 0 until services.length) {
            val service = services.item(index) as? Element ?: continue
            val type = service.childText("serviceType")
            if (type == AV_TRANSPORT || type.contains("AVTransport")) {
                controlUrl = service.childText("controlURL")
                break
            }
        }
        val resolvedControlUrl = URL(URL(location), controlUrl ?: error("No AVTransport service")).toString()
        DlnaRenderer(friendlyName, location, resolvedControlUrl)
    }.getOrNull()

    private fun sendSoap(renderer: DlnaRenderer, action: String, arguments: String) {
        val envelope = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body><u:$action xmlns:u="$AV_TRANSPORT">$arguments</u:$action></s:Body>
            </s:Envelope>
        """.trimIndent()
        val request = Request.Builder()
            .url(renderer.avTransportControlUrl)
            .header("SOAPACTION", "\"$AV_TRANSPORT#$action\"")
            .header("Connection", "close")
            .post(envelope.toRequestBody("text/xml; charset=utf-8".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) {
                "DLNA $action HTTP ${response.code}" + body.takeIf { it.isNotBlank() }?.let { ": ${it.take(256)}" }.orEmpty()
            }
        }
    }
}

private fun safeDocumentBuilderFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = true
    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
}

private fun Element.childText(localName: String): String =
    getElementsByTagNameNS("*", localName).item(0)?.textContent?.trim().orEmpty()

private fun didlMetadata(title: String, artist: String, url: String, mime: String): String = """
    <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
      <item id="0" parentID="0" restricted="1"><dc:title>${title.xmlEscape()}</dc:title><upnp:artist>${artist.xmlEscape()}</upnp:artist><upnp:class>object.item.audioItem.musicTrack</upnp:class><res protocolInfo="http-get:*:${normalizeDlnaMimeType(mime)}:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000">${url.xmlEscape()}</res></item>
    </DIDL-Lite>
""".trimIndent()

private fun String.xmlEscape(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun Long.toDlnaTime(): String {
    val totalSeconds = (coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

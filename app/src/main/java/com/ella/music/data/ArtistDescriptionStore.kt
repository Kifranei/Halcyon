package com.ella.music.data

import android.content.Context
import com.ella.music.data.model.Song
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

enum class ArtistDescriptionStorage {
    NONE,
    NFO,
    LOCAL
}

data class ArtistDescriptionRecord(
    val text: String = "",
    val storage: ArtistDescriptionStorage = ArtistDescriptionStorage.NONE
)

enum class ArtistDescriptionSaveResult {
    SAVED_TO_NFO,
    SAVED_LOCALLY,
    CLEARED
}

/**
 * Stores artist biographies without copying them onto every track.
 *
 * A folder named after the artist uses Kodi/Jellyfin `artist.nfo` `<biography>`.
 * Mixed or read-only libraries fall back to an app-local properties file. The
 * local entry is removed after a successful NFO write so external edits remain visible.
 */
class ArtistDescriptionStore internal constructor(
    private val localStoreFile: File
) {
    private val lock = Any()

    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, LOCAL_STORE_FILE)
    )

    fun load(artistName: String, songs: List<Song>): ArtistDescriptionRecord = synchronized(lock) {
        val key = artistDescriptionKey(artistName, songs)
        artistNfoFile(artistName, songs)
            ?.takeIf(File::isFile)
            ?.let(ArtistNfoDocument::readBiography)
            ?.takeIf(String::isNotBlank)
            ?.let {
                return@synchronized ArtistDescriptionRecord(it, ArtistDescriptionStorage.NFO)
            }
        readLocal(key)?.takeIf(String::isNotBlank)?.let {
            return@synchronized ArtistDescriptionRecord(it, ArtistDescriptionStorage.LOCAL)
        }
        ArtistDescriptionRecord()
    }

    fun save(
        artistName: String,
        songs: List<Song>,
        description: String
    ): ArtistDescriptionSaveResult = synchronized(lock) {
        val normalized = description.trim()
        val key = artistDescriptionKey(artistName, songs)
        val nfoFile = artistNfoFile(artistName, songs)

        if (normalized.isBlank()) {
            if (nfoFile?.isFile == true) {
                check(
                    ArtistNfoDocument.writeBiography(
                        file = nfoFile,
                        description = "",
                        artistName = artistName
                    )
                ) { "Cannot clear ${nfoFile.absolutePath}" }
            }
            writeLocal(key, null)
            return@synchronized ArtistDescriptionSaveResult.CLEARED
        }

        if (
            nfoFile != null &&
            ArtistNfoDocument.writeBiography(
                file = nfoFile,
                description = normalized,
                artistName = artistName
            )
        ) {
            writeLocal(key, null)
            return@synchronized ArtistDescriptionSaveResult.SAVED_TO_NFO
        }

        writeLocal(key, normalized)
        ArtistDescriptionSaveResult.SAVED_LOCALLY
    }

    internal fun nfoFileFor(artistName: String, songs: List<Song>): File? =
        artistNfoFile(artistName, songs)

    private fun readLocal(key: String): String? {
        if (!localStoreFile.isFile) return null
        return runCatching {
            Properties().apply {
                localStoreFile.reader(Charsets.UTF_8).use { reader -> load(reader) }
            }.getProperty(key)
        }.getOrNull()
    }

    private fun writeLocal(key: String, description: String?) {
        val properties = if (localStoreFile.isFile) {
            runCatching {
                Properties().apply {
                    localStoreFile.reader(Charsets.UTF_8).use { reader -> load(reader) }
                }
            }.getOrDefault(Properties())
        } else {
            Properties()
        }
        if (description.isNullOrBlank()) {
            properties.remove(key)
        } else {
            properties.setProperty(key, description)
        }
        if (properties.isEmpty) {
            localStoreFile.delete()
            return
        }
        localStoreFile.parentFile?.mkdirs()
        atomicReplace(localStoreFile) { temporary ->
            temporary.writer(Charsets.UTF_8).buffered().use { writer ->
                properties.store(writer, null)
            }
        }
    }

    companion object {
        private const val LOCAL_STORE_FILE = "artist_descriptions.properties"

        @Volatile
        private var instance: ArtistDescriptionStore? = null

        fun getInstance(context: Context): ArtistDescriptionStore =
            instance ?: synchronized(this) {
                instance ?: ArtistDescriptionStore(context).also { instance = it }
            }

        internal fun artistNfoFile(artistName: String, songs: List<Song>): File? =
            commonArtistFolder(artistName, songs)?.let { File(it, "artist.nfo") }

        internal fun commonArtistFolder(artistName: String, songs: List<Song>): File? {
            if (songs.isEmpty()) return null
            val artistKey = normalizeArtistCoverKey(artistName).takeIf { it.isNotBlank() } ?: return null
            val parents = songs.map { song ->
                val file = song.path
                    .takeIf { it.isNotBlank() && !it.contains("://") }
                    ?.let(::File)
                    ?: return null
                if (!file.isFile) return null
                runCatching { file.canonicalFile.parentFile }.getOrNull() ?: return null
            }.distinct()
            if (parents.isEmpty()) return null
            val start = if (parents.size == 1) {
                parents.first()
            } else {
                parents.reduce(::commonAncestor)
            }
            return generateSequence(start) { it.parentFile }
                .firstOrNull { folder ->
                    folder.parentFile != null &&
                        normalizeArtistCoverKey(folder.name) == artistKey
                }
        }

        private fun commonAncestor(first: File, second: File): File {
            val secondAncestors = generateSequence(second) { it.parentFile }.toHashSet()
            return generateSequence(first) { it.parentFile }
                .firstOrNull { it in secondAncestors }
                ?: first
        }

        internal fun artistDescriptionKey(artistName: String, songs: List<Song>): String {
            val representative = songs.firstOrNull()
            val folder = commonArtistFolder(artistName, songs)?.let {
                runCatching { it.canonicalPath }.getOrDefault(it.absolutePath)
            }.orEmpty()
            val raw = listOf(
                artistName,
                folder,
                representative?.onlineSource.orEmpty(),
                representative?.onlineId.orEmpty()
            ).joinToString("|") { it.trim().lowercase() }
            return MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}

internal object ArtistNfoDocument {
    fun readBiography(file: File): String? {
        val document = parse(file) ?: return null
        return sequenceOf("biography", "bio", "plot", "outline", "review")
            .mapNotNull { tag ->
                document.documentElement
                    ?.getElementsByTagName(tag)
                    ?.item(0)
                    ?.textContent
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
            }
            .firstOrNull()
    }

    fun writeBiography(
        file: File,
        description: String,
        artistName: String
    ): Boolean = runCatching {
        val document = when {
            file.isFile -> parse(file) ?: return false
            else -> newArtistDocument(artistName)
        }
        val root = document.documentElement?.takeIf { it.tagName.equals("artist", ignoreCase = true) }
            ?: return false
        val existing = root.getElementsByTagName("biography")
        val biography = existing.item(0) as? Element
        if (description.isBlank()) {
            biography?.parentNode?.removeChild(biography)
        } else {
            val target = biography ?: document.createElement("biography").also(root::appendChild)
            target.textContent = description
        }
        file.parentFile?.mkdirs()
        atomicReplace(file) { temporary ->
            val transformer = TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            }
            temporary.outputStream().buffered().use { output ->
                transformer.transform(DOMSource(document), StreamResult(output))
            }
        }
        true
    }.getOrDefault(false)

    private fun parse(file: File): Document? = runCatching {
        secureDocumentBuilderFactory()
            .newDocumentBuilder()
            .parse(file)
            .apply { documentElement?.normalize() }
    }.getOrNull()

    private fun newArtistDocument(artistName: String): Document {
        val document = secureDocumentBuilderFactory().newDocumentBuilder().newDocument()
        val root = document.createElement("artist")
        document.appendChild(root)
        artistName.takeIf { it.isNotBlank() }?.let {
            root.appendChild(document.createElement("name").apply { textContent = it })
        }
        return document
    }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            isExpandEntityReferences = false
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
}

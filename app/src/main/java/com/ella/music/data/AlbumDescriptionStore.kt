package com.ella.music.data

import android.content.Context
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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

enum class AlbumDescriptionStorage {
    NONE,
    NFO,
    LOCAL
}

data class AlbumDescriptionRecord(
    val text: String = "",
    val storage: AlbumDescriptionStorage = AlbumDescriptionStorage.NONE
)

enum class AlbumDescriptionSaveResult {
    SAVED_TO_NFO,
    SAVED_LOCALLY,
    CLEARED
}

/**
 * Stores album-level descriptions without duplicating them into every track.
 *
 * A writable, unambiguous album folder uses the established Kodi/Jellyfin album.nfo
 * `<review>` field. Read-only, remote and mixed-folder albums fall back to an app-local
 * properties file. The local entry is removed after a successful NFO write so external
 * edits to album.nfo remain visible.
 */
class AlbumDescriptionStore internal constructor(
    private val localStoreFile: File
) {
    private val lock = Any()

    constructor(context: Context) : this(
        File(context.applicationContext.filesDir, LOCAL_STORE_FILE)
    )

    fun load(album: Album?, songs: List<Song>): AlbumDescriptionRecord = synchronized(lock) {
        val key = albumDescriptionKey(album, songs)
        albumNfoFile(songs)
            ?.takeIf(File::isFile)
            ?.let(AlbumNfoDocument::readReview)
            ?.takeIf(String::isNotBlank)
            ?.let {
                return@synchronized AlbumDescriptionRecord(it, AlbumDescriptionStorage.NFO)
            }
        readLocal(key)?.takeIf(String::isNotBlank)?.let {
            return@synchronized AlbumDescriptionRecord(it, AlbumDescriptionStorage.LOCAL)
        }
        AlbumDescriptionRecord()
    }

    fun save(
        album: Album?,
        songs: List<Song>,
        description: String
    ): AlbumDescriptionSaveResult = synchronized(lock) {
        val normalized = description.trim()
        val key = albumDescriptionKey(album, songs)
        val nfoFile = albumNfoFile(songs)

        if (normalized.isBlank()) {
            if (nfoFile?.isFile == true) {
                check(
                    AlbumNfoDocument.writeReview(
                        file = nfoFile,
                        description = "",
                        album = album
                    )
                ) { "Cannot clear ${nfoFile.absolutePath}" }
            }
            writeLocal(key, null)
            return@synchronized AlbumDescriptionSaveResult.CLEARED
        }

        if (
            nfoFile != null &&
            AlbumNfoDocument.writeReview(
                file = nfoFile,
                description = normalized,
                album = album
            )
        ) {
            writeLocal(key, null)
            return@synchronized AlbumDescriptionSaveResult.SAVED_TO_NFO
        }

        writeLocal(key, normalized)
        AlbumDescriptionSaveResult.SAVED_LOCALLY
    }

    internal fun nfoFileFor(songs: List<Song>): File? = albumNfoFile(songs)

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
        private const val LOCAL_STORE_FILE = "album_descriptions.properties"
        private val discFolderRegex = Regex("""(?i)(?:cd|disc|disk|part)[\s._-]*\d+""")

        @Volatile
        private var instance: AlbumDescriptionStore? = null

        fun getInstance(context: Context): AlbumDescriptionStore =
            instance ?: synchronized(this) {
                instance ?: AlbumDescriptionStore(context).also { instance = it }
            }

        internal fun albumNfoFile(songs: List<Song>): File? =
            commonAlbumFolder(songs)?.let { File(it, "album.nfo") }

        internal fun commonAlbumFolder(songs: List<Song>): File? {
            if (songs.isEmpty()) return null
            val parents = songs.map { song ->
                val file = song.path
                    .takeIf { it.isNotBlank() && !it.contains("://") }
                    ?.let(::File)
                    ?: return null
                if (!file.isFile) return null
                runCatching { file.canonicalFile.parentFile }.getOrNull() ?: return null
            }.distinct()
            if (parents.size == 1) return parents.first()

            val common = parents.reduceOrNull(::commonAncestor) ?: return null
            if (common.parentFile == null) return null
            val onlyDiscSubfolders = parents.all { parent ->
                val relative = runCatching {
                    common.toPath().relativize(parent.toPath())
                }.getOrNull() ?: return@all false
                relative.nameCount == 1 && discFolderRegex.matches(relative.fileName.toString())
            }
            return common.takeIf { onlyDiscSubfolders }
        }

        private fun commonAncestor(first: File, second: File): File {
            val secondAncestors = generateSequence(second) { it.parentFile }.toHashSet()
            return generateSequence(first) { it.parentFile }
                .firstOrNull { it in secondAncestors }
                ?: first
        }

        internal fun albumDescriptionKey(album: Album?, songs: List<Song>): String {
            val representative = songs.firstOrNull()
            val folder = commonAlbumFolder(songs)?.let {
                runCatching { it.canonicalPath }.getOrDefault(it.absolutePath)
            }.orEmpty()
            val raw = listOf(
                folder,
                album?.name ?: representative?.album.orEmpty(),
                album?.albumArtist?.ifBlank { album.artist }
                    ?: representative?.albumArtist?.ifBlank { representative.artist }.orEmpty(),
                album?.year ?: representative?.year.orEmpty(),
                representative?.onlineSource.orEmpty(),
                representative?.onlineId.orEmpty()
            ).joinToString("|") { it.trim().lowercase() }
            return MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}

internal object AlbumNfoDocument {
    fun readReview(file: File): String? {
        val document = parse(file) ?: return null
        return sequenceOf("review", "plot", "outline")
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

    fun writeReview(
        file: File,
        description: String,
        album: Album?
    ): Boolean = runCatching {
        val document = when {
            file.isFile -> parse(file) ?: return false
            else -> newAlbumDocument(album)
        }
        val root = document.documentElement?.takeIf { it.tagName.equals("album", ignoreCase = true) }
            ?: return false
        val existingReviews = root.getElementsByTagName("review")
        val review = existingReviews.item(0) as? Element
        if (description.isBlank()) {
            review?.parentNode?.removeChild(review)
        } else {
            val target = review ?: document.createElement("review").also(root::appendChild)
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

    private fun newAlbumDocument(album: Album?): Document {
        val document = secureDocumentBuilderFactory().newDocumentBuilder().newDocument()
        val root = document.createElement("album")
        document.appendChild(root)
        album?.name?.takeIf(String::isNotBlank)?.let { root.appendTextElement(document, "title", it) }
        album?.albumArtist
            ?.ifBlank { album.artist }
            ?.takeIf(String::isNotBlank)
            ?.let { root.appendTextElement(document, "artist", it) }
        album?.year?.takeIf(String::isNotBlank)?.let {
            root.appendTextElement(document, "releasedate", it)
        }
        return document
    }

    private fun Element.appendTextElement(document: Document, tag: String, value: String) {
        appendChild(document.createElement(tag).apply { textContent = value })
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

private inline fun atomicReplace(target: File, writeTemporary: (File) -> Unit) {
    val directory = target.parentFile ?: error("Missing parent directory for ${target.absolutePath}")
    if (!directory.exists() && !directory.mkdirs()) {
        error("Cannot create directory ${directory.absolutePath}")
    }
    var temporary: File? = null
    try {
        temporary = File.createTempFile("${target.name}.", ".tmp", directory)
        writeTemporary(temporary)
        try {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    } finally {
        temporary?.takeIf(File::exists)?.delete()
    }
}

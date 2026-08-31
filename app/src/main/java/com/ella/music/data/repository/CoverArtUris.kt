package com.ella.music.data.repository

import android.content.ContentUris
import android.net.Uri

/**
 * The one supported MediaStore album-art URI construction path used by every cover surface.
 * Keeping this here prevents subtly different hard-coded provider URIs from drifting apart.
 */
private const val MEDIA_STORE_ALBUM_ART_COLLECTION =
    "content://media/external/audio/albumart"

/**
 * Returns the canonical provider string without touching Android's Uri implementation.
 * This is also useful for metadata APIs that only accept a string.
 */
internal fun mediaStoreAlbumArtUriString(albumId: Long): String? =
    albumId.takeIf { it > 0L }?.let { "$MEDIA_STORE_ALBUM_ART_COLLECTION/$it" }

internal fun mediaStoreAlbumArtUri(albumId: Long): Uri? =
    mediaStoreAlbumArtUriString(albumId)?.let {
        // MediaStore.Audio.Albums is the album table, not the provider endpoint that exposes
        // the decoded artwork stream. Keep the long-standing /audio/albumart/ endpoint here,
        // but construct it in one place for every cover surface.
        ContentUris.withAppendedId(Uri.parse(MEDIA_STORE_ALBUM_ART_COLLECTION), albumId)
    }

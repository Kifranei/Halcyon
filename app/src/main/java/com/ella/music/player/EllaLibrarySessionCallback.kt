package com.ella.music.player

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@OptIn(UnstableApi::class)
internal class EllaLibrarySessionCallback(
    private val service: PlaybackService
) : MediaLibrarySession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
            .add(SessionCommand(PlaybackService.ACTION_TOGGLE_TRANSLATION, Bundle.EMPTY))
            .add(SessionCommand(PlaybackService.ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
            .add(SessionCommand(PlaybackService.ACTION_TOGGLE_DESKTOP_LYRIC, Bundle.EMPTY))
            .add(SessionCommand(PlaybackService.ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
            .add(SessionCommand(PlaybackService.ACTION_UPDATE_NOTIFICATION_LYRIC, Bundle.EMPTY))
            .build()
        // Media3 intentionally gives untrusted controllers read-only player commands by
        // default. That prevents external widgets from sending the normal transport commands
        // even though this service is explicitly a music playback service. Keep the read-only
        // surface intact, but expose only the transport controls a widget needs.
        val playerCommands = if (controller.isTrusted) {
            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
        } else {
            Player.Commands.Builder()
                .addAllReadOnlyCommands()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .build()
        }
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(playerCommands)
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        val handled = if (customCommand.customAction == PlaybackService.ACTION_UPDATE_NOTIFICATION_LYRIC) {
            service.updateNotificationLyricPresentation(args)
        } else {
            service.handleNotificationCustomAction(customCommand.customAction)
        }
        val result = if (handled) {
            SessionResult(SessionResult.RESULT_SUCCESS)
        } else {
            SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
        }
        return Futures.immediateFuture(result)
    }

    override fun onSetMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return service.prepareOplusLyricForSetMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return Futures.immediateFuture(LibraryResult.ofItem(service.libraryRootItem(), params))
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val item = when (mediaId) {
            PlaybackService.LIBRARY_ROOT_ID -> service.libraryRootItem()
            PlaybackService.LIBRARY_QUEUE_ID -> service.currentQueueFolderItem()
            else -> service.currentQueueItems().firstOrNull { it.mediaId == mediaId }
        }
        return Futures.immediateFuture(
            if (item != null) {
                LibraryResult.ofItem(item, null)
            } else {
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            }
        )
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val children = when (parentId) {
            PlaybackService.LIBRARY_ROOT_ID -> listOf(service.currentQueueFolderItem())
            PlaybackService.LIBRARY_QUEUE_ID -> service.currentQueueItems()
            else -> return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
        }
        return Futures.immediateFuture(LibraryResult.ofItemList(children.page(page, pageSize), params))
    }

    override fun onSubscribe(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        return Futures.immediateFuture(LibraryResult.ofVoid(params))
    }

    private fun <T> List<T>.page(page: Int, pageSize: Int): List<T> {
        if (page < 0 || pageSize <= 0) return this
        val fromIndex = page * pageSize
        if (fromIndex >= size) return emptyList()
        return subList(fromIndex, minOf(fromIndex + pageSize, size))
    }
}

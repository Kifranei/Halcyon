package com.ella.music.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.dp
import com.ella.music.data.ActionMenuIds
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.HorizontalSplit
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Unpin
import top.yukonga.miuix.kmp.icon.extended.VolumeUp

// HyperOS 风格星星图标（对应 drawable/ic_rating_star_fill）
private val MenuRatingStarIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MenuRatingStar",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        addPath(
            pathData = listOf(
                PathNode.MoveTo(233f, 840f),
                PathNode.LineTo(326f, 536f),
                PathNode.LineTo(80f, 360f),
                PathNode.LineTo(384f, 360f),
                PathNode.LineTo(480f, 40f),
                PathNode.LineTo(576f, 360f),
                PathNode.LineTo(880f, 360f),
                PathNode.LineTo(634f, 536f),
                PathNode.LineTo(727f, 840f),
                PathNode.LineTo(480f, 652f),
                PathNode.LineTo(233f, 840f),
                PathNode.Close
            ),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

// Visualizer icon supplied by the product design (three vertical faders).
private val MenuVisualizerIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MenuVisualizer",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        addPath(
            pathData = listOf(
                PathNode.MoveTo(280f, 240f), PathNode.LineTo(360f, 240f),
                PathNode.LineTo(360f, 720f), PathNode.LineTo(280f, 720f), PathNode.Close,
                PathNode.MoveTo(440f, 80f), PathNode.LineTo(520f, 80f),
                PathNode.LineTo(520f, 880f), PathNode.LineTo(440f, 880f), PathNode.Close,
                PathNode.MoveTo(120f, 400f), PathNode.LineTo(200f, 400f),
                PathNode.LineTo(200f, 560f), PathNode.LineTo(120f, 560f), PathNode.Close,
                PathNode.MoveTo(600f, 240f), PathNode.LineTo(680f, 240f),
                PathNode.LineTo(680f, 720f), PathNode.LineTo(600f, 720f), PathNode.Close,
                PathNode.MoveTo(760f, 400f), PathNode.LineTo(840f, 400f),
                PathNode.LineTo(840f, 560f), PathNode.LineTo(760f, 560f), PathNode.Close
            ),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

internal fun actionMenuIcon(id: String): ImageVector? = when (id) {
    ActionMenuIds.ADD_TO_PLAYLIST -> MiuixIcons.Regular.Playlist
    ActionMenuIds.ADD_TO_QUEUE -> MiuixIcons.Regular.Playlist
    ActionMenuIds.PLAY_NEXT -> MiuixIcons.Regular.Play
    ActionMenuIds.SHARE -> MiuixIcons.Regular.Share
    ActionMenuIds.SPECTRUM -> MiuixIcons.Regular.Tune
    ActionMenuIds.AI -> MiuixIcons.Regular.Help
    ActionMenuIds.INFO -> MiuixIcons.Regular.Info
    ActionMenuIds.RATING -> MenuRatingStarIcon
    ActionMenuIds.EDIT_TAGS -> MiuixIcons.Regular.Edit
    ActionMenuIds.LYRIC_TIMING -> MiuixIcons.Regular.Notes
    ActionMenuIds.AUDIO_TOOLS -> MiuixIcons.Regular.Tune
    ActionMenuIds.REMOVE_FROM_PLAYLIST -> MiuixIcons.Regular.Remove
    ActionMenuIds.DELETE -> MiuixIcons.Regular.Delete
    ActionMenuIds.AUDIO_OUTPUT -> MiuixIcons.Regular.VolumeUp
    ActionMenuIds.CASTING -> MiuixIcons.Regular.ScreenMirroring
    ActionMenuIds.AB_REPEAT -> MiuixIcons.Regular.Reset
    ActionMenuIds.REMOTE_QUALITY -> MiuixIcons.Regular.CloudFill
    ActionMenuIds.LANDSCAPE -> MiuixIcons.Regular.HorizontalSplit
    ActionMenuIds.LYRICS_DISPLAY -> MiuixIcons.Regular.Notes
    ActionMenuIds.DYNAMIC_COVER -> MiuixIcons.Regular.Image
    ActionMenuIds.VISUALIZER -> MenuVisualizerIcon
    ActionMenuIds.ONLINE_LYRICS -> MiuixIcons.Regular.Search
    ActionMenuIds.LYRIC_OFFSET -> MiuixIcons.Regular.Timer
    ActionMenuIds.KEEP_SCREEN_ON -> MiuixIcons.Regular.Show
    ActionMenuIds.DOWNLOAD -> MiuixIcons.Regular.Download
    else -> null
}

internal object ActionMenuCommonIcons {
    val add = MiuixIcons.Regular.Add
    val album = MiuixIcons.Regular.Album
    val artist = MiuixIcons.Regular.ContactsCircle
    val block = MiuixIcons.Regular.Blocklist
    val delete = MiuixIcons.Regular.Delete
    val download = MiuixIcons.Regular.Download
    val edit = MiuixIcons.Regular.Edit
    val favorites = MiuixIcons.Regular.Favorites
    val home = MiuixIcons.Regular.Home
    val info = MiuixIcons.Regular.Info
    val link = MiuixIcons.Regular.Link
    val list = MiuixIcons.Regular.ListView
    val notes = MiuixIcons.Regular.Notes
    val pin = MiuixIcons.Regular.Pin
    val play = MiuixIcons.Regular.Play
    val playlist = MiuixIcons.Regular.Playlist
    val share = MiuixIcons.Regular.Share
    val tune = MiuixIcons.Regular.Tune
    val unpin = MiuixIcons.Regular.Unpin
}

package com.ella.music.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.ella.music.data.ActionMenuIds
import com.ella.music.ui.player.PlayerExtraActionIds
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Hide
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
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

internal val AnalyticsTrendIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "AnalyticsTrend",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        addPath(
            pathData = listOf(
                PathNode.MoveTo(140f, 740f),
                PathNode.RelativeLineTo(-60f, -60f),
                PathNode.RelativeLineTo(300f, -300f),
                PathNode.RelativeLineTo(160f, 160f),
                PathNode.RelativeLineTo(284f, -320f),
                PathNode.RelativeLineTo(56f, 56f),
                PathNode.RelativeLineTo(-340f, 384f),
                PathNode.RelativeLineTo(-160f, -160f),
                PathNode.RelativeLineTo(-240f, 240f),
                PathNode.Close
            ),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

internal val PlayNextListIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PlayNextList",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        addPath(
            pathData = listOf(
                PathNode.MoveTo(120f, 640f),
                PathNode.RelativeVerticalTo(-80f),
                PathNode.RelativeHorizontalTo(320f),
                PathNode.RelativeVerticalTo(80f),
                PathNode.HorizontalTo(120f),
                PathNode.Close,
                PathNode.MoveTo(120f, 480f),
                PathNode.RelativeVerticalTo(-80f),
                PathNode.RelativeHorizontalTo(480f),
                PathNode.RelativeVerticalTo(80f),
                PathNode.HorizontalTo(120f),
                PathNode.Close,
                PathNode.MoveTo(120f, 320f),
                PathNode.RelativeVerticalTo(-80f),
                PathNode.RelativeHorizontalTo(480f),
                PathNode.RelativeVerticalTo(80f),
                PathNode.HorizontalTo(120f),
                PathNode.Close,
                PathNode.MoveTo(640f, 840f),
                PathNode.RelativeVerticalTo(-320f),
                PathNode.RelativeLineTo(240f, 160f),
                PathNode.RelativeLineTo(-240f, 160f),
                PathNode.Close
            ),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

private val MenuSleepTimerIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MenuSleepTimer",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(9f, 3f)
            verticalLineTo(1f)
            horizontalLineToRelative(6f)
            verticalLineTo(3f)
            horizontalLineTo(9f)
            close()
            moveToRelative(2f, 11f)
            horizontalLineToRelative(2f)
            verticalLineTo(8f)
            horizontalLineTo(11f)
            verticalLineToRelative(6f)
            close()
            moveTo(8.51f, 21.29f)
            quadTo(6.88f, 20.58f, 5.65f, 19.35f)
            reflectiveQuadTo(3.71f, 16.49f)
            reflectiveQuadTo(3f, 13f)
            reflectiveQuadTo(3.71f, 9.51f)
            reflectiveQuadTo(5.65f, 6.65f)
            quadTo(6.88f, 5.43f, 8.51f, 4.71f)
            reflectiveQuadTo(12f, 4f)
            quadToRelative(1.55f, 0f, 2.98f, 0.5f)
            reflectiveQuadToRelative(2.68f, 1.45f)
            lineToRelative(1.4f, -1.4f)
            lineToRelative(1.4f, 1.4f)
            lineToRelative(-1.4f, 1.4f)
            quadTo(20f, 8.6f, 20.5f, 10.02f)
            reflectiveQuadTo(21f, 13f)
            quadToRelative(0f, 1.85f, -0.71f, 3.49f)
            reflectiveQuadToRelative(-1.94f, 2.86f)
            reflectiveQuadToRelative(-2.86f, 1.94f)
            reflectiveQuadTo(12f, 22f)
            reflectiveQuadTo(8.51f, 21.29f)
            close()
            moveToRelative(8.44f, -3.34f)
            quadTo(19f, 15.9f, 19f, 13f)
            reflectiveQuadTo(16.95f, 8.05f)
            reflectiveQuadTo(12f, 6f)
            reflectiveQuadTo(7.05f, 8.05f)
            reflectiveQuadTo(5f, 13f)
            reflectiveQuadToRelative(2.05f, 4.95f)
            reflectiveQuadTo(12f, 20f)
            reflectiveQuadToRelative(4.95f, -2.05f)
            close()
        }
    }.build()
}

private val MenuAudioToolsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MenuAudioTools",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(2f, 20f)
            verticalLineTo(10f)
            quadTo(2f, 9.17f, 2.59f, 8.59f)
            reflectiveQuadTo(4f, 8f)
            horizontalLineTo(7f)
            verticalLineTo(6f)
            quadTo(7f, 5.18f, 7.59f, 4.59f)
            reflectiveQuadTo(9f, 4f)
            horizontalLineToRelative(6f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            quadTo(17f, 5.18f, 17f, 6f)
            verticalLineTo(8f)
            horizontalLineToRelative(3f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(22f, 10f)
            verticalLineTo(20f)
            horizontalLineTo(2f)
            close()
            moveTo(8f, 15f)
            verticalLineToRelative(1f)
            horizontalLineTo(6f)
            verticalLineTo(15f)
            horizontalLineTo(4f)
            verticalLineToRelative(3f)
            horizontalLineTo(20f)
            verticalLineTo(15f)
            horizontalLineTo(18f)
            verticalLineToRelative(1f)
            horizontalLineTo(16f)
            verticalLineTo(15f)
            horizontalLineTo(8f)
            close()
            moveTo(4f, 10f)
            verticalLineToRelative(3f)
            horizontalLineTo(6f)
            verticalLineTo(12f)
            horizontalLineTo(8f)
            verticalLineToRelative(1f)
            horizontalLineToRelative(8f)
            verticalLineTo(12f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(1f)
            horizontalLineToRelative(2f)
            verticalLineTo(10f)
            horizontalLineTo(4f)
            close()
            moveTo(9f, 8f)
            horizontalLineToRelative(6f)
            verticalLineTo(6f)
            horizontalLineTo(9f)
            verticalLineTo(8f)
            close()
        }
    }.build()
}

internal val AddToPlaylistVectorIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "AddToPlaylist",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = listOf(
                PathNode.MoveTo(5f, 3.75f),
                PathNode.HorizontalTo(16f),
                PathNode.CurveTo(17.24f, 3.75f, 18.25f, 4.76f, 18.25f, 6f),
                PathNode.VerticalTo(12.25f),
                PathNode.MoveTo(5f, 3.75f),
                PathNode.CurveTo(3.76f, 3.75f, 2.75f, 4.76f, 2.75f, 6f),
                PathNode.VerticalTo(18f),
                PathNode.CurveTo(2.75f, 19.24f, 3.76f, 20.25f, 5f, 20.25f),
                PathNode.HorizontalTo(12.25f),
                PathNode.MoveTo(7f, 8.25f),
                PathNode.HorizontalTo(14f),
                PathNode.MoveTo(7f, 12f),
                PathNode.HorizontalTo(13f)
            ),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
        addPath(
            pathData = listOf(
                PathNode.MoveTo(18f, 14.25f),
                PathNode.VerticalTo(21.25f),
                PathNode.MoveTo(14.5f, 17.75f),
                PathNode.HorizontalTo(21.5f)
            ),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        )
    }.build()
}

// Speed gauge approximating PlayerQuickActionKind.Speed shortcut glyph.
private val MenuSpeedIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MenuSpeed",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Open gauge arc (cubic approximation of ~220° sweep).
        addPath(
            pathData = listOf(
                PathNode.MoveTo(4.6f, 16.6f),
                PathNode.CurveTo(4.6f, 9.2f, 9.0f, 4.6f, 12.0f, 4.6f),
                PathNode.CurveTo(15.0f, 4.6f, 19.4f, 9.2f, 19.4f, 16.6f)
            ),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        )
        // Needle
        addPath(
            pathData = listOf(
                PathNode.MoveTo(12f, 13.2f),
                PathNode.LineTo(17.0f, 9.2f)
            ),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        )
    }.build()
}

// Three faders approximating PlayerQuickActionKind.Equalizer shortcut glyph.
private val MenuEqualizerIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MenuEqualizer",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = listOf(
                PathNode.MoveTo(6f, 5f),
                PathNode.LineTo(6f, 19f),
                PathNode.MoveTo(12f, 5f),
                PathNode.LineTo(12f, 19f),
                PathNode.MoveTo(18f, 5f),
                PathNode.LineTo(18f, 19f)
            ),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round
        )
        // Square knobs at shortcut-relative heights (0.68 / 0.42 / 0.58).
        addPath(
            pathData = listOf(
                PathNode.MoveTo(4.3f, 15.4f),
                PathNode.LineTo(7.7f, 15.4f),
                PathNode.LineTo(7.7f, 18.0f),
                PathNode.LineTo(4.3f, 18.0f),
                PathNode.Close,
                PathNode.MoveTo(10.3f, 9.0f),
                PathNode.LineTo(13.7f, 9.0f),
                PathNode.LineTo(13.7f, 11.6f),
                PathNode.LineTo(10.3f, 11.6f),
                PathNode.Close,
                PathNode.MoveTo(16.3f, 12.8f),
                PathNode.LineTo(19.7f, 12.8f),
                PathNode.LineTo(19.7f, 15.4f),
                PathNode.LineTo(16.3f, 15.4f),
                PathNode.Close
            ),
            fill = SolidColor(Color.Black)
        )
    }.build()
}

internal fun actionMenuIcon(id: String): ImageVector? = when (id) {
    ActionMenuIds.ADD_TO_PLAYLIST -> AddToPlaylistVectorIcon
    ActionMenuIds.ADD_TO_QUEUE -> MiuixIcons.Regular.Playlist
    ActionMenuIds.PLAY_NEXT -> PlayNextListIcon
    ActionMenuIds.SHARE -> MiuixIcons.Regular.Share
    ActionMenuIds.SPECTRUM -> MiuixIcons.Regular.Tune
    ActionMenuIds.AI -> MiuixIcons.Regular.Help
    ActionMenuIds.INFO -> MiuixIcons.Regular.Info
    ActionMenuIds.RATING -> MenuRatingStarIcon
    ActionMenuIds.EDIT_TAGS -> MiuixIcons.Regular.Edit
    ActionMenuIds.LYRIC_TIMING -> MiuixIcons.Regular.Notes
    ActionMenuIds.AUDIO_TOOLS -> MenuAudioToolsIcon
    ActionMenuIds.REMOVE_FROM_PLAYLIST -> MiuixIcons.Regular.Remove
    ActionMenuIds.REMOVE_FROM_RECENT_PLAYBACK -> MiuixIcons.Regular.Delete
    ActionMenuIds.DELETE_SINGLE_RECENT_PLAYBACK -> MiuixIcons.Regular.Delete
    ActionMenuIds.CLEAR_RECENT_PLAYBACK -> MiuixIcons.Regular.Delete
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
    ActionMenuIds.SPEED -> MenuSpeedIcon
    ActionMenuIds.EQUALIZER -> MenuEqualizerIcon
    ActionMenuIds.TIMER -> MenuSleepTimerIcon
    ActionMenuIds.KEEP_SCREEN_ON -> MiuixIcons.Regular.Show
    ActionMenuIds.DOWNLOAD -> MiuixIcons.Regular.Download
    PlayerExtraActionIds.LYRIC_SHARE -> MiuixIcons.Regular.Notes
    else -> null
}

internal object ActionMenuCommonIcons {
    val add = MiuixIcons.Regular.Add
    val album = MiuixIcons.Regular.Album
    val artist = MiuixIcons.Regular.ContactsCircle
    val block = MiuixIcons.Regular.Hide
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
    val stopwatch = MiuixIcons.Regular.Stopwatch
    val tune = MiuixIcons.Regular.Tune
    val unpin = MiuixIcons.Regular.Unpin
}

package com.ella.music.ui.poster

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import com.ella.music.R
import com.ella.music.ui.components.EllaCheckOptionGroup
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.player.rememberLyricFramePosition
import com.ella.music.ui.player.rememberPlayerLyricFontState
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

@Composable
internal fun PosterLyricTheaterScreen(playerViewModel: PlayerViewModel, onBack: () -> Unit) {
    PosterImmersiveWindow(landscape = true)
    val context = LocalContext.current
    val settings = remember(context) { com.ella.music.data.SettingsManager.getInstance(context) }
    val fonts = rememberPlayerLyricFontState(context, settings)
    val selectedStyle by settings.posterWallLyricStyle.collectAsState(initial = 0)
    val wordLift by settings.appleMusicLyricsWordLift.collectAsState(initial = true)
    val song by playerViewModel.currentSong.collectAsState()
    val lyrics by playerViewModel.lyrics.collectAsState()
    val index by playerViewModel.currentLyricIndex.collectAsState()
    val sampled by playerViewModel.currentPosition.collectAsState()
    val playing by playerViewModel.isPlaying.collectAsState()
    val translation by playerViewModel.showLyricTranslation.collectAsState()
    val pronunciation by playerViewModel.showLyricPronunciation.collectAsState()
    val position = rememberLyricFramePosition(sampled, playing)
    val time = rememberPosterSceneTime(playing)
    val scope = rememberCoroutineScope()
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var stylePicker by remember { mutableStateOf(false) }
    var interacting by remember { mutableStateOf(false) }
    var interactionToken by remember { mutableIntStateOf(0) }
    val inputMode = LocalInputModeManager.current
    val television = LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    val remoteInput = inputMode.inputMode == InputMode.Keyboard || television
    val styleFocus = remember { FocusRequester() }
    val style = PosterLyricStyle.fromSetting(selectedStyle)
    LaunchedEffect(controlsVisible, stylePicker, playing, interacting, interactionToken, remoteInput) {
        if (!remoteInput && controlsVisible && !stylePicker && playing && !interacting) { delay(5000); controlsVisible = false }
    }
    LaunchedEffect(remoteInput) { if (remoteInput) controlsVisible = true }
    LaunchedEffect(television) { if (television) inputMode.requestInputMode(InputMode.Keyboard) }
    LaunchedEffect(inputMode.inputMode, controlsVisible, stylePicker) {
        if (inputMode.inputMode == InputMode.Keyboard && controlsVisible && !stylePicker) styleFocus.requestFocus()
    }
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            interacting = true
            interactionToken++
            try {
                do { val event = awaitPointerEvent(PointerEventPass.Final) } while (event.changes.any { it.pressed })
            } finally { interacting = false; interactionToken++ }
        }
    }.clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null
    ) { controlsVisible = !controlsVisible }) {
        PosterLyricScene(style, lyrics, index, position, time, song?.title.orEmpty(), translation, pronunciation,
            fontFamily = fonts.originalFontFamily, translationFontFamily = fonts.translationFontFamily,
            fontWeight = fonts.fontWeight, fontScale = fonts.fontScale, wordLiftEnabled = wordLift,
            modifier = Modifier.fillMaxSize())
        if (controlsVisible) {
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PosterIconButton(stringResource(R.string.common_back), onBack) {
                    Icon(MiuixIcons.Regular.Back, stringResource(R.string.common_back), tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Text(song?.title.orEmpty(), color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                PosterLyricStyleButton(stringResource(style.titleRes), Modifier.focusRequester(styleFocus)) { stylePicker = true }
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = 28.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PosterPlayControls(playing, playerViewModel::skipToPrevious, playerViewModel::togglePlayPause, playerViewModel::skipToNext)
                }
                Box(Modifier.weight(1f)) { PosterPlayerProgress(playerViewModel, compact = true) }
            }
        }
    }
    EllaMiuixBottomSheet(show = stylePicker, title = stringResource(R.string.poster_wall_lyrics_style),
        onDismissRequest = { stylePicker = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.65f).dp)
            .verticalScroll(rememberScrollState())) {
        EllaCheckOptionGroup(
            options = PosterLyricStyle.entries.map { it.ordinal.toString() to stringResource(it.titleRes) },
            selected = selectedStyle.toString(), onSelect = { value ->
                scope.launch { settings.setPosterWallLyricStyle(value.toInt()); stylePicker = false; controlsVisible = true }
            }
        )
        }
    }
}

@Composable
internal fun PosterLyricStyleButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    com.ella.music.VideoTextButton(
        text = label,
        onClick = onClick,
        modifier = modifier.testTag("theater-style-button")
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
    )
}

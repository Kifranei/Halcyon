package com.ella.music.ui.components

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.produceState
import androidx.media3.ui.AspectRatioFrameLayout
import com.ella.music.ui.player.DynamicCoverSource
import com.ella.music.ui.player.DynamicCoverVideo
import com.ella.music.ui.player.readMusicVideoPreviewFrame
import java.util.Locale
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import top.yukonga.miuix.kmp.icon.extended.GridView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize as ComposeIntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import coil3.toBitmap
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.sanitizeExportFileName
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

/** Full-resolution cover preview shared by the player and album pages. */
@Composable
internal fun CoverPreviewDialog(
    model: Any,
    models: List<Any> = listOf(model),
    initialIndex: Int = 0,
    title: String,
    saveName: String = title,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val effectiveModels = remember(model, models) {
        if (models.isNotEmpty()) models else listOf(model)
    }
    val safeInitialIndex = remember(effectiveModels, initialIndex) {
        initialIndex.coerceIn(0, (effectiveModels.size - 1).coerceAtLeast(0))
    }
    val pagerState = rememberPagerState(
        initialPage = safeInitialIndex,
        pageCount = { effectiveModels.size }
    )
    val currentModel = effectiveModels.getOrElse(pagerState.currentPage) { model }

    var scale by remember(currentModel) { mutableFloatStateOf(1f) }
    var offset by remember(currentModel) { mutableStateOf(Offset.Zero) }
    // Per-page cache: AsyncImage onSuccess often fires while a neighbour page is preloading
    // (isCurrent=false). Remembering a single nullable by currentModel cleared the badge on
    // swipe and never refilled it when Coil served the already-decoded result without a new
    // onSuccess. Keep every page's size and read the active page below (#654).
    val resolutionByPage = remember(effectiveModels) { mutableStateMapOf<Int, CoverResolution>() }
    val resolution = resolutionByPage[pagerState.currentPage]
    var viewportSize by remember { mutableStateOf(ComposeIntSize.Zero) }
    var doubleTapTargetScale by remember(currentModel) { mutableFloatStateOf(1f) }
    var doubleTapTargetOffset by remember(currentModel) { mutableStateOf(Offset.Zero) }
    var doubleTapRequest by remember(currentModel) { mutableIntStateOf(0) }
    val latestScale by rememberUpdatedState(scale)
    val latestOffset by rememberUpdatedState(offset)

    var showThumbnailSheet by remember { mutableStateOf(false) }

    LaunchedEffect(doubleTapRequest) {
        if (doubleTapRequest == 0) return@LaunchedEffect
        val initialScale = scale
        val initialOffset = offset
        animateCoverTransform(
            initialScale = initialScale,
            targetScale = doubleTapTargetScale,
            initialOffset = initialOffset,
            targetOffset = doubleTapTargetOffset
        ) { animatedScale, animatedOffset ->
            scale = animatedScale
            offset = animatedOffset.coerceWithin(
                coverPreviewPanBounds(
                    resolution = resolution,
                    viewportSize = viewportSize,
                    scale = animatedScale
                )
            )
        }
        scale = doubleTapTargetScale
        offset = doubleTapTargetOffset
    }

    val controlsVisible = scale <= 1.01f && !showThumbnailSheet

    fun handleDoubleTap(tapPosition: Offset) {
        val targetScale = if (scale > 1.01f) {
            1f
        } else {
            COVER_DOUBLE_TAP_SCALE
        }
        val targetOffset = if (targetScale <= 1f) {
            Offset.Zero
        } else {
            coverPreviewZoomOffsetForFocalPoint(
                currentOffset = offset,
                currentScale = scale,
                targetScale = targetScale,
                focalPoint = tapPosition,
                viewportSize = viewportSize
            ).coerceWithin(
                coverPreviewPanBounds(
                    resolution = resolution,
                    viewportSize = viewportSize,
                    scale = targetScale
                )
            )
        }
        doubleTapTargetScale = targetScale
        doubleTapTargetOffset = targetOffset
        doubleTapRequest++
    }

    Dialog(
        onDismissRequest = {
            if (showThumbnailSheet) {
                showThumbnailSheet = false
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ApplyHalcyonSystemBarsToCurrentWindow()
        BackHandler(enabled = showThumbnailSheet) {
            showThumbnailSheet = false
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { viewportSize = it }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = scale <= 1.01f,
                key = { pageIndex -> effectiveModels.getOrNull(pageIndex)?.toString() ?: pageIndex.toString() }
            ) { pageIndex ->
                val pageModel = effectiveModels[pageIndex]
                val isCurrent = pageIndex == pagerState.currentPage
                val isVideo = remember(pageModel) { isCoverVideoModel(context, pageModel) }

                if (isVideo) {
                    var isVideoPlaying by remember(pageModel) { mutableStateOf(false) }
                    LaunchedEffect(isCurrent) {
                        if (!isCurrent) isVideoPlaying = false
                    }
                    val videoUri = remember(pageModel) { toCoverMediaUri(pageModel) }
                    val videoSource = remember(videoUri) {
                        videoUri?.let {
                            DynamicCoverSource(
                                uri = it,
                                failureKey = "preview-video:$it"
                            )
                        }
                    }
                    val previewFrame by produceState<Bitmap?>(initialValue = null, videoUri) {
                        if (videoUri != null) {
                            value = withContext(Dispatchers.IO) {
                                context.readMusicVideoPreviewFrame(
                                    uri = videoUri,
                                    cacheKey = "preview-artist-video:$videoUri"
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { isVideoPlaying = !isVideoPlaying },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVideoPlaying && videoSource != null) {
                            DynamicCoverVideo(
                                source = videoSource,
                                isPlaying = true,
                                playAudio = false,
                                onPlaybackError = { isVideoPlaying = false },
                                modifier = Modifier.fillMaxSize(),
                                cornerRadiusDp = 0f,
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            )
                        } else {
                            if (previewFrame != null) {
                                Image(
                                    bitmap = previewFrame!!.asImageBitmap(),
                                    contentDescription = title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AsyncImage(
                                    model = remember(context, pageModel) {
                                        ImageRequest.Builder(context)
                                            .data(pageModel)
                                            .size(Size.ORIGINAL)
                                            .build()
                                    },
                                    contentDescription = title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .background(Color.Black.copy(alpha = 0.58f), CircleShape)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                CoverPreviewPlayIcon(
                                    modifier = Modifier.size(34.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = remember(context, pageModel) {
                                ImageRequest.Builder(context)
                                    .data(pageModel)
                                    .size(Size.ORIGINAL)
                                    .build()
                            },
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            onSuccess = { state ->
                                resolutionByPage[pageIndex] = CoverResolution(
                                    state.result.image.width,
                                    state.result.image.height
                                )
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (isCurrent) {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offset.x
                                        translationY = offset.y
                                    }
                                }
                        )

                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .coverPreviewGestures(
                                        currentScale = { latestScale },
                                        currentOffset = { latestOffset },
                                        viewportSize = viewportSize,
                                        resolution = resolution,
                                        onTransform = { nextScale, nextOffset ->
                                            scale = nextScale
                                            offset = nextOffset
                                        },
                                        onSettle = { settledScale, settledOffset ->
                                            scale = settledScale
                                            offset = settledOffset
                                        },
                                        onDoubleTap = ::handleDoubleTap
                                    )
                            )
                        }
                    }
                }
            }

            if (controlsVisible) {
                // Top header bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverPreviewAction(
                            contentDescription = stringResource(R.string.cover_preview_back),
                            action = CoverPreviewActionKind.Back,
                            onClick = onDismiss
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (effectiveModels.size > 1) {
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${effectiveModels.size}",
                                    color = Color.White.copy(alpha = 0.72f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                        resolution?.takeIf { it.width > 0 && it.height > 0 }?.let { size ->
                            Text(
                                text = "${size.width} × ${size.height}",
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.46f), RoundedCornerShape(99.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Bottom floating action pill
                CoverPreviewBottomBar(
                    currentPage = pagerState.currentPage,
                    totalCount = effectiveModels.size,
                    onSave = {
                        scope.launch {
                            val currentSaveName = if (effectiveModels.size > 1) {
                                "${saveName}_${pagerState.currentPage + 1}"
                            } else {
                                saveName
                            }
                            val saved = saveCoverToPictures(context, currentModel, currentSaveName)
                            Toast.makeText(
                                context,
                                context.getString(
                                    if (saved) R.string.cover_preview_saved
                                    else R.string.cover_preview_save_failed
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onShare = {
                        scope.launch {
                            val shared = writeAndShareCover(context, currentModel, title)
                            if (!shared) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.cover_preview_share_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onOpenGallery = {
                        showThumbnailSheet = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                )
            }

            // Thumbnail sheet scrim
            AnimatedVisibility(
                visible = showThumbnailSheet,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { showThumbnailSheet = false }
                )
            }

            // Thumbnail sheet content
            AnimatedVisibility(
                visible = showThumbnailSheet,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .background(
                            color = Color(0xFF1E1E1E),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                        .navigationBarsPadding()
                        .padding(top = 16.dp, bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.cover_preview_all_covers)} (${effectiveModels.size})",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.common_close),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { showThumbnailSheet = false }
                                .padding(4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        itemsIndexed(effectiveModels) { index, itemModel ->
                            val isSelected = index == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 0.5.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        scope.launch {
                                            pagerState.scrollToPage(index)
                                        }
                                        showThumbnailSheet = false
                                    }
                            ) {
                                val isItemVideo = remember(itemModel) { isCoverVideoModel(context, itemModel) }
                                AsyncImage(
                                    model = remember(context, itemModel) {
                                        ImageRequest.Builder(context)
                                            .data(itemModel)
                                            .size(300)
                                            .build()
                                    },
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (isItemVideo) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .background(
                                                color = Color.Black.copy(alpha = 0.65f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        CoverPreviewPlayIcon(
                                            modifier = Modifier.size(10.dp),
                                            tint = Color.White
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(
                                            color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.65f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverPreviewBottomBar(
    currentPage: Int,
    totalCount: Int,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(999.dp))
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverPreviewBarButton(
                icon = { PlayerCoverPreviewSaveIcon(modifier = Modifier.size(22.dp)) },
                label = stringResource(R.string.cover_preview_save),
                onClick = onSave
            )
            CoverPreviewBarButton(
                icon = { PlayerCoverPreviewShareIcon(modifier = Modifier.size(22.dp)) },
                label = stringResource(R.string.cover_preview_share),
                onClick = onShare
            )
            if (totalCount > 1) {
                CoverPreviewBarButton(
                    icon = {
                        Icon(
                            imageVector = MiuixIcons.Regular.GridView,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = "${currentPage + 1}/$totalCount",
                    onClick = onOpenGallery
                )
            }
        }
    }
}

@Composable
private fun CoverPreviewBarButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon()
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CoverPreviewAction(
    contentDescription: String,
    action: CoverPreviewActionKind,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        when (action) {
            CoverPreviewActionKind.Back -> Icon(
                imageVector = MiuixIcons.Regular.Back,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            CoverPreviewActionKind.Share -> {
                // Reuse the rounded share glyph used by the player quick actions.
                PlayerCoverPreviewShareIcon(modifier = Modifier.size(24.dp))
            }
            CoverPreviewActionKind.Save -> PlayerCoverPreviewSaveIcon(modifier = Modifier.size(24.dp))
        }
    }
}

private enum class CoverPreviewActionKind { Back, Save, Share }

@Composable
private fun PlayerCoverPreviewShareIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.10f
        val a = Offset(size.width * 0.26f, size.height * 0.58f)
        val b = Offset(size.width * 0.68f, size.height * 0.30f)
        val c = Offset(size.width * 0.70f, size.height * 0.74f)
        drawLine(Color.White, a, b, stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Color.White, a, c, stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        listOf(a, b, c).forEach { point ->
            drawCircle(color = Color.White, radius = stroke * 1.35f, center = point)
        }
    }
}

@Composable
private fun PlayerCoverPreviewSaveIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.105f
        val left = size.width * 0.23f
        val right = size.width * 0.77f
        val top = size.height * 0.16f
        val bottom = size.height * 0.84f
        drawRoundRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke * 1.5f, stroke * 1.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.29f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.61f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.37f, size.height * 0.50f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.63f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.63f, size.height * 0.50f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.63f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
private fun CoverPreviewPlayIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val path = Path().apply {
            val left = size.width * 0.30f
            val right = size.width * 0.78f
            val top = size.height * 0.20f
            val bottom = size.height * 0.80f
            val midY = size.height * 0.5f
            moveTo(left, top)
            lineTo(right, midY)
            lineTo(left, bottom)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

internal fun toCoverMediaUri(model: Any?): Uri? {
    return when (model) {
        is Uri -> model
        is File -> Uri.fromFile(model)
        is String -> {
            if (model.startsWith("content://", ignoreCase = true) || model.startsWith("file://", ignoreCase = true)) {
                Uri.parse(model)
            } else {
                Uri.fromFile(File(model))
            }
        }
        else -> null
    }
}

private val supportedCoverVideoExtensions = setOf("mp4", "m4v", "mov", "webm", "mkv")

internal fun isCoverVideoModel(context: Context, model: Any?): Boolean {
    if (model == null) return false
    val uri = toCoverMediaUri(model) ?: return false
    val path = uri.path.orEmpty()
    val ext = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (ext in supportedCoverVideoExtensions) return true
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    return mime?.startsWith("video/", ignoreCase = true) == true
}

private suspend fun saveVideoToMovies(context: Context, model: Any, title: String): Boolean {
    val srcUri = toCoverMediaUri(model) ?: return false
    return withContext(Dispatchers.IO) {
        try {
            val safeTitle = title.sanitizeExportFileName(fallback = "artist_video", maxLength = 80)
            val ext = srcUri.path.orEmpty().substringAfterLast('.', "mp4")
            val displayName = "$safeTitle.$ext"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/$ext")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}${File.separator}Halcyon"
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val dstUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false
            val inStream = context.contentResolver.openInputStream(srcUri) ?: return@withContext false
            val outStream = context.contentResolver.openOutputStream(dstUri) ?: return@withContext false
            inStream.use { input ->
                outStream.use { output ->
                    input.copyTo(output)
                }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(dstUri, values, null, null)
            true
        } catch (_: Throwable) {
            false
        }
    }
}

private suspend fun writeAndShareCover(context: Context, model: Any, title: String): Boolean {
    return runCatching {
        if (isCoverVideoModel(context, model)) {
            val srcUri = toCoverMediaUri(model) ?: return false
            val ext = srcUri.path.orEmpty().substringAfterLast('.', "mp4")
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/$ext"
                putExtra(Intent.EXTRA_STREAM, srcUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, title, srcUri)
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.cover_preview_share)))
            return true
        }

        // Coil may hand us the same Bitmap object that is currently rendered by the preview/player.
        // Sharing must only recycle a private copy; recycling the source made the preview black and
        // could later crash the player when it attempted to reuse its cover.
        val sharedBitmap = loadCoverBitmapCopy(context, model) ?: return false
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "cover_share").apply { mkdirs() }
            // Keep existing files valid while a target app is still reading their content URI.
            // Old files are cheap to remove, but never remove the whole directory before sharing.
            val staleBefore = System.currentTimeMillis() - COVER_SHARE_CACHE_MAX_AGE_MS
            dir.listFiles()
                ?.filter { it.isFile && it.lastModified() < staleBefore }
                ?.forEach(File::delete)
            try {
                val file = File(dir, "halcyon_cover_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { output ->
                    sharedBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } finally {
                sharedBitmap.recycle()
            }
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, title, uri)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.cover_preview_share)))
        true
    }.getOrElse { false }
}

private suspend fun saveCoverToPictures(context: Context, model: Any, title: String): Boolean {
    if (isCoverVideoModel(context, model)) {
        return saveVideoToMovies(context, model, title)
    }
    val bitmap = loadCoverBitmapCopy(context, model) ?: return false
    return withContext(Dispatchers.IO) {
        var uri: Uri? = null
        try {
            val safeTitle = title.sanitizeExportFileName(fallback = "cover", maxLength = 80)
            val displayName = "$safeTitle.png"
            val customFolderUri = SettingsManager.getInstance(context).coverExportFolderUri.first()
            if (customFolderUri.isNotBlank()) {
                val root = DocumentFile.fromTreeUri(context, Uri.parse(customFolderUri))
                    ?: return@withContext false
                val outputFile = root.createUniqueChildFile("image/png", displayName)
                    ?: return@withContext false
                context.contentResolver.openOutputStream(outputFile.uri)?.use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        "Unable to encode cover image"
                    }
                } ?: return@withContext false
                return@withContext true
            }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}${File.separator}Halcyon"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false
            val output = context.contentResolver.openOutputStream(uri)
                ?: error("Unable to open the saved cover output stream")
            output.use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Unable to encode cover image"
                }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        } catch (_: Throwable) {
            uri?.let { context.contentResolver.delete(it, null, null) }
            false
        } finally {
            bitmap.recycle()
        }
    }
}

private fun DocumentFile.createUniqueChildFile(mimeType: String, requestedName: String): DocumentFile? {
    val baseName = requestedName.substringBeforeLast('.', requestedName).ifBlank { "cover" }
    val extension = requestedName.substringAfterLast('.', "png")
    var candidate = requestedName
    var suffix = 1
    while (findFile(candidate) != null) {
        candidate = "$baseName ($suffix).$extension"
        suffix++
    }
    return createFile(mimeType, candidate)
}

private suspend fun loadCoverBitmapCopy(context: Context, model: Any): Bitmap? =
    withContext(Dispatchers.IO) {
        val source = (model as? Bitmap) ?: context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(model)
                .size(Size.ORIGINAL)
                .build()
        ).image?.toBitmap()
        source?.copy(Bitmap.Config.ARGB_8888, false)
    }

internal fun coverPreviewPanBounds(
    resolution: CoverResolution?,
    viewportSize: ComposeIntSize,
    scale: Float
): Offset {
    val width = viewportSize.width.toFloat()
    val height = viewportSize.height.toFloat()
    val imageWidth = resolution?.width?.toFloat() ?: return Offset.Zero
    val imageHeight = resolution.height.toFloat()
    if (width <= 0f || height <= 0f || imageWidth <= 0f || imageHeight <= 0f) {
        return Offset.Zero
    }
    val imageRatio = imageWidth / imageHeight
    val viewportRatio = width / height
    val fittedWidth: Float
    val fittedHeight: Float
    if (imageRatio >= viewportRatio) {
        fittedWidth = width
        fittedHeight = width / imageRatio
    } else {
        fittedHeight = height
        fittedWidth = height * imageRatio
    }
    return Offset(
        // The graphics layer enlarges the full viewport, while the artwork itself may be fitted
        // inside it. Keep the fitted image covering the viewport, not merely its original bounds.
        x = ((fittedWidth * scale - width) / 2f).coerceAtLeast(0f),
        y = ((fittedHeight * scale - height) / 2f).coerceAtLeast(0f)
    )
}

private fun Offset.coerceWithin(bounds: Offset): Offset = Offset(
    x = x.coerceIn(-bounds.x, bounds.x),
    y = y.coerceIn(-bounds.y, bounds.y)
)

private fun Modifier.coverPreviewGestures(
    currentScale: () -> Float,
    currentOffset: () -> Offset,
    viewportSize: ComposeIntSize,
    resolution: CoverResolution?,
    onTransform: (scale: Float, offset: Offset) -> Unit,
    onSettle: (scale: Float, offset: Offset) -> Unit,
    onDoubleTap: (Offset) -> Unit
): Modifier = pointerInput(viewportSize, resolution) {
    var lastTapUpTime = Long.MIN_VALUE
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var transformed = false
        var usedMultiplePointers = false
        var lastUpPosition: Offset? = null
        var lastUpTime = 0L
        var gestureScale = currentScale()
        var gestureOffset = currentOffset()

        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isNotEmpty()) {
                usedMultiplePointers = usedMultiplePointers || pressed.size > 1
                val zoomChange = event.calculateZoom()
                // With one finger, calculatePan is the raw screen-space delta. The gesture
                // layer is outside graphicsLayer, so this remains a true 1:1 translation even
                // after the artwork has been enlarged to 5x.
                val panChange = if (pressed.size > 1) {
                    event.calculatePan()
                } else {
                    val change = pressed.first()
                    change.position - change.previousPosition
                }
                val hasZoom = zoomChange != 1f
                // Transformable delays one-finger panning while it waits to decide whether a
                // second finger will join. Once enlarged, use the raw drag delta so artwork
                // moves exactly with the finger even at the maximum zoom.
                val hasPan = gestureScale > 1.01f && panChange != Offset.Zero
                if (hasZoom || hasPan) {
                    val nextScale = if (hasZoom) {
                        (gestureScale * zoomChange).coerceIn(COVER_MIN_SCALE, COVER_GESTURE_MAX_SCALE)
                    } else {
                        gestureScale
                    }
                    val centroid = pressed.fold(Offset.Zero) { total, change -> total + change.position } /
                        pressed.size.toFloat()
                    val scaledOffset = if (hasZoom) {
                        coverPreviewZoomOffsetForFocalPoint(
                            currentOffset = gestureOffset,
                            currentScale = gestureScale,
                            targetScale = nextScale,
                            focalPoint = centroid,
                            viewportSize = viewportSize
                        )
                    } else {
                        gestureOffset
                    }
                    gestureScale = nextScale
                    gestureOffset = scaledOffset + if (hasPan) panChange else Offset.Zero
                    onTransform(gestureScale, gestureOffset)
                    event.changes.forEach { it.consume() }
                    transformed = true
                }
            }
            event.changes.firstOrNull { it.changedToUpIgnoreConsumed() }?.let { up ->
                lastUpPosition = up.position
                lastUpTime = up.uptimeMillis
            }
        } while (event.changes.any { it.pressed })

        if (transformed) {
            val settledScale = gestureScale.coerceIn(1f, COVER_MAX_SCALE)
            val settledOffset = if (settledScale <= 1f) {
                Offset.Zero
            } else {
                gestureOffset.coerceWithin(
                    coverPreviewPanBounds(
                        resolution = resolution,
                        viewportSize = viewportSize,
                        scale = settledScale
                    )
                )
            }
            onSettle(settledScale, settledOffset)
        }

        // One recognizer owns both double-tap and transform input. The former two-layer version
        // allowed the tap recognizer to consume the same stream that panning needed, introducing
        // an inconsistent delay after zooming.
        val tapUpPosition = lastUpPosition
        if (!transformed && !usedMultiplePointers && tapUpPosition != null) {
            val isDoubleTap = lastUpTime - lastTapUpTime in 0..viewConfiguration.doubleTapTimeoutMillis
            if (isDoubleTap) {
                onDoubleTap(tapUpPosition)
                lastTapUpTime = Long.MIN_VALUE
            } else {
                lastTapUpTime = lastUpTime
            }
        } else {
            lastTapUpTime = Long.MIN_VALUE
        }
    }
}

internal fun coverPreviewZoomOffsetForFocalPoint(
    currentOffset: Offset,
    currentScale: Float,
    targetScale: Float,
    focalPoint: Offset,
    viewportSize: ComposeIntSize
): Offset {
    if (currentScale <= 0f) return currentOffset
    val center = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
    val focalOffset = focalPoint - center
    val scaleRatio = targetScale / currentScale
    // Keep the content under the user's finger stationary. The old expression used the
    // focal-point term with the opposite sign, which made a double tap zoom to the point
    // mirrored across the viewport centre instead of the tapped point.
    return currentOffset * scaleRatio + focalOffset * (1f - scaleRatio)
}

private suspend fun animateCoverTransform(
    initialScale: Float,
    targetScale: Float,
    initialOffset: Offset,
    targetOffset: Offset,
    onFrame: (scale: Float, offset: Offset) -> Unit
) {
    if (initialScale == targetScale && initialOffset == targetOffset) {
        onFrame(targetScale, targetOffset)
        return
    }
    animate(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    ) { progress, _ ->
        onFrame(
            initialScale + (targetScale - initialScale) * progress,
            Offset(
                x = initialOffset.x + (targetOffset.x - initialOffset.x) * progress,
                y = initialOffset.y + (targetOffset.y - initialOffset.y) * progress
            )
        )
    }
}

internal data class CoverResolution(val width: Int, val height: Int)

private const val COVER_MIN_SCALE = 0.82f
// 5× stays below the overly aggressive 6× experiment while still providing a larger
// inspection range than the previous 4× release.
private const val COVER_MAX_SCALE = 5f
private const val COVER_GESTURE_MAX_SCALE = 5.5f
private const val COVER_DOUBLE_TAP_SCALE = COVER_MAX_SCALE
private const val COVER_SHARE_CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1000L

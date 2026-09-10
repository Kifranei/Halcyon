package com.ella.music.ui.about

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.BuildConfig
import com.ella.music.R
import com.ella.music.ui.components.LocalSettingsCardFrosting
import com.ella.music.ui.components.frostedCardColor
import com.ella.music.ui.components.frostedCardModifier
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme



/**
 * Centered HyperOS "No Update / Checking" view (mirrors no_update_fragment.xml).
 */
@Composable
internal fun HyperOsNoUpdateView(
    state: UpdateUiState,
    isDark: Boolean,
    showCurrentLog: Boolean,
    onToggleCurrentLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Halcyon",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        when (state) {
            UpdateUiState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "CheckingRotation")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "Rotation"
                    )

                    Icon(
                        imageVector = MiuixIcons.Regular.Refresh,
                        contentDescription = null,
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer { rotationZ = rotation },
                        tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )

                    Text(
                        text = "v${BuildConfig.VERSION_NAME}  " + stringResource(R.string.update_checking),
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                    )
                }
            }
            is UpdateUiState.Ready -> {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}  " + stringResource(R.string.update_already_latest),
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                )

                if (state.release.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onToggleCurrentLog)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val linkColor = if (isDark) Color(0xFF6B9BFF) else Color(0xFF2655FF)
                        Text(
                            text = stringResource(
                                if (showCurrentLog) R.string.update_collapse_changelog else R.string.update_current_version_log
                            ),
                            fontSize = 13.5.sp,
                            color = linkColor,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = linkColor
                        )
                    }
                }
            }
            is UpdateUiState.Error -> {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    fontSize = 15.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.update_check_failed),
                    fontSize = 14.sp,
                    color = Color(0xFFE53935),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = state.message,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}

/**
 * Top header card when a new update is found (mirrors new_update_fragment.xml / os_new_update_background).
 */
@Composable
internal fun HyperOsNewUpdateHeaderCard(
    release: GithubRelease,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val frosting = LocalSettingsCardFrosting.current
    val cardColor = frostedCardColor(frosting, defaultAlpha = if (isDark) 0.35f else 0.88f)

    Card(
        modifier = frostedCardModifier(modifier, cornerRadius = 18.dp, frosting = frosting),
        cornerRadius = 18.dp,
        colors = CardDefaults.defaultColors(color = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Halcyon",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Halcyon ${release.tagName}",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val primaryColor = if (isDark) Color(0xFF2050FF) else Color(0xFF2655FF)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(primaryColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.update_new_version_found),
                        fontSize = 11.5.sp,
                        color = if (isDark) Color(0xFF7A9DFF) else primaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val archLabel = release.matchedAsset?.let { detectArchLabel(it.name) }
                if (archLabel != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = archLabel,
                            fontSize = 11.5.sp,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (release.publishedAt.isNotBlank()) {
                    Text(
                        text = release.publishedAt,
                        fontSize = 12.5.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

/**
 * Changelog card (mirrors card_layout_graphic_log.xml).
 */
@Composable
internal fun HyperOsChangelogCard(
    title: String,
    changelog: String,
    modifier: Modifier = Modifier
) {
    val frosting = LocalSettingsCardFrosting.current
    val cardColor = frostedCardColor(frosting, defaultAlpha = 0.42f)

    Card(
        modifier = frostedCardModifier(modifier, cornerRadius = 18.dp, frosting = frosting),
        cornerRadius = 18.dp,
        colors = CardDefaults.defaultColors(color = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Check,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }

                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            ReleaseMarkdown(
                markdown = changelog.ifBlank { stringResource(R.string.update_empty_changelog) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Pinned bottom action button (mirrors check_btn from Xiaomi system updater).
 * When no update: Secondary neutral pill button (height 50dp).
 * When new update found: Primary vibrant Xiaomi blue pill button (height 50dp).
 */
@Composable
internal fun HyperOsBottomActionButton(
    hasUpdate: Boolean,
    isChecking: Boolean,
    isDark: Boolean,
    downloadState: UpdateDownloadState,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonHeight = 50.dp
    val pillShape = CircleShape
    val xiaomiBlue = if (isDark) Color(0xFF2050FF) else Color(0xFF2655FF)

    when (downloadState) {
        is UpdateDownloadState.Downloading -> {
            val trackColor = if (isDark) Color(0xFF19284D) else Color(0xFFE4EDFF)
            val targetProgress = downloadState.progress.coerceIn(0.01f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = targetProgress,
                animationSpec = tween(durationMillis = 200, easing = LinearEasing),
                label = "DownloadProgress"
            )
            val pct = (animatedProgress * 100).toInt().coerceIn(0, 100)
            val speedText = if (downloadState.speedBytesPerSec > 0) {
                UpdateDownloadManager.formatSpeed(downloadState.speedBytesPerSec)
            } else ""
            val displayText = if (speedText.isNotBlank()) {
                "$pct% · $speedText"
            } else {
                "$pct%"
            }

            val textColor by animateColorAsState(
                targetValue = if (animatedProgress >= 0.45f) Color.White else (if (isDark) Color(0xFF7A9DFF) else Color(0xFF2655FF)),
                animationSpec = tween(200),
                label = "DownloadTextColor"
            )

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(buttonHeight)
                    .clip(pillShape)
                    .background(trackColor),
                contentAlignment = Alignment.CenterStart
            ) {
                // Progressive Xiaomi blue fill
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .background(xiaomiBlue)
                )

                // Live progress and speed text
                Text(
                    text = displayText,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
            }
        }

        is UpdateDownloadState.Completed -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(buttonHeight)
                    .clip(pillShape)
                    .background(xiaomiBlue)
                    .clickable(onClick = onButtonClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.update_install),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        is UpdateDownloadState.Failed -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(buttonHeight)
                    .clip(pillShape)
                    .background(xiaomiBlue)
                    .clickable(onClick = onButtonClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.update_download_retry),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        UpdateDownloadState.Idle -> {
            val (bgColor, contentColor) = when {
                hasUpdate -> xiaomiBlue to Color.White
                else -> {
                    val neutral = if (isDark) Color(0xFF2A2A2F) else Color(0xFFEBEBF0)
                    val text = if (isChecking) {
                        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    }
                    neutral to text
                }
            }

            val buttonText = when {
                hasUpdate -> stringResource(R.string.update_download)
                isChecking -> stringResource(R.string.update_checking)
                else -> stringResource(R.string.update_check_button)
            }

            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(buttonHeight)
                    .clip(pillShape)
                    .background(bgColor)
                    .clickable(
                        enabled = !isChecking,
                        onClick = onButtonClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buttonText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }
    }
}

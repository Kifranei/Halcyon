package com.ella.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.StarRate
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/** A title prepared for display without changing the original audio tag. */
internal data class SongTitlePresentation(
    val text: String,
    val isExplicit: Boolean
)

private val explicitTitleToken = Regex("(?i)\\bexplicit\\b")
private val emptyTitleBracket = Regex("[\\(\\[\\{]\\s*[\\)\\]\\}]")
private val repeatedTitleWhitespace = Regex("\\s{2,}")

/**
 * Normalizes distributor-added Explicit markers into a dedicated content-advisory badge.
 * The raw title remains untouched for metadata, matching, sharing, and file operations.
 */
internal fun String.toSongTitlePresentation(): SongTitlePresentation {
    if (!explicitTitleToken.containsMatchIn(this)) {
        return SongTitlePresentation(text = this, isExplicit = false)
    }

    val cleaned = replace(explicitTitleToken, "")
        .replace(emptyTitleBracket, "")
        .replace(repeatedTitleWhitespace, " ")
        .trim()
        .trim('-', '–', '—', '·', '•', '|', '(', ')', '[', ']', '{', '}')
        .trim()

    return SongTitlePresentation(
        text = cleaned.ifBlank { trim() },
        isExplicit = true
    )
}

@Composable
internal fun ExplicitBadge(
    contentColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp
) {
    val labelColor = if (contentColor.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.80f)
    } else {
        Color.White.copy(alpha = 0.92f)
    }
    Box(
        modifier = modifier
            .height(height)
            .defaultMinSize(minWidth = height)
            .clip(RoundedCornerShape(3.dp))
            .background(contentColor.copy(alpha = 0.70f))
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "E",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            maxLines = 1
        )
    }
}

@Composable
internal fun ExplicitSongTitle(
    title: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign = TextAlign.Start,
    softWrap: Boolean = true,
    titleModifier: Modifier = Modifier
) {
    val presentation = remember(title) { title.toSongTitlePresentation() }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = presentation.text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            softWrap = softWrap,
            // Leave only the title's measured width before the badge, rather than pushing E
            // to the far edge of every row.
            modifier = titleModifier.weight(1f, fill = false)
        )
        if (presentation.isExplicit) {
            Spacer(modifier = Modifier.width(2.dp))
            ExplicitBadge(contentColor = color)
        }
    }
}

@Composable
internal fun RatingStarIcon(
    filled: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = if (filled) Icons.Rounded.StarRate else Icons.Rounded.StarBorder,
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

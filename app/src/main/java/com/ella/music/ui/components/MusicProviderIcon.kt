package com.ella.music.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Original provider colors; do not tint brand artwork like a monochrome action icon. */
@Composable
internal fun MusicProviderIcon(iconRes: Int, description: String?, modifier: Modifier = Modifier) {
    Box(modifier.size(28.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        Image(painterResource(iconRes), description, modifier = Modifier.size(24.dp))
    }
}

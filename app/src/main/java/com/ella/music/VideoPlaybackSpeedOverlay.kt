package com.ella.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.VIDEO_PLAYBACK_SPEEDS
import com.ella.music.data.videoSpeedLabel
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun VideoPlaybackSpeedOverlay(speed: Float, holdSpeedPercent: Int, onSpeed: (Float)->Unit,
    onHoldSpeed: (Int)->Unit, onDismiss: ()->Unit) {
    var input by remember(holdSpeedPercent) { mutableStateOf((holdSpeedPercent/100f).toString().removeSuffix(".0")) }
    BoxWithConstraints(Modifier.fillMaxSize().clickable(onClick=onDismiss),contentAlignment=Alignment.CenterEnd) {
        val panelWidth = (maxWidth * .46f).coerceIn(280.dp, 560.dp).coerceAtMost(maxWidth)
        Column(Modifier.width(panelWidth).fillMaxHeight()
            .windowInsetsPadding(WindowInsets.displayCutout)
            .clip(RoundedCornerShape(topStart=24.dp,bottomStart=24.dp))
            .background(Color.Black.copy(alpha=.68f)).clickable { }
            .verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.video_speed_settings),color=Color.White,fontWeight=FontWeight.Bold,fontSize=20.sp,modifier=Modifier.weight(1f))
                VideoTextButton(stringResource(R.string.common_close),onDismiss)
            }
            VIDEO_PLAYBACK_SPEEDS.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    pair.forEach { choice -> VideoTextButton(videoSpeedLabel(choice),{onSpeed(choice)},selected=choice==speed,modifier=Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.video_hold_speed),color=Color.White,fontWeight=FontWeight.Bold)
            Text(stringResource(R.string.video_hold_speed_summary),color=Color.White.copy(alpha=.65f),fontSize=13.sp)
            VIDEO_PLAYBACK_SPEEDS.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    pair.forEach { choice -> VideoTextButton(videoSpeedLabel(choice),{onHoldSpeed((choice*100).toInt())},selected=(choice*100).toInt()==holdSpeedPercent,modifier=Modifier.weight(1f)) }
                }
            }
            Text(stringResource(R.string.video_hold_custom),color=Color.White.copy(alpha=.8f),fontSize=13.sp)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                TextField(value=input,onValueChange={value -> input=value.filter {it.isDigit() || it=='.'}.take(5)},
                    textStyle=MiuixTheme.textStyles.main.copy(color=Color.White),singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),insideMargin=DpSize(12.dp,8.dp),cornerRadius=12.dp,
                    colors=TextFieldDefaults.textFieldColors(backgroundColor=Color.White.copy(alpha=.12f)),modifier=Modifier.weight(1f))
                VideoTextButton(stringResource(R.string.common_confirm),{
                    input.toFloatOrNull()?.takeIf {it.isFinite() && it in .5f..5f}?.let { onHoldSpeed(kotlin.math.round(it*100).toInt()) }
                })
            }
        }
    }
}

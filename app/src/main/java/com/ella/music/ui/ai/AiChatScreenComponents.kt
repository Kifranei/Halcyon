package com.ella.music.ui.ai

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.ui.components.EllaMiuixChip
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AiChatTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = MiuixIcons.Regular.Back,
                contentDescription = stringResource(R.string.common_back),
                tint = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = stringResource(R.string.ai_chat_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.ai_chat_disclaimer),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

@Composable
internal fun AiChatSessionStrip(
    sessions: List<AiChatSessionMeta>,
    currentSessionId: String?,
    onSessionClick: (String) -> Unit,
    onSessionLongClick: (AiChatSessionMeta) -> Unit,
    onCreateSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sessions.forEach { meta ->
            AiChatSessionChip(
                title = meta.title,
                selected = meta.id == currentSessionId,
                onClick = { onSessionClick(meta.id) },
                onLongClick = { onSessionLongClick(meta) },
            )
        }
        if (sessions.size < MAX_SESSIONS) {
            AiChatNewSessionChip(onClick = onCreateSession)
        }
    }
}

@Composable
private fun AiChatSessionChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    EllaMiuixChip(
        text = title,
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick
    )
}

@Composable
private fun AiChatNewSessionChip(onClick: () -> Unit) {
    EllaMiuixChip(
        selected = false,
        onClick = onClick,
        horizontalPadding = 12.dp
    ) { contentColor ->
        Icon(
            imageVector = MiuixIcons.Regular.Add,
            contentDescription = stringResource(R.string.ai_chat_new_session),
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
internal fun AiChatInputBar(
    input: String,
    sending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = input,
            onValueChange = onInputChange,
            label = stringResource(R.string.ai_chat_input_hint),
            singleLine = false,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            enabled = input.isNotBlank() && !sending,
            onClick = onSend,
        ) {
            Text(stringResource(R.string.ai_chat_send))
        }
    }
}

@Composable
internal fun AiChatEmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = MiuixIcons.Regular.Messages,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.78f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.ai_chat_empty_title),
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.ai_chat_empty_summary),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}

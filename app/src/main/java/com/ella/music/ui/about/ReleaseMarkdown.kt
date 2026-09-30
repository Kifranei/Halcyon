package com.ella.music.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ReleaseMarkdown(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val primary = MiuixTheme.colorScheme.onSurface
    val secondary = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val accent = MiuixTheme.colorScheme.primary
    val codeBackground = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    SelectionContainer {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        BasicText(
                            text = inlineMarkdown(block.text, accent, codeBackground),
                            style = MiuixTheme.textStyles.main.copy(
                                color = primary,
                                fontSize = if (block.level <= 2) 18.sp else 15.sp,
                                lineHeight = if (block.level <= 2) 24.sp else 21.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    is MarkdownBlock.Paragraph -> {
                        BasicText(
                            text = inlineMarkdown(block.text, accent, codeBackground),
                            style = MiuixTheme.textStyles.main.copy(
                                color = secondary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        )
                    }
                    is MarkdownBlock.Bullet -> {
                        Row {
                            Text(
                                text = "•",
                                color = accent,
                                fontSize = 13.sp,
                                modifier = Modifier.width(18.dp)
                            )
                            BasicText(
                                text = inlineMarkdown(block.text, accent, codeBackground),
                                style = MiuixTheme.textStyles.main.copy(
                                    color = secondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    is MarkdownBlock.Numbered -> {
                        Row {
                            Text(
                                text = "${block.number}.",
                                color = accent,
                                fontSize = 13.sp,
                                modifier = Modifier.width(28.dp)
                            )
                            BasicText(
                                text = inlineMarkdown(block.text, accent, codeBackground),
                                style = MiuixTheme.textStyles.main.copy(
                                    color = secondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    is MarkdownBlock.Quote -> {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            BasicText(
                                text = inlineMarkdown(block.text, accent, codeBackground),
                                style = MiuixTheme.textStyles.main.copy(
                                    color = secondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            )
                        }
                    }
                    is MarkdownBlock.Code -> {
                        BasicText(
                            text = block.text,
                            style = MiuixTheme.textStyles.main.copy(
                                color = primary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(codeBackground)
                                .padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Numbered(val number: Int, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.replace("\r\n", "\n").split('\n')
    val paragraph = StringBuilder()
    var codeFence = false
    val code = StringBuilder()

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotBlank()) blocks += MarkdownBlock.Paragraph(text)
        paragraph.clear()
    }

    for (rawLine in lines) {
        val line = rawLine.trimEnd()
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (codeFence) {
                blocks += MarkdownBlock.Code(code.toString().trimEnd())
                code.clear()
            } else {
                flushParagraph()
            }
            codeFence = !codeFence
            continue
        }
        if (codeFence) {
            code.appendLine(line)
            continue
        }
        if (trimmed.isBlank()) {
            flushParagraph()
            continue
        }
        val heading = Regex("^(#{1,6})\\s+(.+)$").matchEntire(trimmed)
        val bullet = Regex("^[-*+]\\s+(.+)$").matchEntire(trimmed)
        val numbered = Regex("^(\\d+)\\.\\s+(.+)$").matchEntire(trimmed)
        val quote = Regex("^>\\s?(.+)$").matchEntire(trimmed)
        when {
            heading != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            }
            bullet != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Bullet(bullet.groupValues[1])
            }
            numbered != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Numbered(numbered.groupValues[1].toIntOrNull() ?: 1, numbered.groupValues[2])
            }
            quote != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Quote(quote.groupValues[1])
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
    }
    if (codeFence && code.isNotBlank()) blocks += MarkdownBlock.Code(code.toString().trimEnd())
    flushParagraph()
    return blocks.ifEmpty { listOf(MarkdownBlock.Paragraph(markdown)) }
}

private fun inlineMarkdown(
    text: String,
    accent: Color,
    codeBackground: Color
) = buildAnnotatedString {
    val pattern = Regex("""(\*\*[^*]+\*\*|`[^`]+`|\[[^]]+]\([^)]+\))""")
    var cursor = 0
    pattern.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(token.removeSurrounding("**"))
            }
            token.startsWith("`") -> withStyle(SpanStyle(color = accent, background = codeBackground)) {
                append(token.removeSurrounding("`"))
            }
            token.startsWith("[") -> {
                val label = token.substringAfter('[').substringBefore("](")
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)) {
                    append(label)
                }
            }
            else -> append(token)
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

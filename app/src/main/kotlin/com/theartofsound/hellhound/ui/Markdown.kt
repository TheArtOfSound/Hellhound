package com.theartofsound.hellhound.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Renders a small, dependency-free subset of Markdown for streaming LLM
 * replies: ATX headers, bold (** or __), italic (* or _), inline `code`, and
 * lists. Block code fences (```) are flattened to plain text — they remain
 * legible without a custom block layout. Paragraphs are preserved.
 */
@Composable
fun rememberMarkdown(source: String): AnnotatedString {
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val codeFg = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(source, codeBg, codeFg) {
        renderMarkdown(source, codeBg, codeFg)
    }
}

private fun renderMarkdown(
    source: String,
    codeBg: Color,
    codeFg: Color
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var inFence = false
    val lines = source.split('\n')
    lines.forEachIndexed { index, raw ->
        val line = raw

        if (line.trimStart().startsWith("```")) {
            inFence = !inFence
            // Skip the fence line itself.
            if (index < lines.lastIndex) builder.append('\n')
            return@forEachIndexed
        }

        if (inFence) {
            val start = builder.length
            builder.append(line)
            builder.addStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, color = codeFg, background = codeBg),
                start, builder.length
            )
            if (index < lines.lastIndex) builder.append('\n')
            return@forEachIndexed
        }

        // Headers
        val headerMatch = HEADER_REGEX.matchEntire(line)
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length
            val content = headerMatch.groupValues[2]
            val size = headerSize(level)
            val start = builder.length
            appendInline(builder, content, codeBg, codeFg)
            builder.addStyle(
                SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = size),
                start, builder.length
            )
            if (index < lines.lastIndex) builder.append('\n')
            return@forEachIndexed
        }

        // Bullet/numbered list markers — keep marker, render rest as inline.
        val bulletMatch = BULLET_REGEX.matchEntire(line)
        if (bulletMatch != null) {
            builder.append("•  ")
            appendInline(builder, bulletMatch.groupValues[2], codeBg, codeFg)
            if (index < lines.lastIndex) builder.append('\n')
            return@forEachIndexed
        }

        appendInline(builder, line, codeBg, codeFg)
        if (index < lines.lastIndex) builder.append('\n')
    }

    return builder.toAnnotatedString()
}

private fun headerSize(level: Int): TextUnit = when (level) {
    1 -> 22.sp
    2 -> 19.sp
    else -> 17.sp
}

private val HEADER_REGEX = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET_REGEX = Regex("""^\s*([-*+]|\d+\.)\s+(.*)$""")

private fun appendInline(
    builder: AnnotatedString.Builder,
    text: String,
    codeBg: Color,
    codeFg: Color
) {
    var i = 0
    while (i < text.length) {
        val rest = text.substring(i)

        // Inline code: `...`
        if (rest.startsWith('`')) {
            val end = rest.indexOf('`', startIndex = 1)
            if (end > 0) {
                val content = rest.substring(1, end)
                val start = builder.length
                builder.append(content)
                builder.addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, color = codeFg, background = codeBg),
                    start, builder.length
                )
                i += end + 1
                continue
            }
        }

        // Bold: **...** or __...__
        if (rest.startsWith("**") || rest.startsWith("__")) {
            val token = rest.substring(0, 2)
            val end = rest.indexOf(token, startIndex = 2)
            if (end > 0) {
                val content = rest.substring(2, end)
                val start = builder.length
                appendInline(builder, content, codeBg, codeFg)
                builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, builder.length)
                i += end + 2
                continue
            }
        }

        // Italic: *...* or _..._  (single char, must not be ** that we already handled)
        if ((rest.startsWith('*') || rest.startsWith('_')) && rest.length > 1) {
            val token = rest[0]
            val end = rest.indexOf(token, startIndex = 1)
            if (end > 0 && end != 1) {
                val content = rest.substring(1, end)
                val start = builder.length
                appendInline(builder, content, codeBg, codeFg)
                builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, builder.length)
                i += end + 1
                continue
            }
        }

        builder.append(text[i])
        i++
    }
}

package com.m57.hermescontrol.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrm.latex.renderer.LatexAutoWrap
import com.hrm.latex.renderer.measure.LatexMeasurerState
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme
import com.m57.hermescontrol.data.remote.GatewayFileClient
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import com.m57.hermescontrol.theme.SearchHighlightColors
import com.m57.hermescontrol.theme.Spacing
import com.m57.hermescontrol.theme.WithoutChatFontScale
import com.m57.hermescontrol.theme.searchHighlightColors
import com.m57.hermescontrol.util.BidiUtils

private val URL_PATTERN = Regex("""https?://[^\s)>\[\]"'‘’]+""")
private val TABLE_COL_WIDTH = 160.dp
private val FN_DEF_RE = Regex("""^\[\^([^\]]+)\]:\s*(.*)$""")
private val TASK_LINE_RE = Regex("""^(\s*)[-*+]\s+\[([ xX])\]\s+(.*)$""")
private val BULLET_LINE_RE = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val ORDERED_LINE_RE = Regex("""^(\s*)(\d+)\.\s+(.*)$""")
private val LIST_ITEM_LINE_RE = Regex("""^(\s*)(?:[-*+]\s+(?:\[([ xX])\]\s+)?|(\d+)\.\s+)(.*)$""")

private fun bidiTextDirection(isRtl: Boolean): TextDirection = if (isRtl) TextDirection.Rtl else TextDirection.Ltr

/**
 * Renders chat assistant text as Markdown — but ONLY once the message has finished streaming.
 * While [isStreaming] is true we show the raw text to avoid flicker / re-parse churn, then swap
 * to the formatted view on completion (and for all historical/restored messages).
 *
 * Supports: fenced ```code``` blocks (horizontal scroll + copy), inline `code`, **bold**, *italic*,
 * ***bold italic***, ~~strike~~, ==highlight==, ^sup^ / ~sub~, <kbd>keys</kbd>, headings,
 * bullet/ordered/task lists, > blockquotes, definition lists, tables, --- rules, footnotes, and
 * [links](url) / bare URLs, and inline/display LaTeX math using `$…$` / `$$…$$`.
 */
@Composable
fun MarkdownText(
    text: String,
    textColor: Color,
    isStreaming: Boolean = false,
    searchQuery: String = "",
    isCurrentMatch: Boolean = false,
    modifier: Modifier = Modifier,
    onImageClick: (ImageViewerModel) -> Unit = {},
) {
    val statusColors = LocalHermesStatusColors.current
    val highlights = searchHighlightColors(statusColors)
    if (isStreaming) {
        val isRtl = remember(text) { BidiUtils.isRtlText(text) }
        val streamingDirection = if (isRtl) LayoutDirection.Rtl else LocalLayoutDirection.current
        CompositionLocalProvider(LocalLayoutDirection provides streamingDirection) {
            Text(
                text = if (isRtl) BidiUtils.anchorTrailingRtl(text) else text,
                color = textColor,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        textDirection = bidiTextDirection(isRtl),
                    ),
                modifier = modifier,
            )
        }
        return
    }

    val linkColor = MaterialTheme.colorScheme.primary
    val blocks = remember(text) { parseBlocks(text) }
    val latexMeasurer = rememberLatexMeasurer()

    Column(modifier = modifier.fillMaxWidth()) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Code -> {
                    WithoutChatFontScale {
                        com.m57.hermescontrol.ui.chat.components.CodeBlockCard(
                            code = block.code,
                            language = block.language,
                            onCopy = { /* clipboard handled internally */ },
                            searchQuery = searchQuery,
                            isCurrentMatch = isCurrentMatch,
                        )
                    }
                }

                is MdBlock.Math -> {
                    val matchColors = formulaSearchColors(block.latex, searchQuery, isCurrentMatch, highlights)
                    LatexAutoWrap(
                        latex = block.latex,
                        config =
                            LatexConfig(
                                fontSize = 18.sp,
                                theme =
                                    LatexTheme.light(
                                        color = matchColors?.second ?: textColor,
                                        backgroundColor = Color.Transparent,
                                    ),
                                accessibilityEnabled = true,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (matchColors != null) {
                                        Modifier.background(matchColors.first)
                                    } else {
                                        Modifier
                                    },
                                ).padding(vertical = 6.dp),
                    )
                }

                is MdBlock.Hr -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Spacing.sm),
                        color = textColor.copy(alpha = 0.25f),
                    )
                }

                is MdBlock.Heading -> {
                    val fontSize =
                        when (block.level) {
                            1 -> 22.sp
                            2 -> 20.sp
                            3 -> 18.sp
                            4 -> 16.sp
                            5 -> 15.sp
                            else -> 14.sp
                        }
                    val isRtl = remember(block.text) { BidiUtils.isRtlText(block.text) }
                    val blockDirection = if (isRtl) LayoutDirection.Rtl else LocalLayoutDirection.current
                    CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                        MarkdownInlineText(
                            text = block.text,
                            textColor = textColor,
                            latexMeasurer = latexMeasurer,
                            style =
                                MaterialTheme.typography.bodyMedium
                                    .copy(
                                        fontSize = fontSize,
                                        fontWeight = FontWeight.Bold,
                                        textDirection = bidiTextDirection(isRtl),
                                    ),
                            searchQuery = searchQuery,
                            isCurrentMatch = isCurrentMatch,
                            linkColor = linkColor,
                            highlights = highlights,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }

                is MdBlock.Bullet -> {
                    val indent = (block.level * 16).dp
                    val isRtl = remember(block.text) { BidiUtils.isRtlText(block.text) }
                    val blockDirection = if (isRtl) LayoutDirection.Rtl else LocalLayoutDirection.current
                    CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = indent)
                                    .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            val bulletChar =
                                when (block.level % 3) {
                                    0 -> "•"
                                    1 -> "◦"
                                    else -> "▪"
                                }
                            Text(
                                text = bulletChar,
                                color = textColor,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                            MarkdownInlineText(
                                text = block.text,
                                textColor = textColor,
                                latexMeasurer = latexMeasurer,
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        textDirection = bidiTextDirection(isRtl),
                                    ),
                                searchQuery = searchQuery,
                                isCurrentMatch = isCurrentMatch,
                                linkColor = linkColor,
                                highlights = highlights,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.Task -> {
                    val indent = (block.level * 16).dp
                    val isRtl = remember(block.text) { BidiUtils.isRtlText(block.text) }
                    val blockDirection = if (isRtl) LayoutDirection.Rtl else LocalLayoutDirection.current
                    CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = indent)
                                    .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector =
                                    if (block.checked) {
                                        Icons.Outlined.CheckBox
                                    } else {
                                        Icons.Outlined.CheckBoxOutlineBlank
                                    },
                                contentDescription = null,
                                tint =
                                    if (block.checked) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        textColor.copy(
                                            alpha = 0.6f,
                                        )
                                    },
                                modifier = Modifier.size(18.dp).padding(top = 1.dp, end = 6.dp),
                            )
                            MarkdownInlineText(
                                text = block.text,
                                textColor = textColor,
                                latexMeasurer = latexMeasurer,
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        textDirection = bidiTextDirection(isRtl),
                                    ),
                                searchQuery = searchQuery,
                                isCurrentMatch = isCurrentMatch,
                                linkColor = linkColor,
                                highlights = highlights,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.Ordered -> {
                    val indent = (block.level * 16).dp
                    val isRtl = remember(block.text) { BidiUtils.isRtlText(block.text) }
                    val blockDirection = if (isRtl) LayoutDirection.Rtl else LocalLayoutDirection.current
                    CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = indent)
                                    .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "${block.index}.",
                                color = textColor,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                            MarkdownInlineText(
                                text = block.text,
                                textColor = textColor,
                                latexMeasurer = latexMeasurer,
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        textDirection = bidiTextDirection(isRtl),
                                    ),
                                searchQuery = searchQuery,
                                isCurrentMatch = isCurrentMatch,
                                linkColor = linkColor,
                                highlights = highlights,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.Quote -> {
                    val isRtl = remember(block.text) { BidiUtils.isRtlText(block.text) }
                    val blockDirection = if (isRtl) LayoutDirection.Rtl else LocalLayoutDirection.current
                    CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(textColor.copy(alpha = 0.35f)),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            MarkdownInlineText(
                                text = block.text,
                                textColor = textColor,
                                latexMeasurer = latexMeasurer,
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontStyle = FontStyle.Italic,
                                        textDirection = bidiTextDirection(isRtl),
                                    ),
                                searchQuery = searchQuery,
                                isCurrentMatch = isCurrentMatch,
                                linkColor = linkColor,
                                highlights = highlights,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                is MdBlock.Image -> {
                    val source = remember(block.uri) { resolveImageSource(block.uri) }
                    val isGif =
                        remember(block.uri) {
                            block.uri.contains(".gif", ignoreCase = true) ||
                                block.uri.startsWith("data:image/gif", ignoreCase = true)
                        }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs),
                    ) {
                        com.m57.hermescontrol.ui.chat.components.GifImageThumbnail(
                            model = source.model,
                            gatewayPath = source.gatewayPath,
                            contentDescription = block.alt.ifBlank { null },
                            isGif = isGif,
                            onClick = {
                                onImageClick(
                                    ImageViewerModel(
                                        model = source.model,
                                        gatewayPath = source.gatewayPath,
                                        name = block.alt,
                                        mimeType = if (isGif) "image/gif" else "image/*",
                                    ),
                                )
                            },
                        )
                    }
                }

                is MdBlock.DefList -> {
                    val isRtl =
                        remember(block.items) {
                            block.items.any { item ->
                                BidiUtils.isRtlText(item.term) ||
                                    item.definitions.any { BidiUtils.isRtlText(it) }
                            }
                        }
                    val blockDirection = if (isRtl) LayoutDirection.Rtl else LocalLayoutDirection.current
                    CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            block.items.forEach { item ->
                                val itemRtl = BidiUtils.isRtlText(item.term)
                                Text(
                                    text = if (itemRtl) BidiUtils.anchorTrailingRtl(item.term) else item.term,
                                    color = textColor,
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            textDirection = bidiTextDirection(itemRtl),
                                        ),
                                )
                                item.definitions.forEach { def ->
                                    val defRtl = BidiUtils.isRtlText(def)
                                    Text(
                                        text = if (defRtl) BidiUtils.anchorTrailingRtl(def) else def,
                                        color = textColor,
                                        style =
                                            MaterialTheme.typography.bodyMedium.copy(
                                                textDirection = bidiTextDirection(defRtl),
                                            ),
                                        modifier = Modifier.padding(start = Spacing.md, bottom = 2.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    }
                }

                is MdBlock.Table -> {
                    MarkdownTable(
                        block = block,
                        textColor = textColor,
                        latexMeasurer = latexMeasurer,
                        searchQuery = searchQuery,
                        isCurrentMatch = isCurrentMatch,
                        linkColor = linkColor,
                        highlights = highlights,
                    )
                }

                is MdBlock.Footnotes -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = textColor.copy(alpha = 0.2f),
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Footnotes",
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                        block.notes.forEach { note ->
                            val isRtl = remember(note.text) { BidiUtils.isRtlText(note.text) }
                            val noteDirection = if (isRtl) LayoutDirection.Rtl else LocalLayoutDirection.current
                            CompositionLocalProvider(LocalLayoutDirection provides noteDirection) {
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                    Text(
                                        text = "[${note.id}] ",
                                        color = textColor,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    )
                                    MarkdownInlineText(
                                        text = note.text,
                                        textColor = textColor,
                                        latexMeasurer = latexMeasurer,
                                        style =
                                            MaterialTheme.typography.bodySmall.copy(
                                                textDirection = bidiTextDirection(isRtl),
                                            ),
                                        searchQuery = searchQuery,
                                        isCurrentMatch = isCurrentMatch,
                                        linkColor = linkColor,
                                        highlights = highlights,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }

                is MdBlock.Paragraph -> {
                    val isRtl = remember(block.text) { BidiUtils.isRtlText(block.text) }
                    val blockDirection = if (isRtl) LayoutDirection.Rtl else LocalLayoutDirection.current
                    CompositionLocalProvider(LocalLayoutDirection provides blockDirection) {
                        MarkdownInlineText(
                            text = block.text,
                            textColor = textColor,
                            latexMeasurer = latexMeasurer,
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    textDirection = bidiTextDirection(isRtl),
                                ),
                            searchQuery = searchQuery,
                            isCurrentMatch = isCurrentMatch,
                            linkColor = linkColor,
                            highlights = highlights,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Matches a Markdown image: `![alt](uri)`. The `uri` may be an http(s) URL
 * (gateway-served media the phone can reach), a `data:image/...;base64,...`
 * data URL (e.g. agent-delivered inline media), or any other resolvable model
 * string (Coil handles all three). Bare `![]()` with empty uri is skipped.
 */
private val IMAGE_RE = Regex("""^!\[([^\]]*)\]\(([^)\s]+)\s*\)""")

private fun tryParseImage(line: String): MdBlock.Image? {
    val m = IMAGE_RE.matchAt(line, 0) ?: return null
    val uri = m.groupValues[2].trim()
    if (uri.isEmpty()) return null
    return MdBlock.Image(uri = uri, alt = m.groupValues[1].trim())
}

@Composable
private fun MarkdownInlineText(
    text: String,
    textColor: Color,
    latexMeasurer: LatexMeasurerState,
    style: TextStyle,
    searchQuery: String,
    isCurrentMatch: Boolean,
    linkColor: Color,
    highlights: SearchHighlightColors,
    modifier: Modifier = Modifier,
) {
    val isRtl = remember(text) { BidiUtils.isRtlText(text) }
    val resolvedStyle = style.copy(textDirection = bidiTextDirection(isRtl))
    val markup = remember(text) { buildInlineMathMarkup(text) }
    if (markup.math.isEmpty()) {
        Text(
            text =
                remember(text, searchQuery, isCurrentMatch, textColor, linkColor, highlights) {
                    anchorTrailingRtlPresentation(
                        parseInlineSource(
                            text,
                            textColor,
                            searchQuery,
                            isCurrentMatch,
                            linkColor,
                            highlights,
                            isRtl,
                        ),
                        isRtl,
                    )
                },
            color = textColor,
            style = resolvedStyle,
            modifier = modifier,
        )
        return
    }

    val inlineContent =
        remember(markup, latexMeasurer, style.fontSize, textColor, searchQuery, isCurrentMatch, highlights) {
            val inlineContent = mutableMapOf<String, InlineTextContent>()
            markup.math.forEach { placeholder ->
                val matchColors = formulaSearchColors(placeholder.latex, searchQuery, isCurrentMatch, highlights)
                val config =
                    LatexConfig(
                        fontSize = style.fontSize,
                        theme =
                            LatexTheme.light(
                                color = matchColors?.second ?: textColor,
                                backgroundColor = Color.Transparent,
                            ),
                        accessibilityEnabled = true,
                    )
                latexMeasurer.inlineContent(placeholder.latex, config)?.let {
                    inlineContent[placeholder.id] = it
                }
            }
            inlineContent
        }
    val parsed =
        remember(markup.source, searchQuery, isCurrentMatch, textColor, linkColor, highlights, isRtl) {
            parseInlineSource(markup.source, textColor, searchQuery, isCurrentMatch, linkColor, highlights, isRtl)
        }
    val byMarker = remember(markup) { markup.math.associateBy(InlineMathPlaceholder::marker) }
    val annotated =
        remember(parsed, byMarker, inlineContent, searchQuery, isCurrentMatch, highlights, isRtl) {
            anchorTrailingRtlPresentation(
                buildAnnotatedString {
                    var plainStart = 0
                    parsed.forEachIndexed { index, char ->
                        val placeholder = byMarker[char] ?: return@forEachIndexed
                        append(parsed.subSequence(plainStart, index))

                        val styles = parsed.spanStyles.filter { index in it.start until it.end }
                        val links = parsed.getLinkAnnotations(index, index + 1)
                        styles.forEach { pushStyle(it.item) }
                        links.forEach { pushLink(it.item) }
                        formulaSearchColors(placeholder.latex, searchQuery, isCurrentMatch, highlights)
                            ?.let { (background, foreground) ->
                                pushStyle(SpanStyle(background = background, color = foreground))
                            }
                        if (placeholder.id in inlineContent) {
                            appendInlineContent(placeholder.id, placeholder.latex)
                        } else {
                            append(placeholder.latex)
                        }
                        val pushedSearchStyle =
                            formulaSearchColors(placeholder.latex, searchQuery, isCurrentMatch, highlights) != null
                        repeat(styles.size + links.size + if (pushedSearchStyle) 1 else 0) { pop() }
                        plainStart = index + 1
                    }
                    append(parsed.subSequence(plainStart, parsed.length))
                },
                isRtl,
            )
        }
    Text(
        text = annotated,
        inlineContent = inlineContent,
        color = textColor,
        style = resolvedStyle,
        modifier = modifier,
    )
}

@Composable
private fun MarkdownTable(
    block: MdBlock.Table,
    textColor: Color,
    latexMeasurer: LatexMeasurerState,
    searchQuery: String,
    isCurrentMatch: Boolean,
    linkColor: Color,
    highlights: SearchHighlightColors,
) {
    val tableDirection = tableStructureDirection(LocalLayoutDirection.current)
    val headerBg = textColor.copy(alpha = 0.08f)
    val alignments = block.alignments
    CompositionLocalProvider(LocalLayoutDirection provides tableDirection) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = Spacing.xs),
        ) {
            // Header row
            Row(modifier = Modifier.background(headerBg)) {
                block.header.forEachIndexed { idx, cell ->
                    MarkdownInlineText(
                        text = cell,
                        textColor = textColor,
                        latexMeasurer = latexMeasurer,
                        searchQuery = searchQuery,
                        isCurrentMatch = isCurrentMatch,
                        linkColor = linkColor,
                        highlights = highlights,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                textAlign = tableTextAlign(alignments.getOrNull(idx)),
                            ),
                        modifier =
                            Modifier
                                .width(TABLE_COL_WIDTH)
                                .padding(6.dp),
                    )
                }
            }
            HorizontalDivider(color = textColor.copy(alpha = 0.25f))
            // Body rows
            block.rows.forEach { row ->
                Row {
                    row.forEachIndexed { idx, cell ->
                        MarkdownInlineText(
                            text = cell,
                            textColor = textColor,
                            latexMeasurer = latexMeasurer,
                            searchQuery = searchQuery,
                            isCurrentMatch = isCurrentMatch,
                            linkColor = linkColor,
                            highlights = highlights,
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    textAlign = tableTextAlign(alignments.getOrNull(idx)),
                                ),
                            modifier =
                                Modifier
                                    .width(TABLE_COL_WIDTH)
                                    .padding(6.dp),
                        )
                    }
                }
                HorizontalDivider(color = textColor.copy(alpha = 0.1f))
            }
        }
    }
}

internal fun tableStructureDirection(ambient: LayoutDirection): LayoutDirection = ambient

private fun tableTextAlign(align: TableAlign?): TextAlign =
    when (align) {
        TableAlign.CENTER -> TextAlign.Center
        TableAlign.RIGHT -> TextAlign.End
        else -> TextAlign.Start
    }

internal sealed interface InlineMathSegment {
    data class Text(val value: String) : InlineMathSegment

    data class Math(val latex: String) : InlineMathSegment
}

internal data class InlineMathPlaceholder(
    val marker: Char,
    val latex: String,
    val id: String,
)

internal data class InlineMathMarkup(
    val source: String,
    val math: List<InlineMathPlaceholder>,
)

internal fun buildInlineMathMarkup(text: String): InlineMathMarkup {
    val segments = splitInlineMath(text)
    val mathCount = segments.count { it is InlineMathSegment.Math }
    if (mathCount == 0) return InlineMathMarkup(text, emptyList())

    val availableMarkers =
        ('\uE000'..'\uF8FF')
            .asSequence()
            .filterNot(text::contains)
            .take(mathCount)
            .toList()
    if (availableMarkers.size != mathCount) return InlineMathMarkup(text, emptyList())

    val math = mutableListOf<InlineMathPlaceholder>()
    val source =
        buildString {
            segments.forEach { segment ->
                when (segment) {
                    is InlineMathSegment.Text -> append(segment.value)
                    is InlineMathSegment.Math -> {
                        val marker = availableMarkers[math.size]
                        math +=
                            InlineMathPlaceholder(
                                marker = marker,
                                latex = segment.latex,
                                id = "latex-${marker.code}",
                            )
                        append(marker)
                    }
                }
            }
        }
    return InlineMathMarkup(source, math)
}

private fun formulaSearchColors(
    latex: String,
    searchQuery: String,
    isCurrentMatch: Boolean,
    highlights: SearchHighlightColors,
): Pair<Color, Color>? {
    if (searchQuery.isEmpty() || !latex.contains(searchQuery, ignoreCase = true)) return null
    return if (isCurrentMatch) {
        highlights.currentSearchBackground to highlights.currentSearchForeground
    } else {
        highlights.searchBackground to highlights.searchForeground
    }
}

/** Splits `$…$` without treating escaped dollars or inline-code contents as math. */
internal fun splitInlineMath(text: String): List<InlineMathSegment> {
    val segments = mutableListOf<InlineMathSegment>()
    var plainStart = 0
    var i = 0

    while (i < text.length) {
        if (text[i] == '`' && !text.isEscaped(i)) {
            var delimiterEnd = i + 1
            while (delimiterEnd < text.length && text[delimiterEnd] == '`') delimiterEnd++
            val delimiterLength = delimiterEnd - i
            val delimiter = "`".repeat(delimiterLength)
            val codeEnd = text.indexOf(delimiter, delimiterEnd)
            i = if (codeEnd == -1) text.length else codeEnd + delimiterLength
            continue
        }
        if (text.startsWith("\\(", i) && !text.isEscaped(i)) {
            val end = text.indexOfUnescaped("\\)", i + 2)
            if (end != -1 && end > i + 2) {
                if (plainStart < i) segments.add(InlineMathSegment.Text(text.substring(plainStart, i)))
                segments.add(InlineMathSegment.Math(text.substring(i + 2, end)))
                i = end + 2
                plainStart = i
                continue
            }
        }
        if (text[i] != '$' || text.isEscaped(i) || text.getOrNull(i + 1) == '$') {
            i++
            continue
        }

        var end = i + 1
        while (end < text.length && (text[end] != '$' || text.isEscaped(end))) end++
        if (end >= text.length || end == i + 1 || text[end - 1].isWhitespace()) {
            i++
            continue
        }

        if (plainStart < i) segments.add(InlineMathSegment.Text(text.substring(plainStart, i)))
        segments.add(InlineMathSegment.Math(text.substring(i + 1, end)))
        i = end + 1
        plainStart = i
    }

    if (plainStart < text.length) segments.add(InlineMathSegment.Text(text.substring(plainStart)))
    return segments.ifEmpty { listOf(InlineMathSegment.Text(text)) }
}

private fun String.isEscaped(index: Int): Boolean {
    var slashes = 0
    var i = index - 1
    while (i >= 0 && this[i] == '\\') {
        slashes++
        i--
    }
    return slashes % 2 == 1
}

private fun String.indexOfUnescaped(
    token: String,
    startIndex: Int,
): Int {
    var index = indexOf(token, startIndex)
    while (index != -1 && isEscaped(index)) {
        index = indexOf(token, index + 1)
    }
    return index
}

private fun tryParseDisplayMath(
    lines: List<String>,
    start: Int,
): Pair<MdBlock.Math, Int>? {
    val first = lines[start].trim()
    if (first.startsWith("\\[")) {
        if (first.length > 4 && first.endsWith("\\]")) {
            val latex = first.substring(2, first.length - 2).trim()
            return latex.takeIf { it.isNotEmpty() }?.let { MdBlock.Math(it) to start + 1 }
        }
        if (first != "\\[") return null
        val end = (start + 1 until lines.size).firstOrNull { lines[it].trim() == "\\]" } ?: return null
        val latex = lines.subList(start + 1, end).joinToString("\n").trim()
        return latex.takeIf { it.isNotEmpty() }?.let { MdBlock.Math(it) to end + 1 }
    }
    if (!first.startsWith("$$")) return null
    if (first.length > 4 && first.endsWith("$$")) {
        val latex = first.substring(2, first.length - 2).trim()
        return latex.takeIf { it.isNotEmpty() }?.let { MdBlock.Math(it) to start + 1 }
    }
    if (first != "$$") return null

    val end = (start + 1 until lines.size).firstOrNull { lines[it].trim() == "$$" } ?: return null
    val latex = lines.subList(start + 1, end).joinToString("\n").trim()
    return latex.takeIf { it.isNotEmpty() }?.let { MdBlock.Math(it) to end + 1 }
}

/**
 * Splits source text into Markdown blocks. Fenced code blocks (```...```) are extracted first;
 * everything else is grouped into headings, lists, tables, rules, footnotes, or paragraphs.
 */
internal fun parseBlocks(src: String): List<MdBlock> {
    val lines = src.lines()
    val blocks = mutableListOf<MdBlock>()
    val footnotes = mutableListOf<Footnote>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Footnote definition: [^id]: text  (collected, not rendered inline)
        val fnMatch = FN_DEF_RE.matchAt(line, 0)
        if (fnMatch != null) {
            footnotes.add(Footnote(fnMatch.groupValues[1], fnMatch.groupValues[2]))
            i++
            continue
        }

        val displayMath = tryParseDisplayMath(lines, i)
        if (displayMath != null) {
            blocks.add(displayMath.first)
            i = displayMath.second
            continue
        }

        val codeFence = parseCodeFenceStart(line)
        when {
            codeFence != null -> {
                val end = (i + 1 until lines.size).firstOrNull { isCodeFenceEnd(lines[it], codeFence) }
                if (end != null) {
                    blocks.add(
                        MdBlock.Code(
                            code = lines.subList(i + 1, end).joinToString("\n"),
                            language = codeFence.language,
                        ),
                    )
                    i = end + 1
                } else {
                    blocks.add(
                        MdBlock.Code(
                            code = lines.subList(i + 1, lines.size).joinToString("\n"),
                            language = codeFence.language,
                        ),
                    )
                    i = lines.size
                }
            }

            line.isBlank() -> {
                i++
            }

            isHorizontalRule(line) -> {
                blocks.add(MdBlock.Hr)
                i++
            }

            // Heading: requires a space after the '#' run so "#572" stays a paragraph
            isValidHeading(line) -> {
                val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
                blocks.add(MdBlock.Heading(level, line.substring(level).trim()))
                i++
            }

            isTableStart(lines, i) -> {
                val (header, alignments, body) = parseTable(lines, i)
                blocks.add(MdBlock.Table(header, alignments, body))
                i += body.size + 2
            }

            line.startsWith(">") -> {
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].startsWith(">")) {
                    quote.add(lines[i].removePrefix(">").trim())
                    i++
                }
                blocks.add(MdBlock.Quote(quote.joinToString("\n")))
            }

            // Definition list: term line followed by one+ ": definition" lines
            isDefListStart(lines, i) -> {
                val term = line.trim()
                val defs = mutableListOf<String>()
                i++
                while (i < lines.size && lines[i].trim().startsWith(":")) {
                    defs.add(lines[i].trim().removePrefix(":").trim())
                    i++
                }
                blocks.add(MdBlock.DefList(listOf(DefItem(term, defs))))
            }

            LIST_ITEM_LINE_RE.matches(line) -> {
                val (listBlocks, nextIndex) = parseList(lines, i)
                blocks.addAll(listBlocks)
                i = nextIndex
            }

            // Standalone Markdown image: ![alt](uri) on its own line.
            // Inline images inside a paragraph are left as-is (rendered as text)
            // to keep scope tight; standalone is the common agent-media case.
            line.isNotBlank() && tryParseImage(line) != null -> {
                blocks.add(tryParseImage(line)!!)
                i++
            }

            else -> {
                i = fallthroughToParagraph(lines, i, blocks)
            }
        }
    }

    if (footnotes.isNotEmpty()) {
        blocks.add(MdBlock.Footnotes(footnotes.map { FnNote(it.id, it.text) }))
    }
    return blocks
}

private sealed interface ParsedListItem {
    val indent: Int
    val contentIndent: Int
    val text: String
    val continuationLines: MutableList<String>

    data class Bullet(
        override val indent: Int,
        override val contentIndent: Int,
        override val text: String,
        override val continuationLines: MutableList<String> = mutableListOf(),
    ) : ParsedListItem

    data class Task(
        override val indent: Int,
        override val contentIndent: Int,
        val checked: Boolean,
        override val text: String,
        override val continuationLines: MutableList<String> = mutableListOf(),
    ) : ParsedListItem

    data class Ordered(
        override val indent: Int,
        override val contentIndent: Int,
        val number: Int,
        override val text: String,
        override val continuationLines: MutableList<String> = mutableListOf(),
    ) : ParsedListItem
}

private fun computeListIndent(prefix: String): Int {
    var count = 0
    for (character in prefix) {
        count =
            if (character == '\t') {
                count + 4 - (count % 4)
            } else {
                count + 1
            }
    }
    return count
}

private fun computeListContentIndent(
    line: String,
    match: MatchResult,
    textGroup: Int,
): Int {
    val textStart = match.groups[textGroup]?.range?.first ?: line.length
    return computeListIndent(line.substring(0, textStart))
}

private fun parseList(
    lines: List<String>,
    startIndex: Int,
): Pair<List<MdBlock>, Int> {
    val items = mutableListOf<ParsedListItem>()
    var index = startIndex

    while (index < lines.size) {
        val line = lines[index]
        val taskMatch = TASK_LINE_RE.matchEntire(line)
        val bulletMatch = if (taskMatch == null) BULLET_LINE_RE.matchEntire(line) else null
        val orderedMatch =
            if (taskMatch == null && bulletMatch == null) {
                ORDERED_LINE_RE.matchEntire(line)
            } else {
                null
            }

        when {
            taskMatch != null -> {
                val indent = computeListIndent(taskMatch.groupValues[1])
                items.add(
                    ParsedListItem.Task(
                        indent = indent,
                        contentIndent = computeListContentIndent(line, taskMatch, textGroup = 3),
                        checked = taskMatch.groupValues[2].equals("x", ignoreCase = true),
                        text = taskMatch.groupValues[3],
                    ),
                )
                index++
            }

            bulletMatch != null -> {
                val indent = computeListIndent(bulletMatch.groupValues[1])
                items.add(
                    ParsedListItem.Bullet(
                        indent = indent,
                        contentIndent = computeListContentIndent(line, bulletMatch, textGroup = 2),
                        text = bulletMatch.groupValues[2],
                    ),
                )
                index++
            }

            orderedMatch != null -> {
                val indent = computeListIndent(orderedMatch.groupValues[1])
                val numberText = orderedMatch.groupValues[2]
                items.add(
                    ParsedListItem.Ordered(
                        indent = indent,
                        contentIndent = computeListContentIndent(line, orderedMatch, textGroup = 3),
                        number = numberText.toIntOrNull() ?: 1,
                        text = orderedMatch.groupValues[3],
                    ),
                )
                index++
            }

            line.isBlank() -> {
                val nextItem = lines.indexOfFirstFrom(index + 1) { it.isNotBlank() }
                if (nextItem < 0 || !LIST_ITEM_LINE_RE.matches(lines[nextItem])) break
                index = nextItem
            }

            else -> {
                val prefix = line.takeWhile { it == ' ' || it == '\t' }
                val continuationIndent = computeListIndent(prefix)
                if (
                    items.isEmpty() ||
                    continuationIndent < items.last().contentIndent ||
                    continuationIndent >= items.last().contentIndent + 4 ||
                    isListBlockBoundary(lines, index)
                ) {
                    break
                }
                items.last().continuationLines.add(line.trim())
                index++
            }
        }
    }

    val indentStack = mutableListOf<Int>()
    val blocks =
        items.map { item ->
            val level = resolveListLevel(indentStack, item.indent)
            val fullText =
                buildList {
                    add(item.text)
                    addAll(item.continuationLines)
                }.joinToString(" ")
            when (item) {
                is ParsedListItem.Bullet -> MdBlock.Bullet(fullText, level)
                is ParsedListItem.Task -> MdBlock.Task(item.checked, fullText, level)
                is ParsedListItem.Ordered -> MdBlock.Ordered(item.number, fullText, level)
            }
        }
    return blocks to index
}

private fun isListBlockBoundary(
    lines: List<String>,
    index: Int,
): Boolean {
    val trimmed = lines[index].trimStart()
    return trimmed.startsWith("```") ||
        trimmed.startsWith(">") ||
        trimmed.startsWith("$$") ||
        isValidHeading(trimmed) ||
        isHorizontalRule(trimmed) ||
        isTableStart(lines, index) ||
        isDefListStart(lines, index) ||
        tryParseImage(trimmed) != null ||
        FN_DEF_RE.matchAt(trimmed, 0) != null
}

private fun List<String>.indexOfFirstFrom(
    startIndex: Int,
    predicate: (String) -> Boolean,
): Int {
    for (index in startIndex until size) {
        if (predicate(this[index])) return index
    }
    return -1
}

private fun resolveListLevel(
    indentStack: MutableList<Int>,
    indent: Int,
): Int {
    if (indentStack.isEmpty()) {
        indentStack.add(indent)
        return 0
    }
    while (indentStack.size > 1 && indent < indentStack.last()) {
        indentStack.removeAt(indentStack.lastIndex)
    }
    if (indent > indentStack.last()) {
        indentStack.add(indent)
        return indentStack.lastIndex
    }
    if (indent < indentStack.last()) {
        indentStack[0] = indent
    }
    return indentStack.lastIndex
}

private fun isHorizontalRule(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.length < 3) return false
    val c = trimmed[0]
    return (c == '-' || c == '*' || c == '_') && trimmed.all { it == c }
}

private fun isTableStart(
    lines: List<String>,
    i: Int,
): Boolean {
    val l = lines[i]
    if (!l.contains('|')) return false
    if (i + 1 >= lines.size) return false
    return isTableSeparator(lines[i + 1])
}

private fun isTableSeparator(line: String): Boolean {
    val t = line.trim().trim('|')
    if (t.isEmpty()) return false
    // every cell must be only dashes/colons/spaces, with at least one dash
    return t.split('|').all { cell ->
        val c = cell.trim()
        c.isNotEmpty() && c.all { it == '-' || it == ':' || it == ' ' } && c.any { it == '-' }
    }
}

private fun parseTable(
    lines: List<String>,
    i: Int,
): Triple<List<String>, List<TableAlign>, List<List<String>>> {
    val header = splitRow(lines[i])
    val alignments =
        splitRow(lines[i + 1]).map { cell ->
            val c = cell.trim()
            when {
                c.startsWith(":") && c.endsWith(":") -> TableAlign.CENTER
                c.endsWith(":") -> TableAlign.RIGHT
                c.startsWith(":") -> TableAlign.LEFT
                else -> TableAlign.LEFT
            }
        }
    val body = mutableListOf<List<String>>()
    var j = i + 2
    while (j < lines.size && lines[j].contains('|') && lines[j].trim().isNotEmpty()) {
        body.add(splitRow(lines[j]))
        j++
    }
    return Triple(header, alignments, body)
}

private fun splitRow(line: String): List<String> {
    val trimmed = line.trim().trim('|')
    return trimmed.split('|').map { it.trim() }
}

private data class CodeFenceInfo(
    val fenceChar: Char,
    val fenceLength: Int,
    val language: String?,
)

private fun parseCodeFenceStart(line: String): CodeFenceInfo? {
    if (line.firstOrNull()?.isWhitespace() == true) return null
    val trimmed = line
    if (trimmed.length < 3) return null

    val fenceChar = trimmed.first()
    if (fenceChar != '`' && fenceChar != '~') return null

    val fenceLength = trimmed.takeWhile { it == fenceChar }.length
    if (fenceLength < 3) return null

    val info = trimmed.substring(fenceLength).trim()
    if (fenceChar == '`' && info.contains('`')) return null

    return CodeFenceInfo(
        fenceChar = fenceChar,
        fenceLength = fenceLength,
        language = info.ifBlank { null },
    )
}

private fun isCodeFenceEnd(
    line: String,
    fence: CodeFenceInfo,
): Boolean {
    if (line.firstOrNull()?.isWhitespace() == true) return false
    val trimmed = line
    val fenceLength = trimmed.takeWhile { it == fence.fenceChar }.length
    if (fenceLength < fence.fenceLength) return false

    return trimmed.substring(fenceLength).trim().isEmpty()
}

private fun isDefListStart(
    lines: List<String>,
    i: Int,
): Boolean {
    val l = lines[i].trim()
    if (l.isBlank() || l.startsWith("#") || l.startsWith(">") || parseCodeFenceStart(l) != null) return false
    if (LIST_ITEM_LINE_RE.matches(lines[i])) return false
    if (i + 1 >= lines.size) return false
    return lines[i + 1].trim().startsWith(":")
}

private fun isValidHeading(line: String): Boolean {
    if (!line.startsWith("#")) return false
    val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
    return level < line.length && line[level] == ' '
}

private fun fallthroughToParagraph(
    lines: List<String>,
    start: Int,
    blocks: MutableList<MdBlock>,
): Int {
    var i = start
    val para = mutableListOf<String>()
    while (
        i < lines.size &&
        lines[i].isNotBlank() &&
        parseCodeFenceStart(lines[i]) == null &&
        !isValidHeading(lines[i]) &&
        !lines[i].startsWith(">") &&
        !LIST_ITEM_LINE_RE.matches(lines[i]) &&
        !isHorizontalRule(lines[i]) &&
        !isTableStart(lines, i) &&
        !isDefListStart(lines, i) &&
        tryParseDisplayMath(lines, i) == null &&
        FN_DEF_RE.matchAt(lines[i], 0) == null
    ) {
        para.add(lines[i])
        i++
    }
    if (para.isNotEmpty()) blocks.add(MdBlock.Paragraph(para.joinToString("\n")))
    return i
}

/**
 * Inline Markdown -> AnnotatedString. Handles `code`, **bold**, *italic*, ***bold italic***,
 * ~~strike~~, ==highlight==, ^sup^, ~sub~, <kbd>keys</kbd>, [^ref] footnotes, [text](url) and
 * bare URLs, plus search-query highlighting.
 */
internal fun parseInline(
    text: String,
    textColor: Color,
    searchQuery: String,
    isCurrentMatch: Boolean,
    linkColor: Color,
    highlights: SearchHighlightColors,
    isRtl: Boolean = BidiUtils.isRtlText(text),
): AnnotatedString =
    parseInlineSource(
        text =
            splitInlineMath(text).joinToString(separator = "") { segment ->
                when (segment) {
                    is InlineMathSegment.Math -> segment.latex
                    is InlineMathSegment.Text -> segment.value
                }
            },
        textColor = textColor,
        searchQuery = searchQuery,
        isCurrentMatch = isCurrentMatch,
        linkColor = linkColor,
        highlights = highlights,
        isRtl = isRtl,
    )

private fun AnnotatedString.isolateLtrSnippet(isRtl: Boolean): AnnotatedString =
    if (isRtl && BidiUtils.isLtrSnippet(text)) {
        buildAnnotatedString {
            append(BidiUtils.LRI)
            append(this@isolateLtrSnippet)
            append(BidiUtils.PDI)
        }
    } else {
        this
    }

// Index exact backtick runs once; unmatched runs must not repeatedly scan the suffix.
private fun nextCodeRuns(text: String): Map<Int, Int> {
    val runs = mutableListOf<Pair<Int, Int>>()
    var index = 0
    while (index < text.length) {
        if (text[index] != '`') {
            index++
            continue
        }
        val start = index
        while (index < text.length && text[index] == '`') index++
        runs.add(start to index - start)
    }
    val nextByLength = mutableMapOf<Int, Int>()
    val next = mutableMapOf<Int, Int>()
    for ((start, length) in runs.asReversed()) {
        nextByLength[length]?.let { next[start] = it }
        nextByLength[length] = start
    }
    return next
}

// Pair runs once, discarding unmatched inner openers instead of hiding an outer close.
// Each opener is pushed/popped at most once; code and link destinations are opaque.
internal fun emphasisPairs(
    text: String,
    codeRuns: Map<Int, Int>,
): Map<Int, Int> {
    val pairs = mutableMapOf<Int, Int>()
    val starts = mutableListOf<Int>()
    val widths = mutableListOf<Int>()
    val byWidth = Array(4) { mutableListOf<Int>() }
    val nextParen = IntArray(text.length + 1) { -1 }
    for (index in text.indices.reversed()) {
        nextParen[index] = if (text[index] == ')') index else nextParen[index + 1]
    }

    fun consume(
        start: Int,
        width: Int,
    ) {
        val match = byWidth[width].lastOrNull()
        if (match == null) {
            byWidth[width].add(starts.size)
            starts.add(start)
            widths.add(width)
        } else {
            pairs[starts[match]] = start
            while (starts.size > match) {
                val removedWidth = widths.removeAt(widths.lastIndex)
                byWidth[removedWidth].removeAt(byWidth[removedWidth].lastIndex)
                starts.removeAt(starts.lastIndex)
            }
        }
    }
    var index = 0
    while (index < text.length) {
        when {
            text[index] == '`' -> {
                var end = index
                while (end < text.length && text[end] == '`') end++
                index = codeRuns[index]?.plus(end - index) ?: end
            }
            text.startsWith("](", index) && nextParen[index + 2] >= 0 -> {
                index = nextParen[index + 2] + 1
            }
            text[index] == '*' -> {
                var end = index
                while (end < text.length && text[end] == '*') end++
                val width = end - index
                if (width in 1..3) {
                    if (width == 3 && widths.size >= 2 &&
                        widths.last() + widths[widths.lastIndex - 1] == 3
                    ) {
                        val inner = widths.last()
                        consume(index, inner)
                        consume(index + inner, 3 - inner)
                    } else {
                        consume(index, width)
                    }
                }
                index = end
            }
            else -> index++
        }
    }
    return pairs
}

private fun parseInlineSource(
    text: String,
    textColor: Color,
    searchQuery: String,
    isCurrentMatch: Boolean,
    linkColor: Color,
    highlights: SearchHighlightColors,
    isRtl: Boolean = BidiUtils.isRtlText(text),
    depth: Int = 0,
): AnnotatedString {
    // Bound recursive rendering and repeated indexing of nested substrings.
    if (depth >= 32) return AnnotatedString(text)
    return buildAnnotatedString {
        var i = 0
        val src = text
        val codeRuns = nextCodeRuns(src)
        val emphasis = emphasisPairs(src, codeRuns)

        while (i < src.length) {
            // Cheap prefix gate: URL_PATTERN only matches at "http(s)://", so skip the regex elsewhere.
            val urlMatch =
                if (src.startsWith("http", i, ignoreCase = true)) URL_PATTERN.matchAt(src, i) else null
            when {
                // Inline code is opaque to emphasis parsing, and delimiters must match by run length.
                src[i] == '`' -> {
                    var runEnd = i
                    while (runEnd < src.length && src[runEnd] == '`') runEnd++
                    val runLength = runEnd - i
                    val matchingEnd = codeRuns[i] ?: -1
                    if (matchingEnd >= 0) {
                        val raw = src.substring(runEnd, matchingEnd)
                        val content = if (isRtl) BidiUtils.wrapInlineCodeLtrIsolate(raw) else raw
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                background = textColor.copy(alpha = 0.08f),
                            ),
                        ) {
                            append(content)
                        }
                        i = matchingEnd + runLength
                    } else {
                        append(src.substring(i, runEnd))
                        i = runEnd
                    }
                }

                // Combined / nested emphasis delimiters.
                src.startsWith("***", i) -> {
                    val end = emphasis[i] ?: -1
                    if (end >= 0) {
                        val raw = src.substring(i + 3, end)
                        val content =
                            parseInlineSource(
                                raw,
                                textColor,
                                searchQuery,
                                isCurrentMatch,
                                linkColor,
                                highlights,
                                isRtl && !BidiUtils.isLtrSnippet(raw),
                                depth + 1,
                            ).isolateLtrSnippet(isRtl)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                            append(content)
                        }
                        i = end + 3
                    } else {
                        append("***")
                        i += 3
                    }
                }

                src.startsWith("**", i) -> {
                    val end = emphasis[i] ?: -1
                    if (end != -1) {
                        val raw = src.substring(i + 2, end)
                        val nested =
                            parseInlineSource(
                                raw,
                                textColor,
                                searchQuery,
                                isCurrentMatch,
                                linkColor,
                                highlights,
                                isRtl && !BidiUtils.isLtrSnippet(raw),
                                depth + 1,
                            ).isolateLtrSnippet(isRtl)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(nested) }
                        i = end + 2
                    } else {
                        append(src[i])
                        i++
                    }
                }

                // ~~strike~~
                src.startsWith("~~", i) -> {
                    val end = src.indexOf("~~", i + 2)
                    if (end != -1) {
                        val raw = src.substring(i + 2, end)
                        val toAppend = if (isRtl) BidiUtils.wrapLtrIsolate(raw) else raw
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(toAppend)
                        }
                        i = end + 2
                    } else {
                        append(src[i])
                        i++
                    }
                }

                // *italic*; bold spans keep their styles while nested text is appended.
                src.startsWith("*", i) -> {
                    val close = emphasis[i] ?: -1
                    if (close > i + 1) {
                        val raw = src.substring(i + 1, close)
                        val italic = SpanStyle(fontStyle = FontStyle.Italic)
                        val nested =
                            parseInlineSource(
                                raw,
                                textColor,
                                searchQuery,
                                isCurrentMatch,
                                linkColor,
                                highlights,
                                isRtl && !BidiUtils.isLtrSnippet(raw),
                                depth + 1,
                            ).isolateLtrSnippet(isRtl)
                        withStyle(italic) { append(nested) }
                        i = close + 1
                    } else {
                        append(src[i])
                        i++
                    }
                }

                // ==highlight==
                src.startsWith("==", i) -> {
                    val end = src.indexOf("==", i + 2)
                    if (end != -1) {
                        val raw = src.substring(i + 2, end)
                        val toAppend = if (isRtl) BidiUtils.wrapLtrIsolate(raw) else raw
                        withStyle(SpanStyle(background = highlights.markupBackground)) {
                            append(toAppend)
                        }
                        i = end + 2
                    } else {
                        append(src[i])
                        i++
                    }
                }

                // ^superscript^
                src.startsWith("^", i) -> {
                    val end = src.indexOf('^', i + 1)
                    if (end != -1 && end > i + 1) {
                        withStyle(SpanStyle(baselineShift = BaselineShift.Superscript)) {
                            append(src.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(src[i])
                        i++
                    }
                }

                // ~subscript~ (single tilde; ~~ handled above)
                src.startsWith("~", i) -> {
                    val end = src.indexOf('~', i + 1)
                    if (end != -1 && end > i + 1) {
                        withStyle(SpanStyle(baselineShift = BaselineShift.Subscript)) {
                            append(src.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(src[i])
                        i++
                    }
                }

                // <kbd>key</kbd>
                src.startsWith("<kbd>", i) -> {
                    val end = src.indexOf("</kbd>", i)
                    if (end != -1) {
                        val raw = src.substring(i + 5, end)
                        val toAppend = if (isRtl) BidiUtils.wrapLtrIsolate(raw) else raw
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = textColor.copy(alpha = 0.12f),
                            ),
                        ) {
                            append(toAppend)
                        }
                        i = end + 6
                    } else {
                        append(src[i])
                        i++
                    }
                }

                // [^ref] footnote marker -> superscript
                src.startsWith("[^", i) -> {
                    val close = src.indexOf(']', i)
                    if (close != -1) {
                        val id = src.substring(i + 2, close)
                        withStyle(
                            SpanStyle(
                                baselineShift = BaselineShift.Superscript,
                                color = linkColor,
                                fontWeight = FontWeight.Bold,
                            ),
                        ) {
                            append("[$id]")
                        }
                        i = close + 1
                    } else {
                        append(src[i])
                        i++
                    }
                }

                // [text](url)
                src.startsWith("[", i) -> {
                    val close = src.indexOf(']', i)
                    if (close != -1 && close + 1 < src.length && src[close + 1] == '(') {
                        val urlEnd = src.indexOf(')', close + 2)
                        if (urlEnd != -1) {
                            val label = src.substring(i + 1, close)
                            val url = src.substring(close + 2, urlEnd)
                            pushLink(LinkAnnotation.Url(url))
                            val labelText =
                                parseInlineSource(
                                    label,
                                    textColor,
                                    searchQuery,
                                    isCurrentMatch,
                                    linkColor,
                                    highlights,
                                    isRtl && !BidiUtils.isLtrSnippet(label),
                                    depth + 1,
                                ).isolateLtrSnippet(isRtl)
                            withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                                append(labelText)
                            }
                            pop()
                            i = urlEnd + 1
                        } else {
                            append(src[i])
                            i++
                        }
                    } else {
                        append(src[i])
                        i++
                    }
                }

                // bare URL
                urlMatch != null -> {
                    val url = urlMatch.value
                    val urlToAppend = if (isRtl) BidiUtils.wrapLtrIsolate(url) else url
                    pushLink(LinkAnnotation.Url(url))
                    withStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) {
                        append(urlToAppend)
                    }
                    pop()
                    i = urlMatch.range.last + 1
                }

                // Search highlighting must win before an LTR run is isolated, otherwise an English
                // query in an RTL paragraph is consumed without receiving its highlight style.
                searchQuery.isNotEmpty() &&
                    src.regionMatches(i, searchQuery, 0, searchQuery.length, ignoreCase = true) -> {
                    val match = src.substring(i, i + searchQuery.length)
                    val toAppend = if (isRtl) BidiUtils.wrapLtrIsolate(match) else match
                    append(toAppend)
                    i += searchQuery.length
                }

                // Keep consecutive Latin words together inside a resolved RTL paragraph.
                isRtl && Character.isLetterOrDigit(src.codePointAt(i)) &&
                    Character.getDirectionality(src.codePointAt(i)) == Character.DIRECTIONALITY_LEFT_TO_RIGHT -> {
                    var end = i + Character.charCount(src.codePointAt(i))
                    while (end < src.length) {
                        val cp = src.codePointAt(end)
                        val directionality = Character.getDirectionality(cp)
                        if (directionality == Character.DIRECTIONALITY_LEFT_TO_RIGHT ||
                            directionality == Character.DIRECTIONALITY_EUROPEAN_NUMBER ||
                            directionality == Character.DIRECTIONALITY_EUROPEAN_NUMBER_SEPARATOR ||
                            directionality == Character.DIRECTIONALITY_EUROPEAN_NUMBER_TERMINATOR ||
                            (
                                directionality == Character.DIRECTIONALITY_WHITESPACE && end + 1 < src.length &&
                                    Character.getDirectionality(src.codePointAt(end + 1)) ==
                                    Character.DIRECTIONALITY_LEFT_TO_RIGHT
                            )
                        ) {
                            end += Character.charCount(cp)
                        } else {
                            break
                        }
                    }
                    append(BidiUtils.wrapLtrIsolate(src.substring(i, end)))
                    i = end
                }

                else -> {
                    append(src[i])
                    i++
                }
            }
        }
    }.withSearchHighlights(searchQuery, isCurrentMatch, highlights)
}

/** Overlay search-hit spans on an already syntax-highlighted string, keeping its existing styles. */
internal fun AnnotatedString.withSearchHighlights(
    query: String,
    isCurrent: Boolean,
    highlights: SearchHighlightColors,
): AnnotatedString {
    if (query.isEmpty()) return this
    val (bg, fg) =
        if (isCurrent) {
            highlights.currentSearchBackground to highlights.currentSearchForeground
        } else {
            highlights.searchBackground to highlights.searchForeground
        }
    return buildAnnotatedString {
        append(this@withSearchHighlights)
        var from = 0
        while (true) {
            val hit = text.indexOf(query, from, ignoreCase = true)
            if (hit < 0) break
            val start = if (hit > 0 && text[hit - 1] == BidiUtils.LRI.single()) hit - 1 else hit
            val end = hit + query.length
            val isolatedEnd = if (end < text.length && text[end] == BidiUtils.PDI.single()) end + 1 else end
            addStyle(SpanStyle(background = bg, color = fg), start, isolatedEnd)
            from = hit + query.length
        }
    }
}

internal fun anchorTrailingRtlPresentation(
    text: AnnotatedString,
    isRtl: Boolean,
): AnnotatedString =
    if (isRtl && !text.endsWith(BidiUtils.RLM)) {
        buildAnnotatedString {
            append(text)
            append(BidiUtils.RLM)
        }
    } else {
        text
    }

internal sealed interface MdBlock {
    data class Code(
        val code: String,
        val language: String? = null,
    ) : MdBlock

    data class Math(
        val latex: String,
    ) : MdBlock

    data class Heading(
        val level: Int,
        val text: String,
    ) : MdBlock

    data class Bullet(
        val text: String,
        val level: Int = 0,
    ) : MdBlock

    data class Task(
        val checked: Boolean,
        val text: String,
        val level: Int = 0,
    ) : MdBlock

    data class Ordered(
        val index: Int,
        val text: String,
        val level: Int = 0,
    ) : MdBlock

    data class Image(
        val uri: String,
        val alt: String = "",
    ) : MdBlock

    data class Quote(
        val text: String,
    ) : MdBlock

    data class Paragraph(
        val text: String,
    ) : MdBlock

    data class Table(
        val header: List<String>,
        val alignments: List<TableAlign>,
        val rows: List<List<String>>,
    ) : MdBlock

    object Hr : MdBlock

    data class DefList(
        val items: List<DefItem>,
    ) : MdBlock

    data class Footnotes(
        val notes: List<FnNote>,
    ) : MdBlock
}

internal data class DefItem(
    val term: String,
    val definitions: List<String>,
)

internal data class Footnote(
    val id: String,
    val text: String,
)

internal data class FnNote(
    val id: String,
    val text: String,
)

internal enum class TableAlign {
    LEFT,
    CENTER,
    RIGHT,
}

internal data class ResolvedImageSource(
    val model: String,
    val gatewayPath: String? = null,
)

internal fun resolveImageSource(uri: String): ResolvedImageSource {
    val trimmed = uri.trim()
    if (trimmed.isBlank()) return ResolvedImageSource(uri)

    downloadPathFromUri(trimmed)?.let { path ->
        return ResolvedImageSource(model = path, gatewayPath = path)
    }

    if (!trimmed.startsWith("/api/")) {
        GatewayFileClient.normalizePath(trimmed)?.let { path ->
            return ResolvedImageSource(model = path, gatewayPath = path)
        }
    }

    return ResolvedImageSource(trimmed)
}

private fun downloadPathFromUri(uri: String): String? {
    val parsed = runCatching { java.net.URI(uri) }.getOrNull() ?: return null
    val path = parsed.path ?: return null
    if (path != "/api/files/download" && path != "api/files/download") return null
    val encodedPath =
        parsed.rawQuery
            ?.split('&')
            ?.firstOrNull { it.substringBefore('=') == "path" }
            ?.substringAfter('=', "")
            ?.takeIf { it.isNotBlank() }
            ?: return null
    val decoded =
        runCatching {
            java.net.URLDecoder.decode(encodedPath, java.nio.charset.StandardCharsets.UTF_8.name())
        }.getOrNull() ?: return null
    return GatewayFileClient.normalizePath(decoded)
}

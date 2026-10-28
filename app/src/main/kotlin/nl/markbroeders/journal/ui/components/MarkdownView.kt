package nl.markbroeders.journal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import nl.markbroeders.journal.data.Embeds
import nl.markbroeders.journal.data.MarkdownDoc
import nl.markbroeders.journal.data.MdBlock
import nl.markbroeders.journal.data.MdInline
import nl.markbroeders.journal.data.MdListItem
import nl.markbroeders.journal.ui.media.MediaEntry
import nl.markbroeders.journal.ui.theme.reading
import java.time.LocalDate

/**
 * Shows Markdown formatted, roughly as Obsidian's reading view does. Photos and videos appear
 * where they are embedded; web links open in the browser, `#tags` call [onTag] and links to a
 * daily note (`[[2026-10-23]]`) call [onOpenDate].
 */
@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
    /** The file an embed target resolves to, or null (unresolved embeds are left out). */
    resolveMedia: (String) -> MediaEntry? = { null },
    onOpenPhoto: (MediaEntry) -> Unit = {},
    onOpenDate: (LocalDate) -> Unit = {},
    onTag: (String) -> Unit = {},
) {
    val blocks = remember(markdown) { MarkdownDoc.parse(markdown) }
    val env = MdEnv(
        colors = MdColors(
            link = MaterialTheme.colorScheme.primary,
            muted = MaterialTheme.colorScheme.onSurfaceVariant,
            codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
            rule = MaterialTheme.colorScheme.outlineVariant,
        ),
        resolveMedia = resolveMedia,
        onOpenPhoto = onOpenPhoto,
        onOpenDate = onOpenDate,
        onTag = onTag,
    )
    Blocks(blocks, env, MaterialTheme.typography.bodyLarge.reading(), modifier, spacing = 14)
}

private data class MdColors(val link: Color, val muted: Color, val codeBackground: Color, val rule: Color)

private class MdEnv(
    val colors: MdColors,
    val resolveMedia: (String) -> MediaEntry?,
    val onOpenPhoto: (MediaEntry) -> Unit,
    val onOpenDate: (LocalDate) -> Unit,
    val onTag: (String) -> Unit,
)

@Composable
private fun Blocks(blocks: List<MdBlock>, env: MdEnv, style: TextStyle, modifier: Modifier = Modifier, spacing: Int) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
        blocks.forEach { Block(it, env, style) }
    }
}

@Composable
private fun Block(block: MdBlock, env: MdEnv, style: TextStyle) {
    when (block) {
        is MdBlock.Paragraph -> Paragraph(block.content, env, style)
        is MdBlock.Heading -> InlineText(block.content, env, headingStyle(block.level), Modifier.padding(top = 4.dp))
        is MdBlock.ListBlock -> ListBlock(block, env, style)
        is MdBlock.Quote -> {
            val bar = env.colors.rule
            Blocks(
                block.blocks, env, style.copy(color = env.colors.muted),
                Modifier
                    .drawBehind { drawRect(bar, size = Size(3.dp.toPx(), size.height)) }
                    .padding(start = 15.dp),
                spacing = 10,
            )
        }
        is MdBlock.Callout -> Callout(block, env, style)
        is MdBlock.Code -> Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(env.colors.codeBackground),
        ) {
            Text(
                block.code,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }
        MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp), color = env.colors.rule)
        is MdBlock.Table -> Table(block, env, style)
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle {
    val t = MaterialTheme.typography
    val base = when (level) {
        1 -> t.headlineSmall
        2 -> t.titleLarge
        3 -> t.titleMedium.copy(fontSize = t.titleLarge.fontSize * 0.9f)
        else -> t.titleMedium
    }
    return base.reading().copy(fontWeight = FontWeight.SemiBold)
}

/** A paragraph: text, with photos and videos as their own blocks where they are embedded. */
@Composable
private fun Paragraph(content: List<MdInline>, env: MdEnv, style: TextStyle) {
    val parts = remember(content, env.resolveMedia) { splitAtMedia(content, env.resolveMedia) }
    if (parts.size == 1 && parts[0] is ParagraphPart.Text) {
        InlineText((parts[0] as ParagraphPart.Text).content, env, style)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        parts.forEach { part ->
            when (part) {
                is ParagraphPart.Text -> InlineText(part.content, env, style)
                is ParagraphPart.Media -> EntryPhoto(part.media) { env.onOpenPhoto(part.media) }
            }
        }
    }
}

private sealed interface ParagraphPart {
    data class Text(val content: List<MdInline>) : ParagraphPart
    data class Media(val media: MediaEntry) : ParagraphPart
}

private fun splitAtMedia(content: List<MdInline>, resolve: (String) -> MediaEntry?): List<ParagraphPart> {
    val parts = mutableListOf<ParagraphPart>()
    val run = mutableListOf<MdInline>()
    fun flush() {
        // Line breaks next to a photo would only add empty lines.
        while (run.firstOrNull().isBlankInline()) run.removeAt(0)
        while (run.lastOrNull().isBlankInline()) run.removeAt(run.lastIndex)
        if (run.isNotEmpty()) parts += ParagraphPart.Text(run.toList())
        run.clear()
    }
    for (node in content) {
        if (node is MdInline.Embed && Embeds.isMedia(node.target)) {
            flush()
            Embeds.fileName(node.target)?.let(resolve)?.let { parts += ParagraphPart.Media(it) }
        } else {
            run += node
        }
    }
    flush()
    return parts
}

private fun MdInline?.isBlankInline() =
    this == MdInline.LineBreak || (this is MdInline.Text && text.isBlank())

@Composable
private fun InlineText(content: List<MdInline>, env: MdEnv, style: TextStyle, modifier: Modifier = Modifier) {
    val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = env.colors.codeBackground, fontSize = 0.9.em)
    val text = remember(content, style, env.colors) { annotated(content, env, codeStyle) }
    Text(text, style = style, modifier = modifier)
}

private val HIGHLIGHT = Color(0x66FFD000)
private val DAILY_NOTE = Regex("""(\d{4}-\d{2}-\d{2})(\.md)?""")

private fun annotated(content: List<MdInline>, env: MdEnv, codeStyle: SpanStyle): AnnotatedString =
    buildAnnotatedString {
        val linkStyles = TextLinkStyles(SpanStyle(color = env.colors.link))
        fun append(nodes: List<MdInline>) {
            for (node in nodes) when (node) {
                is MdInline.Text -> append(node.text)
                MdInline.LineBreak -> append('\n')
                is MdInline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(node.content) }
                is MdInline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(node.content) }
                is MdInline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(node.content) }
                is MdInline.Highlight -> withStyle(SpanStyle(background = HIGHLIGHT)) { append(node.content) }
                is MdInline.Code -> withStyle(codeStyle) { append(node.code) }
                is MdInline.Link -> withLink(
                    LinkAnnotation.Url(node.url, TextLinkStyles(SpanStyle(color = env.colors.link, textDecoration = TextDecoration.Underline))),
                ) {
                    if (node.content.isEmpty()) append(node.url) else append(node.content)
                }
                is MdInline.WikiLink -> {
                    val date = dailyNoteDate(node.target)
                    if (date != null) {
                        withLink(LinkAnnotation.Clickable("date:$date", linkStyles) { env.onOpenDate(date) }) {
                            append(node.label)
                        }
                    } else {
                        withStyle(SpanStyle(color = env.colors.link)) { append(node.label) }
                    }
                }
                // A note or non-media file embedded in text (or a photo inside a table): its name.
                is MdInline.Embed -> withStyle(SpanStyle(color = env.colors.link)) {
                    append(Embeds.fileName(node.target) ?: node.target)
                }
                is MdInline.Tag -> withLink(LinkAnnotation.Clickable("tag:${node.tag}", linkStyles) { env.onTag(node.tag) }) {
                    append("#${node.tag}")
                }
            }
        }
        append(content)
    }

/** The date of a link to a daily note (`2026-10-23`, `Journal/2026/2026-10-23#Kop`), else null. */
private fun dailyNoteDate(target: String): LocalDate? {
    val name = target.substringBefore('#').substringAfterLast('/').trim()
    val m = DAILY_NOTE.matchEntire(name) ?: return null
    return runCatching { LocalDate.parse(m.groupValues[1]) }.getOrNull()
}

@Composable
private fun ListBlock(list: MdBlock.ListBlock, env: MdEnv, style: TextStyle) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.items.forEachIndexed { i, item ->
            ListItem(item, marker = if (list.ordered) "${list.start + i}." else "•", env, style)
        }
    }
}

@Composable
private fun ListItem(item: MdListItem, marker: String, env: MdEnv, style: TextStyle) {
    Row {
        Box(Modifier.widthIn(min = 22.dp).padding(end = 6.dp)) {
            if (item.task != null) {
                Icon(
                    if (item.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                    contentDescription = if (item.checked) "Done" else "To do",
                    tint = if (item.checked) env.colors.link else env.colors.muted,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp),
                )
            } else {
                Text(marker, style = style, color = env.colors.muted)
            }
        }
        // Done tasks are struck through and dimmed, like Obsidian.
        val itemStyle = if (item.task == 'x' || item.task == 'X') {
            style.copy(color = env.colors.muted, textDecoration = TextDecoration.LineThrough)
        } else {
            style
        }
        Blocks(item.blocks, env, itemStyle, Modifier.weight(1f), spacing = 6)
    }
}

@Composable
private fun Callout(callout: MdBlock.Callout, env: MdEnv, style: TextStyle) {
    val color = calloutColor(callout.type)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val titleStyle = style.copy(fontWeight = FontWeight.SemiBold, color = color)
        if (callout.title.isEmpty()) {
            Text(callout.type.replaceFirstChar { it.titlecase() }, style = titleStyle)
        } else {
            InlineText(callout.title, env, titleStyle)
        }
        if (callout.blocks.isNotEmpty()) Blocks(callout.blocks, env, style, spacing = 10)
    }
}

/** Obsidian's callout colours, by type and its aliases. */
private fun calloutColor(type: String): Color = when (type) {
    "abstract", "summary", "tldr" -> Color(0xFF00B0C8)
    "info", "todo" -> Color(0xFF086DDD)
    "tip", "hint", "important" -> Color(0xFF00A3A3)
    "success", "check", "done" -> Color(0xFF08A045)
    "question", "help", "faq", "warning", "caution", "attention" -> Color(0xFFE07B00)
    "failure", "fail", "missing", "danger", "error", "bug" -> Color(0xFFE93147)
    "example" -> Color(0xFF7852EE)
    "quote", "cite" -> Color(0xFF7A7A7A)
    else -> Color(0xFF086DDD)
}

/** A table as a grid: columns as wide as their widest cell, scrolling sideways if needed. */
@Composable
private fun Table(table: MdBlock.Table, env: MdEnv, style: TextStyle) {
    val columns = table.header.size
    val border = env.colors.rule
    Box(
        Modifier
            .horizontalScroll(rememberScrollState())
            .border(1.dp, border, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp)),
    ) {
        Layout(
            content = {
                val cellStyle = MaterialTheme.typography.bodyMedium.reading()
                table.header.forEach { cell ->
                    Box(Modifier.background(env.colors.codeBackground).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        InlineText(cell, env, cellStyle.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
                table.rows.forEach { row ->
                    row.forEach { cell ->
                        Box(
                            Modifier
                                .drawBehind { drawRect(border, size = Size(size.width, 1.dp.toPx())) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) { InlineText(cell, env, cellStyle) }
                    }
                }
            },
        ) { measurables, _ ->
            // Cells fill their column and row, so backgrounds and lines line up.
            val maxCell = 280.dp.roundToPx()
            val rows = measurables.chunked(columns)
            val widths = IntArray(columns) { c ->
                rows.maxOf { row -> row.getOrNull(c)?.maxIntrinsicWidth(Constraints.Infinity) ?: 0 }.coerceAtMost(maxCell)
            }
            val heights = rows.map { row -> row.mapIndexed { c, m -> m.maxIntrinsicHeight(widths[c]) }.max() }
            val placeables = rows.mapIndexed { r, row ->
                row.mapIndexed { c, m -> m.measure(Constraints.fixed(widths[c], heights[r])) }
            }
            layout(widths.sum(), heights.sum()) {
                var y = 0
                placeables.forEachIndexed { r, row ->
                    var x = 0
                    row.forEachIndexed { c, p ->
                        p.place(x, y)
                        x += widths[c]
                    }
                    y += heights[r]
                }
            }
        }
    }
}

/** A photo at full width (or a video's first frame with a play icon); tapping opens the viewer. */
@Composable
fun EntryPhoto(photo: MediaEntry, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.fileName,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
        )
        if (photo.isVideo) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(8.dp),
            )
        }
    }
}

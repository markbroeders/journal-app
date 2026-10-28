package nl.markbroeders.journal.data

/** A block of a Markdown document, for display (see [MarkdownDoc]). */
sealed interface MdBlock {
    data class Heading(val level: Int, val content: List<MdInline>) : MdBlock
    data class Paragraph(val content: List<MdInline>) : MdBlock
    data class ListBlock(val ordered: Boolean, val start: Int, val items: List<MdListItem>) : MdBlock
    data class Quote(val blocks: List<MdBlock>) : MdBlock

    /** Obsidian callout: `> [!type] Title`; [title] is empty when none is given. */
    data class Callout(val type: String, val title: List<MdInline>, val blocks: List<MdBlock>) : MdBlock
    data class Code(val info: String, val code: String) : MdBlock
    data object Rule : MdBlock
    data class Table(val header: List<List<MdInline>>, val rows: List<List<List<MdInline>>>) : MdBlock
}

/** A list item; [task] is the character between the brackets of `- [x]` (null if not a task). */
data class MdListItem(val task: Char?, val blocks: List<MdBlock>) {
    val checked: Boolean get() = task != null && task != ' '
}

sealed interface MdInline {
    data class Text(val text: String) : MdInline
    data class Bold(val content: List<MdInline>) : MdInline
    data class Italic(val content: List<MdInline>) : MdInline
    data class Strike(val content: List<MdInline>) : MdInline
    data class Highlight(val content: List<MdInline>) : MdInline
    data class Code(val code: String) : MdInline
    data class Link(val url: String, val content: List<MdInline>) : MdInline

    /** `[[target|label]]`; [label] is the alias, or the target as written. */
    data class WikiLink(val target: String, val label: String) : MdInline

    /** `![[target]]` or `![alt](target)`: a photo, video or transcluded note. */
    data class Embed(val target: String) : MdInline
    data class Tag(val tag: String) : MdInline
    data object LineBreak : MdInline
}

/**
 * Parses Markdown for display: CommonMark's common blocks and inlines plus Obsidian's
 * `[[links]]`, `![[embeds]]`, `==highlights==`, `#tags`, callouts and `%%comments%%` (hidden).
 * Lenient by design: anything it doesn't recognise is shown as text. Single newlines inside a
 * paragraph are line breaks, as in Obsidian's default (non-strict) mode.
 */
object MarkdownDoc {

    private val COMMENT = Regex("""%%[\s\S]*?%%|<!--[\s\S]*?-->""")

    fun parse(markdown: String): List<MdBlock> {
        val text = markdown.replace("\r\n", "\n").replace(COMMENT, "")
        return blocks(text.split('\n').map(::expandTabs))
    }

    // ---- Blocks ----

    private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,})\s*([^`]*)$""")
    private val HEADING = Regex("""^ {0,3}(#{1,6})(?:[ \t]+(.*?))?(?:[ \t]+#+)?[ \t]*$""")
    private val SETEXT = Regex("""^ {0,3}(=+|-+)[ \t]*$""")
    private val RULE = Regex("""^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$""")
    private val QUOTE = Regex("""^ {0,3}> ?""")
    private val LIST_ITEM = Regex("""^( *)([-*+]|\d{1,9}[.)])( +|$)(.*)$""")
    private val TASK = Regex("""^\[(.)] (.*)$""")
    private val CALLOUT = Regex("""^\[!([^\]]+)][+-]?[ \t]*(.*)$""")
    private val TABLE_DELIMITER = Regex("""^ *\|? *:?-+:? *(\| *:?-+:? *)*\|? *$""")

    private fun expandTabs(line: String): String {
        if (!line.startsWith('\t') && !line.startsWith(" \t")) return line
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        var width = 0
        for (c in indent) width = if (c == '\t') (width / 4 + 1) * 4 else width + 1
        return " ".repeat(width) + line.substring(indent.length)
    }

    private fun indentOf(line: String) = line.takeWhile { it == ' ' }.length

    private fun blocks(lines: List<String>): List<MdBlock> {
        val out = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()
        fun flush() {
            if (paragraph.isNotEmpty()) {
                out += MdBlock.Paragraph(inlines(paragraph.joinToString("\n") { it.trim() }))
                paragraph.clear()
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                flush()
                i++
                continue
            }
            // A paragraph followed by `===` or `---` is a heading.
            val setext = SETEXT.matchEntire(line)
            if (setext != null && paragraph.isNotEmpty()) {
                val level = if (setext.groupValues[1][0] == '=') 1 else 2
                out += MdBlock.Heading(level, inlines(paragraph.joinToString("\n") { it.trim() }))
                paragraph.clear()
                i++
                continue
            }
            val fence = FENCE.matchEntire(line)
            if (fence != null) {
                flush()
                val marker = fence.groupValues[1]
                val indent = indentOf(line)
                val code = mutableListOf<String>()
                i++
                while (i < lines.size) {
                    val l = lines[i]
                    val t = l.trim()
                    if (t.length >= marker.length && t.all { it == marker[0] } && indentOf(l) <= 3) {
                        i++
                        break
                    }
                    code += l.drop(minOf(indent, indentOf(l)))
                    i++
                }
                out += MdBlock.Code(fence.groupValues[2].trim(), code.joinToString("\n"))
                continue
            }
            // Indented code can't interrupt a paragraph.
            if (paragraph.isEmpty() && indentOf(line) >= 4) {
                val code = mutableListOf<String>()
                while (i < lines.size && (indentOf(lines[i]) >= 4 || lines[i].isBlank())) {
                    code += lines[i].drop(4)
                    i++
                }
                out += MdBlock.Code("", code.joinToString("\n").trimEnd('\n'))
                continue
            }
            val heading = HEADING.matchEntire(line)
            if (heading != null) {
                flush()
                out += MdBlock.Heading(heading.groupValues[1].length, inlines(heading.groupValues[2]))
                i++
                continue
            }
            if (RULE.matches(line)) {
                flush()
                out += MdBlock.Rule
                i++
                continue
            }
            if (QUOTE.containsMatchIn(line)) {
                flush()
                val inner = mutableListOf<String>()
                while (i < lines.size && QUOTE.containsMatchIn(lines[i])) {
                    inner += lines[i].replaceFirst(QUOTE, "")
                    i++
                }
                out += quote(inner)
                continue
            }
            if (LIST_ITEM.matches(line)) {
                flush()
                i = list(lines, i, out)
                continue
            }
            if ('|' in line && i + 1 < lines.size && TABLE_DELIMITER.matches(lines[i + 1]) &&
                cells(line).size == cells(lines[i + 1]).size
            ) {
                flush()
                val header = cells(line).map(::inlines)
                val rows = mutableListOf<List<List<MdInline>>>()
                i += 2
                while (i < lines.size && lines[i].isNotBlank() && '|' in lines[i]) {
                    val row = cells(lines[i]).map(::inlines)
                    rows += List(header.size) { row.getOrElse(it) { emptyList() } }
                    i++
                }
                out += MdBlock.Table(header, rows)
                continue
            }
            paragraph += line
            i++
        }
        flush()
        return out
    }

    private fun quote(lines: List<String>): MdBlock {
        val callout = lines.firstOrNull()?.let { CALLOUT.matchEntire(it.trim()) }
            ?: return MdBlock.Quote(blocks(lines))
        return MdBlock.Callout(
            type = callout.groupValues[1].trim().lowercase(),
            title = inlines(callout.groupValues[2]),
            blocks = blocks(lines.drop(1)),
        )
    }

    /** Splits a table row into cells, honouring `\|` and pipes inside code or `[[a|b]]`. */
    private fun cells(line: String): List<String> {
        var row = line.trim()
        if (row.startsWith('|')) row = row.substring(1)
        if (row.endsWith('|') && !row.endsWith("\\|")) row = row.dropLast(1)
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var i = 0
        var inCode = false
        var inLink = false
        while (i < row.length) {
            val c = row[i]
            when {
                c == '\\' && row.getOrNull(i + 1) == '|' -> {
                    cell.append('|')
                    i++
                }
                c == '`' -> {
                    inCode = !inCode
                    cell.append(c)
                }
                !inCode && row.startsWith("[[", i) -> {
                    inLink = true
                    cell.append("[[")
                    i++
                }
                !inCode && inLink && row.startsWith("]]", i) -> {
                    inLink = false
                    cell.append("]]")
                    i++
                }
                c == '|' && !inCode && !inLink -> {
                    cells += cell.toString().trim()
                    cell.clear()
                }
                else -> cell.append(c)
            }
            i++
        }
        cells += cell.toString().trim()
        return cells
    }

    /** Parses the list starting at [start]; returns the index of the first line after it. */
    private fun list(lines: List<String>, start: Int, out: MutableList<MdBlock>): Int {
        val first = LIST_ITEM.matchEntire(lines[start])!!
        val ordered = first.groupValues[2].last().let { it == '.' || it == ')' }
        val baseIndent = first.groupValues[1].length
        val items = mutableListOf<MdListItem>()
        var i = start
        while (i < lines.size) {
            val m = LIST_ITEM.matchEntire(lines[i]) ?: break
            val markerOrdered = m.groupValues[2].last().let { it == '.' || it == ')' }
            if (markerOrdered != ordered || m.groupValues[1].length > baseIndent + 3) break
            if (RULE.matches(lines[i])) break
            val marker = m.groupValues[2]
            val spaces = m.groupValues[3].length
            val contentIndent = m.groupValues[1].length + marker.length + if (spaces in 1..4) spaces else 1
            val content = mutableListOf(m.groupValues[4])
            i++
            // Continuation: indented lines (nested blocks), blank lines followed by indented
            // lines, and lazy paragraph lines.
            while (i < lines.size) {
                val l = lines[i]
                when {
                    l.isBlank() -> {
                        val next = lines.subList(i, lines.size).firstOrNull { it.isNotBlank() } ?: break
                        if (indentOf(next) < contentIndent) break
                        content += ""
                    }
                    indentOf(l) >= contentIndent -> content += l.drop(contentIndent)
                    LIST_ITEM.matches(l) || startsBlock(l) || content.last().isBlank() -> break
                    else -> content += l.trim()
                }
                i++
            }
            val task = TASK.matchEntire(content[0])
            if (task != null) content[0] = task.groupValues[2]
            items += MdListItem(task?.groupValues?.get(1)?.single(), blocks(content))
            // A blank line between items still continues the list.
            var j = i
            while (j < lines.size && lines[j].isBlank()) j++
            val nextItem = lines.getOrNull(j)?.let { LIST_ITEM.matchEntire(it) }
            if (j > i && (nextItem == null || nextItem.groupValues[1].length > baseIndent + 3)) break
            i = j
        }
        val startNumber = if (ordered) first.groupValues[2].dropLast(1).toIntOrNull() ?: 1 else 1
        out += MdBlock.ListBlock(ordered, startNumber, items)
        return i
    }

    private fun startsBlock(line: String) =
        HEADING.matches(line) || FENCE.matches(line) || QUOTE.containsMatchIn(line) || RULE.matches(line)

    // ---- Inlines ----

    private const val PUNCTUATION = """!"#$%&'()*+,-./:;<=>?@[\]^_`{|}~"""
    private val URL = Regex("""https?://[^\s<>]+""")
    private val TAG = Regex("""#([\p{L}\p{N}_/\-]+)""")
    private val IMAGE = Regex("""!\[([^\]]*)]\(\s*(?:<([^>\n]+)>|([^)\s]+))(?:\s+"[^"]*")?\s*\)""")

    fun inlines(text: String): List<MdInline> {
        val out = mutableListOf<MdInline>()
        val buffer = StringBuilder()
        fun flushText() {
            if (buffer.isNotEmpty()) {
                out += MdInline.Text(buffer.toString())
                buffer.clear()
            }
        }
        fun add(node: MdInline) {
            flushText()
            out += node
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            val prev = text.getOrNull(i - 1)
            when {
                c == '\\' && i + 1 < text.length && text[i + 1] == '\n' -> {
                    add(MdInline.LineBreak)
                    i += 2
                }
                c == '\\' && i + 1 < text.length && text[i + 1] in PUNCTUATION -> {
                    buffer.append(text[i + 1])
                    i += 2
                }
                c == '\n' -> {
                    while (buffer.endsWith(" ")) buffer.setLength(buffer.length - 1)
                    add(MdInline.LineBreak)
                    i++
                }
                c == '`' -> {
                    val run = text.runLength(i, '`')
                    val close = findBacktickRun(text, i + run, run)
                    if (close < 0) {
                        buffer.append(text, i, i + run)
                        i += run
                    } else {
                        var code = text.substring(i + run, close).replace('\n', ' ')
                        if (code.length > 1 && code.startsWith(' ') && code.endsWith(' ') && code.isNotBlank()) {
                            code = code.substring(1, code.length - 1)
                        }
                        add(MdInline.Code(code))
                        i = close + run
                    }
                }
                text.startsWith("![[", i) -> {
                    val close = text.indexOf("]]", i + 3)
                    if (close < 0 || '\n' in text.substring(i, close)) {
                        buffer.append(c)
                        i++
                    } else {
                        add(MdInline.Embed(text.substring(i + 3, close).substringBefore('|').trim()))
                        i = close + 2
                    }
                }
                text.startsWith("[[", i) -> {
                    val close = text.indexOf("]]", i + 2)
                    if (close < 0 || '\n' in text.substring(i, close)) {
                        buffer.append(c)
                        i++
                    } else {
                        val inner = text.substring(i + 2, close)
                        val target = inner.substringBefore('|').trim()
                        val label = if ('|' in inner) inner.substringAfter('|').trim() else target
                        add(MdInline.WikiLink(target, label))
                        i = close + 2
                    }
                }
                c == '!' && text.startsWith("![", i) && IMAGE.matchAt(text, i) != null -> {
                    val m = IMAGE.matchAt(text, i)!!
                    add(MdInline.Embed(m.groupValues[2].ifEmpty { m.groupValues[3] }))
                    i = m.range.last + 1
                }
                c == '[' -> {
                    val link = link(text, i)
                    if (link == null) {
                        buffer.append(c)
                        i++
                    } else {
                        add(MdInline.Link(link.second, inlines(link.first)))
                        i = link.third
                    }
                }
                c == '<' && URL.matchAt(text, i + 1)?.let { text.getOrNull(it.range.last + 1) == '>' } == true -> {
                    val m = URL.matchAt(text, i + 1)!!
                    add(MdInline.Link(m.value, listOf(MdInline.Text(m.value))))
                    i = m.range.last + 2
                }
                (c == 'h') && (prev == null || !prev.isLetterOrDigit()) && URL.matchAt(text, i) != null -> {
                    val url = trimUrl(URL.matchAt(text, i)!!.value)
                    add(MdInline.Link(url, listOf(MdInline.Text(url))))
                    i += url.length
                }
                c == '#' && (prev == null || prev.isWhitespace() || prev == '(') && TAG.matchAt(text, i) != null -> {
                    val tag = TAG.matchAt(text, i)!!.groupValues[1].trimEnd('/', '-')
                    if (tag.isNotEmpty() && tag.any { !it.isDigit() }) {
                        add(MdInline.Tag(tag))
                        i += tag.length + 1
                    } else {
                        buffer.append(c)
                        i++
                    }
                }
                c == '*' || c == '_' -> {
                    val emphasis = emphasis(text, i)
                    if (emphasis == null) {
                        val run = text.runLength(i, c)
                        buffer.append(text, i, i + run)
                        i += run
                    } else {
                        add(emphasis.first)
                        i = emphasis.second
                    }
                }
                (c == '~' || c == '=') && text.startsWith("$c$c", i) -> {
                    val close = findPair(text, i + 2, "$c$c")
                    if (close < 0) {
                        buffer.append(c).append(c)
                        i += 2
                    } else {
                        val inner = inlines(text.substring(i + 2, close))
                        add(if (c == '~') MdInline.Strike(inner) else MdInline.Highlight(inner))
                        i = close + 2
                    }
                }
                else -> {
                    buffer.append(c)
                    i++
                }
            }
        }
        flushText()
        return out
    }

    private fun String.runLength(from: Int, c: Char): Int {
        var n = 0
        while (from + n < length && this[from + n] == c) n++
        return n
    }

    private fun findBacktickRun(text: String, from: Int, length: Int): Int {
        var i = from
        while (i < text.length) {
            if (text[i] == '`') {
                val run = text.runLength(i, '`')
                if (run == length) return i
                i += run
            } else {
                i++
            }
        }
        return -1
    }

    /** Index just past a code span starting at [i], or [i] + 1 if it isn't one. */
    private fun skipCode(text: String, i: Int): Int {
        val run = text.runLength(i, '`')
        val close = findBacktickRun(text, i + run, run)
        return if (close < 0) i + run else close + run
    }

    /** Finds a closing `~~`/`==` with non-space inside, skipping code spans. */
    private fun findPair(text: String, from: Int, delimiter: String): Int {
        if (from >= text.length || text[from].isWhitespace()) return -1
        var i = from
        while (i < text.length) {
            when {
                text[i] == '`' -> i = skipCode(text, i)
                text.startsWith(delimiter, i) && i > from && !text[i - 1].isWhitespace() -> return i
                else -> i++
            }
        }
        return -1
    }

    /** `*a*`, `**a**`, `***a***` (and `_`): the node and the index after it, or null. */
    private fun emphasis(text: String, start: Int): Pair<MdInline, Int>? {
        val c = text[start]
        val run = text.runLength(start, c)
        val after = text.getOrNull(start + run) ?: return null
        if (after.isWhitespace()) return null
        // Intraword underscores (snake_case) are not emphasis.
        if (c == '_' && text.getOrNull(start - 1)?.isLetterOrDigit() == true) return null
        // Longer runs (`****a**`) stay as text.
        if (run > 3) return null
        val n = run
        val open = start
        var i = open + n
        while (i < text.length) {
            val ch = text[i]
            if (ch == '`') {
                i = skipCode(text, i)
                continue
            }
            if (ch == '[' && text.startsWith("[[", i)) {
                val close = text.indexOf("]]", i)
                if (close > 0) {
                    i = close + 2
                    continue
                }
            }
            if (ch != c) {
                i++
                continue
            }
            val closeRun = text.runLength(i, c)
            val usable = when (n) {
                1 -> closeRun != 2
                2 -> closeRun >= 2
                else -> closeRun >= 3
            }
            val closeAt = i + closeRun - n
            val leftFlanking = !text[i - 1].isWhitespace()
            val rightOk = c != '_' || text.getOrNull(i + closeRun)?.isLetterOrDigit() != true
            if (usable && leftFlanking && rightOk && i > open + n) {
                val inner = inlines(text.substring(open + n, closeAt))
                val node = when (n) {
                    1 -> MdInline.Italic(inner)
                    2 -> MdInline.Bold(inner)
                    else -> MdInline.Bold(listOf(MdInline.Italic(inner)))
                }
                return node to (closeAt + n)
            }
            i += closeRun
        }
        return null
    }

    /** `[text](url)` at [start]: link text, url and the index after it. */
    private fun link(text: String, start: Int): Triple<String, String, Int>? {
        var depth = 0
        var i = start
        var close = -1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i++
                '`' -> {
                    i = skipCode(text, i)
                    continue
                }
                '[' -> depth++
                ']' -> if (--depth == 0) {
                    close = i
                    break
                }
            }
            i++
        }
        if (close < 0 || text.getOrNull(close + 1) != '(') return null
        var j = close + 2
        var parens = 1
        while (j < text.length && parens > 0) {
            when (text[j]) {
                '(' -> parens++
                ')' -> parens--
                '\n' -> return null
            }
            if (parens > 0) j++
        }
        if (parens != 0) return null
        val destination = text.substring(close + 2, j).trim()
        val url = destination.removePrefix("<").substringBefore('>').substringBefore(" \"").trim()
        if (url.isEmpty()) return null
        return Triple(text.substring(start + 1, close), url, j + 1)
    }

    /** Drops trailing punctuation from a bare URL, keeping a `)` that closes one in the URL. */
    private fun trimUrl(url: String): String {
        var u = url
        while (u.isNotEmpty()) {
            val last = u.last()
            u = when {
                last in ".,:;!?'\"*_~" -> u.dropLast(1)
                last == ')' && u.count { it == '(' } < u.count { it == ')' } -> u.dropLast(1)
                else -> return u
            }
        }
        return u
    }
}

package nl.markbroeders.journal.data

/** Turns what another app shares (`EXTRA_SUBJECT` + `EXTRA_TEXT`) into an entry's opening text. */
object SharedText {

    private val URL = Regex("""https?://\S+""")

    /**
     * A bare link with a title (as browsers share a page) becomes `[title](url)`; otherwise the
     * subject goes above the text unless the text already contains it. Line endings become `\n`.
     */
    fun compose(subject: String?, text: String?): String {
        val s = subject?.normalized()?.takeIf { it.isNotEmpty() }
        val t = text?.normalized()?.takeIf { it.isNotEmpty() }
        return when {
            t == null -> s.orEmpty()
            s == null || t.contains(s) -> t
            URL.matches(t) -> "[${s.replace("[", "\\[").replace("]", "\\]")}](${t.replace(")", "%29")})"
            else -> "$s\n\n$t"
        }
    }

    private fun String.normalized() = replace("\r\n", "\n").replace('\r', '\n').trim()
}

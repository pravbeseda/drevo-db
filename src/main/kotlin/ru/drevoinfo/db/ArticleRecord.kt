package ru.drevoinfo.db

const val ALL_ARTICLES_TOPIC = 99

private const val MAIN_PAGE_ID = 4L

private const val FIELD_COUNT = 5

// Topics from 13 up are service topics; an article in any of them stays out of "all articles".
private const val FIRST_SERVICE_TOPIC = 13

/**
 * One line of the site export `drevo.txt` — `id<drevo>title<drevo>topics<drevo>toc<drevo>body` —
 * in the shape it is stored in the database.
 */
data class ArticleRecord(
    val id: Long,
    val title: String,
    val topics: List<Int>,
    val text: String,
) {
    companion object {
        fun parse(line: String): ArticleRecord {
            val fields = line.split("<drevo>")
            require(fields.size >= FIELD_COUNT) { "expected $FIELD_COUNT <drevo>-separated fields, found ${fields.size}" }
            val id = fields[0].javaTrim().toLong()
            val title = fields[1].javaTrim()
            val topics =
                fields[2]
                    .javaTrim()
                    .split(",")
                    .filter { it.isNotEmpty() }
                    .map { it.toInt() }
            val inAllArticles = id == MAIN_PAGE_ID || topics.none { it >= FIRST_SERVICE_TOPIC }
            return ArticleRecord(
                id = id,
                // The space sorts the main page first under ORDER BY title, which keeps the title index usable.
                title = if (id == MAIN_PAGE_ID) " $title" else title,
                topics = if (inAllArticles) topics + ALL_ARTICLES_TOPIC else topics,
                text = toc(fields[3].javaTrim()) + " <drevo> " + fields[4].javaTrim(),
            )
        }

        /** Turns the export's HTML table of contents into `#anchor|title` entries, a nested one as `#_anchor|title`. */
        private fun toc(html: String): String =
            html
                .replace("<div class=\"indent\"> ", "#")
                .replace("</div>", "")
                .replace("<a href=\"", "")
                .replace("\">", "|")
                .replace("</a>", "")
                .replace("##", "#_")
                .replace("   ", " ")
                .replace("  ", " ")
    }
}

/** Java's `String.trim()`, which strips only code points up to U+0020: Kotlin's `trim()` would also strip a no-break space. */
internal fun String.javaTrim(): String = trim { it <= ' ' }

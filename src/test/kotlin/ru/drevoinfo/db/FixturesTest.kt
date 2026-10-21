package ru.drevoinfo.db

import com.nothome.delta.text.TextPatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager
import java.util.Base64

class FixturesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val committed = File("fixtures")

    @Test
    fun committedFixturesMatchAFreshBuild() {
        val fresh = temp.newFolder()

        Fixtures.generate(committed.resolve("source"), fresh)

        assertEquals(outputs(committed), outputs(fresh))
        for (name in listOf(Fixtures.MAIN, Fixtures.PATCH)) {
            assertEquals(name, dump(committed.resolve(name)), dump(fresh.resolve(name)))
        }
        assertEquals(texts(committed), texts(fresh))
        for (file in committed.resolve(Fixtures.EXPECTED).listFiles()!!) {
            assertEquals(file.name, file.readText(), fresh.resolve(Fixtures.EXPECTED).resolve(file.name).readText())
        }
    }

    @Test
    fun everyExpectedTextIsRebuiltFromTheTsvTheWayClientsDo() {
        for (row in rows()) {
            val main = row.main?.let(ZipText::unzip)
            val patch = row.patch?.let(ZipText::unzip)
            val text =
                when (row.isFullText) {
                    null -> main
                    1 -> patch
                    else -> TextPatcher(main).patch(patch)
                }
            assertEquals("article ${row.id}", expected(row.id), text)
        }
    }

    @Test
    fun fixturesCoverEveryCaseTheFormatHas() {
        val rows = rows()

        assertTrue("unchanged: no patch row", rows.any { it.isFullText == null && it.main != null })
        assertTrue("delta", rows.any { it.isFullText == 0 })
        assertTrue("full replacement over the threshold", rows.any { it.isFullText == 1 && it.main != null })
        assertTrue("new article: no main row", rows.any { it.isFullText == 1 && it.main == null })
        assertTrue("nested table of contents", rows.any { expected(it.id).contains("#_") })
        assertTrue("non-BMP characters in a delta", rows.any { it.isFullText == 0 && expected(it.id).any(Char::isSurrogate) })
        val inAllArticles = query(committed.resolve(Fixtures.PATCH), "SELECT article_id FROM tblTopics WHERE topic = 99")
        val mainPageTopics = query(committed.resolve(Fixtures.PATCH), "SELECT topic FROM tblTopics WHERE article_id = 4")
        assertTrue("main page in all articles despite a service topic", "4" in inAllArticles && mainPageTopics.any { it.toInt() in 13..98 })
        assertTrue("article left out of all articles", rows.any { it.id.toString() !in inAllArticles })
    }

    private data class Row(
        val id: Long,
        val isFullText: Int?,
        val main: ByteArray?,
        val patch: ByteArray?,
    )

    private fun rows(dir: File = committed): List<Row> =
        dir.resolve(Fixtures.TEXTS).readLines().drop(1).map { line ->
            val (id, isFullText, main, patch) = line.split("\t")
            Row(id.toLong(), isFullText.toIntOrNull(), decode(main), decode(patch))
        }

    private fun decode(field: String) = if (field.isEmpty()) null else Base64.getDecoder().decode(field)

    // Blobs are compared inflated: any valid zlib stream of the same text serves the clients equally.
    private fun texts(dir: File) =
        rows(dir).map { listOf(it.id, it.isFullText, it.main?.let(ZipText::unzip), it.patch?.let(ZipText::unzip)) }

    private fun expected(id: Long) = committed.resolve(Fixtures.EXPECTED).resolve("$id.txt").readText()

    private fun outputs(dir: File) =
        dir
            .walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(dir).path }
            .filterNot { it.startsWith("source/") }
            .sorted()
            .toList()

    /** What `sqlite3 .dump` shows, plus the header fields it leaves out, with every blob inflated. */
    private fun dump(file: File): List<String> =
        query(file, "PRAGMA page_size") + query(file, "PRAGMA user_version") +
            query(file, "SELECT type || ' ' || name || ' ' || coalesce(sql, '') FROM sqlite_master ORDER BY name") +
            query(file, "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").flatMap { table ->
                query(file, "SELECT * FROM $table ORDER BY rowid").map { "$table $it" }
            }

    private fun query(
        file: File,
        sql: String,
    ): List<String> =
        DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    val columns = result.metaData.columnCount
                    generateSequence {
                        if (!result.next()) return@generateSequence null
                        (1..columns).joinToString("|") { column ->
                            when (val value = result.getObject(column)) {
                                is ByteArray -> ZipText.unzip(value)
                                else -> value.toString()
                            }
                        }
                    }.toList()
                }
            }
        }
}

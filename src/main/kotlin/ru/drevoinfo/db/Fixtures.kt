package ru.drevoinfo.db

import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.Base64

/**
 * The reference fixtures both clients test against (docs/format.md, "Fixtures"): `main` built from
 * `drevo.v1.txt` in full mode, `patch` from `drevo.v2.txt` against it, the texts in a SQLite-free form, and the
 * text every article must assemble to.
 */
object Fixtures {
    const val MAIN = "main.sqlite"
    const val PATCH = "patch.sqlite"
    const val TEXTS = "texts.tsv"
    const val EXPECTED = "expected"

    fun generate(
        source: File,
        out: File,
    ) {
        out.mkdirs()
        val main = out.resolve(MAIN)
        val patch = out.resolve(PATCH)
        val indexOnlyPatch = File.createTempFile("index-only-patch", ".sqlite")
        try {
            Generator.build(source.resolve("drevo.v1.txt"), main, indexOnlyPatch, full = true)
        } finally {
            indexOnlyPatch.delete()
        }
        val export = source.resolve("drevo.v2.txt")
        Generator.build(export, main, patch, full = false)
        val records = export.readLines(Charsets.UTF_8).filter { it.javaTrim().isNotEmpty() }.map(ArticleRecord::parse)

        open(main).use { mainDb ->
            open(patch).use { patchDb ->
                val lines =
                    records.map { record ->
                        val patchRow = row(patchDb, "SELECT ziptext, isfulltext FROM tblTexts WHERE _id = ?", record.id)
                        val mainRow = row(mainDb, "SELECT ziptext, NULL FROM tblTexts WHERE _id = ?", record.id)
                        listOf(record.id, patchRow?.second ?: "", encode(mainRow?.first), encode(patchRow?.first)).joinToString("\t")
                    }
                out.resolve(TEXTS).writeText((listOf("_id\tisfulltext\tmain\tpatch") + lines).joinToString("\n", postfix = "\n"))
            }
        }

        val expected = out.resolve(EXPECTED)
        expected.deleteRecursively()
        expected.mkdirs()
        records.forEach { expected.resolve("${it.id}.txt").writeText(it.text, Charsets.UTF_8) }
    }

    private fun open(file: File): Connection =
        DriverManager.getConnection("jdbc:sqlite:$file", SQLiteConfig().apply { setReadOnly(true) }.toProperties())

    private fun row(
        connection: Connection,
        sql: String,
        id: Long,
    ): Pair<ByteArray, Int?>? =
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { if (it.next()) it.getBytes(1) to it.getObject(2) as Int? else null }
        }

    private fun encode(blob: ByteArray?) = blob?.let { Base64.getEncoder().encodeToString(it) } ?: ""
}

/** `./gradlew generateFixtures`: rebuilds `fixtures/` from `fixtures/source/`. */
fun main(args: Array<String>) {
    Fixtures.generate(File(args[0]), File(args[1]))
}

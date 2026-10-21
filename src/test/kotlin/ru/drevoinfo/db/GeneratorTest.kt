package ru.drevoinfo.db

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager

class GeneratorTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val text = "Текст статьи. ".repeat(20)

    @Test
    fun fullBuildWritesFormatVersionAndPageSizeIntoBothFiles() {
        val main = temp.root.resolve("main.sqlite")
        val patch = temp.root.resolve("patch.sqlite")

        Generator.build(export("5<drevo>А<drevo>1<drevo> <drevo>$text"), main, patch, full = true)

        for (file in listOf(main, patch)) {
            assertEquals(file.name, FORMAT_VERSION, pragma(file, "user_version"))
            assertEquals(file.name, 1024, pragma(file, "page_size"))
        }
    }

    @Test
    fun patchBuildStoresOnlyChangedTextsAgainstMain() {
        val main = temp.root.resolve("main.sqlite")
        Generator.build(
            export("5<drevo>А<drevo>1<drevo> <drevo>$text", "6<drevo>Б<drevo>1<drevo> <drevo>$text"),
            main,
            temp.root.resolve("full-patch.sqlite"),
            full = true,
        )
        val patch = temp.root.resolve("patch.sqlite")

        val stats =
            Generator.build(
                export("5<drevo>А<drevo>1<drevo> <drevo>$text", "6<drevo>Б<drevo>1<drevo> <drevo>${text}Ещё."),
                main,
                patch,
                full = false,
            )

        assertEquals(BuildStats(all = 2, added = 0, patched = 1), stats)
        assertEquals(listOf(6L to 0), rows(patch, "SELECT _id, isfulltext FROM tblTexts ORDER BY _id") { it.getLong(1) to it.getInt(2) })
        assertEquals(FORMAT_VERSION, pragma(patch, "user_version"))
    }

    @Test
    fun failedFullBuildLeavesNoDatabaseBehind() {
        val db = temp.newFolder("DB")

        assertThrows(IllegalStateException::class.java) {
            Generator.build(
                export("5<drevo>А<drevo>1<drevo> <drevo>$text", "6<drevo>Б<drevo>1,x<drevo> <drevo>$text"),
                db.resolve("main.sqlite"),
                db.resolve("patch.sqlite"),
                full = true,
            )
        }

        assertEquals(emptyList<String>(), db.list()!!.toList())
    }

    @Test
    fun failedPatchBuildLeavesMainAsItWasAndNoPatch() {
        val db = temp.newFolder("DB")
        val main = db.resolve("main.sqlite")
        Generator.build(export("5<drevo>А<drevo>1<drevo> <drevo>$text"), main, temp.root.resolve("full-patch.sqlite"), full = true)
        val mainBytes = main.readBytes()

        assertThrows(IllegalStateException::class.java) {
            Generator.build(export("5<drevo>А<drevo>1<drevo> <drevo>$text", "6<drevo>Б"), main, db.resolve("patch.sqlite"), full = false)
        }

        assertEquals(listOf("main.sqlite"), db.list()!!.toList())
        assertArrayEquals(mainBytes, main.readBytes())
    }

    @Test
    fun failureNamesTheExportLineAndArticleId() {
        val export = export("5<drevo>А<drevo>1<drevo> <drevo>$text", "", "6<drevo>Б<drevo>1,x<drevo> <drevo>$text")

        val error =
            assertThrows(IllegalStateException::class.java) {
                Generator.build(export, temp.root.resolve("main.sqlite"), temp.root.resolve("patch.sqlite"), full = true)
            }

        assertTrue(error.message, error.message!!.startsWith("${export.name} line 3, _id 6: "))
    }

    private fun export(vararg lines: String): File = temp.newFile().apply { writeText(lines.joinToString("\n"), Charsets.UTF_8) }

    private fun pragma(
        file: File,
        name: String,
    ): Int = rows(file, "PRAGMA $name") { it.getInt(1) }.single()

    private fun <T> rows(
        file: File,
        sql: String,
        read: (java.sql.ResultSet) -> T,
    ): List<T> =
        DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result -> generateSequence { if (result.next()) read(result) else null }.toList() }
            }
        }
}

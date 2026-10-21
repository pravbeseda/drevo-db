package ru.drevoinfo.db

import org.junit.Assert.assertEquals
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

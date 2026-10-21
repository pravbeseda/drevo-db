package ru.drevoinfo.db

import org.sqlite.SQLiteConfig
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.DriverManager

/** The `user_version` both files carry; docs/format.md defines what each version means. */
const val FORMAT_VERSION = 1

private const val PAGE_SIZE = 1024
private const val CREATE_MAIN_TEXTS = "CREATE TABLE IF NOT EXISTS tblTexts(_id INTEGER PRIMARY KEY, ziptext BLOB, isfulltext)"

object Generator {
    /**
     * Builds the databases from [export]. `full` writes a new [main] with every text and a [patch] holding the index
     * alone; otherwise only [patch] is written, its texts as changes against the existing [main].
     *
     * Each file is written as `<name>.tmp` and renamed only once it is complete, so a failed build leaves none behind.
     */
    fun build(
        export: File,
        main: File,
        patch: File,
        full: Boolean,
    ): BuildStats {
        // main goes first: a new patch without its main would be released against the old main.
        val temps = (if (full) listOf(main, patch) else listOf(patch)).associateWith { File("${it.path}.tmp").apply { delete() } }
        try {
            val stats = write(export, temps[main] ?: main, temps.getValue(patch), full)
            temps.forEach { (target, temp) -> Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE) }
            return stats
        } finally {
            temps.values.forEach { it.delete() }
        }
    }

    private fun write(
        export: File,
        main: File,
        patch: File,
        full: Boolean,
    ): BuildStats {
        connect(patch).use { patchDb ->
            patchDb.execute("CREATE TABLE IF NOT EXISTS tblSlovnik(_id INTEGER, title TEXT PRIMARY KEY ASC)")
            patchDb.execute("CREATE TABLE IF NOT EXISTS tblTopics(article_id INTEGER, topic INTEGER, PRIMARY KEY ([article_id], [topic]))")
            patchDb.execute("CREATE TABLE IF NOT EXISTS tblTexts(_id INTEGER PRIMARY KEY ASC, ziptext BLOB, isfulltext)")
            // In full mode the texts go into a new main database; in patch mode into the patch, as changes against main.
            val texts = if (full) connect(main).apply { execute(CREATE_MAIN_TEXTS) } else patchDb
            val base = if (full) null else connect(main, readOnly = true)
            try {
                patchDb.autoCommit = false
                texts.autoCommit = false
                // VACUUM refuses to run while a statement is open, so the writer is closed before the commit.
                val stats =
                    Writer(patchDb, texts, base).use { writer ->
                        export.bufferedReader(Charsets.UTF_8).useLines { lines ->
                            lines.forEachIndexed { index, line ->
                                if (line.javaTrim().isEmpty()) return@forEachIndexed
                                try {
                                    writer.write(line)
                                } catch (e: Exception) {
                                    val id = line.substringBefore("<drevo>").javaTrim()
                                    throw IllegalStateException("${export.name} line ${index + 1}, _id $id: ${e.message}", e)
                                }
                            }
                        }
                        writer.stats
                    }
                println("Creating indexes...")
                patchDb.execute("CREATE INDEX IF NOT EXISTS id ON tblSlovnik(_id)")
                patchDb.execute("CREATE INDEX IF NOT EXISTS title ON tblSlovnik(title)")
                println("Commit patch...")
                commitAndVacuum(patchDb)
                if (texts !== patchDb) {
                    println("Commit main...")
                    commitAndVacuum(texts)
                }
                return stats
            } finally {
                if (texts !== patchDb) texts.close()
                base?.close()
            }
        }
    }
}

private class Writer(
    patch: Connection,
    texts: Connection,
    base: Connection?,
) : AutoCloseable {
    var stats = BuildStats()
        private set

    private val insertTitle = patch.prepareStatement("INSERT INTO tblSlovnik (_id, title) VALUES (?, ?)")
    private val insertTopic = patch.prepareStatement("INSERT INTO tblTopics (article_id, topic) VALUES (?, ?)")
    private val insertText = texts.prepareStatement("INSERT INTO tblTexts (_id, ziptext, isfulltext) VALUES (?, ?, ?)")
    private val selectBaseText = base?.prepareStatement("SELECT ziptext FROM tblTexts WHERE _id = ?")

    fun write(line: String) {
        val record = ArticleRecord.parse(line)
        insertTitle.setLong(1, record.id)
        insertTitle.setString(2, record.title)
        insertTitle.executeUpdate()
        for (topic in record.topics) {
            insertTopic.setLong(1, record.id)
            insertTopic.setInt(2, topic)
            insertTopic.executeUpdate()
        }
        print("${record.id} - ${record.topics}")

        stats = stats.copy(all = stats.all + 1)
        when (val stored = TextChange.of(baseText(record.id), record.text)) {
            StoredText.Unchanged -> {
                print(" - equals")
            }

            is StoredText.Delta -> {
                insert(record.id, stored.delta, isFullText = false)
                print(" - patch")
                stats = stats.copy(patched = stats.patched + 1)
            }

            is StoredText.Full -> {
                insert(record.id, stored.text, isFullText = true)
                print(" - add")
                stats = stats.copy(added = stats.added + 1)
            }
        }
        println(" - done")
    }

    override fun close() {
        listOfNotNull(insertTitle, insertTopic, insertText, selectBaseText).forEach { it.close() }
    }

    private fun baseText(id: Long): String? {
        val select = selectBaseText ?: return null
        select.setLong(1, id)
        return select.executeQuery().use { if (it.next()) ZipText.unzip(it.getBytes(1)) else null }
    }

    private fun insert(
        id: Long,
        text: String,
        isFullText: Boolean,
    ) {
        insertText.setLong(1, id)
        insertText.setBytes(2, ZipText.zip(text))
        insertText.setInt(3, if (isFullText) 1 else 0)
        insertText.executeUpdate()
    }
}

internal fun connect(
    file: File,
    readOnly: Boolean = false,
): Connection {
    val config =
        SQLiteConfig().apply {
            setReadOnly(readOnly)
            // The page size the published databases have always had: sqlite-jdbc's default of 4096 makes main ~11 MB larger.
            setPageSize(PAGE_SIZE)
        }
    return DriverManager.getConnection("jdbc:sqlite:$file", config.toProperties())
}

private fun Connection.execute(sql: String) {
    createStatement().use { it.execute(sql) }
}

private fun commitAndVacuum(connection: Connection) {
    connection.execute("PRAGMA user_version = $FORMAT_VERSION")
    connection.commit()
    connection.autoCommit = true
    connection.execute("VACUUM")
}

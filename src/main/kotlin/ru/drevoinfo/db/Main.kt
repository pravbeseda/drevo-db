package ru.drevoinfo.db

import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.system.exitProcess

private const val DB_DIR = "./DB/"
private const val EXPORT = DB_DIR + "drevo.txt"
private const val PAGE_SIZE = 1024
private const val CREATE_MAIN_TEXTS = "CREATE TABLE IF NOT EXISTS tblTexts(_id INTEGER PRIMARY KEY, ziptext BLOB, isfulltext)"

/**
 * Usage: `drevo-db <mode> <mainVersion> <patchVersion>`.
 *
 * `patch` builds only the patch database, against the existing main one; `full` rebuilds both,
 * so every text goes into main and the patch holds the index alone.
 */
fun main(args: Array<String>) {
    if (args.size < 3) {
        println("Usage: drevo-db <mode> <mainVersion> <patchVersion>")
        println("  mode: patch | full")
        exitProcess(1)
    }
    val (mode, mainVersion, patchVersion) = args
    if (mode != "patch" && mode != "full") {
        println("Unknown mode: $mode. Use 'patch' or 'full'.")
        exitProcess(1)
    }
    val full = mode == "full"
    val mainPath = "${DB_DIR}main.$mainVersion.ru.drevoinfo.obb"
    val patchPath = "${DB_DIR}patch.$patchVersion.ru.drevoinfo.obb"

    println("Mode: $mode")
    println("mainBase: $mainPath")
    println("patchBase: $patchPath")
    println("Start!")

    val stats = build(full, mainPath, patchPath)

    println(stats.summary())
    println("Done!")
}

private fun build(
    full: Boolean,
    mainPath: String,
    patchPath: String,
): BuildStats {
    File(patchPath).delete()
    if (full) File(mainPath).delete()
    connect(patchPath).use { patch ->
        patch.execute("CREATE TABLE IF NOT EXISTS tblSlovnik(_id INTEGER, title TEXT PRIMARY KEY ASC)")
        patch.execute("CREATE TABLE IF NOT EXISTS tblTopics(article_id INTEGER, topic INTEGER, PRIMARY KEY ([article_id], [topic]))")
        patch.execute("CREATE TABLE IF NOT EXISTS tblTexts(_id INTEGER PRIMARY KEY ASC, ziptext BLOB, isfulltext)")
        // In full mode the texts go into a new main database; in patch mode into the patch, as changes against main.
        val texts = if (full) connect(mainPath).apply { execute(CREATE_MAIN_TEXTS) } else patch
        val base = if (full) null else connect(mainPath, readOnly = true)
        try {
            patch.autoCommit = false
            texts.autoCommit = false
            // VACUUM refuses to run while a statement is open, so the writer is closed before the commit.
            val stats =
                Writer(patch, texts, base).use { writer ->
                    File(EXPORT).bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.filter { it.javaTrim().isNotEmpty() }.forEach(writer::write)
                    }
                    writer.stats
                }
            println("Creating indexes...")
            patch.execute("CREATE INDEX IF NOT EXISTS id ON tblSlovnik(_id)")
            patch.execute("CREATE INDEX IF NOT EXISTS title ON tblSlovnik(title)")
            println("Commit patch...")
            commitAndVacuum(patch)
            if (texts !== patch) {
                println("Commit main...")
                commitAndVacuum(texts)
            }
            return stats
        } finally {
            if (texts !== patch) texts.close()
            base?.close()
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

private fun connect(
    path: String,
    readOnly: Boolean = false,
): Connection {
    val config =
        SQLiteConfig().apply {
            setReadOnly(readOnly)
            // The page size the published databases have always had: sqlite-jdbc's default of 4096 makes main ~11 MB larger.
            setPageSize(PAGE_SIZE)
        }
    return DriverManager.getConnection("jdbc:sqlite:$path", config.toProperties())
}

private fun Connection.execute(sql: String) {
    createStatement().use { it.execute(sql) }
}

private fun commitAndVacuum(connection: Connection) {
    connection.commit()
    connection.autoCommit = true
    connection.execute("VACUUM")
}

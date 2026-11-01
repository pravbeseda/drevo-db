package ru.drevoinfo.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Runs make.sh from a scratch root whose download, Gradle, generator, publish.sh and `date` are fakes that log their calls. */
class MakeScriptTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var db: File
    private lateinit var log: File

    private class Run(
        val exitCode: Int,
        val output: String,
    )

    @Before
    fun setUp() {
        File("make.sh").copyTo(temp.root.resolve("make.sh"))
        db = temp.newFolder("DB")
        db.resolve("main.254.ru.drevoinfo.obb").writeText("main")
        db.resolve("patch.259.ru.drevoinfo.obb").writeText("patch")
        log = temp.newFolder("log")
        executable("DB/download.sh", "echo download >> \"\$FAKE_LOG/calls\"")
        executable("gradlew", "exit 0")
        executable("publish.sh", "echo publish >> \"\$FAKE_LOG/calls\"")
        executable(
            "build/install/drevo-db/bin/drevo-db",
            """
            echo "generator $*" >> "${'$'}FAKE_LOG/calls"
            [ "$1" = full ] && echo built > "DB/main.$2.ru.drevoinfo.obb"
            echo built > "DB/patch.$3.ru.drevoinfo.obb"
            echo "Count: 37865 Added: 185 (0%) Patched: 29063 (76%)"
            """.trimIndent(),
        )
        executable("bin/date", "[ \"$1\" = +%y%m%d ] && echo 261101 || echo 1.11.2026")
    }

    private fun executable(
        path: String,
        body: String,
    ) = temp.root.resolve(path).apply {
        parentFile.mkdirs()
        writeText("#!/bin/bash\n$body\n")
        setExecutable(true)
    }

    private fun run(mode: String): Run {
        val process =
            ProcessBuilder("bash", "make.sh", mode)
                .directory(temp.root)
                .redirectErrorStream(true)
                .apply {
                    environment()["PATH"] = temp.root.resolve("bin").path + File.pathSeparator + System.getenv("PATH")
                    environment()["FAKE_LOG"] = log.path
                }.start()
        process.outputStream.bufferedWriter().use { it.write("y\n") }
        val output = process.inputStream.bufferedReader().readText()
        return Run(process.waitFor(), output)
    }

    private fun calls(): List<String> =
        log
            .resolve("calls")
            .takeIf { it.exists() }
            ?.readLines()
            .orEmpty()

    private fun history() = temp.root.resolve("build-history.txt").readText()

    @Test
    fun patchBuildIdsThePatchByTheBuildDate() {
        val run = run("patch")

        assertEquals(run.output, 0, run.exitCode)
        assertEquals(listOf("download", "generator patch 254 261101", "publish"), calls())
        assertEquals("254/261101\t1.11.2026 - Count: 37865 Added: 185 (0%) Patched: 29063 (76%) - 0\n", history())
    }

    @Test
    fun fullBuildIdsBothDatabasesByTheBuildDate() {
        val run = run("full")

        assertEquals(run.output, 0, run.exitCode)
        assertEquals(listOf("download", "generator full 261101 261101", "publish"), calls())
        assertTrue(history().startsWith("261101/261101\t"))
    }

    @Test
    fun refusesASecondPatchBuildOnTheSameDay() {
        db.resolve("patch.261101.ru.drevoinfo.obb").writeText("today's patch")

        val run = run("patch")

        assertNotEquals(0, run.exitCode)
        assertTrue(run.output, run.output.contains("patch.261101.ru.drevoinfo.obb"))
        assertEquals(emptyList<String>(), calls())
        assertFalse(temp.root.resolve("build-history.txt").exists())
    }

    @Test
    fun refusesAFullBuildOnTheDayOfAPatchBuild() {
        db.resolve("patch.261101.ru.drevoinfo.obb").writeText("today's patch")

        val run = run("full")

        assertNotEquals(0, run.exitCode)
        assertEquals(emptyList<String>(), calls())
    }
}

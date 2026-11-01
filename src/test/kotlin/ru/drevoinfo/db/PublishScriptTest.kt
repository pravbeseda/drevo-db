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

/** Runs publish.sh in a scratch DB/ with fake `gh` and `git` on PATH that record what would be published and pushed. */
class PublishScriptTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val script = File("publish.sh").absoluteFile
    private lateinit var log: File

    private class Run(
        val exitCode: Int,
        val output: String,
    )

    @Before
    fun setUp() {
        val db = temp.newFolder("DB")
        db.resolve("main.229.ru.drevoinfo.obb").writeText("old main")
        db.resolve("main.254.ru.drevoinfo.obb").writeText("main")
        db.resolve("patch.257.ru.drevoinfo.obb").writeText("old patch")
        db.resolve("patch.258.ru.drevoinfo.obb").writeText("patch")
        db.resolve("stat.txt").writeText("37853;01.10.2026;145;0;9000;12")
        history("254/258\t1.10.2026 - Count: 37853 Added: 145 (0%) Patched: 29086 (76%) - 16")
        log = temp.newFolder("log")
        val bin = temp.newFolder("bin")
        bin.resolve("gh").apply {
            writeText(FAKE_GH)
            setExecutable(true)
        }
        bin.resolve("git").apply {
            writeText(FAKE_GIT)
            setExecutable(true)
        }
    }

    private fun history(vararg lines: String) = temp.root.resolve("build-history.txt").writeText(lines.joinToString("\n", postfix = "\n"))

    private fun run(
        answer: String = "y",
        existingTag: Boolean = false,
        mainRelease: String = "",
        branch: String = "main",
        historyChanged: Boolean = true,
        historyStaged: Boolean = false,
    ): Run {
        val process =
            ProcessBuilder("bash", script.path)
                .directory(temp.root)
                .redirectErrorStream(true)
                .apply {
                    environment()["PATH"] = temp.root.resolve("bin").path + File.pathSeparator + System.getenv("PATH")
                    environment()["FAKE_GH_LOG"] = log.path
                    environment()["FAKE_GH_EXISTING_TAG"] = if (existingTag) "1" else ""
                    environment()["FAKE_GH_MAIN_RELEASE"] = mainRelease
                    environment()["FAKE_GIT_BRANCH"] = branch
                    environment()["FAKE_GIT_CHANGED"] = if (historyChanged) "1" else ""
                    environment()["FAKE_GIT_STAGED"] = if (historyStaged) "1" else ""
                }.start()
        process.outputStream.bufferedWriter().use { it.write(answer + "\n") }
        val output = process.inputStream.bufferedReader().readText()
        return Run(process.waitFor(), output)
    }

    private fun createdArgs(): List<String> =
        log
            .resolve("create-args")
            .takeIf { it.exists() }
            ?.readLines()
            .orEmpty()

    private fun gitCalls(): List<String> =
        log
            .resolve("git-calls")
            .takeIf { it.exists() }
            ?.readLines()
            .orEmpty()

    private fun committed() = gitCalls().any { it.startsWith("commit ") }

    /** The positional arguments after the tag: every `--option` here takes a value. */
    private fun uploadedFiles(): List<String> {
        val files = mutableListOf<String>()
        val args = createdArgs().drop(1).iterator()
        while (args.hasNext()) {
            val arg = args.next()
            if (arg.startsWith("--")) args.next() else files += File(arg).name
        }
        return files
    }

    @Test
    fun publishesTheNewestPairWithMainWhenNoReleaseHoldsIt() {
        val run = run()

        assertEquals(run.output, 0, run.exitCode)
        assertEquals("db-254-258", createdArgs().first())
        assertEquals(
            listOf("main.254.ru.drevoinfo.obb", "patch.258.ru.drevoinfo.obb", "stat.txt", "SHA256SUMS"),
            uploadedFiles(),
        )
        assertTrue(log.resolve("notes").readText().contains("main.254.ru.drevoinfo.obb is attached here"))
    }

    @Test
    fun leavesMainOutAndNamesTheReleaseThatHoldsIt() {
        val run = run(mainRelease = "db-254-257")

        assertEquals(run.output, 0, run.exitCode)
        assertEquals(listOf("patch.258.ru.drevoinfo.obb", "stat.txt", "SHA256SUMS"), uploadedFiles())
        assertTrue(log.resolve("notes").readText().contains("main.254.ru.drevoinfo.obb is attached to db-254-257"))
    }

    @Test
    fun notesCarryTheBuildStatistics() {
        run()

        assertTrue(log.resolve("notes").readText().contains("Count: 37853 Added: 145 (0%) Patched: 29086 (76%)"))
    }

    @Test
    fun checksumsCoverMainEvenWhenItIsNotAttached() {
        run(mainRelease = "db-254-257")

        val names = log.resolve("SHA256SUMS").readLines().map { it.substringAfter("  ") }
        assertEquals(listOf("main.254.ru.drevoinfo.obb", "patch.258.ru.drevoinfo.obb", "stat.txt"), names)
        assertTrue(log.resolve("SHA256SUMS").readText().startsWith(sha256("main")))
    }

    @Test
    fun refusesATagThatIsAlreadyPublished() {
        val run = run(existingTag = true)

        assertNotEquals(0, run.exitCode)
        assertTrue(run.output, run.output.contains("db-254-258"))
        assertTrue(createdArgs().isEmpty())
    }

    @Test
    fun refusesAPairThatBuildHistoryDoesNotRecord() {
        history("254/257\t1.09.2026 - Count: 37843 Added: 119 (0%) Patched: 29100 (76%) - 15")

        val run = run()

        assertNotEquals(0, run.exitCode)
        assertTrue(run.output, run.output.contains("254/258"))
        assertTrue(createdArgs().isEmpty())
    }

    @Test
    fun namesTheMissingDatabaseInAnEmptyDbFolder() {
        temp.root
            .resolve("DB")
            .listFiles()!!
            .forEach { it.delete() }

        val run = run()

        assertNotEquals(0, run.exitCode)
        assertTrue(run.output, run.output.contains("no main or patch database"))
    }

    @Test
    fun publishesNothingWhenTheAnswerIsNo() {
        val run = run(answer = "n")

        assertEquals(run.output, 0, run.exitCode)
        assertTrue(createdArgs().isEmpty())
    }

    @Test
    fun publishesNothingOnAnEmptyAnswer() {
        run(answer = "")

        assertFalse(log.resolve("create-args").exists())
    }

    @Test
    fun commitsBuildHistoryAndPushesMainAfterPublishing() {
        val run = run()

        assertEquals(run.output, 0, run.exitCode)
        val calls = gitCalls()
        val commit = calls.single { it.startsWith("commit ") }
        assertTrue(commit, commit.contains("--no-verify"))
        assertTrue(commit, commit.endsWith("-- build-history.txt"))
        assertTrue(calls.toString(), calls.indexOf(commit) < calls.indexOf("push origin main"))
    }

    @Test
    fun commitsABuildHistoryThatIsAlreadyStaged() {
        run(historyStaged = true)

        assertTrue(gitCalls().toString(), committed())
    }

    @Test
    fun commitsNothingWhenNothingIsPublished() {
        run(answer = "n")

        assertFalse(committed())
    }

    @Test
    fun leavesTheCommitToTheMaintainerOffMain() {
        val run = run(branch = "feature")

        assertEquals(run.output, 0, run.exitCode)
        assertFalse(committed())
        assertFalse(gitCalls().any { it.startsWith("push") })
        assertTrue(run.output, run.output.contains("build-history.txt"))
    }

    @Test
    fun skipsTheCommitWhenBuildHistoryIsUnchanged() {
        val run = run(historyChanged = false)

        assertEquals(run.output, 0, run.exitCode)
        assertFalse(committed())
        assertFalse(gitCalls().any { it.startsWith("push") })
    }

    private fun sha256(text: String): String =
        java.security.MessageDigest
            .getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        /** `release view` finds only the tag in FAKE_GH_EXISTING_TAG; `api` lists FAKE_GH_MAIN_RELEASE; `release create` is recorded. */
        val FAKE_GH =
            """
            #!/bin/bash
            case "${'$'}1 ${'$'}2" in
                "release view") [ -n "${'$'}FAKE_GH_EXISTING_TAG" ] ;;
                "api "*) [ -n "${'$'}FAKE_GH_MAIN_RELEASE" ] && echo "${'$'}FAKE_GH_MAIN_RELEASE"; exit 0 ;;
                "release create")
                    shift 2
                    previous=""
                    for arg in "${'$'}@"; do
                        echo "${'$'}arg" >> "${'$'}FAKE_GH_LOG/create-args"
                        [ "${'$'}previous" = "--notes-file" ] && cp "${'$'}arg" "${'$'}FAKE_GH_LOG/notes"
                        [ "${'$'}(basename "${'$'}arg")" = "SHA256SUMS" ] && cp "${'$'}arg" "${'$'}FAKE_GH_LOG/SHA256SUMS"
                        previous="${'$'}arg"
                    done
                    exit 0 ;;
                *) echo "unexpected gh call: ${'$'}*" >&2; exit 1 ;;
            esac
            """.trimIndent()

        /**
         * `symbolic-ref` prints FAKE_GIT_BRANCH; `diff --quiet` reports a change when FAKE_GIT_CHANGED is set, except that a
         * change already staged (FAKE_GIT_STAGED) is seen only against HEAD; every call is recorded.
         */
        val FAKE_GIT =
            """
            #!/bin/bash
            echo "${'$'}*" >> "${'$'}FAKE_GH_LOG/git-calls"
            case "${'$'}1" in
                symbolic-ref) echo "${'$'}FAKE_GIT_BRANCH" ;;
                diff)
                    case " ${'$'}* " in
                        *" HEAD "*) [ -z "${'$'}FAKE_GIT_CHANGED" ] ;;
                        *) [ -z "${'$'}FAKE_GIT_CHANGED" ] || [ -n "${'$'}FAKE_GIT_STAGED" ] ;;
                    esac ;;
                *) true ;;
            esac
            """.trimIndent()
    }
}

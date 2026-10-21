package ru.drevoinfo.db

import java.io.File
import kotlin.system.exitProcess

private const val DB_DIR = "./DB/"
private const val EXPORT = DB_DIR + "drevo.txt"

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

    val stats = Generator.build(File(EXPORT), File(mainPath), File(patchPath), full)

    println(stats.summary())
    println("Done!")
}

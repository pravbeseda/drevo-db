package ru.drevoinfo.db

/** Article counts of one build. */
data class BuildStats(
    val all: Int = 0,
    val added: Int = 0,
    val patched: Int = 0,
) {
    /** The line make.sh greps for (`^Count:`) and appends to build-history.txt. */
    fun summary(): String = "Count: $all Added: $added (${100 * added / all}%) Patched: $patched (${100 * patched / all}%)"
}

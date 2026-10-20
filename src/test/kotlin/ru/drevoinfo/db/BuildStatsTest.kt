package ru.drevoinfo.db

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildStatsTest {
    @Test
    fun summaryIsTheCountLineMakeShRecords() {
        val stats = BuildStats(all = 200, added = 3, patched = 5)

        assertEquals("Count: 200 Added: 3 (1%) Patched: 5 (2%)", stats.summary())
    }
}

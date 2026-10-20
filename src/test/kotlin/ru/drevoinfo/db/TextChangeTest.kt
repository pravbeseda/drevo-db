package ru.drevoinfo.db

import com.nothome.delta.text.TextPatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChangeTest {
    private val base = "Начало статьи. ".repeat(50)

    @Test
    fun storesFullTextWhenThereIsNoBaseText() {
        assertEquals(StoredText.Full("новая"), TextChange.of(null, "новая"))
    }

    @Test
    fun storesNothingWhenTextIsUnchanged() {
        assertEquals(StoredText.Unchanged, TextChange.of(base, base))
    }

    @Test
    fun storesDeltaThatRebuildsTheNewTextFromTheBase() {
        val newText = base + "Добавлено."

        val stored = TextChange.of(base, newText)

        assertTrue(stored is StoredText.Delta)
        assertEquals(newText, TextPatcher(base).patch((stored as StoredText.Delta).delta))
    }

    @Test
    fun storesFullTextWhenDeltaIsAtLeast40PercentOfTheNewText() {
        val newText = "Совсем другой текст статьи."

        assertEquals(StoredText.Full(newText), TextChange.of(base, newText))
    }
}

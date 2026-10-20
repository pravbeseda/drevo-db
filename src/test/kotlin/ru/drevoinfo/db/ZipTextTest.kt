package ru.drevoinfo.db

import org.junit.Assert.assertEquals
import org.junit.Test

class ZipTextTest {
    @Test
    fun writesZlibStream() {
        val bytes = ZipText.zip("текст")

        assertEquals(0x78, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun roundTripsCyrillicAndCharactersOutsideTheBmp() {
        val text = "Древо 𝄞 ⲁ"

        assertEquals(text, ZipText.unzip(ZipText.zip(text)))
    }
}

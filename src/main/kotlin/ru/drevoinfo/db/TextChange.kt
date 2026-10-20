package ru.drevoinfo.db

import com.nothome.delta.text.Delta

// A delta at least this share of the new text, in percent, is stored as the full text instead.
private const val MAX_DELTA_PERCENT = 40

/** How an article's text goes into the patch database. */
sealed interface StoredText {
    /** The base text is current: the patch holds no row. */
    data object Unchanged : StoredText

    /** A javaxdelta text delta against the base text (`isfulltext = 0`). */
    data class Delta(
        val delta: String,
    ) : StoredText

    /** The whole text (`isfulltext = 1`). */
    data class Full(
        val text: String,
    ) : StoredText
}

object TextChange {
    fun of(
        baseText: String?,
        newText: String,
    ): StoredText {
        if (baseText == null) return StoredText.Full(newText)
        if (baseText == newText) return StoredText.Unchanged
        val delta = Delta().compute(baseText, newText)
        return if (100 * delta.length / newText.length < MAX_DELTA_PERCENT) StoredText.Delta(delta) else StoredText.Full(newText)
    }
}

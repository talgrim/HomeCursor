package com.homecursor.client.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {
    private fun bytes(text: String) = text.toByteArray(Charsets.UTF_8).size

    @Test
    fun emptyText_givesNoChunks() {
        assertTrue(TextChunker.split("", 10).isEmpty())
    }

    @Test
    fun shortText_isOneChunk() {
        assertEquals(listOf("hello"), TextChunker.split("hello", 10))
    }

    @Test
    fun longText_isSplitIntoChunksWithinTheLimit_andJoinsBackToTheOriginal() {
        val text = "abcdefghij".repeat(7)
        val chunks = TextChunker.split(text, 16)

        assertTrue(chunks.all { bytes(it) <= 16 })
        assertEquals(text, chunks.joinToString(""))
        assertEquals(5, chunks.size)
    }

    @Test
    fun multiByteCharacters_countByUtf8Size() {
        // "\u017C" is two bytes: three of them do not fit in five bytes.
        val chunks = TextChunker.split("\u017C\u017C\u017C", 5)

        assertEquals(listOf("\u017C\u017C", "\u017C"), chunks)
    }

    @Test
    fun emoji_isNeverSplitInTheMiddleOfItsSurrogatePair() {
        val emoji = "\uD83D\uDE42"
        val text = "ab" + emoji + "cd" + emoji

        val chunks = TextChunker.split(text, 6)

        assertEquals(text, chunks.joinToString(""))
        for (chunk in chunks) {
            assertTrue(bytes(chunk) <= 6)
            assertTrue(chunk.isEmpty() || !Character.isLowSurrogate(chunk.first()))
            assertTrue(chunk.isEmpty() || !Character.isHighSurrogate(chunk.last()))
        }
    }

    @Test
    fun limitSmallerThanOneCodePoint_throws() {
        assertThrows(IllegalArgumentException::class.java) { TextChunker.split("a", 3) }
    }
}

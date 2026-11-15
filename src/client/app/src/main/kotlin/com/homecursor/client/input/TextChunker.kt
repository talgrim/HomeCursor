package com.homecursor.client.input

/** Splits text into pieces that each fit the wire format's string limit. */
internal object TextChunker {
    /**
     * Splits [text] into consecutive pieces of at most [maxBytes] UTF-8 bytes each. A piece never ends inside a
     * surrogate pair (an emoji stays whole), so the pieces joined together are exactly [text]. An empty text gives no
     * pieces.
     */
    fun split(text: String, maxBytes: Int): List<String> {
        require(maxBytes >= MAX_CODE_POINT_BYTES) { "maxBytes must fit one code point." }
        val chunks = mutableListOf<String>()
        var start = 0
        var bytes = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val size = utf8Size(codePoint)
            if (bytes + size > maxBytes) {
                chunks += text.substring(start, index)
                start = index
                bytes = 0
            }
            bytes += size
            index += Character.charCount(codePoint)
        }
        if (start < text.length) chunks += text.substring(start)
        return chunks
    }

    private fun utf8Size(codePoint: Int): Int = when {
        codePoint < 0x80 -> 1
        codePoint < 0x800 -> 2
        codePoint < 0x10000 -> 3
        else -> MAX_CODE_POINT_BYTES
    }

    private const val MAX_CODE_POINT_BYTES = 4
}

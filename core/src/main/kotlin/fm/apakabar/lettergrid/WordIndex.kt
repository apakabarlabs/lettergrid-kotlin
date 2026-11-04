package fm.apakabar.lettergrid

internal class WordIndex(
    words: Set<String>,
) {
    private class Node {
        val letters = mutableListOf<Int>()
        val children = mutableListOf<Int>()
        var word: String? = null
        var wordCount = 0

        fun child(letter: Int): Int? = letters.indexOf(letter).takeIf { it >= 0 }?.let { children[it] }
    }

    private val nodes = mutableListOf(Node())
    private val alphabet = mutableMapOf<String, Int>()
    var longest = 0
        private set

    init {
        for (word in words) {
            if (word.isNotEmpty()) nodes[0].wordCount += 1
            val letters = word.cells
            longest = maxOf(longest, letters.size)
            var index = 0
            for (character in letters) {
                val letter = codeAdding(character)
                val existing = nodes[index].child(letter)
                if (existing != null) {
                    index = existing
                } else {
                    val next = nodes.size
                    nodes += Node()
                    nodes[index].letters += letter
                    nodes[index].children += next
                    index = next
                }
                nodes[index].wordCount += 1
            }
            nodes[index].word = word
        }
    }

    private fun codeAdding(character: String): Int = alphabet.getOrPut(character) { alphabet.size }

    fun code(of: String): Int? = alphabet[of]

    fun next(
        after: Int?,
        code: Int,
    ): Int? = after?.let { nodes[it].child(code) }

    fun next(
        after: Int?,
        letter: String,
    ): Int? = code(of = letter)?.let { next(after, it) }

    fun word(at: Int?): String? = at?.let { nodes[it].word }

    fun wordCount(below: Int): Int = nodes[below].wordCount

    fun path(to: String): List<Int> {
        val path = mutableListOf(0)
        for (letter in to.cells) {
            val next = next(path.last(), letter) ?: return emptyList()
            path += next
        }
        return path
    }
}

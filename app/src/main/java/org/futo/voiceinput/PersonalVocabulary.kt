package org.futo.voiceinput

object PersonalVocabulary {
    fun apply(text: String, vocabulary: String): String {
        if (text.isBlank() || vocabulary.isBlank()) return text

        val entries = vocabulary.split(ENTRY_SEPARATOR)
            .map(String::trim)
            .filter(String::isNotEmpty)
        val preferredTerms = entries.map { normalize(it.substringAfter("=>").trim()) }.toSet()
        var corrected = text

        entries.mapNotNull { entry ->
            entry.split("=>", limit = 2).takeIf { it.size == 2 }
                ?.map(String::trim)
                ?.takeIf { it.all(String::isNotEmpty) }
        }.forEach { (heard, preferred) ->
            corrected = Regex(
                "(?i)(?<![\\p{L}\\p{N}])${Regex.escape(heard)}(?![\\p{L}\\p{N}])"
            ).replace(corrected) { preferred }
        }

        entries.filterNot { it.contains("=>") }
            .sortedByDescending { normalize(it).length }
            .forEach { preferred -> corrected = correctTerm(corrected, preferred, preferredTerms) }

        return corrected
    }

    private fun correctTerm(text: String, preferred: String, preferredTerms: Set<String>): String {
        val preferredWords = WORD.findAll(preferred).toList()
        if (preferredWords.isEmpty()) return text

        val words = WORD.findAll(text).toList()
        val preferredWidth = preferredWords.size
        val wanted = normalize(preferred)
        val maxDistance = when {
            wanted.length >= 10 -> 2
            wanted.length >= 5 -> 1
            else -> 0
        }

        val replacements = mutableListOf<IntRange>()
        var index = 0
        while (index < words.size) {
            val widths = (preferredWidth + 1 downTo maxOf(1, preferredWidth - 1))
            fun candidate(width: Int): String? {
                if (index + width > words.size) return null
                val first = words[index]
                val last = words[index + width - 1]
                return normalize(text.substring(first.range.first, last.range.last + 1))
            }
            // An exact preferred spelling wins over fuzzy matches, including alias outputs.
            val matchedWidth = widths.firstOrNull { candidate(it) == wanted } ?: widths.firstOrNull { width ->
                val candidate = candidate(width) ?: return@firstOrNull false
                candidate !in preferredTerms &&
                    (index until index + width).none { normalize(words[it].value) in preferredTerms } &&
                    editDistance(candidate, wanted) <= maxDistance
            }
            if (matchedWidth == null) {
                index++
                continue
            }
            replacements += words[index].range.first..words[index + matchedWidth - 1].range.last
            index += matchedWidth
        }
        return replacements.asReversed().fold(text) { result, range ->
            result.replaceRange(range, preferred)
        }
    }

    private fun normalize(value: String): String =
        value.lowercase().filter(Char::isLetterOrDigit)

    private fun editDistance(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        left.forEachIndexed { leftIndex, leftChar ->
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            right.forEachIndexed { rightIndex, rightChar ->
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (leftChar == rightChar) 0 else 1
                )
            }
            previous = current
        }
        return previous.last()
    }

    private val WORD = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*")
    private val ENTRY_SEPARATOR = Regex("[,\\r\\n]+")
}

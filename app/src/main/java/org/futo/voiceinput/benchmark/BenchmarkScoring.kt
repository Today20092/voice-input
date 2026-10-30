package org.futo.voiceinput.benchmark

import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data class EditScore(val substitutions: Int, val deletions: Int, val insertions: Int, val denominator: Int) {
    val rate: Double = (substitutions + deletions + insertions).toDouble() / denominator
}

@Serializable
data class Accuracy(val words: EditScore? = null, val characters: EditScore? = null, val unavailable: String? = null)

object BenchmarkScoring {
    const val POLICY = "nfkc-root-lower-punctuation-v1"

    // Apostrophes join contractions; other punctuation separates words. Numbers and fillers remain literal.
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .split('\u0131').joinToString("\u0131") { it.uppercase(Locale.ROOT).lowercase(Locale.ROOT) }
        .replace('\u03c2', '\u03c3').replace(Regex("['\u2019]"), "")
        .replace(Regex("[\\p{P}\\p{S}]"), " ").replace(Regex("\\s+"), " ").trim()

    fun score(reference: String?, transcript: String): Accuracy {
        val expected = normalize(reference.orEmpty())
        if (expected.isEmpty()) return Accuracy(unavailable = "No spoken reference text")
        val actual = normalize(transcript)
        return Accuracy(
            edits(expected.split(' '), actual.takeIf { it.isNotEmpty() }?.split(' ').orEmpty()),
            edits(expected.codePoints().toArray().toList(), actual.codePoints().toArray().toList())
        )
    }

    internal fun <T> edits(reference: List<T>, actual: List<T>): EditScore {
        var previousS = IntArray(actual.size + 1)
        var previousD = IntArray(actual.size + 1)
        var previousI = IntArray(actual.size + 1) { it }
        reference.forEachIndexed { index, token ->
            val rowS = IntArray(actual.size + 1)
            val rowD = IntArray(actual.size + 1)
            val rowI = IntArray(actual.size + 1)
            rowD[0] = index + 1
            actual.forEachIndexed { j, recognized ->
                val substitution = if (token == recognized) 0 else 1
                val diagonal = previousS[j] + previousD[j] + previousI[j] + substitution
                val deletion = previousS[j + 1] + previousD[j + 1] + previousI[j + 1] + 1
                val insertion = rowS[j] + rowD[j] + rowI[j] + 1
                when {
                    diagonal <= deletion && diagonal <= insertion -> {
                        rowS[j + 1] = previousS[j] + substitution
                        rowD[j + 1] = previousD[j]; rowI[j + 1] = previousI[j]
                    }
                    deletion <= insertion -> {
                        rowS[j + 1] = previousS[j + 1]
                        rowD[j + 1] = previousD[j + 1] + 1; rowI[j + 1] = previousI[j + 1]
                    }
                    else -> {
                        rowS[j + 1] = rowS[j]; rowD[j + 1] = rowD[j]; rowI[j + 1] = rowI[j] + 1
                    }
                }
            }
            previousS = rowS; previousD = rowD; previousI = rowI
        }
        return EditScore(previousS.last(), previousD.last(), previousI.last(), reference.size)
    }
}

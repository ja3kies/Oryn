package com.example.oryn.reader.ui.screen

import com.example.oryn.reader.data.importer.PdfWord
import kotlin.math.abs

object PdfReflow {
    /**
     * Algorithme Recursive XY-Cut.
     * Découpe récursivement la liste de mots en trouvant les plus grands espaces blancs (horizontaux et verticaux).
     */
    fun process(words: List<PdfWord>): String {
        if (words.isEmpty()) return ""
        val sortedWords = words.sortedWith(compareBy({ it.t }, { it.l }))
        val blocks = recursiveXYCut(sortedWords)
        
        val sb = StringBuilder()
        for (block in blocks) {
            val text = formatBlock(block)
            if (text.isNotBlank()) {
                sb.append(text).append("\n\n")
            }
        }
        return sb.toString().trim()
    }

    private fun recursiveXYCut(words: List<PdfWord>): List<List<PdfWord>> {
        if (words.size <= 1) return listOf(words)

        // 1. Chercher la plus grande coupure verticale (colonnes)
        val vCut = findVerticalCut(words)
        if (vCut != null) {
            val left = words.filter { it.r <= vCut }
            val right = words.filter { it.l >= vCut }
            // Si la coupure isole un bloc vide, on ignore
            if (left.isNotEmpty() && right.isNotEmpty()) {
                return recursiveXYCut(left) + recursiveXYCut(right)
            }
        }

        // 2. Chercher la plus grande coupure horizontale (paragraphes/sections)
        val hCut = findHorizontalCut(words)
        if (hCut != null) {
            val top = words.filter { it.b <= hCut }
            val bottom = words.filter { it.t >= hCut }
            if (top.isNotEmpty() && bottom.isNotEmpty()) {
                return recursiveXYCut(top) + recursiveXYCut(bottom)
            }
        }

        // 3. Aucune coupure possible : c'est un bloc atomique
        return listOf(words)
    }

    private fun findVerticalCut(words: List<PdfWord>): Float? {
        // On trie les mots par leur bord droit pour trouver les espaces
        val sorted = words.sortedBy { it.r }
        var maxGap = 0f
        var bestCut: Float? = null
        
        // Un gap doit être suffisamment large pour être une colonne (ex: > 4% de la page)
        val minGapThreshold = 0.04f

        for (i in 0 until sorted.size - 1) {
            val gap = sorted[i + 1].l - sorted[i].r
            if (gap > maxGap && gap > minGapThreshold) {
                // Vérifier que la coupure passe bien à travers toute la page
                // (optionnel, pour plus de robustesse on pourrait vérifier s'il y a des mots à gauche et à droite)
                maxGap = gap
                bestCut = sorted[i].r + (gap / 2f)
            }
        }
        return bestCut
    }

    private fun findHorizontalCut(words: List<PdfWord>): Float? {
        val sorted = words.sortedBy { it.b }
        var maxGap = 0f
        var bestCut: Float? = null
        
        val minGapThreshold = 0.015f // Un saut de paragraphe

        for (i in 0 until sorted.size - 1) {
            val gap = sorted[i + 1].t - sorted[i].b
            if (gap > maxGap && gap > minGapThreshold) {
                maxGap = gap
                bestCut = sorted[i].b + (gap / 2f)
            }
        }
        return bestCut
    }

    private fun formatBlock(words: List<PdfWord>): String {
        // Un bloc est déjà trié, on regroupe par ligne (Y similaire)
        val lines = mutableListOf<MutableList<PdfWord>>()
        var currentLine = mutableListOf<PdfWord>()
        var currentT = -1f

        for (w in words) {
            if (currentLine.isEmpty() || abs(w.t - currentT) < 0.01f) {
                currentLine.add(w)
                currentT = if (currentLine.size == 1) w.t else (currentT + w.t) / 2f
            } else {
                if (currentLine.isNotEmpty()) {
                    currentLine.sortBy { it.l }
                    lines.add(currentLine)
                }
                currentLine = mutableListOf(w)
                currentT = w.t
            }
        }
        if (currentLine.isNotEmpty()) {
            currentLine.sortBy { it.l }
            lines.add(currentLine)
        }

        // Assembler les lignes avec des espaces, et des sauts de ligne pour les paragraphes
        val sb = StringBuilder()
        for ((index, line) in lines.withIndex()) {
            sb.append(line.joinToString(" ") { it.text })
            if (index < lines.size - 1) {
                // Si l'écart vertical est grand, on met un double saut de ligne
                val nextLineTop = lines[index + 1].minOf { it.t }
                val currentBottom = line.maxOf { it.b }
                if (nextLineTop - currentBottom > 0.015f) {
                    sb.append("\n\n")
                } else {
                    sb.append(" ")
                }
            }
        }
        return sb.toString().trim()
    }
}

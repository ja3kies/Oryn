package com.example.oryn.reader.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookInfoMatchTest {
    @Test fun acceptsSameTitleWithAccentAndCaseDifferences() {
        assertTrue(sameOpenLibraryTitle("Les Nuits blanches", "LES NUITS BLANCHES"))
        assertTrue(sameOpenLibraryTitle("L'Étranger", "L ETRANGER"))
    }

    @Test fun refusesPartialTitleHitsAndMissingMetadata() {
        assertFalse(sameOpenLibraryTitle("La peste", "La peste au Moyen Âge"))
        assertFalse(sameOpenLibraryTitle("Madame Bovary", "Madame Bovary: criticism"))
        assertFalse(sameOpenLibraryTitle("", "Madame Bovary"))
    }

    @Test fun authorSurnameMustMatchWholeWord() {
        assertTrue(sameOpenLibraryAuthor("Gustave Flaubert", "Flaubert, Gustave"))
        assertTrue(sameOpenLibraryAuthor("Camus, Albert", "Albert Camus"))
        assertFalse(sameOpenLibraryAuthor("Fedor Dostoïevski", "Charles Dickens"))
        assertFalse(sameOpenLibraryAuthor("Ana", "Anastasia"))
    }
}

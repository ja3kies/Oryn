package com.example.oryn.reader.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class BookInfoOverridesTest {
    private val remote = BookInfo(
        description = "Résumé du catalogue", descriptionSource = "Open Library", year = 1961,
        rating = 4.2f, ratingCount = 10
    )

    @Test fun manualDetailsWinButKeepVerifiedCatalogueRating() {
        val merged = remote.withOverrides(LocalBookInfoOverrides(description = "Ma description", year = 1992))
        assertEquals("Ma description", merged.description)
        assertEquals("Saisi dans Oryn", merged.descriptionSource)
        assertEquals(1992, merged.year)
        assertEquals(remote.rating, merged.rating)
        assertEquals(remote.ratingCount, merged.ratingCount)
    }

    @Test fun missingManualFieldsKeepSourceMetadata() {
        assertEquals(remote, remote.withOverrides(LocalBookInfoOverrides()))
        assertEquals(remote, remote.withOverrides(LocalBookInfoOverrides(description = "")))
    }

    @Test fun noCatalogueDataDoesNotProduceFakeValues() {
        val empty = BookInfo()
        val merged = empty.withOverrides(LocalBookInfoOverrides())
        assertEquals(null, merged.year)
        assertEquals(null, merged.description)
        assertEquals(null, merged.rating)
    }
}

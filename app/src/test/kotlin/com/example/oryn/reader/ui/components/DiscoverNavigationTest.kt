package com.example.oryn.reader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The import (+) remains a separate CTA; Settings remains outside the navbar. */
class DiscoverNavigationTest {
    @Test fun fourVisibleDestinationsHaveOneDiscoverAndKeepLibraryBookmarks() {
        assertEquals(
            listOf(ReaderTab.Home, ReaderTab.Explore, ReaderTab.Discover, ReaderTab.Favorites),
            visibleTabs
        )
        assertEquals(4, visibleTabs.map { it.route }.distinct().size)
        assertEquals("discover", ReaderTab.Discover.route)
        assertTrue(visibleTabs.none { it == ReaderTab.Settings })
    }
}

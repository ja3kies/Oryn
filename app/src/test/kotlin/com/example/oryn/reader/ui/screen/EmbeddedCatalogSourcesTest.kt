package com.example.oryn.reader.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/** Embedded catalogues share a single WebLibraryDialog entry point. */
class EmbeddedCatalogSourcesTest {
    @Test fun allCatalogueSourcesUseHttpsWithNoDuplicateHostCard() {
        val sources = recentSources + classicSources + externalIndexes
        assertTrue(sources.size >= 10)
        assertEquals(sources.size, sources.map { it.name }.distinct().size)
        sources.forEach { source ->
            val uri = URI(source.url)
            assertEquals("HTTPS expected for ${source.name}", "https", uri.scheme)
            assertTrue("Host missing: ${source.name}", !uri.host.isNullOrBlank())
            assertTrue("No credentials in catalog URL", uri.userInfo == null)
        }
    }

    @Test fun annasArchiveIsListedAsSeparateIndexWithTheSameEmbeddedEntryPoint() {
        val anna = externalIndexes.single { it.name == "Anna’s Archive" }
        assertEquals("https://annas-archive.gl", anna.url)
        assertTrue(recentSources.none { it.name == anna.name })
        assertTrue(classicSources.none { it.name == anna.name })
    }
}

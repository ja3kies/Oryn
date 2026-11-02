package com.example.oryn.reader.community

/** Release cannot supply simulated data, even if a saved debug UI selection is restored. */
object CommunityDevelopmentCatalog {
    const val available = false
    fun repository(scenario: CommunitySimulationScenario): CommunityCatalogRepository? = null
}

package com.example.oryn.reader.community.edge

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Source manifest contracts; device intent resolution still needs Android validation. */
class CommunityManifestPolicyTest {
    private val ns = "http://schemas.android.com/apk/res/android"
    private fun Element.attr(name: String) = getAttributeNS(ns, name)
    private fun Element.elements(tag: String) = getElementsByTagName(tag).let { nodes ->
        (0 until nodes.length).map { nodes.item(it) as Element }
    }
    private fun manifest(path: String): Element = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(File(path)).documentElement

    @Test fun localFileViewKeepsMimeTypesAndOnlyContentOrFileSchemes() {
        val main = manifest("src/main/AndroidManifest.xml").elements("activity").single { it.attr("name") == ".MainActivity" }
        val filter = main.elements("intent-filter").single { it.elements("action").any { it.attr("name") == "android.intent.action.VIEW" } }
        val data = filter.elements("data")
        assertEquals(setOf("content", "file"), data.map { it.attr("scheme") }.filter(String::isNotEmpty).toSet())
        assertEquals(setOf("application/epub+zip", "application/zip", "application/pdf", "text/plain"),
            data.map { it.attr("mimeType") }.filter(String::isNotEmpty).toSet())
        assertTrue(filter.elements("category").any { it.attr("name") == "android.intent.category.BROWSABLE" })
    }

    @Test fun baseManifestExposesNoAuthLinkUntilDebugConfigurationIsComplete() {
        val auth = manifest("src/main/AndroidManifest.xml").elements("activity")
            .single { it.attr("name") == ".community.auth.CommunityAuthCallbackActivity" }
        assertTrue(auth.elements("intent-filter").isEmpty())
        assertEquals("\${communityAuthEnabled}", auth.attr("enabled"))
        assertEquals("true", auth.attr("noHistory"))
        assertEquals("true", auth.attr("excludeFromRecents"))
    }

    @Test fun optionalDebugAuthManifestRequiresVerifiedHttpsAndExactCallbackPath() {
        val auth = manifest("src/isolatedAuth/AndroidManifest.xml").elements("activity").single()
        assertEquals(".community.auth.CommunityAuthCallbackActivity", auth.attr("name"))
        val filter = auth.elements("intent-filter").single()
        assertEquals("true", filter.attr("autoVerify"))
        assertEquals(setOf("android.intent.action.VIEW"), filter.elements("action").map { it.attr("name") }.toSet())
        val data = filter.elements("data").single()
        assertEquals("https", data.attr("scheme"))
        assertEquals("\${communityAuthRedirectHost}", data.attr("host"))
        assertEquals("/auth/callback", data.attr("path"))
        for (attribute in listOf("pathPrefix", "pathPattern", "port", "mimeType")) assertEquals("", data.attr(attribute))
    }
}

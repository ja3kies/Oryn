package com.example.oryn.reader.community.edge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Inclusion allowlists preserve the pre-existing cloud/transfer policy without redundant excludes. */
class CommunityBackupPolicyTest {
    private fun root(name: String): Element {
        val path = listOf(File("src/main/res/xml/$name"), File("app/src/main/res/xml/$name")).first { it.exists() }
        val factory = DocumentBuilderFactory.newInstance().apply { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return factory.newDocumentBuilder().parse(path).documentElement
    }
    private fun includes(element: Element): Set<Pair<String, String>> {
        val nodes = element.getElementsByTagName("include")
        return (0 until nodes.length).map { val node = nodes.item(it) as Element; node.getAttribute("domain") to node.getAttribute("path") }.toSet()
    }
    @Test fun cloudStillIncludesOnlyPreferencesAndDatastoreNotRoomOrBooks() {
        val expected = setOf("sharedpref" to ".", "file" to "datastore")
        assertEquals(expected, includes(root("backup_rules.xml")))
        val cloud = root("data_extraction_rules.xml").getElementsByTagName("cloud-backup").item(0) as Element
        assertEquals(expected, includes(cloud))
        assertFalse(includes(cloud).any { it.first == "database" || it.second in setOf("books", "covers", "community_auth") })
    }
    @Test fun existingDeviceTransferPolicyHasNotBeenBroadenedOrRemoved() {
        val transfer = root("data_extraction_rules.xml").getElementsByTagName("device-transfer").item(0) as Element
        assertEquals(setOf("sharedpref" to ".", "file" to "datastore", "database" to ".", "file" to "covers", "file" to "books"), includes(transfer))
    }
}

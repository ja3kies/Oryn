package com.example.oryn.reader.community.edge

import com.example.oryn.reader.community.CommunityBook
import com.example.oryn.reader.community.CommunityBookDetails
import com.example.oryn.reader.community.CommunityPreparation
import com.example.oryn.reader.community.CommunitySessionProvider
import com.example.oryn.reader.community.CommunityShareConsent
import com.example.oryn.reader.community.CommunityShareDraft
import com.example.oryn.reader.community.CommunityTransfers
import com.example.oryn.reader.community.PreparedCommunityDownload
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.security.MessageDigest
import java.util.UUID

internal val edgeBookId: UUID = UUID.fromString("10000000-0000-4000-8000-000000000001")
internal val edgeOwnerId: UUID = UUID.fromString("20000000-0000-4000-8000-000000000002")
internal const val edgeToken = "testheader.testpayload.testsignature"
internal const val freshEdgeToken = "freshheader.freshpayload.freshsignature"
internal const val edgeKey = "sb_publishable_edge_fixture_1234567890"
internal val edgeBytes = byteArrayOf(80, 75, 3, 4) + "test-only-byte-fixture-not-a-published-epub".toByteArray()
internal fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
internal fun downloadPlan(bytes: ByteArray = edgeBytes): PreparedCommunityDownload {
    val metadata = CommunityBookDetails(CommunityBook(edgeBookId, "test title", "test author", "fr"),
        sizeBytes = bytes.size.toLong(), sha256 = hash(bytes))
    return (CommunityTransfers.prepareDownload(metadata) as CommunityPreparation.Review).value
}
internal fun createInput(): CreateCommunityUploadInput {
    val draft = CommunityShareDraft(UUID.randomUUID(), "private-local-name.epub", 4096, "application/epub+zip")
    val share = (CommunityTransfers.prepareShare(draft, CommunityShareConsent(draft.selectionId, true, true)) as CommunityPreparation.Review).value
    return CreateCommunityUploadInput(share, "test title", "test author", "fr")
}
internal open class EdgeSession(var token: String? = edgeToken) : CommunitySessionProvider {
    var reads = 0
    var clears = 0
    override suspend fun accessToken(): String? { reads++; return token }
    override suspend fun isCurrent(accessToken: String) = token == accessToken
    override suspend fun clear() { clears++; token = null }
}
internal class EdgeTestFixture : AutoCloseable {
    private val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
    private val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
    val server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
    val client = HttpsCommunityEdgeHttp.hardenedClient().newBuilder()
        .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
    val origin = CommunityEdgeOrigin(server.url("/").toString(), edgeKey)
    val protocol = CommunityEdgeProtocol(HttpsCommunityEdgeHttp(origin, client)) { 1000L }
    val session = EdgeSession()
    val repository = CommunityEdgeOperationsCore(protocol, session)
    fun json(body: String, status: Int = 200) = MockResponse().setResponseCode(status)
        .setHeader("Content-Type", "application/json; charset=utf-8").setBody(body)
    fun finalized() = json("""{"bookId":"$edgeBookId","status":"pending","validated":true,"sha256":"${"a".repeat(64)}"}""")
    override fun close() { server.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}

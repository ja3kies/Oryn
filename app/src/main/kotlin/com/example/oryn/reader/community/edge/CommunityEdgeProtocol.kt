package com.example.oryn.reader.community.edge

import com.example.oryn.reader.community.MAX_COMMUNITY_EPUB_BYTES
import com.example.oryn.reader.community.PreparedCommunityDownload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

internal interface CommunityEdgeApi {
    suspend fun create(input: CreateCommunityUploadInput, token: CommunityUserToken): CommunityUploadTicket
    suspend fun finalize(bookId: UUID, token: CommunityUserToken): CommunityFinalization
    suspend fun retract(bookId: UUID, token: CommunityUserToken): CommunityRetraction
    suspend fun download(plan: PreparedCommunityDownload, token: CommunityUserToken): IntegrityCheckedCommunityEpub
    suspend fun moderate(bookId: UUID, decision: CommunityModerationDecision, token: CommunityUserToken): CommunityModeration
}

/** Protocol only, injected into the guarded production API or hermetic HTTP tests. */
internal class CommunityEdgeProtocol(
    private val http: CommunityEdgeHttp,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : CommunityEdgeApi {
    override suspend fun create(input: CreateCommunityUploadInput, token: CommunityUserToken): CommunityUploadTicket {
        val result = json(UserCommunityFunction.Create, token, JSONObject()
            .put("title", input.title).put("author", input.author).put("language", input.language).put("rightsConfirmed", true), setOf(201))
        return validate {
            val row = result.json
            val id = uuid(row.getString("bookId"))
            require(row.getString("status") == "pending" && row.getString("bucket") == "community-epubs")
            val path = row.getString("objectPath")
            val parts = path.split('/')
            require(parts.size == 2 && parts[0] == uuid(parts[0]).toString() && parts[1] == "$id.epub")
            val expiry = timestamp(row.getString("uploadExpiresAt"))
            require(expiry > nowMillis())
            val max = row.getLong("maxFileBytes")
            require(max in 1..MAX_COMMUNITY_EPUB_BYTES && input.share.draft.sizeBytes <= max)
            CommunityUploadTicket(id, path, expiry, max)
        }
    }

    override suspend fun finalize(bookId: UUID, token: CommunityUserToken): CommunityFinalization {
        requireCommunityBookId(bookId)
        val row = json(UserCommunityFunction.Finalize, token, idBody(bookId)).json
        return validate {
            require(uuid(row.getString("bookId")) == bookId && row.getString("status") == "pending")
            require(row.get("validated") == true)
            val hash = row.getString("sha256").also { require(it.matches(Regex("[a-f0-9]{64}"))) }
            CommunityFinalization(bookId, hash)
        }
    }

    override suspend fun retract(bookId: UUID, token: CommunityUserToken): CommunityRetraction {
        requireCommunityBookId(bookId)
        val response = json(UserCommunityFunction.Retract, token, idBody(bookId), setOf(200, 202))
        return validate {
            val row = response.json
            require(uuid(row.getString("bookId")) == bookId && row.getString("status") == "removed")
            val deleted = row.get("objectDeleted").also { require(it is Boolean) } as Boolean
            val pending = row.get("cleanupPending").also { require(it is Boolean) } as Boolean
            require(if (response.status == 200) deleted && !pending else !deleted && pending)
            CommunityRetraction(bookId, deleted, pending)
        }
    }

    override suspend fun moderate(bookId: UUID, decision: CommunityModerationDecision, token: CommunityUserToken): CommunityModeration {
        requireCommunityBookId(bookId)
        val row = json(UserCommunityFunction.Moderate, token, idBody(bookId)
            .put("decision", if (decision == CommunityModerationDecision.Publish) "publish" else "reject")).json
        return validate {
            require(uuid(row.getString("bookId")) == bookId)
            val published = if (row.isNull("publishedAt")) null else timestamp(row.getString("publishedAt"))
            if (decision == CommunityModerationDecision.Publish) require(row.getString("status") == "published" && published != null)
            else require(row.getString("status") == "rejected" && published == null)
            CommunityModeration(bookId, decision, published)
        }
    }

    override suspend fun download(plan: PreparedCommunityDownload, token: CommunityUserToken): IntegrityCheckedCommunityEpub {
        requireCommunityBookId(plan.id)
        requireDownloadMetadata(plan.sizeBytes, plan.sha256)
        val bytes = http.post(UserCommunityFunction.Download, token, idBody(plan.id).toString().toByteArray(),
            setOf(200), "application/epub+zip", plan.sizeBytes.toInt()).bytes
        var delivered = false
        return try { withContext(Dispatchers.Default) {
            try {
                require(bytes.size.toLong() == plan.sizeBytes && bytes.size >= 4)
                require(bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2] == 3.toByte() && bytes[3] == 4.toByte())
                val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
                val expected = plan.sha256.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                require(MessageDigest.isEqual(actual, expected))
                IntegrityCheckedCommunityEpub(plan.id, bytes)
            } catch (_: IllegalArgumentException) {
                throw InvalidCommunityEdgeResponse()
            }
        }.also { delivered = true } } finally { if (!delivered) bytes.fill(0) }
    }

    private class JsonResult(val status: Int, val json: JSONObject)
    private suspend fun json(function: UserCommunityFunction, token: CommunityUserToken, body: JSONObject,
        statuses: Set<Int> = setOf(200)): JsonResult {
        val response = http.post(function, token, body.toString().toByteArray(Charsets.UTF_8), statuses, "application/json", 32 * 1024)
        return validate {
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(response.bytes)).toString()
            JsonResult(response.status, JSONObject(text))
        }
    }
    private fun idBody(id: UUID) = JSONObject().put("bookId", id.toString())
    private fun uuid(raw: String): UUID = UUID.fromString(raw).also { requireCommunityBookId(it); require(raw.equals(it.toString(), true)) }
    private fun timestamp(raw: String): Long {
        // Supabase emits UTC Z or +00:00, with optional fractional microseconds. API 24 compatible.
        val match = Regex("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,6}))?(?:Z|\\+00:00)").matchEntire(raw)
            ?: throw IllegalArgumentException("Invalid timestamp")
        val normalized = match.groupValues[1] + "." + match.groupValues[2].padEnd(3, '0').take(3) + "+0000"
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT).apply {
            isLenient = false; timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = ParsePosition(0)
        val date = format.parse(normalized, position)
        require(date != null && position.index == normalized.length)
        return date.time
    }
    private fun <T> validate(block: () -> T): T = try { block() }
    catch (_: Exception) { throw InvalidCommunityEdgeResponse() }
}

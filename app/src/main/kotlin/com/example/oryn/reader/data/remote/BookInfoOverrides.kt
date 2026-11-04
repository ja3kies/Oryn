package com.example.oryn.reader.data.remote

import android.content.Context

/** Local overrides are stored outside Room to keep existing database migrations intact. */
data class LocalBookInfoOverrides(
    val description: String? = null,
    val year: Int? = null,
    val publisher: String? = null,
    val pages: Int? = null
)

object BookInfoOverrides {
    private const val PREFS = "oryn_book_info_overrides"
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, bookId: Long): LocalBookInfoOverrides {
        val store = prefs(context)
        return LocalBookInfoOverrides(
            description = store.getString("description_$bookId", null),
            year = store.getInt("year_$bookId", 0).takeIf { it in 1..2100 },
            publisher = store.getString("publisher_$bookId", null),
            pages = store.getInt("pages_$bookId", 0).takeIf { it in 1..200_000 }
        )
    }

    fun save(
        context: Context, bookId: Long, description: String, year: Int?,
        publisher: String, pages: Int?
    ) {
        prefs(context).edit().apply {
            if (description.isBlank()) remove("description_$bookId")
            else putString("description_$bookId", description.trim().take(12_000))
            if (year == null) remove("year_$bookId")
            else putInt("year_$bookId", year)
            if (publisher.isBlank()) remove("publisher_$bookId")
            else putString("publisher_$bookId", publisher.trim().take(160))
            if (pages == null) remove("pages_$bookId")
            else putInt("pages_$bookId", pages)
        }.apply()
    }
}

/** Never mistake missing catalogue metadata for fabricated facts. */
internal fun BookInfo.withOverrides(overrides: LocalBookInfoOverrides): BookInfo =
    copy(
        description = overrides.description?.takeIf { it.isNotBlank() } ?: description,
        descriptionSource = if (overrides.description.isNullOrBlank()) descriptionSource else "Saisi dans Oryn",
        year = overrides.year ?: year,
        publisher = overrides.publisher?.takeIf { it.isNotBlank() } ?: publisher,
        pages = overrides.pages ?: pages
    )

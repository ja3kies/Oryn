package com.example.oryn.reader.ui.components

/** Reader chrome only; document position, selection and settings never belong to this state. */
enum class ReaderControlsState { HIDDEN, COMPACT, EXPANDED }
enum class ReaderControlsEvent { SURFACE_TAP, EXPAND, DISMISS_SHEET, BACK, SHOW_COMPACT, HIDE }

fun ReaderControlsState.transition(event: ReaderControlsEvent): ReaderControlsState = when (event) {
    ReaderControlsEvent.SURFACE_TAP -> when (this) {
        ReaderControlsState.HIDDEN, ReaderControlsState.EXPANDED -> ReaderControlsState.COMPACT
        ReaderControlsState.COMPACT -> ReaderControlsState.HIDDEN
    }
    ReaderControlsEvent.EXPAND -> if (this == ReaderControlsState.COMPACT) ReaderControlsState.EXPANDED else this
    ReaderControlsEvent.DISMISS_SHEET -> if (this == ReaderControlsState.EXPANDED) ReaderControlsState.COMPACT else this
    ReaderControlsEvent.BACK -> when (this) {
        ReaderControlsState.EXPANDED -> ReaderControlsState.COMPACT
        ReaderControlsState.COMPACT, ReaderControlsState.HIDDEN -> ReaderControlsState.HIDDEN
    }
    ReaderControlsEvent.SHOW_COMPACT -> ReaderControlsState.COMPACT
    ReaderControlsEvent.HIDE -> ReaderControlsState.HIDDEN
}

/** Only Back in HIDDEN is allowed to leave the document. */
val ReaderControlsState.backLeavesReader: Boolean get() = this == ReaderControlsState.HIDDEN

/** Only bounded, real document offsets produce chapter progress. No reading-speed assumptions. */
data class ReaderChapterProgress(val title: String, val fraction: Float, val remainingPages: Int?)
fun readerChapterProgress(
    titles: List<Pair<Int, String>>, position: Int, documentLength: Int, pdfStride: Int? = null
): ReaderChapterProgress? {
    if (documentLength <= 0 || position < 0 || position >= documentLength || titles.isEmpty()) return null
    val ordered = titles.filter { it.first in 0 until documentLength }
        .sortedBy { it.first }
        .distinctBy { it.first }
    val index = ordered.indexOfLast { it.first <= position }
    if (index < 0) return null
    val start = ordered[index].first
    val end = ordered.getOrNull(index + 1)?.first ?: documentLength
    if (end <= start) return null
    val fraction = ((position - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    val remaining = pdfStride?.takeIf { it > 0 }?.let { ((end - 1) / it - position / it).coerceAtLeast(0) }
    return ReaderChapterProgress(ordered[index].second, fraction, remaining)
}

/** Remaining pages after the visible page, from the actual current pagination, never text length. */
fun readerChapterPagesAfter(pageStarts: IntArray, page: Int, chapterOffsets: List<Int>, documentLength: Int): Int? {
    val start = pageStarts.getOrNull(page) ?: return null
    if (start !in 0 until documentLength) return null
    val chapters = chapterOffsets.filter { it in 0 until documentLength }.sorted()
    if (chapters.none { it <= start }) return null
    val end = chapters.firstOrNull { it > start } ?: documentLength
    // A malformed pagination snapshot must not produce a misleading page estimate.
    if (pageStarts.isEmpty() || pageStarts[0] < 0 ||
        pageStarts.last() >= documentLength ||
        pageStarts.indices.drop(1).any { pageStarts[it] <= pageStarts[it - 1] }
    ) return null
    val last = pageStarts.binarySearch(end - 1).let { if (it >= 0) it else -it - 2 }
    return (last - page).coerceAtLeast(0)
}

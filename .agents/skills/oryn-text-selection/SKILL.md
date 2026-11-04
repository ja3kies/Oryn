---
name: oryn-text-selection
description: Audit or modify EPUB/PDF text selection, long press, annotation handles, copy, highlight, text navigation, and their Compose gesture interactions in Oryn.
---

# Native-feeling text selection

1. Inspect `ReaderPager.kt`, `ReaderAnnotations.kt`, `ReaderPosition.kt`, `ReaderScreen.kt` and the current Compose BOM/version. Follow gesture ownership from page curl / VerticalPager through `pointerInput`, `awaitLongPressOrCancellation`, `detectTapGestures`, coordinate conversion, selection handles and `SelectionBar`.
2. For simple non-annotated static text, prefer Compose's `SelectionContainer`; for the reader, do **not** blindly replace Oryn's custom selection. It represents document-global offsets, interacts with page curl and supports persistent highlights, notes, lookup and jumps.
3. The latest Android Compose foundation may expose `rememberSelectionState` / `SelectionContainer(state=...)`. Check the **actual** BOM and APIs in Oryn before using them. Do not break persistence or back navigation to chase novelty.
4. Review pointer cancellation, drag positions and RTL/bidi text, Unicode grapheme boundaries, CJK/Thai segmentation, line-wrap selection, scroll/pager conflict, handles near display edges, TalkBack and 48dp hit targets. Do not assume Latin `getWordBoundary` works for CJK; test it on supported hardware.
5. Verify selection actions: copy uses exact text, highlight/note link to immutable original offsets, undo/clear works, scrolling does not trigger an unintended page turn, and dialog/toolbars never cover their own handles. Test with long paragraphs, repeated characters, emoji, ligatures, mixed scripts, small windows, max font scale.
6. Avoid using `SelectionContainer` around a lazy list without checking item composition limits; selected offscreen elements may not exist. Check custom paging separately.
7. Every bug fix gets a JVM test for pure offset calculations and an Android instrumented test when gesture/rendering is involved. Compilation of instrumented tests is **not** physical execution.
8. Produce a `report` containing root cause, change, tests run, tests blocked, risks and rollback SHA. Do not mark text selection verified until a real device/emulator test covers dragging.

Primary references:
- https://developer.android.com/develop/ui/compose/text/user-interactions
- https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html
- https://www.reddit.com/r/androiddev/comments/1vmzy3p/composes_worditerator_makes_a_long_press_select/
- https://www.youtube.com/watch?v=NRB2j6To0SI

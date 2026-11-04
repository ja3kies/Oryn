package com.example.oryn.reader.data.importer

import org.junit.Test

class PdfReflowLimitsTest {
    @Test fun exactBudgetIsAcceptedWithoutAllocatingAFullDocument() {
        PdfReflowLimits.checkGrowth(PdfReflowLimits.MAX_CHARACTERS - 10, 10, PdfReflowLimits.MAX_SPANS - 2, 2)
    }
    @Test(expected = java.io.IOException::class) fun excessiveTextStopsBeforeAllocation() {
        PdfReflowLimits.checkGrowth(PdfReflowLimits.MAX_CHARACTERS, 1, 0, 0)
    }
    @Test(expected = java.io.IOException::class) fun excessiveSpanCountStopsBeforeAllocation() {
        PdfReflowLimits.checkGrowth(1, 1, PdfReflowLimits.MAX_SPANS, 1)
    }
    @Test(expected = java.io.IOException::class) fun integerOverflowCannotBypassTheBudget() {
        PdfReflowLimits.checkGrowth(Int.MAX_VALUE, Int.MAX_VALUE, 0, 0)
    }
}

package ru.dikoveriya.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Count conservation checks: only one explicitly selected material unit at a time. */
class LabOperationBoundaryTest {
    @Test fun splitPreservesSourceCountWhenOutputsMultiply() {
        // New shoots are an output measurement, not extra source material.
        assertEquals(2, LabOperationRules.remainingAfterSplit(10, listOf(3, 4), listOf(30, 40), 1))
    }

    @Test fun splitRejectsExcessAcrossMultipleDestinations() {
        assertThrows(IllegalArgumentException::class.java) {
            LabOperationRules.remainingAfterSplit(10, listOf(6, 5), listOf(6, 5), 0)
        }
    }

    @Test fun splitRejectsIntegerOverflowAttempt() {
        assertThrows(IllegalArgumentException::class.java) {
            LabOperationRules.remainingAfterSplit(1_000_000, listOf(1_000_000), listOf(1), Int.MAX_VALUE)
        }
    }

    @Test fun mergeKeepsIndependentSourceRemainders() {
        assertEquals(listOf(0, 7, 1), LabOperationRules.remainingAfterMerge(
            listOf(5, 10, 2), listOf(5, 3, 1), 12))
    }

    @Test fun mergeRejectsZeroOrOverdrawnSources() {
        assertThrows(IllegalArgumentException::class.java) {
            LabOperationRules.remainingAfterMerge(listOf(3, 4), listOf(0, 2), 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LabOperationRules.remainingAfterMerge(listOf(3, 4), listOf(3, 5), 2)
        }
    }

    @Test fun fullLossIsAllowedButZeroLossIsNot() {
        assertEquals(0, LabOperationRules.remainingAfterLoss(7, 7))
        assertThrows(IllegalArgumentException::class.java) {
            LabOperationRules.remainingAfterLoss(7, 0)
        }
    }

    @Test fun splitRejectsMoreThanTwentyFiveDestinations() {
        assertThrows(IllegalArgumentException::class.java) {
            LabOperationRules.remainingAfterSplit(100, List(26) { 1 }, List(26) { 1 }, 0)
        }
    }
}

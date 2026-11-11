package ru.dikoveriya.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LabOperationRulesTest {
    @Test fun splitWithLoss() {
        assertEquals(1,LabOperationRules.remainingAfterSplit(10,listOf(3,4),listOf(8,9),2))
    }
    @Test fun splitEntireSource() {
        assertEquals(0,LabOperationRules.remainingAfterSplit(10,listOf(10),listOf(20),0))
    }
    @Test fun splitAboveAvailableFails() {
        assertThrows(IllegalArgumentException::class.java) {
            LabOperationRules.remainingAfterSplit(5,listOf(3,3),listOf(6,5),0)
        }
    }
    @Test fun splitNegativeLossFails() {
        assertThrows(IllegalArgumentException::class.java) {
            LabOperationRules.remainingAfterSplit(5,listOf(3),listOf(6),-1)
        }
    }
    @Test fun mergeKeepsRemainders() {
        assertEquals(listOf(5,1),LabOperationRules.remainingAfterMerge(listOf(10,20),listOf(5,19),7))
    }
    @Test fun mergeAboveSourceFails() {
        assertThrows(IllegalArgumentException::class.java) {
            LabOperationRules.remainingAfterMerge(listOf(10,5),listOf(7,6),3)
        }
    }
    @Test fun partialLoss() {
        assertEquals(7,LabOperationRules.remainingAfterLoss(10,3))
    }
    @Test fun lossLargerThanAvailableFails() {
        assertThrows(IllegalArgumentException::class.java) {
            LabOperationRules.remainingAfterLoss(10,11)
        }
    }
}

package ru.dikoveriya.lab

import org.junit.Assert.*
import org.junit.Test

class Lab07NavigationTest {
    @Test fun collectionVarietyContainerAndFourRoutesPopExactlyOneLevel() {
        var stack=emptyList<String>()
        stack=Lab07Navigation.enter(stack,Lab07Navigation.COLLECTION)
        stack=Lab07Navigation.enter(stack,Lab07Navigation.variety(7))
        stack=Lab07Navigation.enter(stack,Lab07Navigation.container(91))
        for(screen in listOf(
            Lab07Navigation.growth(91),Lab07Navigation.medium(91),
            Lab07Navigation.operations(91),Lab07Navigation.history(91)
        )) {
            val deeper=Lab07Navigation.enter(stack,screen)
            assertEquals(screen,Lab07Navigation.current(deeper))
            assertEquals(Lab07Navigation.container(91),
                Lab07Navigation.current(Lab07Navigation.back(deeper)))
            assertEquals(stack,Lab07Navigation.back(deeper))
        }
        stack=Lab07Navigation.back(stack)
        assertEquals(Lab07Navigation.variety(7),Lab07Navigation.current(stack))
        stack=Lab07Navigation.back(stack)
        assertEquals(Lab07Navigation.COLLECTION,Lab07Navigation.current(stack))
        stack=Lab07Navigation.back(stack)
        assertTrue(stack.isEmpty())
    }

    @Test fun bottomTabCollectionIsRootNotDuplicated() {
        val stack=Lab07Navigation.enter(emptyList(),Lab07Navigation.variety(2))
        assertEquals(emptyList<String>(),Lab07Navigation.back(stack))
        assertEquals(listOf(Lab07Navigation.variety(2)),stack)
    }

    @Test fun noDuplicateRouteWhenOpeningSameScreenTwice() {
        val old=listOf(Lab07Navigation.COLLECTION,Lab07Navigation.variety(17))
        assertEquals(old,Lab07Navigation.enter(old,Lab07Navigation.variety(17)))
    }

    @Test fun rejectInvalidRoutesAndMissingBiologyIds() {
        assertFalse(Lab07Navigation.isKnown("container:0"))
        assertFalse(Lab07Navigation.isKnown("container:-1"))
        assertFalse(Lab07Navigation.isKnown("foo:99"))
        assertFalse(Lab07Navigation.isKnown("growth:"))
        assertTrue(Lab07Navigation.isKnown("medium:19"))
        try {
            Lab07Navigation.enter(emptyList(),"foobar")
            fail("Invalid route accepted")
        } catch(expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Unknown screen"))
        }
    }

    @Test fun parentForHistoryAndContainerIsStable() {
        assertEquals(Lab07Navigation.container(11),Lab07Navigation.parentFor("history:11",4))
        assertEquals(Lab07Navigation.variety(4),Lab07Navigation.parentFor("container:11",4))
        assertEquals(Lab07Navigation.COLLECTION,Lab07Navigation.parentFor("variety:4",4))
        assertNull(Lab07Navigation.parentFor(Lab07Navigation.COLLECTION,null))
    }

    @Test fun fourHomeCardsPushOneRouteAndBackReturnsExactlyToOverview(){
        for(page in listOf(
            Lab07Navigation.work("vitro"),
            Lab07Navigation.work("attention"),
            Lab07Navigation.work("growth"),
            Lab07Navigation.TASKS
        )){
            val stack=Lab07Navigation.enter(emptyList(),page)
            assertEquals(page,Lab07Navigation.current(stack))
            assertEquals(emptyList<String>(),Lab07Navigation.back(stack))
        }
    }
    @Test fun groupsAndPlantWorklistsRemainOneLevelDeep(){
        val stack=Lab07Navigation.enter(
            Lab07Navigation.enter(emptyList(),Lab07Navigation.GROUPS),
            Lab07Navigation.groupWork(17))
        assertEquals(Lab07Navigation.GROUPS,Lab07Navigation.current(Lab07Navigation.back(stack)))
        assertTrue(Lab07Navigation.isKnown(Lab07Navigation.plantWork(27)))
        assertFalse(Lab07Navigation.isKnown("work:plant:0"))
        assertTrue(Lab07Navigation.isKnown(Lab07Navigation.PLACES))
        assertFalse(Lab07Navigation.isKnown("work:unknown"))
    }
}

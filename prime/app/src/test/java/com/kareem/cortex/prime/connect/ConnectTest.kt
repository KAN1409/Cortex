package com.kareem.cortex.prime.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectTest {
    private val docs = listOf(
        Doc(1, "Kids summer plan: swimming lessons in July", 1),
        Doc(2, "لازم أحجز تمرين السباحة للولاد في يوليو", 2),
        Doc(3, "summer plan for the kids — camp or swimming?", 3),
        Doc(4, "Amazon order shipped, return window closes Thursday", 4),
        Doc(5, "the kids summer swimming camp costs 2000", 5),
        Doc(6, "car licence renewal documents", 6),
    )

    @Test fun searchFindsEnglishAndArabicTerms() {
        val idx = MemoryIndex(docs)
        assertEquals(listOf(4L), idx.search("amazon return").map { it.id }.take(1))
        assertTrue(idx.search("سباحة").any { it.id == 2L })
        assertTrue(idx.search("swimming kids").take(3).map { it.id }.containsAll(listOf(1L, 3L, 5L)))
        assertTrue(idx.search("zzzz nothing").isEmpty())
    }

    @Test fun normalisedArabicMatchesVariants() {
        val idx = MemoryIndex(listOf(Doc(9, "رخصة العربية", 1)))
        assertEquals(9L, idx.search("رخصه").single().id)
    }

    @Test fun ideasClusterWhenThreeOrMoreRelate() {
        val clusters = IdeaClusters.build(docs)
        val summer = clusters.single { c -> c.memberIds.containsAll(listOf(1L, 3L, 5L)) }
        assertTrue(summer.memberIds.size >= 3)
        assertTrue(summer.title.contains("summer", ignoreCase = true) || summer.title.contains("kids", ignoreCase = true))
        assertTrue(clusters.none { 4L in it.memberIds && it.memberIds.size >= 3 })
    }
}

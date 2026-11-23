package com.kareem.cortex.prime.ui.now

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {
    @Test fun greetingByHour() {
        assertEquals("Good morning, Kareem", greeting(9, "Kareem"))
        assertEquals("Good evening", greeting(22, null))
        assertEquals("Good afternoon", greeting(13, " "))
        assertEquals("Good evening", greeting(3, null))
    }
}

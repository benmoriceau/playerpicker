package com.example.chwazi2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerTest {

    @Test
    fun `test finger creation`() {
        val currentTime = System.currentTimeMillis()
        val finger = Finger(10f, 20f, 0xFF0000, currentTime)
        
        assertEquals(10f, finger.x, 0f)
        assertEquals(20f, finger.y, 0f)
        assertEquals(0xFF0000, finger.color)
        assertEquals(currentTime, finger.startTime)
        assertNull(finger.groupColor)
    }

    @Test
    fun `test finger defaults`() {
        // We can't easily test default startTime since it uses System.currentTimeMillis() internally in the default parameter
        // but we can verify other properties
        val finger = Finger(10f, 20f, 0xFF0000)
        assertNull(finger.groupColor)
        
        // Check that startTime is recent
        val now = System.currentTimeMillis()
        assertTrue(now - finger.startTime < 1000)
    }

    @Test
    fun `test update properties`() {
        val finger = Finger(10f, 20f, 0xFF0000)
        finger.x = 50f
        finger.y = 60f
        finger.color = 0x00FF00
        finger.groupColor = 0x0000FF
        
        assertEquals(50f, finger.x, 0f)
        assertEquals(60f, finger.y, 0f)
        assertEquals(0x00FF00, finger.color)
        assertEquals(0x0000FF, finger.groupColor)
    }
}

package com.example.pick_a_player

import org.junit.Assert.assertEquals
import org.junit.Test

class GameModeTest {

    @Test
    fun `verify game mode display names`() {
        assertEquals("Starting Player", GameMode.STARTING_PLAYER.displayName)
        assertEquals("Group", GameMode.GROUP.displayName)
    }

    @Test
    fun `verify game mode values`() {
        val modes = GameMode.values()
        assertEquals(2, modes.size)
        assertEquals(GameMode.STARTING_PLAYER, modes[0])
        assertEquals(GameMode.GROUP, modes[1])
    }
}

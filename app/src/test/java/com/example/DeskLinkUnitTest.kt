package com.example

import com.example.domain.PcProfile
import com.example.domain.PowerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeskLinkUnitTest {

    @Test
    fun `pc profile normalizes scheme and trailing slashes`() {
        val lan = PcProfile(
            id = "lan",
            label = "PC",
            host = "http://192.168.1.50/",
            port = 8765,
            pin = "123456",
            useHttps = false,
        )
        assertEquals("http://192.168.1.50:8765", lan.baseUrl)

        val remote = PcProfile(
            id = "remote",
            label = "Remote PC",
            host = "https://pc.example.ts.net/",
            port = 8443,
            pin = "123456",
            useHttps = true,
        )
        assertEquals("https://pc.example.ts.net:8443", remote.baseUrl)
    }

    @Test
    fun `destructive power actions require confirmation`() {
        assertTrue(PowerAction.SHUTDOWN.requiresConfirmation)
        assertTrue(PowerAction.RESTART.requiresConfirmation)
        assertTrue(PowerAction.SLEEP.requiresConfirmation)
        assertFalse(PowerAction.LOCK.requiresConfirmation)
        assertFalse(PowerAction.ABORT.requiresConfirmation)
    }
}

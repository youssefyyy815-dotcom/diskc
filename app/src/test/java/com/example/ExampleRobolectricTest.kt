package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DeskLinkPreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches DeskLink`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DeskLink", appName)
    }

    @Test
    fun `bundled python server script loads from assets`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = DeskLinkPreferencesRepository(context)
        val script = repo.loadBundledServerScript()
        assertTrue(script.contains("ThreadingHTTPServer"))
        assertTrue(script.contains("/api/power/"))
        assertTrue(script.contains("LockWorkStation"))
    }
}

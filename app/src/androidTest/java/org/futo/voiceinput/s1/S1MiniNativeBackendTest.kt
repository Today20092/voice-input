package org.futo.voiceinput.s1

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class S1MiniNativeBackendTest {
    @Test
    fun packagedCpuBackendIsDiscoverableFromAndroidNativeLibraryDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backends = S1MiniNative.availableBackends(context.applicationInfo.nativeLibraryDir)

        assertTrue("Expected a CPU backend, found $backends", backends.any { it.startsWith("cpu:") })
    }
}

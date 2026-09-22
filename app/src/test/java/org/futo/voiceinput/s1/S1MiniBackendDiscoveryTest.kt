package org.futo.voiceinput.s1

import org.junit.Assert.assertEquals
import org.junit.Test

class S1MiniBackendDiscoveryTest {
    @Test
    fun separatesDevicesFromNativeLoaderErrors() {
        val discovery = S1MiniBackendDiscovery.fromNativeEntries(
            listOf(
                "cpu:ARMv8 CPU",
                "loader_error:failed to load libggml-cpu.so: dependency missing"
            )
        )

        assertEquals(listOf("cpu:ARMv8 CPU"), discovery.devices)
        assertEquals(
            listOf("failed to load libggml-cpu.so: dependency missing"),
            discovery.loaderErrors
        )
    }
}

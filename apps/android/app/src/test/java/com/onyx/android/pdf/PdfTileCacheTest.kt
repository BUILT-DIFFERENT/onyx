package com.onyx.android.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PdfTileCacheTest {
    @Test
    fun `resolvePdfTileCacheSizeBytes switches to low-ram tier`() {
        assertEquals(
            32 * 1024 * 1024,
            resolvePdfTileCacheSizeBytes(isLowRamDevice = true, memoryClassMb = 512),
        )
        assertEquals(
            32 * 1024 * 1024,
            resolvePdfTileCacheSizeBytes(isLowRamDevice = false, memoryClassMb = 128),
        )
        assertEquals(
            64 * 1024 * 1024,
            resolvePdfTileCacheSizeBytes(isLowRamDevice = false, memoryClassMb = 256),
        )
    }

    @Test
    fun `constructor rejects non-positive tile size`() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfTileCache(
                maxSizeBytes = 1024 * 1024,
                tileSizePx = 0,
            )
        }
    }
}

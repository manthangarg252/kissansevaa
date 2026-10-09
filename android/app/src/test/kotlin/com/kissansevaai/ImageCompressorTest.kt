package com.kissansevaai

import com.kissansevaai.core.util.ImageCompressor
import org.junit.Assert.*
import org.junit.Test

class ImageCompressorTest {

    @Test
    fun `MAX_LONG_EDGE is 1280 and JPEG_QUALITY is 80`() {
        assertEquals(1280, ImageCompressor.MAX_LONG_EDGE)
        assertEquals(80, ImageCompressor.JPEG_QUALITY)
    }
}

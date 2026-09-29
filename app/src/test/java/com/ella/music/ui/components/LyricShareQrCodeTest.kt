package com.ella.music.ui.components

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LyricShareQrCodeTest {
    @Test fun encodesSongLinkAsSquareMatrixWithFinderPatterns() {
        val url = "https://y.music.163.com/m/song?id=2155423467"
        val matrix = assertNotNull(encodeShareQrCode(url)).let { encodeShareQrCode(url)!! }
        val size = matrix.size
        assertTrue(size >= 21 && (size - 17) % 4 == 0)
        assertTrue(matrix.all { it.size == size })
        // Top-left finder pattern: dark ring with dark 3x3 centre.
        assertTrue(matrix[0][0] && matrix[0][6] && matrix[6][0] && matrix[6][6] && matrix[3][3])
        assertTrue(!matrix[1][1] && !matrix[5][5])
        // Dump for external decoding checks (build output only).
        File("build/tmp").mkdirs()
        File("build/tmp/qr_matrix.txt").writeText(url + "\n" + matrix.joinToString("\n") { row -> row.joinToString("") { if (it) "1" else "0" } })
    }
}

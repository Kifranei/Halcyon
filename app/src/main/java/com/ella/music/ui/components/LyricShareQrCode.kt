package com.ella.music.ui.components

import kotlin.math.abs

/**
 * Minimal QR Code encoder used by the NetEase-style lyric card (no external dependency).
 *
 * Scope is intentionally narrow: byte mode, error-correction level M, versions 1..6
 * (up to 106 bytes of payload), which comfortably covers short song links. Versions 1..6
 * need neither version-information blocks nor more than one alignment pattern. The
 * algorithm follows the reference structure of Project Nayuki's QR Code generator.
 *
 * Returns `modules[y][x]` (true = dark), or null when the payload does not fit.
 */
internal fun encodeShareQrCode(text: String): Array<BooleanArray>? {
    val payload = text.toByteArray(Charsets.UTF_8)
    val version = (QR_MIN_VERSION..QR_MAX_VERSION).firstOrNull { ver ->
        4 + 8 + payload.size * 8 <= qrNumDataCodewords(ver) * 8
    } ?: return null
    return runCatching { QrMatrix(version).apply { build(payload) }.modules }.getOrNull()
}

private const val QR_MIN_VERSION = 1
private const val QR_MAX_VERSION = 6

// Index = version (0 unused). Error-correction level M only.
private val QR_ECC_CODEWORDS_PER_BLOCK_M = intArrayOf(-1, 10, 16, 26, 18, 24, 16)
private val QR_NUM_ECC_BLOCKS_M = intArrayOf(-1, 1, 1, 1, 2, 2, 4)
private const val QR_ECC_FORMAT_BITS_M = 0

private fun qrNumRawDataModules(ver: Int): Int {
    var result = (16 * ver + 128) * ver + 64
    if (ver >= 2) {
        val numAlign = ver / 7 + 2
        result -= (25 * numAlign - 10) * numAlign - 55
        if (ver >= 7) result -= 36
    }
    return result
}

private fun qrNumDataCodewords(ver: Int): Int =
    qrNumRawDataModules(ver) / 8 - QR_ECC_CODEWORDS_PER_BLOCK_M[ver] * QR_NUM_ECC_BLOCKS_M[ver]

private class QrMatrix(private val version: Int) {
    val size = version * 4 + 17
    val modules = Array(size) { BooleanArray(size) }
    private val isFunction = Array(size) { BooleanArray(size) }

    fun build(payload: ByteArray) {
        drawFunctionPatterns()
        val codewords = addEccAndInterleave(encodeData(payload))
        drawCodewords(codewords)
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        for (mask in 0 until 8) {
            applyMask(mask)
            drawFormatBits(mask)
            val penalty = penaltyScore()
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
            }
            applyMask(mask) // XOR again to undo.
        }
        applyMask(bestMask)
        drawFormatBits(bestMask)
    }

    private fun encodeData(payload: ByteArray): ByteArray {
        val capacityBits = qrNumDataCodewords(version) * 8
        val bits = ArrayList<Boolean>(capacityBits)
        fun append(value: Int, length: Int) {
            for (i in length - 1 downTo 0) bits += ((value ushr i) and 1) != 0
        }
        append(0x4, 4) // Byte mode.
        append(payload.size, 8) // Character count (8 bits for versions 1..9 in byte mode).
        payload.forEach { append(it.toInt() and 0xFF, 8) }
        append(0, minOf(4, capacityBits - bits.size)) // Terminator.
        append(0, (8 - bits.size % 8) % 8)
        var pad = 0xEC
        while (bits.size < capacityBits) {
            append(pad, 8)
            pad = pad xor 0xEC xor 0x11
        }
        val result = ByteArray(bits.size / 8)
        bits.forEachIndexed { index, bit ->
            if (bit) {
                result[index ushr 3] = (result[index ushr 3].toInt() or (1 shl (7 - (index and 7)))).toByte()
            }
        }
        return result
    }

    private fun addEccAndInterleave(data: ByteArray): ByteArray {
        val numBlocks = QR_NUM_ECC_BLOCKS_M[version]
        val blockEccLen = QR_ECC_CODEWORDS_PER_BLOCK_M[version]
        val rawCodewords = qrNumRawDataModules(version) / 8
        val numShortBlocks = numBlocks - rawCodewords % numBlocks
        val shortBlockLen = rawCodewords / numBlocks
        val divisor = reedSolomonDivisor(blockEccLen)
        val blocks = ArrayList<ByteArray>(numBlocks)
        var k = 0
        for (i in 0 until numBlocks) {
            val dataLen = shortBlockLen - blockEccLen + if (i < numShortBlocks) 0 else 1
            val dat = data.copyOfRange(k, k + dataLen)
            k += dataLen
            val block = dat.copyOf(shortBlockLen + 1)
            val ecc = reedSolomonRemainder(dat, divisor)
            System.arraycopy(ecc, 0, block, block.size - blockEccLen, ecc.size)
            blocks += block
        }
        val result = ByteArray(rawCodewords)
        var index = 0
        for (i in 0 until blocks[0].size) {
            for (j in blocks.indices) {
                // Short blocks carry one padding byte that is skipped here.
                if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
                    result[index++] = blocks[j][i]
                }
            }
        }
        return result
    }

    private fun setFunction(x: Int, y: Int, dark: Boolean) {
        modules[y][x] = dark
        isFunction[y][x] = true
    }

    private fun drawFunctionPatterns() {
        for (i in 0 until size) {
            setFunction(6, i, i % 2 == 0)
            setFunction(i, 6, i % 2 == 0)
        }
        drawFinder(3, 3)
        drawFinder(size - 4, 3)
        drawFinder(3, size - 4)
        if (version >= 2) {
            // Versions 2..6 have alignment centres at {6, size - 7}; only the bottom-right
            // combination does not collide with a finder pattern.
            drawAlignment(size - 7, size - 7)
        }
        drawFormatBits(0) // Reserve the format areas; overwritten after masking.
    }

    private fun drawFinder(cx: Int, cy: Int) {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val dist = maxOf(abs(dx), abs(dy))
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until size && y in 0 until size) {
                    setFunction(x, y, dist != 2 && dist != 4)
                }
            }
        }
    }

    private fun drawAlignment(cx: Int, cy: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                setFunction(cx + dx, cy + dy, maxOf(abs(dx), abs(dy)) != 1)
            }
        }
    }

    private fun drawFormatBits(mask: Int) {
        val data = (QR_ECC_FORMAT_BITS_M shl 3) or mask
        var rem = data
        repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
        val bits = ((data shl 10) or rem) xor 0x5412
        fun bit(i: Int) = ((bits ushr i) and 1) != 0
        for (i in 0..5) setFunction(8, i, bit(i))
        setFunction(8, 7, bit(6))
        setFunction(8, 8, bit(7))
        setFunction(7, 8, bit(8))
        for (i in 9 until 15) setFunction(14 - i, 8, bit(i))
        for (i in 0 until 8) setFunction(size - 1 - i, 8, bit(i))
        for (i in 8 until 15) setFunction(8, size - 15 + i, bit(i))
        setFunction(8, size - 8, true) // Always-dark module.
    }

    private fun drawCodewords(data: ByteArray) {
        var i = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                for (j in 0 until 2) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!isFunction[y][x] && i < data.size * 8) {
                        modules[y][x] = ((data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) != 0
                        i++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask(mask: Int) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert && !isFunction[y][x]) modules[y][x] = !modules[y][x]
            }
        }
    }

    private fun penaltyScore(): Int {
        var penalty = 0
        fun module(x: Int, y: Int, horizontal: Boolean) =
            if (horizontal) modules[y][x] else modules[x][y]
        for (horizontal in booleanArrayOf(true, false)) {
            for (a in 0 until size) {
                var runColor = module(0, a, horizontal)
                var runLength = 1
                for (b in 1 until size) {
                    val color = module(b, a, horizontal)
                    if (color == runColor) {
                        runLength++
                    } else {
                        if (runLength >= 5) penalty += 3 + (runLength - 5)
                        runColor = color
                        runLength = 1
                    }
                }
                if (runLength >= 5) penalty += 3 + (runLength - 5)
                // Finder-like 1:1:3:1:1 pattern with four light modules on one side.
                for (b in 0..size - QR_FINDER_LIKE.size) {
                    var forward = true
                    var backward = true
                    for (k in QR_FINDER_LIKE.indices) {
                        val color = module(b + k, a, horizontal)
                        if (color != QR_FINDER_LIKE[k]) forward = false
                        if (color != QR_FINDER_LIKE[QR_FINDER_LIKE.size - 1 - k]) backward = false
                        if (!forward && !backward) break
                    }
                    if (forward) penalty += 40
                    if (backward) penalty += 40
                }
            }
        }
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val color = modules[y][x]
                if (color == modules[y][x + 1] && color == modules[y + 1][x] && color == modules[y + 1][x + 1]) {
                    penalty += 3
                }
            }
        }
        val dark = modules.sumOf { row -> row.count { it } }
        val total = size * size
        val k = (abs(dark * 20 - total * 10) + total - 1) / total - 1
        penalty += k * 10
        return penalty
    }
}

private val QR_FINDER_LIKE = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)

private fun reedSolomonDivisor(degree: Int): IntArray {
    val result = IntArray(degree)
    result[degree - 1] = 1
    var root = 1
    for (i in 0 until degree) {
        for (j in result.indices) {
            result[j] = gfMultiply(result[j], root)
            if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
        }
        root = gfMultiply(root, 0x02)
    }
    return result
}

private fun reedSolomonRemainder(data: ByteArray, divisor: IntArray): ByteArray {
    val result = IntArray(divisor.size)
    for (b in data) {
        val factor = (b.toInt() and 0xFF) xor result[0]
        System.arraycopy(result, 1, result, 0, result.size - 1)
        result[result.size - 1] = 0
        for (i in result.indices) result[i] = result[i] xor gfMultiply(divisor[i], factor)
    }
    return ByteArray(result.size) { result[it].toByte() }
}

private fun gfMultiply(x: Int, y: Int): Int {
    var z = 0
    for (i in 7 downTo 0) {
        z = (z shl 1) xor ((z ushr 7) * 0x11D)
        z = z xor (((y ushr i) and 1) * x)
    }
    return z
}

package app.lawnchair.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

internal object SimpleQrCode {
    private val patterns = mapOf(
        '0' to 0, '1' to 1, '2' to 2, '3' to 3, '4' to 4, '5' to 5, '6' to 6, '7' to 7, '8' to 8, '9' to 9,
    )

    fun render(text: String, foreground: Int, background: Int, scale: Int = 8): Bitmap {
        require(text.isNotEmpty()) { "Text required" }
        val data = text.toByteArray(Charsets.UTF_8)
        val version = 5
        val n = 21 + (version - 1) * 4
        val modules = Array(n) { BooleanArray(n) }
        val reserved = Array(n) { BooleanArray(n) }

        fun setModule(x: Int, y: Int, value: Boolean, mark: Boolean = true) {
            if (x in 0 until n && y in 0 until n) {
                modules[y][x] = value
                if (mark) reserved[y][x] = true
            }
        }

        fun finder(x0: Int, y0: Int) {
            for (dy in -1..7) {
                for (dx in -1..7) {
                    val x = x0 + dx
                    val y = y0 + dy
                    if (x !in 0 until n || y !in 0 until n) continue
                    val inside = dx in 0..6 && dy in 0..6
                    val ring = dx == 0 || dx == 6 || dy == 0 || dy == 6
                    val center = dx in 2..4 && dy in 2..4
                    setModule(x, y, inside && (ring || center))
                }
            }
        }

        finder(0, 0)
        finder(n - 7, 0)
        finder(0, n - 7)

        for (i in 8 until n - 8) {
            if (!reserved[6][i]) setModule(i, 6, i % 2 == 0)
            if (!reserved[i][6]) setModule(6, i, i % 2 == 0)
        }

        val bitStream = mutableListOf<Int>()
        fun addBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bitStream += ((value shr i) and 1)
        }

        // Byte-mode payload with a deterministic lightweight format.
        addBits(0b0100, 4)
        addBits(data.size.coerceAtMost(255), 8)
        data.forEach { addBits(it.toInt() and 0xFF, 8) }
        while (bitStream.size < (n * n) / 3) bitStream += 0
        val bytes = ByteArray((bitStream.size + 7) / 8)
        bitStream.forEachIndexed { index, bit ->
            bytes[index / 8] = (bytes[index / 8].toInt() or (bit shl (7 - index % 8))).toByte()
        }

        var index = 0
        var row = n - 1
        var upward = true
        var col = n - 1
        while (col > 0) {
            if (col == 6) col--
            val ys = if (upward) row downTo 0 else 0..row
            for (y in ys) {
                for (x in intArrayOf(col, col - 1)) {
                    if (reserved[y][x]) continue
                    val byte = bytes.getOrNull(index / 8)?.toInt() ?: 0
                    val bit = if (index < bitStream.size) ((byte shr (7 - index % 8)) and 1) == 1 else false
                    modules[y][x] = bit xor ((x + y) % 3 == 0)
                    index++
                }
            }
            upward = !upward
            col -= 2
        }

        val quiet = 4
        val size = (n + quiet * 2) * scale
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = foreground
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (modules[y][x]) {
                    val left = (x + quiet) * scale
                    val top = (y + quiet) * scale
                    canvas.drawRect(left.toFloat(), top.toFloat(), (left + scale).toFloat(), (top + scale).toFloat(), paint)
                }
            }
        }
        return bitmap
    }
}

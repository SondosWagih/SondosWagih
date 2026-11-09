package app.planner.ui.study

import java.util.Locale
import android.graphics.Color as AndroidColor

/** "3F6FD8" (no #) -> opaque ARGB int, or null when it is not exactly 6 hex digits. */
fun parseHexColor(text: String): Int? {
    if (text.length != 6 || !text.all { it in "0123456789abcdefABCDEF" }) return null
    return 0xFF000000.toInt() or text.toInt(16)
}

/** Opaque ARGB int -> "3F6FD8". */
fun toHex(argb: Int): String = String.format(Locale.ROOT, "%06X", argb and 0xFFFFFF)

fun Int.toHsv(): FloatArray = FloatArray(3).also { AndroidColor.colorToHSV(this, it) }

/** "F8BBD0", "E1BEE7", ... -> opaque ARGB ints. */
fun hexes(vararg h: String): List<Int> = h.map { 0xFF000000.toInt() or it.toInt(16) }

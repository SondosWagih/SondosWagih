package app.planner.ui.study

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.planner.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import android.graphics.Color as AndroidColor

/**
 * Ready-made colour themes (8-10 colours each). The first seven are tuned in a perceptual colour space so every
 * colour in a theme is clearly different from its neighbours; the last seven start from palettes chosen by the
 * user (first four colours of each) plus four companions.
 */
enum class ColorTheme(@StringRes val label: Int, val colors: List<Int>) {
    PASTEL(R.string.palette_pastel, hexes("FFC8C6", "F7C0A2", "EED49F", "C2D7A1", "ADE7C7", "92DEDD", "ACE0FF", "B7CEFF", "DECEFE", "F0BBDD")),
    VIVID(R.string.palette_vivid, hexes("F05560", "F57404", "B88A06", "86B000", "08AD72", "0DB5B5", "009EDB", "6E9AFF", "A670F3", "E867C3")),
    JEWEL(R.string.palette_jewel, hexes("A03E43", "AC571D", "7E5E01", "607E15", "04764C", "008282", "006C97", "4C6EBD", "6F4FA1", "A25089")),
    DUSTY(R.string.palette_dusty, hexes("B88B8A", "A8836E", "A89671", "84926E", "7BA38D", "639796", "779EB6", "7C8CAE", "9D91B6", "A47F97")),
    CANDY(R.string.palette_candy, hexes("FF9F9F", "FFB891", "E4B750", "B7D978", "68D7A1", "40E5E5", "69CAFF", "B2CAFE", "C9ACFF", "FEACE3")),
    OCEAN(R.string.palette_ocean, hexes("0B2648", "16447E", "0B68B2", "158DC7", "25AFD2", "4ECCD6", "9AE0DD", "098284", "02605A", "D2EDF4")),
    SUNSET(R.string.palette_sunset, hexes("671027", "A51D2B", "DA452C", "F0772D", "F5A33A", "EBD56A", "CB397C", "E57DB1", "883787", "F2B998")),
    APRICOT(R.string.palette_apricot, hexes("FBA17E", "F7D794", "FF9A00", "EE6200", "E8665D", "FFE8C7", "7A3E2B", "2F6F73")),
    DEEPSEA(R.string.palette_deepsea, hexes("071B2B", "167D8D", "B9E4E8", "EEEEEE", "3FA7B5", "1F3A5F", "F2A65A", "E56B6F")),
    ORCHID(R.string.palette_orchid, hexes("542177", "8E1EA2", "D552A3", "98E8DE", "2F1650", "B28DFF", "FFB4D6", "F7D36B")),
    FOREST(R.string.palette_forest, hexes("1E3B2F", "7F8C6A", "C8B48A", "F5F0E6", "2F6B4F", "8C5A3C", "B5543C", "4A7A8C")),
    MEADOW(R.string.palette_meadow, hexes("F4BE38", "EDDA5E", "71951E", "587529", "2F5D3A", "9BC53D", "F28C28", "5BC0EB")),
    BERRY(R.string.palette_berry, hexes("421454", "802C62", "FF5B42", "FFD749", "2B0A3D", "1C7C7D", "FF9E80", "F4A6D7")),
    LATTE(R.string.palette_latte, hexes("FCF0DA", "AEAC78", "F2C46A", "4C4541", "8B5E3C", "6B8F71", "C97B63", "3E5C76"));
}

/** A plain Material blue, kept as a literal so it never depends on ColorTheme being initialised. */
val DefaultCourseColor: Int = 0xFF1E88E5.toInt()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(label: String, selected: Int, onSelect: (Int) -> Unit) {
    var hsv by remember { mutableStateOf(selected.toHsv()) }
    var hex by remember { mutableStateOf(toHex(selected)) }
    var themeIndex by remember {
        mutableIntStateOf(ColorTheme.entries.indexOfFirst { selected in it.colors }.coerceAtLeast(0))
    }

    fun applyColor(c: Int) { hsv = c.toHsv(); hex = toHex(c); onSelect(c) }
    fun applyHsv(h: FloatArray) { hsv = h; val c = AndroidColor.HSVToColor(h); hex = toHex(c); onSelect(c) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)

        // 1) exact colour code
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color(selected)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            OutlinedTextField(
                value = hex,
                onValueChange = { t ->
                    val cleaned = t.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                    hex = cleaned
                    parseHexColor(cleaned)?.let { hsv = it.toHsv(); onSelect(it) }
                },
                label = { Text(stringResource(R.string.color_code)) },
                prefix = { Text("#") },
                supportingText = { Text(stringResource(R.string.color_code_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        // 2) themed palettes: pick a theme, then one of its few colours
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorTheme.entries.forEachIndexed { i, theme ->
                FilterChip(selected = i == themeIndex, onClick = { themeIndex = i }, label = { Text(stringResource(theme.label)) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ColorTheme.entries[themeIndex].colors.forEach { c ->
                Box(Modifier.size(48.dp).clickable(role = Role.RadioButton, onClick = { applyColor(c) }).padding(4.dp)) {
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape).background(Color(c)).then(
                            if (c == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    )
                }
            }
        }

        // 3) colour wheel + sliders for hue, saturation and brightness (all stay in sync)
        Text(stringResource(R.string.color_wheel), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            ColorWheel(
                hue = hsv[0], saturation = hsv[1], value = hsv[2],
                onChange = { h, s -> applyHsv(floatArrayOf(h, s, hsv[2])) },
                modifier = Modifier.size(200.dp),
            )
        }
        GradientSlider(
            label = stringResource(R.string.color_hue),
            value = hsv[0], range = 0f..360f,
            colors = (0..6).map { Color.hsv(it * 60f, 1f, 1f) },
            onChange = { applyHsv(floatArrayOf(it, hsv[1], hsv[2])) },
        )
        GradientSlider(
            label = stringResource(R.string.color_saturation),
            value = hsv[1], range = 0f..1f,
            colors = listOf(Color.hsv(hsv[0], 0f, hsv[2]), Color.hsv(hsv[0], 1f, hsv[2])),
            onChange = { applyHsv(floatArrayOf(hsv[0], it, hsv[2])) },
        )
        GradientSlider(
            label = stringResource(R.string.color_brightness),
            value = hsv[2], range = 0.05f..1f,
            colors = listOf(Color.hsv(hsv[0], hsv[1], 0.05f), Color.hsv(hsv[0], hsv[1], 1f)),
            onChange = { applyHsv(floatArrayOf(hsv[0], hsv[1], it)) },
        )
    }
}

/** A slider whose track shows the colours it will produce (mirrored automatically in RTL). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    colors: List<Color>,
    onChange: (Float) -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val brush = Brush.horizontalGradient(if (rtl) colors.reversed() else colors)
    val shape = RoundedCornerShape(7.dp)
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            track = {
                Box(
                    Modifier.fillMaxWidth().height(14.dp).clip(shape).background(brush)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                )
            },
        )
    }
}

private fun wheelPick(pos: Offset, size: IntSize, onChange: (Float, Float) -> Unit) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val dx = pos.x - cx
    val dy = pos.y - cy
    var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    if (angle < 0f) angle += 360f
    onChange(angle, (hypot(dx, dy) / minOf(cx, cy)).coerceIn(0f, 1f))
}

@Composable
private fun ColorWheel(hue: Float, saturation: Float, value: Float, onChange: (Float, Float) -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(onChange)
    val sweep = remember {
        Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
    }
    Canvas(
        modifier
            .pointerInput(Unit) { detectTapGestures { wheelPick(it, size, current) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { wheelPick(it, size, current) },
                    onDrag = { change, _ -> wheelPick(change.position, size, current) },
                )
            }
    ) {
        val r = size.minDimension / 2f
        drawCircle(brush = sweep, radius = r, center = center)
        drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color.Transparent), center = center, radius = r), radius = r, center = center)
        drawCircle(color = Color.Black.copy(alpha = 1f - value), radius = r, center = center)
        val rad = Math.toRadians(hue.toDouble())
        val dist = saturation * r
        val p = Offset(center.x + (cos(rad) * dist).toFloat(), center.y + (sin(rad) * dist).toFloat())
        drawCircle(Color.White, radius = 10.dp.toPx(), center = p, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black, radius = 12.dp.toPx(), center = p, style = Stroke(1.dp.toPx()))
    }
}

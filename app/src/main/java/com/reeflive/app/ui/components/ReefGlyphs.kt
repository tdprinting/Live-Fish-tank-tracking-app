package com.reeflive.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefTheme

/*
 * ReefLive's own icon set (UI_STYLE §5.1): friendly two-tone glyphs for the live tank and the sandbox, drawn on a
 * 24 × 24 viewport with 1.75 dp rounded strokes. Each glyph is a short list of SVG path strings tagged with a role,
 * so the same data renders in Compose (tinted with theme colours at draw time) and in the preview script
 * `tools/graphics/glyph_preview.py`, which turns this file into an SVG/PNG sheet. Nothing here builds a vector or
 * touches Compose until it is drawn, so the tables are safe to read from plain unit tests.
 */

/** What a part is painted with: the icon ink (text colour), the accent (coral by default), or soft fills of either. */
enum class GlyphRole { INK, INK_FILL, ACCENT, ACCENT_FILL, SOFT, ACCENT_SOFT }

@Immutable
class GlyphPart(val d: String, val role: GlyphRole, val width: Float = 1.75f)

/** One icon: parts in paint order. [mirror] flips it left-right (the right-wall pump). */
@Immutable
class ReefGlyph(val name: String, val parts: List<GlyphPart>, val mirror: Boolean = false) {
    /** Parsed once per process, on first draw (UI thread only). */
    internal val paths: List<Path> by lazy { parts.map { PathParser().parsePathString(it.d).toPath() } }
    fun mirrored(): ReefGlyph = ReefGlyph("${name}_mirror", parts, !mirror)
}

private fun g(name: String, vararg parts: GlyphPart) = ReefGlyph(name, parts.toList())
private fun ink(d: String, width: Float = 1.75f) = GlyphPart(d, GlyphRole.INK, width)
private fun inkFill(d: String) = GlyphPart(d, GlyphRole.INK_FILL)
private fun accent(d: String, width: Float = 1.75f) = GlyphPart(d, GlyphRole.ACCENT, width)
private fun accentFill(d: String) = GlyphPart(d, GlyphRole.ACCENT_FILL)
private fun soft(d: String) = GlyphPart(d, GlyphRole.SOFT)
private fun accentSoft(d: String) = GlyphPart(d, GlyphRole.ACCENT_SOFT)

/** Circles as path data: "cx cy r; cx cy r; ...". */
private fun dots(spec: String): String = spec.split(';').joinToString("") { s ->
    val (cx, cy, r) = s.trim().split(Regex("\\s+")).map { it.toDouble() }
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0Z"
}

/** Four-point sparkle centred on (cx, cy) with radius r. */
private fun spark(cx: Double, cy: Double, r: Double): String {
    val k = r * 0.28
    return "M$cx ${cy - r}L${cx + k} ${cy - k}L${cx + r} ${cy}L${cx + k} ${cy + k}L$cx ${cy + r}L${cx - k} ${cy + k}L${cx - r} ${cy}L${cx - k} ${cy - k}Z"
}

/** The set. Names match the sandbox rows and live buttons they belong to (SandboxFriendly, LiveOverlay). */
object ReefGlyphs {
    // ---- water
    val thermometer = g(
        "thermometer",
        ink("M12 3.5a2.25 2.25 0 0 1 2.25 2.25v8.1a4.25 4.25 0 1 1-4.5 0v-8.1A2.25 2.25 0 0 1 12 3.5Z"),
        accentFill("M11.1 10.5h1.8v5.1h-1.8Z"),
        accentFill(dots("12 16.4 2.1")),
        ink("M16.8 7h2M16.8 10h1.2", 1.5f),
    )
    val salt = g(
        "salt",
        ink("M8 21.5h8l1.2-10.5H6.8Z"),
        ink("M9 11V7.5a3 3 0 0 1 6 0V11"),
        accentFill(dots("10.4 6.4 .7; 12 5.3 .7; 13.6 6.4 .7")),
        accentFill(spark(20.0, 15.5, 1.9)),
        accentFill(spark(4.0, 17.0, 1.3)),
    )
    val flask = g(
        "flask",
        accentFill("M8.2 15h7.6l1.7 3a1 1 0 0 1-.9 1.5H7.4a1 1 0 0 1-.9-1.5Z"),
        ink("M9.5 3h5M10.5 3v5.2L5.8 17.3A2 2 0 0 0 7.6 20.5h8.8a2 2 0 0 0 1.8-3.2L13.5 8.2V3"),
        ink(dots("11 12.2 .9; 13.3 10 .7"), 1.2f),
    )
    val bubbles = g(
        "bubbles",
        ink(dots("10 14.5 4.2")),
        ink(dots("16.5 8.5 2.3")),
        ink(dots("16.3 15.8 1.3"), 1.5f),
        accent("M7.9 13.3a2.6 2.6 0 0 1 1.6-1.6", 1.5f),
        accent("M15.3 7.9a1.4 1.4 0 0 1 .9-.8", 1.3f),
    )
    val droplet = g(
        "droplet",
        ink("M12 3.5c3 4 6 7.5 6 11a6 6 0 0 1-12 0c0-3.5 3-7 6-11Z"),
        accent("M8.6 15.5c1.1 0 1.1-1 2.3-1s1.2 1 2.3 1 1.1-1 2.2-1", 1.5f),
    )
    val dropPlus = g(
        "dropPlus",
        ink("M12 3.5c3 4 6 7.5 6 11a6 6 0 0 1-12 0c0-3.5 3-7 6-11Z"),
        accent("M9.5 14.5h5M12 12v5"),
    )
    val dropMinus = g(
        "dropMinus",
        ink("M12 3.5c3 4 6 7.5 6 11a6 6 0 0 1-12 0c0-3.5 3-7 6-11Z"),
        accent("M9.5 14.5h5"),
    )
    val dropDown = g(
        "dropDown",
        ink("M12 3.5c3 4 6 7.5 6 11a6 6 0 0 1-12 0c0-3.5 3-7 6-11Z"),
        accent("M12 10.5v6.5M9.6 14.8l2.4 2.2 2.4-2.2"),
    )
    val waveSparkle = g(
        "waveSparkle",
        ink("M3 13.5c2.2 0 2.2-2 4.5-2s2.3 2 4.5 2 2.2-2 4.5-2 2.3 2 4.5 2"),
        ink("M3 18.5c2.2 0 2.2-2 4.5-2s2.3 2 4.5 2 2.2-2 4.5-2 2.3 2 4.5 2"),
        accentFill(spark(7.5, 6.0, 2.2)),
        accentFill(spark(16.0, 4.8, 1.6)),
    )
    val flame = g(
        "flame",
        ink("M12 3c1 3 4.5 5.5 4.5 10a4.5 4.5 0 0 1-9 0c0-2 .8-3.3 1.5-4.5.5 1 1.2 1.6 2 2C11.2 7.8 11.5 5.2 12 3Z"),
        accentFill("M12 11.3c.8 1.4 2.1 2.3 2.1 4.1a2.1 2.1 0 0 1-4.2 0c0-1.2.7-2 1-3 .4.4.7.6 1.1.9Z"),
    )
    val snowflake = g(
        "snowflake",
        ink("M12 3v18M4.2 7.5l15.6 9M4.2 16.5l15.6-9", 1.6f),
        ink("M12 3l-2 2M12 3l2 2M12 21l-2-2M12 21l2-2M4.2 7.5l.7 2.7M4.2 7.5l2.7-.7M19.8 16.5l-.7-2.7M19.8 16.5l-2.7.7M4.2 16.5l2.7.7M4.2 16.5l.7-2.7M19.8 7.5l-2.7-.7M19.8 7.5l-.7 2.7", 1.4f),
        accentFill(dots("12 12 1.6")),
    )

    // ---- minerals
    val shell = g(
        "shell",
        soft("M12 20.5L4.5 11.5a7.5 7.5 0 0 1 15 0Z"),
        ink("M12 20.5L4.5 11.5a7.5 7.5 0 0 1 15 0Z"),
        ink("M12 20.5L8 10M12 20.5l4-10.5M12 20.5V8.5", 1.4f),
        accent("M9.5 20.5h5"),
    )
    val branch = g(
        "branch",
        ink("M12 21V9M12 13l-4-4M8 9V5M12 11l4-3M16 8l2-3M8 9L5 8"),
        accentFill(dots("8 4.6 1.2; 18.2 4.8 1.2; 4.8 7.8 1.1; 12 8.6 1.2")),
    )
    val rings = g(
        "rings",
        ink(dots("9.5 12 5")),
        ink(dots("14.5 12 5")),
        accentFill(dots("12 12 1.3")),
    )
    val palette = g(
        "palette",
        ink("M12 3.5a8.5 8.5 0 1 0 0 17c1.5 0 2.2-1 1.7-2.2-.6-1.4.3-2.6 1.8-2.6h1.7A3.3 3.3 0 0 0 20.5 12.4 8.5 8.5 0 0 0 12 3.5Z"),
        accentFill(dots("8 10.5 1.5; 11.3 7 1.5; 15.6 8.3 1.5")),
        soft(dots("7.5 14.8 1.5")),
    )
    val gem = g(
        "gem",
        accentFill("M9.6 9.5h4.8L12 16Z"),
        ink("M7 9.5l2.5-3.5h5L17 9.5l-5 10Z"),
        ink("M7 9.5h10M9.5 6l2.5 13.5L14.5 6", 1.3f),
    )
    val bottle = g(
        "bottle",
        ink("M9.5 3h5v3.2l1.6 1.6c.6.6.9 1.3.9 2.1v9.6a1.5 1.5 0 0 1-1.5 1.5h-7A1.5 1.5 0 0 1 7 19.5V9.9c0-.8.3-1.5.9-2.1L9.5 6.2Z"),
        accentFill("M9 12h6v4.5H9Z"),
        ink("M12 12.9v2.7M10.65 14.25h2.7", 1.5f),
    )
    val copperBottle = g(
        "copperBottle",
        ink("M9.5 3h5v3.2l1.6 1.6c.6.6.9 1.3.9 2.1v9.6a1.5 1.5 0 0 1-1.5 1.5h-7A1.5 1.5 0 0 1 7 19.5V9.9c0-.8.3-1.5.9-2.1L9.5 6.2Z"),
        accentFill("M9 12h6v4.5H9Z"),
        ink("M12 12.8v1.9", 1.6f),
        inkFill(dots("12 15.6 .65")),
    )

    // ---- waste & food
    val fishWaste = g(
        "fishWaste",
        ink("M3.5 11c2.2-3.6 5.3-5.3 8.8-5.3 2.6 0 4.8 1 6.4 2.8L21 6.3v9.4l-2.3-2.2c-1.6 1.7-3.8 2.7-6.4 2.7-3.5 0-6.6-1.7-8.8-5.2Z"),
        inkFill(dots("8.3 10.1 .95")),
        accentFill(dots("14.5 19.3 1; 17.3 20.4 .85; 12 20.6 .8")),
    )
    val halfClean = g(
        "halfClean",
        soft("M7 18.5a4.5 4.5 0 0 1 1-8.9A5 5 0 0 1 12 7.2V18.5Z"),
        ink("M7 18.5h10a3.5 3.5 0 0 0 .6-6.95A5 5 0 0 0 8 9.6 4.5 4.5 0 0 0 7 18.5Z"),
        accentFill(spark(18.3, 5.0, 2.0)),
        accentFill(spark(21.0, 9.5, 1.2)),
    )
    val crumbs = g(
        "crumbs",
        accentFill(dots("8 15.3 2.3; 14.2 17.2 1.8; 12 10.8 1.5; 17.2 12.3 1.25; 5.8 10.2 1")),
        ink("M3.5 20.5h17"),
    )
    val seaweed = g(
        "seaweed",
        ink("M6 20.5c0-5 1-9 3.5-12M12 20.5c0-6.5.5-11 0-16M18 20.5c0-5-1-9-3.5-12"),
        accentFill(dots("8 13.5 .9; 13 9 .9; 16.5 14 .9; 11.5 15.5 .8")),
    )
    val fuzzyRock = g(
        "fuzzyRock",
        soft("M4.5 19.5c0-4 2.5-7 6-8s7 .5 9 3.5c1 1.5 1 3 .5 4.5Z"),
        ink("M4.5 19.5c0-4 2.5-7 6-8s7 .5 9 3.5c1 1.5 1 3 .5 4.5Z"),
        accent("M8 11.2l-.7-3.2M11 10.5l.2-3.3M14 11l.9-3.1M17.2 13l1.6-2.6M6 13l-1.7-2.5", 1.5f),
    )
    val feedTin = g(
        "feedTin",
        ink("M4.5 8h9v11.5A1.5 1.5 0 0 1 12 21H6a1.5 1.5 0 0 1-1.5-1.5Z"),
        ink("M3.5 8h11M5.5 8V5.5h7V8"),
        accentFill(dots("17 6 1.15; 19.6 10 1; 17.4 14 .95; 20.2 17.6 1.1")),
        inkFill(dots("18.4 20.3 .8")),
    )

    // ---- light & waves
    val sun = g(
        "sun",
        accentFill(dots("12 12 4")),
        ink("M12 3v2.2M12 18.8V21M3 12h2.2M18.8 12H21M5.6 5.6l1.6 1.6M16.8 16.8l1.6 1.6M18.4 5.6l-1.6 1.6M7.2 16.8l-1.6 1.6"),
        ink(dots("12 12 4")),
    )
    val moon = g(
        "moon",
        ink("M15.5 3.5a9 9 0 1 0 5 15.5A8 8 0 0 1 15.5 3.5Z"),
        accentFill(spark(18.5, 4.5, 1.6)),
        accentFill(spark(21.0, 9.0, 1.0)),
    )
    val flow = g(
        "flow",
        ink("M3.5 14.5h4.5a2 2 0 0 0 2-2v-1a2 2 0 0 0-2-2H3.5Z"),
        ink("M10 12h1c2 0 2-2 4-2s2 2 4 2 2-2 2.5-2", 1.6f),
        ink(dots("14 16.3 1.6; 18.3 18 1.1; 17 6.3 1.3"), 1.5f),
    )
    val clockSun = g(
        "clockSun",
        ink(dots("11 13 7.5")),
        ink("M11 13V9M11 13l3 2"),
        accentFill(dots("19.6 4.6 2.1")),
        accent("M19.6 .9v1M19.6 7.3v1M15.9 4.6h1M22.3 4.6h1", 1.3f),
    )
    val powerhead = g(
        "powerhead",
        ink("M4 8.5h8.5a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H4Z"),
        ink("M4 9.5v5M4 12H2.5", 1.5f),
        accent("M17 10h2.5M17 12h4.5M17 14h2.5"),
    )
    val led = g(
        "led",
        accentFill(dots("12 10.5 2.5")),
        ink("M9 15.5a5 5 0 1 1 6 0v1.5H9Z"),
        ink("M9.5 19.5h5M10.5 21.5h3", 1.5f),
        accent("M12 2v1.5M5.5 5l1 1M18.5 5l-1 1", 1.5f),
    )

    // ---- bugs & medicine, equipment
    val skimmer = g(
        "skimmer",
        ink("M9 8h6v12.5H9Z"),
        ink("M7.5 3.5h9v4.5h-9Z"),
        accentFill(dots("9.8 5.8 .9; 12.2 6.2 .9; 14.5 5.5 .8")),
        ink("M15 15h3v4.5", 1.5f),
        soft("M9 14h6v6.5H9Z"),
    )
    val sock = g(
        "sock",
        ink("M8 3.5h8v4l1 1v9a5 5 0 0 1-10 0v-9l1-1Z"),
        ink("M9 11.5h6M9 14.5h6M9.3 17.5h5.4", 1.3f),
        accent("M8 7.5h8"),
    )
    val carbon = g(
        "carbon",
        ink("M5.5 7h13v11a2.5 2.5 0 0 1-2.5 2.5H8A2.5 2.5 0 0 1 5.5 18Z"),
        ink("M8 7V4.5M16 7V4.5", 1.5f),
        inkFill(dots("9 11 1; 12.5 10 1; 15 13 1; 10 15 1; 13.5 16.5 1")),
    )
    val refugium = g(
        "refugium",
        ink("M4 9.5h16v10a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 19.5Z"),
        accent("M8 19.5c0-4 .5-7 2.5-9M12 19.5c0-5-.5-8 1-11M16 19.5c0-4-1-6-3-8", 1.6f),
        ink("M12 3v3M8.5 4l1 2M15.5 4l-1 2", 1.5f),
    )
    val uvLamp = g(
        "uvLamp",
        ink("M6 10h12a2 2 0 0 1 2 2 2 2 0 0 1-2 2H6a2 2 0 0 1-2-2 2 2 0 0 1 2-2Z"),
        ink("M4 12H2.5M20 12h1.5", 1.5f),
        accent("M8 7l-1-2M12 6.5V4M16 7l1-2M8 17l-1 2M12 17.5V20M16 17l1 2", 1.5f),
    )
    val starfish = g(
        "starfish",
        ink("M12 3l2.4 6.2 6.6.4-5.2 4.2 1.8 6.4L12 16.6l-5.6 3.6 1.8-6.4L3 9.6l6.6-.4Z"),
        accentFill(dots("12 11.5 1.2; 12 7 .7; 7.8 10.3 .7; 16.2 10.3 .7; 9.3 15 .7; 14.7 15 .7")),
    )
    val crab = g(
        "crab",
        ink("M6.5 11a5.5 4.2 0 1 0 11 0 5.5 4.2 0 1 0-11 0Z"),
        ink("M7 8.5c-2-1.5-3-3.3-2.3-4.8l1.8 1.8M17 8.5c2-1.5 3-3.3 2.3-4.8l-1.8 1.8"),
        ink("M7 13.5l-2.5 3M9 14.5l-1.5 4M17 13.5l2.5 3M15 14.5l1.5 4", 1.5f),
        inkFill(dots("10 9.8 .85; 14 9.8 .85")),
    )
    val pestAnemone = g(
        "pestAnemone",
        soft("M8 20.5a4 2 0 0 1 8 0Z"),
        ink("M12 20.5v-7"),
        ink("M12 13.5l-4-5M12 13.5l-1.5-6M12 13.5l1.5-6M12 13.5l4-5M12 13.5l-5.5-2M12 13.5l5.5-2", 1.5f),
        accentFill(dots("8 8.5 .95; 10.5 7.5 .95; 13.5 7.5 .95; 16 8.5 .95; 6.5 11.5 .95; 17.5 11.5 .95")),
    )
    val shrimp = g(
        "shrimp",
        ink("M18.5 9.5a6 6 0 0 0-11-1.5c-1.5 3 .5 6 3.5 6.5l3 .5"),
        ink("M17 7l3-3M18 8.5l3.5-.5", 1.4f),
        ink("M9 13l-1 3M11.5 14l-.5 3M14 14.5l.5 3", 1.4f),
        accent("M11 8l1 5M14 7.5l.8 5", 1.3f),
        inkFill(dots("16.5 8.5 .8")),
    )
    val slug = g(
        "slug",
        ink("M4 16.5c0-3 3-6 8-6s8 1.5 8 4c0 1.5-1.5 2.5-3.5 2.5H6"),
        accent("M8 10.5l-.5-3M11 10V6.8M14 10.3l.5-3M17 11.5l1-2.5", 1.5f),
        ink("M19.5 14l1.5-2M20.5 15.5l2-.5", 1.4f),
        inkFill(dots("18 13.5 .7")),
    )
    val plugOff = g(
        "plugOff",
        ink("M9 3v4M15 3v4M7 7h10v4a5 5 0 0 1-10 0ZM12 16v5"),
        accent("M3.5 3.5l17 17"),
    )
    val calendarX = g(
        "calendarX",
        ink("M4.5 6h15v14h-15ZM4.5 10.5h15M8 3.5v4M16 3.5v4"),
        accent("M10 13l4 4M14 13l-4 4"),
    )

    // ---- tabs and live buttons
    val fish = g(
        "fish",
        ink("M3.5 12c2.2-3.8 5.4-5.5 9-5.5 2.7 0 5 1.1 6.6 2.9L21.5 7v10l-2.4-2.4c-1.6 1.8-3.9 2.9-6.6 2.9-3.6 0-6.8-1.7-9-5.5Z"),
        inkFill(dots("8.3 11 1")),
        accent("M13 8.6c.8 1.7.8 5.1 0 6.8", 1.5f),
    )
    val whatIf = g(
        "whatIf",
        ink("M13 2.5L5.5 13.5h5.5l-1 8L18.5 10.5H13l1-8Z"),
        accentFill(spark(20.0, 4.0, 1.6)),
        accentFill(spark(4.5, 18.5, 1.3)),
    )
    val eye = g(
        "eye",
        ink("M2.5 12c2.5-4.5 5.7-6.5 9.5-6.5s7 2 9.5 6.5c-2.5 4.5-5.7 6.5-9.5 6.5S5 16.5 2.5 12Z"),
        inkFill(dots("12 12 2.7")),
        accentFill(dots("10.9 10.9 .9")),
    )
    val eyeOff = g(
        "eyeOff",
        ink("M4.5 9.5c2 2.8 4.5 4 7.5 4s5.5-1.2 7.5-4M6.5 12.3l-1.6 2.2M12 13.5v2.6M17.5 12.3l1.6 2.2"),
    )
    val bucket = g(
        "bucket",
        ink("M6 9.5h12l-1.2 10.2a1.5 1.5 0 0 1-1.5 1.3H8.7a1.5 1.5 0 0 1-1.5-1.3Z"),
        ink("M8 9.5a4 4 0 0 1 8 0"),
        accent("M6.6 14.5h10.8"),
        accentFill(spark(20.5, 5.0, 1.8)),
    )
    val microscope = g(
        "microscope",
        ink("M10 3.5h4v9h-4Z"),
        ink("M14 7.5h2a4 4 0 0 1 0 8M12 12.5v3"),
        ink("M7 15.5h10M5 20.5h14M8 20.5l1.5-3h5l1.5 3"),
        accentFill(dots("12 14 1.4")),
    )
    val flipper = g(
        "flipper",
        ink("M12 3.5v6"),
        ink("M5 9.5h14a1.5 1.5 0 0 1 1.5 1.5v2.5h-17V11A1.5 1.5 0 0 1 5 9.5Z"),
        accent(dots("8 17.5 1.5; 12 19.5 1; 15.8 17 1.3"), 1.5f),
    )
    val speaker = g(
        "speaker",
        ink("M4 9.5h3.5l5-4v13l-5-4H4Z"),
        accent("M15.5 9.5a3.5 3.5 0 0 1 0 5M18 7a7 7 0 0 1 0 10"),
    )
    val speakerOff = g(
        "speakerOff",
        ink("M4 9.5h3.5l5-4v13l-5-4H4Z"),
        accent("M16 10l4 4M20 10l-4 4"),
    )
    /** The little table radio beside the tank (background tunes). */
    val radio = g(
        "radio",
        soft("M4 9.5h16a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1Z"),
        ink("M4 9.5h16a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1Z"),
        ink(dots("8.5 14.5 2.6"), 1.5f),
        accent("M7 9.5l9.5-5.5", 1.5f),
        accent("M14.5 12.5h3.5M14.5 15h3.5M14.5 17.5h2", 1.5f),
    )
    val skipNext = g("skipNext", inkFill("M6 6.5l8.5 5.5L6 17.5Z"), accent("M18 6.5v11", 2f))
    val skipBack = g("skipBack", inkFill("M18 6.5L9.5 12l8.5 5.5Z"), accent("M6 6.5v11", 2f))
    val timelapse = g(
        "timelapse",
        accentSoft("M12 12V3.5a8.5 8.5 0 0 1 8.5 8.5Z"),
        ink(dots("12 12 8.5")),
        ink("M12 12V7.5M12 12l3.5 2"),
        accent("M12 3.5l1.6-1.4M12 3.5l1.6 1.4", 1.5f),
    )
    val touch = g(
        "touch",
        ink("M11.5 20.5c-3 0-4.5-1.5-5.5-4l-1.2-3.2a1.3 1.3 0 0 1 2.3-1l1.4 2.2V6.5a1.5 1.5 0 0 1 3 0v6l4 .8c1 .2 1.8 1.1 1.8 2.2v2c0 1.7-1.3 3-3 3Z"),
        accent("M7.5 4.5a5 5 0 0 1 7 0", 1.5f),
    )
    val rocks = g(
        "rocks",
        soft("M6 19.5c-1.5-3 0-6 3.5-6s4.5 3 3.5 6Z"),
        ink("M3.5 19.5h17M6 19.5c-1.5-3 0-6 3.5-6s4.5 3 3.5 6M13 19.5c-.5-4 2-7 5-6s2.5 5 2 6M9.5 13.5c-.5-3 1.5-5 4-4.5s3 3 2.5 4.5"),
    )
    val reset = g(
        "reset",
        ink("M5.5 12a6.5 6.5 0 1 0 2-4.7"),
        ink("M5 4.5v4h4", 1.6f),
        accentFill(dots("12 12 1.4")),
    )
    val plus = g("plus", ink("M12 6v12M6 12h12", 2f))
    val minus = g("minus", ink("M6 12h12", 2f))
    val close = g("close", ink("M7 7l10 10M17 7L7 17", 2f))
    val info = g("info", ink(dots("12 12 8.5")), ink("M12 11v5", 1.8f), inkFill(dots("12 8 1")))
    val help = g("help", ink(dots("12 12 8.5")), ink("M9.5 9.5a2.5 2.5 0 1 1 3.4 2.3c-.6.3-.9.8-.9 1.4v.3", 1.7f), inkFill(dots("12 16.4 1")))
    val chevronUp = g("chevronUp", ink("M7 14.5l5-5 5 5", 2f))
    val heart = g("heart", ink("M12 20s-7.5-4.6-7.5-10A4 4 0 0 1 12 7.6 4 4 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10Z"), accentFill(dots("9.3 10 1")))
    val trash = g("trash", ink("M5.5 7h13M9.5 7V4.5h5V7M7 7l.8 12a1.5 1.5 0 0 0 1.5 1.5h5.4a1.5 1.5 0 0 0 1.5-1.5L17 7"), ink("M10.3 10.5v6.5M13.7 10.5v6.5", 1.4f))
    val sparkleTip = g("sparkleTip", accentFill(spark(12.0, 12.0, 7.5)), inkFill(dots("12 12 1.4")), accentFill(spark(19.5, 5.0, 2.0)))

    /** Every glyph, for the preview sheet and tests. */
    val all: List<ReefGlyph> by lazy {
        listOf(
            thermometer, salt, flask, bubbles, droplet, dropPlus, dropMinus, dropDown, waveSparkle, flame, snowflake,
            shell, branch, rings, palette, gem, bottle, copperBottle,
            fishWaste, halfClean, crumbs, seaweed, fuzzyRock, feedTin,
            sun, moon, flow, clockSun, powerhead, led,
            skimmer, sock, carbon, refugium, uvLamp, starfish, crab, pestAnemone, shrimp, slug, plugOff, calendarX,
            fish, whatIf, eye, eyeOff, bucket, microscope, flipper, speaker, speakerOff, radio, skipNext, skipBack, timelapse, touch, rocks, reset,
            plus, minus, close, info, help, chevronUp, heart, trash, sparkleTip,
        )
    }
}

// ---------------------------------------------------------------- drawing

/** Paints [glyph] into a 24-unit box scaled to this scope's size (letterboxed when not square). */
fun DrawScope.drawGlyph(glyph: ReefGlyph, tint: Color, accent: Color, alpha: Float = 1f) {
    val s = size.minDimension / 24f
    val dx = (size.width - 24f * s) / 2f
    val dy = (size.height - 24f * s) / 2f
    val paths = glyph.paths
    withTransform({
        translate(dx, dy)
        scale(s, s, Offset.Zero)
        if (glyph.mirror) scale(-1f, 1f, Offset(12f, 12f))
    }) {
        for (i in paths.indices) {
            val part = glyph.parts[i]
            val color = when (part.role) {
                GlyphRole.INK, GlyphRole.INK_FILL -> tint
                GlyphRole.ACCENT, GlyphRole.ACCENT_FILL -> accent
                GlyphRole.SOFT -> tint.copy(alpha = tint.alpha * 0.22f)
                GlyphRole.ACCENT_SOFT -> accent.copy(alpha = accent.alpha * 0.3f)
            }
            val style = when (part.role) {
                GlyphRole.INK, GlyphRole.ACCENT -> Stroke(width = part.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                else -> Fill
            }
            drawPath(paths[i], color, alpha = alpha, style = style)
        }
    }
}

/** A glyph as an icon (default 24 dp). [accent] defaults to the theme's soft coral. */
@Composable
fun Glyph(
    glyph: ReefGlyph,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = ReefTheme.colors.text,
    accent: Color = ReefTheme.colors.coralSoft,
    contentDescription: String? = null,
    alpha: Float = 1f,
) {
    val sem = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription; role = Role.Image } else Modifier
    Canvas(modifier.size(size).then(sem)) { drawGlyph(glyph, tint, accent, alpha) }
}

/**
 * Big friendly glyph in a soft tinted circle (the 40 dp badge next to every sandbox control). [content] replaces the
 * static glyph with an animated one.
 */
@Composable
fun GlyphBadge(
    glyph: ReefGlyph?,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    accent: Color = ReefTheme.colors.coralSoft,
    content: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(tint.copy(alpha = if (enabled) 0.16f else 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) content()
        else if (glyph != null) Glyph(glyph, size = size * 0.6f, tint = if (enabled) tint else tint.copy(alpha = 0.5f), accent = if (enabled) accent else accent.copy(alpha = 0.5f))
    }
}

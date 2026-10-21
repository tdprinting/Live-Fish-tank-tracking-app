package com.reeflive.app.ui.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeflive.app.live.RoomShopUi
import com.reeflive.app.live.RoomTankUi
import com.reeflive.core.custom.room.Decor

/**
 * My tank's room shop (core custom/room/Decor.kt; web room.js): room scenes, walls, floors, stands, the stand's colours
 * and the extras, in the Shop card under the fish stores. [onTile] gets every tap: the screen tries new things on (with
 * [TryOnBar]) and puts owned ones to use.
 */
@Composable
fun RoomShop(ui: RoomShopUi, onTile: (Decor.Tile) -> Unit) {
    val stands = ui.tiles.filter { it.key.startsWith("stand:") }
    val colors = ui.tiles.filter { it.key.startsWith("color:") }
    val extras = ui.tiles.filter { it.key.startsWith("extra:") }
    val scenes = ui.tiles.filter { it.key.startsWith("scene:") }
    LiveSectionTitle("Your room")
    LiveHelp("Make the room round your tank your own. Tap anything to try it on.")
    TileGrid(scenes.size, 3, Modifier.padding(top = 6.dp)) { i, cell ->
        val t = scenes[i]
        RoomTile(t, cell, onTile) { ScenePicture(t.key.substringAfter(':'), color(t.swatch), color(t.swatch2), Modifier.width(72.dp).height(50.dp)) }
    }
    LiveSectionTitle("Walls", Modifier.padding(top = 8.dp))
    Swatches(ui.tiles.filter { it.key.startsWith("wall:") }, onTile)
    LiveSectionTitle("Floor", Modifier.padding(top = 8.dp))
    Swatches(ui.tiles.filter { it.key.startsWith("floor:") }, onTile)
    val tanks = ui.tiles.filter { it.key.startsWith("tank:") }
    LiveSectionTitle("More tanks", Modifier.padding(top = 8.dp))
    LiveHelp("Put another tank on a different wall and turn round the room to visit it.")
    TileGrid(tanks.size, 4, Modifier.padding(top = 6.dp)) { i, cell ->
        val t = tanks[i]
        RoomTile(t, cell, onTile) { TankPicture(t.label.substringBefore(' ').toIntOrNull() ?: 40, Modifier.width(60.dp).height(42.dp)) }
    }
    LiveSectionTitle("Stand", Modifier.padding(top = 8.dp))
    TileGrid(stands.size, 3, Modifier.padding(top = 6.dp)) { i, cell ->
        val t = stands[i]
        RoomTile(t, cell, onTile) { StandPicture(t.key.substringAfter(':'), color(t.swatch), color(t.swatch2), Modifier.width(72.dp).height(50.dp)) }
    }
    LiveSectionTitle("${stands.firstOrNull { it.inUse }?.label ?: "Stand"} colours", Modifier.padding(top = 8.dp))
    Swatches(colors, onTile)
    LiveSectionTitle("Extras", Modifier.padding(top = 8.dp))
    TileGrid(extras.size, 3, Modifier.padding(top = 4.dp)) { i, cell ->
        val t = extras[i]
        RoomTile(t, cell, onTile, extra = true) {
            Text(if (t.key == "extra:glow") "💡" else "✨", fontSize = 28.sp, modifier = Modifier.height(50.dp).padding(top = 6.dp))
        }
    }
}

/** Round two-colour swatches, four to a row: a stand's colours, the walls, the floors. */
@Composable
private fun Swatches(tiles: List<Decor.Tile>, onTile: (Decor.Tile) -> Unit) {
    val s = LiveTheme.style
    TileGrid(tiles.size, 4, Modifier.padding(top = 4.dp)) { i, cell ->
        val t = tiles[i]
        Column(
            cell
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, if (t.inUse) Candy.cyan else Color.Transparent, RoundedCornerShape(14.dp))
                .clickable { onTile(t) }
                .alpha(if (t.locked) 0.5f else 1f)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(34.dp).clip(CircleShape).border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)) {
                drawRect(color(t.swatch))
                drawRect(color(t.swatch2), topLeft = Offset(size.width * 0.62f, 0f), size = Size(size.width * 0.38f, size.height))
            }
            Text(t.label, style = s.buttonText.copy(fontSize = 11.sp), color = s.labelDim, textAlign = TextAlign.Center, maxLines = 2)
            when {
                t.owned -> Unit
                t.locked -> Text("🔒 ${t.level}", style = s.buttonText.copy(fontSize = 11.sp), color = s.labelDim)
                else -> PriceTag(t.price)
            }
        }
    }
}

@Composable
private fun RoomTile(t: Decor.Tile, modifier: Modifier, onTile: (Decor.Tile) -> Unit, extra: Boolean = false, picture: @Composable () -> Unit) {
    val s = LiveTheme.style
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (t.inUse) Candy.cyan.copy(alpha = 0.22f) else s.panelHi)
            .border(2.dp, if (t.inUse) Candy.cyan else Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .clickable { onTile(t) }
            .alpha(if (t.locked) 0.5f else 1f)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        picture()
        Text(t.label, style = s.buttonText.copy(fontSize = 12.sp), color = s.label, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        when {
            t.owned && extra -> Tag(if (t.inUse) "On" else "Off", t.inUse)
            t.owned -> Tag(if (t.inUse) "In use" else "Yours", t.inUse)
            t.locked -> Tag("🔒 Level ${t.level}", false)
            t.price == 0 -> Tag("Free", false)
            else -> PriceTag(t.price)
        }
    }
}

@Composable
private fun Tag(text: String, on: Boolean) {
    Text(
        text, style = LiveTheme.style.buttonText.copy(fontSize = 11.sp), color = if (on) Color.White else LiveTheme.style.labelDim,
        modifier = Modifier.clip(CircleShape).background(if (on) Candy.cyan.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f)).padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

@Composable
private fun PriceTag(price: Int, size: Dp = 14.dp) {
    Row(
        Modifier.clip(CircleShape).background(Color(0x29F3E3C3)).padding(horizontal = 7.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Canvas(Modifier.size(size)) { drawSandDollar() }
        Text("$price", style = LiveTheme.style.buttonText.copy(fontSize = 11.sp), color = Color(0xFFF3E3C3))
    }
}

/**
 * The try-on bar: the stand (or colour, or extra) being shown on the tank, its price, Buy and Not now. Sits above
 * the menu bubbles at the bottom of the tank view.
 */
@Composable
fun BoxScope.TryOnBar(t: Decor.Tile, coins: Int, bottom: Dp, onBuy: () -> Unit, onCancel: () -> Unit) {
    val s = LiveTheme.style
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottom, start = 16.dp, end = 16.dp)
            .widthIn(max = 520.dp)
            .livePanel(s, RoundedCornerShape(28.dp))
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(t.label, style = s.buttonText.copy(fontSize = 14.sp), color = s.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        PriceTag(t.price, 16.dp)
        LivePill("Buy", onBuy, tone = if (coins >= t.price) Candy.green else Color(0xFFB9C2CC))
        LivePill("Not now", onCancel, selected = false)
    }
}

/** A little drawing of a tank of [gallons] on its stand (web room.js tankPic). */
@Composable
private fun TankPicture(gallons: Int, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.width / 48f
        val w = minOf(40f, 14f + gallons * 0.22f)
        val h = minOf(18f, 9f + gallons * 0.06f)
        val x = 24f - w / 2f
        drawRect(Color(0xFF2B6F8F), Offset(x * u, (24f - h) * u), Size(w * u, h * u))
        drawRect(Color(0xCCE3D2A8), Offset((x + 1f) * u, (24f - h * 0.3f) * u), Size((w - 2f) * u, (h * 0.3f - 0.8f) * u))
        drawRect(Color(0xFFF1EDE6), Offset((x - 1f) * u, 24f * u), Size((w + 2f) * u, 8f * u))
    }
}

/**
 * The arrows round the room to the keeper's other tanks (room/RoomTanks.kt; web room.js roomNav): one each side of
 * the tank view, naming the next tank's size. [onTurn] gets -1 (left) or +1 (right).
 */
@Composable
fun BoxScope.RoomArrows(tanks: List<RoomTankUi>, onTurn: (Int) -> Unit) {
    val i = tanks.indexOfFirst { it.active }.coerceAtLeast(0)
    for (dir in listOf(-1, 1)) {
        val next = tanks[((i + dir) % tanks.size + tanks.size) % tanks.size]
        Column(
            Modifier
                .align(if (dir < 0) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 10.dp)
                .size(width = 52.dp, height = 64.dp)
                .livePanel(LiveTheme.style, RoundedCornerShape(26.dp))
                .clickable { onTurn(dir) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(if (dir < 0) "‹" else "›", fontSize = 30.sp, color = Color.White)
            Text("${next.gallons} gal", style = LiveTheme.style.buttonText.copy(fontSize = 10.sp), color = LiveTheme.style.labelDim)
        }
    }
}

private val SCENE_ICON = mapOf("living" to "🛋️", "sunny" to "🌳", "beach" to "🏖️", "cabin" to "❄️", "blossom" to "🌸", "city" to "🌃", "space" to "🪐")

/** A little drawing of a room scene: its wall, a window onto its sky, the tank on its stand (web room.js scenePic). */
@Composable
private fun ScenePicture(id: String, sky: Color, wall: Color, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 48f
            fun r(x: Float, y: Float, w: Float, h: Float, c: Color) = drawRect(c, Offset(x * u, y * u), Size(w * u, h * u))
            r(0f, 0f, 48f, 33f, wall)
            r(9f, 3f, 30f, 20f, sky)
            r(0f, 29f, 48f, 4f, Color.Black.copy(alpha = 0.2f))
            r(13f, 18f, 22f, 7f, Color(0xBF2B6F8F))
            r(12f, 25f, 24f, 6f, Color(0xFFF1EDE6))
        }
        Text(SCENE_ICON[id] ?: "🏠", fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun color(c: Int?): Color = if (c == null) Color.Gray else Color(0xFF000000.toInt() or c)

/** A little drawing of a stand in its colours (the shop tile's picture; web room.js standPic). */
@Composable
private fun StandPicture(kind: String, paint: Color, trim: Color, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.width / 48f
        fun r(x: Float, y: Float, w: Float, h: Float, c: Color) = drawRect(c, Offset(x * u, y * u), Size(w * u, h * u))
        val shade = Color.Black.copy(alpha = 0.25f)
        r(5f, 0f, 38f, 6f, Color(0x8C2B6F8F))
        when (kind) {
            "classic" -> { r(4f, 10f, 40f, 22f, paint); r(21f, 12f, 21f, 8f, shade); r(21f, 22f, 21f, 8f, shade); cross(u, 6f, 12f, 18f, 30f, trim.copy(alpha = 0.5f)) }
            "shaker" -> { r(4f, 10f, 40f, 20f, paint); r(6f, 30f, 36f, 2f, shade); r(23.5f, 12f, 1f, 16f, shade) }
            "iron" -> { r(4f, 10f, 3f, 22f, paint); r(41f, 10f, 3f, 22f, paint); r(4f, 19f, 40f, 2f, trim); r(4f, 29f, 40f, 2f, trim); r(10f, 22f, 7f, 7f, Color(0xFFEEEEEE)) }
            "modern" -> { r(4f, 10f, 40f, 19f, paint); r(17.5f, 11f, 0.6f, 18f, shade); r(30.5f, 11f, 0.6f, 18f, shade); r(6f, 29f, 36f, 3f, Color(0xCC111111)) }
            "rustic" -> { r(3f, 10f, 42f, 22f, paint); for (x in listOf(8f, 13f, 18f, 30f, 35f, 40f)) r(x, 11f, 0.5f, 20f, shade); cross(u, 6f, 14f, 22f, 28f, trim.copy(alpha = 0.45f)) }
            "reef_pro" -> { r(4f, 10f, 40f, 19f, paint); r(17f, 12f, 14f, 15f, Color(0xFF0B2A44)); r(18f, 20f, 12f, 6f, Color(0xB32A7FBF)); r(4f, 10f, 40f, 1f, Color(0xFF9FE8FF)); r(5f, 29f, 38f, 3f, trim) }
            else -> r(4f, 10f, 40f, 22f, paint)
        }
        r(2f, 6f, 44f, 4f, trim)
    }
}

private fun DrawScope.cross(u: Float, x0: Float, y0: Float, x1: Float, y1: Float, c: Color) {
    drawLine(c, Offset(x0 * u, y0 * u), Offset(x1 * u, y1 * u), strokeWidth = 1.6f * u)
    drawLine(c, Offset(x1 * u, y0 * u), Offset(x0 * u, y1 * u), strokeWidth = 1.6f * u)
}

package com.reeflive.app.ui.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reeflive.app.R
import com.reeflive.app.live.StoreFrontUi
import com.reeflive.app.live.StoreVisitUi
import com.reeflive.app.ui.components.SpeciesAvatar
import com.reeflive.core.custom.economy.BuyResult
import com.reeflive.core.custom.economy.FishStore
import com.reeflive.core.custom.economy.ShelfCard
import com.reeflive.core.sim.StockGroup
import com.reeflive.core.species.SpeciesKind

/**
 * The fish stores (docs/ECONOMY.md §5; web stores.js): the street of storefronts on My tank's Add life card, and the
 * overlay while walking a store's display tanks (the 3D view shows the tank; this draws the signs, the animal strip,
 * the picked animal's card, the shop bag and the counter).
 */
private fun FishStore.picture(): Int = when (this) {
    FishStore.SHELLYS -> R.drawable.store_shellys
    FishStore.FRAG_SHACK -> R.drawable.store_frag_shack
    FishStore.BIG_BLUE -> R.drawable.store_big_blue
    FishStore.DEEP_REEF -> R.drawable.store_deep_reef
}

private fun ShelfCard.kind(): SpeciesKind = when (group) {
    StockGroup.FISH -> SpeciesKind.FISH
    StockGroup.INVERTS -> SpeciesKind.INVERT
    StockGroup.CORALS -> SpeciesKind.CORAL
}

private val grey = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.15f) })

/** Four storefronts in a 2 × 2 grid; a shut store is dark with the level it opens at. */
@Composable
fun StoreStreet(fronts: List<StoreFrontUi>, onEnter: (FishStore) -> Unit, onShut: (FishStore) -> Unit) {
    val s = LiveTheme.style
    LiveHelp("Visit a fish store to buy new animals with your sand dollars.")
    fronts.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { f ->
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1.5f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.5.dp, Candy.navy, RoundedCornerShape(16.dp))
                        .clickable { if (f.open) onEnter(f.store) else onShut(f.store) },
                ) {
                    Image(
                        painterResource(f.store.picture()), f.store.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                        colorFilter = if (f.open) null else grey, alpha = if (f.open) 1f else 0.5f,
                    )
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0xCC030912)).padding(horizontal = 8.dp, vertical = 5.dp)) {
                        Text(f.store.label, style = s.buttonText.copy(fontSize = 13.sp), color = Color.White)
                    }
                    if (!f.open) Text(
                        "🔒 Opens at level ${f.store.level}", style = s.buttonText.copy(fontSize = 12.sp), color = Candy.yellow,
                        textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Inside a store. [tankPick] is the species of an animal just tapped in the display tank (null when none);
 * [onCheckout] pays for the bag and reports back so a refusal can be shown.
 */
@Composable
fun BoxScope.StoreOverlay(
    v: StoreVisitUi,
    tankPick: String?,
    onTank: (Int) -> Unit,
    onLeave: () -> Unit,
    onCheckout: (List<String>, (BuyResult) -> Unit) -> Unit,
    say: (String) -> Unit,
    tick: () -> Unit,
    topInset: androidx.compose.ui.unit.Dp,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    val s = LiveTheme.style
    val bag = remember(v.store) { mutableStateListOf<String>() }
    var picked by remember(v.store) { mutableStateOf<String?>(null) }
    var counter by remember(v.store) { mutableStateOf(false) }
    LaunchedEffect(tankPick) { if (tankPick != null) picked = tankPick }
    val (tank, cards) = v.tanks[v.at.coerceIn(0, v.tanks.size - 1)]
    val all = remember(v) { v.tanks.flatMap { it.second }.associateBy { it.speciesId } }
    val total = bag.sumOf { all[it]?.price ?: 0 }
    val add: (ShelfCard) -> Unit = { c ->
        if (!c.locked) {
            if (bag.count { it == c.speciesId } >= c.left) say("That is all the ${c.name} they have today.")
            else { bag += c.speciesId; tick() }
        }
    }
    val remove: (String) -> Unit = { id -> bag.lastIndexOf(id).takeIf { it >= 0 }?.let { bag.removeAt(it) } }

    // the sign, the bag and the section tabs along the top
    Column(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(top = topInset + 56.dp, start = 12.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LivePanel(contentPadding = PaddingValues(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LivePill("🚪", onLeave, tone = Candy.white)
                    Column {
                        Text(v.store.label, style = s.titleText.copy(fontSize = 17.sp), color = s.label)
                        Text("${tank.title} · ${tank.animals} animals", style = MaterialTheme.typography.labelSmall, color = s.labelDim)
                    }
                }
            }
            Box(Modifier.weight(1f))
            LivePill(
                if (bag.isEmpty()) "🛍️ 0" else "🛍️ ${bag.size} · $total",
                if (bag.isEmpty()) null else ({ tick(); counter = true }),
                tone = if (total > v.coins) Candy.red else Candy.yellow,
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            v.tanks.forEachIndexed { i, (t, _) -> LivePill(t.title, { picked = null; onTank(i) }, tone = Candy.cyan, selected = i == v.at) }
        }
    }
    // walk left and right between tanks
    if (v.tanks.size > 1) {
        LivePill("◀", { picked = null; onTank(v.at - 1) }, Modifier.align(Alignment.CenterStart).padding(start = 8.dp), tone = Candy.white)
        LivePill("▶", { picked = null; onTank(v.at + 1) }, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), tone = Candy.white)
    }
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = bottomInset + 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.firstOrNull { it.speciesId == picked }?.let { c ->
            ShelfCardPanel(c, bag.count { it == c.speciesId }, v.coins - total, { add(c) }, { remove(c.speciesId) }, { picked = null })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.forEach { c -> ShelfTile(c, bag.count { it == c.speciesId }, picked == c.speciesId) { tick(); picked = if (picked == c.speciesId) null else c.speciesId } }
        }
    }
    if (counter) Counter(bag, all, v.coins, total, { add(it) }, remove, { counter = false }) {
        onCheckout(bag.toList()) { r ->
            when (r) {
                BuyResult.OK -> Unit
                BuyResult.TOO_POOR -> say("Not enough sand dollars yet.")
                BuyResult.SOLD_OUT -> say("Someone bought the last one!")
                BuyResult.LEVEL_LOCKED -> say("That one needs a higher level.")
                else -> say("The till got stuck. Try again.")
            }
        }
        counter = false
    }
}

@Composable
private fun Price(n: Int, color: Color = Candy.yellow, size: Int = 14) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Canvas(Modifier.size((size + 2).dp)) { drawSandDollar() }
        Text("$n", style = LiveTheme.style.buttonText.copy(fontSize = size.sp), color = color)
    }
}

@Composable
private fun ShelfTile(c: ShelfCard, inBag: Int, on: Boolean, onTap: () -> Unit) {
    val s = LiveTheme.style
    Box {
        LivePanel(Modifier.width(104.dp), contentPadding = PaddingValues(6.dp), tint = if (on) Candy.yellow else null, onClick = onTap) {
            Box(Modifier.fillMaxWidth().alpha(if (c.locked) 0.5f else 1f), contentAlignment = Alignment.Center) {
                SpeciesAvatar(c.speciesId, c.kind(), emptyMap(), size = 52.dp)
            }
            Text(c.name, style = s.buttonText.copy(fontSize = 12.sp), color = s.label, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.fillMaxWidth().height(32.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (c.locked) Text("🔒 Lv ${c.level}", style = s.buttonText.copy(fontSize = 13.sp), color = Candy.yellow) else Price(c.price)
            }
        }
        val ribbon = if (c.deal) "Deal" to Candy.orange else if (c.wanted) "Wanted" to Candy.green else null
        ribbon?.let { (t, col) ->
            Text(t, style = s.buttonText.copy(fontSize = 11.sp), color = Candy.navy, modifier = Modifier.offset(6.dp, (-6).dp).clip(RoundedCornerShape(8.dp)).background(col).padding(horizontal = 6.dp, vertical = 1.dp))
        }
        if (inBag > 0) Text(
            "$inBag", style = s.buttonText.copy(fontSize = 12.sp), color = Candy.navy, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).size(22.dp).clip(CircleShape).background(Candy.green),
        )
    }
}

@Composable
private fun ShelfCardPanel(c: ShelfCard, inBag: Int, money: Int, onAdd: () -> Unit, onLess: () -> Unit, onClose: () -> Unit) {
    val s = LiveTheme.style
    LivePanel(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpeciesAvatar(c.speciesId, c.kind(), emptyMap(), size = 72.dp)
            Column(Modifier.weight(1f)) {
                Text(c.name, style = s.titleText.copy(fontSize = 18.sp), color = s.label)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Price(c.price, size = 18)
                    if (c.deal) Text("30% off today", style = MaterialTheme.typography.labelSmall, color = Candy.orange)
                    if (c.wanted) Text("Wanted here", style = MaterialTheme.typography.labelSmall, color = Candy.green)
                }
                Text("${c.left} in the shop today" + if (inBag > 0) " · $inBag in your bag" else "", style = MaterialTheme.typography.bodySmall, color = s.labelDim)
            }
            Text("✕", color = s.label, modifier = Modifier.clickable(onClick = onClose).padding(6.dp))
        }
        when {
            c.locked -> Text("🔒 Unlocks at level ${c.level}. Keep doing your care jobs!", style = MaterialTheme.typography.bodySmall, color = Candy.yellow)
            c.price > money -> Text("You need ${c.price - money} more sand dollars.", style = MaterialTheme.typography.bodySmall, color = s.labelDim)
        }
        c.warning?.let { Text("⚠️ $it", style = MaterialTheme.typography.bodySmall, color = Candy.orange) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            if (inBag > 0) LivePill("− One less", onLess, tone = Candy.white)
            val can = !c.locked && inBag < c.left
            LivePill(
                when { c.locked -> "Locked"; c.left <= 0 -> "Sold out"; inBag >= c.left -> "All in your bag"; else -> "Add to bag" },
                if (can) onAdd else null, tone = Candy.green, selected = can,
            )
        }
    }
}

@Composable
private fun Counter(
    bag: List<String>,
    all: Map<String, ShelfCard>,
    coins: Int,
    total: Int,
    onMore: (ShelfCard) -> Unit,
    onLess: (String) -> Unit,
    onClose: () -> Unit,
    onPay: () -> Unit,
) {
    val s = LiveTheme.style
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.widthIn(max = 420.dp).livePanel(s).background(s.panel.copy(alpha = 0.94f)).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("The counter", style = s.titleText, color = s.label)
            bag.distinct().forEach { id ->
                val c = all[id] ?: return@forEach
                val n = bag.count { it == id }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeciesAvatar(c.speciesId, c.kind(), emptyMap(), size = 40.dp)
                    Text(c.name, style = MaterialTheme.typography.bodyMedium, color = s.label, modifier = Modifier.weight(1f))
                    LivePill("−", { onLess(id) }, tone = Candy.white)
                    Text("$n", style = s.buttonText, color = s.label)
                    LivePill("+", { onMore(c) }, tone = Candy.white)
                    Price(c.price * n)
                }
            }
            if (bag.isEmpty()) LiveHelp("Your bag is empty.")
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Total", style = s.titleText.copy(fontSize = 18.sp), color = s.label, modifier = Modifier.weight(1f))
                Price(total, size = 18)
            }
            Text("You have $coins. After paying: ${maxOf(0, coins - total)}", style = MaterialTheme.typography.bodySmall, color = s.labelDim)
            val short = total - coins
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                LivePill("Keep shopping", onClose, tone = Candy.white)
                LivePill(if (short > 0) "Need $short more" else "Pay $total", if (short > 0 || bag.isEmpty()) null else onPay, tone = Candy.green, selected = short <= 0 && bag.isNotEmpty())
            }
        }
    }
}

package com.reeflive.app.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.GlassIconButton
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.SegmentedPills
import com.reeflive.app.ui.components.SpeciesAvatar
import com.reeflive.app.ui.components.rememberHaptics
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.Ink
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.StockGroup
import com.reeflive.core.sim.Stocking
import com.reeflive.core.species.SpeciesKind
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- state

/** One species card in the "Add life" gallery (a species with a 3D model, [Stocking.CATALOG]). */
data class StockCard(
    val speciesId: String,
    val name: String,
    val sizeCm: Double,
    val group: StockGroup,
    val colors: Map<String, String> = emptyMap(),
) {
    val kind: SpeciesKind get() = when (group) {
        StockGroup.FISH -> SpeciesKind.FISH
        StockGroup.INVERTS -> SpeciesKind.INVERT
        StockGroup.CORALS -> SpeciesKind.CORAL
    }
    val maxPerAdd: Int get() = if (group == StockGroup.CORALS) Stocking.MAX_CORALS_PER_ADD else Stocking.MAX_ANIMALS_PER_ADD
    /** Typical adult length (colony width for corals). */
    val sizeText: String get() = "≈ " + (if (sizeCm % 1.0 == 0.0) "${sizeCm.toInt()}" else "$sizeCm") + " cm"
}

/** Gallery data: the catalog cards plus what is in the sandbox right now. */
data class StockUi(
    val cards: List<StockCard> = emptyList(),
    /** Living animals / colonies per species id in the sandbox. */
    val counts: Map<String, Int> = emptyMap(),
    val animals: Int = 0,
    val corals: Int = 0,
) {
    val animalsFull: Boolean get() = animals >= Stocking.MAX_ANIMALS
    val coralsFull: Boolean get() = corals >= Stocking.MAX_CORALS

    companion object {
        /** Every addable species, in catalog order, with sheet colours from [colorsOf]. */
        fun catalogCards(colorsOf: (String) -> Map<String, String>): List<StockCard> =
            Stocking.CATALOG.map { StockCard(it.speciesId, it.common, it.typicalCm, it.group, colorsOf(it.speciesId)) }
    }
}

private fun plural(n: Int, one: String, many: String) = if (n == 1) "1 $one" else "$n $many"

// ---------------------------------------------------------------- gallery

/**
 * "Add life" tab of the sandbox dock: a gallery of every species the tank can draw. Tap a card to drop one into
 * the sandbox; hold it (or use the + on the card) to pick how many. Everything added is ordinary livestock in the
 * sandbox sim (stress, aggression, bioload), and Reset puts the tank back the way it is.
 */
@Composable
fun StockGallery(
    stock: StockUi,
    onAdd: (StockCard, Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ReefTheme.colors
    val haptics = rememberHaptics()
    var groupIdx by rememberSaveable { mutableStateOf(0) }
    var pickedId by rememberSaveable { mutableStateOf<String?>(null) }
    var qty by rememberSaveable { mutableStateOf(5) }
    // brief "+3 Blue tang" confirmation; quick taps on the same card add up
    var flashOn by remember { mutableStateOf(false) }
    var flashText by remember { mutableStateOf("") }
    var flashWarn by remember { mutableStateOf(false) }
    var flashSpecies by remember { mutableStateOf<String?>(null) }
    var flashCount by remember { mutableStateOf(0) }
    var flashKey by remember { mutableStateOf(0) }
    LaunchedEffect(flashKey) {
        if (flashOn) { delay(1800); flashOn = false; flashSpecies = null; flashCount = 0 }
    }
    val groups = StockGroup.values().toList()
    val group = groups[groupIdx.coerceIn(0, groups.size - 1)]
    val cards = stock.cards.filter { it.group == group }
    val picked = stock.cards.firstOrNull { it.speciesId == pickedId }

    val add: (StockCard, Int) -> Unit = { card, n ->
        val full = if (card.group == StockGroup.CORALS) stock.coralsFull else stock.animalsFull
        if (full) {
            flashText = if (card.group == StockGroup.CORALS) "Coral limit reached (${Stocking.MAX_CORALS} colonies)" else "Tank limit reached (${Stocking.MAX_ANIMALS} animals)"
            flashWarn = true; flashSpecies = null; flashCount = 0
        } else {
            haptics.tick()
            onAdd(card, n)
            flashCount = (if (flashOn && flashSpecies == card.speciesId) flashCount else 0) + n
            flashSpecies = card.speciesId
            flashText = "+$flashCount ${card.name}"
            flashWarn = false
        }
        flashOn = true
        flashKey++
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("In the tank now", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.semantics { heading() })
                Text(
                    "${plural(stock.animals, "animal", "animals")} · ${plural(stock.corals, "coral colony", "coral colonies")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (stock.animalsFull || stock.coralsFull) c.cautionText else c.textSecondary,
                )
            }
            ReefButton("Reset tank", onReset, kind = ButtonKind.OUTLINED, icon = Icons.Rounded.RestartAlt)
        }
        Text(
            "Tap a card to add one. Tap + (or hold the card) to add many. They eat, play, squabble and make the water " +
                "dirtier like real animals, so you can see what happens with too many. Only this sandbox changes.",
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
        )
        SegmentedPills(
            options = groups.map { g -> "${g.label} ${stock.cards.count { it.group == g }}" },
            selected = groupIdx,
            onSelect = { groupIdx = it; haptics.tick() },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Species group" },
            selectedColor = c.coralSoft,
        )
        AnimatedVisibility(flashOn, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(
                flashText,
                style = MaterialTheme.typography.labelLarge,
                color = if (flashWarn) c.cautionText else c.coralSoft,
                modifier = Modifier
                    .clip(ReefShapes.pill)
                    .background(c.glass)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        AnimatedVisibility(picked != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            picked?.let { p ->
                QuantityBar(
                    card = p,
                    qty = qty.coerceIn(1, p.maxPerAdd),
                    onQty = { qty = it.coerceIn(1, p.maxPerAdd); haptics.tick() },
                    onAdd = { add(p, qty.coerceIn(1, p.maxPerAdd)); pickedId = null },
                    onClose = { pickedId = null },
                )
            }
        }
        CardGrid(
            cards = cards,
            counts = stock.counts,
            picked = pickedId,
            onTap = { add(it, 1) },
            onMany = { pickedId = it.speciesId; qty = qty.coerceIn(1, it.maxPerAdd); haptics.confirm() },
        )
    }
}

@Composable
private fun CardGrid(
    cards: List<StockCard>,
    counts: Map<String, Int>,
    picked: String?,
    onTap: (StockCard) -> Unit,
    onMany: (StockCard) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / 112.dp).toInt().coerceIn(2, 5)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cards.chunked(cols).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { i, card ->
                        SpeciesCard(
                            card, counts[card.speciesId] ?: 0, card.speciesId == picked,
                            onTap = { onTap(card) }, onMany = { onMany(card) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            order = r * cols + i,
                        )
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SpeciesCard(card: StockCard, count: Int, picked: Boolean, onTap: () -> Unit, onMany: () -> Unit, modifier: Modifier, order: Int = 0) {
    val c = ReefTheme.colors
    val reduce = ReefTheme.reduceMotion
    // staggered entrance: each card rises and fades in a beat after the one before (first dozen only, so it stays quick)
    val appear = remember(card.speciesId) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(card.speciesId) {
        if (appear.value < 1f) {
            delay(order.coerceAtMost(12) * 28L)
            appear.animateTo(1f, ReefMotion.tween(false, ReefMotion.NORMAL))
        }
    }
    val pop = remember { Animatable(1f) }
    var lastCount by remember { mutableStateOf(count) }
    LaunchedEffect(count) {
        if (count > lastCount && !reduce) { pop.snapTo(1.35f); pop.animateTo(1f, ReefMotion.bouncy(false)) }
        lastCount = count
    }
    GlassCard(
        modifier = modifier
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 18.dp.toPx()
            }
            .semantics(mergeDescendants = true) {
            contentDescription = "${card.name}, ${card.sizeText}, $count in the tank. Tap to add one, hold to add several."
        },
        shape = ReefShapes.tile,
        tint = if (picked) c.coral else null,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
        onClick = onTap,
        onLongClick = onMany,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SpeciesAvatar(card.speciesId, card.kind, card.colors, size = 64.dp)
            if (count > 0) {
                Text(
                    "×$count",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ink.onCoral,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                        .clip(ReefShapes.pill)
                        .background(c.coral)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            card.name, style = MaterialTheme.typography.labelLarge, color = c.text, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(card.sizeText, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 1, modifier = Modifier.weight(1f))
            // visible "add many" affordance for people who don't long-press
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(c.glass)
                    .clickable(role = Role.Button, onClickLabel = "Add several ${card.name}", onClick = onMany),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add several", tint = c.coralSoft, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Hold-to-add-many panel: − / count / + stepper, quick amounts and the Add button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuantityBar(card: StockCard, qty: Int, onQty: (Int) -> Unit, onAdd: () -> Unit, onClose: () -> Unit) {
    val c = ReefTheme.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ReefShapes.tile,
        tint = c.coral,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpeciesAvatar(card.speciesId, card.kind, card.colors, size = 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("How many? Up to ${card.maxPerAdd} at a time", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
            GlassIconButton(Icons.Rounded.Close, "Close", onClose)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassIconButton(Icons.Rounded.Remove, "One fewer", { onQty(qty - 1) })
            Text(
                "$qty", style = MaterialTheme.typography.headlineSmall, color = c.text, textAlign = TextAlign.Center,
                modifier = Modifier.width(48.dp),
            )
            GlassIconButton(Icons.Rounded.Add, "One more", { onQty(qty + 1) })
            Spacer(Modifier.weight(1f))
            ReefButton("Add $qty", onAdd, kind = ButtonKind.CORAL, icon = Icons.Rounded.Add)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 20, card.maxPerAdd).distinct().filter { it <= card.maxPerAdd }.forEach { n ->
                ReefChip(
                    if (n == card.maxPerAdd) "Max $n" else "$n", selected = qty == n, onClick = { onQty(n) }, accent = c.coral,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- previews

private val previewStock = StockUi(
    cards = StockUi.catalogCards { id ->
        when (id) {
            "amphiprion_ocellaris" -> mapOf("primary" to "#FF7A1A", "secondary" to "#FFFFFF", "accent" to "#111111")
            "paracanthurus_hepatus" -> mapOf("primary" to "#2B5FD9", "secondary" to "#F4D01A", "accent" to "#0B0F1A")
            else -> emptyMap()
        }
    },
    counts = mapOf("amphiprion_ocellaris" to 2, "paracanthurus_hepatus" to 12),
    animals = 31, corals = 9,
)

@Preview(name = "Add life · Abyss", widthDp = 393, heightDp = 900, showBackground = true)
@Composable
private fun StockGalleryPreview() = ReefLiveTheme(darkTheme = true, reduceMotion = true) {
    Box(Modifier.background(ReefTheme.colors.bg).padding(16.dp)) { StockGallery(previewStock, { _, _ -> }, {}) }
}

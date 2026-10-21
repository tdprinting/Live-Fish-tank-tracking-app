package com.reeflive.app.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeflive.app.render.SupplyShelfLayout
import com.reeflive.app.render.TankProjection
import com.reeflive.app.ui.components.Glyph
import com.reeflive.app.ui.components.ReefGlyph
import com.reeflive.app.ui.components.ReefGlyphs
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.custom.economy.SupplyBook
import com.reeflive.core.sim.AiptasiaTreatment
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.MaintenanceTask
import com.reeflive.core.sim.WaterState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * The supply shelf (UI_STYLE §10.6): a 3D rack beside the tank (render/SupplyShelf.kt, supply_shelf.glb) that holds
 * one prop per thing the live menu used to list as tiles. The Supplies bubble swings the camera over to it; a drag
 * looks along the rows, a tap picks a prop (it lifts and glows in 3D, its name pops up here), then the camera glides
 * back to the tank and the action plays its usual in-tank picture (ActionFx) and Hermie line, the command going out
 * when the picture reaches the water. Sandbox-only props stand dimmed in Watch mode and only explain themselves.
 */

/** Shelf rows, top to bottom, in the menu's candy tones. */
enum class ShelfRow(val label: String, val glyph: ReefGlyph, val tone: Color) {
    FEED("Feed", ReefGlyphs.feedTin, Candy.orange),
    CARE("Care", ReefGlyphs.droplet, Candy.green),
    TOOLS("Tools", ReefGlyphs.microscope, Candy.cyan),
}

/**
 * One prop on the shelf. [id] is its node in supply_shelf.glb (`Supply_<id>`, tools/blender/supplies.py);
 * [sandboxOnly] props work only in the pretend tank (only Move rocks now; care props work in Watch too). [product] is
 * the real reef-keeping product the prop is modelled on (its packaging is printed on the 3D prop; supply_labels.py),
 * shown small under the name when the prop is picked; the name tag itself stays a short plain name.
 */
enum class SupplyItem(
    val id: String,
    val row: ShelfRow,
    private val title: String? = null,
    val sandboxOnly: Boolean = false,
    val food: FoodKind? = null,
    private val note: String? = null,
    val product: String = "",
) {
    FLAKE("flake", ShelfRow.FEED, food = FoodKind.FLAKE, product = "Ocean Nutrition Formula Two Flakes"),
    PELLET("pellet", ShelfRow.FEED, food = FoodKind.PELLET, product = "Ocean Nutrition Formula One Pellets"),
    FLOATING_PELLET("floating_pellet", ShelfRow.FEED, food = FoodKind.FLOATING_PELLET, product = "Hikari Marine S"),
    ALGAE_WAFER("algae_wafer", ShelfRow.FEED, food = FoodKind.ALGAE_WAFER, product = "Hikari Mini Algae Wafers"),
    MYSIS("mysis", ShelfRow.FEED, food = FoodKind.MYSIS, product = "Hikari Bio-Pure Mysis Shrimp"),
    BRINE("brine", ShelfRow.FEED, food = FoodKind.BRINE, product = "San Francisco Bay Brand Brine Shrimp"),
    KRILL("krill", ShelfRow.FEED, food = FoodKind.KRILL, product = "BRS Krill Superba"),
    PRAWN("prawn", ShelfRow.FEED, food = FoodKind.PRAWN, product = "Fresh frozen prawn"),
    CYCLOPS("cyclops", ShelfRow.FEED, food = FoodKind.CYCLOPS, product = "Hikari Bio-Pure Cyclops"),
    NORI("nori", ShelfRow.FEED, food = FoodKind.NORI, product = "Two Little Fishies Sea Veggies"),
    CORAL_FOOD("coral_food", ShelfRow.FEED, food = FoodKind.CORAL_FOOD, product = "Polyp Lab Reef-Roids"),
    PLANKTON("plankton", ShelfRow.FEED, food = FoodKind.PLANKTON, product = "Reef Nutrition Phyto-Feast"),
    PODS("pods", ShelfRow.FEED, food = FoodKind.PODS, product = "AlgaeBarn 5280 Pods"),

    WATER_CHANGE("water_change", ShelfRow.CARE, "Change water", note = "Mix fresh salt water and swap 25 %", product = "Red Sea Coral Pro Salt"),
    WATER_SMALL("water_small", ShelfRow.CARE, "Small change", note = "Mix a little salt water and swap 10 %", product = "Instant Ocean Sea Salt"),
    DOSE_ALK("dose_alk", ShelfRow.CARE, "Shell builder", note = "Alkalinity +1 for coral skeletons", product = "Red Sea Reef Foundation B"),
    DOSE_CA("dose_ca", ShelfRow.CARE, "Calcium", note = "Calcium +20 for corals and coralline", product = "Red Sea Reef Foundation A"),
    DOSE_MG("dose_mg", ShelfRow.CARE, "Magnesium", note = "Magnesium +50 keeps calcium in balance", product = "Red Sea Reef Foundation C"),
    NEW_SOCK("new_sock", ShelfRow.CARE, "New sock", note = "Swap the dirty filter sock for a clean felt one", product = "BRS felt filter sock"),
    FRESH_CARBON("fresh_carbon", ShelfRow.CARE, "Fresh carbon", note = "New carbon keeps the water clear", product = "Seachem Matrix Carbon"),
    TRIM_ALGAE("trim_algae", ShelfRow.CARE, "Trim algae", note = "Snip the algae garden", product = "Curved aquascaping scissors"),
    ZAP_PEST("zap_pest", ShelfRow.CARE, "Zap pest", note = "Get rid of a pest anemone", product = "Red Sea Aiptasia-X"),
    SETTLE_IN("settle_in", ShelfRow.CARE, "Settle in", note = "Everyone gets used to the water now", product = "Drip acclimation kit"),

    GLASS_CLEANER("glass_cleaner", ShelfRow.TOOLS, "Clean glass", note = "Slide the Flipper over the front glass", product = "Flipper Edge magnet"),
    TEST_KIT("test_kit", ShelfRow.TOOLS, "Test kit", note = "Read the water", product = "API Reef Master + Salifert KH"),
    MICROSCOPE("microscope", ShelfRow.TOOLS, "Look closer", note = "The tiny life in the tank", product = "Student microscope"),
    KNOCKER("knocker", ShelfRow.TOOLS, "Knock glass", note = "Tap tap! Who comes to look?", product = "Rubber mallet"),
    MOVE_ROCKS("move_rocks", ShelfRow.TOOLS, "Move rocks", true, note = "Shuffle the rockwork", product = "CaribSea LifeRock"),
    JOES_JUICE("joes_juice", ShelfRow.TOOLS, "Joe's Juice", note = "Feed a pest anemone its last meal", product = "Blue Life Joe's Juice"),
    KALK_PASTE("kalk_paste", ShelfRow.TOOLS, "Kalk paste", note = "Home-made pest paste in a syringe", product = "Kalkwasser (pickling lime) paste"),
    PEST_LASER("pest_laser", ShelfRow.TOOLS, "Pest laser", note = "Zap a pest anemone with light", product = "Aiptasia laser pen"),
    ;

    val label: String get() = food?.label ?: title.orEmpty()

    /** The short name on the prop's tag under it on the shelf (two lines at most on a small phone). */
    val tag: String get() = when (this) {
        PLANKTON -> "Phyto plankton"
        MYSIS -> "Mysis"
        PRAWN -> "Prawn"
        NORI -> "Nori"
        WATER_CHANGE -> "Big water change"
        WATER_SMALL -> "Small water change"
        DOSE_ALK -> "Alkalinity"
        GLASS_CLEANER -> "Glass cleaner"
        ZAP_PEST -> "Aiptasia-X"
        else -> label
    }

    /** Position along its row, left to right (the order of the props in supply_shelf.glb). */
    val slot: Int get() = SupplyItem.entries.count { it.row == row && it.ordinal < ordinal }
    val blurb: String get() = food?.blurb ?: note.orEmpty()

    /** The picture the carried prop shows (foods draw their own [FoodPicture]). */
    val glyph: ReefGlyph get() = when (this) {
        WATER_CHANGE -> ReefGlyphs.bucket
        WATER_SMALL -> ReefGlyphs.droplet
        DOSE_ALK -> ReefGlyphs.shell
        DOSE_CA -> ReefGlyphs.branch
        DOSE_MG -> ReefGlyphs.rings
        NEW_SOCK -> ReefGlyphs.sock
        FRESH_CARBON -> ReefGlyphs.carbon
        TRIM_ALGAE -> ReefGlyphs.refugium
        ZAP_PEST -> ReefGlyphs.pestAnemone
        SETTLE_IN -> ReefGlyphs.heart
        GLASS_CLEANER -> ReefGlyphs.flipper
        TEST_KIT -> ReefGlyphs.flask
        MICROSCOPE -> ReefGlyphs.microscope
        KNOCKER -> ReefGlyphs.touch
        MOVE_ROCKS -> ReefGlyphs.rocks
        JOES_JUICE, KALK_PASTE, PEST_LASER -> ReefGlyphs.pestAnemone
        else -> ReefGlyphs.feedTin
    }

    /**
     * What the prop changes in the tank and by how much, shown under its blurb in the pick label (docs/research/
     * shelf_audit.md). Foods read theirs off the sim's own numbers (FoodKind.properties: sink / float, who eats it,
     * waste); the care props and tools name the sim effect they drive.
     */
    val properties: String get() = food?.properties ?: when (this) {
        WATER_CHANGE -> "Swaps 25 %: nitrate and phosphate −25 %. Coral Pro mixes rich: alk about +1 dKH, Ca +6, Mg +18"
        WATER_SMALL -> "Swaps 10 %: nitrate and phosphate −10 %. Instant Ocean is close to sea water: minerals barely move"
        DOSE_ALK -> "Alkalinity +1 dKH over 10 min. More than 1 dKH a day stresses corals"
        DOSE_CA -> "Calcium +20 ppm over 20 min. With high alk it falls out as white crust"
        DOSE_MG -> "Magnesium +50 ppm. Holds calcium and alk in the water (no crust)"
        NEW_SOCK -> "Fits a clean felt sock: traps drifting muck. Left in past 2-3 days it rots back to ammonia"
        FRESH_CARBON -> "Fresh carbon: clears yellow water and coral toxins in a day. Slowly strips iodine too"
        TRIM_ALGAE -> "Cuts the red macro by a third and the refugium by half: the nitrate in it leaves too"
        ZAP_PEST -> "Injected into the biggest open pest anemone: it shrinks away in about 5 minutes. A closed one often escapes"
        JOES_JUICE -> "Fed over a pest anemone's mouth: gone in about 15 minutes. A bit more likely to leave a baby behind"
        KALK_PASTE -> "Cheap lime paste: kills in about half an hour, but 1 in 5 drops a baby. Keep it off the corals"
        PEST_LASER -> "A second of light: the pest shrivels at once and rarely splits. It can duck into the rock first"
        SETTLE_IN -> "Resets everyone's comfort point to today's water: shock from swings fades"
        GLASS_CLEANER -> "Wipes the algae film off the front glass where you drag"
        TEST_KIT -> "Reads KH, Ca, NO3, PO4 like a real kit: colour cards round, drops are ±1"
        MICROSCOPE -> "Pods, worms and plankton living in the rock, sand and water"
        KNOCKER -> "A hard knock: nearby fish startle and hide, and stress a little"
        MOVE_ROCKS -> "Territories reset for a day (more squabbles); can tear a pest anemone in two"
        else -> ""
    }

    /** The salt a water-change prop mixes (its product): Coral Pro for the big change, Instant Ocean for the small. */
    val saltMix: WaterState? get() = when (this) {
        WATER_CHANGE -> WaterState.CORAL_PRO_MIX
        WATER_SMALL -> WaterState.INSTANT_OCEAN_MIX
        else -> null
    }

    /** Share of the water a water-change prop swaps. */
    val changeFraction: Double get() = when (this) {
        WATER_CHANGE -> 0.25
        WATER_SMALL -> 0.10
        else -> 0.0
    }

    /** The salt's short name for the banner ("Coral Pro"). */
    val saltName: String get() = when (this) {
        WATER_CHANGE -> "Coral Pro"
        WATER_SMALL -> "Instant Ocean"
        else -> ""
    }

    /** The prop stays on the shelf when picked (it shows something there) instead of taking you back to the tank. */
    val staysOnShelf: Boolean get() = this == TEST_KIT

    fun locked(sandbox: Boolean): Boolean = sandboxOnly && !sandbox

    /**
     * Sends this supply's action through the same paths as the old menu tiles, so it plays the same in-tank picture
     * and Hermie line ([SandboxActions] cue them). The glass cleaner enters clean-glass mode like the old button. The
     * water changes go with their own salt ([saltMix]).
     */
    fun use(actions: SandboxActions, tools: LiveTools) {
        food?.let { actions.onFeed(it); return }
        when (this) {
            WATER_CHANGE, WATER_SMALL -> actions.onSaltChange(changeFraction, saltMix ?: WaterState.FRESH_MIX, saltName)
            DOSE_ALK -> actions.onDose("alk", 1.0)
            DOSE_CA -> actions.onDose("ca", 20.0)
            DOSE_MG -> actions.onDose("mg", 50.0)
            NEW_SOCK -> actions.onMaintain(MaintenanceTask.CHANGE_SOCK)
            FRESH_CARBON -> actions.onMaintain(MaintenanceTask.REPLACE_CARBON)
            TRIM_ALGAE -> actions.onMaintain(MaintenanceTask.HARVEST_MACRO)
            ZAP_PEST -> actions.onTreatAiptasia(AiptasiaTreatment.AIPTASIA_X)
            JOES_JUICE -> actions.onTreatAiptasia(AiptasiaTreatment.JOES_JUICE)
            KALK_PASTE -> actions.onTreatAiptasia(AiptasiaTreatment.KALK_PASTE)
            PEST_LASER -> actions.onTreatAiptasia(AiptasiaTreatment.LASER)
            SETTLE_IN -> actions.onAcclimateAll()
            GLASS_CLEANER -> if (!tools.cleaning) tools.onCleanGlass()
            MICROSCOPE -> tools.onMicroscope()
            KNOCKER -> actions.onTapGlass()
            MOVE_ROCKS -> actions.onRearrangeRock()
            else -> Unit
        }
    }

    companion object {
        fun byId(id: String): SupplyItem? = entries.firstOrNull { it.id == id }

        /** Prop ids drawn dimmed: the sandbox-only ones while watching the real tank. */
        fun lockedIds(sandbox: Boolean): Set<String> = if (sandbox) emptySet() else entries.filter { it.sandboxOnly }.map { it.id }.toSet()

        /**
         * Uses of [id] left on My tank's shelf ([stock] is the economy's counts; null outside My tank), or
         * [SupplyBook.UNLIMITED] when it never runs out.
         */
        fun left(stock: Map<String, Int>?, id: String): Int =
            if (stock == null || SupplyBook.pack(id) == null) SupplyBook.UNLIMITED else stock[id] ?: 0

        /** Props that have run out in My tank: taken off the shelf until more are bought. */
        fun emptyIds(stock: Map<String, Int>?): Set<String> =
            if (stock == null) emptySet() else entries.filter { left(stock, it.id) == 0 }.map { it.id }.toSet()
    }
}

/** A prop the user just tapped: [at] is the view pixel above it (null if the camera could not project it). */
@Immutable
data class ShelfPick(val item: SupplyItem, val at: Offset?, val locked: Boolean, val serial: Int, val out: Boolean = false)

/**
 * The Supplies overlay, laid over the full-screen tank view while the camera looks at the shelf: a name tag under every
 * prop and a header on every row (anchored to the shelf in 3D, so they follow the camera; tapping one picks it like
 * tapping the prop), the picked prop's label above it, the first-use hint, the test-kit readouts, and the row jumps
 * with "Back to tank" (a bottom bar in portrait, a column at the side in landscape so the rows stay in view).
 * [bottomClearance] keeps it above the app's bottom bar.
 */
@Composable
fun BoxScope.SupplyShelfOverlay(
    visible: Boolean,
    pick: ShelfPick?,
    readouts: List<WaterReadout>,
    showReadouts: Boolean,
    projection: TankProjection,
    locked: Set<String>,
    /** Props that have run out in My tank: not on the shelf, so no tag either. */
    hidden: Set<String>,
    /** My tank's shelf counts (null outside My tank: nothing runs out). */
    stock: Map<String, Int>?,
    showHint: Boolean,
    landscape: Boolean,
    onTag: (SupplyItem) -> Unit,
    onRow: (ShelfRow) -> Unit,
    onClose: () -> Unit,
    bottomClearance: Dp,
) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    // name tags and row headers: they fade in once the camera has (nearly) arrived at the shelf
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(if (reduce) 0 else ReefMotion.SLOW, delayMillis = if (reduce) 0 else TAG_DELAY_MS)),
        exit = fadeOut(tween(if (reduce) 0 else ReefMotion.FAST)),
    ) {
        ShelfTagLayer(projection, locked, hidden, stock, pick?.item, onTag, onRow, sideClear = if (landscape) SIDE_COLUMN else 0.dp)
    }
    // the picked prop's name, floating above it (same bounds as the GL view, so view pixels are layout pixels)
    AnimatedVisibility(
        visible = visible && pick != null,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(liveSpec(reduce)) + scaleIn(liveSpec(reduce, ReefMotion.SLOW), initialScale = 0.7f, transformOrigin = TransformOrigin(0.5f, 1f)),
        exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)),
    ) {
        val p = pick ?: return@AnimatedVisibility
        Box(Modifier.fillMaxSize()) {
            ShelfLabel(
                p,
                Modifier.layout { m, c ->
                    val placeable = m.measure(c.copy(minWidth = 0, minHeight = 0))
                    val w = if (c.hasBoundedWidth) c.maxWidth else placeable.width
                    val h = if (c.hasBoundedHeight) c.maxHeight else placeable.height
                    layout(w, h) {
                        val ax = p.at?.x ?: (w / 2f)
                        val ay = p.at?.y ?: (h * 0.3f)
                        val x = (ax - placeable.width / 2f).roundToInt().coerceIn(0, (w - placeable.width).coerceAtLeast(0))
                        val y = (ay - placeable.height - 8.dp.toPx()).roundToInt().coerceIn(0, (h - placeable.height).coerceAtLeast(0))
                        placeable.place(x, y)
                    }
                },
            )
        }
    }
    if (landscape) {
        // landscape: a slim column at the side, so the short screen keeps the rows of the shelf in view
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, top = 12.dp, bottom = 12.dp),
            enter = fadeIn(liveSpec(reduce)) + slideInHorizontally(liveSpec(reduce)) { it / 2 },
            exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + slideOutHorizontally(liveSpec(reduce)) { it / 2 },
        ) {
            Column(
                Modifier.width(SIDE_COLUMN - 12.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LivePill("Back to tank", onClose, tone = Candy.white)
                ShelfRow.entries.forEach { r ->
                    LivePill(r.label, { onRow(r) }, tone = r.tone, leading = { Glyph(r.glyph, size = 18.dp, tint = s.onCandy) })
                }
            }
        }
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp, end = SIDE_COLUMN).widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShelfHint(visible && showHint && pick == null)
            AnimatedVisibility(visible && showReadouts && readouts.isNotEmpty(), enter = fadeIn(liveSpec(reduce)) + scaleIn(liveSpec(reduce), initialScale = 0.9f), exit = fadeOut(liveSpec(reduce, ReefMotion.FAST))) {
                WaterStrip(readouts)
            }
        }
        return
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomClearance).padding(horizontal = 12.dp),
        enter = fadeIn(liveSpec(reduce)) + slideInVertically(liveSpec(reduce)) { it / 2 },
        exit = fadeOut(liveSpec(reduce, ReefMotion.FAST)) + slideOutVertically(liveSpec(reduce)) { it / 2 },
    ) {
        Column(
            Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShelfHint(showHint && pick == null)
            AnimatedVisibility(showReadouts && readouts.isNotEmpty(), enter = fadeIn(liveSpec(reduce)) + scaleIn(liveSpec(reduce), initialScale = 0.9f), exit = fadeOut(liveSpec(reduce, ReefMotion.FAST))) {
                WaterStrip(readouts)
            }
            LivePanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Supply shelf", style = s.titleText, color = s.label, modifier = Modifier.semantics { heading() })
                        LiveHelp(
                            if (stock == null) "Drag to look along the shelf. Tap a supply or its name tag to use it."
                            else "Tap a supply to use it. What runs out leaves the shelf: buy more at the fish stores.",
                        )
                    }
                    LivePill("Back to tank", onClose, tone = Candy.white)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ShelfRow.entries.forEach { r ->
                        LivePill(r.label, { onRow(r) }, tone = r.tone, leading = { Glyph(r.glyph, size = 18.dp, tint = s.onCandy) })
                    }
                }
            }
        }
    }
}

/** The first-use coach line ("tap, then drag"), until a supply has been carried into the tank once. */
@Composable
private fun ShelfHint(visible: Boolean) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
    AnimatedVisibility(visible, enter = fadeIn(liveSpec(reduce)), exit = fadeOut(liveSpec(reduce, ReefMotion.FAST))) {
        LivePanel(
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            tint = Candy.yellow,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Glyph(ReefGlyphs.touch, size = 22.dp, tint = s.label)
                Text("Tap a supply, then drag it to the tank", style = s.buttonText.copy(fontSize = 14.sp), color = s.label)
            }
        }
    }
}

/** Width the landscape side column (row jumps, "Back to tank") keeps clear on the right. */
private val SIDE_COLUMN = 148.dp
/** The tags wait for most of the camera's glide to the shelf (Camera.SHELF_TRAVEL_S) before fading in. */
private const val TAG_DELAY_MS = 650
/** A tag hangs from the row's front lip: its top this far below the board top, and this far in front of the lip. */
private const val TAG_DROP_CM = 0.7f
private const val TAG_OUT_CM = 0.8f
/** Row headers hang under the board above their row, this far below it (cm). */
private const val HEADER_DROP_CM = 0.6f

/**
 * Every prop's name tag and every row's header, placed each frame where the shelf is on screen: a tag is as wide as
 * its prop's slot on the board (so neighbours never overlap) and hangs from the board's front lip under the prop; a
 * row header hangs under the board above its row at the row's left end, and stays on screen (sticky at the left)
 * while the camera looks further along. Tapping a tag picks its prop; tapping a header looks at that row.
 * [sideClear] is kept free on the right (the landscape side column).
 */
@Composable
private fun ShelfTagLayer(
    projection: TankProjection,
    locked: Set<String>,
    hidden: Set<String>,
    stock: Map<String, Int>?,
    picked: SupplyItem?,
    onTag: (SupplyItem) -> Unit,
    onRow: (ShelfRow) -> Unit,
    sideClear: Dp,
) {
    // a frame tick read only while placing, so the tags follow the camera without recomposing
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { frame = it } }
    // the feed row's slot width on screen (dp), sampled a few times a second: it picks the tag text size
    val density = LocalDensity.current.density
    var feedSlotDp by remember { mutableFloatStateOf(80f) }
    LaunchedEffect(projection, density) {
        val a = FloatArray(3)
        val b = FloatArray(3)
        val half = SupplyShelfLayout.slotWidth(SupplyShelfLayout.ROW_COUNT[0]) / 2f
        while (true) {
            val y = SupplyShelfLayout.ROW_Y[0]
            if (projection.project(SupplyShelfLayout.START_X - half, y, SupplyShelfLayout.Z_FRONT, a) &&
                projection.project(SupplyShelfLayout.START_X + half, y, SupplyShelfLayout.Z_FRONT, b)
            ) {
                val dp = abs(b[0] - a[0]) / density
                if (abs(dp - feedSlotDp) > 3f) feedSlotDp = dp
            }
            delay(300)
        }
    }
    val items = SupplyItem.entries
    val rows = ShelfRow.entries
    val a = remember { FloatArray(3) }
    val b = remember { FloatArray(3) }
    Layout(
        content = {
            items.forEach { item ->
                val slotDp = feedSlotDp * SupplyShelfLayout.ROW_COUNT[0] / SupplyShelfLayout.ROW_COUNT[item.row.ordinal]
                ShelfTag(item, item.id in locked, item == picked, small = slotDp < 70f, left = SupplyItem.left(stock, item.id)) { onTag(item) }
            }
            rows.forEach { r -> RowHeader(r) { onRow(r) } }
        },
        modifier = Modifier.fillMaxSize(),
    ) { ms, c ->
        frame
        val w = c.maxWidth
        val h = c.maxHeight
        val gap = 4.dp.toPx()
        val minW = 30.dp.roundToPx()
        val maxW = 150.dp.roundToPx()
        val left = 8.dp.roundToPx()
        val right = w - sideClear.roundToPx()
        val placed = arrayOfNulls<Placeable>(ms.size)
        val xs = IntArray(ms.size)
        val ys = IntArray(ms.size)
        items.forEachIndexed { i, item ->
            if (item.id in hidden) return@forEachIndexed
            val r = item.row.ordinal
            val n = SupplyShelfLayout.ROW_COUNT[r]
            val cx = SupplyShelfLayout.propX(item.slot, n)
            val half = SupplyShelfLayout.slotWidth(n) / 2f
            val y = SupplyShelfLayout.ROW_Y[r] - TAG_DROP_CM
            val z = SupplyShelfLayout.Z_FRONT + TAG_OUT_CM
            if (projection.project(cx - half, y, z, a) && projection.project(cx + half, y, z, b)) {
                val tw = ((b[0] - a[0]) - gap).roundToInt()
                if (tw >= minW) {
                    val cw = tw.coerceAtMost(maxW)
                    val p = ms[i].measure(Constraints(minWidth = cw, maxWidth = cw))
                    val x = ((a[0] + b[0]) / 2f - cw / 2f).roundToInt()
                    val top = ((a[1] + b[1]) / 2f).roundToInt()
                    if (x + cw > 0 && x < right && top < h && top + p.height > 0) { placed[i] = p; xs[i] = x; ys[i] = top }
                }
            }
        }
        rows.forEachIndexed { k, _ ->
            val i = items.size + k
            val y = (if (k == 0) SupplyShelfLayout.TOP_Y else SupplyShelfLayout.ROW_Y[k - 1] - SupplyShelfLayout.BOARD) - HEADER_DROP_CM
            if (projection.project(SupplyShelfLayout.SLOT_X0, y, SupplyShelfLayout.Z_FRONT, a) &&
                projection.project(SupplyShelfLayout.SLOT_X1, y, SupplyShelfLayout.Z_FRONT, b)
            ) {
                val p = ms[i].measure(Constraints())
                val x = max(a[0].roundToInt(), left)
                val top = a[1].roundToInt()
                // sticky at the left edge, gone once the row's far end has passed it
                if (x + p.width < b[0] && x < right && top < h && top + p.height > 0) { placed[i] = p; xs[i] = x; ys[i] = top }
            }
        }
        layout(w, h) {
            for (i in placed.indices) placed[i]?.place(xs[i], ys[i])
        }
    }
}

/** A prop's name tag: a candy price tag in its row's tone with the navy outline (faded for sandbox-only props). */
@Composable
private fun ShelfTag(item: SupplyItem, locked: Boolean, picked: Boolean, small: Boolean, left: Int, onClick: () -> Unit) {
    val s = LiveTheme.style
    val shape = RoundedCornerShape(7.dp)
    val fill = if (locked) Color(0xFFE9F3FF).copy(alpha = 0.72f) else item.row.tone
    Box {
        Box(
            Modifier
                .clip(shape)
                .background(fill)
                .border(if (picked) 3.dp else 2.dp, if (picked) Candy.yellow else s.outline, shape)
                .clickable(onClickLabel = "Use ${item.label}", role = Role.Button, onClick = onClick)
                .padding(horizontal = 3.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                item.tag,
                style = s.buttonText.copy(fontSize = if (small) 10.sp else 12.sp, lineHeight = if (small) 11.sp else 14.sp, letterSpacing = 0.sp),
                color = if (locked) s.onCandy.copy(alpha = 0.55f) else s.onCandy,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // what is left on My tank's shelf: a count, "Shop" when it has run out (a kept tool shows nothing once bought)
        val keeps = SupplyBook.pack(item.id)?.keeps == true
        if (left == 0 || (left > 0 && !keeps)) Text(
            if (left == 0) "Shop" else "×$left",
            style = s.buttonText.copy(fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.sp),
            color = Candy.navy,
            modifier = Modifier.align(Alignment.TopEnd).offset(4.dp, (-8).dp).clip(RoundedCornerShape(50))
                .background(if (left == 0) Candy.pink else if (left <= 5) Candy.yellow else Color.White)
                .border(1.5.dp, s.outline, RoundedCornerShape(50)).padding(horizontal = 4.dp),
        )
    }
}

/** A row's header chip (Feed / Care / Tools) in its candy tone. */
@Composable
private fun RowHeader(r: ShelfRow, onClick: () -> Unit) {
    val s = LiveTheme.style
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(r.tone)
            .border(2.dp, s.outline, shape)
            .clickable(onClickLabel = "Look at ${r.label}", role = Role.Button, onClick = onClick)
            .semantics { heading() }
            .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Glyph(r.glyph, size = 18.dp, tint = s.onCandy)
        Text(r.label, style = s.buttonText.copy(fontSize = 15.sp), color = s.onCandy)
    }
}

/** The label that pops up over a picked prop: name, one line about it, and a lock note for sandbox-only props. */
@Composable
private fun ShelfLabel(p: ShelfPick, modifier: Modifier = Modifier) {
    val s = LiveTheme.style
    val item = p.item
    LivePanel(
        modifier.widthIn(max = 240.dp).semantics { liveRegion = LiveRegionMode.Polite },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        tint = if (p.locked || p.out) Candy.purple else item.row.tone,
    ) {
        Text(item.label, style = s.buttonText.copy(fontSize = 15.sp), color = s.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (item.product.isNotEmpty()) {
            Text(item.product, style = s.buttonText.copy(fontSize = 11.sp, lineHeight = 13.sp), color = s.labelDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        val line = when {
            p.locked -> "Pretend tank only: switch to Sandbox to use it"
            p.out -> "All gone! Buy more at the fish stores: tap Shop."
            else -> item.blurb
        }
        if (line.isNotEmpty()) {
            Text(line, style = s.buttonText.copy(fontSize = 12.sp, lineHeight = 15.sp), color = s.labelDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        // what it changes and by how much (the sim effect behind the prop), set apart by a thin rule
        if (item.properties.isNotEmpty()) {
            Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(s.labelDim.copy(alpha = 0.25f)))
            Text(item.properties, style = s.buttonText.copy(fontSize = 11.sp, lineHeight = 14.sp), color = s.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

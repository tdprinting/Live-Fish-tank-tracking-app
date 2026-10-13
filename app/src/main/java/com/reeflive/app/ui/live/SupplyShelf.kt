package com.reeflive.app.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeflive.app.ui.components.Glyph
import com.reeflive.app.ui.components.ReefGlyph
import com.reeflive.app.ui.components.ReefGlyphs
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.MaintenanceTask
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
 * [sandboxOnly] props work only in the pretend tank (as their tiles did in the old menu).
 */
enum class SupplyItem(
    val id: String,
    val row: ShelfRow,
    private val title: String? = null,
    val sandboxOnly: Boolean = false,
    val food: FoodKind? = null,
    private val note: String? = null,
) {
    FLAKE("flake", ShelfRow.FEED, food = FoodKind.FLAKE),
    PELLET("pellet", ShelfRow.FEED, food = FoodKind.PELLET),
    FLOATING_PELLET("floating_pellet", ShelfRow.FEED, food = FoodKind.FLOATING_PELLET),
    ALGAE_WAFER("algae_wafer", ShelfRow.FEED, food = FoodKind.ALGAE_WAFER),
    MYSIS("mysis", ShelfRow.FEED, food = FoodKind.MYSIS),
    BRINE("brine", ShelfRow.FEED, food = FoodKind.BRINE),
    KRILL("krill", ShelfRow.FEED, food = FoodKind.KRILL),
    PRAWN("prawn", ShelfRow.FEED, food = FoodKind.PRAWN),
    CYCLOPS("cyclops", ShelfRow.FEED, food = FoodKind.CYCLOPS),
    NORI("nori", ShelfRow.FEED, food = FoodKind.NORI),
    CORAL_FOOD("coral_food", ShelfRow.FEED, food = FoodKind.CORAL_FOOD),
    PLANKTON("plankton", ShelfRow.FEED, food = FoodKind.PLANKTON),
    PODS("pods", ShelfRow.FEED, food = FoodKind.PODS),

    WATER_CHANGE("water_change", ShelfRow.CARE, "Change water", true, note = "Swap 25 % for fresh salt water"),
    WATER_SMALL("water_small", ShelfRow.CARE, "Small change", true, note = "Swap 10 % of the water"),
    DOSE_ALK("dose_alk", ShelfRow.CARE, "Shell builder", true, note = "Alkalinity +1 for coral skeletons"),
    DOSE_CA("dose_ca", ShelfRow.CARE, "Calcium", true, note = "Calcium +20"),
    DOSE_MG("dose_mg", ShelfRow.CARE, "Magnesium", true, note = "Magnesium +50"),
    NEW_SOCK("new_sock", ShelfRow.CARE, "New sock", true, note = "Swap the dirty filter sock"),
    FRESH_CARBON("fresh_carbon", ShelfRow.CARE, "Fresh carbon", true, note = "New carbon keeps the water clear"),
    TRIM_ALGAE("trim_algae", ShelfRow.CARE, "Trim algae", true, note = "Snip the algae garden"),
    ZAP_PEST("zap_pest", ShelfRow.CARE, "Zap pest", true, note = "Get rid of a pest anemone"),
    SETTLE_IN("settle_in", ShelfRow.CARE, "Settle in", true, note = "Everyone gets used to the water now"),

    GLASS_CLEANER("glass_cleaner", ShelfRow.TOOLS, "Clean glass", note = "Slide the Flipper over the front glass"),
    TEST_KIT("test_kit", ShelfRow.TOOLS, "Test kit", note = "Read the water"),
    MICROSCOPE("microscope", ShelfRow.TOOLS, "Look closer", note = "The tiny life in the tank"),
    KNOCKER("knocker", ShelfRow.TOOLS, "Knock glass", note = "Tap tap! Who comes to look?"),
    MOVE_ROCKS("move_rocks", ShelfRow.TOOLS, "Move rocks", true, note = "Shuffle the rockwork"),
    ;

    val label: String get() = food?.label ?: title.orEmpty()
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
        else -> ReefGlyphs.feedTin
    }

    /** The prop stays on the shelf when picked (it shows something there) instead of taking you back to the tank. */
    val staysOnShelf: Boolean get() = this == TEST_KIT

    fun locked(sandbox: Boolean): Boolean = sandboxOnly && !sandbox

    /**
     * Sends this supply's action through the same paths as the old menu tiles, so it plays the same in-tank picture
     * and Hermie line ([SandboxActions] cue them). The glass cleaner enters clean-glass mode like the old button.
     */
    fun use(actions: SandboxActions, tools: LiveTools) {
        food?.let { actions.onFeed(it); return }
        when (this) {
            WATER_CHANGE -> actions.onWaterChange(0.25)
            WATER_SMALL -> actions.onWaterChange(0.10)
            DOSE_ALK -> actions.onDose("alk", 1.0)
            DOSE_CA -> actions.onDose("ca", 20.0)
            DOSE_MG -> actions.onDose("mg", 50.0)
            NEW_SOCK -> actions.onMaintain(MaintenanceTask.CHANGE_SOCK)
            FRESH_CARBON -> actions.onMaintain(MaintenanceTask.REPLACE_CARBON)
            TRIM_ALGAE -> actions.onMaintain(MaintenanceTask.HARVEST_MACRO)
            ZAP_PEST -> actions.onRemoveAiptasia()
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
    }
}

/** A prop the user just tapped: [at] is the view pixel above it (null if the camera could not project it). */
@Immutable
data class ShelfPick(val item: SupplyItem, val at: Offset?, val locked: Boolean, val serial: Int)

/**
 * The Supplies overlay, laid over the full-screen tank view while the camera looks at the shelf: the picked prop's
 * label above it, the test-kit readouts, and a bottom bar with the row jumps and "Back to tank". [bottomClearance]
 * keeps it above the app's bottom bar.
 */
@Composable
fun BoxScope.SupplyShelfOverlay(
    visible: Boolean,
    pick: ShelfPick?,
    readouts: List<WaterReadout>,
    showReadouts: Boolean,
    onRow: (ShelfRow) -> Unit,
    onClose: () -> Unit,
    bottomClearance: Dp,
) {
    val s = LiveTheme.style
    val reduce = ReefTheme.reduceMotion
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
            AnimatedVisibility(showReadouts && readouts.isNotEmpty(), enter = fadeIn(liveSpec(reduce)) + scaleIn(liveSpec(reduce), initialScale = 0.9f), exit = fadeOut(liveSpec(reduce, ReefMotion.FAST))) {
                WaterStrip(readouts)
            }
            LivePanel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Supply shelf", style = s.titleText, color = s.label, modifier = Modifier.semantics { heading() })
                        LiveHelp("Drag to look along the shelf. Tap a supply to use it.")
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

/** The label that pops up over a picked prop: name, one line about it, and a lock note for sandbox-only props. */
@Composable
private fun ShelfLabel(p: ShelfPick, modifier: Modifier = Modifier) {
    val s = LiveTheme.style
    val item = p.item
    LivePanel(
        modifier.widthIn(max = 240.dp).semantics { liveRegion = LiveRegionMode.Polite },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        tint = if (p.locked) Candy.purple else item.row.tone,
    ) {
        Text(item.label, style = s.buttonText.copy(fontSize = 15.sp), color = s.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        val line = if (p.locked) "Pretend tank only: switch to Sandbox to use it" else item.blurb
        if (line.isNotEmpty()) {
            Text(line, style = s.buttonText.copy(fontSize = 12.sp, lineHeight = 15.sp), color = s.labelDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

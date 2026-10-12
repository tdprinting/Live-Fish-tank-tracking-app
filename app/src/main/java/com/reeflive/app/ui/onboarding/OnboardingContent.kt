package com.reeflive.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.SegmentedPills
import com.reeflive.app.ui.components.SpeciesAvatar
import com.reeflive.app.ui.components.TextAction
import com.reeflive.app.ui.components.parseDecimal
import com.reeflive.app.ui.theme.CausticBackground
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.Ink
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.model.ReefType
import com.reeflive.core.model.Tank
import com.reeflive.core.species.SpeciesKind
import com.reeflive.core.units.ParamUnit
import com.reeflive.core.util.Fmt
import kotlinx.coroutines.launch

/** Tank form on onboarding page 2 (strings as typed; dimensions in inches or cm). */
data class TankForm(
    val name: String = "Tee's Reef",
    val length: String = "48",
    val width: String = "13",
    val height: String = "24",
    val inches: Boolean = true,
    val volume: String = "60",
    val volumeEdited: Boolean = true,
    val sump: String = "0",
    val reefType: ReefType = ReefType.SOFT,
) {
    private fun cm(s: String): Double? = parseDecimal(s)?.let { if (inches) it * CM_PER_IN else it }

    val lengthCm: Double? get() = cm(length)
    val widthCm: Double? get() = cm(width)
    val heightCm: Double? get() = cm(height)
    val grossGallons: Double?
        get() {
            val l = lengthCm ?: return null
            val w = widthCm ?: return null
            val h = heightCm ?: return null
            return Tank.grossGallons(l, w, h)
        }
    val volumeGallons: Double? get() = parseDecimal(volume)
    val valid: Boolean get() = name.isNotBlank() && (lengthCm ?: 0.0) > 0 && (widthCm ?: 0.0) > 0 && (heightCm ?: 0.0) > 0 && (volumeGallons ?: 0.0) > 0

    /** Converts the dimension fields when the unit toggle flips. */
    fun withInches(toInches: Boolean): TankForm {
        if (toInches == inches) return this
        fun conv(s: String): String = parseDecimal(s)?.let { Fmt.trim(if (toInches) it / CM_PER_IN else it * CM_PER_IN, 1) } ?: s
        return copy(length = conv(length), width = conv(width), height = conv(height), inches = toInches)
    }

    /** Re-derives the display volume from the dimensions unless the user typed one. */
    fun recomputed(): TankForm = if (volumeEdited) this else copy(volume = grossGallons?.let { Fmt.trim(it, 0) } ?: volume)

    val summary: String
        get() {
            val u = if (inches) "in" else "cm"
            return "$name · ${volume.ifBlank { "?" }} gal · $length × $width × $height $u · ${reefType.label.lowercase()}"
        }

    companion object {
        const val CM_PER_IN = 2.54

        fun from(t: Tank): TankForm = TankForm(
            name = t.name,
            length = Fmt.trim(t.lengthCm / CM_PER_IN, 1),
            width = Fmt.trim(t.widthCm / CM_PER_IN, 1),
            height = Fmt.trim(t.heightCm / CM_PER_IN, 1),
            inches = true,
            volume = Fmt.trim(t.displayGallons, 1),
            volumeEdited = true,
            sump = Fmt.trim(t.sumpGallons, 1),
            reefType = t.reefType,
        )
    }
}

/** One seeded organism with its checkbox on page 3. */
data class LivestockChoice(
    val index: Int,
    val nickname: String,
    val speciesId: String,
    val speciesName: String,
    val scientific: String,
    val kind: SpeciesKind?,
    val colors: Map<String, String>,
    val quantity: Int,
    val verified: Boolean,
    val checked: Boolean = true,
)

data class OnboardingUi(
    val tank: TankForm = TankForm(),
    val livestock: List<LivestockChoice> = emptyList(),
    val tempUnit: ParamUnit = ParamUnit.F,
    val salUnit: ParamUnit = ParamUnit.SG,
    val alkUnit: ParamUnit = ParamUnit.DKH,
    val reminderHour: Int = 8,
    val notificationsGranted: Boolean = false,
    val busy: Boolean = false,
)

/** Callbacks for [OnboardingContent]. */
data class OnboardingActions(
    val onTank: (TankForm) -> Unit = {},
    val onToggle: (Int) -> Unit = {},
    val onTempUnit: (ParamUnit) -> Unit = {},
    val onSalUnit: (ParamUnit) -> Unit = {},
    val onAlkUnit: (ParamUnit) -> Unit = {},
    val onReminderHour: (Int) -> Unit = {},
    val onRequestNotifications: () -> Unit = {},
    val onFinish: () -> Unit = {},
)

private const val PAGES = 4

/** Onboarding (DESIGN §5.1): Welcome → Your tank → Your livestock → Reminders & units. */
@Composable
fun OnboardingContent(state: OnboardingUi, actions: OnboardingActions, animate: Boolean = true, pager: PagerState = rememberPagerState { PAGES }) {
    val scope = rememberCoroutineScope()
    CausticBackground(animate = animate) {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                when (page) {
                    0 -> WelcomePage()
                    1 -> TankPage(state.tank, actions.onTank)
                    2 -> LivestockPage(state.livestock, actions.onToggle)
                    else -> UnitsPage(state, actions)
                }
            }
        }
        if (pager.currentPage < PAGES - 1) {
            TextAction(
                "Skip",
                onClick = { actions.onFinish() },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 12.dp),
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageDots(pager.currentPage, PAGES)
            Spacer(Modifier.height(16.dp))
            val label = when (pager.currentPage) {
                0 -> "Get started"
                1 -> "Looks right"
                2 -> "Continue"
                else -> if (state.busy) "Filling the tank…" else "Dive in"
            }
            ReefButton(
                text = label,
                enabled = !state.busy && (pager.currentPage != 1 || state.tank.valid),
                onClick = {
                    if (pager.currentPage < PAGES - 1) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    else actions.onFinish()
                },
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
            )
        }
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    val c = ReefTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 22.dp else 8.dp, ReefMotion.spring(ReefTheme.reduceMotion), label = "dot")
            val col by animateColorAsState(if (i == current) c.primaryBright else c.textTertiary.copy(alpha = 0.6f), label = "dotc")
            Box(Modifier.height(8.dp).width(w).clip(ReefShapes.pill).background(col))
        }
    }
}

private val PageBottom = 150.dp

@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = PageBottom),
    ) { content() }
}

@Composable
private fun WelcomePage() {
    val c = ReefTheme.colors
    Column(
        Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, bottom = PageBottom),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Porthole(Modifier.size(176.dp))
        Spacer(Modifier.height(32.dp))
        Text("ReefLive", style = MaterialTheme.typography.displayLarge, color = c.text)
        Spacer(Modifier.height(8.dp))
        Text("Track it. Watch it live.", style = MaterialTheme.typography.headlineSmall, color = c.primaryBright, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Feature(Icons.Rounded.Science, "Log a test in seconds", "Alk, Ca, nitrate… with live status as you type")
        Feature(Icons.Rounded.Insights, "Read the trend", "Charts with your ideal ranges, alerts before trouble")
        Feature(Icons.Rounded.Waves, "Watch your reef", "Your fish and corals, alive in 3D")
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    val c = ReefTheme.colors
    Row(Modifier.fillMaxWidth().widthIn(max = 420.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.primaryBright, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
    }
}

/** Decorative glowing porthole with light rings. */
@Composable
private fun Porthole(modifier: Modifier) {
    val c = ReefTheme.colors
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(c.primaryBright.copy(alpha = 0.35f), Color.Transparent), radius = r), r)
            for (i in 1..3) {
                drawCircle(c.primaryBright.copy(alpha = 0.10f * (4 - i)), r * (0.5f + i * 0.14f), style = Stroke(width = 1.5.dp.toPx()))
            }
            drawCircle(Brush.linearGradient(listOf(c.primary, c.reef), start = Offset(0f, 0f), end = Offset(size.width, size.height)), r * 0.5f)
            drawCircle(Color.White.copy(alpha = 0.25f), r * 0.5f, style = Stroke(width = 2.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.22f), r * 0.1f, center = Offset(center.x - r * 0.2f, center.y - r * 0.22f))
        }
        Icon(Icons.Rounded.Waves, null, tint = Ink.onPrimary.copy(alpha = 0.85f), modifier = Modifier.size(52.dp))
    }
}

@Composable
private fun PageTitle(title: String, body: String) {
    val c = ReefTheme.colors
    Text(title, style = MaterialTheme.typography.headlineLarge, color = c.text)
    Spacer(Modifier.height(6.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun TankPage(form: TankForm, onChange: (TankForm) -> Unit) {
    val c = ReefTheme.colors
    PageColumn {
        PageTitle("Your tank", "We filled in Tee's reef. Adjust anything that's off.")
        GlassCard(Modifier.fillMaxWidth(), tint = c.primary) {
            Text(form.summary, style = MaterialTheme.typography.titleMedium, color = c.text)
        }
        Spacer(Modifier.height(16.dp))
        TankFormFields(form, onChange)
    }
}

/** Tank name, dimensions, volumes and reef type — shared by onboarding and Tank settings. */
@Composable
fun TankFormFields(form: TankForm, onChange: (TankForm) -> Unit) {
    val c = ReefTheme.colors
    Column {
            ReefTextField(form.name, { onChange(form.copy(name = it)) }, label = "Name", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dimensions", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
                SegmentedPills(listOf("in", "cm"), if (form.inches) 0 else 1, { onChange(form.withInches(it == 0)) }, Modifier.width(120.dp))
            }
            Spacer(Modifier.height(8.dp))
            val unit = if (form.inches) "in" else "cm"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReefTextField(form.length, { onChange(form.copy(length = it).recomputed()) }, Modifier.weight(1f), label = "Length", numeric = true, suffix = { UnitText(unit) })
                ReefTextField(form.width, { onChange(form.copy(width = it).recomputed()) }, Modifier.weight(1f), label = "Width", numeric = true, suffix = { UnitText(unit) })
                ReefTextField(form.height, { onChange(form.copy(height = it).recomputed()) }, Modifier.weight(1f), label = "Height", numeric = true, suffix = { UnitText(unit) })
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReefTextField(form.volume, { onChange(form.copy(volume = it, volumeEdited = true)) }, Modifier.weight(1f), label = "Display volume", numeric = true, suffix = { UnitText("gal") })
                ReefTextField(form.sump, { onChange(form.copy(sump = it)) }, Modifier.weight(1f), label = "Sump", numeric = true, suffix = { UnitText("gal") })
            }
            form.grossGallons?.let { g ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Glass volume ≈ ${Fmt.trim(g, 0)} gal", style = MaterialTheme.typography.bodySmall, color = c.textTertiary, modifier = Modifier.weight(1f))
                    if (form.volumeEdited) TextAction("Use it", onClick = { onChange(form.copy(volumeEdited = false).recomputed()) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Coral type", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            SegmentedPills(
                ReefType.entries.map { if (it == ReefType.SOFT) "Soft" else it.label },
                ReefType.entries.indexOf(form.reefType),
                { onChange(form.copy(reefType = ReefType.entries[it])) },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text("Picks the default target ranges. You can fine-tune them later.", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
    }
}

@Composable
private fun UnitText(u: String) {
    Text(u, style = MaterialTheme.typography.labelMedium, color = ReefTheme.colors.textSecondary)
}

@Composable
private fun LivestockPage(items: List<LivestockChoice>, onToggle: (Int) -> Unit) {
    val c = ReefTheme.colors
    PageColumn {
        val checked = items.filter { it.checked }.sumOf { it.quantity }
        PageTitle("Your livestock", "$checked organisms. Uncheck anything that's no longer in the tank.")
        val groups = listOf(
            "Fish" to items.filter { it.kind == SpeciesKind.FISH },
            "Inverts" to items.filter { it.kind == SpeciesKind.INVERT || it.kind == null },
            "Corals & algae" to items.filter { it.kind == SpeciesKind.CORAL || it.kind == SpeciesKind.ALGAE },
        )
        groups.filter { it.second.isNotEmpty() }.forEach { (title, list) ->
            Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            GlassCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                list.forEach { LivestockRow(it) { onToggle(it.index) } }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Add more any time from More → Livestock.", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
    }
}

@Composable
private fun LivestockRow(item: LivestockChoice, onToggle: () -> Unit) {
    val c = ReefTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Checkbox, onClick = onToggle).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeciesAvatar(item.speciesId, item.kind, item.colors, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.nickname, style = MaterialTheme.typography.titleMedium, color = if (item.checked) c.text else c.textTertiary)
                if (item.quantity > 1) Text("  ×${item.quantity}", style = ReefTheme.numbers.body, color = c.textSecondary)
            }
            Text(
                item.speciesName + if (!item.verified) " · verify ID" else "",
                style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
                color = if (item.verified) c.textSecondary else c.cautionText,
            )
        }
        CheckCircle(item.checked)
    }
}

@Composable
private fun CheckCircle(checked: Boolean) {
    val c = ReefTheme.colors
    val bg by animateColorAsState(if (checked) c.primaryBright else Color.Transparent, ReefMotion.tween(ReefTheme.reduceMotion, ReefMotion.FAST), label = "chk")
    Box(
        Modifier.size(26.dp).clip(CircleShape).background(bg).border(1.5.dp, if (checked) c.primaryBright else c.textTertiary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Rounded.Check, contentDescription = "Included", tint = Ink.onPrimary, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitsPage(state: OnboardingUi, actions: OnboardingActions) {
    val c = ReefTheme.colors
    PageColumn {
        PageTitle("Reminders & units", "How you like your numbers, and when to nudge you.")
        GlassCard(Modifier.fillMaxWidth()) {
            UnitRow("Temperature", listOf(ParamUnit.F, ParamUnit.C), state.tempUnit, actions.onTempUnit)
            UnitRow("Salinity", listOf(ParamUnit.SG, ParamUnit.PPT), state.salUnit, actions.onSalUnit)
            UnitRow("Alkalinity", listOf(ParamUnit.DKH, ParamUnit.MEQ_L), state.alkUnit, actions.onAlkUnit)
        }
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Daily check & reminders", style = MaterialTheme.typography.titleMedium, color = c.text)
            Spacer(Modifier.height(4.dp))
            Text("We look at your numbers once a day and remind you about tank chores.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 8, 9, 12, 18, 20).forEach { h ->
                    ReefChip("%02d:00".format(h), selected = state.reminderHour == h, onClick = { actions.onReminderHour(h) })
                }
            }
            Spacer(Modifier.height(14.dp))
            if (state.notificationsGranted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NotificationsActive, null, tint = c.goodText, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Notifications are on", style = MaterialTheme.typography.bodyMedium, color = c.goodText)
                }
            } else {
                ReefButton("Allow notifications", actions.onRequestNotifications, kind = ButtonKind.TONAL, icon = Icons.Rounded.NotificationsActive)
            }
        }
    }
}

@Composable
private fun UnitRow(label: String, units: List<ParamUnit>, selected: ParamUnit, onSelect: (ParamUnit) -> Unit) {
    val c = ReefTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
        SegmentedPills(units.map { it.symbol }, units.indexOf(selected).coerceAtLeast(0), { onSelect(units[it]) }, Modifier.width(150.dp))
    }
}

package com.reeflive.app.ui.care

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefChip
import com.reeflive.app.ui.components.ReefTextField
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.SegmentedPills
import com.reeflive.app.ui.components.parseDecimal
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.chem.Ammonia
import com.reeflive.core.chem.DoseTarget
import com.reeflive.core.chem.Dosing
import com.reeflive.core.chem.Evaporation
import com.reeflive.core.chem.SaltMix
import com.reeflive.core.chem.WaterChange
import com.reeflive.core.model.DoseDetail
import com.reeflive.core.params.ParameterDef
import com.reeflive.core.params.ParameterTable
import com.reeflive.core.units.ParamUnit
import com.reeflive.core.units.UnitConverter
import com.reeflive.core.util.Fmt

/** What calculators prefill from: latest values (base units), targets and tank volume. */
data class CalcContext(
    val latest: Map<String, Double> = emptyMap(),
    val defs: Map<String, ParameterDef> = emptyMap(),
    val systemGallons: Double = 60.0,
    val tempUnit: ParamUnit = ParamUnit.F,
) {
    fun def(id: String): ParameterDef? = defs[id] ?: ParameterTable.byId(id)
    fun latestOrMid(id: String): Double? = latest[id] ?: def(id)?.ideal?.mid
}

/** Actions a calculator can take on the outside world. */
data class CalcActions(
    val onBack: () -> Unit = {},
    val onLogDose: (DoseDetail, String) -> Unit = { _, _ -> },
    val onLogWaterChange: (gallons: Double, fraction: Double, mix: Map<String, Double>) -> Unit = { _, _, _ -> },
)

private fun fmtIn(v: Double?, d: Int): String = v?.let { Fmt.trim(it, d) } ?: ""

@Composable
fun CalculatorContent(calc: Calc, ctx: CalcContext, actions: CalcActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(calc.title, onBack = actions.onBack, subtitle = calc.subtitle)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (calc) {
                Calc.DOSING -> DosingCalc(ctx, actions)
                Calc.WATER_CHANGE -> WaterChangeCalc(ctx, actions)
                Calc.SALT -> SaltCalc(ctx)
                Calc.EVAPORATION -> EvaporationCalc(ctx)
                Calc.AMMONIA -> AmmoniaCalc(ctx)
                Calc.CONVERT -> ConvertCalc()
            }
        }
    }
}

@Composable
private fun InputCard(content: @Composable ColumnScope.() -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, unit: String, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    ReefTextField(
        value, { t -> onChange(t.filter { it.isDigit() || it == '.' || it == ',' }.take(10)) }, modifier.fillMaxWidth(),
        label = label, numeric = true, error = value.isNotBlank() && parseDecimal(value) == null,
        suffix = { Text(unit, style = MaterialTheme.typography.labelMedium, color = c.textTertiary) },
    )
}

/** Result card: hero value + supporting lines, tinted with the brand gradient. */
@Composable
private fun ResultCard(title: String, value: String, unit: String, content: @Composable ColumnScope.() -> Unit = {}) {
    val c = ReefTheme.colors
    GlassCard(Modifier.fillMaxWidth().animateContentSize(), tint = c.primary) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = ReefTheme.numbers.hero, color = c.text, modifier = Modifier.alignByBaseline())
            Spacer(Modifier.width(6.dp))
            Text(unit, style = MaterialTheme.typography.titleMedium, color = c.textSecondary, modifier = Modifier.alignByBaseline())
        }
        content()
    }
}

@Composable
private fun Line(label: String, value: String, warn: Boolean = false) {
    val c = ReefTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = ReefTheme.numbers.body, color = if (warn) c.cautionText else c.text)
    }
}

@Composable
private fun Warn(text: String) {
    val c = ReefTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(ReefShapes.tile).background(c.caution.copy(alpha = c.statusFillAlpha)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Warning, null, tint = c.cautionText)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.cautionText)
    }
}

// ---------------------------------------------------------------- Dosing

private const val CUSTOM = "custom"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DosingCalc(ctx: CalcContext, actions: CalcActions) {
    val c = ReefTheme.colors
    var target by rememberSaveable { mutableStateOf(DoseTarget.ALK) }
    val def = ctx.def(target.paramId)
    var current by rememberSaveable(target) { mutableStateOf(fmtIn(ctx.latestOrMid(target.paramId), def?.precision ?: 1)) }
    var goal by rememberSaveable(target) { mutableStateOf(fmtIn(def?.ideal?.mid, def?.precision ?: 1)) }
    var volume by rememberSaveable { mutableStateOf(fmtIn(ctx.systemGallons, 1)) }
    var productId by rememberSaveable(target) { mutableStateOf(Dosing.productsFor(target).first().id) }
    var effect by rememberSaveable(target) { mutableStateOf("") }
    var logged by remember(target, current, goal, volume, productId, effect) { mutableStateOf(false) }

    InputCard {
        SegmentedPills(DoseTarget.entries.map { ParameterTable.shortName(it.paramId) }, target.ordinal, { target = DoseTarget.entries[it] }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Current", current, { current = it }, target.unitLabel, Modifier.weight(1f))
            Field("Target", goal, { goal = it }, target.unitLabel, Modifier.weight(1f))
        }
        Field("System volume", volume, { volume = it }, "gal")
        Text("Product", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Dosing.productsFor(target).forEach { p ->
                ReefChip(p.name, productId == p.id, { productId = p.id })
            }
            ReefChip("Custom liquid", productId == CUSTOM, { productId = CUSTOM })
        }
        if (productId == CUSTOM) {
            Field("Raises per mL in 1 gal", effect, { effect = it }, target.unitLabel)
            Text("From the bottle: e.g. “1 mL raises 100 gal by 0.014 dKH” → 1.4", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        }
    }

    val cur = parseDecimal(current)
    val tgt = parseDecimal(goal)
    val vol = parseDecimal(volume)?.takeIf { it > 0 }
    if (cur == null || tgt == null || vol == null) return
    val delta = tgt - cur
    if (delta <= 0) {
        ResultCard("Nothing to dose", "0", if (productId == CUSTOM) "mL" else "g") {
            Line("Change needed", Fmt.signed(delta, (def?.precision ?: 1)) + " ${target.unitLabel}")
            Text("Already at or above target — let consumption bring it down.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
        }
        return
    }
    val product = Dosing.product(productId)
    val liquidEffect = parseDecimal(effect)?.takeIf { it > 0 }
    val amount: Double
    val unit: String
    if (product != null) {
        amount = Dosing.grams(product, delta, vol)
        unit = "g"
    } else {
        if (liquidEffect == null) return
        amount = Dosing.ml(delta, vol, liquidEffect)
        unit = "mL"
    }
    val cap = def?.maxChangePerDay
    val plan = Dosing.plan(amount, delta, cap)
    ResultCard("Total dose", Fmt.num(amount, if (amount < 10) 2 else 1), unit) {
        Line("Raises ${ParameterTable.shortName(target.paramId)} by", "${Fmt.num(delta, (def?.precision ?: 1) + 1)} ${target.unitLabel}")
        if (plan.days > 1) Line("Spread over", "${plan.days} days · ${Fmt.num(plan.perDay, 1)} $unit/day", warn = true)
        if (product != null && product.id == "nahco3") Line("Measuring", "≈ ${Fmt.num(amount / 4.8, 1)} tsp")
        if (plan.exceedsCap && cap != null) {
            Warn("A jump of ${Fmt.num(delta, 2)} ${target.unitLabel} exceeds the safe ${Fmt.trim(cap, 2)} ${target.unitLabel}/day. Dose over ${plan.days} days.")
        }
        Spacer(Modifier.height(12.dp))
        ReefButton(
            if (logged) "Dose logged" else "Log this dose",
            {
                val name = product?.name ?: "Custom liquid"
                val detail = DoseDetail(
                    paramId = target.paramId, product = name,
                    grams = if (product != null) amount else null, ml = if (product == null) amount else null,
                    expectedDelta = delta,
                )
                val per = if (plan.days > 1) plan.perDay else amount
                actions.onLogDose(detail, "Dosed ${Fmt.num(per, 1)} $unit ${name.substringBefore(" (").lowercase()}")
                logged = true
            },
            Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Science,
            enabled = !logged,
        )
    }
}

// ---------------------------------------------------------------- Water change

@Composable
private fun WaterChangeCalc(ctx: CalcContext, actions: CalcActions) {
    val c = ReefTheme.colors
    var mode by rememberSaveable { mutableStateOf(0) }
    var amount by rememberSaveable { mutableStateOf(fmtIn(Math.round(ctx.systemGallons * 0.1 * 2) / 2.0, 1)) }
    var pct by rememberSaveable { mutableStateOf("10") }
    val mixIds = WaterChange.DEFAULT_MIX.keys.toList()
    var mix by remember { mutableStateOf(WaterChange.DEFAULT_MIX.mapValues { (id, v) -> fmtIn(v, ctx.def(id)?.precision ?: 1) }) }
    var logged by remember(mode, amount, pct) { mutableStateOf(false) }

    InputCard {
        SegmentedPills(listOf("Gallons", "Percent"), mode, { mode = it }, Modifier.fillMaxWidth())
        if (mode == 0) Field("Change", amount, { amount = it }, "gal") else Field("Change", pct, { pct = it }, "%")
        Text("System volume ${Fmt.trim(ctx.systemGallons, 1)} gal", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        Text("New saltwater", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        mixIds.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { id ->
                    val d = ctx.def(id)
                    Field(ParameterTable.shortName(id), mix[id].orEmpty(), { v -> mix = mix + (id to v) }, d?.baseUnit?.symbol.orEmpty(), Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    val fraction = if (mode == 0) parseDecimal(amount)?.let { WaterChange.fraction(it, ctx.systemGallons) }
    else parseDecimal(pct)?.let { (it / 100.0).coerceIn(0.0, 1.0) }
    if (fraction == null || fraction <= 0) return
    val gallons = fraction * ctx.systemGallons
    val mixVals = mix.mapNotNull { (k, v) -> parseDecimal(v)?.let { k to it } }.toMap()
    val before = mixIds.mapNotNull { id -> ctx.latest[id]?.let { id to it } }.toMap()
    val after = WaterChange.predict(before, mixVals, fraction)

    ResultCard("Change", Fmt.num(gallons, 1), "gal · ${Fmt.num(fraction * 100, 0)}%") {
        if (before.isEmpty()) {
            Text("Log a test to see predicted values after the change.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
        }
        before.forEach { (id, b) ->
            val a = after[id] ?: b
            val d = ctx.def(id)
            BeforeAfter(ParameterTable.shortName(id), b, a, d)
        }
        Spacer(Modifier.height(12.dp))
        ReefButton(
            if (logged) "Water change logged" else "Log water change",
            { actions.onLogWaterChange(gallons, fraction, mixVals); logged = true },
            Modifier.fillMaxWidth(), icon = Icons.Rounded.Waves, enabled = !logged,
        )
    }
}

@Composable
private fun BeforeAfter(label: String, before: Double, after: Double, def: ParameterDef?) {
    val c = ReefTheme.colors
    val p = def?.precision ?: 1
    val lo = minOf(before, after, def?.acceptable?.lo ?: before)
    val hi = maxOf(before, after, def?.acceptable?.hi ?: after)
    val span = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
            Text("${Fmt.num(before, p)} → ${Fmt.num(after, p)}", style = ReefTheme.numbers.body, color = c.text)
        }
        Spacer(Modifier.height(4.dp))
        Bar((before - lo) / span, c.textTertiary)
        Spacer(Modifier.height(2.dp))
        Bar((after - lo) / span, c.primaryBright)
    }
}

@Composable
private fun Bar(fraction: Double, color: androidx.compose.ui.graphics.Color) {
    val c = ReefTheme.colors
    Box(Modifier.fillMaxWidth().height(6.dp).clip(ReefShapes.pill).background(c.glass)) {
        Box(Modifier.fillMaxWidth(fraction.toFloat().coerceIn(0.03f, 1f)).fillMaxHeight().clip(ReefShapes.pill).background(color))
    }
}

// ---------------------------------------------------------------- Salt mix

@Composable
private fun SaltCalc(ctx: CalcContext) {
    var sg by rememberSaveable { mutableStateOf("1.025") }
    var gal by rememberSaveable { mutableStateOf(fmtIn(Math.round(ctx.systemGallons * 0.1 * 2) / 2.0, 1)) }
    var rate by rememberSaveable { mutableStateOf(Fmt.trim(SaltMix.DEFAULT_G_PER_GAL_AT_1025, 0)) }
    InputCard {
        Field("Target salinity", sg, { sg = it }, "SG")
        Field("Fresh water", gal, { gal = it }, "gal")
        Field("Salt per gallon at 1.025", rate, { rate = it }, "g/gal")
        Text("Default 136 g/gal (≈ ½ cup). Check your salt's label.", style = MaterialTheme.typography.labelSmall, color = ReefTheme.colors.textTertiary)
    }
    val s = parseDecimal(sg)?.takeIf { it in 1.0..1.04 } ?: return
    val g = parseDecimal(gal)?.takeIf { it > 0 } ?: return
    val r = parseDecimal(rate)?.takeIf { it > 0 } ?: return
    val grams = SaltMix.grams(s, g, r)
    ResultCard("Salt", Fmt.num(grams, 0), "g") {
        Line("Cups", "≈ ${Fmt.num(SaltMix.cups(grams), 2)}")
        Line("Pounds", "≈ ${Fmt.num(grams / 453.6, 2)} lb")
        Line("Salinity", "${Fmt.num(UnitConverter.sgToPpt(s), 1)} ppt")
    }
}

// ---------------------------------------------------------------- Evaporation

@Composable
private fun EvaporationCalc(ctx: CalcContext) {
    val c = ReefTheme.colors
    var evap by rememberSaveable { mutableStateOf("0.5") }
    var sg by rememberSaveable { mutableStateOf(fmtIn(ctx.latest["sal"] ?: 1.025, 3)) }
    var days by rememberSaveable { mutableStateOf("2") }
    InputCard {
        Field("Evaporation", evap, { evap = it }, "gal/day")
        Field("Salinity at last top-off", sg, { sg = it }, "SG")
        Field("Days since top-off", days, { days = it }, "days")
        Text("System volume ${Fmt.trim(ctx.systemGallons, 1)} gal", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
    }
    val e = parseDecimal(evap)?.takeIf { it >= 0 } ?: return
    val s = parseDecimal(sg)?.takeIf { it in 1.0..1.04 } ?: return
    val d = parseDecimal(days)?.takeIf { it >= 0 } ?: return
    if (e * d >= ctx.systemGallons * 0.9) {
        Warn("That would evaporate most of the tank — check the numbers.")
        return
    }
    val after = Evaporation.sgAfter(s, ctx.systemGallons, e, d)
    val drift = Evaporation.driftPerDay(s, ctx.systemGallons, e)
    val def = ctx.def("sal")
    ResultCard("Predicted salinity", Fmt.num(after, 4), "SG") {
        Line("Drift", "+${Fmt.num(drift, 4)} SG/day")
        Line("Top-off to return", "${Fmt.num(Evaporation.topOffGallons(after, s, ctx.systemGallons), 2)} gal RO/DI")
        if (def != null && after > def.acceptable.hi) Warn("Above the acceptable range — top off now and consider an auto top-off.")
    }
}

// ---------------------------------------------------------------- Ammonia

@Composable
private fun AmmoniaCalc(ctx: CalcContext) {
    val c = ReefTheme.colors
    val tempIsF = ctx.tempUnit == ParamUnit.F
    var tan by rememberSaveable { mutableStateOf(fmtIn(ctx.latest["nh3"] ?: 0.25, 2)) }
    var ph by rememberSaveable { mutableStateOf(fmtIn(ctx.latest["ph"] ?: 8.1, 2)) }
    val baseTemp = ctx.latest["temp"] ?: 78.0
    var temp by rememberSaveable { mutableStateOf(fmtIn(if (tempIsF) baseTemp else UnitConverter.fToC(baseTemp), 1)) }
    InputCard {
        Field("Total ammonia (test kit)", tan, { tan = it }, "ppm")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("pH", ph, { ph = it }, "", Modifier.weight(1f))
            Field("Temperature", temp, { temp = it }, if (tempIsF) "°F" else "°C", Modifier.weight(1f))
        }
    }
    val t = parseDecimal(tan)?.takeIf { it >= 0 } ?: return
    val p = parseDecimal(ph)?.takeIf { it in 6.0..10.0 } ?: return
    val tc = parseDecimal(temp)?.let { if (tempIsF) UnitConverter.fToC(it) else it } ?: return
    val free = Ammonia.freeNh3(t, p, tc)
    val danger = Ammonia.isDangerous(free)
    ResultCard("Free ammonia (NH3)", Fmt.num(free, 3), "ppm") {
        Line("Toxic share", "${Fmt.num(Ammonia.fraction(p, tc) * 100, 1)}% of total")
        Spacer(Modifier.height(8.dp))
        if (danger) Warn("Above ${Ammonia.DANGER_FREE_PPM} ppm free NH3 — harmful to fish. Water change and find the source.")
        else Text(
            if (free > Ammonia.DANGER_FREE_PPM / 2) "Low but present — retest tomorrow." else "Safe level.",
            style = MaterialTheme.typography.bodyMedium, color = if (free > Ammonia.DANGER_FREE_PPM / 2) c.cautionText else c.goodText,
        )
    }
}

// ---------------------------------------------------------------- Convert

private data class Quantity(val label: String, val units: List<ParamUnit>, val precision: Map<ParamUnit, Int>)

private val QUANTITIES = listOf(
    Quantity("Temperature", listOf(ParamUnit.F, ParamUnit.C), mapOf(ParamUnit.F to 1, ParamUnit.C to 1)),
    Quantity("Salinity", listOf(ParamUnit.SG, ParamUnit.PPT, ParamUnit.MS_CM), mapOf(ParamUnit.SG to 4, ParamUnit.PPT to 1, ParamUnit.MS_CM to 1)),
    Quantity("Alkalinity", listOf(ParamUnit.DKH, ParamUnit.MEQ_L, ParamUnit.PPM_CACO3), mapOf(ParamUnit.DKH to 2, ParamUnit.MEQ_L to 2, ParamUnit.PPM_CACO3 to 0)),
    Quantity("Nitrate", listOf(ParamUnit.PPM, ParamUnit.PPM_NO3_N), mapOf(ParamUnit.PPM to 2, ParamUnit.PPM_NO3_N to 3)),
    Quantity("Phosphate", listOf(ParamUnit.PPM, ParamUnit.PPM_PO4_P, ParamUnit.PPB), mapOf(ParamUnit.PPM to 3, ParamUnit.PPM_PO4_P to 3, ParamUnit.PPB to 0)),
)

/** Non-parameter conversions: volume and length. */
private object Plain {
    fun convert(q: Int, v: Double, from: Int, to: Int): Double {
        val factors = if (q == 0) listOf(1.0, 3.785411784, 0.8326741846) else listOf(1.0, 2.54)
        return v / factors[from] * factors[to]
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConvertCalc() {
    val c = ReefTheme.colors
    var q by rememberSaveable { mutableStateOf(0) }
    var from by rememberSaveable(q) { mutableStateOf(0) }
    var input by rememberSaveable(q) { mutableStateOf(listOf("78", "1.025", "8.0", "10", "0.05", "10", "4")[q]) }
    val labels = QUANTITIES.map { it.label } + listOf("Volume", "Length")
    InputCard {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { i, l -> ReefChip(l, q == i, { q = i }) }
        }
        val unitLabels = when {
            q < QUANTITIES.size -> QUANTITIES[q].units.map { it.symbol }
            q == QUANTITIES.size -> listOf("US gal", "L", "UK gal")
            else -> listOf("in", "cm")
        }
        SegmentedPills(unitLabels, from.coerceAtMost(unitLabels.size - 1), { from = it }, Modifier.fillMaxWidth())
        Field("Value", input, { input = it }, unitLabels.getOrElse(from) { "" })
    }
    val v = parseDecimal(input) ?: return
    GlassCard(Modifier.fillMaxWidth(), tint = c.primary) {
        if (q < QUANTITIES.size) {
            val quantity = QUANTITIES[q]
            val src = quantity.units[from.coerceAtMost(quantity.units.size - 1)]
            quantity.units.filter { it != src }.forEach { u ->
                val r = runCatching { UnitConverter.convert(v, src, u) }.getOrNull()
                ConvRow(u.symbol, r?.let { Fmt.num(it, quantity.precision[u] ?: 2) } ?: "—")
            }
        } else {
            val idx = q - QUANTITIES.size
            val names = if (idx == 0) listOf("US gal", "L", "UK gal") else listOf("in", "cm")
            names.indices.filter { it != from }.forEach { i -> ConvRow(names[i], Fmt.num(Plain.convert(idx, v, from, i), 2)) }
        }
    }
}

@Composable
private fun ConvRow(unit: String, value: String) {
    val c = ReefTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(value, style = ReefTheme.numbers.field, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(unit, style = MaterialTheme.typography.titleSmall, color = c.textSecondary)
    }
}

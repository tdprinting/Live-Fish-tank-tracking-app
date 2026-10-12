package com.reeflive.app.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.reeflive.app.ui.extras.CoralActions
import com.reeflive.app.ui.extras.CoralItem
import com.reeflive.app.ui.extras.CoralsContent
import com.reeflive.app.ui.extras.EmergencyContent
import com.reeflive.app.ui.extras.FeedingActions
import com.reeflive.app.ui.extras.FeedingContent
import com.reeflive.app.ui.extras.IcpContent
import com.reeflive.app.ui.extras.InsightsContent
import com.reeflive.app.ui.extras.InsightsMapper
import com.reeflive.app.ui.extras.ParContent
import com.reeflive.app.ui.extras.SpendingContent
import com.reeflive.app.ui.extras.SuppliesContent
import com.reeflive.app.ui.extras.SupplyActions
import com.reeflive.app.ui.extras.TreatmentActions
import com.reeflive.app.ui.extras.TreatmentsContent
import com.reeflive.app.ui.theme.CausticBackground
import com.reeflive.app.ui.theme.ReefLiveTheme
import com.reeflive.core.extras.Expense
import com.reeflive.core.extras.ExpenseCategory
import com.reeflive.core.extras.Feeding
import com.reeflive.core.extras.FragEntry
import com.reeflive.core.extras.FragFate
import com.reeflive.core.extras.GrowthEntry
import com.reeflive.core.extras.IcpResult
import com.reeflive.core.extras.IcpValue
import com.reeflive.core.extras.ParReading
import com.reeflive.core.extras.ReefExtras
import com.reeflive.core.extras.Supply
import com.reeflive.core.extras.SupplyCategory
import com.reeflive.core.extras.TreatmentCase
import com.reeflive.core.extras.TreatmentKind
import java.time.ZoneId

/** Sample reef-keeping extras for previews. */
object ExtrasPreviewData {
    private const val DAY = 86_400_000L
    private val now = PreviewData.NOW

    val extras = ReefExtras(
        supplies = listOf(
            Supply("s1", "Red Sea Coral Pro salt", SupplyCategory.SALT, 1.0, "bucket", lowAt = 1.0, cost = 74.99, boughtAt = now - 40 * DAY),
            Supply("s2", "Mysis (frozen)", SupplyCategory.FOOD, 6.0, "cubes", lowAt = 5.0),
            Supply("s3", "Hanna alkalinity reagent", SupplyCategory.REAGENT, 18.0, "tests", lowAt = 5.0, expiresAt = now + 12 * DAY),
            Supply("s4", "RO/DI sediment cartridge", SupplyCategory.RODI, 2.0, "", lowAt = 1.0),
            Supply("s5", "Filter socks 7\"", SupplyCategory.FILTER, 6.0, "socks", lowAt = 2.0),
        ),
        feedings = listOf(
            Feeding("f1", now - 3 * 3_600_000L, "Mysis"),
            Feeding("f2", now - DAY, "Nori"), Feeding("f3", now - DAY - 6 * 3_600_000L, "Pellets"),
            Feeding("f4", now - 2 * DAY, "Mysis"), Feeding("f5", now - 2 * DAY - 6 * 3_600_000L, "Pellets"),
        ),
        cases = listOf(
            TreatmentCase("c1", "New tang quarantine", TreatmentKind.COPPER, listOf("l1"), "Copper 0.5 ppm (Cupramine)", now - 8 * DAY, 28, 1,
                doses = (1..7).map { now - it * DAY }),
            TreatmentCase("c2", "Clown flukes", TreatmentKind.PRAZIQUANTEL, emptyList(), "Prazipro 1 tsp/20 gal", now - 40 * DAY, 7, 7,
                endedAt = now - 30 * DAY, outcome = "Healthy, moved to display"),
        ),
        expenses = listOf(
            Expense("e1", now - 5 * DAY, "Salt bucket", 74.99, ExpenseCategory.SUPPLIES),
            Expense("e2", now - 12 * DAY, "Zoa frag", 35.0, ExpenseCategory.CORALS),
            Expense("e3", now - 50 * DAY, "Return pump", 129.0, ExpenseCategory.EQUIPMENT),
        ),
        icp = listOf(
            IcpResult("i1", now - 10 * DAY, "Triton", listOf(
                IcpValue("Mg", 1310.0, "mg/L"), IcpValue("Ca", 425.0, "mg/L"), IcpValue("Sr", 7.6, "mg/L"), IcpValue("K", 395.0, "mg/L"),
                IcpValue("I", 38.0, "µg/L"), IcpValue("Mo", 14.0, "µg/L"), IcpValue("Cu", 3.1, "µg/L"),
            )),
        ),
        growth = listOf(
            GrowthEntry("g1", "k1", now - 60 * DAY, sizeCm = 9.0, note = "Added"),
            GrowthEntry("g2", "k1", now - 30 * DAY, sizeCm = 11.5),
            GrowthEntry("g3", "k1", now - 2 * DAY, sizeCm = 14.0, note = "Two new branches"),
        ),
        frags = listOf(FragEntry("fr1", "k1", now - 20 * DAY, 2, FragFate.GIVEN, note = "To the local club")),
        par = listOf(
            ParReading("p1", "Kenya tree top", now - 3 * DAY, 165.0, 60), ParReading("p2", "Zoa rock (sand)", now - 3 * DAY, 70.0, 60),
            ParReading("p3", "GSP upper left", now - 3 * DAY, 240.0, 60), ParReading("p4", "Kenya tree top", now - 40 * DAY, 120.0, 45),
        ),
    )
    val corals = listOf(
        CoralItem("k1", "Kenya tree", "capnella_sp", "Kenya tree coral"),
        CoralItem("k2", "Green star polyps", "briareum_sp", "Green star polyps"),
    )
    val insights by lazy { InsightsMapper.build(PreviewData.evals, emptyList(), now, ZoneId.of("UTC")) }
    val home = mapOf("ca" to 440.0, "mg" to 1290.0, "no3" to 6.0)
}

@Composable
private fun Frame(dark: Boolean, content: @Composable () -> Unit) {
    ReefLiveTheme(darkTheme = dark, reduceMotion = true) { CausticBackground(animate = false) { content() } }
}

private val E get() = ExtrasPreviewData.extras
private val N get() = PreviewData.NOW

@Preview(name = "Insights · Abyss", widthDp = 393, heightDp = 852)
@Composable
private fun InsightsDark() = Frame(true) { InsightsContent(ExtrasPreviewData.insights, {}) }

@Preview(name = "Insights · Lagoon", widthDp = 393, heightDp = 852)
@Composable
private fun InsightsLight() = Frame(false) { InsightsContent(ExtrasPreviewData.insights, {}) }

@Preview(name = "Supplies · Abyss", widthDp = 393, heightDp = 852)
@Composable
private fun SuppliesDark() = Frame(true) { SuppliesContent(E, N, SupplyActions()) }

@Preview(name = "Supplies · Lagoon", widthDp = 393, heightDp = 852)
@Composable
private fun SuppliesLight() = Frame(false) { SuppliesContent(E, N, SupplyActions()) }

@Preview(name = "Feeding · Abyss", widthDp = 393, heightDp = 852)
@Composable
private fun FeedingDark() = Frame(true) { FeedingContent(E, N, FeedingActions()) }

@Preview(name = "Feeding · Lagoon", widthDp = 393, heightDp = 852)
@Composable
private fun FeedingLight() = Frame(false) { FeedingContent(E, N, FeedingActions()) }

@Preview(name = "Quarantine · Abyss", widthDp = 393, heightDp = 852)
@Composable
private fun TreatmentsDark() = Frame(true) { TreatmentsContent(E, mapOf("l1" to "Blue"), N, TreatmentActions()) }

@Preview(name = "Quarantine · Lagoon", widthDp = 393, heightDp = 852)
@Composable
private fun TreatmentsLight() = Frame(false) { TreatmentsContent(E, mapOf("l1" to "Blue"), N, TreatmentActions()) }

@Preview(name = "Emergency · Abyss", widthDp = 393, heightDp = 852)
@Composable
private fun EmergencyDark() = Frame(true) { EmergencyContent({}, initialOpen = "power") }

@Preview(name = "Emergency · Lagoon", widthDp = 393, heightDp = 852)
@Composable
private fun EmergencyLight() = Frame(false) { EmergencyContent({}, initialOpen = "ammonia") }

@Preview(name = "ICP · Abyss", widthDp = 393, heightDp = 852)
@Composable
private fun IcpDark() = Frame(true) { IcpContent(E, ExtrasPreviewData.home, {}, {}, {}) }

@Preview(name = "ICP · Lagoon", widthDp = 393, heightDp = 852)
@Composable
private fun IcpLight() = Frame(false) { IcpContent(E, ExtrasPreviewData.home, {}, {}, {}) }

@Preview(name = "PAR · Abyss", widthDp = 393, heightDp = 852)
@Composable
private fun ParDark() = Frame(true) { ParContent(E, {}, {}, {}) }

@Preview(name = "PAR · Lagoon", widthDp = 393, heightDp = 852)
@Composable
private fun ParLight() = Frame(false) { ParContent(E, {}, {}, {}) }

@Preview(name = "Spending · Abyss", widthDp = 393, heightDp = 852)
@Composable
private fun SpendingDark() = Frame(true) { SpendingContent(E, emptyList(), N, {}, {}, {}) }

@Preview(name = "Spending · Lagoon", widthDp = 393, heightDp = 852)
@Composable
private fun SpendingLight() = Frame(false) { SpendingContent(E, emptyList(), N, {}, {}, {}) }

@Preview(name = "Coral log · Abyss", widthDp = 393, heightDp = 852)
@Composable
private fun CoralsDark() = Frame(true) { CoralsContent(E, ExtrasPreviewData.corals, ExtrasPreviewData.corals[0], CoralActions()) }

@Preview(name = "Coral log · Lagoon", widthDp = 393, heightDp = 852)
@Composable
private fun CoralsLight() = Frame(false) { CoralsContent(E, ExtrasPreviewData.corals, null, CoralActions()) }

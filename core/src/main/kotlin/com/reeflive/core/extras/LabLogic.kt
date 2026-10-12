package com.reeflive.core.extras

import java.time.Instant
import java.time.ZoneId

/** Reference range for one ICP element (natural seawater / common reef targets; research 01 §1). */
data class IcpRef(val element: String, val name: String, val unit: String, val lo: Double, val hi: Double, val homeParam: String? = null)

enum class IcpFlag(val label: String) { LOW("Low"), OK("OK"), HIGH("High"), UNKNOWN("No reference") }

/** ICP references, paste-import and home-kit comparison. Reference only; labs differ slightly. */
object IcpLogic {
    val refs: List<IcpRef> = listOf(
        IcpRef("Na", "Sodium", "mg/L", 10_200.0, 11_200.0),
        IcpRef("Mg", "Magnesium", "mg/L", 1_250.0, 1_400.0, "mg"),
        IcpRef("S", "Sulfur", "mg/L", 820.0, 980.0),
        IcpRef("Ca", "Calcium", "mg/L", 400.0, 450.0, "ca"),
        IcpRef("K", "Potassium", "mg/L", 380.0, 420.0),
        IcpRef("Br", "Bromine", "mg/L", 55.0, 75.0),
        IcpRef("Sr", "Strontium", "mg/L", 7.0, 9.0),
        IcpRef("B", "Boron", "mg/L", 4.0, 6.0),
        IcpRef("Li", "Lithium", "µg/L", 150.0, 250.0),
        IcpRef("I", "Iodine", "µg/L", 50.0, 80.0),
        IcpRef("Ba", "Barium", "µg/L", 5.0, 30.0),
        IcpRef("Mo", "Molybdenum", "µg/L", 10.0, 20.0),
        IcpRef("Fe", "Iron", "µg/L", 0.0, 5.0),
        IcpRef("Mn", "Manganese", "µg/L", 0.0, 3.0),
        IcpRef("Zn", "Zinc", "µg/L", 0.0, 10.0),
        IcpRef("Ni", "Nickel", "µg/L", 0.0, 5.0),
        IcpRef("Cu", "Copper", "µg/L", 0.0, 2.0),
        IcpRef("Al", "Aluminium", "µg/L", 0.0, 10.0),
        IcpRef("Si", "Silicon", "µg/L", 0.0, 300.0),
        IcpRef("P", "Phosphorus", "µg/L", 10.0, 30.0),
        IcpRef("PO4", "Phosphate", "mg/L", 0.03, 0.10, "po4"),
        IcpRef("NO3", "Nitrate", "mg/L", 2.0, 10.0, "no3"),
    )
    private val byElement = refs.associateBy { it.element.lowercase() }
    private val byName = refs.associateBy { it.name.lowercase() }

    fun ref(element: String): IcpRef? = byElement[element.trim().lowercase()] ?: byName[element.trim().lowercase()]

    fun flag(v: IcpValue): IcpFlag {
        val r = ref(v.element) ?: return IcpFlag.UNKNOWN
        if (v.unit.isNotBlank() && !sameUnit(v.unit, r.unit)) return IcpFlag.UNKNOWN
        return when {
            v.value < r.lo -> IcpFlag.LOW
            v.value > r.hi -> IcpFlag.HIGH
            else -> IcpFlag.OK
        }
    }

    private fun sameUnit(a: String, b: String): Boolean {
        fun n(u: String) = u.lowercase().replace("ppm", "mg/l").replace("ppb", "µg/l").replace("ug/l", "µg/l").replace(" ", "")
        return n(a) == n(b)
    }

    /**
     * Parses pasted lab results: one element per line, `element value [unit]` separated by commas, tabs, semicolons
     * or spaces (e.g. "Mg, 1320, mg/L" or "Iodine 62 µg/L"); use a dot for decimals. Uses the reference unit when none is given.
     * Returns the parsed values and the lines it could not read.
     */
    fun parse(text: String): Pair<List<IcpValue>, List<String>> {
        val ok = mutableListOf<IcpValue>()
        val bad = mutableListOf<String>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("""[\s,;]+""")).filter { it.isNotEmpty() }
            val numIdx = parts.indexOfFirst { it.toDoubleOrNull() != null }
            val value = parts.getOrNull(numIdx)?.toDoubleOrNull()
            val name = parts.take(numIdx.coerceAtLeast(0)).joinToString(" ")
            if (value == null || name.isBlank() || numIdx < 1) {
                if (!line.lowercase().startsWith("element")) bad += line
                continue
            }
            val unit = parts.drop(numIdx + 1).joinToString(" ").ifBlank { ref(name)?.unit.orEmpty() }
            ok += IcpValue(ref(name)?.element ?: name, value, unit)
        }
        return ok to bad
    }

    /** Elements of [r] that are out of range, worst first by relative distance. */
    fun outOfRange(r: IcpResult): List<IcpValue> = r.values.filter { flag(it) == IcpFlag.LOW || flag(it) == IcpFlag.HIGH }

    /** Home-kit vs lab difference in percent of the lab value (positive = kit reads high). */
    fun kitDeltaPercent(lab: Double, kit: Double): Double? = if (lab == 0.0) null else (kit - lab) / lab * 100.0
}

/** PAR guidance for corals (common hobby bands). */
object ParLogic {
    data class Band(val label: String, val lo: Double, val hi: Double)

    val bands = listOf(
        Band("Low light (mushrooms, zoas, GSP)", 0.0, 100.0),
        Band("Soft corals and LPS", 100.0, 200.0),
        Band("LPS and high-light softies", 200.0, 300.0),
        Band("SPS", 300.0, 2_000.0),
    )

    fun band(par: Double): Band = bands.lastOrNull { par >= it.lo } ?: bands.first()

    /** Latest reading per spot, newest spot first. */
    fun latestBySpot(readings: List<ParReading>): List<ParReading> =
        readings.groupBy { it.spot.trim().lowercase() }.values.map { l -> l.maxBy { it.at } }.sortedByDescending { it.at }
}

/** Spending totals. */
object SpendLogic {
    fun total(expenses: List<Expense>, fromMs: Long, toMs: Long = Long.MAX_VALUE): Double =
        expenses.filter { it.at in fromMs..toMs }.sumOf { it.amount }

    fun byCategory(expenses: List<Expense>, fromMs: Long): List<Pair<ExpenseCategory, Double>> =
        expenses.filter { it.at >= fromMs }.groupBy { it.category }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }

    fun startOfYear(nowMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfMonth(nowMs: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
}

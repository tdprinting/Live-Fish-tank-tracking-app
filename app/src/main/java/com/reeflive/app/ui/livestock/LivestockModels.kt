package com.reeflive.app.ui.livestock

import com.reeflive.app.ui.common.TimeText
import com.reeflive.core.model.HealthLog
import com.reeflive.core.model.Livestock
import com.reeflive.core.model.LivestockStatus
import com.reeflive.core.species.Risk
import com.reeflive.core.species.SpeciesDb
import com.reeflive.core.species.SpeciesKind
import com.reeflive.core.species.SpeciesSheet
import com.reeflive.core.util.Fmt

data class LivestockCardUi(
    val id: String,
    val speciesId: String,
    val kind: SpeciesKind?,
    val colors: Map<String, String>,
    val title: String,
    val species: String,
    val since: String?,
    val quantity: Int,
    val status: LivestockStatus,
    val warning: Boolean,
    val unverified: Boolean,
    val photoPath: String?,
)

data class LivestockSection(val title: String, val items: List<LivestockCardUi>)

data class CompatUi(val name: String, val risk: Risk, val note: String)

data class HealthUi(val id: String, val date: String, val symptom: String, val detail: String?)

data class LivestockDetailUi(
    val loading: Boolean = true,
    val found: Boolean = true,
    val card: LivestockCardUi? = null,
    val scientific: String = "",
    val warning: String? = null,
    val facts: List<Pair<String, String>> = emptyList(),
    val careNotes: List<String> = emptyList(),
    val compat: List<CompatUi> = emptyList(),
    val health: List<HealthUi> = emptyList(),
    val notes: String? = null,
    val canViewLive: Boolean = false,
)

/** Pure mapping for the livestock screens (DESIGN §5.10). */
object LivestockMapper {
    private const val DAY = 86_400_000L

    /** "3 wk", "8 mo", "1 y 2 mo" since [from]. */
    fun duration(from: Long, now: Long): String {
        val days = ((now - from) / DAY).coerceAtLeast(0)
        return when {
            days < 14 -> "$days d"
            days < 60 -> "${days / 7} wk"
            days < 365 -> "${days / 30} mo"
            else -> {
                val y = days / 365
                val m = (days % 365) / 30
                if (m == 0L) "$y y" else "$y y $m mo"
            }
        }
    }

    fun cmToIn(cm: Double): String = Fmt.trim(cm / 2.54, 1) + " in"

    fun card(l: Livestock, db: SpeciesDb, displayGallons: Double, now: Long): LivestockCardUi {
        val s = db[l.speciesId]
        val name = s?.common ?: l.speciesId
        return LivestockCardUi(
            id = l.id,
            speciesId = l.speciesId,
            kind = s?.kind,
            colors = s?.colors.orEmpty(),
            title = l.nickname.ifBlank { name },
            species = listOfNotNull(name, l.variant).joinToString(" · "),
            since = l.acquiredAt?.let { duration(it, now) },
            quantity = l.quantity,
            status = l.status,
            warning = l.status == LivestockStatus.ALIVE && db.outgrowsTank(l.speciesId, displayGallons),
            unverified = !l.verified,
            photoPath = l.photoPath,
        )
    }

    fun sections(all: List<Livestock>, db: SpeciesDb, displayGallons: Double, now: Long, showGone: Boolean): List<LivestockSection> {
        val order = listOf("Fish", "Inverts", "Corals", "Algae", "Other")
        return all.filter { showGone || it.status == LivestockStatus.ALIVE }
            .groupBy { db[it.speciesId]?.section ?: "Other" }
            .toList()
            .sortedBy { order.indexOf(it.first) }
            .map { (title, list) ->
                LivestockSection(
                    title,
                    list.sortedWith(compareBy({ it.status != LivestockStatus.ALIVE }, { (db[it.speciesId]?.common ?: it.speciesId) }, { it.nickname }))
                        .map { card(it, db, displayGallons, now) },
                )
            }
    }

    /** Tank-size warning text, e.g. "Adult size 12 in — needs 120 gal+". */
    fun warning(s: SpeciesSheet, displayGallons: Double): String? {
        val min = s.minTankGallons ?: return null
        if (min <= displayGallons) return null
        return "Adult size ${cmToIn(s.typicalAdultCm)} — needs ${Fmt.trim(min, 0)} gal+. This tank is ${Fmt.trim(displayGallons, 0)} gal."
    }

    /** Risks between [self] and every other living species in the tank, highest first. */
    fun compat(self: Livestock, mates: List<Livestock>, db: SpeciesDb): List<CompatUi> {
        val m = db.interactions
        val others = mates.filter { it.id != self.id && it.status == LivestockStatus.ALIVE }
        val bySpecies = others.groupBy { it.speciesId }
        return bySpecies.mapNotNull { (sid, list) ->
            if (sid == self.speciesId && m.entry(sid, sid) == null) return@mapNotNull null
            val risk = m.risk(self.speciesId, sid)
            val name = db[sid]?.common ?: sid
            val label = if (list.size > 1 && sid != self.speciesId) "$name ×${list.sumOf { it.quantity }}" else name
            CompatUi(label, risk, m.entry(self.speciesId, sid)?.note.orEmpty())
        }.sortedWith(compareByDescending<CompatUi> { it.risk.level }.thenBy { it.name })
    }

    fun detail(l: Livestock, mates: List<Livestock>, health: List<HealthLog>, db: SpeciesDb, displayGallons: Double, now: Long): LivestockDetailUi {
        val s = db[l.speciesId]
        val facts = buildList {
            if (s != null) {
                val now = l.sizeCm?.let { cmToIn(it) }
                add("Size" to listOfNotNull(now?.let { "$it now" }, "adult ${cmToIn(s.typicalAdultCm)}").joinToString(" · "))
                if (s.lifespanYears > 0) add("Lifespan" to "${Fmt.trim(s.lifespanYears, 0)} years")
                if (s.dietSummary.isNotBlank()) add("Diet" to s.dietSummary)
                if (s.temperamentSummary.isNotBlank()) add("Temperament" to s.temperamentSummary)
                add("Care level" to s.careLevel)
                add("Reef safe" to s.reefSafe)
                s.minTankGallons?.let { add("Minimum tank" to "${Fmt.trim(it, 0)} gal") }
                s.coral?.let { cInfo ->
                    add("Light" to "${(cInfo.lightLo * 100).toInt()}–${(cInfo.lightHi * 100).toInt()}% of fixture")
                    add("Flow" to cInfo.flow.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
            if (l.quantity > 1) add("Count" to "${l.quantity}")
            l.acquiredAt?.let { add("In tank" to "${duration(it, now)} · since ${TimeText.longDate(it)}") }
            l.source?.let { add("Source" to it) }
            l.priceCents?.let { add("Price" to "$" + Fmt.num(it / 100.0, 2)) }
            if (l.status != LivestockStatus.ALIVE) add("Status" to l.status.label + (l.statusChangedAt?.let { " · ${TimeText.longDate(it)}" } ?: ""))
        }
        return LivestockDetailUi(
            loading = false,
            card = card(l, db, displayGallons, now),
            scientific = s?.scientific.orEmpty(),
            warning = s?.let { warning(it, displayGallons) },
            facts = facts,
            careNotes = s?.careNotes.orEmpty(),
            compat = if (l.status == LivestockStatus.ALIVE) compat(l, mates, db) else emptyList(),
            health = health.sortedByDescending { it.at }.map { h ->
                HealthUi(h.id, TimeText.longDate(h.at), h.symptom, listOfNotNull(h.treatment, h.dose, h.outcome, h.note).joinToString(" · ").ifBlank { null })
            },
            notes = l.notes,
            canViewLive = l.status == LivestockStatus.ALIVE && s?.model != null,
        )
    }
}

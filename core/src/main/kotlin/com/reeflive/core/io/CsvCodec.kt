package com.reeflive.core.io

import com.reeflive.core.model.Measurement
import com.reeflive.core.model.TestSession
import com.reeflive.core.params.ParameterTable
import com.reeflive.core.units.ParamUnit
import com.reeflive.core.units.UnitConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** A measurement as one CSV row (DESIGN §6.7). [value] is expressed in [unit]. */
data class CsvRow(
    val takenAt: Long,
    val paramId: String,
    val value: Double,
    val unit: ParamUnit,
    val kit: String? = null,
    val sessionId: String? = null,
    val note: String? = null,
)

data class CsvImportResult(val rows: List<CsvRow>, val errors: List<String>, val header: List<String>)

/**
 * Measurements CSV: `timestamp_iso,parameter_id,value,unit,kit,session_id,note`.
 * Export writes values in the parameter's base unit with full precision so an import restores them exactly.
 * Import maps common header aliases, converts units to the base unit and reports per-line errors.
 */
object CsvCodec {
    val HEADER = listOf("timestamp_iso", "parameter_id", "value", "unit", "kit", "session_id", "note")

    private val ALIASES: Map<String, List<String>> = mapOf(
        "timestamp_iso" to listOf("timestamp_iso", "timestamp", "time", "date", "datetime", "taken_at", "date_time"),
        "parameter_id" to listOf("parameter_id", "parameter", "param", "param_id", "test"),
        "value" to listOf("value", "reading", "result", "amount"),
        "unit" to listOf("unit", "units"),
        "kit" to listOf("kit", "kit_brand", "brand", "test_kit"),
        "session_id" to listOf("session_id", "session"),
        "note" to listOf("note", "notes", "comment"),
    )

    fun export(sessions: List<TestSession>, measurements: List<Measurement>): String {
        val byId = sessions.associateBy { it.id }
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",")).append("\r\n")
        for (m in measurements.sortedWith(compareBy({ it.takenAt }, { it.paramId }))) {
            val s = byId[m.sessionId]
            val unit = ParameterTable.byId(m.paramId)?.baseUnit ?: m.enteredUnit
            val cells = listOf(
                Instant.ofEpochMilli(m.takenAt).toString(), m.paramId, m.value.toString(), unit.symbol,
                s?.kitBrand.orEmpty(), m.sessionId, s?.note.orEmpty(),
            )
            sb.append(cells.joinToString(",") { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }

    fun escape(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell

    /** RFC-4180-ish parser (quoted fields, doubled quotes, CRLF/LF). */
    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        val src = text.removePrefix("﻿")
        while (i < src.length) {
            val c = src[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < src.length && src[i + 1] == '"') { cell.append('"'); i++ } else inQuotes = false
                } else cell.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { row.add(cell.toString()); cell.setLength(0) }
                '\r' -> {}
                '\n' -> { row.add(cell.toString()); cell.setLength(0); rows.add(row); row = mutableListOf() }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    /** Index of each canonical column in [header], or -1. */
    fun mapHeader(header: List<String>): Map<String, Int> {
        val norm = header.map { it.trim().lowercase().replace(' ', '_') }
        return ALIASES.mapValues { (_, names) -> norm.indexOfFirst { it in names } }
    }

    fun import(text: String, zone: ZoneId = ZoneOffset.UTC, columnOverrides: Map<String, Int> = emptyMap()): CsvImportResult {
        val all = parseCsv(text)
        if (all.isEmpty()) return CsvImportResult(emptyList(), listOf("The file is empty"), emptyList())
        val header = all.first()
        val cols = mapHeader(header) + columnOverrides
        val errors = mutableListOf<String>()
        if (cols["timestamp_iso"]!! < 0 || cols["parameter_id"]!! < 0 || cols["value"]!! < 0) {
            return CsvImportResult(emptyList(), listOf("Need timestamp, parameter and value columns"), header)
        }
        val out = mutableListOf<CsvRow>()
        all.drop(1).forEachIndexed { idx, r ->
            val line = idx + 2
            fun col(name: String): String? = cols[name]?.takeIf { it >= 0 && it < r.size }?.let { r[it].trim() }
            val ts = col("timestamp_iso")?.let { parseTime(it, zone) }
            val pid = col("parameter_id")?.let { normaliseParam(it) }
            val v = col("value")?.replace(',', '.')?.toDoubleOrNull()
            if (ts == null || pid == null || v == null) {
                errors += "Line $line: unreadable row"
                return@forEachIndexed
            }
            val def = ParameterTable.byId(pid)
            val base = def?.baseUnit ?: ParamUnit.NONE
            val unit = col("unit")?.let { ParamUnit.parse(it) } ?: base
            val baseValue = try {
                if (def == null || unit == base || unit == ParamUnit.NONE) v else UnitConverter.convert(v, unit, base)
            } catch (e: IllegalArgumentException) {
                errors += "Line $line: unit ${unit.symbol} does not fit $pid"
                return@forEachIndexed
            }
            out += CsvRow(ts, pid, baseValue, base, col("kit")?.ifBlank { null }, col("session_id")?.ifBlank { null }, col("note")?.ifBlank { null })
        }
        return CsvImportResult(out, errors, header)
    }

    /** Accepts ISO instants, ISO local date-times (in [zone]) and plain dates. */
    fun parseTime(s: String, zone: ZoneId): Long? = runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(s.replace(' ', 'T')).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDate.parse(s).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
        ?: s.toLongOrNull()

    fun normaliseParam(s: String): String? {
        val t = s.trim().lowercase()
        if (t in ParameterTable.ALL_IDS) return t
        ParameterTable.SOFT_CORAL.firstOrNull { it.name.equals(s.trim(), true) || it.shortName.equals(s.trim(), true) }?.let { return it.id }
        return when (t) {
            "temperature" -> "temp"; "salinity", "sg" -> "sal"; "alkalinity", "kh", "dkh" -> "alk"; "calcium" -> "ca"
            "magnesium" -> "mg"; "nitrate" -> "no3"; "phosphate" -> "po4"; "ammonia" -> "nh3"; "nitrite" -> "no2"
            "potassium" -> "k"; "strontium" -> "sr"; "iodine" -> "i"; "oxygen", "dissolved_o2", "o2" -> "do"
            "silicate" -> "sio2"; "iron" -> "fe"
            else -> null
        }
    }
}

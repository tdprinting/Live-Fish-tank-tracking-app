package com.reeflive.core.io

import com.reeflive.core.model.LightCurve
import com.reeflive.core.model.LightSchedule
import com.reeflive.core.model.Livestock
import com.reeflive.core.model.Measurement
import com.reeflive.core.model.TestSession
import com.reeflive.core.units.ParamUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecTest {
    private val t0 = 1_760_000_000_000L

    private fun sample(): Pair<List<TestSession>, List<Measurement>> {
        val s1 = TestSession("s1", "tank", t0, "Salifert", "after \"big\" feed, cloudy")
        val s2 = TestSession("s2", "tank", t0 + 86_400_000, "Hanna", null)
        val ms = listOf(
            Measurement("m1", "s1", "tank", "alk", 8.6, ParamUnit.DKH, t0),
            Measurement("m2", "s1", "tank", "ca", 420.0, ParamUnit.PPM, t0),
            Measurement("m3", "s2", "tank", "alk", 7.2, ParamUnit.MEQ_L, t0 + 86_400_000),
            Measurement("m4", "s2", "tank", "po4", 0.037, ParamUnit.PPB, t0 + 86_400_000),
            Measurement("m5", "s2", "tank", "sal", 1.0251234567, ParamUnit.PPT, t0 + 86_400_000),
        )
        return listOf(s1, s2) to ms
    }

    @Test
    fun csvRoundTripKeepsRowsAndValues() {
        val (sessions, ms) = sample()
        val csv = CsvCodec.export(sessions, ms)
        assertTrue(csv.startsWith("timestamp_iso,parameter_id,value,unit,kit,session_id,note"))
        val res = CsvCodec.import(csv)
        assertEquals(emptyList<String>(), res.errors)
        assertEquals(ms.size, res.rows.size)
        val byKey = res.rows.associateBy { it.paramId to it.takenAt }
        for (m in ms) {
            val r = byKey[m.paramId to m.takenAt]!!
            assertEquals(m.value, r.value, 0.0)
            assertEquals(m.sessionId, r.sessionId)
        }
        assertEquals("after \"big\" feed, cloudy", byKey["alk" to t0]!!.note)
        assertEquals("Salifert", byKey["ca" to t0]!!.kit)
    }

    @Test
    fun csvImportMapsAliasesAndConvertsUnits() {
        val text = "Date,Parameter,Reading,Units,Brand\n2025-10-01 09:30,Alkalinity,3.0,meq/L,Hanna\n2025-10-01,Calcium,430,,\nbad,row\n"
        val res = CsvCodec.import(text)
        assertEquals(2, res.rows.size)
        assertEquals(1, res.errors.size)
        assertEquals("alk", res.rows[0].paramId)
        assertEquals(3.0 / 0.357, res.rows[0].value, 1e-9)
        assertEquals("Hanna", res.rows[0].kit)
        assertEquals("ca", res.rows[1].paramId)
    }

    @Test
    fun backupRoundTrip() {
        val (sessions, ms) = sample()
        val b = Backup(
            exportedAt = t0, sessions = sessions, measurements = ms,
            livestock = listOf(Livestock("l1", "tank", "paracanthurus_hepatus", "Blue", sizeCm = 6.5)),
            lightSchedules = listOf(LightSchedule("ls", "tank", "AI", LightCurve.AI_SOFT_CORAL)),
        )
        val back = BackupCodec.decode(BackupCodec.encode(b))
        assertEquals(b, back)
    }
}

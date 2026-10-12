package com.reeflive.app.data.repo

import com.reeflive.app.ui.nav.Routes
import com.reeflive.app.work.ExtrasReminders
import com.reeflive.core.extras.Supply
import com.reeflive.core.extras.TreatmentCase
import com.reeflive.core.extras.TreatmentKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset

class ExtrasRepositoryTest {
    private val clock = Clock { 1_790_000_000_000L }
    private fun repo(f: File) = ExtrasRepository(f, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), clock)

    @Test
    fun persistsAcrossInstancesAndRestocksIntoSpending() = runBlocking {
        val f = File(Files.createTempDirectory("extras").toFile(), ExtrasRepository.FILE_NAME)
        val a = repo(f)
        a.saveSupply(Supply("s", "Salt", quantity = 1.0, lowAt = 1.0))
        a.restock("s", 2.0, 60.0)
        a.logFeeding("Mysis")
        val b = repo(f).snapshot()
        assertEquals(3.0, b.supplies.single().quantity, 1e-9)
        assertEquals(60.0, b.expenses.single().amount, 1e-9)
        assertEquals(1, b.feedings.size)
    }

    @Test
    fun unreadableFileIsKeptAside() = runBlocking {
        val dir = Files.createTempDirectory("extras").toFile()
        val f = File(dir, ExtrasRepository.FILE_NAME).apply { writeText("{not json") }
        val r = repo(f)
        assertEquals(0, r.snapshot().supplies.size)
        assertTrue(File(dir, ExtrasRepository.FILE_NAME + ".bad").isFile)
    }

    @Test
    fun remindersForLowStockAndDueDoses() = runBlocking {
        val f = File(Files.createTempDirectory("extras").toFile(), ExtrasRepository.FILE_NAME)
        val r = repo(f)
        r.saveSupply(Supply("s", "Salt", quantity = 0.0))
        r.saveCase(TreatmentCase("c", "QT", TreatmentKind.COPPER, startAt = clock.now(), days = 14, doseEveryDays = 1))
        val out = ExtrasReminders.build(r.snapshot(), clock.now(), ZoneOffset.UTC)
        assertEquals(listOf(Routes.SUPPLIES, Routes.TREATMENTS), out.map { it.route })
        r.logDose("c")
        assertEquals(listOf(Routes.SUPPLIES), ExtrasReminders.build(r.snapshot(), clock.now(), ZoneOffset.UTC).map { it.route })
    }
}

package com.reeflive.app.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reeflive.app.di.AppGraph
import com.reeflive.app.ui.nav.reefViewModel
import com.reeflive.core.model.Tank
import com.reeflive.core.seed.SeedBundle
import com.reeflive.core.units.ParamUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(private val graph: AppGraph) : ViewModel() {
    private val bundle: SeedBundle = graph.seedInstaller.preview()
    private val _state = MutableStateFlow(
        OnboardingUi(
            tank = TankForm.from(bundle.tank),
            livestock = bundle.livestock.mapIndexed { i, l ->
                val sheet = graph.speciesDb[l.speciesId]
                LivestockChoice(
                    index = i, nickname = l.nickname, speciesId = l.speciesId,
                    speciesName = sheet?.common ?: l.speciesId, scientific = sheet?.scientific.orEmpty(),
                    kind = sheet?.kind, colors = sheet?.colors.orEmpty(), quantity = l.quantity, verified = l.verified,
                )
            },
        ),
    )
    val state: StateFlow<OnboardingUi> = _state.asStateFlow()

    fun setTank(f: TankForm) = _state.update { it.copy(tank = f) }
    fun toggle(index: Int) = _state.update { s -> s.copy(livestock = s.livestock.map { if (it.index == index) it.copy(checked = !it.checked) else it }) }
    fun setTemp(u: ParamUnit) = _state.update { it.copy(tempUnit = u) }
    fun setSal(u: ParamUnit) = _state.update { it.copy(salUnit = u) }
    fun setAlk(u: ParamUnit) = _state.update { it.copy(alkUnit = u) }
    fun setHour(h: Int) = _state.update { it.copy(reminderHour = h) }
    fun setNotificationsGranted(v: Boolean) = _state.update { it.copy(notificationsGranted = v) }

    fun finish(onDone: () -> Unit) {
        val s = _state.value
        if (s.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val f = s.tank
            val tank: Tank = if (f.valid) {
                bundle.tank.copy(
                    name = f.name.trim(), lengthCm = f.lengthCm!!, widthCm = f.widthCm!!, heightCm = f.heightCm!!,
                    displayGallons = f.volumeGallons!!, sumpGallons = com.reeflive.app.ui.components.parseDecimal(f.sump) ?: 0.0,
                    reefType = f.reefType,
                )
            } else bundle.tank
            val keep = s.livestock.filter { it.checked }.map { it.index }.toSet()
            graph.seedInstaller.install(bundle.copy(tank = tank, livestock = bundle.livestock.filterIndexed { i, _ -> i in keep }))
            graph.settings.setTempUnit(s.tempUnit)
            graph.settings.setSalUnit(s.salUnit)
            graph.settings.setAlkUnit(s.alkUnit)
            graph.settings.setReminderHour(s.reminderHour)
            graph.settings.setOnboarded(true)
            runCatching {
                graph.reminders.rescheduleAll()
                graph.reminders.scheduleDailyAlerts(replace = true)
            }
            onDone()
        }
    }
}

@Composable
fun OnboardingRoute(onDone: () -> Unit, animate: Boolean) {
    val vm = reefViewModel { OnboardingViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun granted() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    LaunchedEffect(Unit) { vm.setNotificationsGranted(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> vm.setNotificationsGranted(ok) }
    OnboardingContent(
        state = state,
        animate = animate,
        actions = OnboardingActions(
            onTank = vm::setTank,
            onToggle = vm::toggle,
            onTempUnit = vm::setTemp,
            onSalUnit = vm::setSal,
            onAlkUnit = vm::setAlk,
            onReminderHour = vm::setHour,
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setNotificationsGranted(true)
            },
            onFinish = { vm.finish(onDone) },
        ),
    )
}

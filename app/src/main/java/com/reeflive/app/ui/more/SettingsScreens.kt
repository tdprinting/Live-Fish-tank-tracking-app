package com.reeflive.app.ui.more

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reeflive.app.appGraph
import com.reeflive.app.data.repo.AppSettings
import com.reeflive.app.data.repo.LiveQuality
import com.reeflive.app.data.repo.ThemeMode
import com.reeflive.app.ui.common.ParamView
import com.reeflive.app.ui.components.ButtonKind
import com.reeflive.app.ui.components.EmptyState
import com.reeflive.app.ui.components.LocalSnackbar
import com.reeflive.app.ui.components.ParamGlyph
import com.reeflive.app.ui.components.ReefButton
import com.reeflive.app.ui.components.ReefDialog
import com.reeflive.app.ui.components.ScreenHeader
import com.reeflive.app.ui.components.SectionHeader
import com.reeflive.app.ui.components.SegmentedPills
import com.reeflive.app.ui.components.TimeOfDayDialog
import com.reeflive.app.ui.components.pagePadding
import com.reeflive.app.ui.onboarding.TankForm
import com.reeflive.app.ui.onboarding.TankFormFields
import com.reeflive.app.ui.theme.GlassCard
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.params.ParameterDef
import com.reeflive.core.units.ParamUnit
import com.reeflive.core.util.Fmt
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Shared rows

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionHeader(title)
    GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { content() }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = ReefTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.text, uncheckedTrackColor = c.glass))
    }
}

@Composable
private fun PillsRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = ReefTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text)
        Spacer(Modifier.height(6.dp))
        SegmentedPills(options, selected, onSelect, Modifier.fillMaxWidth())
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val c = ReefTheme.colors
    GlassCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textSecondary)
        }
    }
}

// ---------------------------------------------------------------- Settings

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onTemp: (ParamUnit) -> Unit = {},
    val onSal: (ParamUnit) -> Unit = {},
    val onAlk: (ParamUnit) -> Unit = {},
    val onReduceMotion: (Boolean) -> Unit = {},
    val onHaptics: (Boolean) -> Unit = {},
    val onGlassBlur: (Boolean) -> Unit = {},
    val onLiveQuality: (LiveQuality) -> Unit = {},
    val onNotifications: (Boolean) -> Unit = {},
    val onReminderTime: () -> Unit = {},
    val onRequestPermission: () -> Unit = {},
    val onTargets: () -> Unit = {},
    val onTank: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onResetSeed: () -> Unit = {},
    val onWipe: () -> Unit = {},
)

private val TEMP = listOf(ParamUnit.F, ParamUnit.C)
private val SAL = listOf(ParamUnit.SG, ParamUnit.PPT, ParamUnit.MS_CM)
private val ALK = listOf(ParamUnit.DKH, ParamUnit.MEQ_L, ParamUnit.PPM_CACO3)

/** Settings (DESIGN §5.14). */
@Composable
fun SettingsContent(s: AppSettings, permissionGranted: Boolean, actions: SettingsActions, modifier: Modifier = Modifier) {
    val c = ReefTheme.colors
    Column(modifier.fillMaxSize()) {
        ScreenHeader("Settings", onBack = actions.onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePadding(top = 0.dp))) {
            Group("Appearance") {
                PillsRow("Theme", listOf("System", "Abyss", "Lagoon"), listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT).indexOf(s.theme)) {
                    actions.onTheme(listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT)[it])
                }
                SwitchRow("Reduce motion", "Calmer transitions; caustics stand still", s.reduceMotion, actions.onReduceMotion)
                SwitchRow("Glass blur", "Frosted cards (turn off on slower phones)", s.glassBlur, actions.onGlassBlur)
                SwitchRow("Haptics", "Gentle taps when you save or complete", s.haptics, actions.onHaptics)
            }
            Group("Units") {
                PillsRow("Temperature", TEMP.map { it.symbol }, TEMP.indexOf(s.tempUnit).coerceAtLeast(0)) { actions.onTemp(TEMP[it]) }
                PillsRow("Salinity", SAL.map { it.symbol }, SAL.indexOf(s.salUnit).coerceAtLeast(0)) { actions.onSal(SAL[it]) }
                PillsRow("Alkalinity", listOf("dKH", "meq/L", "ppm"), ALK.indexOf(s.alkUnit).coerceAtLeast(0)) { actions.onAlk(ALK[it]) }
            }
            Group("Alerts & reminders") {
                SwitchRow("Notifications", "Danger alerts, task reminders and stale tests", s.notifications, actions.onNotifications)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily check", style = MaterialTheme.typography.bodyLarge, color = c.text)
                        Text("When ReefLive reviews your parameters", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
                    }
                    ReefButton("%02d:00".format(s.reminderHour), actions.onReminderTime, kind = ButtonKind.OUTLINED, compact = true)
                }
                if (!permissionGranted) {
                    Text("Notifications are blocked for ReefLive.", style = MaterialTheme.typography.bodySmall, color = c.cautionText)
                    Spacer(Modifier.height(6.dp))
                    ReefButton("Allow notifications", actions.onRequestPermission, kind = ButtonKind.TONAL, compact = true)
                    Spacer(Modifier.height(6.dp))
                }
            }
            Group("Live Tank") {
                PillsRow("Quality", listOf("Low", "Medium", "High"), s.liveQuality.ordinal) { actions.onLiveQuality(LiveQuality.entries[it]) }
            }
            SectionHeader("Tank & parameters")
            LinkRow("Targets & parameters", "Ranges, favorites, hide/show", actions.onTargets)
            LinkRow("Tank", "Size, volume, coral type", actions.onTank)
            SectionHeader("Data")
            LinkRow("Export & backup", "CSV, JSON backup and restore", actions.onExport)
            GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Text("Start over", style = MaterialTheme.typography.titleSmall, color = c.text)
                Text("Reset reloads Tee's tank, livestock, equipment, tasks and lights; your tests stay. Clear removes everything.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReefButton("Reset to seed", actions.onResetSeed, kind = ButtonKind.OUTLINED, compact = true)
                    ReefButton("Clear all data", actions.onWipe, kind = ButtonKind.DESTRUCTIVE, compact = true)
                }
            }
        }
    }
}

@Composable
fun SettingsRoute(onBack: () -> Unit, onTargets: () -> Unit, onTank: () -> Unit, onExport: () -> Unit, onWiped: () -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.appGraph
    val s by g.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    fun granted() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var permission by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { permission = granted() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> permission = ok }
    var pickTime by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(0) }
    fun go(block: suspend () -> Unit) { scope.launch { block() } }

    SettingsContent(
        s, permission,
        SettingsActions(
            onBack = onBack,
            onTheme = { go { g.settings.setTheme(it) } },
            onTemp = { go { g.settings.setTempUnit(it) } },
            onSal = { go { g.settings.setSalUnit(it) } },
            onAlk = { go { g.settings.setAlkUnit(it) } },
            onReduceMotion = { go { g.settings.setReduceMotion(it) } },
            onHaptics = { go { g.settings.setHaptics(it) } },
            onGlassBlur = { go { g.settings.setGlassBlur(it) } },
            onLiveQuality = { go { g.settings.setLiveQuality(it) } },
            onNotifications = { on ->
                go { g.settings.setNotifications(on) }
                if (on && !permission && Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onReminderTime = { pickTime = true },
            onRequestPermission = { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            onTargets = onTargets,
            onTank = onTank,
            onExport = onExport,
            onResetSeed = { confirm = 1 },
            onWipe = { confirm = 2 },
        ),
    )
    if (pickTime) {
        TimeOfDayDialog(s.reminderHour, 0, title = "Daily check", onPick = { h, _ ->
            pickTime = false
            go {
                g.settings.setReminderHour(h)
                runCatching { g.reminders.scheduleDailyAlerts(replace = true) }
            }
        }, onDismiss = { pickTime = false })
    }
    when (confirm) {
        1 -> ReefDialog(
            "Reset to Tee's tank?",
            "Tank, livestock, equipment, tasks and the light schedule return to the seeded setup. Test results and the journal are kept.",
            "Reset",
            onConfirm = {
                confirm = 0
                go {
                    g.seedInstaller.install()
                    runCatching { g.reminders.rescheduleAll() }
                    snackbar.showSnackbar("Tank reset to the seed")
                }
            },
            onDismiss = { confirm = 0 },
        )
        2 -> ReefDialog(
            "Clear all data?",
            "Every test, task, livestock entry, journal note and photo record on this phone is deleted. Export a backup first if you might want it back.",
            "Clear everything",
            destructive = true,
            onConfirm = {
                confirm = 0
                go {
                    g.backup.wipeAll()
                    runCatching { g.reminders.rescheduleAll() }
                    g.settings.setOnboarded(false)
                    onWiped()
                }
            },
            onDismiss = { confirm = 0 },
        )
    }
}

// ---------------------------------------------------------------- Tank

@Composable
fun TankSettingsRoute(onBack: () -> Unit) {
    val g = LocalContext.current.appGraph
    val tank by g.tank.tank.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var form by remember { mutableStateOf<TankForm?>(null) }
    val t = tank
    val f = form ?: t?.let(TankForm::from)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Tank", onBack = onBack)
        if (f == null || t == null) {
            EmptyState("No tank yet", "Finish onboarding to set up your tank.")
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePadding())) {
            GlassCard(Modifier.fillMaxWidth()) { TankFormFields(f) { form = it } }
            Spacer(Modifier.height(14.dp))
            ReefButton("Save", {
                val n = f
                scope.launch {
                    g.tank.save(
                        t.copy(
                            name = n.name.trim(), lengthCm = n.lengthCm ?: t.lengthCm, widthCm = n.widthCm ?: t.widthCm,
                            heightCm = n.heightCm ?: t.heightCm, displayGallons = n.volumeGallons ?: t.displayGallons,
                            sumpGallons = com.reeflive.app.ui.components.parseDecimal(n.sump) ?: t.sumpGallons, reefType = n.reefType,
                        ),
                    )
                    if (n.reefType != t.reefType) g.alertEvaluator.runNow(notify = false)
                    form = null
                    snackbar.showSnackbar("Tank saved")
                }
            }, Modifier.fillMaxWidth(), enabled = form != null && f.valid)
        }
    }
}

// ---------------------------------------------------------------- Targets

/** All parameters: favorite (dashboard), visible, ranges at a glance; tap opens the detail with the inline editor. */
@Composable
fun TargetsRoute(onBack: () -> Unit, onOpenParam: (String) -> Unit) {
    val g = LocalContext.current.appGraph
    val defs by g.targets.all.collectAsStateWithLifecycle(emptyList())
    val overrides by g.targets.overrides.collectAsStateWithLifecycle(emptyMap())
    val settings by g.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var confirmReset by remember { mutableStateOf(false) }
    val c = ReefTheme.colors
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pagePadding(top = 0.dp)) {
        item(key = "h") {
            ScreenHeader("Targets & parameters", onBack = onBack, modifier = Modifier.padding(horizontal = 0.dp))
            Text("Star a parameter to pin it on the dashboard; the eye hides it from logging and lists. Tap for ranges.", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
            Spacer(Modifier.height(8.dp))
        }
        items(defs, key = { it.id }) { d ->
            val hidden = overrides[d.id]?.hidden == true
            TargetRow(d, ParamView(d, settings.displayUnitFor(d.id, d.baseUnit)), hidden, edited = overrides[d.id]?.idealLo != null,
                onOpen = { onOpenParam(d.id) },
                onFavorite = { scope.launch { g.targets.setFavorite(d.id, !d.favorite) } },
                onHidden = { scope.launch { g.targets.setHidden(d.id, !hidden) } },
            )
        }
        item(key = "reset") {
            Spacer(Modifier.height(12.dp))
            ReefButton("Reset all to defaults", { confirmReset = true }, Modifier.fillMaxWidth(), kind = ButtonKind.OUTLINED)
        }
    }
    if (confirmReset) {
        ReefDialog("Reset all targets?", "Ranges, favorites, order and hidden parameters go back to the defaults for your coral type.", "Reset", onConfirm = {
            confirmReset = false
            scope.launch {
                g.targets.resetAll(defs.map { it.id })
                g.alertEvaluator.runNow(notify = false)
                snackbar.showSnackbar("Targets reset")
            }
        }, onDismiss = { confirmReset = false })
    }
}

@Composable
private fun TargetRow(d: ParameterDef, v: ParamView, hidden: Boolean, edited: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit, onHidden: () -> Unit) {
    val c = ReefTheme.colors
    GlassCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = onOpen, contentPadding = PaddingValues(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), fillAlpha = if (hidden) 0.5f else 1f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ParamGlyph(d.id, size = 20.dp, tint = if (hidden) c.textTertiary else c.primaryBright)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name + if (edited) " · custom" else "", style = MaterialTheme.typography.bodyLarge, color = if (hidden) c.textTertiary else c.text)
                val ideal = v.range(d.ideal)
                Text("Ideal ${Fmt.num(ideal.lo, v.precision)}–${Fmt.num(ideal.hi, v.precision)} ${v.symbol}", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
            IconButton(onClick = onFavorite) {
                Icon(if (d.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (d.favorite) "Unpin ${d.name}" else "Pin ${d.name}", tint = if (d.favorite) c.sand else c.textSecondary)
            }
            IconButton(onClick = onHidden) {
                Icon(if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (hidden) "Show ${d.name}" else "Hide ${d.name}", tint = c.textSecondary)
            }
        }
    }
}

// ---------------------------------------------------------------- About

@Composable
fun AboutRoute(onBack: () -> Unit) {
    val c = ReefTheme.colors
    val g = LocalContext.current.appGraph
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("About", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pagePadding()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard(Modifier.fillMaxWidth(), tint = c.primary) {
                Text("ReefLive", style = MaterialTheme.typography.headlineMedium, color = c.text)
                Text("Track your reef. Watch it live.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                Text("Version 0.1 · ${g.speciesDb.size} species · offline, no account", style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Your data", style = MaterialTheme.typography.titleSmall, color = c.text)
                Text("Everything stays on this phone. Nothing is uploaded; export a backup to move it.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Advice, not a diagnosis", style = MaterialTheme.typography.titleSmall, color = c.text)
                Text("Targets and dosing math follow common hobby references. Change slowly, test again, and trust what your corals tell you.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Credits", style = MaterialTheme.typography.titleSmall, color = c.text)
                Text("Fonts: Outfit and Inter (SIL Open Font License). Icons: Material Symbols (Apache 2.0). 3D models and species art made for ReefLive.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
    }
}

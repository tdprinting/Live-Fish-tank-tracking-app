package com.reeflive.app.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reeflive.app.ui.theme.ReefTheme
import com.reeflive.core.custom.TankBuild
import com.reeflive.core.custom.TankBuilds
import kotlin.math.roundToInt

/**
 * "My tank" before there is one: pick a tank to set up. It starts clean and empty (cycled, ready for animals); then
 * Add life fills it, and everything in it is saved on the phone from then on.
 */
@Composable
fun MyTankSetupCard(onPick: (TankBuild) -> Unit, modifier: Modifier = Modifier) {
    val s = LiveTheme.style
    val c = ReefTheme.colors
    LivePanel(modifier.widthIn(max = 460.dp).fillMaxWidth()) {
        Text("Set up your own tank", style = s.titleText, color = s.label)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pick a tank. It starts clean and empty, then add any animals you like from Add life. " +
                "Everything is saved: names, personalities, how much they grow.",
            style = MaterialTheme.typography.bodyMedium, color = c.text,
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TankBuilds.all.forEach { b ->
                LivePanel(Modifier.fillMaxWidth(), tint = Candy.green, onClick = { onPick(b) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.label, style = s.buttonText.copy(fontSize = 16.sp), color = s.label)
                            Text("${b.gallons.roundToInt()} gal · ${b.sizeLabel}", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(b.blurb, style = MaterialTheme.typography.bodySmall, color = c.text)
                }
            }
        }
    }
}


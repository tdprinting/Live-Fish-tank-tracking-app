package com.reeflive.app.ui.live

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.reeflive.app.audio.RecordSleeves
import com.reeflive.core.radio.RadioStation

/**
 * The record shelf: the radio's music settings. Every record sleeve, the one playing ringed; tap one you own to put it
 * on, tap a locked one to buy it with sand dollars (My tank only: [coins] is null elsewhere). Music on/off and the
 * music volume sit at the top.
 */
@Composable
fun RecordShelf(
    on: Boolean,
    playing: Int,
    owned: Set<String>,
    coins: Int?,
    volume: Float,
    onToggle: () -> Unit,
    onVolume: (Float) -> Unit,
    onPlay: (Int) -> Unit,
    onBuy: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LiveTheme.style
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 460.dp).livePanel(s).background(s.panel.copy(alpha = 0.9f)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Record shelf", style = s.titleText, color = s.label, modifier = Modifier.weight(1f))
                if (coins != null) Text("$coins sand dollars", style = MaterialTheme.typography.labelMedium, color = Candy.yellow)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LivePill(if (on) "Music on" else "Music off", onToggle, tone = if (on) Candy.green else Candy.white)
                var v by remember(volume) { mutableFloatStateOf(volume) }
                Slider(
                    value = v, onValueChange = { v = it }, onValueChangeFinished = { onVolume(v) },
                    colors = SliderDefaults.colors(thumbColor = Candy.purple, activeTrackColor = Candy.purple),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Music volume" },
                )
                Text("${(v * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = s.labelDim)
            }
            LazyVerticalGrid(
                GridCells.Adaptive(96.dp),
                Modifier.heightIn(max = 440.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(RadioStation.songs) { i, song ->
                    val mine = RadioStation.playable(song, owned)
                    val current = on && i == playing
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp)).clickable { if (mine) onPlay(i) else onBuy(i) }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painterResource(RecordSleeves.of(song.file)), song.title,
                                Modifier.fillMaxWidth().aspectRatio(1f).alpha(if (mine) 1f else 0.45f)
                                    .then(if (current) Modifier.border(3.dp, Candy.green, CircleShape) else Modifier),
                            )
                            if (!mine) Text(
                                "${song.price}", color = Color.Black, fontSize = 14.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Candy.yellow).padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            song.title, style = MaterialTheme.typography.labelSmall, color = if (current) Candy.green else s.label,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        )
                        Text(
                            if (current) "Playing" else if (mine) song.blurb else if (coins != null) "Buy" else "Buy in My tank",
                            style = MaterialTheme.typography.labelSmall, color = s.labelDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LivePill("Done", onDismiss, tone = Candy.cyan) }
        }
    }
}

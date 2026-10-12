package com.reeflive.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.reeflive.app.appGraph
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme

/** A photo stored under app files (path relative to filesDir), cropped to fill [modifier]'s size. */
@Composable
fun StoredPhoto(path: String, contentDescription: String?, modifier: Modifier = Modifier, shape: Shape = ReefShapes.tile) {
    val g = LocalContext.current.appGraph
    val file = remember(path) { g.photos.file(path) }
    Box(modifier.clip(shape).background(ReefTheme.colors.glass)) {
        AsyncImage(model = file, contentDescription = contentDescription, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
    }
}

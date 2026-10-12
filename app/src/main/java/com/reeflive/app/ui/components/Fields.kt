package com.reeflive.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.reeflive.app.ui.theme.ReefMotion
import com.reeflive.app.ui.theme.ReefShapes
import com.reeflive.app.ui.theme.ReefTheme

/**
 * Glass text field (UI_STYLE §6.5): 6 % white fill, 14 dp radius, 1 dp border that turns
 * lagoonBright 1.5 dp with a soft outer glow when focused. Optional [suffix] (unit) in mist.
 */
@Composable
fun ReefTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    suffix: (@Composable () -> Unit)? = null,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    imeAction: ImeAction = ImeAction.Done,
    onIme: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    textStyle: TextStyle? = null,
    error: Boolean = false,
) {
    val c = ReefTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        when { error -> c.danger; focused -> c.primaryBright; else -> c.glassBorder },
        ReefMotion.tween(ReefTheme.reduceMotion, ReefMotion.FAST), label = "field",
    )
    val style = (textStyle ?: if (numeric) ReefTheme.numbers.field else MaterialTheme.typography.bodyLarge).copy(color = c.text)
    Column(modifier) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            Spacer(Modifier.padding(top = 6.dp))
        }
        var fm = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (focused) drawRoundRect(c.primaryBright.copy(alpha = 0.20f), cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(6.dp.toPx()))
            }
            .clip(ReefShapes.field)
            .background(c.field)
            .border(if (focused) 1.5.dp else 1.dp, borderColor, ReefShapes.field)
            .heightIn(min = if (numeric) 52.dp else 48.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
        if (focusRequester != null) fm = fm.focusRequester(focusRequester)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = style,
            cursorBrush = SolidColor(c.primaryBright),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onIme?.invoke() }),
            modifier = fm,
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, style = style.copy(color = c.textTertiary))
                        inner()
                    }
                    if (suffix != null) {
                        Spacer(Modifier.width(8.dp))
                        suffix()
                    }
                }
            },
        )
    }
}

/** Parses user decimal input, accepting a comma as decimal separator. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** Simple label/value row for facts and stats. */
@Composable
fun FactRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    val c = ReefTheme.colors
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = ReefTheme.numbers.body, color = valueColor ?: c.text)
    }
}

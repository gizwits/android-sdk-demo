package com.gizwits.smart.app.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
fun SmartSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.onPrimary,
        disabledThumbColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
        activeTrackColor = MaterialTheme.colorScheme.onPrimary,
        disabledActiveTrackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
        inactiveTrackColor = MaterialTheme.colorScheme.primary,
        disabledInactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    ),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource
    )
}
package com.ridesniper.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridesniper.app.model.Recommendation
import com.ridesniper.app.overlay.colorFor
import com.ridesniper.app.overlay.labelFor
import com.ridesniper.app.ui.theme.TakeGreen
import com.ridesniper.app.util.Fmt
import kotlinx.coroutines.delay

/** The app's standard container: rounded surface with a hairline border. Resizes smoothly. */
@Composable
fun RsCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/** Small upper-case label above a group of content. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/** Large page title with an optional line underneath and an optional element on the right. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

/** "Running" (green, pulsing dot) or "Off" (grey). */
@Composable
fun StatusPill(running: Boolean) {
    val color by animateColorAsState(
        if (running) TakeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "statusColor"
    )
    val pulse = rememberInfiniteTransition(label = "statusPulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "statusDot"
    )
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .graphicsLayer { alpha = if (running) dotAlpha else 1f }
                .background(color, CircleShape)
        )
        Text(if (running) "Running" else "Off", style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** Coloured TAKE / MAYBE / DECLINE pill. */
@Composable
fun RecommendationBadge(recommendation: Recommendation) {
    val color = colorFor(recommendation)
    Text(
        text = labelFor(recommendation),
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** Friendly placeholder for screens with nothing to show yet. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private enum class SaveState { IDLE, CAN_SAVE, SAVED }

/**
 * Number input that saves explicitly: a tick button appears once the value
 * changes, and turns into a green "saved" check for a moment after saving.
 * Accepts "22,50" or "22.50" and shows an error for out-of-range values.
 */
@Composable
fun NumberSettingField(
    label: String,
    value: Double,
    onSave: (Double) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    decimals: Int = 2,
    validRange: ClosedFloatingPointRange<Double>? = null
) {
    var text by remember(value) { mutableStateOf(Fmt.number(value, decimals)) }
    var justSaved by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current

    val parsed = Fmt.parseDecimal(text)
    val valid = parsed != null && (validRange == null || parsed in validRange)
    val changed = parsed != null && Fmt.number(parsed, decimals) != Fmt.number(value, decimals)

    fun save() {
        if (parsed == null || !valid || !changed) return
        onSave(parsed)
        justSaved = true
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        focusManager.clearFocus()
    }

    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(1800)
            justSaved = false
        }
    }

    val state = when {
        justSaved -> SaveState.SAVED
        valid && changed -> SaveState.CAN_SAVE
        else -> SaveState.IDLE
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            justSaved = false
        },
        label = { Text(label) },
        suffix = if (unit != null) {
            { Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        isError = !valid,
        supportingText = if (!valid) {
            {
                Text(
                    if (validRange != null) {
                        "Enter a number from ${Fmt.number(validRange.start, 0)} to ${Fmt.number(validRange.endInclusive, 0)}"
                    } else {
                        "Enter a number"
                    }
                )
            }
        } else null,
        trailingIcon = {
            AnimatedContent(
                targetState = state,
                transitionSpec = { (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.7f)) togetherWith fadeOut(tween(100)) },
                label = "saveState"
            ) { s ->
                when (s) {
                    SaveState.SAVED -> Icon(Icons.Filled.CheckCircle, contentDescription = "Saved", tint = TakeGreen)
                    SaveState.CAN_SAVE -> IconButton(onClick = { save() }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save $label", tint = MaterialTheme.colorScheme.primary)
                    }
                    SaveState.IDLE -> Spacer(Modifier.size(24.dp))
                }
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save() }),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    )
}

/** Whole-row toggle: tapping the text flips the switch too. */
@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Label + value stacked, used for small numbers like R/km. */
@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}

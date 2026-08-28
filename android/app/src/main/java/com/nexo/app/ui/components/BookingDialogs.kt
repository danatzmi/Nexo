package com.nexo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * Centered, auto-dismissing success confirmation — mirrors iOS's
 * `BookingSuccessCard`/`.bookingSuccessOverlay`. Must be composed as a
 * sibling drawn after the screen's main content (inside an outer `Box`)
 * so it renders on top; it never intercepts touches (no `clickable`
 * anywhere in this tree), matching iOS's `.allowsHitTesting(false)`, and
 * dismisses itself via [onDismissed] after ~1.8s — if the screen is
 * navigated away from first, the enclosing composition is disposed and
 * this timer is simply cancelled, never crashing or blocking navigation.
 */
@Composable
fun BookingSuccessPopup(
    visible: Boolean,
    title: String,
    message: String,
    isWaitlist: Boolean,
    onDismissed: () -> Unit
) {
    LaunchedEffect(visible) {
        if (visible) {
            delay(1800)
            onDismissed()
        }
    }

    // No wrapping Box here — the caller places this inside an outer
    // `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center)`
    // alongside the screen's main content, so it centers over the whole
    // screen without this composable needing to know the screen's size.
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)) + scaleIn(
            initialScale = 0.4f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        ),
        exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.92f, animationSpec = tween(250))
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            shadowElevation = 20.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.widthIn(max = 260.dp).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(if (isWaitlist) Color(0xFFFF9500) else Color(0xFF34C759), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isWaitlist) Icons.Filled.Schedule else Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Centered confirmation card — mirrors iOS's `CancelBookingCard`/`LeaveWaitlistCard` shape (stacked full-width pill buttons), used by both [CancelBookingDialog] and [LeaveWaitlistDialog]. */
@Composable
private fun ConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnClickOutside = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            shadowElevation = 20.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.widthIn(max = 300.dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    text = body,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Column(modifier = Modifier.padding(top = 20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onConfirm,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(confirmLabel, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = onDismiss, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                        Text(dismissLabel)
                    }
                }
            }
        }
    }
}

/** "Cancel Booking?" confirmation — mirrors iOS's `CancelBookingCard`. */
@Composable
fun CancelBookingDialog(classTitle: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmationDialog(
        title = "Cancel Booking?",
        body = "Are you sure you want to cancel your booking for $classTitle?",
        confirmLabel = "Cancel Booking",
        dismissLabel = "Keep Booking",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * "Leave Waitlist?" confirmation — mirrors iOS's `LeaveWaitlistCard`. The
 * secondary button reads "Keep Waiting" (matching `LeaveWaitlistCard.swift`
 * exactly) rather than FEEDBACK.md's "Stay on Waitlist" — the real iOS
 * source takes precedence over the spec's description of it, per this
 * project's established precedent.
 */
@Composable
fun LeaveWaitlistDialog(classTitle: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmationDialog(
        title = "Leave Waitlist?",
        body = "Are you sure you want to leave the waitlist for $classTitle?",
        confirmLabel = "Leave Waitlist",
        dismissLabel = "Keep Waiting",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

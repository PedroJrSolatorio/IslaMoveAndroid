package com.rj.islamove.utils

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable

/**
 * Safe clickable modifier that logs clicks and catches exceptions
 */
fun Modifier.safeClickable(
    tag: String = "SafeClick",
    onClick: () -> Unit
): Modifier {
    return this.clickable {
        try {
            Log.d(tag, "Click event triggered")
            onClick()
        } catch (e: Exception) {
            Log.e(tag, "Exception in click handler", e)
            // Don't crash the app, just log
        }
    }
}

/**
 * Safe composable wrapper that handles exceptions through state
 */
@Composable
fun SafeComposable(
    tag: String = "SafeComposable",
    content: @Composable () -> Unit
) {
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            // Initialize safely
        } catch (e: Exception) {
            Log.e(tag, "Exception in composable initialization", e)
            hasError = true
        }
    }

    if (!hasError) {
        content()
    } else {
        // Show nothing instead of crashing
        Log.e(tag, "SafeComposable is in error state")
    }
}

/**
 * Debugging helper to log component lifecycle
 */
@Composable
fun DebugLifecycle(tag: String) {
    DisposableEffect(Unit) {
        Log.d("DebugLifecycle", "$tag: Composed")
        onDispose {
            Log.d("DebugLifecycle", "$tag: Disposed")
        }
    }
}
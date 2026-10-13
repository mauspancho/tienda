package com.mauspancho.tiendapos.ui.pos

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/** Play only confirmed additions while the scanner is visible and the app is active. */
@Composable
internal fun ScanSound(additions: Flow<Unit>) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }
    DisposableEffect(tone) {
        onDispose { tone?.release() }
    }
    LaunchedEffect(additions, lifecycle, tone) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            additions.collect { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) }
        }
    }
}

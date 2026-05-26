package com.example.exoplayerdummy.player.config

import android.util.Log
import androidx.media3.exoplayer.DefaultLoadControl

object BufferingStrategy {

    private const val TAG = "BufferingStrategy"

    fun vodConfig(): DefaultLoadControl {
        Log.d(TAG, "VOD buffering config: min=25s max=60s")
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs= */ 25_000,
                /* maxBufferMs= */ 60_000,
                /* bufferForPlaybackMs= */ 2_500,
                /* bufferForPlaybackAfterRebufferMs= */ 5_000
            )
            .build()
    }

    fun liveConfig(): DefaultLoadControl {
        Log.d(TAG, "LIVE buffering config: min=15s max=30s")
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs= */ 15_000,
                /* maxBufferMs= */ 30_000,
                /* bufferForPlaybackMs= */ 1_500,
                /* bufferForPlaybackAfterRebufferMs= */ 3_000
            )
            .build()
    }

    fun lowLatencyConfig(): DefaultLoadControl {
        Log.d(TAG, "LOW-LATENCY buffering config: min=5s max=10s")
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs= */ 5_000,
                /* maxBufferMs= */ 10_000,
                /* bufferForPlaybackMs= */ 500,
                /* bufferForPlaybackAfterRebufferMs= */ 1_500
            )
            .build()
    }
}

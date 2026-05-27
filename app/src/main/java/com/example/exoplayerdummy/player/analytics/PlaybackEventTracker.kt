package com.example.exoplayerdummy.player.analytics
import androidx.media3.common.Format
import com.example.exoplayerdummy.AppLogger as Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData

@UnstableApi
class PlaybackEventTracker : AnalyticsListener {

    companion object {
        private const val TAG = "PlaybackEventTracker"
    }

    private var sessionStartMs: Long = 0L
    private var firstFrameDelivered: Boolean = false
    private var stallCount: Int = 0
    private var stallStartMs: Long = 0L
    private var cumulativeStallMs: Long = 0L
    private var lastBandwidthBps: Long = 0L

    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        val label = when (state) {
            Player.STATE_IDLE -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY -> "READY"
            Player.STATE_ENDED -> "ENDED"
            else -> "UNKNOWN($state)"
        }
        Log.i(TAG, "State → $label | pos=${eventTime.currentPlaybackPositionMs}ms")

        when (state) {
            Player.STATE_BUFFERING -> {
                stallStartMs = System.currentTimeMillis()
                if (firstFrameDelivered) {
                    stallCount++
                    Log.w(TAG, "Stall #$stallCount at pos=${eventTime.currentPlaybackPositionMs}ms")
                }
            }
            Player.STATE_READY -> {
                if (stallStartMs > 0L) {
                    cumulativeStallMs += System.currentTimeMillis() - stallStartMs
                    Log.i(TAG, "Stall resolved | total stall=${cumulativeStallMs}ms")
                    stallStartMs = 0L
                }
            }
            Player.STATE_ENDED -> {
                Log.i(TAG, "Session ended — stalls=$stallCount total_stall=${cumulativeStallMs}ms")
            }
        }
    }

    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long
    ) {
        if (!firstFrameDelivered) {
            firstFrameDelivered = true
            val ttff = if (sessionStartMs > 0L) System.currentTimeMillis() - sessionStartMs else renderTimeMs
            Log.i(TAG, "First frame — TTFF=${ttff}ms (target <2000ms)")
        }
    }

    override fun onPlayWhenReadyChanged(
        eventTime: AnalyticsListener.EventTime,
        playWhenReady: Boolean,
        reason: Int
    ) {
        if (playWhenReady && !firstFrameDelivered) {
            sessionStartMs = System.currentTimeMillis()
            Log.i(TAG, "Play requested — TTFF timer started")
        }
    }

    override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) {
        Log.i(TAG, "Resolution: ${videoSize.width}x${videoSize.height}")
    }

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long
    ) {
        Log.w(TAG, "Dropped $droppedFrames frames in ${elapsedMs}ms")
    }

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        Log.e(TAG, "Playback error [${error.errorCodeName}] (code=${error.errorCode}) at pos=${eventTime.currentPlaybackPositionMs}ms: ${error.message}", error)
        when {
            error.errorCode in 1000..1999 -> Log.e(TAG, "Category: I/O / network error")
            error.errorCode in 2000..2999 -> Log.e(TAG, "Category: Content / format error")
            error.errorCode in 3000..3999 -> Log.e(TAG, "Category: Decoder / renderer error")
            error.errorCode in 4000..4999 -> Log.e(TAG, "Category: DRM / license error")
        }
    }

    override fun onBandwidthEstimate(
        eventTime: AnalyticsListener.EventTime,
        totalLoadTimeMs: Int,
        totalBytesLoaded: Long,
        bitrateEstimate: Long
    ) {
        val significantChange = lastBandwidthBps == 0L ||
                kotlin.math.abs(bitrateEstimate - lastBandwidthBps) > lastBandwidthBps * 0.2
        if (significantChange) {
            Log.d(TAG, "Bandwidth ~${bitrateEstimate / 1_000}kbps | loaded=${totalBytesLoaded / 1024}KB in ${totalLoadTimeMs}ms")
            lastBandwidthBps = bitrateEstimate
        }
    }

    override fun onLoadStarted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData
    ) {
        Log.v(TAG, "Load started: ${loadEventInfo.uri.lastPathSegment}")
    }

    override fun onLoadCompleted(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData
    ) {
        Log.v(TAG, "Load done: ${loadEventInfo.bytesLoaded / 1024}KB in ${loadEventInfo.loadDurationMs}ms")
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?
    ) {
        super.onVideoInputFormatChanged(eventTime, format, decoderReuseEvaluation)
    }

    fun resetSession() {
        sessionStartMs = 0L
        firstFrameDelivered = false
        stallCount = 0
        stallStartMs = 0L
        cumulativeStallMs = 0L
        lastBandwidthBps = 0L
        Log.d(TAG, "Session reset")
    }
}

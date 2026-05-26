package com.example.exoplayerdummy.player.controller

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.exoplayerdummy.domain.model.StreamProtocol
import com.example.exoplayerdummy.domain.model.Video
import com.example.exoplayerdummy.player.analytics.PlaybackEventTracker
import com.example.exoplayerdummy.player.config.BufferingStrategy
import com.example.exoplayerdummy.player.config.MediaCacheManager
import com.example.exoplayerdummy.player.network.AdaptiveRetryPolicy

@UnstableApi
class ExoPlaybackController(
    private val context: Context,
    private val cacheManager: MediaCacheManager
) : PlaybackController {

    companion object {
        private const val TAG = "ExoPlaybackController"
    }

    override var player: ExoPlayer? = null
        private set

    private var trackSelector: DefaultTrackSelector? = null
    private val eventTracker = PlaybackEventTracker()

    override fun initPlayer(isLive: Boolean): ExoPlayer {
        Log.i(TAG, "Initialising player (isLive=$isLive)")
        releasePlayer()

        trackSelector = DefaultTrackSelector(context).apply {
            parameters = parameters.buildUpon()
                .setMaxVideoSizeSd()
                .setPreferredTextLanguage("en")
                .build()
        }

        val bufferingConfig = if (isLive) BufferingStrategy.liveConfig() else BufferingStrategy.vodConfig()
        val mediaSourceFactory = DefaultMediaSourceFactory(cacheManager.buildDataSourceFactory())
            .setLoadErrorHandlingPolicy(AdaptiveRetryPolicy())

        player = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector!!)
            .setLoadControl(bufferingConfig)
            .setMediaSourceFactory(mediaSourceFactory)
            .setSeekForwardIncrementMs(10_000)
            .setSeekBackIncrementMs(10_000)
            .build()
            .also { exo ->
                exo.addAnalyticsListener(eventTracker)
                exo.playWhenReady = true
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            trackSelector?.parameters = trackSelector!!.parameters
                                .buildUpon()
                                .clearVideoSizeConstraints()
                                .build()
                            Log.d(TAG, "Video size constraints cleared after STATE_READY")
                        }
                    }
                })
            }

        Log.i(TAG, "Player ready")
        return player!!
    }

    override fun prepareMedia(video: Video) {
        val p = player ?: run {
            Log.e(TAG, "prepareMedia called before initPlayer")
            return
        }

        Log.i(TAG, "Preparing: ${video.title} [${video.protocol}] live=${video.isLiveStream}")
        eventTracker.resetSession()

        val builder = MediaItem.Builder()
            .setUri(video.streamUrl)
            .setMediaId(video.id)

        when (video.protocol) {
            StreamProtocol.HLS -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            StreamProtocol.DASH -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
            StreamProtocol.PROGRESSIVE -> { /* auto-detected */ }
        }

        video.protection?.let { protection ->
            Log.i(TAG, "DRM: Widevine @ ${protection.licenseUrl}")
            builder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(protection.licenseUrl)
                    .setMultiSession(protection.multiSession)
                    .build()
            )
        }

        if (video.captions.isNotEmpty()) {
            builder.setSubtitleConfigurations(video.captions.map { caption ->
                SubtitleConfiguration.Builder(Uri.parse(caption.uri))
                    .setMimeType(caption.mimeType)
                    .setLanguage(caption.language)
                    .setLabel(caption.label)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            })
        }

        if (video.isLiveStream) {
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder().setMaxPlaybackSpeed(1.02f).build()
            )
        }

        if (video.adsEnabled && video.adTagUri != null) {
            builder.setAdsConfiguration(
                MediaItem.AdsConfiguration.Builder(Uri.parse(video.adTagUri)).build()
            )
        }

        p.setMediaItem(builder.build())
        p.prepare()
        Log.i(TAG, "Media prepared — awaiting first frame")
    }

    override fun getVideoTracks(): List<TrackInfo> {
        val p = player ?: return emptyList()
        return p.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                val gIdx = p.currentTracks.groups.indexOf(group)
                (0 until group.length)
                    .filter { i -> group.getTrackFormat(i).let { it.width > 0 && it.height > 0 } }
                    .map { i ->
                        val fmt = group.getTrackFormat(i)
                        TrackInfo(gIdx, i, "${fmt.width}x${fmt.height} (${fmt.bitrate / 1000}kbps)", group.isTrackSelected(i))
                    }
            }
    }

    override fun getAudioTracks(): List<TrackInfo> {
        val p = player ?: return emptyList()
        return p.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .flatMap { group ->
                val gIdx = p.currentTracks.groups.indexOf(group)
                (0 until group.length).map { i ->
                    val fmt = group.getTrackFormat(i)
                    TrackInfo(gIdx, i, "${fmt.language ?: "Unknown"} ${fmt.channelCount}ch (${fmt.bitrate / 1000}kbps)", group.isTrackSelected(i))
                }
            }
    }

    override fun getSubtitleTracks(): List<TrackInfo> {
        val p = player ?: return emptyList()
        val result = mutableListOf<TrackInfo>()
        for (group in p.currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                val gIdx = p.currentTracks.groups.indexOf(group)
                for (i in 0 until group.length) {
                    val fmt = group.getTrackFormat(i)
                    result.add(TrackInfo(gIdx, i, fmt.label ?: fmt.language ?: "Subtitle ${result.size + 1}", group.isTrackSelected(i)))
                }
            }
        }
        return result
    }

    override fun selectVideoTrack(groupIndex: Int, trackIndex: Int) {
        val group = player?.currentTracks?.groups?.getOrNull(groupIndex) ?: return
        player?.trackSelectionParameters = player!!.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
            .build()
    }

    override fun setAutoQuality() {
        player?.trackSelectionParameters = player!!.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
    }

    override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val group = player?.currentTracks?.groups?.getOrNull(groupIndex) ?: return
        player?.trackSelectionParameters = player!!.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
            .build()
    }

    override fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) {
        val group = player?.currentTracks?.groups?.getOrNull(groupIndex) ?: return
        player?.trackSelectionParameters = player!!.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
            .build()
    }

    override fun disableSubtitles() {
        player?.trackSelectionParameters = player!!.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    override fun enableSubtitles() {
        player?.trackSelectionParameters = player!!.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
    }

    override fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
    }

    override fun setMaxBitrate(maxBitrate: Int) {
        trackSelector?.parameters = trackSelector!!.parameters
            .buildUpon()
            .setMaxVideoBitrate(maxBitrate)
            .build()
    }

    override fun forceLowestBitrate() {
        trackSelector?.parameters = trackSelector!!.parameters
            .buildUpon()
            .setForceLowestBitrate(true)
            .build()
    }

    override fun clearBitrateCap() {
        trackSelector?.parameters = trackSelector!!.parameters
            .buildUpon()
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .setForceLowestBitrate(false)
            .build()
    }

    override fun releasePlayer() {
        player?.let { exo ->
            exo.removeAnalyticsListener(eventTracker)
            exo.release()
        }
        player = null
        trackSelector = null
        Log.i(TAG, "Player released")
    }
}

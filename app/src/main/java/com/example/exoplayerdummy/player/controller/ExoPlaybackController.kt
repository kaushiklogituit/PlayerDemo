package com.example.exoplayerdummy.player.controller

import android.content.Context
import android.net.Uri
import com.example.exoplayerdummy.AppLogger as Log
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
import java.util.Locale

@UnstableApi
class ExoPlaybackController(
    private val context: Context,
    private val cacheManager: MediaCacheManager
) : PlaybackController {

    companion object {
        private const val TAG = "ExoPlaybackController"

        private val ISO_LANGUAGE_BY_THREE_LETTER_CODE: Map<String, String> by lazy {
            Locale.getISOLanguages().mapNotNull { languageCode ->
                runCatching {
                    Locale.Builder()
                        .setLanguage(languageCode)
                        .build()
                        .isO3Language
                        .lowercase(Locale.US) to languageCode
                }.getOrNull()
            }.toMap()
        }
    }

    override var player: ExoPlayer? = null
        private set

    private var trackSelector: DefaultTrackSelector? = null
    private val eventTracker = PlaybackEventTracker()

    override fun initPlayer(isLive: Boolean): ExoPlayer {
        Log.i(TAG, "Initialising player (isLive=$isLive)")
        releasePlayer()

        trackSelector = DefaultTrackSelector(context).apply {
            Log.d(TAG, "Creating track selector with initial SD cap and preferred text language=en")
            parameters = parameters.buildUpon()
                .setMaxVideoSizeSd()
                .setPreferredTextLanguage("en")
                .build()
        }

        val bufferingConfig = if (isLive) BufferingStrategy.liveConfig() else BufferingStrategy.vodConfig()
        val dataSourceFactory = if (isLive) {
            cacheManager.buildLiveDataSourceFactory()
        } else {
            cacheManager.buildDataSourceFactory()
        }
        Log.d(TAG, "Building media source factory with ${if (isLive) "uncached live" else "cached VOD"} data source and adaptive retry policy")
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
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

        Log.i(TAG, "Preparing media id=${video.id}, title=${video.title}, protocol=${video.protocol}, live=${video.isLiveStream}, url=${video.streamUrl}")
        eventTracker.resetSession()

        val builder = MediaItem.Builder()
            .setUri(video.streamUrl)
            .setMediaId(video.id)

        when (video.protocol) {
            StreamProtocol.HLS -> {
                Log.d(TAG, "Applying HLS MIME type")
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            StreamProtocol.DASH -> {
                Log.d(TAG, "Applying DASH MIME type")
                builder.setMimeType(MimeTypes.APPLICATION_MPD)
            }
            StreamProtocol.PROGRESSIVE -> Log.d(TAG, "Progressive stream — MIME type auto-detected")
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
            Log.d(TAG, "Adding ${video.captions.size} side-loaded caption track(s): ${video.captions.joinToString { it.label }}")
            builder.setSubtitleConfigurations(video.captions.map { caption ->
                SubtitleConfiguration.Builder(Uri.parse(caption.uri))
                    .setMimeType(caption.mimeType)
                    .setLanguage(caption.language)
                    .setLabel(caption.label)
                    .build()
            })
        }

        if (video.isLiveStream) {
            Log.d(TAG, "Applying live configuration targetOffset=8s min=4s max=20s speed=0.97x..1.03x")
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(8_000)
                    .setMinOffsetMs(4_000)
                    .setMaxOffsetMs(20_000)
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.03f)
                    .build()
            )
        }

        if (video.adsEnabled && video.adTagUri != null) {
            Log.d(TAG, "Adding ad tag: ${video.adTagUri}")
            builder.setAdsConfiguration(
                MediaItem.AdsConfiguration.Builder(Uri.parse(video.adTagUri)).build()
            )
        }

        p.setMediaItem(builder.build())
        Log.d(TAG, "Media item set; calling prepare()")
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
                    val label = formatSubtitleTrackLabel(
                        label = fmt.label,
                        languageTag = fmt.language,
                        fallbackIndex = result.size + 1
                    )
                    result.add(TrackInfo(gIdx, i, label, group.isTrackSelected(i)))
                }
            }
        }
        return result
    }

    private fun formatSubtitleTrackLabel(
        label: String?,
        languageTag: String?,
        fallbackIndex: Int
    ): String {
        val normalizedLanguageTag = languageTag?.trim()?.takeIf { it.isNotEmpty() && it != C.LANGUAGE_UNDETERMINED }
        val displayLanguage = normalizedLanguageTag?.toDisplayLanguageName()
        val cleanLabel = label?.trim()?.takeIf { it.isNotEmpty() }

        return when {
            normalizedLanguageTag != null && displayLanguage != null -> {
                val suffix = cleanLabel
                    ?.takeUnless { it.equals(displayLanguage, ignoreCase = true) }
                    ?.takeUnless { it.equals(normalizedLanguageTag, ignoreCase = true) }
                    ?.let { " ($it)" }
                    .orEmpty()
                "$displayLanguage $suffix"
            }
            cleanLabel != null -> cleanLabel
            normalizedLanguageTag != null -> normalizedLanguageTag
            else -> "Subtitle $fallbackIndex"
        }
    }

    private fun String.toDisplayLanguageName(): String? {
        val locale = toResolvedLocale()
        return locale.getDisplayName(Locale.ENGLISH)
            .takeIf { it.isNotBlank() && it != "und" }
            ?.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.ENGLISH) else char.toString()
            }
    }

    private fun String.toResolvedLocale(): Locale {
        val normalizedTag = replace('_', '-').trim()
        val parts = normalizedTag.split('-').filter { it.isNotBlank() }
        val language = parts.firstOrNull()?.lowercase(Locale.US).orEmpty()
        val isoLanguage = when (language.length) {
            3 -> ISO_LANGUAGE_BY_THREE_LETTER_CODE[language] ?: language
            else -> language
        }

        if (isoLanguage.isEmpty()) return Locale.forLanguageTag(normalizedTag)

        val resolvedTag = buildString {
            append(isoLanguage)
            parts.drop(1).forEach { part ->
                append('-')
                append(part)
            }
        }
        return Locale.forLanguageTag(resolvedTag)
    }

    override fun selectVideoTrack(groupIndex: Int, trackIndex: Int) {
        val p = player ?: run {
            Log.w(TAG, "selectVideoTrack ignored because player is null")
            return
        }
        val group = p.currentTracks.groups.getOrNull(groupIndex) ?: run {
            Log.w(TAG, "selectVideoTrack ignored because group=$groupIndex is missing")
            return
        }
        Log.d(TAG, "Applying video track override group=$groupIndex track=$trackIndex")
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
            .build()
    }

    override fun setAutoQuality() {
        val p = player ?: run {
            Log.w(TAG, "setAutoQuality ignored because player is null")
            return
        }
        Log.d(TAG, "Clearing manual video quality override")
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
    }

    override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val p = player ?: run {
            Log.w(TAG, "selectAudioTrack ignored because player is null")
            return
        }
        val group = p.currentTracks.groups.getOrNull(groupIndex) ?: run {
            Log.w(TAG, "selectAudioTrack ignored because group=$groupIndex is missing")
            return
        }
        Log.d(TAG, "Applying audio track override group=$groupIndex track=$trackIndex")
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
            .build()
    }

    override fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) {
        val p = player ?: run {
            Log.w(TAG, "selectSubtitleTrack ignored because player is null")
            return
        }
        val group = p.currentTracks.groups.getOrNull(groupIndex) ?: run {
            Log.w(TAG, "selectSubtitleTrack ignored because group=$groupIndex is missing")
            return
        }
        Log.d(TAG, "Applying subtitle track override group=$groupIndex track=$trackIndex")
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
            .build()
    }

    override fun disableSubtitles() {
        val p = player ?: run {
            Log.w(TAG, "disableSubtitles ignored because player is null")
            return
        }
        Log.d(TAG, "Disabling text track type")
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    override fun enableSubtitles() {
        val p = player ?: run {
            Log.w(TAG, "enableSubtitles ignored because player is null")
            return
        }
        Log.d(TAG, "Enabling text track type")
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
    }

    override fun setPlaybackSpeed(speed: Float) {
        val p = player ?: run {
            Log.w(TAG, "setPlaybackSpeed ignored because player is null")
            return
        }
        Log.d(TAG, "Setting ExoPlayer speed to ${speed}x")
        p.setPlaybackSpeed(speed)
    }

    override fun setMaxBitrate(maxBitrate: Int) {
        val selector = trackSelector ?: run {
            Log.w(TAG, "setMaxBitrate ignored because track selector is null")
            return
        }
        Log.d(TAG, "Applying max video bitrate=$maxBitrate")
        selector.parameters = selector.parameters
            .buildUpon()
            .setMaxVideoBitrate(maxBitrate)
            .build()
    }

    override fun forceLowestBitrate() {
        val selector = trackSelector ?: run {
            Log.w(TAG, "forceLowestBitrate ignored because track selector is null")
            return
        }
        Log.d(TAG, "Forcing lowest available bitrate")
        selector.parameters = selector.parameters
            .buildUpon()
            .setForceLowestBitrate(true)
            .build()
    }

    override fun clearBitrateCap() {
        val selector = trackSelector ?: run {
            Log.w(TAG, "clearBitrateCap ignored because track selector is null")
            return
        }
        Log.d(TAG, "Clearing bitrate constraints")
        selector.parameters = selector.parameters
            .buildUpon()
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .setForceLowestBitrate(false)
            .build()
    }

    override fun releasePlayer() {
        player?.let { exo ->
            Log.i(TAG, "Releasing player at position=${exo.currentPosition}ms state=${exo.playbackState} playing=${exo.isPlaying}")
            exo.removeAnalyticsListener(eventTracker)
            exo.release()
        } ?: Log.d(TAG, "releasePlayer called with no active player")
        player = null
        trackSelector = null
        Log.i(TAG, "Player released")
    }
}

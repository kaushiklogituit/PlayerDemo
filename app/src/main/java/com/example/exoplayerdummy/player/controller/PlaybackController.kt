package com.example.exoplayerdummy.player.controller

import androidx.media3.exoplayer.ExoPlayer
import com.example.exoplayerdummy.domain.model.Video

interface PlaybackController {
    val player: ExoPlayer?
    fun initPlayer(isLive: Boolean = false): ExoPlayer
    fun prepareMedia(video: Video)
    fun getVideoTracks(): List<TrackInfo>
    fun getAudioTracks(): List<TrackInfo>
    fun getSubtitleTracks(): List<TrackInfo>
    fun selectVideoTrack(groupIndex: Int, trackIndex: Int)
    fun setAutoQuality()
    fun selectAudioTrack(groupIndex: Int, trackIndex: Int)
    fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int)
    fun disableSubtitles()
    fun enableSubtitles()
    fun setPlaybackSpeed(speed: Float)
    fun setMaxBitrate(maxBitrate: Int)
    fun forceLowestBitrate()
    fun clearBitrateCap()
    fun releasePlayer()
}

package com.example.exoplayerdummy.presentation.player

import com.example.exoplayerdummy.domain.model.Video
import com.example.exoplayerdummy.player.controller.TrackInfo

object PlaybackContract {

    data class State(
        val currentVideo: Video? = null,
        val isPlaying: Boolean = false,
        val playerStateCode: Int = 0,
        val playerStateName: String = "IDLE",
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val bufferedPercent: Int = 0,
        val videoResolution: String = "N/A",
        val isMuted: Boolean = false,
        val playbackSpeed: Float = 1.0f,
        val isLiveStream: Boolean = false,
        val liveOffsetMs: Long = 0L,
        val selectedQualityLabel: String = "Auto",
        val selectedAudioLabel: String = "",
        val selectedCaptionLabel: String = "Off",
        val activeBitrateLabel: String = "Auto ▲",
        val bitrateEstimate: Long = 0L,
        val errorMessage: String? = null,
        // Track lists — refreshed every sync cycle so chips always reflect actual selection
        val videoTracks: List<TrackInfo> = emptyList(),
        val audioTracks: List<TrackInfo> = emptyList(),
        val captionTracks: List<TrackInfo> = emptyList()
    )

    sealed interface Action {
        data class LoadVideo(val video: Video) : Action
        data object TogglePlayPause : Action
        data class SeekTo(val positionMs: Long) : Action
        data object SeekForward : Action
        data object SeekBack : Action
        data object SeekToLiveEdge : Action
        data object ToggleMute : Action
        data class SetSpeed(val speed: Float) : Action
        data class SelectVideoTrack(val track: TrackInfo) : Action
        data object SetAutoQuality : Action
        data class SelectAudioTrack(val track: TrackInfo) : Action
        data class SelectCaptionTrack(val track: TrackInfo) : Action
        data object DisableCaptions : Action
        data class SetMaxBitrate(val bps: Int) : Action
        data object ForceLowestBitrate : Action
        data object ClearBitrateCap : Action
        data object OnPause : Action
        data object OnResume : Action
        data object StopPlayback : Action
        data object ClearError : Action
    }

    sealed interface Event {
        data class ShowError(val message: String) : Event
    }
}

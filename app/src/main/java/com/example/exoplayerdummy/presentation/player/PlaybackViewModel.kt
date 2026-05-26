package com.example.exoplayerdummy.presentation.player

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.example.exoplayerdummy.domain.model.Video
import com.example.exoplayerdummy.player.controller.PlaybackController
import com.example.exoplayerdummy.player.controller.TrackInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackViewModel(
    application: Application,
    val playbackController: PlaybackController
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlaybackViewModel"
        private const val POSITION_POLL_INTERVAL_MS = 500L
    }

    private val _state = MutableStateFlow(PlaybackContract.State())
    val state = _state.asStateFlow()

    private val _events = Channel<PlaybackContract.Event>()
    val events = _events.receiveAsFlow()

    private var pollingJob: kotlinx.coroutines.Job? = null

    fun onAction(action: PlaybackContract.Action) {
        when (action) {
            is PlaybackContract.Action.LoadVideo -> loadVideo(action.video)
            PlaybackContract.Action.TogglePlayPause -> togglePlayPause()
            is PlaybackContract.Action.SeekTo -> playbackController.player?.seekTo(action.positionMs)
            PlaybackContract.Action.SeekForward -> playbackController.player?.seekForward()
            PlaybackContract.Action.SeekBack -> playbackController.player?.seekBack()
            PlaybackContract.Action.SeekToLiveEdge -> playbackController.player?.seekToDefaultPosition()
            PlaybackContract.Action.ToggleMute -> toggleMute()
            is PlaybackContract.Action.SetSpeed -> applySpeed(action.speed)
            is PlaybackContract.Action.SelectVideoTrack -> applyVideoTrack(action.track)
            PlaybackContract.Action.SetAutoQuality -> {
                playbackController.setAutoQuality()
                _state.update { it.copy(selectedQualityLabel = "Auto") }
            }
            is PlaybackContract.Action.SelectAudioTrack -> {
                playbackController.selectAudioTrack(action.track.groupIndex, action.track.trackIndex)
                _state.update { it.copy(selectedAudioLabel = action.track.label) }
            }
            is PlaybackContract.Action.SelectCaptionTrack -> applyCaptionTrack(action.track)
            PlaybackContract.Action.DisableCaptions -> {
                playbackController.disableSubtitles()
                _state.update { it.copy(selectedCaptionLabel = "Off") }
            }
            is PlaybackContract.Action.SetMaxBitrate -> {
                playbackController.setMaxBitrate(action.bps)
                val label = when (action.bps) {
                    1_000_000 -> "1 Mbps"
                    3_000_000 -> "3 Mbps"
                    else -> "${action.bps / 1_000_000} Mbps"
                }
                _state.update { it.copy(activeBitrateLabel = label) }
            }
            PlaybackContract.Action.ForceLowestBitrate -> {
                playbackController.forceLowestBitrate()
                _state.update { it.copy(activeBitrateLabel = "Lowest") }
            }
            PlaybackContract.Action.ClearBitrateCap -> {
                playbackController.clearBitrateCap()
                _state.update { it.copy(activeBitrateLabel = "Auto ▲") }
            }
            PlaybackContract.Action.OnPause -> {
                Log.d(TAG, "Lifecycle pause — pausing player")
                playbackController.player?.pause()
            }
            PlaybackContract.Action.OnResume -> { /* user controls resume */ }
            PlaybackContract.Action.StopPlayback -> stopPlayback()
            PlaybackContract.Action.ClearError -> _state.update { it.copy(errorMessage = null) }
        }
    }

    private fun loadVideo(video: Video) {
        Log.i(TAG, "Loading video: ${video.title}")
        _state.update { it.copy(currentVideo = video) }

        val player = playbackController.initPlayer(isLive = video.isLiveStream)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = syncState()
            override fun onIsPlayingChanged(isPlaying: Boolean) = syncState()
            override fun onVideoSizeChanged(videoSize: VideoSize) = syncState()
            override fun onPlayerError(error: PlaybackException) {
                val msg = "${error.errorCodeName}: ${error.message}"
                _state.update { it.copy(errorMessage = msg) }
                viewModelScope.launch { _events.send(PlaybackContract.Event.ShowError(msg)) }
            }
        })

        playbackController.prepareMedia(video)
        startPolling()
    }

    private fun togglePlayPause() {
        val p = playbackController.player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    private fun toggleMute() {
        val p = playbackController.player ?: return
        val muting = p.volume != 0f
        p.volume = if (muting) 0f else 1f
        _state.update { it.copy(isMuted = muting) }
    }

    private fun applySpeed(speed: Float) {
        playbackController.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    private fun applyVideoTrack(track: TrackInfo) {
        playbackController.selectVideoTrack(track.groupIndex, track.trackIndex)
        _state.update { it.copy(selectedQualityLabel = track.label) }
    }

    private fun applyCaptionTrack(track: TrackInfo) {
        playbackController.selectSubtitleTrack(track.groupIndex, track.trackIndex)
        _state.update { it.copy(selectedCaptionLabel = track.label) }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                syncState()
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    private fun syncState() {
        val p = playbackController.player ?: return
        val stateName = when (p.playbackState) {
            Player.STATE_IDLE      -> "IDLE"
            Player.STATE_BUFFERING -> "BUFFERING"
            Player.STATE_READY     -> "READY"
            Player.STATE_ENDED     -> "ENDED"
            else                   -> "UNKNOWN"
        }
        val videoTracks   = playbackController.getVideoTracks()
        val audioTracks   = playbackController.getAudioTracks()
        val captionTracks = playbackController.getSubtitleTracks()
        _state.update { current ->
            current.copy(
                isPlaying       = p.isPlaying,
                playerStateCode = p.playbackState,
                playerStateName = stateName,
                positionMs      = p.currentPosition,
                durationMs      = p.duration,
                bufferedPercent = p.bufferedPercentage,
                videoResolution = "${p.videoSize.width}x${p.videoSize.height}",
                isMuted         = p.volume == 0f,
                playbackSpeed   = p.playbackParameters.speed,
                isLiveStream    = p.isCurrentMediaItemLive,
                liveOffsetMs    = if (p.isCurrentMediaItemLive) p.currentLiveOffset else 0L,
                videoTracks     = videoTracks,
                audioTracks     = audioTracks,
                captionTracks   = captionTracks,
                // keep optimistic label if user already selected; otherwise follow ExoPlayer's isSelected
                selectedAudioLabel = current.selectedAudioLabel.ifEmpty {
                    audioTracks.find { it.isSelected }?.label ?: ""
                }
            )
        }
    }

    private fun stopPlayback() {
        Log.d(TAG, "Stopping playback")
        pollingJob?.cancel()
        playbackController.releasePlayer()
        _state.update { PlaybackContract.State() }
    }

    fun getVideoTracks(): List<TrackInfo> = playbackController.getVideoTracks()
    fun getAudioTracks(): List<TrackInfo> = playbackController.getAudioTracks()
    fun getCaptionTracks(): List<TrackInfo> = playbackController.getSubtitleTracks()

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        playbackController.releasePlayer()
        Log.d(TAG, "ViewModel cleared — player released")
    }
}

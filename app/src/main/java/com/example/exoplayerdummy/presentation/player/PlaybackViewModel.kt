package com.example.exoplayerdummy.presentation.player

import android.app.Application
import com.example.exoplayerdummy.AppLogger as Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
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
    private var lastLoggedStateName: String? = null
    private var lastLoggedPlaying: Boolean? = null
    private var lastLoggedResolution: String? = null
    private var lastLoggedTrackCounts: Triple<Int, Int, Int>? = null
    private val timelineWindow = Timeline.Window()

    fun onAction(action: PlaybackContract.Action) {
        when (action) {
            is PlaybackContract.Action.LoadVideo -> loadVideo(action.video)
            PlaybackContract.Action.TogglePlayPause -> togglePlayPause()
            is PlaybackContract.Action.SeekTo -> {
                Log.d(TAG, "Seeking to ${action.positionMs}ms")
                playbackController.player?.seekTo(action.positionMs) ?: Log.w(TAG, "Seek ignored because player is null")
            }
            PlaybackContract.Action.SeekForward -> {
                Log.d(TAG, "Seeking forward")
                playbackController.player?.seekForward() ?: Log.w(TAG, "Seek forward ignored because player is null")
            }
            PlaybackContract.Action.SeekBack -> {
                Log.d(TAG, "Seeking back")
                playbackController.player?.seekBack() ?: Log.w(TAG, "Seek back ignored because player is null")
            }
            PlaybackContract.Action.SeekToLiveEdge -> {
                Log.d(TAG, "Seeking to live edge")
                playbackController.player?.seekToDefaultPosition() ?: Log.w(TAG, "Live-edge seek ignored because player is null")
            }
            PlaybackContract.Action.ToggleMute -> toggleMute()
            is PlaybackContract.Action.SetSpeed -> applySpeed(action.speed)
            is PlaybackContract.Action.SelectVideoTrack -> applyVideoTrack(action.track)
            PlaybackContract.Action.SetAutoQuality -> {
                Log.d(TAG, "Selecting adaptive video quality")
                playbackController.setAutoQuality()
                _state.update {
                    it.copy(
                        selectedQualityLabel = "Auto",
                        selectedQualityGroupIndex = null,
                        selectedQualityTrackIndex = null
                    )
                }
            }
            is PlaybackContract.Action.SelectAudioTrack -> applyAudioTrack(action.track)
            is PlaybackContract.Action.SelectCaptionTrack -> applyCaptionTrack(action.track)
            PlaybackContract.Action.DisableCaptions -> {
                Log.d(TAG, "Disabling captions")
                playbackController.disableSubtitles()
                _state.update {
                    it.copy(
                        selectedCaptionLabel = "Off",
                        selectedCaptionGroupIndex = null,
                        selectedCaptionTrackIndex = null
                    )
                }
            }
            is PlaybackContract.Action.SetMaxBitrate -> {
                Log.d(TAG, "Setting max bitrate to ${action.bps}bps")
                playbackController.setMaxBitrate(action.bps)
                val label = when (action.bps) {
                    1_000_000 -> "1 Mbps"
                    3_000_000 -> "3 Mbps"
                    else -> "${action.bps / 1_000_000} Mbps"
                }
                _state.update { it.copy(activeBitrateLabel = label) }
            }
            PlaybackContract.Action.ForceLowestBitrate -> {
                Log.d(TAG, "Forcing lowest bitrate")
                playbackController.forceLowestBitrate()
                _state.update { it.copy(activeBitrateLabel = "Lowest") }
            }
            PlaybackContract.Action.ClearBitrateCap -> {
                Log.d(TAG, "Clearing bitrate cap")
                playbackController.clearBitrateCap()
                _state.update { it.copy(activeBitrateLabel = "Auto ▲") }
            }
            PlaybackContract.Action.OnPause -> {
                Log.d(TAG, "Lifecycle pause — pausing player")
                playbackController.player?.pause()
            }
            PlaybackContract.Action.OnResume -> Log.d(TAG, "Lifecycle resume — waiting for explicit user playback")
            PlaybackContract.Action.StopPlayback -> stopPlayback()
            PlaybackContract.Action.ClearError -> {
                Log.d(TAG, "Clearing playback error")
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun loadVideo(video: Video) {
        Log.i(TAG, "Loading video id=${video.id}, title=${video.title}, protocol=${video.protocol}, live=${video.isLiveStream}, captions=${video.captions.size}, drm=${video.protection != null}, ads=${video.adsEnabled}")
        lastLoggedStateName = null
        lastLoggedPlaying = null
        lastLoggedResolution = null
        lastLoggedTrackCounts = null
        _state.update { it.copy(currentVideo = video) }

        val player = playbackController.initPlayer(isLive = video.isLiveStream)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = syncState()
            override fun onIsPlayingChanged(isPlaying: Boolean) = syncState()
            override fun onVideoSizeChanged(videoSize: VideoSize) = syncState()
            override fun onTracksChanged(tracks: Tracks) = syncState()
            override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) = syncState()
            override fun onPlayerError(error: PlaybackException) {
                val msg = "${error.errorCodeName}: ${error.message}"
                Log.e(TAG, "Player listener error code=${error.errorCode}, name=${error.errorCodeName}, message=${error.message}", error)
                _state.update { it.copy(errorMessage = msg) }
                viewModelScope.launch { _events.send(PlaybackContract.Event.ShowError(msg)) }
            }
        })

        playbackController.prepareMedia(video)
        startPolling()
    }

    private fun togglePlayPause() {
        val p = playbackController.player ?: run {
            Log.w(TAG, "Toggle play/pause ignored because player is null")
            return
        }
        if (p.isPlaying) {
            Log.d(TAG, "User paused playback at ${p.currentPosition}ms")
            p.pause()
        } else {
            Log.d(TAG, "User started playback at ${p.currentPosition}ms")
            p.play()
        }
    }

    private fun toggleMute() {
        val p = playbackController.player ?: run {
            Log.w(TAG, "Toggle mute ignored because player is null")
            return
        }
        val muting = p.volume != 0f
        Log.d(TAG, if (muting) "Muting playback" else "Unmuting playback")
        p.volume = if (muting) 0f else 1f
        _state.update { it.copy(isMuted = muting) }
    }

    private fun applySpeed(speed: Float) {
        Log.d(TAG, "Applying playback speed ${speed}x")
        playbackController.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    private fun applyVideoTrack(track: TrackInfo) {
        Log.d(TAG, "Selecting video track: ${track.label} (group=${track.groupIndex}, track=${track.trackIndex})")
        playbackController.selectVideoTrack(track.groupIndex, track.trackIndex)
        _state.update {
            it.copy(
                selectedQualityLabel = track.label,
                selectedQualityGroupIndex = track.groupIndex,
                selectedQualityTrackIndex = track.trackIndex
            )
        }
    }

    private fun applyAudioTrack(track: TrackInfo) {
        Log.d(TAG, "Selecting audio track: ${track.label} (group=${track.groupIndex}, track=${track.trackIndex})")
        playbackController.selectAudioTrack(track.groupIndex, track.trackIndex)
        _state.update {
            it.copy(
                selectedAudioLabel = track.label,
                selectedAudioGroupIndex = track.groupIndex,
                selectedAudioTrackIndex = track.trackIndex
            )
        }
    }

    private fun applyCaptionTrack(track: TrackInfo) {
        Log.d(TAG, "Selecting caption track: ${track.label} (group=${track.groupIndex}, track=${track.trackIndex})")
        playbackController.selectSubtitleTrack(track.groupIndex, track.trackIndex)
        _state.update {
            it.copy(
                selectedCaptionLabel = track.label,
                selectedCaptionGroupIndex = track.groupIndex,
                selectedCaptionTrackIndex = track.trackIndex
            )
        }
    }

    private fun startPolling() {
        Log.d(TAG, "Starting playback state polling every ${POSITION_POLL_INTERVAL_MS}ms")
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
        val durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val isLive = p.isCurrentMediaItemLive
        val isLiveSeekable = isLive && !p.currentTimeline.isEmpty &&
                p.currentTimeline.getWindow(p.currentMediaItemIndex, timelineWindow).isSeekable
        val liveOffsetMs = if (isLive && p.currentLiveOffset != C.TIME_UNSET) p.currentLiveOffset else 0L
        logStateSnapshotIfChanged(
            stateName = stateName,
            isPlaying = p.isPlaying,
            positionMs = p.currentPosition,
            durationMs = durationMs,
            bufferedPercent = p.bufferedPercentage,
            resolution = "${p.videoSize.width}x${p.videoSize.height}",
            videoTrackCount = videoTracks.size,
            audioTrackCount = audioTracks.size,
            captionTrackCount = captionTracks.size
        )
        val selectedVideoTrack = videoTracks.singleOrNull { it.isSelected }
        val selectedAudioTrack = audioTracks.singleOrNull { it.isSelected }
        val selectedCaptionTrack = captionTracks.singleOrNull { it.isSelected }

        _state.update { current ->
            val manuallySelectedQualityStillActive = current.selectedQualityGroupIndex != null &&
                    selectedVideoTrack?.matches(
                        groupIndex = current.selectedQualityGroupIndex,
                        trackIndex = current.selectedQualityTrackIndex
                    ) == true

            current.copy(
                isPlaying       = p.isPlaying,
                playerStateCode = p.playbackState,
                playerStateName = stateName,
                positionMs      = p.currentPosition,
                durationMs      = durationMs,
                bufferedPercent = p.bufferedPercentage,
                videoResolution = "${p.videoSize.width}x${p.videoSize.height}",
                isMuted         = p.volume == 0f,
                playbackSpeed   = p.playbackParameters.speed,
                isLiveStream    = isLive,
                isLiveSeekable  = isLiveSeekable,
                liveOffsetMs    = liveOffsetMs,
                videoTracks     = videoTracks,
                audioTracks     = audioTracks,
                captionTracks   = captionTracks,
                selectedQualityLabel = if (manuallySelectedQualityStillActive) {
                    selectedVideoTrack.label
                } else {
                    "Auto"
                },
                selectedQualityGroupIndex = if (manuallySelectedQualityStillActive) selectedVideoTrack.groupIndex else null,
                selectedQualityTrackIndex = if (manuallySelectedQualityStillActive) selectedVideoTrack.trackIndex else null,
                selectedAudioLabel = selectedAudioTrack?.label.orEmpty(),
                selectedAudioGroupIndex = selectedAudioTrack?.groupIndex,
                selectedAudioTrackIndex = selectedAudioTrack?.trackIndex,
                selectedCaptionLabel = selectedCaptionTrack?.label ?: "Off",
                selectedCaptionGroupIndex = selectedCaptionTrack?.groupIndex,
                selectedCaptionTrackIndex = selectedCaptionTrack?.trackIndex
            )
        }
    }

    private fun TrackInfo.matches(groupIndex: Int?, trackIndex: Int?): Boolean =
        this.groupIndex == groupIndex && this.trackIndex == trackIndex

    private fun logStateSnapshotIfChanged(
        stateName: String,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        bufferedPercent: Int,
        resolution: String,
        videoTrackCount: Int,
        audioTrackCount: Int,
        captionTrackCount: Int
    ) {
        val trackCounts = Triple(videoTrackCount, audioTrackCount, captionTrackCount)
        val changed = lastLoggedStateName != stateName ||
                lastLoggedPlaying != isPlaying ||
                lastLoggedResolution != resolution ||
                lastLoggedTrackCounts != trackCounts

        if (changed) {
            Log.d(
                TAG,
                "State snapshot: state=$stateName, playing=$isPlaying, pos=${positionMs}ms, duration=${durationMs}ms, buffered=$bufferedPercent%, resolution=$resolution, tracks(video=$videoTrackCount,audio=$audioTrackCount,captions=$captionTrackCount)"
            )
            lastLoggedStateName = stateName
            lastLoggedPlaying = isPlaying
            lastLoggedResolution = resolution
            lastLoggedTrackCounts = trackCounts
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

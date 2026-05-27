package com.example.exoplayerdummy.presentation.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import com.example.exoplayerdummy.AppLogger as Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.exoplayerdummy.domain.model.StreamProtocol
import com.example.exoplayerdummy.domain.model.Video
import com.example.exoplayerdummy.player.controller.TrackInfo
import com.example.exoplayerdummy.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

private const val TAG = "PlaybackScreen"

// ─── Root ─────────────────────────────────────────────────────────────────────

@Composable
fun PlaybackRoot(
    video: Video,
    onNavigateBack: () -> Unit,
    viewModel: PlaybackViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Full immersive — hide both status bar and nav bar
    DisposableEffect(Unit) {
        Log.d(TAG, "Entering immersive playback UI")
        val win = (context as? Activity)?.window
        win?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            WindowCompat.getInsetsController(w, w.decorView).let { ctrl ->
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
                ctrl.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            Log.d(TAG, "Exiting immersive playback UI")
            win?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(video.id) {
        Log.i(TAG, "Loading ${video.title} (id=${video.id}) and forcing landscape orientation")
        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        viewModel.onAction(PlaybackContract.Action.LoadVideo(video))
    }
    val scope = rememberCoroutineScope()
    var isExiting by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        Log.i(TAG, "Back tapped — restoring orientation before navigation")
        isExiting = true
       
        viewModel.playbackController.player?.setVideoSurface(null)

        // 2. Stop player
        viewModel.onAction(PlaybackContract.Action.StopPlayback)

        // 3. Restore orientation first, wait for it to settle, THEN navigate
        (context as? Activity)?.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        scope.launch {  // or use a coroutine scope available here
            delay(150)           // give Android time to rotate back
            onNavigateBack()
        }                                   // ← then navigate
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE  -> {
                    Log.d(TAG, "Lifecycle ON_PAUSE observed")
                    viewModel.onAction(PlaybackContract.Action.OnPause)
                }
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "Lifecycle ON_RESUME observed")
                    viewModel.onAction(PlaybackContract.Action.OnResume)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.i(TAG, "PlaybackRoot disposed — stopping playback and restoring orientation")

        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PlaybackScreen(
            state          = state,
            player         = viewModel.playbackController.player,
            video          = video,
            onAction       = viewModel::onAction,
            onNavigateBack = handleBack
        )

        // Black curtain — drops instantly when exiting, hides the white flash
        if (isExiting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
    }
}

// ─── Screen dispatcher ────────────────────────────────────────────────────────

@Composable
fun PlaybackScreen(
    state: PlaybackContract.State,
    player: ExoPlayer?,
    video: Video,
    onAction: (PlaybackContract.Action) -> Unit,
    onNavigateBack: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        LandscapeImmersivePlayer(
            state = state, player = player, video = video,
            onAction = onAction, onNavigateBack = onNavigateBack
        )
    }
}

// ─── Video Surface ────────────────────────────────────────────────────────────

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoSurfaceComposable(player: ExoPlayer?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            }
        },
        update  = { pv ->
            if (pv.player !== player) {
                Log.d(TAG, "Binding PlayerView to player=${player != null}")
            }
            pv.player = player
        },
        modifier = modifier.background(Color.Black)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// LANDSCAPE — FULL IMMERSIVE PLAYER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LandscapeImmersivePlayer(
    state: PlaybackContract.State,
    player: ExoPlayer?,
    video: Video,
    onAction: (PlaybackContract.Action) -> Unit,
    onNavigateBack: () -> Unit
) {
    var showOverlay  by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var isSeeking    by remember { mutableStateOf(false) }
    // Auto-hide overlay 3.5s after it becomes visible (only while playing, not in settings)
    LaunchedEffect(showOverlay, state.isPlaying, showSettings) {
        if (showOverlay && state.isPlaying && !showSettings) {
            kotlinx.coroutines.delay(3_500)
            showOverlay = false
        }
    }

    LaunchedEffect(showOverlay, state.isPlaying, showSettings, isSeeking) {  // ← ADD isSeeking
        if (showOverlay && state.isPlaying && !showSettings && !isSeeking) { // ← ADD !isSeeking
            kotlinx.coroutines.delay(3_500)
            showOverlay = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures {
                    if (!showSettings) {
                        showOverlay = !showOverlay
                        Log.d(TAG, "Landscape overlay ${if (showOverlay) "shown" else "hidden"} by tap")
                    }
                }
            }
    ) {
        VideoSurfaceComposable(player = player, modifier = Modifier.fillMaxSize())

        // ── Buffering / play-pause — never overlap ───────────────────────────
        AnimatedContent(
            targetState  = state.playerStateName == "BUFFERING",
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
            label        = "centerState",
            modifier     = Modifier.align(Alignment.Center)
        ) { isBuffering ->
            if (isBuffering) {
                CircularProgressIndicator(
                    color       = VaultRed,
                    strokeWidth = 3.dp,
                    modifier    = Modifier.size(52.dp)
                )
            } else {
                // shown only when overlay is visible
                if (showOverlay) {
                    val scale by animateFloatAsState(
                        targetValue  = if (state.isPlaying) 1f else 1.08f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label        = "playScale"
                    )
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                            .clip(CircleShape)
                            .background(VaultRed.copy(alpha = 0.92f))
                            .clickable {
                                Log.d(TAG, "Landscape center play/pause tapped")
                                onAction(PlaybackContract.Action.TogglePlayPause)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint     = VaultWhite,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }
        }

        // Seek flanks (only when overlay showing)
        if (showOverlay && state.playerStateName != "BUFFERING") {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(130.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                GhostIconBtn(Icons.Filled.Replay10, size = 28) { onAction(PlaybackContract.Action.SeekBack) }
                GhostIconBtn(Icons.Filled.Forward10, size = 28) { onAction(PlaybackContract.Action.SeekForward) }
            }
        }

        // Controls overlay (top + bottom bars)
        AnimatedVisibility(
            visible = showOverlay,
            enter   = fadeIn(tween(200)),
            exit    = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth().height(90.dp)
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(.75f), Color.Transparent)))
                )
                // Bottom scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth().height(130.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.85f))))
                )

                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GhostIconBtn(Icons.AutoMirrored.Filled.ArrowBack) {
                        Log.i(TAG, "Landscape back tapped")
                        onNavigateBack()
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        video.title,
                        color    = VaultWhite,
                        style    = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.weight(1f))
                    if (video.isLiveStream) {
                        LiveBadge()
                        Spacer(Modifier.width(10.dp))
                    }
                    GhostIconBtn(Icons.Filled.Tune) {
                        Log.d(TAG, "Landscape settings opened")
                        showSettings = true
                        showOverlay = true
                    }
                }

                // Bottom controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Seek bar
                    if (!state.isLiveStream || state.isLiveSeekable) {
                        VaultSeekBar(
                            position = state.positionMs,
                            duration = state.durationMs,
                            buffered = state.bufferedPercent,
                            onSeek   = { onAction(PlaybackContract.Action.SeekTo(it)) }
                        )
                    }
                    // Time + action row
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!state.isLiveStream) {
                            Text(
                                "${formatDuration(state.positionMs)}  /  ${formatDuration(state.durationMs)}",
                                color = VaultWhite.copy(.75f),
                                style = MaterialTheme.typography.labelMedium
                            )
                        } else if (state.isLiveSeekable) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "DVR ${formatDuration(state.positionMs)}  /  ${formatDuration(state.durationMs)}",
                                    color = VaultWhite.copy(.75f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                LiveEdgeButton { onAction(PlaybackContract.Action.SeekToLiveEdge) }
                            }
                        } else {
                            LiveEdgeButton { onAction(PlaybackContract.Action.SeekToLiveEdge) }
                        }
                        Spacer(Modifier.weight(1f))
                        // Quick-access chips row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            GhostIconBtn(
                                icon = if (state.isMuted) Icons.AutoMirrored.Filled.VolumeOff
                                       else Icons.AutoMirrored.Filled.VolumeUp,
                                tint = if (state.isMuted) VaultTextSecond else VaultWhite
                            ) { onAction(PlaybackContract.Action.ToggleMute) }

                            StatusChip(state.selectedQualityLabel.ifBlank { "Auto" })
                            StatusChip(state.selectedCaptionLabel)
                            StatusChip("${state.playbackSpeed}×")
                        }
                    }
                }
            }
        }

        // Settings side panel
        AnimatedVisibility(
            visible = showSettings,
            enter   = slideInHorizontally(tween(280)) { it } + fadeIn(tween(260)),
            exit    = slideOutHorizontally(tween(240)) { it } + fadeOut(tween(200))
        ) {
            LandscapeSettingsPanel(
                state     = state,
                video     = video,
                onDismiss = {
                    Log.d(TAG, "Landscape settings dismissed")
                    showSettings = false
                },
                onAction  = onAction
            )
        }
    }
}

@Composable
private fun GhostIconBtn(
    icon: ImageVector,
    size: Int = 22,
    tint: Color = VaultWhite,
    onClick: () -> Unit
) {
    IconButton(
        onClick  = onClick,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(.35f))
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size.dp))
    }
}

@Composable
private fun StatusChip(label: String) {
    Text(
        label,
        color    = VaultWhite.copy(.8f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(Color.White.copy(.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun LiveBadge() {
    Box(
        modifier = Modifier
            .background(VaultRed, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text("● LIVE", color = VaultWhite, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun LiveEdgeButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(VaultRed)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text("● GO LIVE", color = VaultWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Landscape Settings Panel ─────────────────────────────────────────────────

@Composable
private fun LandscapeSettingsPanel(
    state: PlaybackContract.State,
    video: Video,
    onDismiss: () -> Unit,
    onAction: (PlaybackContract.Action) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Tracks", "Speed", "Bitrate")

    Row(modifier = Modifier.fillMaxSize()) {
        // Dim backdrop
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Color.Black.copy(.55f))
                .clickable(onClick = onDismiss)
        )
        // Panel
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(310.dp)
                .background(VaultSurface)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Settings", color = VaultWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, null, tint = VaultTextSecond, modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(color = VaultDivider, thickness = 1.dp)

            // Scrollable tab strip
            LazyRow(
                contentPadding    = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tabs.size) { i ->
                    val sel = tab == i
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (sel) VaultRed else VaultElevated)
                            .clickable { tab = i }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tabs[i],
                            color      = if (sel) VaultWhite else VaultTextSecond,
                            fontSize   = 12.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            HorizontalDivider(color = VaultDivider, thickness = 1.dp)

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                when (tab) {
                    0 -> OverviewContent(state = state, video = video)
                    1 -> TracksContent(state = state, onAction = onAction)
                    2 -> SpeedContent(state = state, onAction = onAction)
                    3 -> BitrateContent(state = state, onAction = onAction)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PORTRAIT PLAYER — VIDEO + TABBED DETAIL
// ─────────────────────────────────────────────────────────────────────────────


@Composable
private fun ControlBtn(icon: ImageVector, tint: Color = VaultTextPrimary, onClick: () -> Unit) {
    IconButton(
        onClick  = onClick,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(VaultElevated)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SHARED PANEL CONTENT  (used in both landscape settings panel + portrait tabs)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OverviewContent(state: PlaybackContract.State, video: Video) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(video.title, color = VaultWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(video.description, color = VaultTextSecond, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoTag(video.protocol.name)
            if (video.isLiveStream)        InfoTag("LIVE", VaultRed)
            if (video.supportsDvr)         InfoTag("DVR",  VaultOrange)
            if (video.protection != null)  InfoTag("DRM",  Color(0xFF7B1FA2))
            if (video.adsEnabled)          InfoTag("ADS",  VaultGreen)
            if (video.captions.isNotEmpty()) InfoTag("CC", VaultBlue)
        }
        HorizontalDivider(color = VaultDivider)
        listOf(
            "State"      to state.playerStateName,
            "Resolution" to state.videoResolution,
            "Quality"    to state.selectedQualityLabel,
            "Audio"      to state.selectedAudioLabel.ifEmpty { "Auto" },
            "Captions"   to state.selectedCaptionLabel,
            "Speed"      to "${state.playbackSpeed}×",
            "Buffered"   to "${state.bufferedPercent}%",
            "Live Delay" to if (state.isLiveStream) formatDuration(state.liveOffsetMs) else "N/A",
            "Muted"      to if (state.isMuted) "Yes" else "No"
        ).chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth()) {
                pair.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, color = VaultTextSecond, fontSize = 10.sp, letterSpacing = 0.5.sp)
                        Text(value, color = VaultTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        state.errorMessage?.let { err ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF3B1212), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = Color(0xFFCF6679), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(err, color = Color(0xFFCF6679), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TracksContent(
    state: PlaybackContract.State,
    onAction: (PlaybackContract.Action) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // Video quality
        TrackGroup(
            heading   = "VIDEO QUALITY",
            autoLabel = "Auto (ABR)",
            autoSelected = state.selectedQualityLabel == "Auto",
            onAuto    = { onAction(PlaybackContract.Action.SetAutoQuality) }
        )
        {
            state.videoTracks.forEach { t ->
                TrackPill(
                    label    = t.label,
                    selected = t.matchesSelectedTrack(
                        groupIndex = state.selectedQualityGroupIndex,
                        trackIndex = state.selectedQualityTrackIndex
                    ),
                    onClick  = { onAction(PlaybackContract.Action.SelectVideoTrack(t)) }
                )
            }
            if (state.videoTracks.isEmpty()) {
                TrackEmptyHint("Quality tracks load after playback starts")
            }
        }

        // Audio
        TrackGroup(heading = "AUDIO") {
            state.audioTracks.forEach { t ->
                TrackPill(
                    label    = t.label,
                    selected = t.matchesSelectedTrack(
                        groupIndex = state.selectedAudioGroupIndex,
                        trackIndex = state.selectedAudioTrackIndex
                    ),
                    onClick  = { onAction(PlaybackContract.Action.SelectAudioTrack(t)) }
                )
            }
            if (state.audioTracks.isEmpty()) {
                TrackEmptyHint("No selectable audio tracks")
            }
        }

        // Subtitles
        TrackGroup(
            heading      = "SUBTITLES",
            autoLabel    = "Off",
            autoSelected = state.selectedCaptionLabel == "Off",
            onAuto       = { onAction(PlaybackContract.Action.DisableCaptions) }
        ) {
            state.captionTracks.forEach { t ->
                TrackPill(
                    label    = t.label,
                    selected = t.matchesSelectedTrack(
                        groupIndex = state.selectedCaptionGroupIndex,
                        trackIndex = state.selectedCaptionTrackIndex
                    ),
                    onClick  = { onAction(PlaybackContract.Action.SelectCaptionTrack(t)) }
                )
            }
            if (state.captionTracks.isEmpty()) {
                TrackEmptyHint("No subtitle tracks available")
            }
        }
    }
}

private fun TrackInfo.matchesSelectedTrack(
    groupIndex: Int?,
    trackIndex: Int?
): Boolean = groupIndex == this.groupIndex && trackIndex == this.trackIndex

@Composable
private fun TrackGroup(
    heading: String,
    autoLabel: String? = null,
    autoSelected: Boolean = false,
    onAuto: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            heading,
            color      = VaultRed,
            fontSize   = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.8.sp
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding        = PaddingValues(vertical = 2.dp)
        ) {
            if (autoLabel != null && onAuto != null) {
                item {
                    TrackPill(label = autoLabel, selected = autoSelected, onClick = onAuto)
                }
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun TrackPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg    = if (selected) VaultRed else VaultCard
    val text  = if (selected) VaultWhite else VaultTextPrimary
    val bdr   = if (selected) VaultRed else VaultDivider

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .border(1.dp, bdr, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Text(label, color = text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, softWrap = false)
        if (selected) {
            Icon(Icons.Filled.Check, null, tint = VaultWhite, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun TrackEmptyHint(text: String) {
    Text(text, color = VaultTextMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun SpeedContent(state: PlaybackContract.State, onAction: (PlaybackContract.Action) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("PLAYBACK SPEED", color = VaultRed, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                val sel = state.playbackSpeed == speed
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) VaultRed else VaultElevated)
                        .border(1.dp, if (sel) VaultRed else VaultDivider, RoundedCornerShape(8.dp))
                        .clickable { onAction(PlaybackContract.Action.SetSpeed(speed)) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${speed}×",
                        color      = if (sel) VaultWhite else VaultTextSecond,
                        fontSize   = 12.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun BitrateContent(state: PlaybackContract.State, onAction: (PlaybackContract.Action) -> Unit) {
    val active = state.activeBitrateLabel
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("BITRATE CAP", color = VaultRed, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                BitrateBtn("Lowest",  Modifier.weight(1f), active == "Lowest")  { onAction(PlaybackContract.Action.ForceLowestBitrate) }
                BitrateBtn("1 Mbps",  Modifier.weight(1f), active == "1 Mbps") { onAction(PlaybackContract.Action.SetMaxBitrate(1_000_000)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                BitrateBtn("3 Mbps",  Modifier.weight(1f), active == "3 Mbps") { onAction(PlaybackContract.Action.SetMaxBitrate(3_000_000)) }
                BitrateBtn("Auto ▲", Modifier.weight(1f), active == "Auto ▲") { onAction(PlaybackContract.Action.ClearBitrateCap) }
            }
        }
    }
}

@Composable
private fun BitrateBtn(label: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (primary) VaultRed else VaultElevated)
            .border(1.dp, if (primary) VaultRed else VaultDivider, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color      = if (primary) VaultWhite else VaultTextPrimary,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun InfoTag(label: String, bg: Color = VaultElevated) {
    Text(
        label,
        color      = if (bg == VaultElevated) VaultTextSecond else VaultWhite,
        fontSize   = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier   = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

// ─── Custom Seek Bar (Canvas) ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSeekBar(
    position: Long,
    duration: Long,
    buffered: Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onSeekStart: () -> Unit = {},
    onSeekEnd: () -> Unit = {}
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val bufFrac = (buffered / 100f).coerceIn(0f, 1f)
    val playedFrac = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val displayFrac = if (isDragging) dragFraction else playedFrac

    Slider(
        value = displayFrac,
        onValueChange = { fraction ->
            if (!isDragging) {
                isDragging = true
                onSeekStart()          // ← TELL PARENT "drag started"
            }
            dragFraction = fraction
        },
        onValueChangeFinished = {
            onSeek((dragFraction * duration).toLong())
            isDragging = false
            onSeekEnd()               // ← TELL PARENT "drag ended"
        },
        modifier = modifier.fillMaxWidth(),
        thumb = {
            val thumbSize by animateDpAsState(
                targetValue = if (isDragging) 20.dp else 16.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "thumbSize"
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(VaultRed)
            ) {
                Box(
                    modifier = Modifier
                        .size(thumbSize * 0.35f)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        },
        track = { sliderState ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isDragging) 5.dp else 4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.14f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufFrac)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.28f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(sliderState.value)
                        .fillMaxHeight()
                        .background(VaultRed)
                )
            }
        }
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s   = ms / 1000
    val h   = s / 3600
    val m   = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%d:%02d", m, sec)
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(name = "Portrait Player", showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun PortraitPreview() {
    AppTheme {
        LandscapeImmersivePlayer (
            state          = previewState,
            player         = null,
            video          = previewVideo,
            onAction       = {},
            onNavigateBack = {}
        )
    }
}

private val previewVideo = Video(
    id = "p1", title = "Big Buck Bunny — HLS", description = "Open source short film.",
    streamUrl = "", protocol = StreamProtocol.HLS, badge = "HLS"
)
private val previewState = PlaybackContract.State(
    isPlaying = true, playerStateName = "READY",
    positionMs = 185_000L, durationMs = 725_000L,
    bufferedPercent = 68, videoResolution = "1920×1080",
    playbackSpeed = 1.0f,
    selectedQualityLabel = "1920×1080 (5800kbps)",
    selectedAudioLabel   = "en 2ch (128kbps)",
    selectedCaptionLabel = "English",
    videoTracks  = listOf(
        TrackInfo(0, 0, "426×240 (450kbps)",  false),
        TrackInfo(0, 1, "1280×720 (3200kbps)", false),
        TrackInfo(0, 2, "1920×1080 (5800kbps)", true)
    ),
    audioTracks  = listOf(
        TrackInfo(1, 0, "en 2ch (128kbps)", true),
        TrackInfo(1, 1, "hi 2ch (128kbps)", false)
    ),
    captionTracks = listOf(
        TrackInfo(2, 0, "English", true),
        TrackInfo(2, 1, "Hindi",   false)
    )
)

package com.example.exoplayerdummy.presentation.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
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
            win?.let { w ->
                WindowCompat.getInsetsController(w, w.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(video.id) {
        Log.i(TAG, "Loading ${video.title}")
        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        viewModel.onAction(PlaybackContract.Action.LoadVideo(video))
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE  -> viewModel.onAction(PlaybackContract.Action.OnPause)
                Lifecycle.Event.ON_RESUME -> viewModel.onAction(PlaybackContract.Action.OnResume)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onAction(PlaybackContract.Action.StopPlayback)
            (context as? Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    PlaybackScreen(
        state          = state,
        player         = viewModel.playbackController.player,
        video          = video,
        onAction       = viewModel::onAction,
        onNavigateBack = onNavigateBack
    )
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
    } else {
        PortraitPlayerView(
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
        update  = { pv -> pv.player = player },
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

    // Auto-hide overlay 3.5s after it becomes visible (only while playing, not in settings)
    LaunchedEffect(showOverlay, state.isPlaying, showSettings) {
        if (showOverlay && state.isPlaying && !showSettings) {
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
                    if (!showSettings) showOverlay = !showOverlay
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
                            .clickable { onAction(PlaybackContract.Action.TogglePlayPause) },
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
                    GhostIconBtn(Icons.AutoMirrored.Filled.ArrowBack) { onNavigateBack() }
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
                    GhostIconBtn(Icons.Filled.Tune) { showSettings = true; showOverlay = true }
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
                    if (!state.isLiveStream) {
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
                onDismiss = { showSettings = false },
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
private fun PortraitPlayerView(
    state: PlaybackContract.State,
    player: ExoPlayer?,
    video: Video,
    onAction: (PlaybackContract.Action) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultBlack)
    ) {
        // ── Video surface ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            VideoSurfaceComposable(player = player, modifier = Modifier.fillMaxSize())

            // Back button
            IconButton(
                onClick  = onNavigateBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(.55f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = VaultWhite, modifier = Modifier.size(18.dp))
            }

            // Fullscreen
            IconButton(
                onClick = {
                    (context as? Activity)?.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(.45f))
            ) {
                Icon(Icons.Filled.Fullscreen, null, tint = VaultWhite, modifier = Modifier.size(18.dp))
            }

            // Center: buffering OR play-pause — never both
            AnimatedContent(
                targetState  = state.playerStateName == "BUFFERING",
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                label        = "centerPortrait",
                modifier     = Modifier.align(Alignment.Center)
            ) { isBuffering ->
                if (isBuffering) {
                    CircularProgressIndicator(
                        color       = VaultRed,
                        strokeWidth = 3.dp,
                        modifier    = Modifier.size(44.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(.55f))
                            .clickable { onAction(PlaybackContract.Action.TogglePlayPause) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            null, tint = VaultWhite, modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // ── Seek bar + controls (persistent, outside tabs) ──────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VaultSurface)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!state.isLiveStream && state.durationMs > 0) {
                VaultSeekBar(
                    position = state.positionMs,
                    duration = state.durationMs,
                    buffered = state.bufferedPercent,
                    onSeek   = { onAction(PlaybackContract.Action.SeekTo(it)) }
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(state.positionMs), color = VaultTextSecond, style = MaterialTheme.typography.labelMedium)
                    Text(formatDuration(state.durationMs), color = VaultTextSecond, style = MaterialTheme.typography.labelMedium)
                }
            } else if (state.isLiveStream) {
                LiveEdgeButton { onAction(PlaybackContract.Action.SeekToLiveEdge) }
            }

            // Controls row — spacious
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                ControlBtn(Icons.Filled.Replay10)  { onAction(PlaybackContract.Action.SeekBack) }
                ControlBtn(Icons.Filled.Forward10) { onAction(PlaybackContract.Action.SeekForward) }
                ControlBtn(
                    if (state.isMuted) Icons.AutoMirrored.Filled.VolumeOff
                    else Icons.AutoMirrored.Filled.VolumeUp,
                    tint = if (state.isMuted) VaultTextSecond else VaultTextPrimary
                ) { onAction(PlaybackContract.Action.ToggleMute) }
            }
        }

        // ── Tab row ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VaultSurface)
        ) {
            listOf("Overview", "Tracks", "Settings").forEachIndexed { i, title ->
                val sel = tab == i
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { tab = i }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        title,
                        color      = if (sel) VaultRed else VaultTextSecond,
                        fontSize   = 12.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .fillMaxWidth(if (sel) 0.7f else 0f)
                            .background(VaultRed, RoundedCornerShape(1.dp))
                    )
                }
            }
        }
        HorizontalDivider(color = VaultDivider, thickness = 1.dp)

        // ── Tab content ──────────────────────────────────────────────────────
        AnimatedContent(
            targetState  = tab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                slideInHorizontally(tween(240)) { dir * it } + fadeIn(tween(200)) togetherWith
                        slideOutHorizontally(tween(200)) { -dir * it } + fadeOut(tween(160))
            },
            label    = "tabContent",
            modifier = Modifier
                .weight(1f)
                .background(VaultBlack)
        ) { selectedTab ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                when (selectedTab) {
                    0 -> OverviewContent(state = state, video = video)
                    1 -> TracksContent(state = state, onAction = onAction)
                    2 -> Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        SpeedContent(state = state, onAction = onAction)
                        BitrateContent(state = state, onAction = onAction)
                    }
                }
            }
        }
    }
}

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
        ) {
            state.videoTracks.forEach { t ->
                TrackPill(
                    label    = t.label,
                    selected = t.label == state.selectedQualityLabel || (state.selectedQualityLabel != "Auto" && t.isSelected),
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
                    selected = t.label == state.selectedAudioLabel || t.isSelected,
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
                    selected = t.label == state.selectedCaptionLabel,
                    onClick  = { onAction(PlaybackContract.Action.SelectCaptionTrack(t)) }
                )
            }
            if (state.captionTracks.isEmpty()) {
                TrackEmptyHint("No subtitle tracks available")
            }
        }
    }
}

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
            item { Row { content() } }
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
        horizontalArrangement = Arrangement.spacedBy(6.dp)
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

@Composable
private fun VaultSeekBar(
    position: Long,
    duration: Long,
    buffered: Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val red    = VaultRed
    val buf    = Color.White.copy(alpha = 0.28f)
    val track  = Color.White.copy(alpha = 0.14f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(duration) {
                awaitPointerEventScope {
                    while (true) {
                        val ev  = awaitPointerEvent()
                        val pos = ev.changes.firstOrNull()?.position ?: continue
                        if (ev.changes.any { it.pressed } && duration > 0) {
                            val frac = (pos.x / size.width).coerceIn(0f, 1f)
                            onSeek((frac * duration).toLong())
                        }
                    }
                }
            }
    ) {
        val trackH  = 4.dp.toPx()
        val thumbR  = 8.dp.toPx()
        val y       = size.height / 2f
        val played  = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val bufFrac = (buffered / 100f).coerceIn(0f, 1f)

        // Background
        drawRoundRect(track, Offset(0f, y - trackH / 2), Size(size.width, trackH), CornerRadius(trackH / 2))
        // Buffered
        drawRoundRect(buf,   Offset(0f, y - trackH / 2), Size(size.width * bufFrac, trackH), CornerRadius(trackH / 2))
        // Played
        if (played > 0f) {
            drawRoundRect(red, Offset(0f, y - trackH / 2), Size(size.width * played, trackH), CornerRadius(trackH / 2))
        }
        // Thumb
        val tx = (size.width * played).coerceIn(thumbR, size.width - thumbR)
        drawCircle(red,           thumbR,         Offset(tx, y))
        drawCircle(Color.White,   thumbR * 0.35f, Offset(tx, y))
    }
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
        PortraitPlayerView(
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

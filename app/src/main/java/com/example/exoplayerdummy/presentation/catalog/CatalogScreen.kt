package com.example.exoplayerdummy.presentation.catalog

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.exoplayerdummy.core.presentation.ObserveAsEvents
import com.example.exoplayerdummy.domain.model.CaptionTrack
import com.example.exoplayerdummy.domain.model.ContentProtection
import com.example.exoplayerdummy.domain.model.StreamProtocol
import com.example.exoplayerdummy.domain.model.Video
import com.example.exoplayerdummy.ui.theme.*
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

private const val TAG = "CatalogScreen"

// ─── Root ─────────────────────────────────────────────────────────────────────

@Composable
fun CatalogRoot(
    onNavigateToPlayer: (String) -> Unit,
    viewModel: CatalogViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is CatalogContract.Event.NavigateToPlayer -> onNavigateToPlayer(event.videoId)
        }
    }
    CatalogScreen(state = state, onAction = viewModel::onAction)
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun CatalogScreen(
    state: CatalogContract.State,
    onAction: (CatalogContract.Action) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VaultBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CatalogTopBar()

            if (state.isLoading) {
                CatalogShimmer(modifier = Modifier.weight(1f))
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (state.videos.isNotEmpty()) {
                        HeroCarousel(videos = state.videos, onAction = onAction)
                        Spacer(Modifier.height(28.dp))
                        ContentRows(videos = state.videos, onAction = onAction)
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@Composable
private fun CatalogTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(VaultBlack)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(VaultRed, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.PlayArrow, null, tint = VaultWhite, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(
            "StreamVault",
            color = VaultWhite,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp
        )
    }
}

// ─── Hero Carousel ────────────────────────────────────────────────────────────

@Composable
private fun HeroCarousel(
    videos: List<Video>,
    onAction: (CatalogContract.Action) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { videos.size })

    LaunchedEffect(pagerState.currentPage) {
        delay(4_500)
        val next = (pagerState.currentPage + 1) % videos.size
        pagerState.animateScrollToPage(next)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val video = videos[page]
            // Parallax-ish scale effect on non-current pages
            Box(modifier = Modifier.graphicsLayer {
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val scale = 1f - 0.05f * kotlin.math.abs(pageOffset)
                scaleX = scale
                scaleY = scale
            }) {
                HeroPage(
                    video = video,
                    onClick = {
                        Log.i(TAG, "Hero tapped: ${video.title}")
                        onAction(CatalogContract.Action.OnVideoSelected(video))
                    }
                )
            }
        }

        // Page indicators
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(videos.size) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .animateContentSize(tween(200))
                        .height(4.dp)
                        .width(if (selected) 24.dp else 5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (selected) VaultRed else VaultTextMuted.copy(alpha = 0.5f)
                        )
                )
            }
        }
    }
}

@Composable
private fun HeroPage(video: Video, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp)
            .clickable(onClick = onClick)
    ) {
        // Tinted background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            heroTint(video.protocol),
                            VaultCard
                        )
                    )
                )
        )

        // Large protocol icon as art
        Icon(
            imageVector = protocolIcon(video.protocol),
            contentDescription = null,
            tint = VaultWhite.copy(alpha = 0.05f),
            modifier = Modifier
                .size(230.dp)
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
        )

        // Bottom fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, VaultBlack)
                    )
                )
        )

        // Content overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 72.dp, bottom = 46.dp)
        ) {
            if (video.isLiveStream) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    LiveDot()
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "LIVE NOW",
                        color = VaultRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                }
            }
            Text(
                video.title,
                color = VaultWhite,
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Text(
                video.description,
                color = VaultTextSecond,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Watch button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(VaultWhite)
                        .clickable(onClick = onClick)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, null, tint = VaultBlack, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Watch", color = VaultBlack, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                // More Info button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(VaultTextSecond.copy(alpha = 0.25f))
                        .border(1.dp, VaultDivider, RoundedCornerShape(5.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = VaultWhite, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Info", color = VaultWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ─── Content Rows ─────────────────────────────────────────────────────────────

@Composable
private fun ContentRows(videos: List<Video>, onAction: (CatalogContract.Action) -> Unit) {
    val liveVideos   = videos.filter { it.isLiveStream }
    val vodVideos    = videos.filter { !it.isLiveStream }

    if (liveVideos.isNotEmpty()) {
        RowSection(
            title = "LIVE NOW",
            icon = Icons.Filled.FiberManualRecord,
            iconTint = VaultRed,
            videos = liveVideos,
            cardStyle = CardStyle.LIVE,
            onAction = onAction
        )
        Spacer(Modifier.height(28.dp))
    }
    if (vodVideos.isNotEmpty()) {
        RowSection(
            title = "ON DEMAND",
            icon = Icons.Filled.VideoLibrary,
            videos = vodVideos,
            cardStyle = CardStyle.POSTER,
            onAction = onAction
        )
        Spacer(Modifier.height(28.dp))
    }
    RowSection(
        title = "ALL CONTENT",
        icon = Icons.Filled.GridView,
        videos = videos,
        cardStyle = CardStyle.WIDE,
        onAction = onAction
    )
}

private enum class CardStyle { POSTER, LIVE, WIDE }

@Composable
private fun RowSection(
    title: String,
    icon: ImageVector,
    iconTint: Color = VaultTextSecond,
    videos: List<Video>,
    cardStyle: CardStyle,
    onAction: (CatalogContract.Action) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                title,
                color = VaultTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(videos) { video ->
                val onClick = {
                    Log.i(TAG, "Row tapped: ${video.title}")
                    onAction(CatalogContract.Action.OnVideoSelected(video))
                }
                when (cardStyle) {
                    CardStyle.POSTER -> PosterCard(video, onClick)
                    CardStyle.LIVE   -> LiveCard(video, onClick)
                    CardStyle.WIDE   -> WideCard(video, onClick)
                }
            }
        }
    }
}

// ─── Card Variants ────────────────────────────────────────────────────────────

@Composable
private fun PosterCard(video: Video, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(175.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(VaultCard)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(125.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(heroTint(video.protocol), VaultElevated)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                protocolIcon(video.protocol), null,
                tint = VaultWhite.copy(alpha = 0.35f),
                modifier = Modifier.size(38.dp)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                video.title,
                color = VaultTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
        }
        if (video.badge.isNotEmpty()) {
            Text(
                video.badge,
                color = VaultWhite,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(VaultRed, RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        // Overlay icons for DRM / ADS
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (video.protection != null) {
                Icon(Icons.Filled.Lock, null, tint = VaultGold, modifier = Modifier.size(11.dp))
            }
            if (video.adsEnabled) {
                Icon(Icons.Filled.CurrencyExchange, null, tint = VaultGreen, modifier = Modifier.size(11.dp))
            }
        }
    }
}

@Composable
private fun LiveCard(video: Video, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(200.dp)
            .height(115.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(VaultCard)
            .border(1.dp, VaultRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(VaultRed.copy(alpha = 0.18f), VaultCard),
                        radius = 320f
                    )
                )
        )
        Icon(
            Icons.Filled.PlayCircle, null,
            tint = VaultWhite.copy(alpha = 0.18f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(26.dp)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot()
                Spacer(Modifier.width(5.dp))
                Text(
                    "LIVE",
                    color = VaultWhite,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                if (video.supportsDvr) {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "DVR",
                        color = VaultTextSecond,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                video.title,
                color = VaultWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun WideCard(video: Video, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(VaultCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(protocolIcon(video.protocol), null, tint = VaultTextMuted, modifier = Modifier.size(26.dp))
            if (video.isLiveStream) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(VaultRed, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiveDot()
                    Spacer(Modifier.width(3.dp))
                    Text("LIVE", color = VaultWhite, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(video.title, color = VaultTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
        Text(video.protocol.name, color = VaultTextSecond, fontSize = 9.sp, letterSpacing = 0.5.sp)
    }
}

// ─── Loading shimmer ──────────────────────────────────────────────────────────

@Composable
private fun CatalogShimmer(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        // Hero placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
                .background(VaultCard)
        )
        Spacer(Modifier.height(28.dp))
        // Row placeholder
        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            repeat(2) {
                Column {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(12.dp)
                            .background(VaultElevated, RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(4) {
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .height(175.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(VaultCard)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Small Helpers ────────────────────────────────────────────────────────────

@Composable
private fun LiveDot() {
    var tick by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) { tick = !tick; delay(800) }
    }
    val alpha by animateFloatAsState(
        targetValue = if (tick) 1f else 0.2f,
        animationSpec = tween(700),
        label = "liveDot"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .graphicsLayer(alpha = alpha)
            .background(VaultRed, RoundedCornerShape(50))
    )
}

private fun heroTint(protocol: StreamProtocol): Color = when (protocol) {
    StreamProtocol.HLS        -> Color(0xFF1E0707)
    StreamProtocol.DASH       -> Color(0xFF070A1E)
    StreamProtocol.PROGRESSIVE -> Color(0xFF071E0A)
}

private fun protocolIcon(protocol: StreamProtocol) = when (protocol) {
    StreamProtocol.HLS        -> Icons.Filled.Stream
    StreamProtocol.DASH       -> Icons.Filled.Dashboard
    StreamProtocol.PROGRESSIVE -> Icons.Filled.VideoFile
}

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(name = "Catalog", showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun CatalogPreview() {
    AppTheme { CatalogScreen(state = CatalogContract.State(videos = previewVideos), onAction = {}) }
}

@Preview(name = "Catalog Loading", showBackground = true, widthDp = 390, heightDp = 400)
@Composable
private fun CatalogLoadingPreview() {
    AppTheme { CatalogScreen(state = CatalogContract.State(isLoading = true), onAction = {}) }
}

private val previewCaption = CaptionTrack("https://example.com/en.vtt", "text/vtt", "en", "English")
private val previewVideos = listOf(
    Video(id = "1", title = "Live Sports HD", description = "Live broadcast with DVR support.", streamUrl = "", protocol = StreamProtocol.HLS, isLiveStream = true, supportsDvr = true, badge = "LIVE"),
    Video(id = "2", title = "HLS Adaptive Stream", description = "Multi-bitrate HLS with embedded subtitles.", streamUrl = "", protocol = StreamProtocol.HLS, badge = "HLS", captions = listOf(previewCaption)),
    Video(id = "3", title = "DASH with Captions", description = "DASH stream with sideloaded WebVTT.", streamUrl = "", protocol = StreamProtocol.DASH, badge = "DASH", captions = listOf(previewCaption)),
    Video(id = "4", title = "DRM Protected VOD", description = "Widevine DASH with IMA pre-roll ads.", streamUrl = "", protocol = StreamProtocol.DASH, protection = ContentProtection(licenseUrl = ""), adsEnabled = true, badge = "DRM"),
    Video(id = "5", title = "Progressive MP4", description = "Direct MP4 file — no adaptive bitrate.", streamUrl = "", protocol = StreamProtocol.PROGRESSIVE, badge = "MP4"),
)

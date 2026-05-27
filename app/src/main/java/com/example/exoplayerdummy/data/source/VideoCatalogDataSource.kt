package com.example.exoplayerdummy.data.source

import com.example.exoplayerdummy.AppLogger as Log
import com.example.exoplayerdummy.domain.model.ContentProtection
import com.example.exoplayerdummy.domain.model.StreamProtocol
import com.example.exoplayerdummy.domain.model.Video

interface VideoCatalogDataSource {
    fun loadCatalog(): List<Video>
}

class LocalVideoCatalogDataSource : VideoCatalogDataSource {

    companion object {
        private const val TAG = "LocalCatalogSource"

        private const val WIDEVINE_DEMO_LICENSE_URL =
            "https://cwip-shaka-proxy.appspot.com/no_auth"
    }

    override fun loadCatalog(): List<Video> {
        Log.d(TAG, "Loading local video catalog")

        return listOf(

            // ─────────────────────────────────────────────
            // HLS #1 — Angel One HLS (Google Shaka demo asset)
            // Audio: EN (×2), DE, IT, ES, FR
            // Subtitles: EN, ES, FR (embedded in manifest)
            // Quality: 5 adaptive renditions
            // Content: Star Trek episode clip
            // ─────────────────────────────────────────────
            Video(
                id = "hls_angel_one_multilingual",
                title = "Angel One — HLS Multilingual",
                description = "HLS stream with 6 audio language tracks (EN×2, DE, IT, ES, FR) and subtitle tracks (EN, ES, FR). 5 adaptive quality renditions. From Google Shaka demo assets.",
                streamUrl = "https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8",
                protocol = StreamProtocol.HLS,
                badge = "HLS",
                hasSubtitleTracks = true,
                hasAudioTracks = true
            ),


            // ─────────────────────────────────────────────
            // DASH #1 — Angel One DASH (Google Shaka demo asset)
            // Audio: EN, ES, DE + more language tracks
            // Subtitles: Multiple languages (WebVTT)
            // Quality: Multiple renditions — H.264 + VP9 (multi-codec)
            // Content: Star Trek episode clip
            // ─────────────────────────────────────────────
            Video(
                id = "dash_angel_one_multicodec",
                title = "Angel One — DASH Multicodec & Multilingual",
                description = "DASH stream with H.264 and VP9 video codec variants, multiple audio language tracks (EN, ES, DE+), and subtitle tracks. Best for testing codec negotiation + track switching.",
                streamUrl = "https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd",
                protocol = StreamProtocol.DASH,
                badge = "DASH",
                hasSubtitleTracks = true
            ),


            Video(
                id = "dash_test_vectors",
                title = "Test Vectors — DASH",
                description = "DASH VOD with multiple audio tracks and 44 subtitle language options (WebVTT). Stress-tests your subtitle picker UI and verifies ExoPlayer handles large track lists cleanly.",
                streamUrl = "https://media.axprod.net/TestVectors/v7-Clear/Manifest_1080p.mpd",
                protocol = StreamProtocol.DASH,
                badge = "DASH",
                hasSubtitleTracks = true,
                hasAudioTracks = true

            ),

            // ─────────────────────────────────────────────
            // DRM #1 — Widevine DASH: Angel One (Google Shaka)
            // Audio: EN, ES, DE + more (multilingual, preserved through DRM)
            // Subtitles: Multiple languages (preserved through DRM)
            // Quality: Multiple adaptive renditions
            // Purpose: Tests full Widevine license acquisition flow on DASH
            // ─────────────────────────────────────────────
            Video(
                id = "drm_widevine_angel_one_dash",
                title = "Widevine DRM — Angel One DASH",
                description = "Widevine-protected DASH stream with multilingual audio and subtitle tracks. Uses Shaka's public no-auth license proxy. Tests DRM license flow + track switching on DASH.",
                streamUrl = "https://storage.googleapis.com/shaka-demo-assets/angel-one-widevine/dash.mpd",
                protocol = StreamProtocol.DASH,
                protection = ContentProtection(
                    licenseUrl = WIDEVINE_DEMO_LICENSE_URL,
                    multiSession = true
                ),
                badge = "DRM",
                hasSubtitleTracks = true,
                hasAudioTracks = true
            ),
            // ─────────────────────────────────────────────
            // DRM #2 — Widevine HLS: Angel One (Google Shaka)
            // Audio: EN, ES, DE + more (multilingual, preserved through DRM)
            // Subtitles: Multiple languages (preserved through DRM)
            // Quality: Multiple adaptive renditions (fMP4 segments)
            // Purpose: Tests Widevine on HLS — a different ExoPlayer code path
            //          from the DASH DRM entry above
            // ─────────────────────────────────────────────
            Video(
                id = "drm_widevine_angel_one_hls",
                title = "Widevine DRM — Angel One HLS",
                description = "Widevine-protected HLS stream (fMP4 segments) with multilingual audio and subtitle tracks. Validates the DRM + HLS code path in ExoPlayer, separate from the DASH DRM path above.",
                streamUrl = "https://storage.googleapis.com/shaka-demo-assets/angel-one-widevine-hls/hls.m3u8",
                protocol = StreamProtocol.HLS,
                protection = ContentProtection(
                    licenseUrl = WIDEVINE_DEMO_LICENSE_URL,
                    multiSession = true
                ),
                badge = "DRM",
                hasSubtitleTracks = true,
                hasAudioTracks = true
            ),
            // ─────────────────────────────────────────────
// ─────────────────────────────────────────────
// LIVE #1 — NHK World Japan (HLS)
// Updated Jan 2026 — old akamaized.net domain retired
// New host: media-tyo.hls.nhkworld.jp (NHK's own CDN)
// Quality: Multiple adaptive renditions
// Geo: Worldwide — no geo-blocks by design
// ─────────────────────────────────────────────
            Video(
                id = "live_hls_nhk_world",
                title = "NHK World Japan — Live",
                description = "NHK World Japan 24/7 English live news and culture channel. Multiple adaptive quality renditions. Explicitly worldwide — no geo-restrictions.",
                streamUrl = "https://media-tyo.hls.nhkworld.jp/hls/w/live/master.m3u8",
                protocol = StreamProtocol.HLS,
                isLiveStream = true,
                supportsDvr = false,
                badge = "LIVE"
            ),

// ─────────────────────────────────────────────
// LIVE #2 — DW News English (HLS)
// Real 24/7 international news channel (Deutsche Welle, Germany)
// Quality: Multiple adaptive renditions
// Geo: No restrictions — global access by design (DW is a public broadcaster)
// Host: Akamai CDN — extremely reliable
// ─────────────────────────────────────────────
            Video(
                id = "live_hls_dw_news",
                title = "DW News — Live International News",
                description = "Deutsche Welle English 24/7 live news. Adaptive HLS stream on Akamai CDN. DW is Germany's public international broadcaster — globally accessible with no geo-blocks.",
                streamUrl = "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/stream05/streamPlaylist.m3u8",
                protocol = StreamProtocol.HLS,
                isLiveStream = true,
                supportsDvr = false,
                badge = "LIVE"
            ),


            ).also { catalog ->
            Log.i(
                TAG,
                "Catalog loaded: total=${catalog.size}, " +
                        "drm=${catalog.count { it.protection != null }}, " +
                        "hls=${catalog.count { it.protocol == StreamProtocol.HLS }}, " +
                        "dash=${catalog.count { it.protocol == StreamProtocol.DASH }}"
            )
        }
    }
}
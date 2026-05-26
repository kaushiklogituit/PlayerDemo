package com.example.exoplayerdummy.data.source

import android.util.Log
import com.example.exoplayerdummy.domain.model.CaptionTrack
import com.example.exoplayerdummy.domain.model.ContentProtection
import com.example.exoplayerdummy.domain.model.StreamProtocol
import com.example.exoplayerdummy.domain.model.Video

interface VideoCatalogDataSource {
    fun loadCatalog(): List<Video>
}

class LocalVideoCatalogDataSource : VideoCatalogDataSource {

    companion object {
        private const val TAG = "LocalCatalogSource"

        private const val WIDEVINE_DEMO_LICENSE_URL = "https://cwip-shaka-proxy.appspot.com/no_auth"
    }

    override fun loadCatalog(): List<Video> {
        Log.d(TAG, "Loading local video catalog")

        return listOf(
            Video(
                id = "hls_subtitles",
                title = "HLS with Embedded Subtitles",
                description = "Adaptive HLS stream with subtitle tracks in the manifest.",
                streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8",
                protocol = StreamProtocol.HLS,
                badge = "HLS"
            ),
            Video(
                id = "hls_advanced_tracks",
                title = "Apple HLS: Audio + Subtitles",
                description = "Advanced Apple HLS sample with adaptive quality variants, alternate audio, and subtitle tracks for track-selection practice.",
                streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/advanced/audio-video-subtitles.m3u8",
                protocol = StreamProtocol.HLS,
                badge = "HLS+"
            ),
            Video(
                id = "hls_mux_adaptive",
                title = "Mux HLS Adaptive Big Buck Bunny",
                description = "Stable public HLS stream with multiple adaptive quality renditions for manual quality switching.",
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                protocol = StreamProtocol.HLS,
                badge = "HLS"
            ),
            Video(
                id = "live_hls_akamai_test",
                title = "Akamai Live HLS Test",
                description = "Public live HLS test channel. Marked live so the catalog and player show the live/DVR UI paths.",
                streamUrl = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
                protocol = StreamProtocol.HLS,
                isLiveStream = true,
                supportsDvr = true,
                badge = "LIVE"
            ),
            Video(
                id = "live_hls_easelive_football",
                title = "EaseLive Football Live HLS",
                description = "Live HLS stream extracted from the EaseLive player-plugin sample. Includes a DVR query flag and is useful for validating live playback UI.",
                streamUrl = "https://eu-dev.stream.easelive.tv/fotball/ngrp:Stream1_all/playlist.m3u8?DVR",
                protocol = StreamProtocol.HLS,
                isLiveStream = true,
                supportsDvr = true,
                badge = "LIVE"
            ),
            Video(
                id = "dash_subtitles",
                title = "DASH with Subtitles",
                description = "Adaptive DASH stream with a sideloaded English WebVTT subtitle track.",
                streamUrl = "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd",
                protocol = StreamProtocol.DASH,
                captions = listOf(
                    CaptionTrack(
                        uri = "https://raw.githubusercontent.com/nickreeves96/webvtt-examples/master/subtitles.vtt",
                        mimeType = "text/vtt",
                        language = "en",
                        label = "English"
                    )
                ),
                badge = "DASH"
            ),
            Video(
                id = "dash_shaka_angel_one",
                title = "Shaka DASH: Angel One",
                description = "Public DASH sample for adaptive quality and available audio/subtitle track inspection.",
                streamUrl = "https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd",
                protocol = StreamProtocol.DASH,
                badge = "DASH"
            ),
            Video(
                id = "dash_bitmovin_sintel",
                title = "Bitmovin DASH: Sintel",
                description = "Public DASH sample commonly used to test adaptive quality, subtitles, and track switching.",
                streamUrl = "https://bitmovin-a.akamaihd.net/content/sintel/sintel.mpd",
                protocol = StreamProtocol.DASH,
                badge = "DASH"
            ),
            Video(
                id = "live_dash_dashif_livesim",
                title = "DASH-IF LiveSim",
                description = "DASH live simulation stream for validating live playback state, live offset, and DVR-style controls.",
                streamUrl = "https://livesim.dashif.org/livesim/testpic_2s/Manifest.mpd",
                protocol = StreamProtocol.DASH,
                isLiveStream = true,
                supportsDvr = true,
                badge = "LIVE"
            ),
            Video(
                id = "drm_widevine_sintel",
                title = "Widevine DRM: Sintel",
                description = "Public Widevine-protected DASH VOD with the Shaka demo license proxy for DRM implementation practice.",
                streamUrl = "https://storage.googleapis.com/shaka-demo-assets/sintel-widevine/dash.mpd",
                protocol = StreamProtocol.DASH,
                protection = ContentProtection(
                    licenseUrl = WIDEVINE_DEMO_LICENSE_URL,
                    multiSession = true
                ),
                badge = "DRM"
            ),
            Video(
                id = "drm_widevine_angel_one",
                title = "Widevine DRM: Angel One",
                description = "Public Widevine DASH sample for validating license acquisition and encrypted adaptive playback.",
                streamUrl = "https://storage.googleapis.com/shaka-demo-assets/angel-one-widevine/dash.mpd",
                protocol = StreamProtocol.DASH,
                protection = ContentProtection(
                    licenseUrl = WIDEVINE_DEMO_LICENSE_URL,
                    multiSession = true
                ),
                badge = "DRM"
            )
        ).also { Log.i(TAG, "Catalog loaded: ${it.size} entries") }
    }
}

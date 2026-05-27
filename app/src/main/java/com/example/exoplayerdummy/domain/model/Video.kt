package com.example.exoplayerdummy.domain.model

data class Video(
    val id: String,
    val title: String,
    val description: String,
    val streamUrl: String,
    val protocol: StreamProtocol,
    val isLiveStream: Boolean = false,
    val supportsDvr: Boolean = false,
    val thumbnailUrl: String = "",
    val captions: List<CaptionTrack> = emptyList(),
    val protection: ContentProtection? = null,
    val adsEnabled: Boolean = false,
    val adTagUri: String? = null,
    val badge: String = "",
    val hasSubtitleTracks: Boolean = false,
    val hasAudioTracks: Boolean = false
)

package com.example.exoplayerdummy.domain.model

data class ContentProtection(
    val scheme: String = "widevine",
    val licenseUrl: String,
    val multiSession: Boolean = false
)

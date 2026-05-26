package com.example.exoplayerdummy.di

import com.example.exoplayerdummy.player.config.MediaCacheManager
import com.example.exoplayerdummy.player.controller.ExoPlaybackController
import com.example.exoplayerdummy.player.controller.PlaybackController
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val playerModule = module {
    single { MediaCacheManager(androidContext()) }
    factory<PlaybackController> { ExoPlaybackController(androidContext(), get()) }
}

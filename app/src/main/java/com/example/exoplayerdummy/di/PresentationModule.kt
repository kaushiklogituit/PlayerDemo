package com.example.exoplayerdummy.di

import com.example.exoplayerdummy.presentation.catalog.CatalogViewModel
import com.example.exoplayerdummy.presentation.player.PlaybackViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val presentationModule = module {
    viewModel { CatalogViewModel(get()) }
    viewModel { PlaybackViewModel(androidApplication(), get()) }
}

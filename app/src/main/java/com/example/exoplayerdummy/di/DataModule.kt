package com.example.exoplayerdummy.di

import com.example.exoplayerdummy.data.repository.VideoCatalogRepositoryImpl
import com.example.exoplayerdummy.data.source.LocalVideoCatalogDataSource
import com.example.exoplayerdummy.data.source.VideoCatalogDataSource
import com.example.exoplayerdummy.domain.repository.VideoCatalogRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val dataModule = module {
    singleOf(::LocalVideoCatalogDataSource) { bind<VideoCatalogDataSource>() }
    singleOf(::VideoCatalogRepositoryImpl) { bind<VideoCatalogRepository>() }
}

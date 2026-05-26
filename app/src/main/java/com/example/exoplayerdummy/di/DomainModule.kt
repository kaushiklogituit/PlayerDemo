package com.example.exoplayerdummy.di

import com.example.exoplayerdummy.domain.usecase.FindVideoByIdUseCase
import com.example.exoplayerdummy.domain.usecase.GetVideoCatalogUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val domainModule = module {
    factoryOf(::GetVideoCatalogUseCase)
    factoryOf(::FindVideoByIdUseCase)
}

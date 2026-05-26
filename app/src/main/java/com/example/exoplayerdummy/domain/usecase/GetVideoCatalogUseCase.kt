package com.example.exoplayerdummy.domain.usecase

import com.example.exoplayerdummy.domain.model.Video
import com.example.exoplayerdummy.domain.repository.VideoCatalogRepository

class GetVideoCatalogUseCase(private val repository: VideoCatalogRepository) {
    operator fun invoke(): List<Video> = repository.getCatalog()
}

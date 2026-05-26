package com.example.exoplayerdummy.domain.usecase

import com.example.exoplayerdummy.domain.model.Video
import com.example.exoplayerdummy.domain.repository.VideoCatalogRepository

class FindVideoByIdUseCase(private val repository: VideoCatalogRepository) {
    operator fun invoke(id: String): Video? = repository.findById(id)
}

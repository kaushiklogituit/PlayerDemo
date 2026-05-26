package com.example.exoplayerdummy.domain.repository

import com.example.exoplayerdummy.domain.model.Video

interface VideoCatalogRepository {
    fun getCatalog(): List<Video>
    fun findById(id: String): Video?
}

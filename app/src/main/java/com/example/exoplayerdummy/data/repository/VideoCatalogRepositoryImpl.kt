package com.example.exoplayerdummy.data.repository

import android.util.Log
import com.example.exoplayerdummy.data.source.VideoCatalogDataSource
import com.example.exoplayerdummy.domain.model.Video
import com.example.exoplayerdummy.domain.repository.VideoCatalogRepository

class VideoCatalogRepositoryImpl(
    private val dataSource: VideoCatalogDataSource
) : VideoCatalogRepository {

    companion object {
        private const val TAG = "VideoCatalogRepository"
    }

    override fun getCatalog(): List<Video> {
        Log.d(TAG, "getCatalog()")
        return dataSource.loadCatalog()
    }

    override fun findById(id: String): Video? {
        Log.d(TAG, "findById(id=$id)")
        return getCatalog().find { it.id == id }.also { video ->
            if (video != null) Log.d(TAG, "Found: ${video.title}")
            else Log.w(TAG, "No video found for id=$id")
        }
    }
}

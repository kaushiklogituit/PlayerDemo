package com.example.exoplayerdummy.player.config

import android.content.Context
import android.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

class MediaCacheManager(context: Context) {

    companion object {
        private const val TAG = "MediaCacheManager"
        private const val CACHE_DIR_NAME = "stream_media_cache"
        private const val MAX_CACHE_BYTES = 100L * 1024 * 1024
        private const val USER_AGENT = "StreamPlayer/1.0 (Android)"
    }

    private val appContext = context.applicationContext

    @Volatile
    private var cache: SimpleCache? = null

    private fun resolveCache(): SimpleCache {
        if (cache == null) {
            synchronized(this) {
                if (cache == null) {
                    val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME)
                    val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
                    val dbProvider = StandaloneDatabaseProvider(appContext)
                    cache = SimpleCache(cacheDir, evictor, dbProvider)
                    Log.i(TAG, "Cache initialised at ${cacheDir.absolutePath} (${MAX_CACHE_BYTES / (1024 * 1024)}MB)")
                }
            }
        }
        return cache!!
    }

    fun buildDataSourceFactory(): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(USER_AGENT)

        return CacheDataSource.Factory()
            .setCache(resolveCache())
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun release() {
        Log.d(TAG, "Releasing media cache")
        cache?.release()
        cache = null
    }
}

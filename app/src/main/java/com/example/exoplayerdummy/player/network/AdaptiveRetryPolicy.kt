package com.example.exoplayerdummy.player.network

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

@UnstableApi
class AdaptiveRetryPolicy : LoadErrorHandlingPolicy {

    companion object {
        private const val TAG = "AdaptiveRetryPolicy"
        private const val MAX_ATTEMPTS = 5
        private const val BASE_DELAY_MS = 1_000L
        private const val MAX_DELAY_MS = 16_000L
        private const val LOCATION_EXCLUSION_MS = 5_000L
        private const val TRACK_EXCLUSION_MS = 60_000L
    }

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val error = loadErrorInfo.exception
        val attempt = loadErrorInfo.errorCount
        val dataType = loadErrorInfo.mediaLoadData.dataType

        Log.w(TAG, "Load error #$attempt [${dataTypeName(dataType)}]: ${error.message}")

        if (error is HttpDataSource.HttpDataSourceException) {
            val statusCode = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: -1
            if (statusCode in 400..499 && statusCode != 408 && statusCode != 429) {
                Log.e(TAG, "HTTP $statusCode — client error, not retrying")
                return C.TIME_UNSET
            }
            if (statusCode == 429) {
                val delay = minOf(BASE_DELAY_MS * 4 * (1L shl (attempt - 1)), MAX_DELAY_MS)
                Log.w(TAG, "Rate limited (429) — retrying in ${delay}ms")
                return delay
            }
        }

        if (attempt >= MAX_ATTEMPTS) {
            Log.e(TAG, "Max attempts ($MAX_ATTEMPTS) reached — giving up")
            return C.TIME_UNSET
        }

        if (dataType == C.DATA_TYPE_AD && attempt >= 1) {
            Log.w(TAG, "Ad load failed — skipping ad gracefully")
            return C.TIME_UNSET
        }

        val delay = minOf(BASE_DELAY_MS * (1L shl (attempt - 1)), MAX_DELAY_MS)
        Log.i(TAG, "Retry in ${delay}ms (attempt ${attempt + 1}/$MAX_ATTEMPTS)")
        return delay
    }

    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
    ): LoadErrorHandlingPolicy.FallbackSelection? {
        if (fallbackOptions.isFallbackAvailable(LoadErrorHandlingPolicy.FALLBACK_TYPE_LOCATION)) {
            Log.d(TAG, "Falling back to alternative CDN location")
            return LoadErrorHandlingPolicy.FallbackSelection(
                LoadErrorHandlingPolicy.FALLBACK_TYPE_LOCATION,
                LOCATION_EXCLUSION_MS
            )
        }
        if (fallbackOptions.isFallbackAvailable(LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK)) {
            Log.d(TAG, "Falling back to lower quality track")
            return LoadErrorHandlingPolicy.FallbackSelection(
                LoadErrorHandlingPolicy.FALLBACK_TYPE_TRACK,
                TRACK_EXCLUSION_MS
            )
        }
        return null
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = when (dataType) {
        C.DATA_TYPE_MANIFEST -> 5
        C.DATA_TYPE_MEDIA -> 3
        C.DATA_TYPE_DRM -> 3
        C.DATA_TYPE_AD -> 1
        else -> 3
    }

    private fun dataTypeName(dataType: Int): String = when (dataType) {
        C.DATA_TYPE_MEDIA -> "MEDIA_SEGMENT"
        C.DATA_TYPE_MANIFEST -> "MANIFEST"
        C.DATA_TYPE_DRM -> "DRM_LICENSE"
        C.DATA_TYPE_AD -> "AD"
        else -> "OTHER($dataType)"
    }
}

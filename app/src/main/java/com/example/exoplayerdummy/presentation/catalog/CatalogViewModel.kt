package com.example.exoplayerdummy.presentation.catalog

import com.example.exoplayerdummy.AppLogger as Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exoplayerdummy.domain.usecase.GetVideoCatalogUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CatalogViewModel(
    private val getVideoCatalog: GetVideoCatalogUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "CatalogViewModel"
    }

    private val _state = MutableStateFlow(CatalogContract.State())
    val state = _state.asStateFlow()

    private val _events = Channel<CatalogContract.Event>()
    val events = _events.receiveAsFlow()

    init {
        Log.d(TAG, "Initialising catalog screen state")
        loadCatalog()
    }

    fun onAction(action: CatalogContract.Action) {
        when (action) {
            is CatalogContract.Action.OnVideoSelected -> {
                Log.d(TAG, "Video selected: id=${action.video.id}, title=${action.video.title}, protocol=${action.video.protocol}, live=${action.video.isLiveStream}")
                viewModelScope.launch {
                    _events.send(CatalogContract.Event.NavigateToPlayer(action.video.id))
                }
            }
            CatalogContract.Action.OnRetry -> {
                Log.i(TAG, "Retry requested")
                loadCatalog()
            }
        }
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            Log.d(TAG, "Loading catalog")
            _state.update { it.copy(isLoading = true) }
            val videos = getVideoCatalog()
            _state.update { it.copy(videos = videos, isLoading = false) }
            Log.d(TAG, "Catalog loaded: total=${videos.size}, live=${videos.count { it.isLiveStream }}, drm=${videos.count { it.protection != null }}")
        }
    }
}

package com.example.exoplayerdummy.presentation.catalog

import android.util.Log
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
        loadCatalog()
    }

    fun onAction(action: CatalogContract.Action) {
        when (action) {
            is CatalogContract.Action.OnVideoSelected -> {
                Log.d(TAG, "Video selected: ${action.video.title}")
                viewModelScope.launch {
                    _events.send(CatalogContract.Event.NavigateToPlayer(action.video.id))
                }
            }
            CatalogContract.Action.OnRetry -> loadCatalog()
        }
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val videos = getVideoCatalog()
            _state.update { it.copy(videos = videos, isLoading = false) }
            Log.d(TAG, "Catalog loaded: ${videos.size} items")
        }
    }
}

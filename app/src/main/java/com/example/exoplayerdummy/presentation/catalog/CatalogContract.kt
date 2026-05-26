package com.example.exoplayerdummy.presentation.catalog

import com.example.exoplayerdummy.domain.model.Video

object CatalogContract {

    data class State(
        val videos: List<Video> = emptyList(),
        val isLoading: Boolean = false
    )

    sealed interface Action {
        data class OnVideoSelected(val video: Video) : Action
        data object OnRetry : Action
    }

    sealed interface Event {
        data class NavigateToPlayer(val videoId: String) : Event
    }
}

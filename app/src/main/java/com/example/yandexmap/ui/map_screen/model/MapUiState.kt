package com.example.yandexmap.ui.map_screen.model

import com.example.yandexmap.ui.model.ErrorType
import com.example.yandexmap.ui.model.SearchResponseItem
import com.example.yandexmap.ui.model.SuggestHolderItem
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Point

sealed interface MapUiState {

    data object Unknown : MapUiState

    data object Loading : MapUiState

    data object CloseSearch : MapUiState

    data class ShowError(val errorType: ErrorType) : MapUiState

    data class SearchResulClick(
        val point: Point
    ) : MapUiState

    data class SearchSuccess(
        val items: List<SearchResponseItem>,
        val zoomToItems: Boolean,
        val itemsBoundingBox: BoundingBox,
    ) : MapUiState

    data class SuggestSuccess(
        val items: List<SuggestHolderItem>
    ) : MapUiState

    data class RoutesUpdated(
        val drivingRoutes: List<DrivingRoute>
    ) : MapUiState

}


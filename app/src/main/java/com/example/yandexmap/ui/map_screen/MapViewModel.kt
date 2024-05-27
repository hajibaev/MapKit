package com.example.yandexmap.ui.map_screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yandexmap.ui.EMPTY_STRING
import com.example.yandexmap.ui.map_screen.model.MapUiState
import com.example.yandexmap.ui.map_screen.model.MapUiState.Loading
import com.example.yandexmap.ui.map_screen.model.MapUiState.RoutesUpdated
import com.example.yandexmap.ui.map_screen.model.MapUiState.SearchResulClick
import com.example.yandexmap.ui.map_screen.model.MapUiState.SearchSuccess
import com.example.yandexmap.ui.map_screen.model.MapUiState.SuggestSuccess
import com.example.yandexmap.ui.map_screen.model.MapUiState.Unknown
import com.example.yandexmap.ui.model.ErrorType
import com.example.yandexmap.ui.model.SearchResponseItem
import com.example.yandexmap.ui.model.SuggestHolderItem
import com.example.yandexmap.ui.toBoundingBox
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.driving.DrivingOptions
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.directions.driving.DrivingSession
import com.yandex.mapkit.directions.driving.VehicleOptions
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.VisibleRegion
import com.yandex.mapkit.search.Response
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchManagerType.COMBINED
import com.yandex.mapkit.search.SearchOptions
import com.yandex.mapkit.search.Session
import com.yandex.mapkit.search.SuggestItem
import com.yandex.mapkit.search.SuggestOptions
import com.yandex.mapkit.search.SuggestResponse
import com.yandex.mapkit.search.SuggestSession
import com.yandex.mapkit.search.SuggestType
import com.yandex.runtime.Error
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.yandex.mapkit.directions.driving.DrivingRouterType.COMBINED as D_COMBINED

private const val REGION_DEBOUNCE = 200L
private const val MAX_ROUTE_SIZE = 3
private const val YOUR_LOCATION = "Ваше местоположение"

@OptIn(FlowPreview::class)
class MapViewModel : ViewModel() {

    private val searchManager = SearchFactory.getInstance().createSearchManager(COMBINED)
    private var searchSession: Session? = null
    private val drivingRouter = DirectionsFactory.getInstance().createDrivingRouter(D_COMBINED)
    private var drivingSession: DrivingSession? = null
    private var zoomToSearchResult = false

    private var isStartPosition: Boolean = false
    var startLocationTitle: String = EMPTY_STRING
    var endLocationTitle: String = EMPTY_STRING

    private val region = MutableStateFlow<VisibleRegion?>(null)
    private val query = MutableStateFlow(EMPTY_STRING)
    private val routePoints: MutableStateFlow<List<Point>> = MutableStateFlow(emptyList())

    var userLocation: Point? = null

    private val _uiAction: MutableStateFlow<MapUiState> = MutableStateFlow(Unknown)
    val uiAction: StateFlow<MapUiState> get() = _uiAction.asStateFlow()

    init {
        routePoints
            .onEach(::handleRoutePoints)
            .launchIn(viewModelScope)

        /**
         * Resubmitting suggests when query, searchState changes.
         */
        query
            .debounce(REGION_DEBOUNCE)
            .onEach { query ->
                if (query.isNotEmpty() && region.value != null) {
                    submitSuggest(query, region.value!!.toBoundingBox())
                } else resetSuggest()
            }
            .launchIn(viewModelScope)
    }

    private fun submitUriSearch(uri: String) {
        searchSession?.cancel()
        searchSession = searchManager.searchByURI(
            uri,
            SearchOptions(),
            searchSessionListener
        )
        _uiAction.tryEmit(Loading)
        zoomToSearchResult = true
    }

    private val searchSessionListener = object : Session.SearchListener {
        override fun onSearchResponse(response: Response) {
            val items = response.collection.children.mapNotNull {
                val point = it.obj?.geometry?.firstOrNull()?.point ?: return@mapNotNull null
                SearchResponseItem(point, it.obj)
            }
            val boundingBox = response.metadata.boundingBox ?: return
            _uiAction.tryEmit(
                SearchSuccess(items, zoomToSearchResult, boundingBox)
            )
        }

        override fun onSearchError(p0: Error) {
            _uiAction.tryEmit(MapUiState.ShowError(ErrorType.SEARCH))
        }
    }

    private val suggestSessionListener = object : SuggestSession.SuggestListener {
        override fun onResponse(responce: SuggestResponse) {
            responce.items.map { it.center?.latitude }
            val suggestItems = responce.items.take(SUGGEST_NUMBER_LIMIT)
                .map {
                    SuggestHolderItem(
                        title = it.title,
                        subtitle = it.subtitle,
                    ) {
                        it.center?.let { point ->
                            if (isStartPosition) startLocationTitle = it.title.text
                            else endLocationTitle = it.title.text
                            _uiAction.tryEmit(
                                SearchResulClick(point = point)
                            )
                        }
                        if (it.action == SuggestItem.Action.SEARCH && it.uri != null) {
                            submitUriSearch(it.uri!!)
                        }
                    }
                }
            _uiAction.tryEmit(SuggestSuccess(suggestItems))

        }

        override fun onError(error: Error) {
            _uiAction.tryEmit(MapUiState.ShowError(ErrorType.SUGGEST))
        }
    }

    private fun submitSuggest(
        query: String,
        box: BoundingBox,
        options: SuggestOptions = SUGGEST_OPTIONS,
    ) {
        searchManager.createSuggestSession().suggest(query, box, options, suggestSessionListener)
        _uiAction.tryEmit(Loading)
    }

    private val drivingRouteListener = object : DrivingSession.DrivingRouteListener {
        override fun onDrivingRoutes(drivingRoutes: MutableList<DrivingRoute>) {
            _uiAction.tryEmit(RoutesUpdated(drivingRoutes))
        }

        override fun onDrivingRoutesError(error: Error) {
            _uiAction.tryEmit(MapUiState.ShowError(ErrorType.DRIVING))
        }
    }

    private fun handleRoutePoints(routePoints: List<Point>) {
        updateSearchQuery(EMPTY_STRING, isStartPosition)
        
        if (routePoints.isEmpty()) drivingSession?.cancel()

        if (routePoints.size < 2) return

        val requestPoints = buildList {
            add(RequestPoint(routePoints.first(), RequestPointType.WAYPOINT, null, null))
            addAll(
                routePoints.subList(1, routePoints.size - 1).map {
                    RequestPoint(it, RequestPointType.VIAPOINT, null, null)
                }
            )
            add(RequestPoint(routePoints.last(), RequestPointType.WAYPOINT, null, null))
        }

        drivingSession = drivingRouter.requestRoutes(
            requestPoints,
            DrivingOptions(),
            VehicleOptions(),
            drivingRouteListener,
        )
    }

    fun updateSearchQuery(value: String, isStartPosition: Boolean) {
        query.tryEmit(value)
        this.isStartPosition = isStartPosition
    }

    fun setVisibleRegion(region: VisibleRegion) = this.region.tryEmit(region)

    fun setRoutePoints(point: Point, isMyLocation: Boolean = false) {
        val currentPoints = routePoints.value.toMutableList()

        if (isMyLocation && isStartPosition && currentPoints.size == 1) currentPoints.removeAt(0)

        if (isStartPosition && currentPoints.isNotEmpty()) currentPoints.add(1, point)
        else currentPoints.add(point)

        if (currentPoints.size >= MAX_ROUTE_SIZE) {
            if (!isStartPosition) currentPoints.removeAt(1)
            else currentPoints.removeAt(0)
        }

        routePoints.tryEmit(currentPoints)
    }

    fun doMyLocationClick() {
        userLocation?.let { location ->
            setRoutePoints(location, isMyLocation = true)
            isStartPosition = true
            startLocationTitle = YOUR_LOCATION
            _uiAction.tryEmit(SearchResulClick(location))
        }
    }

    private fun resetSuggest() {
        searchManager.createSuggestSession().reset()
    }

    fun reset() {
        searchSession?.cancel()
        searchSession = null
        searchManager.createSuggestSession().reset()
    }

    companion object {
        private const val SUGGEST_NUMBER_LIMIT = 20
        private val SUGGEST_OPTIONS = SuggestOptions().setSuggestTypes(
            SuggestType.GEO.value or SuggestType.BIZ.value or SuggestType.TRANSIT.value
        )
    }
}
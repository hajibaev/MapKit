package com.example.yandexmap.ui.map_screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Bundle
import android.view.WindowManager
import android.widget.SearchView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.yandexmap.R
import com.example.yandexmap.databinding.ActivityMainBinding
import com.example.yandexmap.ui.EMPTY_STRING
import com.example.yandexmap.ui.adapter.SuggestsListAdapter
import com.example.yandexmap.ui.hideKeyboard
import com.example.yandexmap.ui.map_screen.model.MapUiState
import com.example.yandexmap.ui.map_screen.model.MapUiState.CloseSearch
import com.example.yandexmap.ui.map_screen.model.MapUiState.Loading
import com.example.yandexmap.ui.map_screen.model.MapUiState.RoutesUpdated
import com.example.yandexmap.ui.map_screen.model.MapUiState.SearchResulClick
import com.example.yandexmap.ui.map_screen.model.MapUiState.SearchSuccess
import com.example.yandexmap.ui.map_screen.model.MapUiState.ShowError
import com.example.yandexmap.ui.map_screen.model.MapUiState.SuggestSuccess
import com.example.yandexmap.ui.model.ErrorType
import com.example.yandexmap.ui.model.SearchResponseItem
import com.example.yandexmap.ui.showToast
import com.example.yandexmap.ui.styleAlternativeRoute
import com.example.yandexmap.ui.styleMainRoute
import com.google.android.gms.location.LocationServices
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.ScreenPoint
import com.yandex.mapkit.ScreenRect
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.SizeChangedListener
import com.yandex.runtime.image.ImageProvider
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainActivity : AppCompatActivity() {

    private val binding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val viewModel: MapViewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(this)[MapViewModel::class.java]
    }

    private val map by lazy(LazyThreadSafetyMode.NONE) {
        binding.mapview.mapWindow.map
    }

    private val fusedLocationClient by lazy(LazyThreadSafetyMode.NONE) {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val suggestAdapter = SuggestsListAdapter()

    private val inputListener = object : InputListener {
        override fun onMapTap(map: Map, point: Point) {
            viewModel.setRoutePoints(point)
        }

        override fun onMapLongTap(map: Map, point: Point) {
            viewModel.setRoutePoints(point)
        }
    }

    private val routesCollection by lazy(LazyThreadSafetyMode.NONE) {
        map.mapObjects.addCollection()
    }

    private val cameraListener = CameraListener { _, _, reason, _ ->
        // Updating current visible region to apply research on map moved by user gestures.
        if (reason == CameraUpdateReason.GESTURES) {
            viewModel.setVisibleRegion(map.visibleRegion)
        }
    }

    private val sizeChangedListener = SizeChangedListener { _, _, _ -> updateFocusRect() }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) getLastKnownLocation()
        else binding.root.showToast(getString(R.string.lack_of_permission))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapKitFactory.initialize(this)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(binding.root)
        checkLocationPermission()
        setUpClickers()
        setUpViews()
        setUpDataListeners()
        viewModel.setVisibleRegion(map.visibleRegion)
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> getLastKnownLocation()

            else -> requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) setUpMap(Point(location.latitude, location.longitude))
                else {
                    setUpMap(DEFAULT_LOCATION)
                    binding.root.showToast(getString(R.string.location_unavailable))
                }
            }
            .addOnFailureListener { binding.root.showToast(it.message.toString()) }
    }

    private fun setUpMap(point: Point) = with(map) {
        addInputListener(inputListener)
        addCameraListener(cameraListener)
        move(CameraPosition(point, DEFAULT_ZOOM, 0f, 0f))
        viewModel.userLocation = point
    }

    private fun setUpViews() = with(binding) {
        setUpQuerySearch()
        mapview.mapWindow.addSizeChangedListener(sizeChangedListener)
        listSuggests.adapter = suggestAdapter
        updateFocusRect()
    }

    private fun setUpClickers() = with(binding) {
        startPositionSearch.setOnCloseListener {
            viewModel.reset()
            true
        }
        endPositionSearch.setOnCloseListener {
            viewModel.reset()
            true
        }
        icMyLocation.setOnClickListener {
            viewModel.doMyLocationClick()
        }
    }

    private fun setUpDataListeners() = with(viewModel) {
        uiAction
            .flowWithLifecycle(lifecycle)
            .onEach(::setUpUiState)
            .launchIn(lifecycleScope)
    }

    private fun setUpSearchView() = with(binding) {
        startPositionSearch.setOnQueryTextListener(setUpTextListener(isStartPosition = true))
        endPositionSearch.setOnQueryTextListener(setUpTextListener(isStartPosition = false))
    }

    private fun setUpTextListener(isStartPosition: Boolean): SearchView.OnQueryTextListener {
        return object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(searchString: String?): Boolean {
                if (searchString != null) viewModel.updateSearchQuery(searchString, isStartPosition)
                return false
            }

            override fun onQueryTextChange(searchString: String?): Boolean {
                if (searchString != null) viewModel.updateSearchQuery(searchString, isStartPosition)
                return false
            }
        }
    }

    private fun setUpUiState(mapUiState: MapUiState) = with(mapUiState) {
        binding.progress.isVisible = mapUiState is Loading
        when (val state = this) {
            is ShowError -> setUpError(state.errorType)
            is SearchResulClick -> setUpSearchResulClick(state.point)
            is CloseSearch -> setUpEmptySuggest()
            is SearchSuccess -> setUpSearchResult(state)
            is RoutesUpdated -> onRoutesUpdated(state.drivingRoutes)
            is SuggestSuccess -> {
                binding.listSuggests.isVisible = state.items.isNotEmpty()
                suggestAdapter.submitList(state.items)
            }

            else -> Unit
        }
    }

    private fun setUpError(errorType: ErrorType) = with(binding) {
        if (errorType == ErrorType.SUGGEST) root.showToast(getString(R.string.search_error))
        else root.showToast(getString(R.string.suggest_error))
    }

    private fun setUpSearchResult(state: SearchSuccess): Unit = with(state) {
        updateSearchResponsePlaceMarks(items)
        if (zoomToItems) {
            focusCamera(
                items.map { item -> item.point },
                itemsBoundingBox
            )
        }
    }

    private fun setUpEmptySuggest() {
        hideKeyboard(binding.root)
        binding.listSuggests.isVisible = false
        suggestAdapter.submitList(listOf())
    }

    private fun setUpSearchResulClick(point: Point) {
        binding.apply {
            setUpEmptySuggest()
            viewModel.setRoutePoints(point)
            setUpQuerySearch()
        }
    }

    private fun setUpQuerySearch() = with(binding) {
        if (viewModel.startLocationTitle != EMPTY_STRING || viewModel.endLocationTitle != EMPTY_STRING) {
            startPositionSearch.setOnQueryTextListener(null)
            endPositionSearch.setOnQueryTextListener(null)
            startPositionSearch.setQuery(viewModel.startLocationTitle, true)
            endPositionSearch.setQuery(viewModel.endLocationTitle, true)
        }
        setUpSearchView()
    }

    private fun updateSearchResponsePlaceMarks(items: List<SearchResponseItem>) {
        val imageProvider = ImageProvider.fromResource(this, R.drawable.ic_location)

        items.forEach {
            map.mapObjects.addPlacemark().apply {
                geometry = it.point
                setIcon(imageProvider, IconStyle().apply {
                    anchor = PointF(0.5f, 1.0f)
                    scale = 0.6f
                }
                )
                addTapListener { _, _ -> true }
                userData = it.geoObject
            }
        }
    }

    private fun focusCamera(points: List<Point>, boundingBox: BoundingBox) {
        if (points.isEmpty()) return

        val position = if (points.size == 1) {
            map.cameraPosition.run {
                CameraPosition(points.first(), zoom, azimuth, tilt)
            }
        } else map.cameraPosition(Geometry.fromBoundingBox(boundingBox))

        map.move(position, Animation(Animation.Type.SMOOTH, 0.5f), null)
    }

    private fun updateFocusRect() {
        val horizontal = resources.getDimension(R.dimen.window_horizontal_padding)
        val vertical = resources.getDimension(R.dimen.window_vertical_padding)
        val window = binding.mapview.mapWindow

        window.focusRect = ScreenRect(
            ScreenPoint(horizontal, vertical),
            ScreenPoint(window.width() - horizontal, window.height() - vertical),
        )
    }

    private fun onRoutesUpdated(routes: List<DrivingRoute>) {
        if (routesCollection.isValid) {
            routesCollection.clear()
            routes.forEachIndexed { index, route ->
                routesCollection.addPolyline(route.geometry).apply {
                    if (index == 0) styleMainRoute(this@MainActivity)
                    else styleAlternativeRoute(this@MainActivity)
                }
            }
        } else binding.root.showToast(getString(R.string.error_routes))
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        binding.mapview.onStart()
    }

    override fun onStop() {
        binding.mapview.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    companion object {
        private const val DEFAULT_ZOOM = 13.0f
        private val DEFAULT_LOCATION = Point(55.755863189697266, 37.617698669433594)
    }
}
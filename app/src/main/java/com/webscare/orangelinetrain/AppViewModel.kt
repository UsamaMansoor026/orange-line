package com.webscare.orangelinetrain

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.webscare.orangelinetrain.common.datastore.PreferenceDataStoreKeysConstants
import com.webscare.orangelinetrain.common.datastore.PreferencesDataStoreHelper
import com.webscare.orangelinetrain.common.enums.NavigationMode
import com.webscare.orangelinetrain.common.enums.SelectionMode
import com.webscare.orangelinetrain.common.enums.SheetSource
import com.webscare.orangelinetrain.domain.model.City
import com.webscare.orangelinetrain.domain.model.Route
import com.webscare.orangelinetrain.domain.model.RouteMeta
import com.webscare.orangelinetrain.domain.model.Stop
import com.webscare.orangelinetrain.domain.repository.CityRepository
import com.webscare.orangelinetrain.domain.repository.DirectionsRepository
import com.webscare.orangelinetrain.domain.repository.RouteRepository
import com.webscare.orangelinetrain.domain.repository.StopsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val stopsRepo: StopsRepository,
    private val cityRepo: CityRepository,
    private val routeRepo: RouteRepository,
    private val directionsRepo: DirectionsRepository,
) : ViewModel() {

    // --- Navigation state persistence ---
    private val prefs by lazy { PreferencesDataStoreHelper(appContext) }
    private val navPrefs = PreferenceDataStoreKeysConstants

    /** Fragment can observe this to know whether restore succeeded/failed. */
    val navRestoreResult = MutableLiveData<Boolean?>(null)


    val sheetSource = MutableLiveData(SheetSource.HOME)

    /** Stops from Room */
    val stops: LiveData<List<Stop>> = stopsRepo.observeStops()
    val cities: LiveData<List<City>> = cityRepo.observeCities()
    val routes: LiveData<List<Route>> = routeRepo.observeRoutes()

    /** Location state */
    val userLocation = MutableLiveData<Location>()
    val selectedCity = MutableLiveData<City?>()

    /** Selection flow */
    val selectionMode = MutableLiveData(SelectionMode.TO)

    /** Selected stops */
    val toStop = MutableLiveData<Stop?>()
    val fromStop = MutableLiveData<Stop?>()

    private val _navigationMode = MutableLiveData(NavigationMode.IDLE)
    val navigationMode: LiveData<NavigationMode> = _navigationMode

    /** Picture-in-Picture state */
    val isInPipMode = MutableLiveData(false)

    /** Navigation footer: which stop user is currently viewing */
    val navStopIndex = MutableLiveData(0)

    private val routeOverviewCache = mutableMapOf<Int, List<LatLng>>()
    val allRouteTracks = MediatorLiveData<Map<Int, List<LatLng>>>()

    fun setNavStopIndex(index: Int) {
        val route = selectedRoute.value
        val max = (route?.stops?.lastIndex ?: 0).coerceAtLeast(0)
        navStopIndex.value = index.coerceIn(0, max)
    }

    fun enterRoutePreview() {
        setNavigationMode(NavigationMode.ROUTE_PREVIEW)
    }

    fun startNavigation() {
        setNavigationMode(NavigationMode.NAVIGATING)
    }

    fun setNavigationMode(mode: NavigationMode) {
//        Log.d("train", "Old: ${_navigationMode.value} -> New: $mode")
        if (_navigationMode.value == mode) return

//        Log.d("train", "Transitioning to: $mode")
        _navigationMode.value = mode
        persistNavState()
    }

    fun exitNavigation() {
        setNavigationMode(NavigationMode.IDLE)
    }

    fun setPipMode(enabled: Boolean) {
        isInPipMode.postValue(enabled)
    }
    val fullRouteStops: LiveData<List<Stop>> = routes.map { routeList ->
        if (routeList.isNullOrEmpty()) return@map emptyList()
        // Orange Line is one route — take the first route's full stop list
        routeList.first().stops
    }

    // --- Persist navigation state ---
    private fun persistNavState() {
        val mode = _navigationMode.value ?: NavigationMode.IDLE
        val routeId = selectedRoute.value?.id?.toString() ?: ""
        val fromId = fromStop.value?.id?.toString() ?: ""
        val toId = toStop.value?.id?.toString() ?: ""
        val navIndex = navStopIndex.value?.toString() ?: "0"
        val destLat = selectedRoute.value?.stops?.lastOrNull()?.latitude?.toString() ?: ""
        val destLng = selectedRoute.value?.stops?.lastOrNull()?.longitude?.toString() ?: ""

        viewModelScope.launch {
            prefs.putPreference(navPrefs.NAVIGATION_MODE, mode.name)
            prefs.putPreference(navPrefs.DEST_LAT, destLat)
            prefs.putPreference(navPrefs.DEST_LNG, destLng)
            prefs.putPreference(navPrefs.NAV_ROUTE_ID, routeId)
            prefs.putPreference(navPrefs.NAV_FROM_ID, fromId)
            prefs.putPreference(navPrefs.NAV_TO_ID, toId)
            prefs.putPreference(navPrefs.NAV_INDEX, navIndex)
        }
    }

    // --- Restore navigation state on process death or cold start ---
    fun restoreNavStateIfNeeded(routesList: List<Route>) {
        viewModelScope.launch {
            val mode = prefs.getFirstPreference(navPrefs.NAVIGATION_MODE, "IDLE")
            if (mode != "NAVIGATING") {
                navRestoreResult.postValue(false)
                return@launch
            }

            val routeId = prefs.getFirstPreference(navPrefs.NAV_ROUTE_ID, "")
            val fromId = prefs.getFirstPreference(navPrefs.NAV_FROM_ID, "")
            val toId = prefs.getFirstPreference(navPrefs.NAV_TO_ID, "")
            val navIndex = prefs.getFirstPreference(navPrefs.NAV_INDEX, "0").toIntOrNull() ?: 0

            val route = routesList.firstOrNull { it.id.toString() == routeId }
            val from = route?.stops?.firstOrNull { it.id.toString() == fromId }
            val to = route?.stops?.firstOrNull { it.id.toString() == toId }

            if (route == null || from == null || to == null) {
                // Restore requested but impossible; flip to IDLE so we don't keep trying forever.
                prefs.putPreference(navPrefs.NAVIGATION_MODE, NavigationMode.IDLE.name)
                navRestoreResult.postValue(false)
                return@launch
            }

            // Re-hydrate state used by HomeFragment observers.
            fromStop.postValue(from)
            toStop.postValue(to)
            selectedRoute.postValue(route.applyDirection(from, to))
            navStopIndex.postValue(navIndex)
            _navigationMode.postValue(NavigationMode.NAVIGATING)
            navRestoreResult.postValue(true)
        }
    }

    val openChooseStop = MutableLiveData(false)

    private val _previewStop = MutableLiveData<Stop?>()
    val previewStop: LiveData<Stop?> = _previewStop

    /** UI helpers */
    val searchHint = MutableLiveData("Where to?")

    // 🔹 Finds nearest point index on recorded track
    private fun findNearestIndex(
        route: List<LatLng>,
        target: LatLng,
        maxDistanceMeters: Float = 200f
    ): Int {

        var minDistance = Float.MAX_VALUE
        var index = -1

        route.forEachIndexed { i, point ->

            val result = FloatArray(1)

            Location.distanceBetween(
                point.latitude,
                point.longitude,
                target.latitude,
                target.longitude,
                result
            )

            if (result[0] < minDistance) {
                minDistance = result[0]
                index = i
            }
        }

        // 🚨 If nearest point is too far → treat as invalid
        return if (minDistance <= maxDistanceMeters) index else -1
    }

    val selectedRoute: MutableLiveData<Route?> = MediatorLiveData<Route?>().apply {

        fun update() {
            val from = fromStop.value
            val to = toStop.value
            val allRoutes = routes.value

            value = if (from != null && to != null && !allRoutes.isNullOrEmpty()) {
                val baseRoute = allRoutes.firstOrNull { route ->
                    val ids = route.stops.map { it.id }
                    ids.contains(from.id) && ids.contains(to.id)
                }

                baseRoute?.applyDirection(from, to)
            } else {
                null
            }
        }

        addSource(fromStop) { update() }
        addSource(toStop) { update() }
        addSource(routes) { update() }
    }

    val roadRoutePoints: LiveData<List<LatLng>> =
        selectedRoute.switchMap { route ->
            liveData {

                if (route == null || route.stops.size < 2) {
                    emit(emptyList())
                    return@liveData
                }

                val fullTrack = directionsRepo.loadRoute()

                if (fullTrack.isEmpty()) {
                    emit(emptyList())
                    return@liveData
                }

                val finalRoute = mutableListOf<LatLng>()

                val stops = route.stops.map {
                    LatLng(it.latitude, it.longitude)
                }

                for (i in 0 until stops.size - 1) {

                    val fromIdx = findNearestIndex(fullTrack, stops[i])
                    val toIdx = findNearestIndex(fullTrack, stops[i + 1])

                    if (fromIdx != -1 && toIdx != -1) {

                        val segment =
                            if (fromIdx <= toIdx) {
                                fullTrack.subList(fromIdx, toIdx + 1)
                            } else {
                                fullTrack.subList(toIdx, fromIdx + 1).asReversed()
                            }

                        finalRoute.addAll(segment)
                    }
                }

                // 🔥 Only LIGHT clean (remove jitter)
                emit(smoothAndCleanRoute(finalRoute, 6f))
            }
        }

    fun loadAllRouteTracks() {

        val allRoutes = routes.value
        if (allRoutes == null) return

        viewModelScope.launch {

            routeOverviewCache.clear()

            val fullTrack = directionsRepo.loadRoute()
            if (fullTrack.isEmpty()) return@launch

            val result = mutableMapOf<Int, List<LatLng>>()

            allRoutes.forEach { route ->

                val stopPoints = route.stops.map {
                    LatLng(it.latitude, it.longitude)
                }

                if (stopPoints.size < 2) return@forEach

                val finalRoute = mutableListOf<LatLng>()

                for (i in 0 until stopPoints.size - 1) {

                    val fromIdx = findNearestIndex(fullTrack, stopPoints[i])
                    val toIdx = findNearestIndex(fullTrack, stopPoints[i + 1])

                    if (fromIdx != -1 && toIdx != -1) {

                        val segment =
                            if (fromIdx <= toIdx) {
                                fullTrack.subList(fromIdx, toIdx + 1)
                            } else {
                                fullTrack.subList(toIdx, fromIdx + 1).asReversed()
                            }

                        finalRoute.addAll(segment)
                    }
                }

                val cleanRoute = smoothAndCleanRoute(finalRoute, 6f)

                routeOverviewCache[route.id] = cleanRoute
                result[route.id] = cleanRoute
            }

            allRouteTracks.postValue(result)
        }
    }

    val filteredStops: LiveData<List<Stop>> = MediatorLiveData<List<Stop>>().apply {

        fun update() {
            val city = selectedCity.value
            val allStops = stops.value

            value = if (city != null && allStops != null) {
                allStops.filter { it.city_id == city.id }
            } else {
                emptyList()
            }
        }

        addSource(stops) { update() }
        addSource(selectedCity) { update() }
    }

    init {
        syncData()
        cities.observeForever { cityList ->
            if (!cityList.isNullOrEmpty()) {
                val lahore = cityList.firstOrNull {
                    it.name.equals("Lahore", ignoreCase = true)
                }
                selectedCity.value = lahore
            }
        }

        userLocation.observeForever { loc ->
            if (loc != null && !cities.value.isNullOrEmpty()) {
//                resolveCityFromLocation()
            }
        }
        routes.observeForever {
            loadAllRouteTracks()
        }
    }

    // ✅ active route = selectedRoute if available, otherwise first route from routes
    val activeRoute: LiveData<Route?> = MediatorLiveData<Route?>().apply {

        fun update() {
            val sel = selectedRoute.value
            val list = routes.value

            value = when {
                sel != null -> sel
                !list.isNullOrEmpty() -> list.first()
                else -> null
            }
        }

        addSource(selectedRoute) { update() }
        addSource(routes) { update() }
    }

    // Stops shown in Route screen timeline
    val routeStopsForTimeline: LiveData<List<Stop>> =
        activeRoute.map { route -> route?.stops ?: emptyList() }

    val routeMeta: LiveData<RouteMeta?> = routes.map { routeList ->
        val route = routeList?.firstOrNull() ?: return@map null
        RouteMeta(
            start = route.stops.firstOrNull()?.name.orEmpty(),
            end = route.stops.lastOrNull()?.name.orEmpty(),
            stopCount = route.stops.size,
            distance = route.ride_distance.toDouble(),
            duration = route.total_ride_time
        )
    }

    fun clearRoute() {
        selectedRoute.value = null
        routeReady.value = false
        fromStop.value = null
        toStop.value = null
        navStopIndex.value = 0
        selectionMode.value = SelectionMode.TO
        searchHint.value = "Where to?"
        openChooseStop.value = false
    }

    override fun onCleared() {
        super.onCleared()
        cities.removeObserver { }
        userLocation.removeObserver { }
    }

    fun resolveCityFromLocation() {
        val loc = userLocation.value ?: return
        val cityList = cities.value ?: return
        if (cityList.isEmpty()) return

        val geocoder = android.location.Geocoder(appContext, java.util.Locale.getDefault())

        try {
            val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val detectedCityName = addresses[0].locality ?: addresses[0].subAdminArea ?: return

                val matchedCity = cityList.firstOrNull { city ->
                    city.name.equals(detectedCityName, ignoreCase = true)
                }

                selectedCity.value = matchedCity

            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun syncData() {
        viewModelScope.launch {
            stopsRepo.syncStops()
            cityRepo.syncCities()
            routeRepo.syncRoutes()
        }
    }

    val routeReady = MediatorLiveData<Boolean>().apply {
        fun update() {
            value = toStop.value != null && fromStop.value != null
        }

        addSource(toStop) { update() }
        addSource(fromStop) { update() }
    }

    fun setSelectionMode(mode: SelectionMode) {
        selectionMode.value = mode
    }

    fun selectStop(stop: Stop) {
        when (selectionMode.value) {
            SelectionMode.TO -> {
                toStop.value = stop
                selectionMode.value = SelectionMode.FROM
                searchHint.value = "Where from?"
            }

            SelectionMode.FROM -> {
                fromStop.value = stop
            }

            else -> Unit
        }
    }

    private fun smoothAndCleanRoute(
        points: List<LatLng>, minDistanceMeters: Float = 8f
    ): List<LatLng> {
        if (points.size < 2) return points

        val clean = mutableListOf(points.first())
        var last = points.first()

        for (i in 1 until points.size) {
            val cur = points[i]
            val dist = FloatArray(1)
            android.location.Location.distanceBetween(
                last.latitude, last.longitude, cur.latitude, cur.longitude, dist
            )

            // Remove tiny jerks
            if (dist[0] >= minDistanceMeters) {
                clean.add(cur)
                last = cur
            }
        }
        return clean
    }

    fun selectNearestStop() {
        val loc = userLocation.value ?: return
        val allStops = stops.value ?: return

        val nearest = allStops.minByOrNull { stop ->
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                loc.latitude, loc.longitude, stop.latitude, stop.longitude, results
            )
            results[0]
        }

        nearest?.let { selectStop(it) }
    }

    private fun Route.applyDirection(
        from: Stop, to: Stop
    ): Route {
        val fromIndex = stops.indexOfFirst { it.id == from.id }
        val toIndex = stops.indexOfFirst { it.id == to.id }

        if (fromIndex == -1 || toIndex == -1) return this

        return if (fromIndex <= toIndex) {
            // ✅ correct direction
            this.copy(
                stops = stops.subList(fromIndex, toIndex + 1), start = from.name, end = to.name
            )
        } else {
            // 🔁 reverse direction
            this.copy(
                stops = stops.subList(toIndex, fromIndex + 1).reversed(),
                start = from.name,
                end = to.name
            )
        }
    }

    fun willFromBeSameAsTo(): Boolean {
        return selectionMode.value == SelectionMode.FROM && toStop.value != null
    }
}

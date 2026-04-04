package com.webscare.orangelinetrain.ui.departure

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.webscare.orangelinetrain.AppViewModel
import com.webscare.orangelinetrain.R
import com.webscare.orangelinetrain.common.MapUtils
import com.webscare.orangelinetrain.common.Utils.addPressEffect
import com.webscare.orangelinetrain.common.datastore.PreferenceDataStoreKeysConstants
import com.webscare.orangelinetrain.common.datastore.PreferencesDataStoreHelper
import com.webscare.orangelinetrain.common.enums.SelectionMode
import com.webscare.orangelinetrain.common.enums.SheetSource
import com.webscare.orangelinetrain.databinding.FragmentDeparturesBinding
import com.webscare.orangelinetrain.domain.model.Departure
import com.webscare.orangelinetrain.domain.model.Route
import com.webscare.orangelinetrain.domain.model.Stop
import com.webscare.orangelinetrain.ui.home.DeparturesBottomSheet
import com.webscare.orangelinetrain.ui.home.StopsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.LocalTime

@AndroidEntryPoint
class DeparturesFragment : Fragment() {

    private var _binding: FragmentDeparturesBinding? = null
    private val binding get() = _binding!!
    private lateinit var googleMap: GoogleMap
    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>
    private var routePolyline: Polyline? = null
    private val routeMarkers = mutableListOf<Marker>()
    private var isMapReady = false
    private val appViewModel: AppViewModel by activityViewModels()
    private lateinit var sheetBehavior: BottomSheetBehavior<FrameLayout>
    private lateinit var stopsAdapter: StopsAdapter
    private var allStops: List<Stop> = emptyList()
    private var ignoreFocusChanges = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeparturesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) enableLocation()
                else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        appViewModel.syncData()
        locationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        sheetBehavior = BottomSheetBehavior.from(binding.stopSelectionSheet)
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        appViewModel.sheetSource.value = SheetSource.DEPARTURES
        setupBottomSheet()
        setupMap(savedInstanceState)
    }

    private fun setupBottomSheet() {
        val sheetView = binding.root.findViewById<FrameLayout>(R.id.stopSelectionSheet)
        sheetBehavior = BottomSheetBehavior.from(sheetView)

        sheetBehavior.isFitToContents = false
        sheetBehavior.halfExpandedRatio = 0.7f

        sheetBehavior.isHideable = true
        sheetBehavior.skipCollapsed = true

        sheetBehavior.isHideable = false
        sheetBehavior.skipCollapsed = false
        val peekHeightDp = 140

        sheetBehavior.peekHeight = peekHeightDp.dp
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        binding.dimOverlay.setOnClickListener {
            appViewModel.clearRoute()
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        binding.sheetContent.recyclerView.isNestedScrollingEnabled = true

        sheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {

            override fun onStateChanged(bottomSheet: View, newState: Int) {

                when (newState) {

                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        binding.sheetContent.recyclerView.isVisible = false
                        binding.dimOverlay.isVisible = false
                        clearSheetFocus()
                    }

                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                        binding.sheetContent.recyclerView.isVisible = true
                        binding.dimOverlay.isVisible = true
                    }

                    BottomSheetBehavior.STATE_EXPANDED -> {
                        binding.sheetContent.recyclerView.isVisible = true
                        binding.dimOverlay.isVisible = true
                    }

                    BottomSheetBehavior.STATE_HIDDEN -> {
                        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                        clearSheetFocus()
                    }

                    BottomSheetBehavior.STATE_DRAGGING -> {

                    }

                    BottomSheetBehavior.STATE_SETTLING -> {

                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // dim only when > collapsed
                binding.dimOverlay.alpha = slideOffset.coerceIn(0f, 0.6f)
            }
        })

        stopsAdapter = StopsAdapter { stop ->
            if (appViewModel.selectionMode.value == SelectionMode.FROM && appViewModel.toStop.value == stop) {
                showSameStopSnackBar()
            } else {
                appViewModel.selectStop(stop)
            }
        }

        binding.sheetContent.recyclerView.adapter = stopsAdapter

        setupSheetSearchListeners()
        setupSheetClickListeners()
        setupSheetObservers()
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager

        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun clearSheetFocus() {

        ignoreFocusChanges = true   // 🔥 START IGNORE

        binding.sheetContent.stopName.clearFocus()
        binding.sheetContent.fromText.clearFocus()
        binding.sheetContent.toText.clearFocus()

        binding.root.requestFocus() // move focus away
        hideKeyboard()

        binding.root.post {
            ignoreFocusChanges = false
        }
    }

    private fun setupSheetObservers() {

        appViewModel.sheetSource.observe(viewLifecycleOwner) { source ->
            val titleText = if (source == SheetSource.HOME) {
                getString(R.string.plan) // Make sure ye string resource file mein ho
            } else {
                getString(R.string.departures)
            }
            binding.sheetContent.title.text = titleText
        }

        appViewModel.filteredStops.observe(viewLifecycleOwner) { stops ->
            allStops = stops
            stopsAdapter.submitList(stops)
        }

        appViewModel.toStop.observe(viewLifecycleOwner) { stop ->
            binding.sheetContent.toText.setText(stop?.name ?: "")
            updateSheetVisibility()
        }

        appViewModel.fromStop.observe(viewLifecycleOwner) { stop ->
            binding.sheetContent.fromText.setText(stop?.name ?: "")
            if (stop != null && appViewModel.toStop.value != null) {
                sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    private fun updateSheetVisibility() {
        val hasToStop = appViewModel.toStop.value != null
        val sheet = binding.sheetContent

        if (hasToStop) {
            sheet.routeHeader.visibility = View.VISIBLE
            sheet.stopName.visibility = View.GONE
            if (appViewModel.fromStop.value == null) {
                appViewModel.selectionMode.value = SelectionMode.FROM
                binding.sheetContent.fromText.requestFocus()
            }
        } else {
            sheet.routeHeader.visibility = View.GONE
            sheet.stopName.visibility = View.VISIBLE
            binding.sheetContent.toText.requestFocus()
            appViewModel.selectionMode.value = SelectionMode.TO
        }
    }

    private fun setupSheetSearchListeners() {
        val sheet = binding.sheetContent

        sheet.stopName.addTextChangedListener { filterStops(it.toString()) }

        sheet.toText.addTextChangedListener {
            if (sheet.toText.hasFocus()) filterStops(it.toString())
        }

        sheet.fromText.addTextChangedListener {
            if (sheet.fromText.hasFocus()) filterStops(it.toString())
        }
    }

    private fun filterStops(query: String) {
        val filtered = if (query.isEmpty()) allStops
        else allStops.filter { it.name.lowercase().contains(query.lowercase()) }
        stopsAdapter.submitList(filtered)
    }

    private fun setupSheetClickListeners() {
        val sheet = binding.sheetContent

        sheet.btnSwap.setOnClickListener {
            val from = appViewModel.fromStop.value
            val to = appViewModel.toStop.value
            appViewModel.fromStop.value = to
            appViewModel.toStop.value = from
        }
    }

    private fun showSameStopSnackBar() {
        Snackbar.make(
            binding.root, "Start and destination cannot be the same", Snackbar.LENGTH_SHORT
        ).setAnchorView(binding.sheetContent.recyclerView).show()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun drawStaticRoute(points: List<LatLng>) {

        if (!isMapReady || points.size < 2) return

        routePolyline?.remove()
        routeMarkers.forEach { it.remove() }
        routeMarkers.clear()

        routePolyline = googleMap.addPolyline(
            PolylineOptions().addAll(points)
                .color(ContextCompat.getColor(requireContext(), R.color.app_color)).width(10f)
        )

        // Camera fit ONCE
        val bounds = LatLngBounds.builder().apply {
            points.forEach { include(it) }
        }.build()

        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, 120)
        )

        // Draw stop markers separately
        val route = appViewModel.selectedRoute.value ?: return
        drawStaticStopMarkers(route.stops)
    }

    private fun drawStaticStopMarkers(stops: List<Stop>) {

        if (!isMapReady || stops.isEmpty()) return

        routeMarkers.forEach { it.remove() }
        routeMarkers.clear()

        stops.forEachIndexed { index, stop ->

            val iconRes = when (index) {
                0 -> R.drawable.ic_marker_start
                stops.lastIndex -> R.drawable.ic_marker_end
                else -> R.drawable.ic_marker_stop
            }

            val marker = googleMap.addMarker(
                MarkerOptions().position(LatLng(stop.latitude, stop.longitude)).icon(
                    MapUtils.bitmapDescriptorFromVector(
                        requireContext(), iconRes
                    )
                ).anchor(0.5f, 0.5f)
            )

            marker?.let { routeMarkers.add(it) }
        }
    }

    private fun setupMap(savedInstanceState: Bundle?) {

        binding.mapView.onCreate(savedInstanceState)

        binding.mapView.getMapAsync { map ->
            googleMap = map
            isMapReady = true

            applyMapStyle()
            moveCameraToUserLocationOnce()

            googleMap.uiSettings.apply {
                isScrollGesturesEnabled = false
                isZoomGesturesEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                isMyLocationButtonEnabled = false
                isMapToolbarEnabled = false
            }

            googleMap.isTrafficEnabled = false
            googleMap.isIndoorEnabled = false
            googleMap.isBuildingsEnabled = false

            checkLocationPermission()
            setupClicks()
            setupObservers()
            if (appViewModel.selectedRoute.value != null) {
                recenterOnSelectedRoute()
            }
        }
    }

    private fun applyMapStyle() {
        val isDark = runBlocking {
            PreferencesDataStoreHelper(requireContext()).getFirstPreference(
                PreferenceDataStoreKeysConstants.DARK_MODE, false
            )
        }

        val styleRes = if (isDark) {
            R.raw.map_style_dark
        } else {
            R.raw.map_style_clean
        }

        googleMap.setMapStyle(
            MapStyleOptions.loadRawResourceStyle(
                requireContext(), styleRes
            )
        )
    }

    private fun setupClicks() {

        googleMap.setOnMapClickListener { }
        googleMap.setOnMarkerClickListener { true }
        googleMap.setOnCameraMoveListener { }

        binding.defaultView.addPressEffect { saveAndApply("default") }
        binding.satelliteView.addPressEffect { saveAndApply("satellite") }
        binding.terrainView.addPressEffect { saveAndApply("terrain") }

        binding.layers.addPressEffect {

            val isOpening = !binding.mapViewType.isVisible

            if (isOpening) {

                binding.mapViewType.apply {
                    visibility = View.VISIBLE
                    alpha = 0f
                    translationY = (-16).dp.toFloat()   // start slightly up

                    animate().alpha(1f).translationY(0f).setDuration(220)
                        .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                }

                lifecycleScope.launch {
                    val currentView =
                        PreferencesDataStoreHelper(requireContext()).getFirstPreference(
                            PreferenceDataStoreKeysConstants.MAP_VIEW, "default"
                        )
                    updateMapViewUI(currentView)
                }

            } else {

                binding.mapViewType.animate().alpha(0f).translationY((-16).dp.toFloat())
                    .setDuration(180)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction {
                        binding.mapViewType.visibility = View.GONE
                        binding.mapViewType.translationY = 0f
                    }.start()
            }
        }

        binding.currentLocation.addPressEffect {
            appViewModel.userLocation.value?.let { loc ->
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(loc.latitude, loc.longitude), 16f
                    )
                )
            }
        }

        binding.sheetContent.stopName.setOnDrawableEndClick {
            appViewModel.openChooseStop.value = true
            appViewModel.sheetSource.value = SheetSource.HOME
            binding.sheetContent.recyclerView.isVisible = true
            sheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            appViewModel.selectNearestStop()
        }

        binding.sheetContent.stopName.setOnFocusChangeListener { _, hasFocus ->
            if (ignoreFocusChanges) return@setOnFocusChangeListener

            if (hasFocus && sheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                appViewModel.sheetSource.value = SheetSource.DEPARTURES
                binding.sheetContent.recyclerView.isVisible = true

                sheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            }
        }

        binding.btnSwap.addPressEffect {
            val from = appViewModel.fromStop.value
            val to = appViewModel.toStop.value
            if (from != null && to != null) {
                appViewModel.fromStop.value = to
                appViewModel.toStop.value = from
            }
        }

        binding.clear.addPressEffect {
            appViewModel.clearRoute()
            clearRouteFromMap()
        }

        binding.showDepartures.addPressEffect {
            openDeparturesSheet()
        }
    }

    private fun recenterOnSelectedRoute() {
        val points = appViewModel.roadRoutePoints.value
        if (!isMapReady || points.isNullOrEmpty() || points.size < 2) return

        val bounds = LatLngBounds.builder().apply {
            points.forEach { include(it) }
        }.build()

        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, 120)
        )
    }

    fun saveAndApply(type: String) {
        updateMapViewUI(type)
        updateMapType(type)
        lifecycleScope.launch {
            PreferencesDataStoreHelper(requireContext()).putPreference(
                PreferenceDataStoreKeysConstants.MAP_VIEW, type
            )
        }
        binding.mapViewType.isVisible = false
    }

    private fun updateMapType(type: String) {
        if (!::googleMap.isInitialized || !isMapReady) return

        val prefs = PreferencesDataStoreHelper(requireContext())
        val isDark = runBlocking {
            prefs.getFirstPreference(PreferenceDataStoreKeysConstants.DARK_MODE, false)
        }

        when (type) {
            "satellite" -> {
                googleMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
            }

            "terrain" -> {
                googleMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
                googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                        requireContext(), R.raw.map_style_clean
                    )
                )
            }

            else -> {
                googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
                val styleRes = if (isDark) R.raw.map_style_dark else R.raw.map_style_clean
                googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                        requireContext(), styleRes
                    )
                )
            }
        }
    }

    private fun updateMapViewUI(type: String) {

        fun select(card: MaterialCardView, text: TextView) {
            card.strokeWidth = 2.dp
            text.setTextColor(requireContext().getColor(R.color.nav_color))
        }

        fun unselect(card: MaterialCardView, text: TextView) {
            card.strokeWidth = 0
            text.setTextColor(requireContext().getColor(R.color.dark_gray))
        }

        unselect(binding.defaultView, binding.defaultName)
        unselect(binding.satelliteView, binding.satelliteName)
        unselect(binding.terrainView, binding.terrainName)

        when (type) {
            "satellite" -> select(binding.satelliteView, binding.satelliteName)
            "terrain" -> select(binding.terrainView, binding.terrainName)
            else -> select(binding.defaultView, binding.defaultName)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableLocation() {
        googleMap.isMyLocationEnabled = true

        locationClient.lastLocation.addOnSuccessListener { loc ->
            loc ?: return@addOnSuccessListener
            appViewModel.userLocation.value = loc

            if (appViewModel.selectedRoute.value != null && (appViewModel.roadRoutePoints.value?.size
                    ?: 0) > 1
            ) {
                return@addOnSuccessListener
            }

            googleMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(loc.latitude, loc.longitude), 15f
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun moveCameraToUserLocationOnce() {

        val loc = appViewModel.userLocation.value ?: return

        if (!isMapReady) return

        googleMap.isMyLocationEnabled = true

        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(loc.latitude, loc.longitude), 14.5f
            )
        )
    }

    private fun setupObservers() {

        appViewModel.roadRoutePoints.observe(viewLifecycleOwner) { points ->
            if (points.size > 1) {
                drawStaticRoute(points)
            }
        }

        appViewModel.routeReady.observe(viewLifecycleOwner) { ready ->
            if (ready == true) {
                val route = appViewModel.selectedRoute.value ?: return@observe

                populateRouteHeader(route)
                openDeparturesSheet()
            } else {
                hideRouteHeader()
            }
        }
    }

    private fun openDeparturesSheet() {
        val route = appViewModel.selectedRoute.value ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            DeparturesBottomSheet(
                buildAllDepartures(route), route.start
            ).show(parentFragmentManager, "departures")
        }
    }

    private fun populateRouteHeader(route: Route) {
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        binding.sheetContent.root.visibility = View.GONE
        clearSheetFocus()
        binding.routeHeader.visibility = View.VISIBLE
        binding.showDepartures.visibility = View.VISIBLE
        binding.clear.visibility = View.VISIBLE

        binding.fromText.text = route.start
        binding.toText.text = route.end

    }

    private fun clearRouteFromMap() {
        routePolyline?.remove()
        routePolyline = null

        routeMarkers.forEach { it.remove() }
        routeMarkers.clear()
    }

    private fun hideRouteHeader() {
        binding.sheetContent.root.visibility = View.VISIBLE
        clearSheetFocus()
        binding.routeHeader.visibility = View.GONE
        binding.showDepartures.visibility = View.GONE
        binding.clear.visibility = View.GONE

        binding.sheetContent.stopName.apply {
            setText("")
            hint = getString(R.string.where_to)
            isEnabled = true
            clearFocus()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildAllDepartures(route: Route): List<Departure> {
        val now = LocalTime.now()

        return buildFullDaySchedule(route).map { time ->
            Departure(
                routeName = route.name,
                destination = route.end,
                scheduledTime = time,
                minutesDiff = Duration.between(now, time).toMinutes()
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildFullDaySchedule(route: Route): List<LocalTime> {
        val first = LocalTime.parse(route.first_ride_time)
        val last = LocalTime.parse(route.last_ride_time)

        val times = mutableListOf<LocalTime>()
        var t = first

        while (!t.isAfter(last)) {
            times.add(t)
            t = t.plusMinutes(route.leave_bus.toLong())
        }
        return times
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()

        if (appViewModel.selectedRoute.value != null) {
            recenterOnSelectedRoute()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapView.onDestroy()
        _binding = null
    }

    val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun EditText.setOnDrawableEndClick(action: () -> Unit) {
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {

                val isRtl = layoutDirection == View.LAYOUT_DIRECTION_RTL

                val drawable = if (isRtl) {
                    compoundDrawables[0] // START = LEFT (RTL end)
                } else {
                    compoundDrawables[2] // END = RIGHT (LTR end)
                } ?: return@setOnTouchListener false

                val drawableWidth = drawable.bounds.width()

                val clicked = if (isRtl) {
                    event.x <= paddingStart + drawableWidth
                } else {
                    event.x >= width - paddingEnd - drawableWidth
                }

                if (clicked) {
                    action()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

}

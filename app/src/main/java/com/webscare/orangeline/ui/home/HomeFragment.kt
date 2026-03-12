package com.webscare.orangeline.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.webscare.orangeline.AppViewModel
import com.webscare.orangeline.R
import com.webscare.orangeline.common.MapUtils
import com.webscare.orangeline.common.Utils.addPressEffect
import com.webscare.orangeline.common.datastore.PreferenceDataStoreKeysConstants
import com.webscare.orangeline.common.datastore.PreferencesDataStoreHelper
import com.webscare.orangeline.common.enums.NavigationMode
import com.webscare.orangeline.common.enums.SelectionMode
import com.webscare.orangeline.common.enums.SheetSource
import com.webscare.orangeline.common.service.NavigationLocationService
import com.webscare.orangeline.databinding.FragmentHomeBinding
import com.webscare.orangeline.databinding.FragmentHomePipBinding
import com.webscare.orangeline.domain.model.Departure
import com.webscare.orangeline.domain.model.Route
import com.webscare.orangeline.domain.model.Stop
import com.webscare.orangeline.ui.route.ChooseStopBottomSheetFragment
import com.webscare.orangeline.ui.route.DestinationReachedBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.compareTo
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var _pipBinding: FragmentHomePipBinding? = null
    private val pipBinding get() = _pipBinding!!
    private var stationLabelsCreated = false
    private var isUsingPipLayout = false
    private var sensorManager: SensorManager? = null
    private var accelValues = FloatArray(3)
    private var magnetValues = FloatArray(3)
    private var hasAccel = false
    private var hasMagnet = false
    private var currentHeading = 0f
    private var navSensorListener: SensorEventListener? = null
    private var reachedStopIndex = 0
    private var isInsideStopZone = false
    private var lastReachedStopId: Int? = null
    private var arrivalSoonNotified = false
    private var reachedNotified = false
    private val DESTINATION_NOTIFY_MINUTES = 1
    private val NAV_CHANNEL_ID = "nav_channel"
    private val NAV_NOTIFY_ID = 101
    private var isDestinationReached = false
    private var destinationSheetShown = false
    private var lastMarkerRotation = 0f
    private var lastSensorUpdateTime = 0L
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private var onNotificationPermissionGranted: (() -> Unit)? = null
    private val ARRIVE_RADIUS = 40.0   // meters
    private val EXIT_RADIUS = 80.0     // meters
    private var allStops: List<Stop> = emptyList()
    private val allRoutePolylines = mutableListOf<Polyline>()
    private val stationLabelMarkers = mutableMapOf<Int, Marker>()
    private lateinit var googleMap: GoogleMap
    private var currentPreviewMarker: Marker? = null
    private lateinit var locationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private var isNavUpdatesRunning = false
    private val appViewModel: AppViewModel by activityViewModels()
    private var minutesPerMeter = 0.0
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>
    private var currentNavIndex: Int = 0
    private var isExpanded = false
    private var startY = 0f
    private var lastTranslationY = 0f
    private var isMapReady = false
    private var cachedBusTimes: List<LocalTime> = emptyList()
    private var routePolyline: Polyline? = null
    private val routeMarkers = mutableListOf<Marker>()
    private var stopsVisible = false
    private var ignoreFocusChanges = false
    private var navUserMarker: Marker? = null
    private var isCameraFollowing = false
    private var lastBearing = 0f
    private var lastStableHeading = 0f
    private lateinit var sheetBehavior: BottomSheetBehavior<FrameLayout>
    private lateinit var stopsAdapter: StopsAdapter
    private var checkedPersistedNavMode = false
    private var shouldRestoreNav = false
    private var allowServiceStop = false
    private var isReturningFromPip = false
    private var wasNavigating = false

    private var lastCameraUpdateTime = 0L
    private var lastCameraBearing = 0f
    private var lastCameraTarget: LatLng? = null

    private val CAMERA_UPDATE_INTERVAL_MS = 140L
    private val CAMERA_BEARING_THRESHOLD = 10f   // jitter control
    private val CAMERA_TARGET_THRESHOLD_METERS = 1.5 // ignore micro target moves

    private val navLocationReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            when (intent?.action) {
                NavigationLocationService.ACTION_LOCATION -> {
                    val lat = intent.getDoubleExtra(
                        NavigationLocationService.EXTRA_LAT, 0.0
                    )
                    val lng = intent.getDoubleExtra(
                        NavigationLocationService.EXTRA_LNG, 0.0
                    )

                    val loc = Location("nav_service").apply {
                        latitude = lat
                        longitude = lng
                    }

                    // 🔥 this keeps your existing observer chain working
                    appViewModel.userLocation.value = loc
                }

                NavigationLocationService.ACTION_NAV_EXITED -> {
                    // User exited navigation from notification -> sync VM/UI back to IDLE
                    if (appViewModel.navigationMode.value != NavigationMode.ROUTE_PREVIEW &&
                        appViewModel.navigationMode.value != NavigationMode.IDLE
                    ) {
                        appViewModel.enterRoutePreview()
                    }
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) enableLocation()
                else locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        notificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    createNavNotificationChannel()
                    onNotificationPermissionGranted?.invoke()
                    onNotificationPermissionGranted = null
                } else {
                    Snackbar.make(
                        binding.root,
                        "Enable notifications to get arrival alert",
                        Snackbar.LENGTH_LONG
                    ).setAction("Settings") { openAppNotificationSettings() }.show()
                    onNotificationPermissionGranted = null
                }
            }

    }

    private var rootContainer: ViewGroup? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Create a container to hold both layouts
        val frameLayout = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        rootContainer = frameLayout

        // Check if we're in PiP mode and use appropriate layout
        val inPipMode = appViewModel.isInPipMode.value == true

        if (inPipMode) {
            _pipBinding = FragmentHomePipBinding.inflate(inflater, container, false)
            isUsingPipLayout = true
            frameLayout.addView(pipBinding.root)
        } else {
            _binding = FragmentHomeBinding.inflate(inflater, container, false)
            isUsingPipLayout = false
            frameLayout.addView(binding.root)
        }

        return frameLayout
    }

    private fun setupSelectionCameraObserver() {
        appViewModel.fromStop.observe(viewLifecycleOwner) { stop ->
            if (stop == null || !::googleMap.isInitialized) return@observe

            if (appViewModel.navigationMode.value != NavigationMode.NAVIGATING) {

                // 1. DISABLE AUTO-FOLLOW: This is the most likely culprit.
                // You must stop the camera from snapping back to the user.
                isCameraFollowing = false

                // 2. RESET THE TIMER:
                // Setting this to Long.MAX_VALUE prevents other automated
                // camera logic from running for a while.
                lastCameraUpdateTime = 0L

                val fromLatLng = LatLng(stop.latitude, stop.longitude)
                Log.d("train", "Moving camera to FROM STOP: $fromLatLng")

                // 3. EXECUTE ANIMATION
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(fromLatLng, 15f))
            }
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        appViewModel.syncData()
        locationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        setupSelectionCameraObserver()
        if (isUsingPipLayout) {
            setupPipLayout(savedInstanceState)
        } else {
            setupNormalLayout(savedInstanceState)
        }
    }

    private fun setupNormalLayout(savedInstanceState: Bundle?) {
        sheetBehavior = BottomSheetBehavior.from(binding.stopSelectionSheet)
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        appViewModel.sheetSource.value = SheetSource.HOME
        setupBottomSheet()
        setupMap(savedInstanceState)
        setupRouteHeaderEditClicks()
        setupClicks()
        createNavNotificationChannel()

        // If app was killed while navigating, prevent the initial IDLE observer from stopping the service
        // before we restore ViewModel state.
        lifecycleScope.launch {
            val prefs = PreferencesDataStoreHelper(requireContext())
            val mode =
                prefs.getFirstPreference(PreferenceDataStoreKeysConstants.NAVIGATION_MODE, "IDLE")
            shouldRestoreNav = (mode == NavigationMode.NAVIGATING.name)
            checkedPersistedNavMode = true
            if (shouldRestoreNav) {
                // Hide planning sheet while we wait for restore to complete.
                binding.bottomSheet.isVisible = false
                binding.sheetContent.root.isVisible = false
            }
        }
    }

    private fun setupPipLayout(savedInstanceState: Bundle?) {
        // Setup map for PiP layout (uses same setupMap but with pipBinding)
        setupMap(savedInstanceState)

        // Setup PiP observers when map is ready (will be called from setupMap)
        // But also set them up here if map is already ready
        if (isMapReady && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupPipObservers()
        }
    }

    private fun setupPipObservers() {
        Log.d("train", "Inside pip observer")
        // Observe navigation state and update PiP footer
        appViewModel.selectedRoute.observe(viewLifecycleOwner) { route ->
            route?.let { updatePipNavFooter(it) }
        }

        appViewModel.userLocation.observe(viewLifecycleOwner) { loc ->
            if (loc != null && appViewModel.navigationMode.value == NavigationMode.NAVIGATING) {
                updateNavUserMarker(loc)
                if (isCameraFollowing) {
                    updateNavCamera(loc)
                }

                val route = appViewModel.selectedRoute.value
                if (route != null) {
                    val full = appViewModel.roadRoutePoints.value ?: emptyList()
                    if (full.size > 1) {
                        drawUpcomingRouteOnly(full, loc)
                    }
                    updatePipNavFooter(route)
                }
            }
        }

        appViewModel.navigationMode.observe(viewLifecycleOwner) { mode ->

            if (mode == NavigationMode.NAVIGATING) {
                startNavCameraMode()
                // Apply camera offset after view is measured
                pipBinding.root.post {
                    applyNavCameraOffset()
                }
                startCompass()
                recenterNavInstant()
                disableDefaultMyLocationDot()
                appViewModel.selectedRoute.value?.let { route ->
                    initRouteSpeed(route)
                    updatePipNavFooter(route)
                    startLiveLocationUpdates()
                }
            }
        }

        // Attempt restore once routes are available
        appViewModel.routes.observe(viewLifecycleOwner) { routes ->
            if (!shouldRestoreNav) return@observe
            if (routes.isNullOrEmpty()) return@observe
            shouldRestoreNav = false
            appViewModel.restoreNavStateIfNeeded(routes)
        }
    }

    private fun updatePipNavFooter(route: Route) {
        if (!isUsingPipLayout || _pipBinding == null) return

        val currentIdx = reachedStopIndex.coerceIn(0, route.stops.lastIndex)
        val current = route.stops[currentIdx]
        val next = route.stops.getOrNull(currentIdx + 1)

        pipBinding.navStopTitlePip.text = current.name
        pipBinding.navNextStopPip.text = next?.name ?: getString(R.string.reached_destination)

        val loc = appViewModel.userLocation.value
        val nextStopMin = timeToNextStopMinutes(route, currentIdx, loc)
        pipBinding.navEtaBubblePip.text =
            if (isDestinationReached) "Done" else "${nextStopMin}\nmin"
    }

    private fun switchToPipLayout() {
        val container = rootContainer ?: return

        // Store current map state before cleanup
        val wasNavigating = appViewModel.navigationMode.value == NavigationMode.NAVIGATING
        val currentRoute = appViewModel.selectedRoute.value
        val currentLocation = appViewModel.userLocation.value

        // Remove old normal layout
        binding.root.let { oldView ->
            container.removeView(oldView)
            // Clean up map from old layout
            val oldMapView = oldView.findViewById<com.google.android.gms.maps.MapView>(R.id.mapView)
            oldMapView?.onPause()
            oldMapView?.onDestroy()
        }

        // Reset map state - map will be reinitialized in setupPipLayout
        isMapReady = false

        // Inflate and add new PiP layout
        _pipBinding = FragmentHomePipBinding.inflate(layoutInflater, container, false)
        isUsingPipLayout = true
        container.addView(pipBinding.root)

        // Re-initialize map (will set up observers when map is ready)
        setupPipLayout(null)

        // If we were navigating, restore the navigation state after map is ready
        if (wasNavigating && currentRoute != null) {
            // The observers will handle this, but we can also trigger an update
            pipBinding.root.post {
                if (isMapReady) {
                    isCameraFollowing = true
                    applyNavCameraOffset()
                    currentLocation?.let {
                        navUserMarker?.remove()
                        navUserMarker = null
                        updateNavUserMarker(it)
                        updateNavCamera(it)
                    }
                    updatePipNavFooter(currentRoute)
                }
            }
        }
    }

    private fun switchToNormalLayout() {
        val container = rootContainer ?: return

        // Store current map state before cleanup
        val wasNavigating = appViewModel.navigationMode.value == NavigationMode.NAVIGATING
        val currentRoute = appViewModel.selectedRoute.value
        val currentLocation = appViewModel.userLocation.value

        routePolyline = null
        routeMarkers.clear()
        navUserMarker = null
        stationLabelsCreated = false
        stationLabelMarkers.clear()

        // Remove old PiP layout
        pipBinding.root.let { oldView ->
            container.removeView(oldView)
            // Clean up map from old layout
            val oldMapView = oldView.findViewById<com.google.android.gms.maps.MapView>(R.id.mapView)
            oldMapView?.onPause()
            oldMapView?.onDestroy()
        }

        // Reset map state - map will be reinitialized in setupPipLayout
        isMapReady = false
        isReturningFromPip = true

        // Inflate and add new normal layout
        _binding = FragmentHomeBinding.inflate(layoutInflater, container, false)
        isUsingPipLayout = false
        container.addView(binding.root)

        // Re-initialize everything (will set up observers when map is ready)
        setupNormalLayout(null)

        // If we were navigating, restore the navigation state after layout is ready
        if (wasNavigating && currentRoute != null) {
            pipBinding.root.post {
                if (isMapReady) {
                    isCameraFollowing = true
                    applyNavCameraOffset()
                    updateNavFooter(currentRoute, currentLocation)
                    currentLocation?.let {
                        navUserMarker = null
                        updateNavUserMarker(it)
                        updateNavCamera(it)
                    }
                }
            }
        }
    }

    private fun setupBottomSheet() {
        val sheetView = binding.root.findViewById<FrameLayout>(R.id.stopSelectionSheet)
        sheetBehavior = BottomSheetBehavior.from(sheetView)

        sheetBehavior.isFitToContents = false
        sheetBehavior.halfExpandedRatio = 0.7f

        sheetBehavior.isHideable = false
        sheetBehavior.skipCollapsed = false
        val peekHeightDp = 140

        sheetBehavior.peekHeight = peekHeightDp.dp
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        binding.dimOverlay.setOnClickListener {
            clearSheetFocus()
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            stopLiveLocationUpdates()
            clearRouteAndResetUI()
            appViewModel.exitNavigation()
        }

        binding.sheetContent.recyclerView.isNestedScrollingEnabled = true

        sheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                _binding?.let { binding ->
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
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                _binding?.let { binding ->
                    // dim only when > collapsed
                    binding.dimOverlay.alpha = slideOffset.coerceIn(0f, 0.6f)
                }
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
        val imm =
            requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager

        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun clearSheetFocus() {
        ignoreFocusChanges = true
        binding.sheetContent.stopName.clearFocus()
        binding.sheetContent.fromText.clearFocus()
        binding.sheetContent.toText.clearFocus()
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
                sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }
    }

    private fun updateSheetVisibility() {
        if (appViewModel.navigationMode.value == NavigationMode.ROUTE_PREVIEW) return
        // Logic migrated from original Fragment
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

        sheet.toText.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                appViewModel.setSelectionMode(SelectionMode.TO)
                filterStops(sheet.toText.text.toString())
            }
        }

        sheet.toText.addTextChangedListener {
            if (sheet.toText.hasFocus()) filterStops(it.toString())
        }

        sheet.fromText.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                appViewModel.setSelectionMode(SelectionMode.FROM)
                filterStops(sheet.fromText.text.toString())
            }
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

        sheet.btnSwap.addPressEffect {
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
    // ---------------- MAP ----------------

    private fun drawAllRoutesTracks(
        tracks: Map<Int, List<LatLng>>
    ) {
        if (!isMapReady) return
        if (tracks.isEmpty()) return

        allRoutePolylines.forEach { it.remove() }
        allRoutePolylines.clear()

        val appColor = ContextCompat.getColor(requireContext(), R.color.app_color)
        val color10 = ColorUtils.setAlphaComponent(appColor, (255 * 0.80f).toInt())

        tracks.values.forEach { pts ->
            if (pts.size < 2) return@forEach

            val pl = googleMap.addPolyline(
                PolylineOptions().addAll(pts).color(color10).width(10f).zIndex(0f)
                    .jointType(JointType.ROUND).startCap(RoundCap()).endCap(RoundCap())
            )
            allRoutePolylines.add(pl)
        }
    }

    private fun updateVisibleStationLabels() {

        if (!isMapReady) return
        if (allStops.isEmpty()) return

        // 🔥 CREATE ONLY ONCE (NO MORE ANR)
        if (!stationLabelsCreated) {
            allStops.forEach { stop ->
                if (stationLabelMarkers.containsKey(stop.id)) return@forEach
                val marker = googleMap.addMarker(createStationLabel(stop))
                marker?.let {
                    stationLabelMarkers[stop.id] = it
                }
            }
            stationLabelsCreated = true
        }

        // 🔁 ONLY TOGGLE VISIBILITY
        val show = appViewModel.navigationMode.value == NavigationMode.IDLE
        stationLabelMarkers.values.forEach { it.isVisible = show }
    }

    private fun createStationLabel(stop: Stop): MarkerOptions {

        return MarkerOptions().position(LatLng(stop.latitude, stop.longitude)).icon(
            MapUtils.iconTextLabel(
                context = requireContext(),
                iconRes = R.drawable.ic_station_marker,
                text = stop.name,
                iconColor = ContextCompat.getColor(requireContext(), R.color.app_color),
                fontRes = R.font.medium
            )
        ).anchor(0f, 0.5f).zIndex(1f)
    }

    private fun setupMap(savedInstanceState: Bundle?) {
        val mapView = if (isUsingPipLayout) pipBinding.mapView else binding.mapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            googleMap = map
            isMapReady = true

            lifecycleScope.launch {
                val currentView = PreferencesDataStoreHelper(requireContext()).getFirstPreference(
                    PreferenceDataStoreKeysConstants.MAP_VIEW, "default"
                )
                updateMapType(currentView)
                if (!isUsingPipLayout) {
                    updateMapViewUI(currentView)
                }
            }

            googleMap.uiSettings.apply {
                isMyLocationButtonEnabled = false
                isCompassEnabled = false
                isMapToolbarEnabled = false
            }

            if (appViewModel.navigationMode.value == NavigationMode.NAVIGATING) {
                appViewModel.userLocation.value?.let { lastLoc ->
                    // Nullify old reference so updateNavUserMarker creates a new one on this map
                    navUserMarker = null
                    updateNavUserMarker(lastLoc)
                }
            }
            // In PiP mode, hide Google Maps attribution by setting padding
            if (isUsingPipLayout) {
                // Post to ensure map is fully loaded
                rootContainer?.post {
                    if (isMapReady) {
                        // Set left padding to push attribution out of view in PiP
                        googleMap.setPadding(50.dp, 0, 0, 0)
                    }
                }
            }

            googleMap.setOnCameraIdleListener {
                if (!isUsingPipLayout) {
                    updateVisibleStationLabels()
                }
            }

            googleMap.isBuildingsEnabled = false
            googleMap.isIndoorEnabled = false
            googleMap.isTrafficEnabled = false
            checkLocationPermission()

            googleMap.setOnMapLoadedCallback {
                if (!isUsingPipLayout) {
                    updateVisibleStationLabels()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (isUsingPipLayout) {
                    setupPipObservers()
                    // Update PiP footer and apply camera offset if already navigating
                    appViewModel.selectedRoute.value?.let { route ->
                        if (appViewModel.navigationMode.value == NavigationMode.NAVIGATING) {
                            pipBinding.root.post {
                                applyNavCameraOffset()
                                updatePipNavFooter(route)
                            }
                        }
                    }
                } else {
                    setupObservers()
                    // Apply camera offset if already navigating
                    if (appViewModel.navigationMode.value == NavigationMode.NAVIGATING) {
                        binding.root.post {
                            applyNavCameraOffset()
                        }
                    }
                }
            }
        }
    }

    private fun moveCamera() {
        val fromStop = appViewModel.fromStop.value
        val toStop = appViewModel.toStop.value
        val stops = appViewModel.selectedRoute.value?.stops

        if (fromStop != null && toStop != null && !stops.isNullOrEmpty()) {
            val bounds = LatLngBounds.builder().apply {
                stops.forEach { include(LatLng(it.latitude, it.longitude)) }
            }.build()

            val dLon = Math.toRadians(toStop.longitude - fromStop.longitude)
            val lat1 = Math.toRadians(fromStop.latitude)
            val lat2 = Math.toRadians(toStop.latitude)
            val x = sin(dLon) * cos(lat2)
            val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
            val bearing = (Math.toDegrees(atan2(x, y)).toFloat() + 360f) % 360f

            binding.root.post {
                if (!isMapReady) return@post
                googleMap.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(bounds.center)
                            .zoom(googleMap.cameraPosition.zoom)
                            .tilt(0f)
                            .bearing(0f)
                            .build()
                    )
                )
                googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                val fitZoom = googleMap.cameraPosition.zoom

                googleMap.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(bounds.center)
                            .zoom(fitZoom)
                            .tilt(0f)
                            .bearing(bearing)
                            .build()
                    ), 450, null
                )
            }
        }
    }

    private fun isNavServiceRunning(): Boolean {
        val manager = requireContext().getSystemService(
            android.content.Context.ACTIVITY_SERVICE
        ) as android.app.ActivityManager

        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == NavigationLocationService::class.java.name
        }
    }

    // ---------------- OBSERVERS ----------------

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupObservers() {
        appViewModel.openChooseStop.observe(viewLifecycleOwner) { open ->
            if (open == true && sheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                sheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            }
        }

        appViewModel.routeReady.observe(viewLifecycleOwner) { ready ->
            if (ready == true) {
                // Only trigger entry logic if we are currently doing nothing (IDLE).
                if (appViewModel.navigationMode.value == NavigationMode.IDLE) {
                    appViewModel.enterRoutePreview()
                    showRouteHeader()
                }
            } else {
                // If the route is actually gone (nullified), hide the header.
                binding.routeHeader.isVisible = false
            }
        }

        appViewModel.roadRoutePoints.observe(viewLifecycleOwner) { points ->
            if (points.size > 1) {
                drawRoadRoute(points, false) // always false — we handle camera here

                appViewModel.selectedRoute.value?.let {
                    drawStopMarkers(it.stops)
                    populateRouteUI(it)
                    if (appViewModel.navigationMode.value != NavigationMode.ROUTE_PREVIEW) {
                        binding.bottomSheet.isVisible = false
                    }
                }

                // Handle camera here when route is ready
                if (appViewModel.navigationMode.value == NavigationMode.ROUTE_PREVIEW) {
                    moveCamera()
                }
            }
        }

        appViewModel.navStopIndex.observe(viewLifecycleOwner) { index ->
            currentNavIndex = index
            if (appViewModel.navigationMode.value == NavigationMode.NAVIGATING) {
                appViewModel.selectedRoute.value?.let { route ->
                    updateNavFooter(route, appViewModel.userLocation.value)
                    focusStopOnMap(route, index)
                }
            }
        }

        // Live location updates -> update footer + auto-advance
        appViewModel.userLocation.observe(viewLifecycleOwner) { loc ->
            val route = appViewModel.selectedRoute.value ?: return@observe
            if (appViewModel.navigationMode.value != NavigationMode.NAVIGATING) return@observe
            if (loc == null) return@observe
            updateNavUserMarker(loc)
            updateNavCamera(loc)

            val full = appViewModel.roadRoutePoints.value ?: emptyList()
            if (full.size > 1) {
                drawUpcomingRouteOnly(full, loc)
            }
            updateReachedStopLogic(route, loc)
            updateNavFooter(route, loc)
            checkDestinationArrival(route, loc)
            checkIfDestinationReached(route, loc)
        }

        appViewModel.navigationMode.observe(viewLifecycleOwner) { mode ->
            when (mode) {
                NavigationMode.IDLE -> {
                    // Only stop service after we've entered NAVIGATING at least once
                    // and we're not in the middle of a restore attempt.
                    showStationLabels()

                    val tracks = appViewModel.allRouteTracks.value
                    if (!tracks.isNullOrEmpty()) {
                        drawAllRoutesTracks(tracks)
                    } else {
                        appViewModel.loadAllRouteTracks()  // triggers allRouteTracks observer which calls drawAllRoutesTracks
                    }

                    if (allowServiceStop) {
                        allowServiceStop = false
                        if (!shouldRestoreNav) {
                            stopNavForegroundService()
                        } else {
                            stopNavForegroundService()
                            shouldRestoreNav = false
                        }
                    }

                    keepScreenAwake(false)

//                    appViewModel.allRouteTracks.value?.let {
//                        drawAllRoutesTracks(it)
//                    }

                    stopLiveLocationUpdates()
                    binding.navFooter.isVisible = false
                    binding.navigationBar.isVisible = false
                    binding.clear.isVisible = false

                    stopNavCameraMode()
                    stopCompass()

                    // ✅ reset camera tilt/bearing + padding
                    resetMapPadding()
                    resetNavCameraView()

                    binding.routeHeader.isVisible = false // Hide Top Header
                    binding.sheetContent.root.isVisible = true // ✅ Ensure search UI is visible
                    sheetBehavior.isDraggable = true

                    enableDefaultMyLocationDot()
                    navUserMarker?.remove()
                    navUserMarker = null
                    if (appViewModel.fromStop.value != null || appViewModel.toStop.value != null) {
                        sheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                    } else {
                        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                        clearRouteAndResetUI()
                    }
                }

                NavigationMode.ROUTE_PREVIEW -> {
                    stopNavCameraMode()
                    stopCompass()
                    stopLiveLocationUpdates()
                    resetMapPadding()

//                    binding.root.post {
//                        resetNavCameraView()
//                    }

                    if (routePolyline == null) {
                        val points = appViewModel.roadRoutePoints.value ?: emptyList()
                        if (points.size > 1) {
                            drawRoadRoute(points, false) // animateCamera=false, we handle it below
                        }
                        appViewModel.selectedRoute.value?.let {
                            drawStopMarkers(it.stops)
                            populateRouteUI(it)
                        }
                    } else {
                        routePolyline?.isVisible = true
                    }

                    binding.root.post {
                        val points = appViewModel.roadRoutePoints.value ?: emptyList()
                        if (points.size > 1 && isMapReady) {
                            moveCamera()
                        } else {
                            // Fallback: move to FROM stop directly
                            appViewModel.fromStop.value?.let { from ->
                                googleMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(
                                            from.latitude,
                                            from.longitude
                                        ), 15f
                                    ),
                                    350, null
                                )
                            }
                        }
                    }

                    hideStationLabels()

//                    if (allowServiceStop && !shouldRestoreNav) {
//                        allowServiceStop = false
//                        Log.d("train","I am Here" )
//                        Log.d("train", "AllowServiceStop: $allowServiceStop , shouldRestoreNav: $shouldRestoreNav")
//                        stopNavForegroundService()
////                        setupSelectionCameraObserver()
//                    }

//                    This piece of code kill the service when we are in NORMAl layout or transition from pip to NORMAL layout
                    if (allowServiceStop) {
                        allowServiceStop = false
                        if (!shouldRestoreNav) {
                            stopNavForegroundService()
                        } else {
                            stopNavForegroundService()
                            shouldRestoreNav = false
                        }
                    }

                    keepScreenAwake(false)
                    binding.navFooter.isVisible = false
                    binding.navigationBar.isVisible = false
                    binding.clear.isVisible = false

                    enableDefaultMyLocationDot()
                    navUserMarker?.remove()
                    navUserMarker = null

                    // ✅ show route preview UI
                    showRouteHeader()
                    sheetBehavior.isDraggable = false
                    binding.sheetContent.root.isVisible = false
                    sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

//                    val points = appViewModel.roadRoutePoints.value ?: emptyList()
//                    if (points.size > 1) {
//                        drawRoadRoute(points, false)   // full route + bounds fit
//                    }
//
//                    // ✅ redraw stop markers if needed
//                    appViewModel.selectedRoute.value?.let {
//                        drawStopMarkers(it.stops)
//                        populateRouteUI(it)
//                    }

                }

                NavigationMode.NAVIGATING -> {
                    hideStationLabels()
                    if (!isNavServiceRunning()) {
                        allowServiceStop = true
                        startNavServiceWithPermission()
                    } else {
                        allowServiceStop = true  // still allow stopping later
                    }
                    keepScreenAwake(true)
                    clearAllRoutesTracks()
                    showNavigationUI()
                    sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    sheetBehavior.isDraggable = false
                    binding.sheetContent.root.isVisible = false
                    reachedStopIndex = 0
                    wasNavigating = true
                    isInsideStopZone = false
                    lastReachedStopId = null
                    hideStopCard()
                    startNavCameraMode()
                    // Apply camera offset after UI is shown (footer visibility changed)
                    binding.root.post {
                        applyNavCameraOffset()
                    }
                    startCompass()
                    recenterNavInstant()
                    disableDefaultMyLocationDot()
                    appViewModel.setNavStopIndex(0)
                    isDestinationReached = false
                    destinationSheetShown = false
                    arrivalSoonNotified = false
                    reachedNotified = false
                    appViewModel.selectedRoute.value?.let { route ->
                        initRouteSpeed(route)
                        updateNavFooter(route, appViewModel.userLocation.value)
                        startLiveLocationUpdates()
                    }
                    googleMap.setOnCameraMoveStartedListener { reason ->
                        if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                            // user dragged map manually
                            isCameraFollowing = false
                        }
                    }
                }
            }
        }

        // Attempt restore once routes are available.
        appViewModel.routes.observe(viewLifecycleOwner) { routes ->
            if (!shouldRestoreNav) return@observe
            if (routes.isNullOrEmpty()) return@observe
            shouldRestoreNav = false
            appViewModel.restoreNavStateIfNeeded(routes)
        }

        // Handle Picture-in-Picture layout switching
        appViewModel.isInPipMode.observe(viewLifecycleOwner) { inPip ->
            if (inPip == true && !isUsingPipLayout) {
                // Switch to PiP layout
                switchToPipLayout()
            } else if (inPip != true && isUsingPipLayout) {
                // Switch back to normal layout
                switchToNormalLayout()
            }
        }

        // If restore fails (missing cached ids, etc), we can now safely stop the service.
        appViewModel.navRestoreResult.observe(viewLifecycleOwner) { ok ->
            if (ok == null) return@observe
            if (!ok) {
                appViewModel.navRestoreResult.value = null
            } else {
                // Successful restore: ensure planning UI stays hidden while navigating
                binding.bottomSheet.isVisible = false
                binding.sheetContent.root.isVisible = false
                appViewModel.navRestoreResult.value = null
            }
        }

        appViewModel.previewStop.observe(viewLifecycleOwner) { stop ->
            if (stop == null) {
                binding.stopInfoCard.visibility = View.GONE
                return@observe
            }
            val route = appViewModel.selectedRoute.value ?: return@observe
            val index = route.stops.indexOfFirst { it.id == stop.id }
            binding.stopTitle.text = stop.name
            binding.stopSubTitle.text = getString(R.string.heading_towards, route.end)
            val kmLeft = routeDistanceLeftKm(route, index, null)

            val (value, unit) = formatDistance(kmLeft)

            binding.stopMeta.text =
                "${index + 1} of ${route.stops.size} stations • $value $unit • 4–5 mins"

            binding.stopInfoCard.apply {
                alpha = 0f
                visibility = View.VISIBLE
                animate().alpha(1f).setDuration(150).start()
            }
        }

//        Here camera is moving to the routes
        appViewModel.allRouteTracks.observe(viewLifecycleOwner) { map ->
            drawAllRoutesTracks(map)
            if (!wasNavigating) {
                resetNavCameraView()
            }
        }

        appViewModel.filteredStops.observe(viewLifecycleOwner) { stops ->
            allStops = stops
            updateVisibleStationLabels()
            stopsAdapter.submitList(stops)
        }
    }

    // ---------------- ROUTE DRAWING ----------------
    private fun drawRoadRoute(
        points: List<LatLng>,
        animateCamera: Boolean
    ) {
        if (!isMapReady || points.size < 2) return

        routePolyline?.remove()

        routePolyline = googleMap.addPolyline(
            PolylineOptions()
                .addAll(points)
                .color("#FC843A".toColorInt())
                .width(12f)
                .zIndex(10f)
                .jointType(JointType.ROUND)
                .startCap(RoundCap())
                .endCap(RoundCap())
                .geodesic(true)
        )

        if (!animateCamera) return

        val bounds = LatLngBounds.builder().apply {
            points.forEach { include(it) }
        }.build()

        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngBounds(bounds, 120)
        )
    }

    private fun drawStopMarkers(stops: List<Stop>) {
        if (stops.isEmpty()) return

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
                ).anchor(0.5f, 0.5f).zIndex(
                    when (index) {
                        0, stops.lastIndex -> 2f
                        else -> 1f
                    }
                )
            )

            marker?.tag = stop
            marker?.let { routeMarkers.add(it) }

            googleMap.setOnMarkerClickListener { marker ->
                val stop = marker.tag as? Stop ?: return@setOnMarkerClickListener false
                currentPreviewMarker = marker
                showStopBubble(marker, stop)
                true
            }

            googleMap.setOnCameraMoveListener {
                currentPreviewMarker?.let {
                    positionBubble(it.position)
                }
            }

            googleMap.setOnMapClickListener {
                currentPreviewMarker = null
                binding.stopInfoCard.visibility = View.GONE
            }

        }
    }

    private fun positionBubble(latLng: LatLng) {
        if (!isMapReady) return
        val point = googleMap.projection.toScreenLocation(latLng)
        binding.stopInfoCard.apply {
            post {
                translationX = point.x - width / 2f
                translationY = (point.y - height - 24.dp).toFloat()
            }
        }
    }

    private fun showStopBubble(marker: Marker, stop: Stop) {
        val route = appViewModel.selectedRoute.value ?: return
        val index = route.stops.indexOfFirst { it.id == stop.id }
        binding.stopTitle.text = stop.name
        binding.stopSubTitle.text = getString(R.string.heading_towards, route.end)

        binding.stopMeta.text = "${index + 1} of ${route.stops.size} stations • 2.1 km • 4–5 mins"
        positionBubble(marker.position)
        binding.stopInfoCard.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(150).start()
        }
    }

    private fun setupStopsList(stops: List<Stop>) {
        binding.stopsRecycler.apply {
            adapter = RouteStopsAdapter(stops)

            layoutAnimation = AnimationUtils.loadLayoutAnimation(
                context, R.anim.layout_slide_up
            )
            scheduleLayoutAnimation()
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun populateRouteUI(route: Route) {

        appViewModel.setNavStopIndex(0)

        setupStopsList(route.stops)

        val nextBus = getNextBusTime(
            route.first_ride_time, route.last_ride_time, route.leave_bus
        )

        if (nextBus != null) {
            binding.startTime.text = formatTime(nextBus)

            val endTime = nextBus.plusMinutes(route.total_ride_time.toLong())
            binding.endTime.text = formatTime(endTime)

            val remaining = getRemainingBuses(
                nextBus, route.last_ride_time, route.leave_bus
            )

            val extra = (remaining.size - 1).coerceAtLeast(0)

            binding.showMore.text = if (extra > 0) getString(R.string.more_timings, extra)
            else getString(R.string.show_timings)

            cachedBusTimes = remaining
        }

        // ---- BASIC INFO ----
        binding.arriveIn.text = getString(
            R.string.arrive_in_mins, route.total_ride_time
        )

// durationStay = fixed 30 seconds
        binding.durationStay.text = getString(
            R.string.for_seconds, 30
        )

// leaveTime = leave_bus (minutes interval)
        binding.leaveTime.text = getString(
            R.string.leaves_every_mins, route.leave_bus
        )
        binding.from.text = route.start
        binding.to.text = route.end

        binding.timeValue.text = route.total_ride_time.toString()
        val km = route.ride_distance.replace("km", "", true).trim().toDoubleOrNull() ?: 0.0

        val (value, unit) = formatDistance(km)

        binding.distanceValue.text = value
        binding.distanceUnit.text = unit

        binding.routeName.text = route.name

        // ---- DOTS (number of stops) ----
        renderHorizontalDots(route.stops.size)
        renderVerticalDots(route.stops)

        // ---- START / END ----
        binding.startStop.text = route.start
        binding.endStop.text = route.end

        // ---- SHOW SHEET ----
        binding.bottomSheet.visibility = View.VISIBLE

        binding.startLocation.text = route.start
        binding.endLocation.text = route.end
    }

    private fun renderHorizontalDots(count: Int) {
        binding.dotsLayout.removeAllViews()

        if (count < 2) return

        repeat(count) {
            val dot = View(requireContext()).apply {
                background = ContextCompat.getDrawable(
                    requireContext(), R.drawable.bg_route_dot
                )
            }

            val params = LinearLayout.LayoutParams(8.dp, 8.dp)
            params.marginStart = 6.dp
            params.marginEnd = 6.dp

            binding.dotsLayout.addView(dot, params)
        }
    }

    private fun renderVerticalDots(stops: List<Stop>) {

        val container = binding.dotsLayoutV   // ✅ CORRECT TARGET
        container.removeAllViews()

        if (stops.isEmpty()) return

        binding.routeDotsContainerV.isVisible = true

        stops.forEach { _ ->

            val dot = View(requireContext()).apply {
                background = ContextCompat.getDrawable(
                    requireContext(), R.drawable.bg_route_dot
                )
            }

            val lp = LinearLayout.LayoutParams(6.dp, 6.dp).apply {
                topMargin = 12.dp
                bottomMargin = 12.dp
            }

            container.addView(dot, lp)
        }

        container.invalidate()
        container.requestLayout()
    }

    val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun clearRouteAndResetUI() {
        appViewModel.clearRoute()

        binding.routeHeader.isVisible = false
        binding.sheetContent.stopName.isVisible = true
        binding.sheetContent.routeHeader.isVisible = false

        binding.sheetContent.stopName.apply {
            hint = getString(R.string.where_to)
            isEnabled = true
            clearFocus()
        }

        binding.bottomSheet.isVisible = false
        binding.moreDetails.isVisible = false
        binding.start.isVisible = true
        binding.viewDetails.isVisible = true

        binding.stopsRecycler.adapter = null
        stopsVisible = false

        routePolyline?.remove()
        routePolyline = null

        routeMarkers.forEach { it.remove() }
        routeMarkers.clear()

        currentPreviewMarker = null
        binding.stopInfoCard.visibility = View.GONE

        appViewModel.stops.value?.let { stops ->
            allStops = stops
            stopsAdapter.submitList(stops)
        }

        // Ensure search fields are cleared and focus is reset
        binding.sheetContent.stopName.setText("")
        binding.sheetContent.toText.setText("")
        binding.sheetContent.fromText.setText("")
    }

    // ---------------- UI ----------------

    private fun openChooseStopSheetForEdit(mode: SelectionMode) {
        appViewModel.selectionMode.value = mode

        val sheet = ChooseStopBottomSheetFragment()
        sheet.show(parentFragmentManager, "ChooseStopBottomSheet")
    }

    private fun setupRouteHeaderEditClicks() {
        // Start location click -> edit FROM
        binding.fromText.addPressEffect {
            openChooseStopSheetForEdit(SelectionMode.FROM)
        }

        // Destination click -> edit TO
        binding.toText.addPressEffect {
            openChooseStopSheetForEdit(SelectionMode.TO)
        }
    }

    private fun showRouteHeader() {
        val from = appViewModel.fromStop.value ?: return
        val to = appViewModel.toStop.value ?: return

        binding.sheetContent.root.isVisible = false
        clearSheetFocus()
        binding.dimOverlay.isVisible = false
        binding.routeHeader.visibility = View.VISIBLE

        binding.fromText.text = from.name
        binding.toText.text = to.name
    }

    private fun showNavigationUI() {
        binding.clear.isVisible = true
        binding.navigationBar.isVisible = true
        binding.navFooter.isVisible = true
        binding.bottomSheet.isVisible = false
        binding.sheetContent.root.isVisible = false
        clearSheetFocus()
        binding.dimOverlay.isVisible = false
        binding.routeHeader.isVisible = false
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

    // ---------------- CLICKS ----------------
    @SuppressLint("ClickableViewAccessibility")
    private fun setupClicks() {
        binding.navEtaBubble.addPressEffect {
            if (!isDestinationReached) return@addPressEffect
            showDestinationReachedSheetOnce()
        }

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

        binding.sheetContent.stopName.setOnFocusChangeListener { _, hasFocus ->
            if (ignoreFocusChanges) return@setOnFocusChangeListener
            if (hasFocus && sheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                appViewModel.openChooseStop.value = true
                appViewModel.sheetSource.value = SheetSource.HOME
                sheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                binding.sheetContent.recyclerView.isVisible = true
            }
        }

        binding.hideShow.addPressEffect {

            stopsVisible = !stopsVisible

            val recycler = binding.stopsRecycler
            val offset = 16.dp.toFloat()

            if (stopsVisible) {

                recycler.apply {
                    visibility = View.VISIBLE
                    alpha = 0f
                    translationY = offset

                    animate().alpha(1f).translationY(0f).setDuration(260)
                        .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                }

            } else {

                recycler.animate().alpha(0f).translationY(offset).setDuration(200)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction {
                        recycler.visibility = View.GONE
                    }.start()
            }

            binding.hideShow.text = if (stopsVisible) getString(R.string.hide_stops)
            else getString(R.string.show_stops)

            binding.hideShow.setCompoundDrawablesWithIntrinsicBounds(
                if (stopsVisible) R.drawable.ic_up else R.drawable.ic_drop_down, 0, 0, 0
            )
        }

        binding.showMore.addPressEffect {
            val route = appViewModel.selectedRoute.value ?: return@addPressEffect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DeparturesBottomSheet(
                    buildAllDepartures(route), route.start
                ).show(parentFragmentManager, "departures")
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

        binding.sheetContent.stopName.setOnDrawableEndClick {
            appViewModel.openChooseStop.value = true
            appViewModel.sheetSource.value = SheetSource.HOME
            binding.sheetContent.recyclerView.isVisible = true
            sheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            appViewModel.selectNearestStop()
        }

        binding.currentLocation.addPressEffect {
            if (appViewModel.navigationMode.value == NavigationMode.NAVIGATING) {
                isCameraFollowing = true
                appViewModel.userLocation.value?.let { updateNavCamera(it) }
            } else {
                appViewModel.userLocation.value?.let { loc ->
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(loc.latitude, loc.longitude), 15f
                        )
                    )
                }
            }
        }

        binding.viewDetails.addPressEffect {
            if (isExpanded) {
                collapseDetails()
            } else {
                expandDetails()
            }
        }

        binding.start.addPressEffect {
            appViewModel.startNavigation()
        }

        binding.startNavigation.addPressEffect {
            appViewModel.startNavigation()
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    when (appViewModel.navigationMode.value) {

                        NavigationMode.NAVIGATING -> {
                            safeExitNavigation()
                        }

                        NavigationMode.ROUTE_PREVIEW -> {
                            appViewModel.exitNavigation()
                        }

                        NavigationMode.IDLE -> {
                            isEnabled = false
                            requireActivity().onBackPressed()
                        }

                        null -> requireActivity().onBackPressed()
                    }
                }
            })

        binding.bottomSheet.setOnTouchListener { _, event ->

            if (!isExpanded) return@setOnTouchListener false

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    lastTranslationY = binding.moreDetails.translationY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY

                    if (dy > 0) { // swipe DOWN only
                        binding.moreDetails.translationY = lastTranslationY + dy
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {

                    // threshold: how much user must drag
                    if (binding.moreDetails.translationY > binding.moreDetails.height / 4) {
                        collapseDetails()
                    } else {
                        // snap back
                        binding.moreDetails.animate().translationY(0f).setDuration(150).start()
                    }
                    true
                }

                else -> false
            }
        }

        binding.close.addPressEffect {
            clearSheetFocus()
            sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            stopLiveLocationUpdates()
            clearRouteAndResetUI()
            appViewModel.exitNavigation()
        }

        binding.clear.addPressEffect {
            safeExitNavigation()

        }

        binding.navPrev.addPressEffect {
            val idx = (appViewModel.navStopIndex.value ?: 0) - 1
            appViewModel.setNavStopIndex(idx)
        }
        binding.navNext.addPressEffect {
            val idx = (appViewModel.navStopIndex.value ?: 0) + 1
            appViewModel.setNavStopIndex(idx)
        }
        setupNavFooterSwipe()
    }

    // ---------------- LOCATION ----------------

    private fun startLiveLocationUpdates() {
        if (isNavUpdatesRunning) return
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 800L)
            .setMinUpdateDistanceMeters(2f).build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                appViewModel.userLocation.value = loc
            }
        }

        locationCallback = cb
        locationClient.requestLocationUpdates(request, cb, requireActivity().mainLooper)
        isNavUpdatesRunning = true
    }

    private fun stopLiveLocationUpdates() {
        if (!isNavUpdatesRunning) return
        val cb = locationCallback ?: return
        locationClient.removeLocationUpdates(cb)
        locationCallback = null
        isNavUpdatesRunning = false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupNavFooterSwipe() {

        var downX = 0f
        var downY = 0f
        val threshold = 60.dp

        binding.navCard.setOnTouchListener { v, event ->

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY

                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_UP -> {

                    val dx = event.rawX - downX
                    val dy = abs(event.rawY - downY)

                    if (abs(dx) > threshold && dy < threshold) {

                        if (dx < 0) {
                            appViewModel.setNavStopIndex(
                                (appViewModel.navStopIndex.value ?: 0) + 1
                            )
                        } else {
                            appViewModel.setNavStopIndex(
                                (appViewModel.navStopIndex.value ?: 0) - 1
                            )
                        }
                        return@setOnTouchListener true
                    }

                    false
                }

                else -> false
            }
        }
    }

    private fun updateNavFooter(route: Route, loc: Location?) {

        val currentIdx = reachedStopIndex.coerceIn(0, route.stops.lastIndex)
        val nextIdx = (currentIdx + 1).coerceAtMost(route.stops.lastIndex)

        val current = route.stops[currentIdx]
        val next = route.stops.getOrNull(nextIdx)

        binding.navStopTitle.text = current.name
        binding.navNextStop.text = next?.name ?: getString(R.string.reached_destination)

        val stationsLeft = route.stops.lastIndex - currentIdx

        val nextStopMin = timeToNextStopMinutes(route, currentIdx, loc)
        val destinationMin = timeToDestinationMinutes(route, currentIdx, loc)

        val kmLeft = routeDistanceLeftKm(route, currentIdx, loc)
        val (distValue, distUnit) = formatDistance(kmLeft)

        binding.navMeta.text = getString(
            R.string.stations_left, stationsLeft
        ) + " • $distValue $distUnit • " + getString(R.string.min_to_destination, destinationMin)

        binding.navEtaBubble.text = if (isDestinationReached) "Done" else "${nextStopMin}\nmin"

        renderNavDots(route.stops.size, currentIdx)

        binding.navPrev.alpha = if (currentIdx <= 0) 0.35f else 1f
        binding.navNext.alpha = if (currentIdx >= route.stops.lastIndex) 0.35f else 1f
    }

    private fun initRouteSpeed(route: Route) {
        val totalMinutes = route.total_ride_time.toDouble()

        val totalKm = route.ride_distance.replace("km", "", true).trim().toDoubleOrNull() ?: return

        minutesPerMeter = totalMinutes / (totalKm * 1000.0)
    }

    private fun renderNavDots(total: Int, activeIndex: Int) {
        binding.navDots.removeAllViews()
        if (total <= 1) return

        repeat(total) { i ->
            val dot = View(requireContext()).apply {
                background = ContextCompat.getDrawable(
                    requireContext(), if (i == activeIndex) R.drawable.dot_active
                    else R.drawable.dot_inactive
                )
            }

            val lp = LinearLayout.LayoutParams(8.dp, 8.dp)
            lp.marginStart = 6.dp
            lp.marginEnd = 6.dp
            binding.navDots.addView(dot, lp)
        }
    }

    private fun focusStopOnMap(route: Route, index: Int) {
        val stop = route.stops.getOrNull(index) ?: return
        val marker = routeMarkers.firstOrNull { (it.tag as? Stop)?.id == stop.id }
        if (marker != null) {
            currentPreviewMarker = marker
            showStopBubble(marker, stop)
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(marker.position))
        }
    }

    private fun updateReachedStopLogic(route: Route, loc: Location) {

        if (route.stops.isEmpty()) return

        // current "target stop" is NEXT stop
        val nextIndex = (reachedStopIndex + 1).coerceAtMost(route.stops.lastIndex)
        val nextStop = route.stops.getOrNull(nextIndex) ?: return

        val distToNext = distanceMeters(
            loc.latitude, loc.longitude, nextStop.latitude, nextStop.longitude
        )

        // ✅ ARRIVED at next stop
        if (!isInsideStopZone && distToNext <= ARRIVE_RADIUS) {

            isInsideStopZone = true
            reachedStopIndex = nextIndex
            lastReachedStopId = nextStop.id

            // update navStopIndex ONLY when reached
            appViewModel.setNavStopIndex(reachedStopIndex)

            // show stopInfoCard ONLY when reached
            showReachedStopCard(route, nextStop, reachedStopIndex)
            return
        }

        // ✅ LEFT the reached stop area (hide stop card)
        if (isInsideStopZone && distToNext >= EXIT_RADIUS) {
            isInsideStopZone = false
            hideStopCard()
        }
    }

    private fun showReachedStopCard(route: Route, stop: Stop, index: Int) {

        binding.stopTitle.text = stop.name
        binding.stopSubTitle.text = getString(R.string.heading_towards, route.end)

        val kmLeft = routeDistanceLeftKm(route, index, appViewModel.userLocation.value)
        val (value, unit) = formatDistance(kmLeft)

        binding.stopMeta.text =
            "${index + 1} of ${route.stops.size} stations • $value $unit • 4–5 mins"

        binding.stopInfoCard.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(180).start()
        }
    }

    private fun hideStopCard() {
        binding.stopInfoCard.animate().alpha(0f).setDuration(150).withEndAction {
            binding.stopInfoCard.visibility = View.GONE
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun recenterNavInstant() {

        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        locationClient.lastLocation.addOnSuccessListener { loc ->
            loc ?: return@addOnSuccessListener

            // update marker + camera instantly
            appViewModel.userLocation.value = loc
            updateNavUserMarker(loc)

            // force camera follow instantly
            isCameraFollowing = true
            applyNavCameraOffset()
            updateNavCamera(loc)
        }
    }

    private fun routeDistanceLeftKm(
        route: Route, fromIndex: Int, loc: Location?
    ): Double {
        if (route.stops.size <= 1) return 0.0
        val idx = fromIndex.coerceIn(0, route.stops.lastIndex)
        var meters = 0.0

        val cur = route.stops[idx]
        meters += if (loc != null) {
            distanceMeters(loc.latitude, loc.longitude, cur.latitude, cur.longitude)
        } else 0.0

        for (i in idx until route.stops.lastIndex) {
            val a = route.stops[i]
            val b = route.stops[i + 1]
            meters += distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        return meters / 1000.0
    }

    private fun timeToDestinationMinutes(
        route: Route, index: Int, userLoc: Location?
    ): Int {

        if (minutesPerMeter == 0.0) return 0
        val stops = route.stops
        var meters = 0.0

        // current → next
        if (userLoc != null && index < stops.lastIndex) {
            meters += distanceMeters(
                userLoc.latitude,
                userLoc.longitude,
                stops[index + 1].latitude,
                stops[index + 1].longitude
            )
        }

        // remaining stops
        for (i in index + 1 until stops.lastIndex) {
            meters += distanceMeters(
                stops[i].latitude, stops[i].longitude, stops[i + 1].latitude, stops[i + 1].longitude
            )
        }

        return (meters * minutesPerMeter).roundToInt().coerceAtLeast(1)
    }

    private fun timeToNextStopMinutes(
        route: Route, index: Int, userLoc: Location?
    ): Int {

        if (minutesPerMeter == 0.0) return 0
        val stops = route.stops
        if (index >= stops.lastIndex) return 0

        val next = stops[index + 1]

        val meters = if (userLoc != null) {
            distanceMeters(
                userLoc.latitude, userLoc.longitude, next.latitude, next.longitude
            )
        } else {
            distanceMeters(
                stops[index].latitude, stops[index].longitude, next.latitude, next.longitude
            )
        }

        return (meters * minutesPerMeter).roundToInt().coerceAtLeast(1)
    }


    private fun distanceMeters(
        lat1: Double, lon1: Double, lat2: Double, lon2: Double
    ): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a =
            sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(
                dLon / 2
            ).pow(2.0)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
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

    @SuppressLint("MissingPermission")
    private fun enableLocation() {
        googleMap.isMyLocationEnabled = true
        locationClient.lastLocation.addOnSuccessListener { loc ->
            loc ?: return@addOnSuccessListener
            appViewModel.userLocation.value = loc
//            val stops = appViewModel.selectedRoute.value?.stops
//            if (!stops.isNullOrEmpty()) {
//                resetNavCameraView()
//            } else {
//                googleMap.moveCamera(
//                    CameraUpdateFactory.newLatLngZoom(
//                        LatLng(loc.latitude, loc.longitude), 15f
//                    )
//                )
//            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getRemainingBuses(
        start: LocalTime, last: String, intervalMin: Int
    ): List<LocalTime> {

        val lastTime = LocalTime.parse(last)
        val list = mutableListOf<LocalTime>()
        var t = start

        while (!t.isAfter(lastTime)) {
            list.add(t)
            t = t.plusMinutes(intervalMin.toLong())
        }
        return list
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getNextBusTime(
        first: String, last: String, intervalMin: Int
    ): LocalTime? {

        val now = LocalTime.now()
        var time = LocalTime.parse(first)

        val lastTime = LocalTime.parse(last)

        while (time.isBefore(now)) {
            time = time.plusMinutes(intervalMin.toLong())
            if (time.isAfter(lastTime)) return null
        }

        return time
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildFullDaySchedule(route: Route): List<LocalTime> {

        val first = parseApiTime(route.first_ride_time)
        val last = parseApiTime(route.last_ride_time)

        val times = mutableListOf<LocalTime>()
        var t = first

        while (!t.isAfter(last)) {
            times.add(t)
            t = t.plusMinutes(route.leave_bus.toLong())
        }

        return times
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildAllDepartures(route: Route): List<Departure> {
        val now = LocalTime.now()

        return buildFullDaySchedule(route).map { time ->
            Departure(
                routeName = route.name,
                destination = route.end,
                scheduledTime = time,
                minutesDiff = java.time.Duration.between(now, time).toMinutes()
            )
        }.sortedBy { it.scheduledTime }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseApiTime(time: String): LocalTime {

        return when {
            time.contains("AM", true) || time.contains("PM", true) -> {
                LocalTime.parse(
                    time, DateTimeFormatter.ofPattern("h:mm a")
                )
            }

            time.count { it == ':' } == 2 -> {
                LocalTime.parse(
                    time, DateTimeFormatter.ofPattern("HH:mm:ss")
                )
            }

            else -> {
                LocalTime.parse(
                    time, DateTimeFormatter.ofPattern("HH:mm")
                )
            }
        }
    }

    private fun updateViewDetailsUI(expanded: Boolean) {
        if (expanded) {
            binding.viewDetails.text = getString(R.string.hide_details)
            binding.viewDetails.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, R.drawable.ic_up, 0
            )
        } else {
            binding.viewDetails.text = getString(R.string.view_details)
            binding.viewDetails.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, R.drawable.ic_drop_down, 0
            )
        }
    }

    private fun expandDetails() {

        if (isExpanded) return

        binding.start.isVisible = false

        binding.moreDetails.apply {
            visibility = View.VISIBLE
            alpha = 0f
            translationY = 24.dp.toFloat()

            animate().alpha(1f).translationY(0f).setDuration(260)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }

        updateViewDetailsUI(true)
        isExpanded = true
    }

    private fun collapseDetails() {

        if (!isExpanded) return

        binding.moreDetails.animate().alpha(0f).translationY(24.dp.toFloat()).setDuration(200)
            .setInterpolator(android.view.animation.AccelerateInterpolator()).withEndAction {
                binding.moreDetails.visibility = View.GONE
                binding.moreDetails.translationY = 0f
                binding.start.isVisible = true
                updateViewDetailsUI(false)
            }.start()

        isExpanded = false
    }

    private fun clearAllRoutesTracks() {
        allRoutePolylines.forEach { it.remove() }
        allRoutePolylines.clear()

        stationLabelMarkers.values.forEach { it.isVisible = false }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun formatTime(time: LocalTime): String {
        val format = runBlocking {
            PreferencesDataStoreHelper(requireContext()).getFirstPreference(
                PreferenceDataStoreKeysConstants.TIME_FORMAT, "12"
            )
        }

        val formatter = if (format == "12") {
            DateTimeFormatter.ofPattern("hh:mm a")
        } else {
            DateTimeFormatter.ofPattern("HH:mm")
        }

        return time.format(formatter)
    }

    private fun formatDistance(km: Double): Pair<String, String> {
        val unit = runBlocking {
            PreferencesDataStoreHelper(requireContext()).getFirstPreference(
                PreferenceDataStoreKeysConstants.DISTANCE_UNIT, "km"
            )
        }

        return if (unit == "mi") {
            val miles = km * 0.621371
            String.format("%.1f", miles) to getString(R.string.mi)
        } else {
            String.format("%.1f", km) to getString(R.string.km)
        }
    }

    private fun startNavCameraMode() {
        isCameraFollowing = true

        // Smooth camera + navigation feel
        googleMap.uiSettings.isRotateGesturesEnabled = false
        googleMap.uiSettings.isTiltGesturesEnabled = true
    }

    private fun stopNavCameraMode() {
        isCameraFollowing = false
        lastBearing = 0f
    }

    private fun getScaledNavIcon(scale: Float = 1.35f): com.google.android.gms.maps.model.BitmapDescriptor {

        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_nav_arrow)!!
        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)

        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        val scaledBitmap =
            bitmap.scale((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())

        return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(scaledBitmap)
    }

    private fun updateNavUserMarker(loc: Location) {
        if (!isMapReady) return

        val pos = LatLng(loc.latitude, loc.longitude)

        val icon = getScaledNavIcon(2.0f)

        val speed = if (loc.hasSpeed()) loc.speed else 0f

        val markerBearing = when {
            // ✅ If moving, use GPS bearing (most stable)
            loc.hasBearing() && speed >= 1.2f -> {
                lastStableHeading = loc.bearing
                loc.bearing
            }

            // ✅ If NOT moving, keep last stable heading (freeze rotation)
            else -> {
                lastStableHeading
            }
        }

        if (isUsingPipLayout && navUserMarker != null) {
            navUserMarker?.remove()
            navUserMarker = null
        }

        if (navUserMarker == null) {
            navUserMarker = googleMap.addMarker(
                MarkerOptions().position(pos).icon(icon).anchor(0.5f, 0.5f).flat(true)
                    .rotation(markerBearing).zIndex(1000f)
            )
        } else {
            navUserMarker?.position = pos
            navUserMarker?.rotation = markerBearing
        }

        navUserMarker?.isVisible = true
    }

    @SuppressLint("MissingPermission")
    private fun enableDefaultMyLocationDot() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun disableDefaultMyLocationDot() {
        try {
            googleMap.isMyLocationEnabled = false
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun updateNavCamera(loc: Location) {
        if (
            !isCameraFollowing ||
            !isMapReady ||
            appViewModel.navigationMode.value != NavigationMode.NAVIGATING
        ) return

        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastCameraUpdateTime < CAMERA_UPDATE_INTERVAL_MS) return

        val userLatLng = LatLng(loc.latitude, loc.longitude)

        val bearing = getStableBearing(loc)

        val metersAhead = if (isUsingPipLayout) 25.0 else 180.0

        val cameraTarget = getTargetWithOffset(
            origin = userLatLng, bearing = bearing, metersForward = metersAhead
        )

        // ---- JITTER CONTROL ----
        val targetChangedEnough = lastCameraTarget?.let {
            distanceBetween(it, cameraTarget) > CAMERA_TARGET_THRESHOLD_METERS
        } ?: true

        val bearingChangedEnough = angleDiff(bearing, lastCameraBearing) >= CAMERA_BEARING_THRESHOLD

        // If neither changed meaningfully, do nothing
        if (!targetChangedEnough && !bearingChangedEnough) return

        lastCameraUpdateTime = now
        lastCameraBearing = bearing
        lastCameraTarget = cameraTarget

        val cameraPosition =
            CameraPosition.Builder().target(cameraTarget).zoom(17.8f).tilt(65f).bearing(bearing)
                .build()

        googleMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(cameraPosition), 260, null
        )
    }

    private fun drawUpcomingRouteOnly(fullPoints: List<LatLng>, userLoc: Location) {

        if (fullPoints.size < 2) return

        // find closest polyline point to user
        var bestIndex = 0
        var bestDist = Double.MAX_VALUE

        for (i in fullPoints.indices) {
            val p = fullPoints[i]
            val d = distanceMeters(userLoc.latitude, userLoc.longitude, p.latitude, p.longitude)
            if (d < bestDist) {
                bestDist = d
                bestIndex = i
            }
        }

        val upcoming = fullPoints.drop(bestIndex)

        if (upcoming.size < 2) return

        routePolyline?.remove()

        routePolyline = googleMap.addPolyline(
            PolylineOptions().addAll(upcoming).color("#FC843A".toColorInt()).width(12f).zIndex(10f)
                .jointType(JointType.ROUND).startCap(RoundCap()).endCap(RoundCap())
        )
    }

    private fun applyNavCameraOffset() {
        if (!isMapReady) return

        // Post to ensure views are measured
        val rootView = if (isUsingPipLayout) pipBinding.root else binding.root
        rootView.post {
            if (!isMapReady) return@post

            val cameraPosition = CameraPosition.Builder().target(
                LatLng(
                    appViewModel.userLocation.value!!.latitude,
                    appViewModel.userLocation.value!!.longitude
                )
            ).zoom(17.8f)  // Adjust zoom level as needed
                .tilt(65f)  // Maintain a tilt for better navigation visibility
                .bearing(lastBearing)  // Keep the bearing based on the user's orientation
                .build()

            googleMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(cameraPosition), 150, null
            )
        }
    }

    private fun resetMapPadding() {
        googleMap.setPadding(0, 0, 0, 0)
    }

    private fun angleDiff(a: Float, b: Float): Float {
        val diff = ((a - b + 540) % 360) - 180
        return kotlin.math.abs(diff)
    }

    private fun distanceBetween(a: LatLng, b: LatLng): Double {
        return distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
    }

    private fun getStableBearing(loc: Location): Float {
        val speed = if (loc.hasSpeed()) loc.speed else 0f

        return if (loc.hasBearing() && speed >= 1.2f) {
            // moving → GPS bearing best
            lastStableHeading = loc.bearing
            loc.bearing
        } else {
            // not moving → sensor heading but already smoothed in compass
            lastStableHeading = smoothAngle(lastStableHeading, currentHeading, 0.08f)
            lastStableHeading
        }
    }

    private fun startCompass() {
        sensorManager =
            requireContext().getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager

        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnet = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        accelValues = event.values.clone()
                        hasAccel = true
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        magnetValues = event.values.clone()
                        hasMagnet = true
                    }
                }

                if (hasAccel && hasMagnet) {
                    val r = FloatArray(9)
                    val i = FloatArray(9)

                    val success = SensorManager.getRotationMatrix(r, i, accelValues, magnetValues)
                    if (success) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(r, orientation)

                        val azimuthRad = orientation[0]
                        var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()

                        if (azimuthDeg < 0) azimuthDeg += 360f

                        // smooth heading
                        currentHeading = smoothAngle(currentHeading, azimuthDeg, 0.15f)
                        // ✅ throttle + threshold to remove shiver
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastSensorUpdateTime >= 80) { // ~25fps
                            lastSensorUpdateTime = now

                            val diff =
                                kotlin.math.abs(((currentHeading - lastMarkerRotation + 540) % 360) - 180)
                            if (diff >= 6f) { // ignore tiny jitter < 2°
                                lastMarkerRotation = currentHeading
                                navUserMarker?.rotation = currentHeading
                            }
                            val loc = appViewModel.userLocation.value
                            if (loc != null && appViewModel.navigationMode.value == NavigationMode.NAVIGATING) {
                                updateNavCamera(loc) // will be throttled + thresholded
                            }
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        navSensorListener = listener

        accel?.let { sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        magnet?.let { sensorManager?.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
    }

    private fun stopCompass() {
        navSensorListener?.let { sensorManager?.unregisterListener(it) }
        navSensorListener = null
        sensorManager = null
    }

    private fun smoothAngle(current: Float, target: Float, factor: Float): Float {
        var diff = (target - current + 540) % 360 - 180
        return (current + diff * factor + 360) % 360
    }

    private fun createNavNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                NAV_CHANNEL_ID, "Navigation Alerts", android.app.NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Arrival alerts during navigation"

            val manager =
                requireContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun ensureNotificationPermission(onGranted: () -> Unit) {

        // Android 12 and below -> no runtime permission
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onGranted()
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            onGranted()
        } else {
            onNotificationPermissionGranted = onGranted
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Wrapper that ensures notification permission before starting the foreground service.
     */
    private fun startNavServiceWithPermission() {
        ensureNotificationPermission {
            startNavForegroundService()
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendArrivalNotificationOnce() {

        if (arrivalSoonNotified) return

        ensureNotificationPermission {

            // user granted -> now send
            arrivalSoonNotified = true

            val notif =
                androidx.core.app.NotificationCompat.Builder(requireContext(), NAV_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_nav_arrow).setContentTitle("Almost there!")
                    .setContentText("Arriving at destination in 1 minute")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true).build()

            if (canNotify()) {
                NotificationManagerCompat.from(requireContext()).notify(NAV_NOTIFY_ID, notif)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun openAppNotificationSettings() {
        val intent = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
        }
        startActivity(intent)
    }

    private fun checkDestinationArrival(route: Route, loc: Location?) {

        if (loc == null) return
        if (arrivalSoonNotified) return

        val currentIdx = reachedStopIndex.coerceIn(0, route.stops.lastIndex)

        val minsLeft = timeToDestinationMinutes(route, currentIdx, loc)

        if (minsLeft <= DESTINATION_NOTIFY_MINUTES) {
            sendArrivalNotificationOnce()
        }
    }

    private fun showDestinationReachedSheetOnce() {

        if (destinationSheetShown) return
        destinationSheetShown = true

        DestinationReachedBottomSheet {
            // Done button pressed inside sheet
            safeExitNavigation()
        }.show(parentFragmentManager, "DestinationReachedBottomSheet")
    }

    @SuppressLint("MissingPermission")
    private fun sendReachedDestinationNotificationOnce() {

        if (reachedNotified) return

        ensureNotificationPermission {

            reachedNotified = true

            val notif =
                androidx.core.app.NotificationCompat.Builder(requireContext(), NAV_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_nav_arrow).setContentTitle("Destination Reached")
                    .setContentText("You have arrived at your destination.")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true).build()

            if (canNotify()) {
                NotificationManagerCompat.from(requireContext())
                    .notify(NAV_NOTIFY_ID + 1, notif) // different ID
            }
        }
    }

    private fun checkIfDestinationReached(route: Route, loc: Location?) {
        if (loc == null) return
        if (isDestinationReached) return

        val lastStop = route.stops.lastOrNull() ?: return

        val dist = distanceMeters(
            loc.latitude, loc.longitude, lastStop.latitude, lastStop.longitude
        )

        if (dist <= 45.0) { // adjust radius
            isDestinationReached = true

            if (isUsingPipLayout) {
                pipBinding.navEtaBubblePip.text = "Done"
            } else {
                binding.navEtaBubble.text = "Done"
            }
            sendReachedDestinationNotificationOnce()
            showDestinationReachedSheetOnce()
        }
    }

    private fun getTargetWithOffset(
        origin: LatLng, bearing: Float, metersForward: Double
    ): LatLng {
        val earthRadius = 6378137.0
        val d = metersForward / earthRadius
        val brng = Math.toRadians(bearing.toDouble())

        val lat1 = Math.toRadians(origin.latitude)
        val lon1 = Math.toRadians(origin.longitude)

        val lat2 = asin(
            sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(brng)
        )

        val lon2 = lon1 + atan2(
            sin(brng) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2)
        )

        return LatLng(
            Math.toDegrees(lat2), Math.toDegrees(lon2)
        )
    }

    private fun resetNavCameraView() {
        if (!isMapReady) return

        val fromStop = appViewModel.fromStop.value
        val toStop = appViewModel.toStop.value
        val stops = appViewModel.selectedRoute.value?.stops

        // If route is selected, always use moveCamera
        if (fromStop != null && toStop != null && !stops.isNullOrEmpty()) {
            moveCamera()
            return
        }


        val allStops = appViewModel.selectedRoute.value?.stops
            ?: allStops.takeIf { it.isNotEmpty() }

        if (allStops.isNullOrEmpty()) return

        val bounds = LatLngBounds.builder().apply {
            allStops.forEach { include(LatLng(it.latitude, it.longitude)) }
        }.build()

        val mapView = if (isUsingPipLayout) pipBinding.mapView else binding.mapView
        mapView.post {
            if (!isMapReady) return@post
            googleMap.setPadding(
                0,
                0,
                (mapView.width * 0.25f).toInt(),
                (mapView.height * 0.20f).toInt()
            )
            googleMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(bounds.center)
                        .zoom(11.5f)
                        .tilt(0f)
                        .bearing(55f)
                        .build()
                ), 350, null
            )
        }
    }

    private fun keepScreenAwake(enable: Boolean) {
        val window = requireActivity().window
        if (enable) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun startNavForegroundService() {
        val route = appViewModel.selectedRoute.value ?: return
        val lastStop = route.stops.lastOrNull() ?: return

        val intent = Intent(requireContext(), NavigationLocationService::class.java).apply {
            action = NavigationLocationService.ACTION_START
            putExtra(NavigationLocationService.EXTRA_DEST_LAT, lastStop.latitude)
            putExtra(NavigationLocationService.EXTRA_DEST_LNG, lastStop.longitude)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }
    }

    private fun canNotify(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireActivity(), Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun stopNavForegroundService() {
        val intent = Intent(requireContext(), NavigationLocationService::class.java).apply {
            action = NavigationLocationService.ACTION_STOP
        }
        requireContext().startService(intent)
    }

    // --- PiP UI helpers ---
    // When using PiP layout, we don't need enterPipUi/exitPipUi
    // The separate layout handles everything

    override fun onResume() {
        super.onResume()
        val mapView = if (isUsingPipLayout) pipBinding.mapView else binding.mapView
        mapView.onResume()

        val filter = android.content.IntentFilter().apply {
            addAction(NavigationLocationService.ACTION_LOCATION)
            addAction(NavigationLocationService.ACTION_NAV_EXITED)
        }
        ContextCompat.registerReceiver(
            requireContext(), navLocationReceiver, filter, ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        val mapView = if (isUsingPipLayout) pipBinding.mapView else binding.mapView
        mapView.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        val mapView = if (isUsingPipLayout) pipBinding.mapView else binding.mapView
        mapView.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val mapView = if (isUsingPipLayout) pipBinding?.mapView else binding?.mapView
        mapView?.onDestroy()
        _binding = null
        _pipBinding = null
        rootContainer = null
    }

    // ---------------- DRAWABLE CLICK ----------------

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

    private fun hideStationLabels() {
        stationLabelMarkers.values.forEach { it.isVisible = false }
    }

    private fun showStationLabels() {
        if (!stationLabelsCreated) return
        stationLabelMarkers.values.forEach { it.isVisible = true }
    }

    private fun safeExitNavigation() {
        stopLiveLocationUpdates()
        stopCompass()
        isCameraFollowing = false

        lastCameraUpdateTime = Long.MAX_VALUE

        navUserMarker?.remove()
        navUserMarker = null

        routePolyline?.remove()
        routePolyline = null

        appViewModel.enterRoutePreview()
    }
}
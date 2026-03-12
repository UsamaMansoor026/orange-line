package com.webscare.orangeline.ui.route

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.webscare.orangeline.AppViewModel
import com.webscare.orangeline.R
import com.webscare.orangeline.common.Utils.addPressEffect
import com.webscare.orangeline.databinding.BottomSheetFragmentStopPreviewBinding
import com.webscare.orangeline.domain.model.Stop
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StopPreviewBottomSheet(
    private val selectedStop: Stop, private val selectedIndex: Int
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFragmentStopPreviewBinding? = null
    private val binding get() = _binding!!
    private val appViewModel: AppViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFragmentStopPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // Observe the SAME route that RouteFragment is using
        appViewModel.activeRoute.observe(viewLifecycleOwner) { route ->
            if (route == null) return@observe

            val stops = route.stops
            val totalDistanceKm = route.ride_distance.toDouble()
            val totalTimeMin = route.total_ride_time.toDouble()

            val selectedIdx = stops.indexOfFirst { it.id == selectedStop.id }
            if (selectedIdx == -1) return@observe

            val startStop = stops.first()
            val endStop = stops.last()

            // --- Distances along real route ---
            val totalStops = stops.size - 1

            val progress = selectedIdx.toDouble() / totalStops.toDouble()

            val distStartToSelected = totalDistanceKm * progress
            val distSelectedToEnd = totalDistanceKm - distStartToSelected

            val timeStartToSelected = (totalTimeMin * progress).toInt()
            val timeSelectedToEnd = (totalTimeMin - timeStartToSelected).toInt()

            // --- Bind UI ---
            binding.stationName.text = selectedStop.name

            binding.startStop.text = startStop.name
            binding.endStop.text = endStop.name

            binding.routeTimeFromStart.text = "$timeStartToSelected mins"
            binding.distanceFromStart.text = "${distStartToSelected.toInt()} km"

            binding.routeTimeFromEnd.text = "$timeSelectedToEnd mins"
            binding.distanceFromEnd.text = "${distSelectedToEnd.toInt()} km"

            // Station number
            binding.stationsIcon.text = "Station No: ${selectedIdx + 1}"

            // Underground / Elevated
            binding.stationFloor.text =
                if (selectedStop.is_underground) "Underground" else "Elevated"
        }

        binding.start.addPressEffect {
            appViewModel.selectStop(selectedStop)
            appViewModel.openChooseStop.value = true
            findNavController().navigate(R.id.homeFragment)
            dismiss()
        }

        binding.close.addPressEffect { dismiss() }

    }

    private fun forceImmersiveMode() {
        dialog?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.apply {
                    hide(
                        WindowInsets.Type.statusBars()
                                or WindowInsets.Type.navigationBars()
                    )
                    systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        }
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.apply {
            setDimAmount(0.45f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(false)
            }

            decorView.setOnSystemUiVisibilityChangeListener {
                forceImmersiveMode()
            }
            setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        }

        val bottomSheet =
            dialog?.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            ) ?: return

        ViewCompat.setOnApplyWindowInsetsListener(bottomSheet) { _, _ ->
            WindowInsetsCompat.CONSUMED
        }

        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        bottomSheet.requestLayout()
        bottomSheet.setPadding(0, 0, 0, 0)

        forceImmersiveMode()
    }

    override fun onResume() {
        super.onResume()
        forceImmersiveMode()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

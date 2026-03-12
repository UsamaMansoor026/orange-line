package com.webscare.orangeline.ui.home

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.webscare.orangeline.R
import com.webscare.orangeline.common.datastore.PreferenceDataStoreKeysConstants
import com.webscare.orangeline.common.datastore.PreferencesDataStoreHelper
import com.webscare.orangeline.databinding.BottomSheetFragmentDeparturesBinding
import com.webscare.orangeline.domain.model.Departure
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class DeparturesBottomSheet(
    private val departures: List<Departure>, private val routeStartName: String
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFragmentDeparturesBinding? = null
    private val binding get() = _binding!!

    private var showPrevious = false
    private lateinit var allDepartures: List<Departure>
    private lateinit var adapter: DeparturesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFragmentDeparturesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        allDepartures = departures
        binding.subTitle.text = getString(R.string.from_route, routeStartName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            adapter = DeparturesAdapter(
                filteredDepartures(), getTimeFormatter()
            )
            binding.recyclerView.adapter = adapter
        }

        binding.showNew.setOnClickListener {

            showPrevious = !showPrevious

            binding.showNew.text = if (showPrevious) getString(R.string.show_upcoming_departures)
            else getString(R.string.show_previous_departures)

            adapter.updateData(filteredDepartures())
        }

        binding.close.setOnClickListener {
            dismiss()
        }
    }

    private fun filteredDepartures(): List<Departure> {
        return if (showPrevious) {
            allDepartures.filter { it.minutesDiff < 0 }.sortedByDescending { it.scheduledTime }
        } else {
            allDepartures.filter { it.minutesDiff >= 0 }.sortedBy { it.scheduledTime }
        }
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getTimeFormatter(): DateTimeFormatter {

        val format = runBlocking {
            PreferencesDataStoreHelper(requireContext()).getFirstPreference(
                    PreferenceDataStoreKeysConstants.TIME_FORMAT, "12"
                )
        }

        return if (format == "12") {
            DateTimeFormatter.ofPattern("hh:mm a")
        } else {
            DateTimeFormatter.ofPattern("HH:mm")
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
        bottomSheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
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

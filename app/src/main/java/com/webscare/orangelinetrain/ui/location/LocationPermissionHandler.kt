package com.webscare.orangelinetrain.ui.location

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.snackbar.Snackbar
import com.webscare.orangelinetrain.R

class LocationPermissionHandler(
    private val fragment: Fragment,
    private val onPermissionGranted: () -> Unit
) : DefaultLifecycleObserver {

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    init {
        fragment.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        requestPermissionLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                onPermissionGranted()
            } else {
                handlePermissionDenied()
            }
        }
    }

    fun requestPermission() {
        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            fragment.requireActivity(),
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (shouldShowRationale) {
            // User denied before but didn't check "Don't ask again" - show dialog again
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // Either first time or user checked "Don't ask again"
            // Check if permission is already granted
            val hasPermission = ActivityCompat.checkSelfPermission(
                fragment.requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                onPermissionGranted()
            } else {
                // First time asking or "Don't ask again" was checked
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun handlePermissionDenied() {
        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            fragment.requireActivity(),
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (!shouldShowRationale) {
            // User checked "Don't ask again" - guide them to settings
            showSettingsSnackbar()
        } else {
            Snackbar.make(
                fragment.requireView(),
                "Location permission is required to track speed",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun showSettingsSnackbar() {
        val rootView = fragment.requireActivity().findViewById<View>(android.R.id.content)
        val snackbar = Snackbar.make(
            rootView,
            "Enable location permission in Settings to use this feature",
            Snackbar.LENGTH_LONG
        )

        // Action click
        snackbar.setAction("Settings") {
            openAppSettings()
        }

        // Get snackbar view
        val snackbarView = snackbar.view

        snackbarView.setBackgroundColor(
            androidx.core.content.ContextCompat.getColor(
                fragment.requireContext(),
                R.color.dark_gray
            )
        )

        // OR ensure no Material tint interference
        snackbarView.backgroundTintList = null
        // ⚪ Message text color
        val textView = snackbarView
            .findViewById<TextView>(com.google.android.material.R.id.snackbar_text)

        textView.setTextColor(
            fragment.requireContext().getColor(R.color.white)
        )

        // 🟡 Action text color
        snackbar.setActionTextColor(
            fragment.requireContext().getColor(R.color.app_color)
        )

        snackbar.show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", fragment.requireActivity().packageName, null)
        }
        fragment.startActivity(intent)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        fragment.lifecycle.removeObserver(this)
    }
}
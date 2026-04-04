package com.webscare.orangelinelahore.ui.route

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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.webscare.orangelinelahore.common.Utils.addPressEffect
import com.webscare.orangelinelahore.databinding.BottomsheetDestinationReachedBinding

class DestinationReachedBottomSheet(
    private val onDoneClick: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomsheetDestinationReachedBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetDestinationReachedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        binding.done.addPressEffect {
            dismiss()
            onDoneClick()
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

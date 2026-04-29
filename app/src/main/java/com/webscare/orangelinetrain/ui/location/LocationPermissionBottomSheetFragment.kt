package com.webscare.orangelinetrain.ui.location

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.webscare.orangelinetrain.common.Utils.addPressEffect
import com.webscare.orangelinetrain.databinding.FragmentLocationPermissionBottomSheetBinding

class LocationPermissionBottomSheetFragment(   var onAccessLocation: (() -> Unit)? = null) : BottomSheetDialogFragment() {

    private var _binding: FragmentLocationPermissionBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationPermissionBottomSheetBinding.inflate(
            inflater, container, false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpClicks()


    }

    private fun setUpClicks() {
        binding.btnNotNow.addPressEffect {
            dismiss()
        }

        binding.closeBtnSheet.addPressEffect {
            dismiss()
        }

        binding.btnEnable.addPressEffect {
            dismiss()
            onAccessLocation?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
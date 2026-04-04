package com.webscare.orangelinelahore.ui.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.webscare.orangelinelahore.R
import com.webscare.orangelinelahore.common.datastore.PreferenceDataStoreKeysConstants
import com.webscare.orangelinelahore.common.datastore.PreferencesDataStoreHelper
import com.webscare.orangelinelahore.databinding.FragmentSplashBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashFragment : Fragment() {
    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!
    private lateinit var dataStoreHelper: PreferencesDataStoreHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dataStoreHelper = PreferencesDataStoreHelper(requireContext())

        setupInitialState()
        playSplashAnimation()

//        MainScope().launch {
//            delay(3000)
//            val firstRun = dataStoreHelper.getFirstPreference(PreferenceDataStoreKeysConstants.FIRST_RUN, true)
//            if (!firstRun) {
//                findNavController().navigate(R.id.homeFragment, null, navOptions { popUpTo(R.id.splashFragment) { inclusive = true } })
//            } else {
//                findNavController().navigate(R.id.onboardingFragment, null, navOptions { popUpTo(R.id.splashFragment) { inclusive = true } })
//            }
//        }

//        The issue with MainScope().launch is that it’s a "fire and forget" scope. If your Service restarts the Activity,
//        a new SplashFragment is created, but the old one might still have a timer running in the background.
//        When that timer hits 3 seconds, it triggers a navigation call on a Fragment that is already being destroyed,
//        leading to your crashes and the "double open" flicker.

        viewLifecycleOwner.lifecycleScope.launch {
            val isExitingNav = requireActivity().intent.getBooleanExtra("EXIT_NAV", false)

            if (isExitingNav) {
                navigateToNext(isFastTrack = true)
            } else {
                delay(3000)
                navigateToNext(isFastTrack = false)
            }
        }


    }

    private suspend fun navigateToNext(isFastTrack: Boolean) {
        // Standard safety check
        if (!isAdded) return

        val firstRun = dataStoreHelper.getFirstPreference(PreferenceDataStoreKeysConstants.FIRST_RUN, true)

        val destination = if (!firstRun) R.id.homeFragment else R.id.onboardingFragment

        findNavController().navigate(destination, null, navOptions {
            popUpTo(R.id.splashFragment) { inclusive = true }

            // If we are resetting, ensure we don't animate the transition
            if (isFastTrack) {
                anim {
                    enter = 0
                    exit = 0
                }
            }
        })
    }

    private fun playSplashAnimation() {

        // 🔥 LOGO ANIMATION (SLOW + SMOOTH)
        val logoFade = ObjectAnimator.ofFloat(binding.drawableSplash, View.ALPHA, 0f, 1f).apply {
            duration = 900
            startDelay = 150
            interpolator = DecelerateInterpolator()
        }

        val logoScaleX = ObjectAnimator.ofFloat(binding.drawableSplash, View.SCALE_X, 0.85f, 1f).apply {
            duration = 1400
            interpolator = DecelerateInterpolator()
        }

        val logoScaleY = ObjectAnimator.ofFloat(binding.drawableSplash, View.SCALE_Y, 0.85f, 1f).apply {
            duration = 1400
            interpolator = DecelerateInterpolator()
        }

        val logoRise = ObjectAnimator.ofFloat(binding.drawableSplash, View.TRANSLATION_Y, 70f, 0f).apply {
            duration = 1400
            interpolator = AccelerateDecelerateInterpolator()
        }

        val logoSet = AnimatorSet().apply {
            playTogether(logoFade, logoScaleX, logoScaleY, logoRise)
        }

        // ✅ TEXT (same as before)
        val titleFade = ObjectAnimator.ofFloat(binding.title, View.ALPHA, 0f, 1f).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
        }
        val titleRise = ObjectAnimator.ofFloat(binding.title, View.TRANSLATION_Y, 25f, 0f).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
        }
        val titleSet = AnimatorSet().apply { playTogether(titleFade, titleRise) }

        val subFade = ObjectAnimator.ofFloat(binding.subTitle, View.ALPHA, 0f, 1f).apply {
            duration = 550
            interpolator = DecelerateInterpolator()
        }
        val subRise = ObjectAnimator.ofFloat(binding.subTitle, View.TRANSLATION_Y, 25f, 0f).apply {
            duration = 550
            interpolator = DecelerateInterpolator()
        }
        val subSet = AnimatorSet().apply { playTogether(subFade, subRise) }

        // Sequence
        AnimatorSet().apply {
            playSequentially(logoSet, titleSet, subSet)
            start()
        }
    }

    private fun setupInitialState() {
        binding.drawableSplash.alpha = 0f
        binding.drawableSplash.scaleX = 0.85f
        binding.drawableSplash.scaleY = 0.85f
        binding.drawableSplash.translationY = 70f

        binding.title.alpha = 0f
        binding.title.translationY = 25f

        binding.subTitle.alpha = 0f
        binding.subTitle.translationY = 25f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
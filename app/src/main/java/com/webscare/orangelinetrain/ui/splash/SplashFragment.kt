package com.webscare.orangelinetrain.ui.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.webscare.orangelinetrain.R
import com.webscare.orangelinetrain.common.datastore.PreferenceDataStoreKeysConstants
import com.webscare.orangelinetrain.common.datastore.PreferencesDataStoreHelper
import com.webscare.orangelinetrain.databinding.FragmentSplashBinding
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
        if (!isAdded) return
        val firstRun = dataStoreHelper.getFirstPreference(PreferenceDataStoreKeysConstants.FIRST_RUN, true)
        val destination = if (!firstRun) R.id.homeFragment else R.id.onboardingFragment
        findNavController().navigate(destination, null, navOptions {
            popUpTo(R.id.splashFragment) { inclusive = true }
            if (isFastTrack) { anim { enter = 0; exit = 0 } }
        })
    }

    /**
     * Hides the original TextView, creates a sibling LinearLayout of per-character
     * TextViews with matching style, then animates each one rising + fading in.
     */
    private fun animateCharByChar(
        source: TextView,
        staggerMs: Long = 60L,
        startDelayMs: Long = 0L,
        charSpacingDp: Float = 0f
    ) {
        val text = source.text.toString()

        // Hide the original so it doesn't show double
        source.visibility = View.INVISIBLE

        // Build a horizontal container to replace it visually
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = source.layoutParams
        }

        val parent = source.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(source)
        parent.addView(container, index + 1)

        val spacingPx = (charSpacingDp * resources.displayMetrics.density).toInt()
        text.forEachIndexed { i, char ->
            val charView = TextView(requireContext()).apply {
                this.text = char.toString()
                // Convert px → sp correctly
                textSize = source.textSize / resources.displayMetrics.scaledDensity
                setTextColor(source.currentTextColor)
                typeface = source.typeface
                includeFontPadding = source.includeFontPadding
                if (spacingPx > 0) setPadding(0, 0, spacingPx, 0)
                // Start invisible and 40px below final position
                alpha = 0f
                translationY = 40f
            }
            container.addView(charView)

            val delay = startDelayMs + i * staggerMs

            ObjectAnimator.ofFloat(charView, View.ALPHA, 0f, 1f).apply {
                duration = 350
                startDelay = delay
                interpolator = DecelerateInterpolator()
                start()
            }
            ObjectAnimator.ofFloat(charView, View.TRANSLATION_Y, 40f, 0f).apply {
                duration = 350
                startDelay = delay
                interpolator = DecelerateInterpolator()
                start()
            }
        }
    }

    private fun playSplashAnimation() {
        // Logo rises
        ObjectAnimator.ofFloat(binding.drawableSplash, View.TRANSLATION_Y, 250f, 0f).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
            start()
        }

        // Title starts after logo (800ms), subtitle after title finishes
        val titleCharCount = binding.title.text.length
        val titleStartMs = 800L
        val subTitleStartMs = titleStartMs + titleCharCount * 60L + 200L

        animateCharByChar(binding.title,    staggerMs = 60L, startDelayMs = titleStartMs)
        animateCharByChar(binding.subTitle, staggerMs = 45L, startDelayMs = subTitleStartMs, charSpacingDp = 4f)
    }

    private fun setupInitialState() {
        binding.drawableSplash.alpha = 1f
        binding.drawableSplash.scaleX = 1f
        binding.drawableSplash.scaleY = 1f
        binding.drawableSplash.translationY = 250f

        binding.title.alpha = 1f
        binding.title.translationY = 0f
        binding.subTitle.alpha = 1f
        binding.subTitle.translationY = 0f
        binding.subTitle.letterSpacing = 0.4f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
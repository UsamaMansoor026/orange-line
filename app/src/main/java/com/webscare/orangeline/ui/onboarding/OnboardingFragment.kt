package com.webscare.orangeline.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.webscare.orangeline.R
import com.webscare.orangeline.common.datastore.PreferenceDataStoreKeysConstants
import com.webscare.orangeline.common.datastore.PreferencesDataStoreHelper
import com.webscare.orangeline.databinding.FragmentOnboardingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch



@AndroidEntryPoint
class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!
    private lateinit var dataStoreHelper: PreferencesDataStoreHelper

    private val images =
        listOf(R.drawable.ic_onboarding_1, R.drawable.ic_onboarding_2, R.drawable.ic_onboarding_3)
    private val titles = listOf(
        "Select Your Nearest Orange Line Station",
        "Track Your Train Live",
        "See Your Upcoming Train Times"
    )
    private val descriptions = listOf(
        "See only active stations and start tracking trains from your location instantly.",
        "See your train’s journey from start to end, with progress and travel time.",
        "Check departure and arrival times for the same train over the next hours or days."
    )

    private lateinit var dots: MutableList<View>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize DataStoreHelper
        dataStoreHelper = PreferencesDataStoreHelper(requireContext())

        // Set up the ViewPager2 and adapter
        val adapter = OnboardingAdapter(images, titles, descriptions) { position ->
            if (position == 2) {
                // On last page, mark onboarding as complete and navigate
                MainScope().launch {
                    dataStoreHelper.putPreference(
                        PreferenceDataStoreKeysConstants.FIRST_RUN, false
                    )
                }
                navigateToHomeScreen()
            } else {
                binding.viewPager.currentItem += 1
            }
        }

        binding.viewPager.adapter = adapter

        // Initialize dots and add them to the layout
        setupDotIndicators()

        // Handle Skip button
        binding.btnSkip.setOnClickListener {
            MainScope().launch {
                dataStoreHelper.putPreference(PreferenceDataStoreKeysConstants.FIRST_RUN, false)
            }
            navigateToHomeScreen()
        }

        // Update dot indicators when page changes
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateDotIndicators(position)
            }
        })
    }

    private fun setupDotIndicators() {
        // Create dots based on the number of onboarding pages
        dots = mutableListOf()
        val dotCount = images.size

        // Dynamically add dot indicators
        for (i in 0 until dotCount) {
            val dot = View(requireContext()).apply {
                val dotSize = dpToPx(12)
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    // Add margin to create gap between dots
                    setMargins(dpToPx(8), 0, 0, 0)  // Adjust this margin for desired spacing between dots
                }
                setBackgroundResource(R.drawable.dot_inactive)  // Set the default inactive state
            }
            dots.add(dot)
            binding.dotsIndicator.addView(dot)
        }

        // Set the first dot as active
        updateDotIndicators(0)
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }


    private fun updateDotIndicators(position: Int) {
        for (i in dots.indices) {
            dots[i].setBackgroundResource(if (i == position) R.drawable.dot_active else R.drawable.dot_inactive)
        }
    }

    private fun navigateToHomeScreen() {
        findNavController().navigate(R.id.homeFragment)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.webscare.orangelinetrain.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.webscare.orangelinetrain.R
import com.webscare.orangelinetrain.common.Utils.addPressEffect
import com.webscare.orangelinetrain.common.datastore.PreferenceDataStoreKeysConstants
import com.webscare.orangelinetrain.common.datastore.PreferencesDataStoreHelper
import com.webscare.orangelinetrain.databinding.FragmentSettingsBinding
import com.webscare.orangelinetrain.databinding.PopupTwoOptionBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val darkModeListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
        if (!buttonView.isPressed) return@OnCheckedChangeListener
        val targetMode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            lifecycleScope.launch {
                dataStore.putPreference(PreferenceDataStoreKeysConstants.DARK_MODE, isChecked)
                AppCompatDelegate.setDefaultNightMode(targetMode)
            }
        }
    }

    private lateinit var dataStore: PreferencesDataStoreHelper
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dataStore = PreferencesDataStoreHelper(requireContext())

        loadSavedPreferences()

        setEvents()
    }

    private fun loadSavedPreferences() {
        viewLifecycleOwner.lifecycleScope.launch {

            val langCode = dataStore.getFirstPreference(
                PreferenceDataStoreKeysConstants.LANGUAGE, "en"
            )

            binding.language.text = if (langCode == "ur") getString(R.string.urdu)
            else getString(R.string.english)

            val distance = dataStore.getFirstPreference(
                PreferenceDataStoreKeysConstants.DISTANCE_UNIT, "km"
            )
            binding.distance.text = if (distance == "mi") getString(R.string.miles)
            else getString(R.string.kilometers)

            val time = dataStore.getFirstPreference(
                PreferenceDataStoreKeysConstants.TIME_FORMAT, "24"
            )
            binding.time.text = if (time == "12") getString(R.string.hour_12)
            else getString(R.string.hour_24)

            val isDark = dataStore.getFirstPreference(
                PreferenceDataStoreKeysConstants.DARK_MODE, false
            )
            binding.darkModeSwitch.setOnCheckedChangeListener(null)
            binding.darkModeSwitch.isChecked = isDark
            binding.darkModeSwitch.setOnCheckedChangeListener(darkModeListener)
        }
    }

    private fun showTwoOptionPopup(
        anchor: View, title1: String, title2: String, selectedIndex: Int, onSelect: (Int) -> Unit
    ) {
        val popupBinding = PopupTwoOptionBinding.inflate(LayoutInflater.from(requireActivity()))
        val popupWidth = (150 * requireActivity().resources.displayMetrics.density).toInt()

        val popupWindow = PopupWindow(
            popupBinding.root, popupWidth, LinearLayout.LayoutParams.WRAP_CONTENT, true
        )

        popupBinding.option1.text = title1
        popupBinding.option2.text = title2

        popupBinding.check1.visibility = if (selectedIndex == 0) View.VISIBLE else View.INVISIBLE
        popupBinding.check2.visibility = if (selectedIndex == 1) View.VISIBLE else View.INVISIBLE

        popupWindow.elevation = 20f
        popupWindow.isOutsideTouchable = true

        popupBinding.option1Click.addPressEffect {
            onSelect(0)
            popupWindow.dismiss()
        }

        popupBinding.option2Click.addPressEffect {
            onSelect(1)
            popupWindow.dismiss()
        }

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)

        val yOffset = 8

        val isRTL =
            resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

        val xOffset = if (isRTL) {
            -popupWidth + anchor.paddingStart
        } else {
            anchor.width - popupWidth - anchor.paddingEnd
        }

        popupWindow.showAsDropDown(anchor, xOffset, yOffset)
    }

    private fun setEvents() {
        setVersionInfo()

        binding.darkModeSwitch.setOnCheckedChangeListener(darkModeListener)

        binding.languageRow.setOnClickListener {

            val currentLang = runBlocking {
                dataStore.getFirstPreference(
                    PreferenceDataStoreKeysConstants.LANGUAGE, "en"
                )
            }

            showTwoOptionPopup(
                anchor = it,
                title1 = getString(R.string.english),
                title2 = getString(R.string.urdu),
                selectedIndex = if (currentLang == "en") 0 else 1
            ) { index ->

                val newLang = if (index == 0) "en" else "ur"

                lifecycleScope.launch {
                    dataStore.putPreference(
                        PreferenceDataStoreKeysConstants.LANGUAGE, newLang
                    )

                    // 🔥 THIS IS CORRECT
                    requireActivity().recreate()
                }
            }
        }

        binding.distanceRow.setOnClickListener {
            showTwoOptionPopup(
                anchor = it,
                title1 = getString(R.string.kilometers),
                title2 = getString(R.string.miles),
                selectedIndex = if (binding.distance.text == getString(R.string.kilometers)) 0 else 1
            ) { index ->

                val unit = if (index == 0) "km" else "mi"

                binding.distance.text = if (unit == "km") getString(R.string.kilometers)
                else getString(R.string.miles)

                lifecycleScope.launch {
                    dataStore.putPreference(
                        PreferenceDataStoreKeysConstants.DISTANCE_UNIT, unit
                    )
                }
            }
        }

        binding.timeRow.setOnClickListener {
            showTwoOptionPopup(
                anchor = it,
                title1 = getString(R.string.hour_24),
                title2 = getString(R.string.hour_12),
                selectedIndex = if (binding.time.text == getString(R.string.hour_24)) 0 else 1
            ) { index ->

                val format = if (index == 0) "24" else "12"

                binding.time.text = if (format == "24") getString(R.string.hour_24)
                else getString(R.string.hour_12)

                lifecycleScope.launch {
                    dataStore.putPreference(
                        PreferenceDataStoreKeysConstants.TIME_FORMAT, format
                    )
                }
            }
        }

        // ⭐ Rate Us
        binding.rateUsRow.addPressEffect {
            openPlayStore()
        }

        // 📤 Share App
        binding.shareRow.addPressEffect {
            shareApp()
        }

        // 💬 Feedback
        binding.feedbackRow.addPressEffect {
            sendEmail(
                email = "support@yourapp.com", subject = "App Feedback"
            )
        }

        // 📞 Contact Us
        binding.contactRow.addPressEffect {
            sendEmail(
                email = "support@yourapp.com", subject = "Contact Support"
            )
        }

        // ℹ️ About Us
        binding.aboutRow.addPressEffect {
            // Navigate or show dialog
            // findNavController().navigate(R.id.aboutFragment)
        }

        // 🧩 More Apps
        binding.moreAppsRow.addPressEffect {
            openDeveloperPage()
        }

        // 🔒 Privacy Policy
        binding.privacyPolicyRow.addPressEffect {
            openWeb("http://webscare.com/privacy-policy-orangelinetrain/")
        }
    }

    // ---------------- HELPERS ----------------

    private fun openPlayStore() {
        val uri = "market://details?id=${requireContext().packageName}".toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openDeveloperPage() {
        val uri = "https://play.google.com/store/apps/developer?id=YourDeveloperName".toUri()
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Check out this app:\nhttps://play.google.com/store/apps/details?id=${requireContext().packageName}"
            )
        }
        startActivity(Intent.createChooser(intent, "Share App"))
    }

    private fun sendEmail(email: String, subject: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:$email".toUri()
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        startActivity(intent)
    }

    private fun openWeb(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    private fun setVersionInfo() {
        val versionName = try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) {
            "—"
        }
        binding.versionInfo.text = "Version $versionName"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

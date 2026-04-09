package com.webscare.orangelinetrain.ui.route

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.webscare.orangelinetrain.AppViewModel
import com.webscare.orangelinetrain.common.enums.SelectionMode
import com.webscare.orangelinetrain.databinding.FragmentChooseStopBinding
import com.webscare.orangelinetrain.domain.model.Stop
import com.webscare.orangelinetrain.ui.home.StopsAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChooseStopBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentChooseStopBinding? = null
    private val binding get() = _binding!!

    private val appViewModel: AppViewModel by activityViewModels()

    private lateinit var stopsAdapter: StopsAdapter
    private var allStops: List<Stop> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChooseStopBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerView) { view, insets ->

            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val bottomPadding = maxOf(imeInsets.bottom, systemInsets.bottom)

            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                bottomPadding
            )

            insets
        }

        setupRecycler()
        setupObservers()
        setupListeners()
        binding.root.post {
            clearInitialFocus()
        }
    }

    private fun showSameStopSnackBar() {
        Snackbar.make(
            binding.root, "Start and destination cannot be the same", Snackbar.LENGTH_SHORT
        ).setAnchorView(binding.root).show()
    }

    private fun setupRecycler() {
        stopsAdapter = StopsAdapter { stop ->
            // Prevent same stop selection
            if (appViewModel.selectionMode.value == SelectionMode.FROM &&
                appViewModel.toStop.value == stop
            ) {
                showSameStopSnackBar()
                return@StopsAdapter
            }

            if (appViewModel.selectionMode.value == SelectionMode.TO &&
                appViewModel.fromStop.value == stop
            ) {
                showSameStopSnackBar()
                return@StopsAdapter
            }

            appViewModel.selectStop(stop)
        }

        binding.recyclerView.adapter = stopsAdapter
        binding.recyclerView.isVisible = true
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

    private fun setupObservers() {
        appViewModel.filteredStops.observe(viewLifecycleOwner) { stops ->
            allStops = stops
            stopsAdapter.submitList(stops)
        }

        appViewModel.fromStop.observe(viewLifecycleOwner) { stop ->
            binding.fromText.setText(stop?.name ?: "")
            updateHeaderVisibility()
        }

        appViewModel.toStop.observe(viewLifecycleOwner) { stop ->
            binding.toText.setText(stop?.name ?: "")
            updateHeaderVisibility()
        }
    }

    private fun updateHeaderVisibility() {
        val fromSelected = appViewModel.fromStop.value != null
        val toSelected = appViewModel.toStop.value != null

        if (fromSelected && toSelected) {
            binding.routeHeader.isVisible = true
            binding.stopName.isVisible = false
            clearInitialFocus()
        } else {
            binding.routeHeader.isVisible = true
            binding.stopName.isVisible = false

            binding.root.postDelayed({
                if (!toSelected) {
                    appViewModel.selectionMode.value = SelectionMode.TO
                    binding.toText.requestFocus()
                } else if (!fromSelected) {
                    appViewModel.selectionMode.value = SelectionMode.FROM
                    binding.fromText.requestFocus()
                }
            }, 200)
        }
    }

    private fun setupListeners() {

        // When user focuses From -> selectionMode FROM
        binding.fromText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                appViewModel.setSelectionMode(SelectionMode.FROM)
                filterStops(binding.fromText.text.toString())
            }
        }

        // When user focuses To -> selectionMode TO
        binding.toText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                appViewModel.setSelectionMode(SelectionMode.TO)
                filterStops(binding.toText.text.toString())
            }
        }

        binding.fromText.setOnClickListener {
            appViewModel.selectionMode.value = SelectionMode.FROM
            binding.fromText.requestFocus()
            binding.fromText.setSelection(binding.fromText.text?.length ?: 0)
            showKeyboard(binding.fromText)
            filterStops(binding.fromText.text.toString())
        }

        // ✅ Tap To -> focus + keyboard + selectionMode TO
        binding.toText.setOnClickListener {
            appViewModel.selectionMode.value = SelectionMode.TO
            binding.toText.requestFocus()
            binding.toText.setSelection(binding.toText.text?.length ?: 0)
            showKeyboard(binding.toText)
            filterStops(binding.toText.text.toString())
        }

        // 🔥 Only filter when that field is focused
        binding.fromText.addTextChangedListener {
            if (binding.fromText.hasFocus()) filterStops(it.toString())
        }

        binding.toText.addTextChangedListener {
            if (binding.toText.hasFocus()) filterStops(it.toString())
        }

        binding.btnSwap.setOnClickListener {
            val from = appViewModel.fromStop.value
            val to = appViewModel.toStop.value
            appViewModel.fromStop.value = to
            appViewModel.toStop.value = from

            clearInitialFocus()
        }
    }

    private fun filterStops(query: String) {
        val filtered = if (query.isEmpty()) allStops
        else allStops.filter { it.name.contains(query, ignoreCase = true) }
        stopsAdapter.submitList(filtered)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun showKeyboard(view: View) {
        view.post {
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun clearInitialFocus() {
        binding.fromText.clearFocus()
        binding.toText.clearFocus()
        hideKeyboard()
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.apply {
            setDimAmount(0.45f)
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
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

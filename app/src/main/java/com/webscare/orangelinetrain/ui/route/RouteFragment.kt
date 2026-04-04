package com.webscare.orangelinetrain.ui.route

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.webscare.orangelinetrain.AppViewModel
import com.webscare.orangelinetrain.databinding.FragmentRouteBinding
import com.webscare.orangelinetrain.domain.model.Stop
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RouteFragment : Fragment() {

    private var _binding: FragmentRouteBinding? = null
    private val binding get() = _binding!!

    private val appViewModel: AppViewModel by activityViewModels()
    private lateinit var adapter: RouteStopsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        setupRecycler()
        observeRoute()
    }

    private fun setupRecycler() {
        adapter = RouteStopsAdapter(emptyList()) { stop, index ->
            showStopSheet(stop, index)
        }

        binding.stopsRecycler.adapter = adapter
    }

    private fun showStopSheet(stop: Stop, index: Int) {
        StopPreviewBottomSheet(stop, index)
            .show(childFragmentManager, "stopPreview")
    }

    private fun observeRoute() {

        appViewModel.routeStopsForTimeline.observe(viewLifecycleOwner) { stops ->
            if (stops.isEmpty()) return@observe
            adapter.submitList(stops)
        }

        appViewModel.routeMeta.observe(viewLifecycleOwner) { meta ->
            if (meta == null) return@observe

            binding.startStation.text = meta.start
            binding.endStation.text = meta.end
            binding.stations.text = meta.stopCount.toString()
            binding.distance.text = "${meta.distance} km"
            binding.routeTime.text = "${meta.duration} min"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

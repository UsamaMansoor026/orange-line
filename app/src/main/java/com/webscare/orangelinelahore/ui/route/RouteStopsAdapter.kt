package com.webscare.orangelinelahore.ui.route

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.webscare.orangelinelahore.common.Utils.addPressEffect
import com.webscare.orangelinelahore.databinding.ItemRouteStopNameBinding
import com.webscare.orangelinelahore.domain.model.Stop

class RouteStopsAdapter(
    private var stops: List<Stop>,
    private val onStopClick: (Stop, Int) -> Unit
) : RecyclerView.Adapter<RouteStopsAdapter.StopVH>() {

    inner class StopVH(
        private val binding: ItemRouteStopNameBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(stop: Stop, position: Int) {

            val isFirst = position == 0
            val isLast = position == stops.lastIndex

            binding.leftText.visibility = View.GONE
            binding.rightText.visibility = View.GONE

            // DEFAULT: timeline visible
            binding.dot.visibility = View.VISIBLE

            when {
                isFirst || isLast -> {
                    // show NOTHING except dot
                    // spacing + dot stays
                }

                position % 2 == 0 -> {
                    binding.leftText.text = stop.name
                    binding.leftText.visibility = View.VISIBLE
                }

                else -> {
                    binding.rightText.text = stop.name
                    binding.rightText.visibility = View.VISIBLE
                }
            }

            binding.root.addPressEffect {
                onStopClick(stop, position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StopVH {
        val binding = ItemRouteStopNameBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StopVH(binding)
    }

    override fun onBindViewHolder(holder: StopVH, position: Int) {
        holder.bind(stops[position], position)
    }

    override fun getItemCount(): Int = stops.size

    fun submitList(newStops: List<Stop>) {
        stops = newStops
        notifyDataSetChanged()
    }
}

package com.webscare.orangelinelahore.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.webscare.orangelinelahore.databinding.ItemRouteStopBinding
import com.webscare.orangelinelahore.domain.model.Stop

class RouteStopsAdapter(
    private val stops: List<Stop>
) : RecyclerView.Adapter<RouteStopsAdapter.VH>() {

    inner class VH(
        val binding: ItemRouteStopBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRouteStopBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val stop = stops[position]

        holder.binding.stopName.text = stop.name
    }

    override fun getItemCount(): Int = stops.size
}

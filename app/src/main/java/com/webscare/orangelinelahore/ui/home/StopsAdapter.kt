package com.webscare.orangelinelahore.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.webscare.orangelinelahore.databinding.LayoutStopItemBinding
import com.webscare.orangelinelahore.domain.model.Stop

class StopsAdapter(
    private val onClick: (Stop) -> Unit
) : ListAdapter<Stop, StopsAdapter.StopVH>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StopVH {
        val binding = LayoutStopItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StopVH(binding)
    }

    override fun onBindViewHolder(holder: StopVH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StopVH(
        private val binding: LayoutStopItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(stop: Stop) {
            binding.stopName.text = stop.name

            binding.root.setOnClickListener {
                onClick(stop)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Stop>() {
            override fun areItemsTheSame(old: Stop, new: Stop) =
                old.id == new.id

            override fun areContentsTheSame(old: Stop, new: Stop) =
                old == new
        }
    }
}
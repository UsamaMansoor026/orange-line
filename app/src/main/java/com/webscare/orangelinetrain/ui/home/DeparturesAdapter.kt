package com.webscare.orangelinetrain.ui.home

import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.webscare.orangelinetrain.databinding.LayoutDepartureItemBinding
import com.webscare.orangelinetrain.domain.model.Departure
import com.webscare.orangelinetrain.domain.model.TimeDisplay
import java.time.format.DateTimeFormatter

class DeparturesAdapter(
    private var items: List<Departure>,
    private val timeFormatter: DateTimeFormatter
) : RecyclerView.Adapter<DeparturesAdapter.VH>() {

    inner class VH(val binding: LayoutDepartureItemBinding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = LayoutDepartureItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = items[position]

        holder.binding.routeName.text = d.routeName
        holder.binding.startStop.text = d.destination
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            holder.binding.timeValue.text =
                d.scheduledTime.format(timeFormatter)
        }

        val timeUi = formatTimeForUI(d.minutesDiff)

        holder.binding.timeLeft.text = timeUi.value
        holder.binding.timeLeftUnit.text = timeUi.unit
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Departure>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun formatTimeForUI(minutes: Long): TimeDisplay {

        val absMin = kotlin.math.abs(minutes)

        val hrs = absMin / 60
        val mins = absMin % 60

        return if (hrs > 0) {
            TimeDisplay(
                value = "$hrs:${mins.toString().padStart(2, '0')}",
                unit = "hr"
            )
        } else {
            TimeDisplay(
                value = absMin.toString(),
                unit = "min"
            )
        }
    }
}

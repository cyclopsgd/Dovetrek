package com.scout.routeplanner.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.scout.routeplanner.data.RouteLeg
import com.scout.routeplanner.databinding.ItemRouteLegBinding

class RouteLegAdapter : ListAdapter<RouteLeg, RouteLegAdapter.ViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<RouteLeg>() {
        override fun areItemsTheSame(oldItem: RouteLeg, newItem: RouteLeg) =
            oldItem.leg == newItem.leg
        override fun areContentsTheSame(oldItem: RouteLeg, newItem: RouteLeg) =
            oldItem == newItem
    }

    class ViewHolder(val binding: ItemRouteLegBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRouteLegBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val leg = getItem(position)
        val darkText = Color.parseColor("#333333")
        with(holder.binding) {
            textLeg.text = leg.leg.toString()
            textFrom.text = leg.from
            textTo.text = leg.to
            textDist.text = "%.2f".format(leg.distance)
            textHeight.text = "%.0f".format(leg.heightGain)
            textTravel.text = "%.1f".format(leg.travelMin)
            textArrival.text = leg.arrival
            textSlot.text = leg.timeSlot
            textOpen.text = if (leg.isOpen) "Yes" else "No"
            textOpen.setTextColor(if (leg.isOpen) Color.parseColor("#2E7D32") else Color.RED)
            textWait.text = "%.1f".format(leg.waitMin)
            textDepart.text = leg.depart
            textCumulative.text = "%.0f".format(leg.cumulativeMin)

            // Set dark text color on all columns except Open (which has its own color)
            for (tv in listOf(textLeg, textFrom, textTo, textDist, textHeight,
                              textTravel, textArrival, textSlot, textWait,
                              textDepart, textCumulative)) {
                tv.setTextColor(darkText)
            }

            // Alternate row colors
            root.setBackgroundColor(
                if (position % 2 == 0) Color.parseColor("#F5F5F5") else Color.WHITE
            )
        }
    }
}

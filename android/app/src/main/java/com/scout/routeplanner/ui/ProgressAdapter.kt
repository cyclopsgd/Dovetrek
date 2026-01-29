package com.scout.routeplanner.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.scout.routeplanner.databinding.ItemProgressCheckpointBinding

data class ProgressItem(
    val name: String,
    val scheduledArrival: String,
    val isVisited: Boolean,
    val isCurrent: Boolean,
    val isStart: Boolean
)

class ProgressAdapter(
    private val onCheckChanged: (String, Boolean) -> Unit
) : ListAdapter<ProgressItem, ProgressAdapter.ViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<ProgressItem>() {
        override fun areItemsTheSame(oldItem: ProgressItem, newItem: ProgressItem) =
            oldItem.name == newItem.name
        override fun areContentsTheSame(oldItem: ProgressItem, newItem: ProgressItem) =
            oldItem == newItem
    }

    class ViewHolder(val binding: ItemProgressCheckpointBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProgressCheckpointBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        with(holder.binding) {
            textCheckpointName.text = item.name
            textScheduledTime.text = item.scheduledArrival

            // Set checkbox state without triggering listener
            checkboxVisited.setOnCheckedChangeListener(null)
            checkboxVisited.isChecked = item.isVisited
            checkboxVisited.isEnabled = !item.isStart  // Can't uncheck Start

            checkboxVisited.setOnCheckedChangeListener { _, isChecked ->
                if (!item.isStart) {
                    onCheckChanged(item.name, isChecked)
                }
            }

            // Status text
            when {
                item.isStart -> {
                    textStatus.text = "Start"
                    textStatus.setTextColor(Color.parseColor("#1976D2"))
                }
                item.isVisited -> {
                    textStatus.text = "Visited"
                    textStatus.setTextColor(Color.parseColor("#2E7D32"))
                }
                item.isCurrent -> {
                    textStatus.text = "Current"
                    textStatus.setTextColor(Color.parseColor("#F57C00"))
                }
                else -> {
                    textStatus.text = "Pending"
                    textStatus.setTextColor(Color.parseColor("#757575"))
                }
            }

            // Styling based on state
            val textColor = when {
                item.isVisited -> Color.parseColor("#9E9E9E")  // Grey out visited
                item.isCurrent -> Color.parseColor("#333333")
                else -> Color.parseColor("#333333")
            }
            textCheckpointName.setTextColor(textColor)
            textScheduledTime.setTextColor(textColor)

            // Bold for current checkpoint
            textCheckpointName.setTypeface(null, if (item.isCurrent) Typeface.BOLD else Typeface.NORMAL)

            // Row background
            val bgColor = when {
                item.isCurrent -> Color.parseColor("#FFF3E0")  // Light orange highlight
                position % 2 == 0 -> Color.parseColor("#F5F5F5")
                else -> Color.WHITE
            }
            root.setBackgroundColor(bgColor)
        }
    }
}

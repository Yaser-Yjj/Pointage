// SafeZoneAdapter.kt
package com.lykos.pointage.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lykos.pointage.R
import com.lykos.pointage.data.model.data.SafeZoneData

class SafeZoneAdapter(
    private val onZoneClick: (SafeZoneData, Int) -> Unit
) : RecyclerView.Adapter<SafeZoneAdapter.ViewHolder>() {

    private val zones: MutableList<SafeZoneData> = mutableListOf()
    var selectedPosition = -1
        set(value) {
            val oldPos = field
            field = value
            oldPos.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
            value.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        }

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newZones: List<SafeZoneData>, lastSelectedId: String? = null) {
        zones.clear()
        zones.addAll(newZones)
        notifyDataSetChanged()
        lastSelectedId?.let { id ->
            lastSelectedId.toIntOrNull()?.let { intId ->
                val index = zones.indexOfFirst { it.id == intId }
                if (index != -1) moveItemToTop(index)
            }
        }
    }

    fun moveItemToTop(position: Int) {
        if (position in zones.indices) {
            val item = zones.removeAt(position)
            zones.add(0, item)
            notifyItemMoved(position, 0)
            selectedPosition = 0
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_safe_zone, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val zone = zones[position]
        holder.bind(zone, position == selectedPosition)
        holder.itemView.setOnClickListener {
            onZoneClick(zone, position)
            moveItemToTop(position) // يخليه يطلع الأول
        }
    }

    override fun getItemCount() = zones.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textName: TextView = itemView.findViewById(R.id.textZoneName)
        private val textDetails: TextView = itemView.findViewById(R.id.textZoneDetails)

        fun bind(zone: SafeZoneData, isSelected: Boolean) {
            textName.text = zone.name
            textDetails.text = "📍 ${"%.2f".format(zone.latitude)}, ${"%.2f".format(zone.longitude)} | 📏 ${zone.radius}m"

            val bgRes = if (isSelected) {
                R.drawable.bg_zone_selected
            } else {
                R.drawable.bg_zone_default
            }
            itemView.setBackgroundResource(bgRes)
        }
    }
}

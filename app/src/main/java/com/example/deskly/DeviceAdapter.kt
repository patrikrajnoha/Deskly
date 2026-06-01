package com.example.deskly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val onClick: (DiscoveredDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    private val items = mutableListOf<DiscoveredDevice>()

    fun submit(list: List<DiscoveredDevice>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View, val onClick: (DiscoveredDevice) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val txtName = itemView.findViewById<TextView>(R.id.txtName)
        private val txtIpPort = itemView.findViewById<TextView>(R.id.txtIpPort)

        fun bind(d: DiscoveredDevice) {
            txtName.text = d.name
            txtIpPort.text = "${d.ip}:${d.port}"
            itemView.setOnClickListener { onClick(d) }
        }
    }
}

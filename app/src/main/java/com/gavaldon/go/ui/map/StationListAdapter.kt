package com.gavaldon.go.ui.map

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gavaldon.go.databinding.ItemStationRowBinding
import com.gavaldon.go.domain.GeoUtils
import com.gavaldon.go.domain.Station

/** Vista "Lista" de estaciones (botón "Ver en mapa / Lista" del wireframe). */
class StationListAdapter(
    private val onClick: (Station) -> Unit
) : RecyclerView.Adapter<StationListAdapter.Holder>() {

    private var stations: List<Station> = emptyList()
    private var distances: Map<String, Double> = emptyMap()

    fun submit(newStations: List<Station>, newDistances: Map<String, Double>) {
        stations = newStations
        distances = newDistances
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = stations.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemStationRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(stations[position])

    inner class Holder(private val b: ItemStationRowBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(station: Station) {
            b.tvName.text = station.name
            b.tvDms.text = station.dmsLocation
            val d = distances[station.id]
            if (d != null) {
                b.tvDistance.visibility = View.VISIBLE
                b.tvDistance.text = GeoUtils.formatDistance(d)
            } else {
                b.tvDistance.visibility = View.GONE
            }
            b.root.setOnClickListener { onClick(station) }
        }
    }
}

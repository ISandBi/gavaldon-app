package com.gavaldon.go.ui.map

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gavaldon.go.R
import com.gavaldon.go.databinding.ItemFuelOptionBinding
import com.gavaldon.go.databinding.ItemSheetActionBinding
import com.gavaldon.go.databinding.ItemSheetHeaderBinding
import com.gavaldon.go.domain.FuelPrice
import com.gavaldon.go.domain.FuelType
import com.gavaldon.go.domain.GeoUtils
import com.gavaldon.go.domain.Money
import com.gavaldon.go.domain.Station
import java.util.Locale

/** Filas del detalle de estación mostradas en el RecyclerView de la hoja inferior. */
sealed class SheetItem {
    data class Header(val station: Station, val distanceMeters: Double?) : SheetItem()
    data class Fuel(val price: FuelPrice, val stationName: String) : SheetItem()
    object Directions : SheetItem()
}

class StationDetailAdapter(
    private val items: List<SheetItem>,
    private val onBuy: (FuelType) -> Unit,
    private val onDirections: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_FUEL = 1
        const val TYPE_ACTION = 2
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is SheetItem.Header -> TYPE_HEADER
        is SheetItem.Fuel -> TYPE_FUEL
        SheetItem.Directions -> TYPE_ACTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ItemSheetHeaderBinding.inflate(inflater, parent, false))
            TYPE_FUEL -> FuelHolder(ItemFuelOptionBinding.inflate(inflater, parent, false))
            else -> ActionHolder(ItemSheetActionBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SheetItem.Header -> (holder as HeaderHolder).bind(item)
            is SheetItem.Fuel -> (holder as FuelHolder).bind(item)
            SheetItem.Directions -> (holder as ActionHolder).bind()
        }
    }

    inner class HeaderHolder(private val b: ItemSheetHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: SheetItem.Header) {
            val ctx = b.root.context
            val s = item.station
            b.tvName.text = s.name
            b.tvDms.text = ctx.getString(R.string.station_location_label, s.dmsLocation)
            b.tvCoords.text = ctx.getString(
                R.string.station_lat_lon,
                String.format(Locale.US, "%.7f", s.latitude),
                String.format(Locale.US, "%.7f", s.longitude)
            )
            if (item.distanceMeters != null) {
                b.tvDistance.visibility = View.VISIBLE
                b.tvDistance.text =
                    ctx.getString(R.string.station_distance, GeoUtils.formatDistance(item.distanceMeters))
            } else {
                b.tvDistance.visibility = View.GONE
            }
        }
    }

    inner class FuelHolder(private val b: ItemFuelOptionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: SheetItem.Fuel) {
            val ctx = b.root.context
            val fuel = item.price.fuel
            b.tvFuel.text = fuel.label
            b.tvPrice.text = "${Money.price(item.price.pricePerLiter)} ${ctx.getString(R.string.per_liter)}"
            b.btnBuy.contentDescription = "${ctx.getString(R.string.action_buy)} ${fuel.label} · ${item.stationName}"
            b.btnBuy.setOnClickListener { onBuy(fuel) }
        }
    }

    inner class ActionHolder(private val b: ItemSheetActionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind() {
            b.btnDirections.setOnClickListener { onDirections() }
        }
    }
}

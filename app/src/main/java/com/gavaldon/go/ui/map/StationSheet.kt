package com.gavaldon.go.ui.map

import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import com.gavaldon.go.GavaldonApp
import com.gavaldon.go.R
import com.gavaldon.go.databinding.SheetStationBinding
import com.gavaldon.go.domain.Station
import com.gavaldon.go.ui.pay.PaymentActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Hoja inferior que se abre al tocar una ubicación del mapa. Contiene el RecyclerView con el detalle
 * de la estación y las opciones para comprar combustible.
 */
class StationSheet : BottomSheetDialogFragment() {

    interface Host {
        fun onStationSheetDismissed()
        fun distanceTo(station: Station): Double?
    }

    companion object {
        private const val ARG_STATION_ID = "station_id"

        fun newInstance(stationId: String) = StationSheet().apply {
            arguments = bundleOf(ARG_STATION_ID to stationId)
        }
    }

    private var _binding: SheetStationBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetStationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as GavaldonApp
        val stationId = requireArguments().getString(ARG_STATION_ID).orEmpty()
        val station = app.container.stations.station(stationId)
        if (station == null) {
            dismiss()
            return
        }
        val host = parentFragment as? Host

        val items = ArrayList<SheetItem>()
        items.add(SheetItem.Header(station, host?.distanceTo(station)))
        app.container.stations.prices(station.id).forEach { items.add(SheetItem.Fuel(it, station.name)) }
        items.add(SheetItem.Directions)

        binding.rvDetail.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDetail.adapter = StationDetailAdapter(
            items = items,
            onBuy = { fuel ->
                startActivity(PaymentActivity.newIntent(requireContext(), station.id, fuel))
                dismiss()
            },
            onDirections = { openDirections(station) }
        )
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (parentFragment as? Host)?.onStationSheetDismissed()
    }

    override fun onDestroyView() {
        binding.rvDetail.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun openDirections(station: Station) {
        val label = Uri.encode(station.name)
        val uri = Uri.parse("geo:0,0?q=${station.latitude},${station.longitude}($label)")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.directions_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}

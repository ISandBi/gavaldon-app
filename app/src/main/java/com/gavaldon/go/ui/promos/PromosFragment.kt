package com.gavaldon.go.ui.promos

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gavaldon.go.GavaldonApp
import com.gavaldon.go.R
import com.gavaldon.go.databinding.FragmentListBinding
import com.gavaldon.go.databinding.ItemPromoBinding
import com.gavaldon.go.domain.Promotion
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** RF-19 / RF-22: solo se muestran promociones dentro de su periodo de vigencia. */
class PromosFragment : Fragment() {

    private var _binding: FragmentListBinding? = null
    private val binding get() = _binding!!
    private val adapter = PromoAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvItems.adapter = adapter
        binding.tvEmpty.setText(R.string.promos_empty)
        refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refresh()
    }

    private fun refresh() {
        if (_binding == null) return
        val container = (requireActivity().application as GavaldonApp).container
        val promos = container.promotions.active(LocalDate.now())
        val names = container.stations.stations().associate { it.id to it.name }
        adapter.submit(promos, names)
        binding.tvEmpty.visibility = if (promos.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        binding.rvItems.adapter = null
        _binding = null
        super.onDestroyView()
    }
}

private class PromoAdapter : RecyclerView.Adapter<PromoAdapter.Holder>() {
    private var items: List<Promotion> = emptyList()
    private var stationNames: Map<String, String> = emptyMap()
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "MX"))

    fun submit(promos: List<Promotion>, names: Map<String, String>) {
        items = promos
        stationNames = names
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemPromoBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val b: ItemPromoBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(p: Promotion) {
            val ctx = b.root.context
            b.tvTitle.text = p.title
            b.tvBenefit.text = p.benefit
            b.tvDescription.text = p.description
            b.tvValidity.text = ctx.getString(R.string.promo_valid_until, p.endDate.format(dateFormat))
            b.tvStation.text = if (p.stationId == null) {
                ctx.getString(R.string.promo_station_all)
            } else {
                ctx.getString(R.string.promo_station_one, stationNames[p.stationId].orEmpty())
            }
        }
    }
}

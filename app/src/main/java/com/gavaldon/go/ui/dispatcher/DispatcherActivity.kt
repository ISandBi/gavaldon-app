package com.gavaldon.go.ui.dispatcher

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.gavaldon.go.R
import com.gavaldon.go.databinding.ActivityDispatcherBinding
import com.gavaldon.go.domain.Role
import com.gavaldon.go.ui.base.SecureActivity
import com.gavaldon.go.ui.profile.ProfileFragment

/** Contenedor del perfil despachador: Inicio, Compras, Despachos, Corte y Perfil. */
class DispatcherActivity : SecureActivity() {

    override val requiredRole: Role = Role.DISPATCHER

    private lateinit var binding: ActivityDispatcherBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDispatcherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = container.auth.currentUser()
        val station = user?.stationId?.let { container.stations.station(it) }
        if (user == null || user.role != Role.DISPATCHER || station == null) {
            expireSession()
            return
        }
        binding.tvStationHeader.text = station.name

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        if (savedInstanceState == null) binding.bottomNav.selectedItemId = R.id.dnav_home
    }

    private fun createFragment(id: Int): Fragment = when (id) {
        R.id.dnav_purchases -> DispatcherPurchasesFragment()
        R.id.dnav_history -> DispatcherHistoryFragment()
        R.id.dnav_shift -> DispatcherShiftFragment()
        R.id.dnav_profile -> ProfileFragment()
        else -> DispatcherHomeFragment()
    }

    private fun showTab(id: Int) {
        val tag = "d$id"
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
        fm.fragments.forEach { tx.hide(it) }
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) tx.add(R.id.fragment_container, createFragment(id), tag) else tx.show(existing)
        tx.commit()
    }

    /** Lo usa el Inicio para saltar a otra pestaña (p. ej. el corte). */
    fun select(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }
}

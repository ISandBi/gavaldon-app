package com.gavaldon.go.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.gavaldon.go.R
import com.gavaldon.go.databinding.ActivityMainBinding
import com.gavaldon.go.ui.base.SecureActivity
import com.gavaldon.go.ui.history.HistoryFragment
import com.gavaldon.go.ui.map.MapFragment
import com.gavaldon.go.ui.profile.ProfileFragment
import com.gavaldon.go.ui.promos.PromosFragment

class MainActivity : SecureActivity() {

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_HISTORY = "history"
    }

    private lateinit var binding: ActivityMainBinding

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* opcional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(tagFor(item.itemId))
            true
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId =
                if (intent.getStringExtra(EXTRA_TAB) == TAB_HISTORY) R.id.nav_history else R.id.nav_map
            askNotificationPermissionOnce()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getStringExtra(EXTRA_TAB) == TAB_HISTORY) {
            binding.bottomNav.selectedItemId = R.id.nav_history
        }
    }

    private fun tagFor(itemId: Int): String = when (itemId) {
        R.id.nav_promos -> "promos"
        R.id.nav_history -> "history"
        R.id.nav_profile -> "profile"
        else -> "map"
    }

    private fun createFragment(tag: String): Fragment = when (tag) {
        "promos" -> PromosFragment()
        "history" -> HistoryFragment()
        "profile" -> ProfileFragment()
        else -> MapFragment()
    }

    /** hide/show conserva el estado del mapa (zoom, marcador seleccionado) al cambiar de pestaña. */
    private fun showTab(tag: String) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
        fm.fragments.forEach { tx.hide(it) }
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            tx.add(R.id.fragment_container, createFragment(tag), tag)
        } else {
            tx.show(existing)
        }
        tx.commit()
    }

    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

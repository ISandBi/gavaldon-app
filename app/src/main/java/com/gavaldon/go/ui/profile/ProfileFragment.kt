package com.gavaldon.go.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.gavaldon.go.GavaldonApp
import com.gavaldon.go.R
import com.gavaldon.go.databinding.FragmentProfileBinding
import com.gavaldon.go.ui.auth.LoginActivity

/** RF-03: datos de la cuenta y cierre de sesión. */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val container = (requireActivity().application as GavaldonApp).container
        container.auth.currentUser()?.let {
            binding.tvName.text = it.name
            binding.tvEmail.text = it.email
            binding.tvPhone.text = it.phone
            val station = it.stationId?.let { id -> container.stations.station(id) }
            if (station != null) {
                binding.tvStation.text = getString(R.string.profile_station) + ": " + station.name
                binding.tvStation.visibility = View.VISIBLE
            }
        }
        binding.btnLogout.setOnClickListener {
            container.auth.logout()
            startActivity(
                Intent(requireContext(), LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

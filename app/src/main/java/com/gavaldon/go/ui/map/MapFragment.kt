package com.gavaldon.go.ui.map

import android.Manifest
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.gavaldon.go.GavaldonApp
import com.gavaldon.go.R
import com.gavaldon.go.databinding.FragmentMapBinding
import com.gavaldon.go.databinding.ViewPriceTileBinding
import com.gavaldon.go.domain.GeoUtils
import com.gavaldon.go.domain.Money
import com.gavaldon.go.domain.Station
import com.gavaldon.go.util.LocationProvider
import com.google.android.material.snackbar.Snackbar
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * RF-06: mapa con las 10 estaciones. Al tocar un marcador se abre [StationSheet] (RecyclerView de compra).
 * El mapa es OpenStreetMap vía osmdroid: no requiere API key ni cuenta.
 */
class MapFragment : Fragment(), StationSheet.Host {

    private companion object {
        const val SHEET_TAG = "station_sheet"
        const val PIN_SIZE_DP = 44
        const val PIN_SELECTED_SIZE_DP = 56
        const val USER_DOT_SIZE_DP = 26
    }

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val app get() = requireActivity().application as GavaldonApp

    private var stations: List<Station> = emptyList()
    private val markers = HashMap<String, Marker>()
    private var selectedId: String? = null
    private var userMarker: Marker? = null
    private var userLocation: Location? = null
    private var listMode = false

    private lateinit var locationProvider: LocationProvider
    private lateinit var listAdapter: StationListAdapter

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) fetchLocation() else showMessage(R.string.location_denied)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        configureOsmdroid() // debe ejecutarse ANTES de inflar el MapView
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        stations = app.container.stations.stations()
        locationProvider = LocationProvider(requireContext())

        setupPriceTiles()
        setupMap()
        setupList()

        binding.btnToggle.setOnClickListener { setListMode(!listMode) }
        binding.btnMyLocation.setOnClickListener { onLocateClicked() }
    }

    // ---------- configuración ----------

    private fun configureOsmdroid() {
        val ctx = requireContext().applicationContext
        val cfg = Configuration.getInstance()
        // La política de OpenStreetMap exige un User-Agent que identifique la app.
        cfg.userAgentValue = ctx.packageName
        // Todo en almacenamiento privado de la app: no se piden permisos de almacenamiento.
        cfg.osmdroidBasePath = File(ctx.filesDir, "osmdroid").apply { mkdirs() }
        cfg.osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles").apply { mkdirs() }
    }

    private fun setupPriceTiles() {
        val prices = app.container.stations.prices(stations.first().id)
        prices.forEach { p ->
            val tile = ViewPriceTileBinding.inflate(layoutInflater, binding.priceRow, false)
            tile.tvFuel.text = p.fuel.label
            tile.tvPrice.text = Money.price(p.pricePerLiter)
            binding.priceRow.addView(tile.root)
        }
    }

    private fun setupMap() {
        val map = binding.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.minZoomLevel = 5.0
        map.maxZoomLevel = 19.0
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        map.overlays.add(CopyrightOverlay(requireContext()))

        // Vista inicial razonable mientras se calcula el encuadre.
        map.controller.setZoom(13.0)
        map.controller.setCenter(GeoPoint(19.4423, -99.1470))

        stations.forEach { addMarker(it) }

        val box = BoundingBox.fromGeoPoints(stations.map { GeoPoint(it.latitude, it.longitude) })
        if (map.isLayoutOccurred) {
            map.zoomToBoundingBox(box, false, 120)
        } else {
            map.addOnFirstLayoutListener(
                MapView.OnFirstLayoutListener { _, _, _, _, _ ->
                    map.zoomToBoundingBox(box, false, 120)
                }
            )
        }
    }

    private fun addMarker(station: Station) {
        val marker = Marker(binding.mapView)
        marker.position = GeoPoint(station.latitude, station.longitude)
        marker.title = station.name
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = pinDrawable(selected = false)
        // Devolver true consume el toque: abrimos nuestra hoja en lugar del globo por defecto.
        marker.setOnMarkerClickListener(Marker.OnMarkerClickListener { _, _ ->
            select(station, centerOnMap = true)
            true
        })
        binding.mapView.overlays.add(marker)
        markers[station.id] = marker
    }

    private fun setupList() {
        listAdapter = StationListAdapter { station -> select(station, centerOnMap = false) }
        binding.stationList.layoutManager = LinearLayoutManager(requireContext())
        binding.stationList.adapter = listAdapter
        listAdapter.submit(stations, emptyMap())
    }

    // ---------- selección ----------

    private fun select(station: Station, centerOnMap: Boolean) {
        if (childFragmentManager.findFragmentByTag(SHEET_TAG) != null) return
        setSelected(station.id)
        if (centerOnMap) {
            binding.mapView.controller.animateTo(GeoPoint(station.latitude, station.longitude))
        }
        StationSheet.newInstance(station.id).show(childFragmentManager, SHEET_TAG)
    }

    private fun setSelected(id: String?) {
        selectedId?.let { markers[it]?.icon = pinDrawable(selected = false) }
        selectedId = id
        id?.let { markers[it]?.icon = pinDrawable(selected = true) }
        binding.mapView.invalidate()
    }

    override fun onStationSheetDismissed() {
        if (_binding != null) setSelected(null)
    }

    override fun distanceTo(station: Station): Double? {
        val loc = userLocation ?: return null
        return GeoUtils.distanceMeters(loc.latitude, loc.longitude, station.latitude, station.longitude)
    }

    // ---------- lista / mapa ----------

    private fun setListMode(list: Boolean) {
        listMode = list
        binding.mapView.visibility = if (list) View.GONE else View.VISIBLE
        binding.stationList.visibility = if (list) View.VISIBLE else View.GONE
        binding.btnToggle.setText(if (list) R.string.map_view_map else R.string.map_view_list)
        binding.btnToggle.setIconResource(if (list) R.drawable.ic_map else R.drawable.ic_list)
    }

    // ---------- ubicación (RF-05) ----------

    private fun onLocateClicked() {
        if (locationProvider.hasPermission()) {
            fetchLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun fetchLocation() {
        locationProvider.lastKnown()?.let { onLocation(it) }
        val started = locationProvider.requestOnce { onLocation(it) }
        if (!started && userLocation == null) showMessage(R.string.location_unavailable)
    }

    private fun onLocation(location: Location) {
        if (_binding == null) return
        userLocation = location

        userMarker?.let { binding.mapView.overlays.remove(it) }
        val dot = Marker(binding.mapView)
        dot.position = GeoPoint(location.latitude, location.longitude)
        dot.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        dot.icon = drawableOf(R.drawable.ic_user_dot, USER_DOT_SIZE_DP)
        dot.title = getString(R.string.my_location)
        dot.setOnMarkerClickListener(Marker.OnMarkerClickListener { _, _ -> true })
        binding.mapView.overlays.add(dot)
        userMarker = dot

        val distances = stations.associate {
            it.id to GeoUtils.distanceMeters(location.latitude, location.longitude, it.latitude, it.longitude)
        }
        listAdapter.submit(stations.sortedBy { distances[it.id] }, distances)
        binding.mapView.invalidate()

        // "Encontrar la gasolinera más cercana"
        GeoUtils.nearest(stations, location.latitude, location.longitude)?.let { (station, meters) ->
            val text = getString(R.string.nearest_station, station.name, GeoUtils.formatDistance(meters))
            Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG)
                .setActionTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_on_dark))
                .setAction(R.string.action_view) { select(station, centerOnMap = true) }
                .show()
        }
    }

    private fun showMessage(@StringRes res: Int) {
        if (_binding != null) Snackbar.make(binding.root, res, Snackbar.LENGTH_LONG).show()
    }

    // ---------- dibujo ----------

    private fun pinDrawable(selected: Boolean): Drawable =
        if (selected) drawableOf(R.drawable.ic_pin_selected, PIN_SELECTED_SIZE_DP)
        else drawableOf(R.drawable.ic_pin, PIN_SIZE_DP)

    private fun drawableOf(@DrawableRes res: Int, sizeDp: Int): Drawable {
        val density = resources.displayMetrics.density
        val px = (sizeDp * density).toInt()
        val source = ContextCompat.getDrawable(requireContext(), res)!!
        return BitmapDrawable(resources, source.toBitmap(px, px))
    }

    // ---------- ciclo de vida del mapa ----------

    override fun onResume() {
        super.onResume()
        _binding?.mapView?.onResume()
    }

    override fun onPause() {
        _binding?.mapView?.onPause()
        locationProvider.stop()
        super.onPause()
    }

    override fun onDestroyView() {
        binding.mapView.onDetach()
        markers.clear()
        userMarker = null
        _binding = null
        super.onDestroyView()
    }
}

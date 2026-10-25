package com.pereviader.backgroundswitcher.ui.schedule

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.File

@Composable
fun GpsMapView(
    latitude: Double,
    longitude: Double,
    radiusMeters: Double,
    onPositionChange: (newLatitude: Double, newLongitude: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    val primaryColor = MaterialTheme.colorScheme.primary

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.onDetach()
            mapViewRef = null
        }
    }

    fun fetchCurrentDeviceLocation() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val loc = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            if (loc != null) {
                onPositionChange(loc.latitude, loc.longitude)
                mapViewRef?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            fetchCurrentDeviceLocation()
        }
    }

    fun requestLocationOrFetch() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            fetchCurrentDeviceLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("rule_map_view_container")
    ) {
        // Map Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .testTag("rule_map_view")
        ) {
            // OSMDroid MapView embedded in Jetpack Compose
            AndroidView(
                factory = { ctx ->
                    Configuration.getInstance().apply {
                        userAgentValue = ctx.packageName.ifEmpty { "AndroidBackgroundSwitcher" }
                        osmdroidBasePath = ctx.cacheDir
                        osmdroidTileCache = File(ctx.cacheDir, "osm_tiles")
                    }

                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(15.0)

                        val initialCenter = GeoPoint(latitude, longitude)
                        controller.setCenter(initialCenter)

                        // Geofence circle
                        val circle = Polygon(this).apply {
                            id = "geofence_circle"
                            points = Polygon.pointsAsCircle(initialCenter, radiusMeters)
                            fillPaint.color = primaryColor.copy(alpha = 0.22f).toArgb()
                            outlinePaint.color = primaryColor.toArgb()
                            outlinePaint.strokeWidth = 2f * ctx.resources.displayMetrics.density
                        }
                        overlays.add(circle)

                        // Center Marker
                        val marker = Marker(this).apply {
                            id = "geofence_marker"
                            position = initialCenter
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setInfoWindow(null)
                            isDraggable = true
                            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                                override fun onMarkerDrag(marker: Marker?) {}
                                override fun onMarkerDragStart(marker: Marker?) {}
                                override fun onMarkerDragEnd(marker: Marker?) {
                                    marker?.position?.let { p ->
                                        onPositionChange(p.latitude, p.longitude)
                                    }
                                }
                            })
                        }
                        overlays.add(marker)

                        // Tap on map listener to move position
                        val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                onPositionChange(p.latitude, p.longitude)
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint): Boolean {
                                onPositionChange(p.latitude, p.longitude)
                                return true
                            }
                        })
                        overlays.add(mapEventsOverlay)

                        // Prevent parent vertical scroll from intercepting touch gestures on the map
                        @SuppressLint("ClickableViewAccessibility")
                        setOnTouchListener { v, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }

                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    val currentCenter = GeoPoint(latitude, longitude)

                    val circle = mapView.overlays.filterIsInstance<Polygon>().find { it.id == "geofence_circle" }
                    circle?.let {
                        it.points = Polygon.pointsAsCircle(currentCenter, radiusMeters)
                        it.fillPaint.color = primaryColor.copy(alpha = 0.22f).toArgb()
                        it.outlinePaint.color = primaryColor.toArgb()
                    }

                    val marker = mapView.overlays.filterIsInstance<Marker>().find { it.id == "geofence_marker" }
                    marker?.let {
                        if (it.position.latitude != latitude || it.position.longitude != longitude) {
                            it.position = currentCenter
                        }
                    }

                    mapView.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top-left: Interactive Coordinate Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .testTag("rule_coordinate_badge")
            ) {
                Text(
                    text = "%.4f, %.4f".format(latitude, longitude),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Top-right: Select Current Location Button
            FloatingActionButton(
                onClick = { requestLocationOrFetch() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(40.dp)
                    .testTag("rule_my_location_button"),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Select Current Location",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Bottom-right: Zoom In / Zoom Out Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shadowElevation = 2.dp
                ) {
                    Column {
                        IconButton(
                            onClick = { mapViewRef?.controller?.zoomIn() },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("rule_map_zoom_in")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { mapViewRef?.controller?.zoomOut() },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("rule_map_zoom_out")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Precision Coordinate Input Fields
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = "%.4f".format(latitude),
                onValueChange = { input ->
                    input.toDoubleOrNull()?.let { newLat ->
                        if (newLat in -90.0..90.0) {
                            onPositionChange(newLat, longitude)
                            mapViewRef?.controller?.animateTo(GeoPoint(newLat, longitude))
                        }
                    }
                },
                label = { Text("Latitude") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("rule_latitude_input")
            )
            OutlinedTextField(
                value = "%.4f".format(longitude),
                onValueChange = { input ->
                    input.toDoubleOrNull()?.let { newLon ->
                        if (newLon in -180.0..180.0) {
                            onPositionChange(latitude, newLon)
                            mapViewRef?.controller?.animateTo(GeoPoint(latitude, newLon))
                        }
                    }
                },
                label = { Text("Longitude") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("rule_longitude_input")
            )
        }
    }
}

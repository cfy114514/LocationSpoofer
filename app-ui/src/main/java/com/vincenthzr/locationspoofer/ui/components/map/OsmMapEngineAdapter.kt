package com.vincenthzr.locationspoofer.ui.components.map

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.vincenthzr.locationspoofer.data.model.AppMapType
import com.vincenthzr.locationspoofer.ui.components.AppMapController
import com.vincenthzr.locationspoofer.ui.components.AppMapMarker
import com.vincenthzr.locationspoofer.ui.components.MarkerType
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

private fun osmPoint(lat: Double, lng: Double): GeoPoint {
    val point = gcj02ToWgs84(lat, lng)
    return GeoPoint(point.first, point.second)
}

class OsmMapController(private val map: MapView, private val context: Context, private val interaction: MapInteractionContainer? = null) : AppMapController {
    override fun setOnUserDragListener(listener: (() -> Unit)?) { interaction?.onUserDrag = listener }
    private val handler = Handler(Looper.getMainLooper())
    private var onFinish: ((Double, Double) -> Unit)? = null
    private var onMove: ((Double, Double) -> Unit)? = null
    private val finish = Runnable { current()?.let { onFinish?.invoke(it.first, it.second) } }
    private fun current(): Pair<Double, Double>? = map.mapCenter?.let { wgs84ToGcj02(it.latitude, it.longitude) }
    init {
        map.addMapListener(object : MapListener {
            private fun changed() {
                current()?.let { onMove?.invoke(it.first, it.second) }
                handler.removeCallbacks(finish)
                handler.postDelayed(finish, 180)
            }
            override fun onScroll(event: ScrollEvent?): Boolean { changed(); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { changed(); return false }
        })
    }
    fun dispose() { handler.removeCallbacks(finish) }
    override fun clear() { map.overlays.clear(); map.invalidate() }
    override fun addPolyline(points: List<Pair<Double, Double>>, colorInt: Int, width: Float) {
        map.overlays.add(Polyline().apply {
            setPoints(points.map { osmPoint(it.first, it.second) })
            outlinePaint.color = colorInt
            outlinePaint.strokeWidth = width
        }); map.invalidate()
    }
    override fun addCircle(lat: Double, lng: Double, radius: Double, fillColorInt: Int, strokeColorInt: Int, strokeWidth: Float) {
        map.overlays.add(Polygon().apply {
            points = Polygon.pointsAsCircle(osmPoint(lat, lng), radius)
            fillPaint.color = fillColorInt
            outlinePaint.color = strokeColorInt
            outlinePaint.strokeWidth = strokeWidth
        }); map.invalidate()
    }
    override fun addMarker(lat: Double, lng: Double, title: String, type: MarkerType): AppMapMarker {
        val marker = Marker(map).apply {
            position = osmPoint(lat, lng)
            icon = BitmapDrawable(context.resources, GaodeMarkerHelper.getMarkerBitmap(context, title, type))
            setAnchor(Marker.ANCHOR_CENTER, 0.9f)
            this.title = title
        }
        map.overlays.add(marker); map.invalidate()
        return object : AppMapMarker {
            override fun setPosition(lat: Double, lng: Double) { marker.position = osmPoint(lat, lng); map.invalidate() }
        }
    }
    override fun animateCamera(lat: Double, lng: Double, zoom: Float?) {
        if (zoom != null) map.controller.setZoom(zoom.toDouble())
        map.controller.animateTo(osmPoint(lat, lng))
    }
    override fun moveCamera(lat: Double, lng: Double, zoom: Float?) {
        if (zoom != null) map.controller.setZoom(zoom.toDouble())
        map.controller.setCenter(osmPoint(lat, lng))
    }
    override fun fitBounds(points: List<Pair<Double, Double>>, padding: Int) {
        if (points.isEmpty()) return
        if (points.size == 1) { animateCamera(points[0].first, points[0].second, null); return }
        val converted = points.map { osmPoint(it.first, it.second) }
        val box = BoundingBox.fromGeoPoints(converted)
        map.post { map.zoomToBoundingBox(box, true, padding) }
    }
    override val cameraTargetLat: Double? get() = current()?.first
    override val cameraTargetLng: Double? get() = current()?.second
    override fun setOnCameraChangeListener(onFinish: (Double, Double) -> Unit) { this.onFinish = onFinish }
    override fun setOnCameraMoveListener(onMove: (Double, Double) -> Unit) { this.onMove = onMove }
    override fun disableUiControls() { map.setMultiTouchControls(true); map.zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER) }
    // Standard OSM tiles provide neither satellite imagery nor 3D.
    override fun setMapType(type: AppMapType) = Unit
    override fun setDarkMode(isDark: Boolean, context: Context) = Unit
}

@Composable
fun OsmMapViewContainer(context: Context, lifecycleOwner: LifecycleOwner, modifier: Modifier,
    onMapReady: (AppMapController) -> Unit, onControllerCreated: (AppMapController) -> Unit) {
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = "LocationSpoofer/${com.vincenthzr.locationspoofer.ui.BuildConfig.VERSION_NAME} (https://github.com/cfy114514/LocationSpoofer)"
            // Keep tile cache in app-private storage, without broad storage permissions.
            osmdroidBasePath = java.io.File(context.filesDir, "osmdroid").apply { mkdirs() }
            osmdroidTileCache = java.io.File(osmdroidBasePath, "tiles").apply { mkdirs() }
        }
        MapView(context).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true) }
    }
    val interaction = remember(map) { MapInteractionContainer(context) }
    val controller = remember(map) { OsmMapController(map, context, interaction) }
    DisposableEffect(lifecycleOwner, map) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map.onResume()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); controller.dispose(); map.onPause(); map.onDetach() }
    }
    val uriHandler = LocalUriHandler.current
    Box(modifier) {
        AndroidView(factory = {
            map.setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
            interaction.addView(map, android.widget.FrameLayout.LayoutParams(-1, -1))
            onControllerCreated(controller); onMapReady(controller)
            interaction
        }, modifier = Modifier.fillMaxSize())
        Text("© OpenStreetMap contributors", modifier = Modifier.align(Alignment.TopStart)
            .background(Color.White.copy(alpha = 0.85f))
            .clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") }
            .padding(horizontal = 4.dp, vertical = 2.dp),
            color = Color.Black, fontSize = 10.sp, style = MaterialTheme.typography.labelSmall)
    }
}

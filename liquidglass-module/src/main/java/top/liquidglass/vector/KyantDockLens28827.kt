package top.liquidglass.vector

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.shapes.RoundedRectangle
import de.robv.android.xposed.XposedBridge
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min

/** Kyant optical Dock: a background-only sibling BEFORE Hotseat, not an overlay
 *  on top of Launcher icons. No View is added into OEM CellLayout/Hotseat itself.
 *  Unsupported parents fail closed; HookEntry retains the original Dock drawable.
 */
object KyantDockLens28827 {
    // V288.29: authoritative Dock capsule bounds come from HookEntry's
    // ORIGINAL DockBackground204 geometry, never the screen or Hotseat viewport.
    private data class DockGeometry(
        val x: Float, val y: Float, val width: Float, val height: Float, val radius: Float
    )
    private fun geometryOf(hotseat: View): DockGeometry? {
        val g = HookEntry.dockGeometry28829(hotseat) ?: return null
        return DockGeometry(g[0], g[1], g[2], g[3], g[4])
    }

    private val hosts = WeakHashMap<View, Host>()
    private class Owner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store
        fun start() {
            saved.performAttach(); saved.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        fun stop() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }

    private class Host(val hotseat: View, val parent: FrameLayout, reference: Bitmap) {
        val owner = Owner()
        val frame = FrameLayout(hotseat.context).apply {
            isClickable = false; isFocusable = false
            // The optics must not bleed outside the Hotseat native view bounds.
            clipChildren = true; clipToPadding = true
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
        }
        val compose = ComposeView(hotseat.context).apply {
            isClickable = false; isFocusable = false
        }
        // Position denotes the *actual native capsule's* screen origin, not the
        // top-left of a re-centered viewport or an independent optical bounds.
        val position = mutableStateOf(Offset.Zero)
        val geometry = mutableStateOf(geometryOf(hotseat))
        private var geometryLogged = false
        val wallpaper = mutableStateOf(reference)
        val scope = CoroutineScope(AndroidUiDispatcher.Main + SupervisorJob() +
            CoroutineExceptionHandler { _, t ->
                XposedBridge.log("KYANT28827_DOCK_RECOMPOSER_ERROR ${t.javaClass.simpleName} ${t.message}")
                android.os.Handler(Looper.getMainLooper()).post {
                    detach(hotseat)
                    HookEntry.restoreDockFallback28827(hotseat)
                }
            })
        val recomposer = Recomposer(scope.coroutineContext)
        val listener = android.view.ViewTreeObserver.OnPreDrawListener {
            updateGeometry()
            true
        }
        val detachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                android.os.Handler(Looper.getMainLooper()).post { detach(hotseat) }
            }
        }
        private var stopped = false
        fun start() {
            owner.start()
            val index = parent.indexOfChild(hotseat)
            if (index < 0) throw IllegalStateException("Hotseat parent changed")
            frame.addView(compose, FrameLayout.LayoutParams(-1, -1))
            // Placed beneath Hotseat: existing icons keep their touch and z-order.
            parent.addView(frame, index, FrameLayout.LayoutParams(max(1,hotseat.width),max(1,hotseat.height)))
            compose.setParentCompositionContext(recomposer)
            compose.setContent {
                val source = wallpaper.value
                DockOptics(source, position.value, geometry.value)
            }
            scope.launch { recomposer.runRecomposeAndApplyChanges() }
            hotseat.addOnAttachStateChangeListener(detachListener)
            parent.viewTreeObserver.addOnPreDrawListener(listener)
            updateGeometry()
        }
        private val transform = Matrix()
        private val screenPoint = FloatArray(2)
        private val rootScreen = IntArray(2)
        private val rootWindow = IntArray(2)
        fun updateGeometry() {
            if (stopped || !hotseat.isAttachedToWindow || hotseat.parent !== parent) return
            val w = hotseat.width; val h = hotseat.height
            if (w <= 0 || h <= 0) return
            val native = geometryOf(hotseat) ?: return
            if (geometry.value != native) geometry.value = native
            val p = frame.layoutParams as FrameLayout.LayoutParams
            if (p.width != w || p.height != h) {
                frame.layoutParams = p.apply { width = w; height = h }
            }
            // Frame and Hotseat are siblings.  Copy the REAL parent-local View
            // transform instead of converting global pixels back to parent X.
            // The old conversion double-applied parent scroll/translation on
            // some ColorOS Hotseat layouts and could extend the right corner.
            if (frame.x != hotseat.x) frame.x = hotseat.x
            if (frame.y != hotseat.y) frame.y = hotseat.y
            if (frame.pivotX != hotseat.pivotX) frame.pivotX = hotseat.pivotX
            if (frame.pivotY != hotseat.pivotY) frame.pivotY = hotseat.pivotY
            if (frame.scaleX != hotseat.scaleX) frame.scaleX = hotseat.scaleX
            if (frame.scaleY != hotseat.scaleY) frame.scaleY = hotseat.scaleY
            if (frame.rotation != hotseat.rotation) frame.rotation = hotseat.rotation
            if (frame.rotationX != hotseat.rotationX) frame.rotationX = hotseat.rotationX
            if (frame.rotationY != hotseat.rotationY) frame.rotationY = hotseat.rotationY
            if (frame.alpha != hotseat.alpha) frame.alpha = hotseat.alpha

            // The wallpaper is sampled from the real glass rectangle's transformed
            // origin.  No second inset, safe area clamp or viewport correction.
            transform.reset()
            hotseat.transformMatrixToGlobal(transform)
            screenPoint[0] = native.x
            screenPoint[1] = native.y
            transform.mapPoints(screenPoint)
            val root = hotseat.rootView
            root.getLocationOnScreen(rootScreen)
            root.getLocationInWindow(rootWindow)
            val next = Offset(screenPoint[0] + rootScreen[0] - rootWindow[0],
                screenPoint[1] + rootScreen[1] - rootWindow[1])
            if (position.value != next) position.value = next
            if (!geometryLogged) {
                geometryLogged = true
                XposedBridge.log("KYANT28829_DOCK_NATIVE_BOUNDS " +
                    "hotseatSize=${w}x$h hotseatParentXY=${hotseat.x},${hotseat.y} " +
                    "local=$native screen=$next frameXY=${frame.x},${frame.y}")
            }
            val latest = KyantLauncherFrameBridge266.peekReferenceFrame270()
            if (latest != null && latest !== wallpaper.value) wallpaper.value = latest
        }
        fun stop() {
            if (stopped) return
            stopped = true
            try { hotseat.removeOnAttachStateChangeListener(detachListener) } catch (_: Throwable) {}
            try { if (parent.viewTreeObserver.isAlive) parent.viewTreeObserver.removeOnPreDrawListener(listener) } catch (_: Throwable) {}
            try { compose.disposeComposition() } catch (_: Throwable) {}
            try { (frame.parent as? ViewGroup)?.removeView(frame) } catch (_: Throwable) {}
            try { recomposer.close() } catch (_: Throwable) {}
            try { scope.cancel() } catch (_: Throwable) {}
            try { owner.stop() } catch (_: Throwable) {}
        }
    }

    @JvmStatic fun attach(hotseat: View): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) return false
        if (!hotseat.isAttachedToWindow) return false
        if (Settings.Global.getInt(hotseat.context.contentResolver, KyantOptics28827.DOCK_KEY, 0) != 1) {
            detach(hotseat); return false
        }
        synchronized(hosts) {
            val old = hosts[hotseat]
            if (old != null) {
                if (old.frame.parent != null) return true
                old.stop()
                hosts.remove(hotseat)
            }
            // A non-FrameLayout parent cannot guarantee the sibling is behind icons.
            val parent = hotseat.parent as? FrameLayout ?: run {
                XposedBridge.log("KYANT28827_DOCK_PARENT_UNSUPPORTED ${hotseat.parent?.javaClass?.name}")
                return false
            }
            val source = KyantLauncherFrameBridge266.peekReferenceFrame270() ?: run {
                XposedBridge.log("KYANT28827_DOCK_REFERENCE_NOT_READY")
                return false
            }
            KyantOptics28827.ensure(hotseat.context)
            val host = Host(hotseat, parent, source)
            return try {
                host.start()
                hosts[hotseat] = host
                XposedBridge.log("KYANT28827_DOCK_ATTACHED size=${hotseat.width}x${hotseat.height} reference=${source.width}x${source.height}")
                true
            } catch (t: Throwable) {
                host.stop()
                XposedBridge.log("KYANT28827_DOCK_ATTACH_ERROR ${t.javaClass.simpleName} ${t.message}")
                false
            }
        }
    }

    @JvmStatic fun detach(hotseat: View) {
        synchronized(hosts) { hosts.remove(hotseat)?.stop() }
    }

    @Composable private fun DockOptics(reference: Bitmap, screen: Offset,
                                      geometry: DockGeometry?) {
        val g = geometry ?: return
        if (g.width <= 0f || g.height <= 0f) return
        val density = LocalDensity.current.density
        val mat = KyantOptics28827.material
        val opticalW = g.width
        val opticalH = g.height
        val radiusDp = (g.radius / density).dp
        val backdrop = rememberLayerBackdrop()
        val paint = androidx.compose.runtime.remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
        val dst = androidx.compose.runtime.remember { RectF() }
        val fullWidth = android.content.res.Resources.getSystem().displayMetrics.widthPixels.coerceAtLeast(1)
        val fullHeight = android.content.res.Resources.getSystem().displayMetrics.heightPixels.coerceAtLeast(1)
        val scaleX = reference.width.toFloat()/fullWidth
        val scaleY = reference.height.toFloat()/fullHeight
        Box(Modifier.offset(x=(g.x/density).dp, y=(g.y/density).dp)
            .size((opticalW/density).dp,(opticalH/density).dp)
            .clip(RoundedCornerShape(radiusDp))) {
            Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop).graphicsLayer { alpha = 1f }) {
                // Sampling follows the VISUAL glass edge after clipping;
                // retaining the old inset here would offset the wallpaper.
                val sampleX = screen.x*scaleX
                val sampleY = screen.y*scaleY
                val sourceW = opticalW*scaleX
                val sourceH = opticalH*scaleY
                dst.set(-sampleX*size.width/sourceW, -sampleY*size.height/sourceH,
                    (reference.width-sampleX)*size.width/sourceW,
                    (reference.height-sampleY)*size.height/sourceH)
                drawIntoCanvas { it.nativeCanvas.drawBitmap(reference, null, dst, paint) }
            }
            Box(Modifier.fillMaxSize().drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(radiusDp) },
                effects = {
                    val m = size.minDimension
                    blur(mat.blur.dp.toPx())
                    if (mat.strength > 0) {
                        val factor = mat.strength/100f
                        lens(refractionHeight=.1f*m*factor, refractionAmount=.2f*m*factor,
                            depthEffect=true, chromaticAberration=false)
                    }
                },
                highlight = { Highlight(alpha=mat.highlight/100f, style=HighlightStyle.Plain) }
            ))
        }
    }
}

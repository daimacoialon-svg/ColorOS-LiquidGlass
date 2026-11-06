package top.liquidglass.vector

import android.provider.Settings
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Offset
import android.util.TypedValue
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.RoundedRectangle
import com.kyant.backdrop.highlight.Highlight
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
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
import de.robv.android.xposed.XposedBridge
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** V269.1: isolated Compose composition/host-draw/disposal verification; no Launcher glass replacement. */
object KyantLauncherLens270 {
    // Opt-in diagnostic: 1 allows only one active optical host, 0 retains all hosts.
    private const val SINGLE_HOST_KEY = "liquidglass_kyant_single_host_282"
    private const val SAMPLE_MODE_KEY = "liquidglass_kyant_sampling_mode_283"
    private const val RENDER_AB_KEY = "liquidglass_kyant_render_ab_2883"
    private const val MOTION_AB_KEY = "liquidglass_kyant_motion_ab_2884"
    private const val LEGACY_SYNC_AB_KEY = "liquidglass_kyant_legacy_sync_ab_2886"
    private const val NATIVE_COMPARISON_KEY = "liquidglass_kyant_native_compare_2887"
    private const val NATIVE_BRIDGE_KEY = "liquidglass_kyant_native_bridge_2888"
    private const val NATIVE_LAYER_KEY = "liquidglass_kyant_native_layer_28812"
    private const val DISPLAY_ISOLATION_KEY = "liquidglass_kyant_display_isolation_28811"
    private const val BRIDGE_SYNC_KEY = "liquidglass_kyant_bridge_sync_2889"
    private val activeHostCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val lastSamplingCommitNs = java.util.concurrent.atomic.AtomicLong(0L)

    private const val KEY = "liquidglass_kyant_persistent_probe_2884"

    private class Owner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()
        private var started = false
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store
        fun start() {
            controller.performAttach()
            controller.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            started = true
        }
        fun stop() {
            if (started) {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                started = false
            }
            store.clear()
        }
    }

    @JvmStatic fun run(anchor: View) {
        if (!anchor.isAttachedToWindow || KyantLauncherLensProbe270.isActiveFor273(anchor)) return
        val reference = KyantLauncherFrameBridge266.peekReferenceFrame270()
        if (reference == null) {
            XposedBridge.log("KYANT2885_REFERENCE_NOT_READY one_shot_not_consumed")
            return
        }
        val root = anchor.rootView as? ViewGroup ?: return
        // Place the Compose host as the FIRST child of FolderIcon, before native icon children.
        // This preserves the original draw ordering without a DecorView overlay.
        val folder = anchor as? ViewGroup ?: run {
            XposedBridge.log("KYANT2885_ABORT anchor_not_viewgroup")
            return
        }
        // Persistent experiment is explicitly opt-in; never disarm the switch on attach.
        if (try { Settings.Global.getInt(anchor.context.contentResolver, KEY, 0) != 1 }
                catch (_: Throwable) { true }) return
        val samplingMode = try {
            Settings.Global.getInt(anchor.context.contentResolver, SAMPLE_MODE_KEY, 1).coerceIn(0, 1)
        } catch (_: Throwable) { 1 }
        // 0: original optics + original bitmap sampling; 1: cropped bitmap source,
        // 2: original bitmap sampling with isolated offscreen compositing layer.
        // Read once on Host creation; switching modes requires Launcher restart.
        val renderAbMode = try {
            Settings.Global.getInt(anchor.context.contentResolver, RENDER_AB_KEY, 0).coerceIn(0, 2)
        } catch (_: Throwable) { 0 }
        // Motion diagnostic is independent of the earlier render A/B switch.
        // 0 = baseline, 1 = freeze wallpaper source at its first draw,
        // 2 = live wallpaper source without lens refraction.
        val motionAbMode = try {
            Settings.Global.getInt(anchor.context.contentResolver, MOTION_AB_KEY, 0).coerceIn(0, 2)
        } catch (_: Throwable) { 0 }
        val bridgeSyncMode = try {
            Settings.Global.getInt(anchor.context.contentResolver, BRIDGE_SYNC_KEY, 1).coerceIn(0, 2)
        } catch (_: Throwable) { 1 }
        XposedBridge.log("KYANT28810_BRIDGE_SYNC mode=$bridgeSyncMode")
        // 0=legacy switches, 1=native Canvas full overlay, 2=Compose Bitmap without
        // Kyant optics, 3=Compose Bitmap with full Kyant optics.
        val displayIsolationMode = try {
            Settings.Global.getInt(anchor.context.contentResolver, DISPLAY_ISOLATION_KEY, 3).coerceIn(0, 3)
        } catch (_: Throwable) { 3 }
        XposedBridge.log("KYANT28811_DISPLAY_ISOLATION mode=$displayIsolationMode")
        // 0=legacy isolation, 1=native above Compose, 2=native behind Compose
        // with 0-alpha source experiment, 3=native behind Compose with 2% source.
        val nativeLayerMode = try {
            Settings.Global.getInt(anchor.context.contentResolver, NATIVE_LAYER_KEY, 0).coerceIn(0, 3)
        } catch (_: Throwable) { 0 }
        XposedBridge.log("KYANT28812_NATIVE_LAYER mode=$nativeLayerMode isolation=$displayIsolationMode")
        val nativeBridgeMode = if (nativeLayerMode in 1..3) 0 else if (displayIsolationMode in 1..3) {
            if (displayIsolationMode == 1) 0 else 1
        } else try {
            Settings.Global.getInt(anchor.context.contentResolver, NATIVE_BRIDGE_KEY, 0).coerceIn(0, 1)
        } catch (_: Throwable) { 0 }
        XposedBridge.log("KYANT2888_NATIVE_BRIDGE mode=$nativeBridgeMode (0=baseline 1=native_bitmap_bridge)")
        val nativeCompareMode = try {
            Settings.Global.getInt(anchor.context.contentResolver, NATIVE_COMPARISON_KEY, 0).coerceIn(0, 1)
        } catch (_: Throwable) { 0 }
        XposedBridge.log("KYANT2887_NATIVE_COMPARE mode=$nativeCompareMode (0=full_kyant 1=left_native_right_kyant)")
        val legacySyncAbMode = try {
            Settings.Global.getInt(anchor.context.contentResolver, LEGACY_SYNC_AB_KEY, 0).coerceIn(0, 1)
        } catch (_: Throwable) { 0 }
        XposedBridge.log("KYANT2886_LEGACY_SYNC_AB mode=$legacySyncAbMode (0=predraw_state 1=draw_time_matrix)")
        XposedBridge.log("KYANT2885_MOTION_AB mode=$motionAbMode (0=baseline 1=freeze_source 2=no_refraction)")
        XposedBridge.log("KYANT2885_RENDER_AB mode=" + renderAbMode +
            " (0=baseline 1=cropped_bitmap 2=offscreen_compositing)")
        XposedBridge.log("KYANT2885_SAMPLE_MODE mode=$samplingMode (0=cropped_source 1=whole_bitmap_transform) referenceIdentity=${System.identityHashCode(reference)}")
        // V288.1: mode 1 was confirmed to freeze wallpaper sampling on device.
        // Always use the verified PreDraw commit path; no Compose state mutation
        // inside FrameLayout.dispatchDraw.
        XposedBridge.log("KYANT2885_FRAME_SYNC disabled_bad_draw_commit=true predraw_only=true")
        val singleHostMode = try {
            Settings.Global.getInt(anchor.context.contentResolver, SINGLE_HOST_KEY, 0) == 1
        } catch (_: Throwable) { false }
        // Controlled A/B test; do not silently change normal multi-folder behavior.
        if (singleHostMode && activeHostCount.get() > 0) {
            XposedBridge.log("KYANT2885_SINGLE_HOST_SKIP active=" + activeHostCount.get())
            return
        }
        XposedBridge.log("KYANT2885_PERSISTENT_ARMED key=" + KEY)
        val startedAt = SystemClock.uptimeMillis()
        val displayWidth = root.width.coerceAtLeast(1)
        val displayHeight = root.height.coerceAtLeast(1)
        val initialBounds = FolderGlassGeometry272.resolve(anchor)
        var lastBounds = android.graphics.Rect(initialBounds)
        val nativeRadius = HookEntry.kyantFolderGlassRadius273(anchor)
        XposedBridge.log("KYANT2885_NATIVE_GEOMETRY rect=$initialBounds radiusPx=$nativeRadius")
        val previewWidth = initialBounds.width().coerceAtLeast(1)
        val previewHeight = initialBounds.height().coerceAtLeast(1)
        val anchorXY = IntArray(2)
        anchor.getLocationOnScreen(anchorXY)
        // Place the diagnostic preview at the upper-left of the Launcher root.
        // The bitmap sample coordinates are aligned to the root's screen position.
        
        val compositionSeen = AtomicBoolean(false)
        val drawSeen = AtomicBoolean(false)
        // Single state write per frame prevents separate X/Y snapshot invalidations.
        val samplePosition = androidx.compose.runtime.mutableStateOf(
            Offset(0f, 0f))
        // Last observed global matrix and its timestamp, captured by PreDraw.
        // Atomic immutable snapshot avoids mismatched X/Y values.
        val predrawMatrixSnapshot = java.util.concurrent.atomic.AtomicReference(
            Pair(Offset(Float.NaN, Float.NaN), 0L))
        val sampleWidth = androidx.compose.runtime.mutableIntStateOf(previewWidth)
        val sampleHeight = androidx.compose.runtime.mutableIntStateOf(previewHeight)
        // Initial sample uses the full transform, not the early integer view location.
        val sampleRadiusPx = androidx.compose.runtime.mutableFloatStateOf(
            nativeRadius.coerceIn(0f, minOf(previewWidth, previewHeight) * 0.49f))
        XposedBridge.log("KYANT2885_SHAPE size=${previewWidth}x${previewHeight} radiusPx=${sampleRadiusPx.floatValue} radiusDp=${sampleRadiusPx.floatValue / anchor.resources.displayMetrics.density} source=legacy")
        var followListener: android.view.ViewTreeObserver.OnPreDrawListener? = null
        var detachListener: View.OnAttachStateChangeListener? = null
        var layoutListener: View.OnLayoutChangeListener? = null
        // Reuse the global cached switch value; no Settings provider IPC per rendered frame.
        var frameChecks = 0L
        var geometryChanges = 0L
        var hostLayoutCorrections = 0L
        var dragDiagnosticCount = 0L
        var lastHostCorrectionMs = 0L
        // Diagnostics: track local host position independently from inherited Workspace motion.
        // Never modify Compose state or layout in a draw callback.
        var lastWorkspaceScrollX = Int.MIN_VALUE
        var lastWorkspaceTranslationX = Float.NaN
        var lastHostScreenX = Float.NaN
        var lastAnchorScreenX = Float.NaN
        var lastPositionTraceMs = 0L
        var positionChanges = 0L
        var maxPredrawUs = 0L
        var totalPredrawUs = 0L
        var offscreenChecks = 0L
        var skippedPositionUpdates = 0L
        var stableLayoutFrames = 0
        var lastDrawNs = 0L
        var drawIntervals = 0L
        var drawGapOver16ms = 0L
        var drawGapOver33ms = 0L
        var maxDrawGapMs = 0L
        var lastCommitNs = 0L
        var sampleCommitCount = 0L
        var commitToDrawTotalMs = 0L
        var commitToDrawSamples = 0L
        var activeCountRegistered = false
        val locationScratch = IntArray(2)
        val matrixScratch = Matrix()
        val matrixPoint = FloatArray(2)
        val rootScreen = IntArray(2)
        val rootWindow = IntArray(2)
        fun glassScreenPosition(b: Rect): Offset {
            // Include fractional Workspace translations rather than truncating to integer pixels.
            // transformMatrixToGlobal is window-local; convert to physical screen coordinates.
            matrixScratch.reset()
            anchor.transformMatrixToGlobal(matrixScratch)
            matrixPoint[0] = b.left.toFloat(); matrixPoint[1] = b.top.toFloat()
            matrixScratch.mapPoints(matrixPoint)
            root.getLocationOnScreen(rootScreen)
            root.getLocationInWindow(rootWindow)
            return Offset(matrixPoint[0] + rootScreen[0] - rootWindow[0],
                matrixPoint[1] + rootScreen[1] - rootWindow[1])
        }
        // Initialize only after the local coordinate helper is declared.
        samplePosition.value = glassScreenPosition(initialBounds)
        predrawMatrixSnapshot.set(Pair(samplePosition.value, SystemClock.elapsedRealtimeNanos()))
        var lastSample = Offset(Float.NaN, Float.NaN)
        var firstPositionSettled = false
        var startupSyncAttempts = 0
        var startupLastPosition = Offset(Float.NaN, Float.NaN)
        var startupStableTicks = 0
        val startupBeginMs = SystemClock.uptimeMillis()
        // V286: startup position is continuously tracked; no fixed multi-second visibility gate.
        var startupGateOpen = true
        // V281: synchronize visible wallpaper sampling on each pre-draw pass.
        // V279 22ms throttle caused the optical texture to lag behind Workspace.
        val owner = Owner()
        // ComposeView is final in the Compose UI version used by this project.
        // Observe the enclosing FrameLayout instead of subclassing ComposeView.
        var disposed = false
        val frame = object : FrameLayout(anchor.context) {
            override fun dispatchDraw(canvas: Canvas) {
                // Never mutate Compose state during View.dispatchDraw: the
                // V288 experiment stalled the source snapshot on real devices.
                super.dispatchDraw(canvas)
                val nowNs = SystemClock.elapsedRealtimeNanos()
                if (lastDrawNs != 0L) {
                    val gapMs = (nowNs - lastDrawNs) / 1_000_000L
                    drawIntervals++
                    if (gapMs > 16L) drawGapOver16ms++
                    if (gapMs > 33L) drawGapOver33ms++
                    if (gapMs > maxDrawGapMs) maxDrawGapMs = gapMs
                    if (drawIntervals % 180L == 0L) {
                        XposedBridge.log("KYANT2885_DRAW_CADENCE host=" + System.identityHashCode(anchor) +
                            " intervals=" + drawIntervals + " over16ms=" + drawGapOver16ms +
                            " over33ms=" + drawGapOver33ms + " maxGapMs=" + maxDrawGapMs +
                            " sampleCommits=" + sampleCommitCount + " activeHosts=" + activeHostCount.get())
                    }
                }
                lastDrawNs = nowNs
                if (lastCommitNs > 0L && nowNs >= lastCommitNs) {
                    commitToDrawTotalMs += (nowNs - lastCommitNs) / 1_000_000L
                    commitToDrawSamples++
                    lastCommitNs = 0L
                }
                if (drawSeen.compareAndSet(false, true)) {
                    XposedBridge.log("KYANT2885_FIRST_DRAW elapsedMs=" +
                        (SystemClock.uptimeMillis() - startedAt) +
                        " compositionSeen=" + compositionSeen.get() +
                        " width=" + width + " height=" + height +
                        " hardware=" + canvas.isHardwareAccelerated +
                        " source=host_frame_dispatchDraw")
                }
            }
        }.apply {
            isClickable = false
            isFocusable = false
            // Do not show an uncomposed placeholder; reveal as soon as geometry and composition agree.
            visibility = View.INVISIBLE
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
        }
        val compose = ComposeView(anchor.context).apply {
            isClickable = false
            isFocusable = false
        }
        // V288.8 experimental bridge: native View Canvas draws wallpaper into a
        // mutable Bitmap; Compose uses that Bitmap as its Kyant backdrop source.
        // This tests input compatibility; it does NOT guarantee same-frame sync.
        val bridgeBitmap = if (nativeBridgeMode == 1) Bitmap.createBitmap(
            previewWidth.coerceAtLeast(1), previewHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888) else null
        val bridgeVersion = androidx.compose.runtime.mutableIntStateOf(0)
        val bridgePublishedPosition = java.util.concurrent.atomic.AtomicReference<Offset?>(null)
        val bridgeDrawCount = java.util.concurrent.atomic.AtomicLong(0L)
        val bridgeMismatchObserved = java.util.concurrent.atomic.AtomicLong(0L)
        // Native View and Compose draw run on the Launcher UI thread. This single
        // reusable Bitmap is updated atomically relative to those draw callbacks.
        // A pending notification is coalesced to avoid unbounded post() backlog.
        val bridgeNotifyPending = java.util.concurrent.atomic.AtomicBoolean(false)
        val bridgePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val bridgeDst = RectF()
        val bridgeCanvas = bridgeBitmap?.let { Canvas(it) }
        fun sampleBridgeAt(pos: Offset) {
            val bitmap = bridgeBitmap ?: return
            val target = bridgeCanvas ?: return
            val sw = android.content.res.Resources.getSystem().displayMetrics.widthPixels.coerceAtLeast(1)
            val sh = android.content.res.Resources.getSystem().displayMetrics.heightPixels.coerceAtLeast(1)
            val sx = reference.width.toFloat() / sw
            val sy = reference.height.toFloat() / sh
            val cropW = bitmap.width * sx
            val cropH = bitmap.height * sy
            val x = pos.x * sx
            val y = pos.y * sy
            bridgeDst.set(-x * bitmap.width / cropW.coerceAtLeast(.001f),
                -y * bitmap.height / cropH.coerceAtLeast(.001f),
                (reference.width - x) * bitmap.width / cropW.coerceAtLeast(.001f),
                (reference.height - y) * bitmap.height / cropH.coerceAtLeast(.001f))
            target.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            target.drawBitmap(reference, null, bridgeDst, bridgePaint)
            bridgePublishedPosition.set(pos)
            bridgeDrawCount.incrementAndGet()
        }
        val bridgeView = if (bridgeBitmap != null) object : View(anchor.context) {
            private var count = 0L
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (disposed || width <= 0 || height <= 0) return
                val pos = try { glassScreenPosition(lastBounds) } catch (_: Throwable) { return }
                sampleBridgeAt(pos)
                count++
                if (bridgeNotifyPending.compareAndSet(false, true)) {
                    post {
                        bridgeNotifyPending.set(false)
                        if (!disposed) bridgeVersion.intValue++
                    }
                }
                if (count % 120L == 0L) XposedBridge.log(
                    "KYANT2889_NATIVE_PUBLISH draws=$count x=${pos.x} y=${pos.y} version=${bridgeVersion.intValue}")
            }
        }.apply { isClickable = false; isFocusable = false; setWillNotDraw(false) } else null
        // V288.7: native Canvas reference, intentionally ABOVE Kyant on the LEFT half
        // only. Right half stays the unmodified Compose/Kyant optical renderer.
        // This is an A/B diagnostic, NOT the final hybrid optical implementation.
        val nativeSampleView = if ((nativeCompareMode == 1 && nativeBridgeMode == 0 && displayIsolationMode == 0) || displayIsolationMode == 1 || nativeLayerMode in 1..3) object : View(anchor.context) {
            private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            private val destination = RectF()
            private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                strokeWidth = resources.displayMetrics.density.coerceAtLeast(1f)
            }
            private var drawCount = 0L
            private var lastDrawTimeNs = 0L
            private var maxGapMs = 0L
            private var lastX = Float.NaN
            private var lastY = Float.NaN
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (width <= 0 || height <= 0 || disposed) return
                val position = try { glassScreenPosition(lastBounds) } catch (_: Throwable) { return }
                val screenW = android.content.res.Resources.getSystem().displayMetrics.widthPixels.coerceAtLeast(1)
                val screenH = android.content.res.Resources.getSystem().displayMetrics.heightPixels.coerceAtLeast(1)
                val scaleX = reference.width.toFloat() / screenW
                val scaleY = reference.height.toFloat() / screenH
                val x = position.x * scaleX
                val y = position.y * scaleY
                val sw = width.toFloat() * scaleX
                val sh = height.toFloat() * scaleY
                if (sw <= 0f || sh <= 0f) return
                destination.set(-x * width / sw, -y * height / sh,
                    (reference.width - x) * width / sw,
                    (reference.height - y) * height / sh)
                val checkpoint = canvas.save()
                if (displayIsolationMode == 1 || nativeLayerMode in 1..3) {
                    canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
                } else {
                    canvas.clipRect(0f, 0f, width * 0.5f, height.toFloat())
                }
                canvas.drawBitmap(reference, null, destination, bitmapPaint)
                canvas.restoreToCount(checkpoint)
                if (displayIsolationMode != 1 && nativeLayerMode == 0)
                    canvas.drawLine(width * 0.5f, 0f, width * 0.5f, height.toFloat(), dividerPaint)
                val now = SystemClock.elapsedRealtimeNanos()
                if (lastDrawTimeNs > 0L) {
                    val gap = (now - lastDrawTimeNs) / 1_000_000L
                    if (gap > maxGapMs) maxGapMs = gap
                }
                lastDrawTimeNs = now
                drawCount++
                if (drawCount % 120L == 0L) {
                    XposedBridge.log("KYANT2887_NATIVE_DRAW host=" + System.identityHashCode(anchor) +
                        " draws=" + drawCount + " x=" + position.x + " y=" + position.y +
                        " deltaX=" + (position.x - lastX) + " deltaY=" + (position.y - lastY) +
                        " maxGapMs=" + maxGapMs + " hardware=" + canvas.isHardwareAccelerated)
                    maxGapMs = 0L
                }
                lastX = position.x; lastY = position.y
            }
        }.apply { isClickable = false; isFocusable = false; setWillNotDraw(false) } else null
        var recomposer: Recomposer? = null
        var scope: CoroutineScope? = null
        fun dispose(reason: String) {
            if (disposed) return
            disposed = true
            if (activeCountRegistered) {
                activeHostCount.decrementAndGet()
                activeCountRegistered = false
            }
            KyantLauncherLensProbe270.setActiveFor273(anchor, false)
            KyantLauncherLensProbe270.clearAttempt(anchor)
            try { followListener?.let { if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnPreDrawListener(it) } } catch (_: Throwable) {}
            try { detachListener?.let { anchor.removeOnAttachStateChangeListener(it) } } catch (_: Throwable) {}
            try { layoutListener?.let { anchor.removeOnLayoutChangeListener(it) } } catch (_: Throwable) {}
            try { compose.disposeComposition() } catch (t: Throwable) {
                XposedBridge.log("KYANT2885_DISPOSE_COMPOSE_ERROR " + t.javaClass.simpleName)
            }
            try { if (frame.parent === folder) folder.removeView(frame) } catch (t: Throwable) {
                XposedBridge.log("KYANT2885_DISPOSE_VIEW_ERROR " + t.javaClass.simpleName)
            }
            try { recomposer?.close() } catch (_: Throwable) {}
            try { scope?.cancel() } catch (_: Throwable) {}
            try { owner.stop() } catch (t: Throwable) {
                XposedBridge.log("KYANT2885_DISPOSE_OWNER_ERROR " + t.javaClass.simpleName)
            }
            XposedBridge.log("KYANT2885_RENDER_DIAG host=" + System.identityHashCode(anchor) +
                " drawIntervals=" + drawIntervals + " over16ms=" + drawGapOver16ms +
                " over33ms=" + drawGapOver33ms + " maxDrawGapMs=" + maxDrawGapMs +
                " sampleCommits=" + sampleCommitCount +
                " avgCommitToDrawMs=" + (if (commitToDrawSamples > 0L) commitToDrawTotalMs / commitToDrawSamples else -1L) +
                " activeHosts=" + activeHostCount.get())
            XposedBridge.log("KYANT2885_PERF frames=" + frameChecks + " geometryChanges=" + geometryChanges +
                " positionChanges=" + positionChanges + " offscreenChecks=" + offscreenChecks +
                " offscreenSkippedUpdates=" + skippedPositionUpdates + " maxPredrawUs=" + maxPredrawUs +
                " avgPredrawUs=" + (if (frameChecks > 0) totalPredrawUs / frameChecks else 0L))
            XposedBridge.log("KYANT2885_DISPOSE_CHECK reason=" + reason +
                " composition=" + compositionSeen.get() +
                " draw=" + drawSeen.get() +
                " removed=" + (frame.parent == null) +
                " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt))
            XposedBridge.log("KYANT2885_HOST_DISPOSED reason=" + reason)
        }
        try {
            owner.start()
            // Explicit parent CompositionContext avoids DecorView's missing LifecycleOwner.
            // AndroidUiDispatcher.Main supplies Android's UI frame clock.
            val dispatcher = AndroidUiDispatcher.Main
            val exceptionHandler = CoroutineExceptionHandler { _, t ->
                XposedBridge.log("KYANT2885_RECOMPOSER_ERROR " + t.javaClass.simpleName + " " + (t.message ?: ""))
                frame.post {
                    try { Settings.Global.putInt(anchor.context.contentResolver, KEY, 0) } catch (_: Throwable) {}
                    dispose("recomposer_error")
                }
            }
            val newScope = CoroutineScope(dispatcher + SupervisorJob() + exceptionHandler)
            scope = newScope
            val newRecomposer = Recomposer(newScope.coroutineContext)
            recomposer = newRecomposer
            compose.setParentCompositionContext(newRecomposer)
            bridgeView?.let { frame.addView(it, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)) }
            // V288.12: modes 2/3 put stable native Canvas BELOW Compose.
            if (nativeLayerMode in 2..3) nativeSampleView?.let { frame.addView(it,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)) }
            frame.addView(compose, FrameLayout.LayoutParams(previewWidth, previewHeight))
            // Mode 1 is a control: native fully covers Compose, same as V288.11 A.
            if (nativeLayerMode !in 2..3) nativeSampleView?.let { frame.addView(it,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)) }

            compose.setContent { LensContent270(reference, sampleWidth.intValue, sampleHeight.intValue,
                displayWidth, displayHeight, samplePosition, sampleRadiusPx.floatValue, samplingMode,
                renderAbMode, motionAbMode, legacySyncAbMode, if (nativeLayerMode in 2..3) 3 else displayIsolationMode, nativeLayerMode, bridgeBitmap, bridgeVersion,
                if (displayIsolationMode in 1..3) 0 else bridgeSyncMode,
                bridgePublishedPosition, bridgeMismatchObserved, { pos -> sampleBridgeAt(pos) }, predrawMatrixSnapshot,
                { glassScreenPosition(lastBounds) }) {
                if (compositionSeen.compareAndSet(false, true)) {
                    XposedBridge.log("KYANT2885_COMPOSITION_CONFIRMED elapsedMs=" +
                        (SystemClock.uptimeMillis() - startedAt))
                }
            } }
            newScope.launch { newRecomposer.runRecomposeAndApplyChanges() }
            // A normal child at index 0 is drawn by FolderIcon.dispatchDraw before icons.
            // Explicitly lay it out because OEM FolderIcon may not manage injected children.
            folder.addView(frame, 0, ViewGroup.LayoutParams(previewWidth, previewHeight))
            val onDetach = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) {
                    dispose("folder_detached")
                }
            }
            detachListener = onDetach
            anchor.addOnAttachStateChangeListener(onDetach)
            val onLayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (!disposed) {
                    // Native layout may overwrite injected child's local placement.
                    // Schedule one frame after layout so the pre-draw reconciliation runs
                    // even when the user does not touch the Launcher.
                    anchor.postInvalidateOnAnimation()
                    frame.postInvalidateOnAnimation()
                }
            }
            layoutListener = onLayout
            anchor.addOnLayoutChangeListener(onLayout)
            frame.layout(initialBounds.left, initialBounds.top,
                initialBounds.left + previewWidth, initialBounds.top + previewHeight)
            // Mark old glass inactive only AFTER the Kyant child is successfully attached.
            KyantLauncherLensProbe270.setActiveFor273(anchor, true)
            activeHostCount.incrementAndGet()
            activeCountRegistered = true
            XposedBridge.log("KYANT2885_MUTUAL_EXCLUSION active=true layer=below_icons fallback=automatic_on_dispose")
            var lastX = anchorXY[0]
            var lastY = anchorXY[1]
            // V288.5: frame cadence counters; no changes to sampling or optical drawing.
        var cadenceWindowNs = SystemClock.elapsedRealtimeNanos()
        var cadencePredraw = 0L
        var cadenceMoving = 0L
        var cadenceCommits = 0L
        var cadenceLongestGapNs = 0L
        var cadenceLastNs = 0L
        var cadenceStartX = Float.NaN
        var cadenceEndX = Float.NaN
        val listener = android.view.ViewTreeObserver.OnPreDrawListener {
                if (!disposed && anchor.isAttachedToWindow) {
                    // The switch is intentionally checked during drawing, not on a fixed timer.
                    val enabled = KyantLauncherLensProbe270.isExperimentEnabled(anchor.context)
                    if (!enabled) {
                        frame.post { dispose("switch_off") }
                        return@OnPreDrawListener true
                    }
                    frameChecks++
                    val cadenceNow = SystemClock.elapsedRealtimeNanos()
                    cadencePredraw++
                    if (cadenceLastNs > 0L) cadenceLongestGapNs = maxOf(cadenceLongestGapNs, cadenceNow - cadenceLastNs)
                    cadenceLastNs = cadenceNow
                    // Observe ColorOS drag overlay class without modifying its view hierarchy.
                    // DragView is commonly a separate bitmap snapshot; original FolderIcon
                    // cannot provide real-time sampling while that snapshot is being dragged.
                    if (frameChecks % 30L == 0L && dragDiagnosticCount < 12L) {
                        try {
                            val group = root as? ViewGroup
                            if (group != null) {
                                val queue = java.util.ArrayDeque<View>()
                                queue.add(group)
                                var examined = 0
                                while (!queue.isEmpty() && examined++ < 200) {
                                    val candidate = queue.removeFirst()
                                    val className = candidate.javaClass.name
                                    if (className.contains("DragView", ignoreCase = true) &&
                                        candidate.visibility == View.VISIBLE) {
                                        dragDiagnosticCount++
                                        XposedBridge.log("KYANT2885_DRAG_VIEW_FOUND class=" + className +
                                            " x=" + candidate.x + " y=" + candidate.y +
                                            " width=" + candidate.width + " height=" + candidate.height)
                                        break
                                    }
                                    if (candidate is ViewGroup) {
                                        for (i in 0 until candidate.childCount) queue.add(candidate.getChildAt(i))
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            XposedBridge.log("KYANT2885_DRAG_DIAG_ERROR " + t.javaClass.simpleName)
                        }
                    }
                    val predrawStartedNs = SystemClock.elapsedRealtimeNanos()
                    // Native FolderIcon child already inherits Workspace translation.
                    // V286.2 also corrects OEM child relayout with no anchor movement.
                    // Screen coordinates are required only when position changes, and do
                    // not need to trigger recomposition for a stationary desktop.
                    anchor.getLocationOnScreen(locationScratch)
                    val xy = locationScratch
                    val actualSample = glassScreenPosition(lastBounds)
                    // Ignore only transform-matrix numerical noise (< 1/64 px).
                    // Keep real fractional Workspace movement live.
                    val sampleDelta = kotlin.math.abs(actualSample.x - lastSample.x) +
                        kotlin.math.abs(actualSample.y - lastSample.y)
                    val moved = xy[0] != lastX || xy[1] != lastY ||
                        sampleDelta > (1f / 64f)
                    if (moved) {
                        positionChanges++
                        // Native invalidation only. No Compose snapshot writes in draw.
                        nativeSampleView?.postInvalidateOnAnimation()
                        bridgeView?.postInvalidateOnAnimation()
                    }
                    // Background geometry is local to FolderIcon; skip expensive reflection
                    // while only the Workspace scrolls, but recheck periodically for OEM resize.
                    // A reparented/moved folder can change its local geometry as well as its global transform.
                    val b = if (frame.visibility == View.INVISIBLE || moved || frameChecks % 20L == 0L)
                        FolderGlassGeometry272.resolve(anchor) else lastBounds
                    val boundsChangedNow = b != lastBounds
                    // OEM FolderIcon can relayout an injected child after Launcher restores
                    // its workspace. Detect that independent of anchor motion or sampling.
                    val expectedW = b.width().coerceAtLeast(1)
                    val expectedH = b.height().coerceAtLeast(1)
                    // Read ancestor Workspace transform only for diagnosis; this must not
                    // become a second translation applied to the child host.
                    var workspace: View? = anchor.parent as? View
                    var depth = 0
                    while (workspace != null && depth++ < 12 &&
                        !workspace.javaClass.name.contains("Workspace", ignoreCase = true)) {
                        workspace = workspace.parent as? View
                    }
                    val workspaceScrollX = workspace?.scrollX ?: Int.MIN_VALUE
                    val workspaceTranslationX = workspace?.translationX ?: Float.NaN
                    val workspaceMoved = workspaceScrollX != lastWorkspaceScrollX ||
                        workspaceTranslationX != lastWorkspaceTranslationX
                    lastWorkspaceScrollX = workspaceScrollX
                    lastWorkspaceTranslationX = workspaceTranslationX
                    val hostMisplaced = frame.left != b.left || frame.top != b.top ||
                        frame.width != expectedW || frame.height != expectedH ||
                        frame.translationX != 0f || frame.translationY != 0f
                    // Full visibility test: partially visible folders must continue sampling.
                    val visible = xy[0] + b.right > 0 && xy[0] + b.left < displayWidth &&
                        xy[1] + b.bottom > 0 && xy[1] + b.top < displayHeight
                    if (!visible) offscreenChecks++
                    // Track the real host/anchor relationship (including parent transforms),
                    // separately from the reference Bitmap's sampling coordinate.
                    // Throttle diagnostics to avoid log I/O every animation frame.
                    val traceNowMs = SystemClock.uptimeMillis()
                    if (visible && (hostMisplaced ||
                        (workspaceMoved && traceNowMs - lastPositionTraceMs >= 180L))) {
                        val hostMatrix = android.graphics.Matrix()
                        frame.transformMatrixToGlobal(hostMatrix)
                        val hostPoint = floatArrayOf(0f, 0f)
                        hostMatrix.mapPoints(hostPoint)
                        val anchorMatrix = android.graphics.Matrix()
                        anchor.transformMatrixToGlobal(anchorMatrix)
                        val anchorPoint = floatArrayOf(b.left.toFloat(), b.top.toFloat())
                        anchorMatrix.mapPoints(anchorPoint)
                        val hostDeltaX = hostPoint[0] - anchorPoint[0]
                        val hostDeltaY = hostPoint[1] - anchorPoint[1]
                        if (hostMisplaced || traceNowMs - lastPositionTraceMs >= 180L) {
                            XposedBridge.log("KYANT2885_HOST_WORKSPACE_TRACE host=" +
                                System.identityHashCode(anchor) +
                                " workspaceScrollX=" + workspaceScrollX +
                                " workspaceTranslationX=" + workspaceTranslationX +
                                " workspaceMoved=" + workspaceMoved +
                                " hostMisplaced=" + hostMisplaced +
                                " hostLocalX=" + frame.left + " expectedLocalX=" + b.left +
                                " hostGlobalX=" + hostPoint[0] +
                                " anchorGlassGlobalX=" + anchorPoint[0] +
                                " globalDx=" + hostDeltaX + " globalDy=" + hostDeltaY +
                                " deltaHostX=" + (hostPoint[0] - lastHostScreenX) +
                                " deltaAnchorX=" + (anchorPoint[0] - lastAnchorScreenX))
                            lastPositionTraceMs = traceNowMs
                        }
                        lastHostScreenX = hostPoint[0]
                        lastAnchorScreenX = anchorPoint[0]
                    }
                    // Only fully offscreen folders skip sampling. Partially visible
                    // folders update every frame, with no fixed temporal throttle.
                    // Commit every visible transform change; offscreen transitions are reconciled on return.
                    val updatePosition = (moved || !firstPositionSettled || boundsChangedNow) && visible
                    if (moved && !updatePosition) skippedPositionUpdates++
                    if (boundsChangedNow || updatePosition || hostMisplaced) {
                        geometryChanges++
                        if (hostMisplaced) {
                            hostLayoutCorrections++
                            val nowMs = SystemClock.uptimeMillis()
                            if (hostLayoutCorrections <= 8 || nowMs - lastHostCorrectionMs >= 1500L) {
                                XposedBridge.log("KYANT2885_HOST_RELAYOUT_FIX host=" + System.identityHashCode(anchor) +
                                    " actual=" + frame.left + "," + frame.top + "," + frame.width + "x" + frame.height +
                                    " expected=" + b.left + "," + b.top + "," + expectedW + "x" + expectedH +
                                    " elapsedMs=" + (nowMs - startedAt))
                                lastHostCorrectionMs = nowMs
                            }
                        }
                        val boundsChanged = b != lastBounds
                        if (updatePosition) { lastX = xy[0]; lastY = xy[1] }
                        lastBounds = android.graphics.Rect(b)
                        // Child geometry uses FolderIcon-local coordinates, not DecorView coordinates.
                        frame.translationX = 0f
                        frame.translationY = 0f
                        val w = b.width().coerceAtLeast(1)
                        val h = b.height().coerceAtLeast(1)
                        if (frame.layoutParams.width != w || frame.layoutParams.height != h) {
                            frame.layoutParams = frame.layoutParams.apply {
                                width = w; height = h
                            }
                            compose.layoutParams = (compose.layoutParams as FrameLayout.LayoutParams).apply {
                                width = w; height = h
                            }
                            sampleWidth.intValue = w; sampleHeight.intValue = h
                        }
                        if (boundsChanged || w != sampleWidth.intValue || h != sampleHeight.intValue) {
                            sampleRadiusPx.floatValue = HookEntry.kyantFolderGlassRadius273(anchor)
                                .coerceIn(0f, minOf(w, h) * 0.49f)
                        }
                        if (boundsChanged || hostMisplaced) {
                            frame.layout(b.left, b.top, b.left + w, b.top + h)
                            if (hostMisplaced) {
                                frame.postInvalidateOnAnimation()
                                compose.postInvalidateOnAnimation()
                            }
                        }
                        run {
                            val position = glassScreenPosition(b)
                            if (samplePosition.value != position) {
                                samplePosition.value = position
                                lastCommitNs = SystemClock.elapsedRealtimeNanos()
                                sampleCommitCount++
                                cadenceCommits++
                            }
                            lastSample = position
                            firstPositionSettled = true
                        }
                    }
                    // V286.1: visibility MUST NOT depend on a matching draw-state snapshot.
                    // An INVISIBLE Compose host may not produce the draw that would make
                    // sampleMatches true, leaving every folder permanently hidden.
                    // Keep geometry valid and composition initialized as the only gate.
                    val layoutMatches = frame.left == b.left && frame.top == b.top &&
                        frame.width == b.width() && frame.height == b.height()
                    val currentSample = glassScreenPosition(b)
                    predrawMatrixSnapshot.set(Pair(currentSample, SystemClock.elapsedRealtimeNanos()))
                    if (cadenceStartX.isNaN()) cadenceStartX = currentSample.x
                    if (!cadenceEndX.isNaN() && kotlin.math.abs(currentSample.x - cadenceEndX) > 0.015625f) cadenceMoving++
                    cadenceEndX = currentSample.x
                    val cadenceElapsed = cadenceNow - cadenceWindowNs
                    if (cadenceElapsed >= 1_000_000_000L) {
                        XposedBridge.log("KYANT2885_PREDRAW_CADENCE host=" + System.identityHashCode(anchor) +
                            " elapsedMs=" + (cadenceElapsed / 1_000_000L) +
                            " predraw=" + cadencePredraw + " moving=" + cadenceMoving +
                            " commits=" + cadenceCommits + " longestGapMs=" + (cadenceLongestGapNs / 1_000_000L) +
                            " startX=" + cadenceStartX + " endX=" + cadenceEndX +
                            " visible=" + (frame.visibility == View.VISIBLE))
                        cadenceWindowNs = cadenceNow
                        cadencePredraw = 0L; cadenceMoving = 0L; cadenceCommits = 0L
                        cadenceLongestGapNs = 0L; cadenceStartX = Float.NaN
                    }
                    if (frameChecks % 180L == 0L) {
                        val integerX = xy[0].toFloat() + b.left
                        val integerY = xy[1].toFloat() + b.top
                        XposedBridge.log("KYANT2885_COORD host=" + System.identityHashCode(anchor) +
                            " matrixX=" + currentSample.x + " matrixY=" + currentSample.y +
                            " integerX=" + integerX + " integerY=" + integerY +
                            " dx=" + (currentSample.x - integerX) + " dy=" + (currentSample.y - integerY) +
                            " committedX=" + samplePosition.value.x + " committedY=" + samplePosition.value.y)
                    }
                    if (layoutMatches && b.width() > 0 && b.height() > 0 && compositionSeen.get()) {
                        if (frame.visibility != View.VISIBLE) {
                            // Commit the CURRENT matrix position before the first visible draw.
                            samplePosition.value = currentSample
                            lastSample = currentSample
                            firstPositionSettled = true
                            frame.visibility = View.VISIBLE
                            frame.postInvalidateOnAnimation()
                            compose.postInvalidateOnAnimation()
                            XposedBridge.log("KYANT2885_FIRST_VISIBLE frames=" + frameChecks +
                                " bounds=" + b + " delayMs=" + (SystemClock.uptimeMillis() - startedAt))
                        }
                    } else if (frameChecks % 120L == 0L) {
                        XposedBridge.log("KYANT2885_VISIBILITY_WAIT layout=" + layoutMatches +
                            " composed=" + compositionSeen.get() + " bounds=" + b +
                            " frame=" + frame.width + "x" + frame.height)
                    }
                    val predrawUs = (SystemClock.elapsedRealtimeNanos() - predrawStartedNs) / 1000L
                    totalPredrawUs += predrawUs
                    if (predrawUs > maxPredrawUs) maxPredrawUs = predrawUs
                }
                true
            }
            followListener = listener
            root.viewTreeObserver.addOnPreDrawListener(listener)
            frame.translationX = 0f
            frame.translationY = 0f
            XposedBridge.log("KYANT2885_HOST_ATTACHED layer=folder_child_index_0 owners=isolated recomposer=explicit view=${anchor.width}x${anchor.height} glassBounds=$initialBounds lens=${previewWidth}x${previewHeight} reference=${reference.width}x${reference.height}")
            // V286: keep the first position live through layout, scroll and pre-draw.
            // The previous 32-ms delayed gate made glass appear seconds late on page switches.
            // OnPreDraw above is the single source of truth for reference-frame coordinates.
            XposedBridge.log("KYANT2885_STARTUP_GATE_REMOVED continuous_predraw_tracking=true")
            // Persistent host remains until disabled, detached or an error occurs.
            XposedBridge.log("KYANT2885_PERSISTENT_READY stop=switch_off_or_folder_detached")
        } catch (t: Throwable) {
            XposedBridge.log("KYANT2885_HOST_ERROR " + t.javaClass.simpleName + " " + (t.message ?: ""))
            try { Settings.Global.putInt(anchor.context.contentResolver, KEY, 0) } catch (_: Throwable) {}
            dispose("setup_error")
        }
    }

    /** Real wallpaper pixels sampled from the existing Launcher Bitmap, without a CPU crop. */
    @Composable private fun LensContent270(
        reference: Bitmap, previewWidth: Int, previewHeight: Int,
        rootWidth: Int, rootHeight: Int, screenPosition: androidx.compose.runtime.State<Offset>,
        cornerRadiusPx: Float, samplingMode: Int, renderAbMode: Int, motionAbMode: Int,
        legacySyncAbMode: Int, displayIsolationMode: Int, nativeLayerMode: Int, bridgeBitmap: Bitmap?,
        bridgeVersion: androidx.compose.runtime.State<Int>,
        bridgeSyncMode: Int,
        bridgePublishedPosition: java.util.concurrent.atomic.AtomicReference<Offset?>,
        bridgeMismatchObserved: java.util.concurrent.atomic.AtomicLong,
        bridgeResample: (Offset) -> Unit,
        predrawMatrixSnapshot: java.util.concurrent.atomic.AtomicReference<Pair<Offset, Long>>,
        currentNativePosition: () -> Offset,
        onComposed: () -> Unit
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        val backdrop = rememberLayerBackdrop()
        val image = androidx.compose.runtime.remember(reference) { reference.asImageBitmap() }
        val bitmapPaint = androidx.compose.runtime.remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
        val wholeDestination = androidx.compose.runtime.remember { RectF() }
        // Freeze only the SOURCE coordinate, not the Host position or the live
        // Compose State. Capture on the first actual draw to avoid startup offset.
        val frozenSource = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicReference<Offset?>(null) }
        val sourceDrawCount = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicLong(0L) }
        val sourceLastDrawNs = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicLong(0L) }
        val drawCadenceWindowNs = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicLong(0L) }
        val drawCadenceCount = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicLong(0L) }
        val drawCadenceOver16 = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicLong(0L) }
        val drawCadenceOver33 = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicLong(0L) }
        val drawCadenceMaxNs = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicLong(0L) }
        val sourceOver16 = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicLong(0L) }
        val sourceOver33 = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicLong(0L) }
        val sourceMaxGapNs = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicLong(0L) }
        // Clip the WHOLE optical pipeline, including wallpaper source and highlight,
        // not just the Kyant lens shape. Prevent pixels escaping rounded corners.
        val cornerDp = (cornerRadiusPx / density).dp
        // A/B 2 isolates the optical composition in an explicit offscreen layer.
        // A/B 0/1 retain the exact baseline parent layer path.
        val opticalContainer = Modifier.size(
            (previewWidth / density).dp,
            (previewHeight / density).dp
        ).then(if (renderAbMode == 2) Modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        } else Modifier).clip(RoundedCornerShape(cornerDp))
        Box(opticalContainer) {
            // Diagnostic only: transparent source may also remove the backdrop input.
            // Never interpret visible highlights alone as genuine refraction.
            val sourceAlpha = when (nativeLayerMode) { 2 -> 0f; 3 -> .02f; else -> 1f }
            ComposeCanvas(Modifier.fillMaxSize().layerBackdrop(backdrop).graphicsLayer { alpha = sourceAlpha }) {
                // Reference is screen-aligned; root offset is accounted for explicitly.
                val screenW = android.content.res.Resources.getSystem().displayMetrics.widthPixels.coerceAtLeast(1)
                val screenH = android.content.res.Resources.getSystem().displayMetrics.heightPixels.coerceAtLeast(1)
                val scaleX = reference.width.toFloat() / screenW
                val scaleY = reference.height.toFloat() / screenH
                // Read snapshot state INSIDE the draw phase. Position changes now
                // invalidate the source drawing, not the whole Compose composition.
                val committedPosition = screenPosition.value
                // V288.6 A/B: reuse the legacy native View matrix sampling at the
                // moment Compose actually draws. The state remains the invalidation
                // trigger; it is NOT written from this draw callback.
                val livePosition = if (legacySyncAbMode == 1) {
                    try { currentNativePosition() } catch (_: Throwable) { committedPosition }
                } else committedPosition
                if (motionAbMode == 1) frozenSource.compareAndSet(null, livePosition)
                val drawPosition = if (motionAbMode == 1) (frozenSource.get() ?: livePosition) else livePosition
                val rootScreenX = drawPosition.x
                val rootScreenY = drawPosition.y
                val x = rootScreenX * scaleX
                val y = rootScreenY * scaleY
                val sw = previewWidth * scaleX
                val sh = previewHeight * scaleY
                // A/B: keep a stable, full-frame bitmap as the source and change only
                // the destination transform. No per-frame source rectangle allocation.
                // This isolates dynamic cropping from Compose/Backdrop frame scheduling.
                if (bridgeBitmap != null) {
                    // State read here registers the draw invalidation dependency.
                    val version = bridgeVersion.value
                    if (bridgeSyncMode == 1 || bridgeSyncMode == 2) {
                        // Mode 1: experimental catch-up, as in V288.9.
                        // Mode 2: observe the exact same coordinate mismatch but NEVER
                        // mutate the bridge bitmap or published position during Compose draw.
                        // Logging is rate-limited to avoid introducing additional jank.
                        val nowPos = try { currentNativePosition() } catch (_: Throwable) { null }
                        val published = bridgePublishedPosition.get()
                        if (nowPos != null && (published == null ||
                            kotlin.math.abs(nowPos.x - published.x) > 0.015f ||
                            kotlin.math.abs(nowPos.y - published.y) > 0.015f)) {
                            val dx = if (published == null) 0f else nowPos.x - published.x
                            val dy = if (published == null) 0f else nowPos.y - published.y
                            if (bridgeSyncMode == 1) {
                                bridgeResample(nowPos)
                                if (kotlin.math.abs(dx) > 0.5f || kotlin.math.abs(dy) > 0.5f)
                                    XposedBridge.log("KYANT2889_FRAME_CATCHUP dx=$dx dy=$dy version=$version")
                            } else {
                                val n = bridgeMismatchObserved.incrementAndGet()
                                if (n % 60L == 1L) XposedBridge.log(
                                    "KYANT28810_MISMATCH_OBSERVE count=$n dx=$dx dy=$dy version=$version noResample=true")
                            }
                        }
                    }
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawBitmap(bridgeBitmap, 0f, 0f, bitmapPaint)
                    }
                    if (drawCadenceCount.get() % 240L == 0L)
                        XposedBridge.log("KYANT2889_COMPOSE_CONSUME version=$version mode=$bridgeSyncMode")
                } else if (renderAbMode != 1 && samplingMode == 1) {
                    val sourceScaleX = size.width / sw.coerceAtLeast(0.001f)
                    val sourceScaleY = size.height / sh.coerceAtLeast(0.001f)
                    wholeDestination.set(-x * sourceScaleX, -y * sourceScaleY,
                        (reference.width - x) * sourceScaleX,
                        (reference.height - y) * sourceScaleY)
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawBitmap(reference, null, wholeDestination, bitmapPaint)
                    }
                } else {
                // Keep the wallpaper's continuous source-to-target transform. The source
                // rectangle is expanded by one pixel for bilinear filtering, and the
                // destination is positioned with float precision. No roundToInt origin.
                val left = kotlin.math.floor(x).toInt().coerceAtLeast(0)
                val top = kotlin.math.floor(y).toInt().coerceAtLeast(0)
                val right = kotlin.math.ceil(x + sw).toInt().coerceAtMost(reference.width)
                val bottom = kotlin.math.ceil(y + sh).toInt().coerceAtMost(reference.height)
                if (right > left && bottom > top && sw > 0f && sh > 0f) {
                    val dst = RectF(
                        (left - x) * size.width / sw,
                        (top - y) * size.height / sh,
                        (right - x) * size.width / sw,
                        (bottom - y) * size.height / sh)
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawBitmap(reference,
                            Rect(left, top, right, bottom), dst,
                            bitmapPaint)
                    }
                }
                }
                val now = SystemClock.elapsedRealtimeNanos()
                val previous = sourceLastDrawNs.getAndSet(now)
                val draws = sourceDrawCount.incrementAndGet()
                if (previous > 0L) {
                    val delta = now - previous
                    if (delta <= 100_000_000L) {
                        if (delta > 16_666_667L) sourceOver16.incrementAndGet()
                        if (delta > 33_333_334L) sourceOver33.incrementAndGet()
                    }
                    sourceMaxGapNs.accumulateAndGet(delta) { a, b -> maxOf(a, b) }
                }
                // Count actual source draw invocations over a one-second wall-clock window.
                if (drawCadenceWindowNs.get() == 0L) drawCadenceWindowNs.compareAndSet(0L, now)
                drawCadenceCount.incrementAndGet()
                if (previous > 0L) {
                    val gap = now - previous
                    if (gap > 16_666_667L) drawCadenceOver16.incrementAndGet()
                    if (gap > 33_333_334L) drawCadenceOver33.incrementAndGet()
                    drawCadenceMaxNs.accumulateAndGet(gap) { a, b -> maxOf(a, b) }
                }
                val windowStart = drawCadenceWindowNs.get()
                if (now - windowStart >= 1_000_000_000L && drawCadenceWindowNs.compareAndSet(windowStart, now)) {
                    XposedBridge.log("KYANT2885_DRAW_CADENCE elapsedMs=" + ((now - windowStart) / 1_000_000L) +
                        " draws=" + drawCadenceCount.getAndSet(0L) +
                        " over16=" + drawCadenceOver16.getAndSet(0L) +
                        " over33=" + drawCadenceOver33.getAndSet(0L) +
                        " maxGapMs=" + (drawCadenceMaxNs.getAndSet(0L) / 1_000_000L) +
                        " sourceX=" + rootScreenX + " sourceY=" + rootScreenY +
                        " mode=" + motionAbMode)
                }
                // Diagnostics only: compare the actual Compose source position to
                // the latest PreDraw matrix observation; no writes or invalidations.
                if (draws % 120L == 0L) {
                    val observed = predrawMatrixSnapshot.get()
                    if (legacySyncAbMode == 1) {
                        XposedBridge.log("KYANT2886_DRAW_MATRIX_COMPARE nativeX=" + livePosition.x +
                            " nativeY=" + livePosition.y + " committedX=" + committedPosition.x +
                            " committedY=" + committedPosition.y +
                            " deltaX=" + (livePosition.x - committedPosition.x) +
                            " deltaY=" + (livePosition.y - committedPosition.y))
                    }
                    val ageMs = if (observed.second > 0L)
                        (now - observed.second) / 1_000_000L else -1L
                    XposedBridge.log("KYANT2885_SOURCE_MATRIX_DELTA draws=" + draws +
                        " matrixX=" + observed.first.x + " matrixY=" + observed.first.y +
                        " sourceX=" + rootScreenX + " sourceY=" + rootScreenY +
                        " motionAb=" + motionAbMode +
                        " liveX=" + livePosition.x + " liveY=" + livePosition.y +
                        " dx=" + (rootScreenX - observed.first.x) +
                        " dy=" + (rootScreenY - observed.first.y) +
                        " matrixAgeMs=" + ageMs)
                }
                if (draws % 240L == 0L) {
                    XposedBridge.log("KYANT2885_SOURCE_DRAW mode=$samplingMode renderAb=$renderAbMode motionAb=$motionAbMode draws=$draws " +
                        "gapMs=" + (if (previous > 0L) (now - previous) / 1_000_000L else -1L) + " " +
                        "over16=${sourceOver16.get()} over33=${sourceOver33.get()} maxGapMs=${sourceMaxGapNs.get()/1_000_000L} " +
                        "sampleX=$rootScreenX sampleY=$rootScreenY bitmapIdentity=" +
                        System.identityHashCode(reference))
                }
            }
            // Mode B: source Bitmap is displayed by Compose, but no Kyant backdrop
            // is captured or rendered. Mode C keeps the original optical pipeline.
            if (displayIsolationMode != 2 || nativeLayerMode in 2..3) Box(Modifier.fillMaxSize().drawBackdrop(
                backdrop = backdrop,
                // The OEM radius is in physical pixels. Convert to dp and clamp to avoid a circle.
                shape = { RoundedRectangle(cornerDp) },
                effects = {
                    val m = size.minDimension
                    blur(0.dp.toPx())
                    if (motionAbMode != 2) {
                        lens(refractionHeight = .1f * m, refractionAmount = .2f * m,
                            depthEffect = true, chromaticAberration = false)
                    }
                },
                highlight = { Highlight.Plain }
            ))
        }
        SideEffect { onComposed() }
    }
}

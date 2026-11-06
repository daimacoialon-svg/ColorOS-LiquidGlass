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
    private val activeHostCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val lastSamplingCommitNs = java.util.concurrent.atomic.AtomicLong(0L)

    private const val KEY = "liquidglass_kyant_persistent_probe_284"

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
            XposedBridge.log("KYANT284_REFERENCE_NOT_READY one_shot_not_consumed")
            return
        }
        val root = anchor.rootView as? ViewGroup ?: return
        // Place the Compose host as the FIRST child of FolderIcon, before native icon children.
        // This preserves the original draw ordering without a DecorView overlay.
        val folder = anchor as? ViewGroup ?: run {
            XposedBridge.log("KYANT284_ABORT anchor_not_viewgroup")
            return
        }
        // Persistent experiment is explicitly opt-in; never disarm the switch on attach.
        if (try { Settings.Global.getInt(anchor.context.contentResolver, KEY, 0) != 1 }
                catch (_: Throwable) { true }) return
        val samplingMode = try { Settings.Global.getInt(anchor.context.contentResolver, SAMPLE_MODE_KEY, 1).coerceIn(0, 1) }
            catch (_: Throwable) { 1 }
        XposedBridge.log("KYANT284_SAMPLE_MODE mode=$samplingMode (0=cropped_source 1=whole_bitmap_transform) referenceIdentity=${System.identityHashCode(reference)}")
        val singleHostMode = try {
            Settings.Global.getInt(anchor.context.contentResolver, SINGLE_HOST_KEY, 0) == 1
        } catch (_: Throwable) { false }
        // Controlled A/B test; do not silently change normal multi-folder behavior.
        if (singleHostMode && activeHostCount.get() > 0) {
            XposedBridge.log("KYANT284_SINGLE_HOST_SKIP active=" + activeHostCount.get())
            return
        }
        XposedBridge.log("KYANT284_PERSISTENT_ARMED key=" + KEY)
        val startedAt = SystemClock.uptimeMillis()
        val displayWidth = root.width.coerceAtLeast(1)
        val displayHeight = root.height.coerceAtLeast(1)
        val initialBounds = FolderGlassGeometry272.resolve(anchor)
        val nativeRadius = HookEntry.kyantFolderGlassRadius273(anchor)
        XposedBridge.log("KYANT284_NATIVE_GEOMETRY rect=$initialBounds radiusPx=$nativeRadius")
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
            Offset(anchorXY[0].toFloat() + initialBounds.left, anchorXY[1].toFloat() + initialBounds.top))
        val sampleWidth = androidx.compose.runtime.mutableIntStateOf(previewWidth)
        val sampleHeight = androidx.compose.runtime.mutableIntStateOf(previewHeight)
        // Initial sample uses the full transform, not the early integer view location.
        val sampleRadiusPx = androidx.compose.runtime.mutableFloatStateOf(
            nativeRadius.coerceIn(0f, minOf(previewWidth, previewHeight) * 0.49f))
        XposedBridge.log("KYANT284_SHAPE size=${previewWidth}x${previewHeight} radiusPx=${sampleRadiusPx.floatValue} radiusDp=${sampleRadiusPx.floatValue / anchor.resources.displayMetrics.density} source=legacy")
        var followListener: android.view.ViewTreeObserver.OnPreDrawListener? = null
        var detachListener: View.OnAttachStateChangeListener? = null
        // Reuse the global cached switch value; no Settings provider IPC per rendered frame.
        var frameChecks = 0L
        var geometryChanges = 0L
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
        var lastSample = Offset(Float.NaN, Float.NaN)
        var firstPositionSettled = false
        var startupSyncAttempts = 0
        var startupLastPosition = Offset(Float.NaN, Float.NaN)
        var startupStableTicks = 0
        val startupBeginMs = SystemClock.uptimeMillis()
        val startupMaxDurationMs = 6000L
        var startupGateOpen = false
        // V281: synchronize visible wallpaper sampling on each pre-draw pass.
        // V279 22ms throttle caused the optical texture to lag behind Workspace.
        val owner = Owner()
        // ComposeView is final in the Compose UI version used by this project.
        // Observe the enclosing FrameLayout instead of subclassing ComposeView.
        val frame = object : FrameLayout(anchor.context) {
            override fun dispatchDraw(canvas: Canvas) {
                super.dispatchDraw(canvas)
                val nowNs = SystemClock.elapsedRealtimeNanos()
                if (lastDrawNs != 0L) {
                    val gapMs = (nowNs - lastDrawNs) / 1_000_000L
                    drawIntervals++
                    if (gapMs > 16L) drawGapOver16ms++
                    if (gapMs > 33L) drawGapOver33ms++
                    if (gapMs > maxDrawGapMs) maxDrawGapMs = gapMs
                    if (drawIntervals % 180L == 0L) {
                        XposedBridge.log("KYANT284_DRAW_CADENCE host=" + System.identityHashCode(anchor) +
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
                    XposedBridge.log("KYANT284_FIRST_DRAW elapsedMs=" +
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
            // Hide the first-frame placeholder until OEM layout has settled.
            visibility = View.INVISIBLE
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
        }
        val compose = ComposeView(anchor.context).apply {
            isClickable = false
            isFocusable = false
        }
        var recomposer: Recomposer? = null
        var scope: CoroutineScope? = null
        var disposed = false
        fun dispose(reason: String) {
            if (disposed) return
            disposed = true
            if (activeCountRegistered) {
                activeHostCount.decrementAndGet()
                activeCountRegistered = false
            }
            KyantLauncherLensProbe270.setActiveFor273(anchor, false)
            KyantLauncherLensProbe270.clearAttempt(anchor)
            try { followListener?.let { if (folder.viewTreeObserver.isAlive) folder.viewTreeObserver.removeOnPreDrawListener(it) } } catch (_: Throwable) {}
            try { detachListener?.let { anchor.removeOnAttachStateChangeListener(it) } } catch (_: Throwable) {}
            try { compose.disposeComposition() } catch (t: Throwable) {
                XposedBridge.log("KYANT284_DISPOSE_COMPOSE_ERROR " + t.javaClass.simpleName)
            }
            try { if (frame.parent === folder) folder.removeView(frame) } catch (t: Throwable) {
                XposedBridge.log("KYANT284_DISPOSE_VIEW_ERROR " + t.javaClass.simpleName)
            }
            try { recomposer?.close() } catch (_: Throwable) {}
            try { scope?.cancel() } catch (_: Throwable) {}
            try { owner.stop() } catch (t: Throwable) {
                XposedBridge.log("KYANT284_DISPOSE_OWNER_ERROR " + t.javaClass.simpleName)
            }
            XposedBridge.log("KYANT284_RENDER_DIAG host=" + System.identityHashCode(anchor) +
                " drawIntervals=" + drawIntervals + " over16ms=" + drawGapOver16ms +
                " over33ms=" + drawGapOver33ms + " maxDrawGapMs=" + maxDrawGapMs +
                " sampleCommits=" + sampleCommitCount +
                " avgCommitToDrawMs=" + (if (commitToDrawSamples > 0L) commitToDrawTotalMs / commitToDrawSamples else -1L) +
                " activeHosts=" + activeHostCount.get())
            XposedBridge.log("KYANT284_PERF frames=" + frameChecks + " geometryChanges=" + geometryChanges +
                " positionChanges=" + positionChanges + " offscreenChecks=" + offscreenChecks +
                " offscreenSkippedUpdates=" + skippedPositionUpdates + " maxPredrawUs=" + maxPredrawUs +
                " avgPredrawUs=" + (if (frameChecks > 0) totalPredrawUs / frameChecks else 0L))
            XposedBridge.log("KYANT284_DISPOSE_CHECK reason=" + reason +
                " composition=" + compositionSeen.get() +
                " draw=" + drawSeen.get() +
                " removed=" + (frame.parent == null) +
                " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt))
            XposedBridge.log("KYANT284_HOST_DISPOSED reason=" + reason)
        }
        try {
            owner.start()
            // Explicit parent CompositionContext avoids DecorView's missing LifecycleOwner.
            // AndroidUiDispatcher.Main supplies Android's UI frame clock.
            val dispatcher = AndroidUiDispatcher.Main
            val exceptionHandler = CoroutineExceptionHandler { _, t ->
                XposedBridge.log("KYANT284_RECOMPOSER_ERROR " + t.javaClass.simpleName + " " + (t.message ?: ""))
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
            frame.addView(compose, FrameLayout.LayoutParams(previewWidth, previewHeight))
            compose.setContent { LensContent270(reference, sampleWidth.intValue, sampleHeight.intValue,
                displayWidth, displayHeight, samplePosition.value.x, samplePosition.value.y, sampleRadiusPx.floatValue, samplingMode) {
                if (compositionSeen.compareAndSet(false, true)) {
                    XposedBridge.log("KYANT284_COMPOSITION_CONFIRMED elapsedMs=" +
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
            frame.layout(initialBounds.left, initialBounds.top,
                initialBounds.left + previewWidth, initialBounds.top + previewHeight)
            // Mark old glass inactive only AFTER the Kyant child is successfully attached.
            KyantLauncherLensProbe270.setActiveFor273(anchor, true)
            activeHostCount.incrementAndGet()
            activeCountRegistered = true
            XposedBridge.log("KYANT284_MUTUAL_EXCLUSION active=true layer=below_icons fallback=automatic_on_dispose")
            var lastX = anchorXY[0]
            var lastY = anchorXY[1]
            var lastBounds = android.graphics.Rect(initialBounds)
            val listener = android.view.ViewTreeObserver.OnPreDrawListener {
                if (!disposed && anchor.isAttachedToWindow) {
                    // The switch is intentionally checked during drawing, not on a fixed timer.
                    val enabled = KyantLauncherLensProbe270.isExperimentEnabled(anchor.context)
                    if (!enabled) {
                        frame.post { dispose("switch_off") }
                        return@OnPreDrawListener true
                    }
                    frameChecks++
                    val predrawStartedNs = SystemClock.elapsedRealtimeNanos()
                    // Native FolderIcon child already inherits Workspace translation.
                    // Screen coordinates are required only when position changes, and do
                    // not need to trigger recomposition for a stationary desktop.
                    anchor.getLocationOnScreen(locationScratch)
                    val xy = locationScratch
                    val actualSample = glassScreenPosition(lastBounds)
                    val moved = xy[0] != lastX || xy[1] != lastY ||
                        actualSample != lastSample
                    if (moved) positionChanges++
                    // Background geometry is local to FolderIcon; skip expensive reflection
                    // while only the Workspace scrolls, but recheck periodically for OEM resize.
                    val b = if (frame.visibility == View.INVISIBLE || (!moved && frameChecks % 20L == 0L))
                        FolderGlassGeometry272.resolve(anchor) else lastBounds
                    val boundsChangedNow = b != lastBounds
                    // Full visibility test: partially visible folders must continue sampling.
                    val visible = xy[0] + b.right > 0 && xy[0] + b.left < displayWidth &&
                        xy[1] + b.bottom > 0 && xy[1] + b.top < displayHeight
                    if (!visible) offscreenChecks++
                    // Only fully offscreen folders skip sampling. Partially visible
                    // folders update every frame, with no fixed temporal throttle.
                    val updatePosition = (moved || !firstPositionSettled) && visible
                    if (moved && !updatePosition) skippedPositionUpdates++
                    if (boundsChangedNow || updatePosition) {
                        geometryChanges++
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
                        if (boundsChanged || frame.left != b.left || frame.top != b.top ||
                            frame.width != w || frame.height != h) {
                            frame.layout(b.left, b.top, b.left + w, b.top + h)
                        }
                        if (visible) {
                            val position = glassScreenPosition(b)
                            if (samplePosition.value != position) {
                                samplePosition.value = position
                                lastCommitNs = SystemClock.elapsedRealtimeNanos()
                                sampleCommitCount++
                            }
                            lastSample = position
                            firstPositionSettled = true
                        }
                    }
                    // Do not expose the lens while the OEM FolderIcon is still settling.
                    // Two successive stable layout passes and a completed composition are required.
                    val layoutMatches = frame.left == b.left && frame.top == b.top &&
                        frame.width == b.width() && frame.height == b.height()
                    // Require the optical source and the current screen transform to agree.
                    val currentSample = glassScreenPosition(b)
                    val sampleMatches = kotlin.math.abs(samplePosition.value.x - currentSample.x) < 0.25f &&
                        kotlin.math.abs(samplePosition.value.y - currentSample.y) < 0.25f
                    if (layoutMatches && sampleMatches && firstPositionSettled && compositionSeen.get()) stableLayoutFrames++
                    else stableLayoutFrames = 0
                    if (frame.visibility != View.VISIBLE && stableLayoutFrames >= 2 && startupGateOpen) {
                        frame.visibility = View.VISIBLE
                        XposedBridge.log("KYANT284_FIRST_LAYOUT_STABLE frames=" + frameChecks +
                            " bounds=" + b + " delayMs=" + (SystemClock.uptimeMillis() - startedAt))
                    }
                    val predrawUs = (SystemClock.elapsedRealtimeNanos() - predrawStartedNs) / 1000L
                    totalPredrawUs += predrawUs
                    if (predrawUs > maxPredrawUs) maxPredrawUs = predrawUs
                }
                true
            }
            followListener = listener
            folder.viewTreeObserver.addOnPreDrawListener(listener)
            frame.translationX = 0f
            frame.translationY = 0f
            XposedBridge.log("KYANT284_HOST_ATTACHED layer=folder_child_index_0 owners=isolated recomposer=explicit view=${anchor.width}x${anchor.height} glassBounds=$initialBounds lens=${previewWidth}x${previewHeight} reference=${reference.width}x${reference.height}")
            // V284: cold-start gate checks the actual screen transform across multiple
            // scheduled frames. It is not bounded to 12 x 32 ms: Launcher page restoration
            // can continue after that window, especially after am force-stop.
            val startupHandler = android.os.Handler(android.os.Looper.getMainLooper())
            val startupSync = object : Runnable {
                override fun run() {
                    if (disposed || !anchor.isAttachedToWindow || startupGateOpen) return
                    startupSyncAttempts++
                    val b = FolderGlassGeometry272.resolve(anchor)
                    val current = glassScreenPosition(b)
                    val same = kotlin.math.abs(current.x - startupLastPosition.x) < 0.25f &&
                        kotlin.math.abs(current.y - startupLastPosition.y) < 0.25f
                    startupStableTicks = if (same) startupStableTicks + 1 else 0
                    startupLastPosition = current
                    if (samplePosition.value != current) {
                        samplePosition.value = current
                        lastSample = current
                        stableLayoutFrames = 0
                        lastCommitNs = SystemClock.elapsedRealtimeNanos()
                        sampleCommitCount++
                    }
                    if (frame.left != b.left || frame.top != b.top ||
                        frame.width != b.width() || frame.height != b.height()) {
                        frame.layout(b.left, b.top, b.right, b.bottom)
                        stableLayoutFrames = 0
                    }
                    val elapsed = SystemClock.uptimeMillis() - startupBeginMs
                    // 4 consecutive 32-ms checks after at least 500 ms avoids
                    // showing an optical layer at an intermediate Workspace position.
                    if (elapsed >= 500L && startupStableTicks >= 4 && compositionSeen.get()) {
                        startupGateOpen = true
                        frame.invalidate()
                        anchor.invalidate()
                        folder.invalidate()
                        XposedBridge.log("KYANT284_STARTUP_GATE_OPEN attempts=$startupSyncAttempts elapsedMs=$elapsed x=${current.x} y=${current.y}")
                        return
                    }
                    if (elapsed >= startupMaxDurationMs) {
                        // Fail closed: never reveal a misaligned glass surface.
                        XposedBridge.log("KYANT284_STARTUP_GATE_TIMEOUT attempts=$startupSyncAttempts stable=$startupStableTicks elapsedMs=$elapsed")
                        dispose("startup_gate_timeout")
                        return
                    }
                    if (startupSyncAttempts % 20 == 0) {
                        XposedBridge.log("KYANT284_STARTUP_GATE_WAIT attempts=$startupSyncAttempts stable=$startupStableTicks x=${current.x} y=${current.y}")
                    }
                    startupHandler.postDelayed(this, 32L)
                }
            }
            startupHandler.post(startupSync)
            // Persistent host remains until disabled, detached or an error occurs.
            XposedBridge.log("KYANT284_PERSISTENT_READY stop=switch_off_or_folder_detached")
        } catch (t: Throwable) {
            XposedBridge.log("KYANT284_HOST_ERROR " + t.javaClass.simpleName + " " + (t.message ?: ""))
            try { Settings.Global.putInt(anchor.context.contentResolver, KEY, 0) } catch (_: Throwable) {}
            dispose("setup_error")
        }
    }

    /** Real wallpaper pixels sampled from the existing Launcher Bitmap, without a CPU crop. */
    @Composable private fun LensContent270(
        reference: Bitmap, previewWidth: Int, previewHeight: Int,
        rootWidth: Int, rootHeight: Int, rootScreenX: Float, rootScreenY: Float,
        cornerRadiusPx: Float, samplingMode: Int, onComposed: () -> Unit
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        val backdrop = rememberLayerBackdrop()
        val image = androidx.compose.runtime.remember(reference) { reference.asImageBitmap() }
        val bitmapPaint = androidx.compose.runtime.remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
        val wholeDestination = androidx.compose.runtime.remember { RectF() }
        val sourceDrawCount = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicLong(0L) }
        val sourceLastDrawNs = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicLong(0L) }
        val sourceOver16 = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicLong(0L) }
        val sourceOver33 = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicLong(0L) }
        val sourceMaxGapNs = androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicLong(0L) }
        // Clip the WHOLE optical pipeline, including wallpaper source and highlight,
        // not just the Kyant lens shape. Prevent pixels escaping rounded corners.
        val cornerDp = (cornerRadiusPx / density).dp
        Box(Modifier.size(
            (previewWidth / density).dp,
            (previewHeight / density).dp
        ).clip(RoundedCornerShape(cornerDp))) {
            ComposeCanvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                // Reference is screen-aligned; root offset is accounted for explicitly.
                val screenW = android.content.res.Resources.getSystem().displayMetrics.widthPixels.coerceAtLeast(1)
                val screenH = android.content.res.Resources.getSystem().displayMetrics.heightPixels.coerceAtLeast(1)
                val scaleX = reference.width.toFloat() / screenW
                val scaleY = reference.height.toFloat() / screenH
                val x = rootScreenX * scaleX
                val y = rootScreenY * scaleY
                val sw = previewWidth * scaleX
                val sh = previewHeight * scaleY
                // A/B: keep a stable, full-frame bitmap as the source and change only
                // the destination transform. No per-frame source rectangle allocation.
                // This isolates dynamic cropping from Compose/Backdrop frame scheduling.
                if (samplingMode == 1) {
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
                    if (delta > 16_666_667L) sourceOver16.incrementAndGet()
                    if (delta > 33_333_334L) sourceOver33.incrementAndGet()
                    sourceMaxGapNs.accumulateAndGet(delta) { a, b -> maxOf(a, b) }
                }
                if (draws % 120L == 0L) {
                    XposedBridge.log("KYANT284_SOURCE_DRAW mode=$samplingMode draws=$draws " +
                        "gapMs=" + (if (previous > 0L) (now - previous) / 1_000_000L else -1L) +
                        "over16=${sourceOver16.get()} over33=${sourceOver33.get()} maxGapMs=${sourceMaxGapNs.get()/1_000_000L} " +
                        "sampleX=$rootScreenX sampleY=$rootScreenY bitmapIdentity=" +
                        System.identityHashCode(reference))
                }
            }
            Box(Modifier.fillMaxSize().drawBackdrop(
                backdrop = backdrop,
                // The OEM radius is in physical pixels. Convert to dp and clamp to avoid a circle.
                shape = { RoundedRectangle(cornerDp) },
                effects = {
                    val m = size.minDimension
                    blur(0.dp.toPx())
                    lens(refractionHeight = .1f * m, refractionAmount = .2f * m,
                        depthEffect = true, chromaticAberration = false)
                },
                highlight = { Highlight.Plain }
            ))
        }
        SideEffect { onComposed() }
    }
}

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
import android.app.Activity
import android.content.ContextWrapper
import android.view.MotionEvent
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import de.robv.android.xposed.XC_MethodHook
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
    private val reboundLayerGuard28818 = AtomicBoolean(false)
    // V288.21: isolated trace of release BEFORE and AFTER actual OEM touch handling.
    // The rendering pipeline remains identical to V288.19 mode 2.
    private const val RELEASE_TRACE_28821_KEY = "liquidglass_kyant_release_trace_28821"
    private const val FRAME_SYNC_AB_28822_KEY = "liquidglass_kyant_frame_sync_ab_28822"
    private val releaseSeq28820 = AtomicLong(0L)
    private val releaseNs28820 = AtomicLong(0L)
    @Volatile private var releaseDx28820 = 0f
    @Volatile private var releaseAction28820 = "NONE"
    @Volatile private var touchDownX28820 = Float.NaN
    @Volatile private var touchDownY28820 = Float.NaN
    @Volatile private var lastTouchEventKey28820 = ""
    @Volatile private var lastTouchDownKey28821 = ""
    @Volatile private var releaseBeforeVia28821 = ""
    @Volatile private var releaseAfterKey28821 = ""
    @Volatile private var activeTouchRoot28820: WeakReference<View>? = null
    @Volatile private var activeTouchWorkspace28820: WeakReference<View>? = null
    private val hookedMethods28820 = ConcurrentHashMap.newKeySet<Method>()
    private class ReleaseHostProbe28821(
        val weakAnchor: WeakReference<View>,
        val snapshot: () -> String
    )
    private val liveHostProbes28821 = ConcurrentHashMap<Int, ReleaseHostProbe28821>()
    @Volatile private var selectedHostIds28821: Set<Int> = emptySet()
    private fun selectVisibleHosts28821(): Set<Int> {
        // Re-select on EVERY release; never freeze the first three attached hosts.
        val all = liveHostProbes28821.entries.mapNotNull { (id, probe) ->
            val anchor = probe.weakAnchor.get() ?: return@mapNotNull null
            try {
                if (!anchor.isAttachedToWindow || !anchor.isShown) return@mapNotNull null
                val r = Rect()
                if (!anchor.getGlobalVisibleRect(r) || r.isEmpty) return@mapNotNull null
                Pair(id, r.width().toLong() * r.height().toLong())
            } catch (_: Throwable) { null }
        }
        return all.sortedByDescending { it.second }.take(3).map { it.first }.toSet()
    }
    private fun releaseSnapshot28821(stage: String, serial: Long, via: String) {
        val workspace = activeTouchWorkspace28820?.get()
        XposedBridge.log("KYANT28821_RELEASE_$stage seq=$serial via=$via " +
            "uptimeNs=${SystemClock.elapsedRealtimeNanos()} dx=$releaseDx28820 " +
            "workspaceX=${workspace?.x} workspaceTx=${workspace?.translationX} " +
            "workspaceScrollX=${workspace?.scrollX} workspaceLayer=${workspace?.layerType} " +
            "selected=${selectedHostIds28821.joinToString()}")
        for (id in selectedHostIds28821) {
            val probe = liveHostProbes28821[id] ?: continue
            val state = try { probe.snapshot() } catch (t: Throwable) { "probeError=${t.javaClass.simpleName}" }
            XposedBridge.log("KYANT28821_RELEASE_HOST seq=$serial phase=$stage host=$id $state")
        }
    }
    private fun observeTouchEvent28821(event: MotionEvent, method: String, phase: String) {
        val action = event.actionMasked
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_UP &&
            action != MotionEvent.ACTION_CANCEL) return
        val eventKey = "${event.downTime}:${event.eventTime}:$action"
        if (action == MotionEvent.ACTION_DOWN) {
            if (phase == "BEFORE" && eventKey != lastTouchDownKey28821) {
                lastTouchDownKey28821 = eventKey
                touchDownX28820 = event.rawX
                touchDownY28820 = event.rawY
            }
            return
        }
        val dx = if (touchDownX28820.isFinite()) event.rawX - touchDownX28820 else Float.NaN
        val dy = if (touchDownY28820.isFinite()) event.rawY - touchDownY28820 else Float.NaN
        if (!dx.isFinite() || kotlin.math.abs(dx) < 12f) return
        if (phase == "BEFORE") {
            if (eventKey == lastTouchEventKey28820) return
            lastTouchEventKey28820 = eventKey
            releaseBeforeVia28821 = method
            releaseDx28820 = dx
            releaseAction28820 = if (action == MotionEvent.ACTION_UP) "UP" else "CANCEL"
            selectedHostIds28821 = selectVisibleHosts28821()
            releaseNs28820.set(SystemClock.elapsedRealtimeNanos())
            val serial = releaseSeq28820.incrementAndGet()
            XposedBridge.log("KYANT28821_TOUCH_RELEASE seq=$serial action=$releaseAction28820 " +
                "dx=$dx dy=$dy rawX=${event.rawX} rawY=${event.rawY} eventMs=${event.eventTime} via=$method")
            releaseSnapshot28821("BEFORE", serial, method)
        } else if (eventKey == lastTouchEventKey28820 &&
            eventKey != releaseAfterKey28821 && method == releaseBeforeVia28821) {
            // The matching after callback belongs to the SAME outermost method.
            // Nested workspace hooks must not consume this event's AFTER marker.
            releaseAfterKey28821 = eventKey
            releaseSnapshot28821("AFTER", releaseSeq28820.get(), method)
        }
    }
    private fun installTouchHooks28820(anchor: View) {
        val root = anchor.rootView
        activeTouchRoot28820 = WeakReference(root)
        var workspace: View? = anchor
        var depth = 0
        while (workspace != null && depth++ < 15 &&
            !workspace.javaClass.name.contains("Workspace", ignoreCase = true)) {
            workspace = workspace.parent as? View
        }
        if (workspace != null) activeTouchWorkspace28820 = WeakReference(workspace)
        var installed = 0
        // Hook the OEM implementation, not android.view.ViewGroup globally.
        if (workspace != null) {
            for (name in arrayOf("dispatchTouchEvent", "onTouchEvent", "onInterceptTouchEvent")) {
                var klass: Class<*>? = workspace.javaClass
                while (klass != null && !klass.name.startsWith("android.")) {
                    for (method in klass.declaredMethods) {
                        if (method.name != name || method.parameterTypes.size != 1 ||
                            method.parameterTypes[0] != MotionEvent::class.java) continue
                        if (hookedMethods28820.add(method)) {
                            try {
                                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                                    override fun beforeHookedMethod(param: MethodHookParam) {
                                        if (param.thisObject !== activeTouchWorkspace28820?.get()) return
                                        val event = param.args.firstOrNull() as? MotionEvent ?: return
                                        try { observeTouchEvent28821(event, name, "BEFORE") } catch (t: Throwable) { XposedBridge.log("KYANT28821_TOUCH_ERROR before:${t.javaClass.simpleName}") }
                                    }
                                    override fun afterHookedMethod(param: MethodHookParam) {
                                        if (param.thisObject !== activeTouchWorkspace28820?.get()) return
                                        val event = param.args.firstOrNull() as? MotionEvent ?: return
                                        try { observeTouchEvent28821(event, name, "AFTER") } catch (t: Throwable) { XposedBridge.log("KYANT28821_TOUCH_ERROR after:${t.javaClass.simpleName}") }
                                    }
                                })
                                XposedBridge.log("KYANT28821_HOOK_OK method=${klass.name}.$name")
                            } catch (t: Throwable) {
                                hookedMethods28820.remove(method)
                                XposedBridge.log("KYANT28821_HOOK_FAILED method=${klass.name}.$name error=${t.javaClass.simpleName}")
                            }
                        }
                        installed++
                    }
                    klass = klass.superclass
                }
            }
        }
        // Fallback: Activity-level release event, scoped by the same root window.
        var context = anchor.context
        var activity: Activity? = null
        for (i in 0 until 10) {
            if (context is Activity) { activity = context as Activity; break }
            val wrapped = context as? ContextWrapper ?: break
            context = wrapped.baseContext
        }
        val act = activity
        if (act != null) {
            val method = runCatching {
                act.javaClass.getMethod("dispatchTouchEvent", MotionEvent::class.java)
            }.getOrNull()
            if (method != null && hookedMethods28820.add(method)) {
                try {
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val current = param.thisObject as? Activity ?: return
                            if (current.window?.decorView?.rootView !== activeTouchRoot28820?.get()) return
                            val event = param.args.firstOrNull() as? MotionEvent ?: return
                            try { observeTouchEvent28821(event, "activity", "BEFORE") } catch (t: Throwable) { XposedBridge.log("KYANT28821_TOUCH_ERROR activityBefore:${t.javaClass.simpleName}") }
                        }
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val current = param.thisObject as? Activity ?: return
                            if (current.window?.decorView?.rootView !== activeTouchRoot28820?.get()) return
                            val event = param.args.firstOrNull() as? MotionEvent ?: return
                            try { observeTouchEvent28821(event, "activity", "AFTER") } catch (t: Throwable) { XposedBridge.log("KYANT28821_TOUCH_ERROR activityAfter:${t.javaClass.simpleName}") }
                        }
                    })
                    XposedBridge.log("KYANT28821_HOOK_OK method=${method.declaringClass.name}.dispatchTouchEvent")
                } catch (t: Throwable) {
                    hookedMethods28820.remove(method)
                    XposedBridge.log("KYANT28821_HOOK_FAILED method=activity error=${t.javaClass.simpleName}")
                }
            }
        }
        XposedBridge.log("KYANT28821_HOOK_STATUS workspace=${workspace?.javaClass?.simpleName ?: "absent"} " +
            "workspaceMethods=$installed activity=${act?.javaClass?.simpleName ?: "absent"}")
    }
    // Opt-in diagnostic: 1 allows only one active optical host, 0 retains all hosts.
    private const val SINGLE_HOST_KEY = "liquidglass_kyant_single_host_282"
    private const val SAMPLE_MODE_KEY = "liquidglass_kyant_sampling_mode_283"
    private const val RENDER_AB_KEY = "liquidglass_kyant_render_ab_2883"
    private const val MOTION_AB_KEY = "liquidglass_kyant_motion_ab_2884"
    private const val LEGACY_SYNC_AB_KEY = "liquidglass_kyant_legacy_sync_ab_2886"
    private const val NATIVE_COMPARISON_KEY = "liquidglass_kyant_native_compare_2887"
    private const val NATIVE_BRIDGE_KEY = "liquidglass_kyant_native_bridge_2888"
    private const val RIGHT_START_TRACE_KEY = "liquidglass_kyant_right_start_trace_28813"
    private const val MATRIX_TRACE_28814_KEY = "liquidglass_kyant_matrix_trace_28814"
    private const val FRAME_TRACE_28815_KEY = "liquidglass_kyant_frame_trace_28815"
    private const val REDRAW_AB_28816_KEY = "liquidglass_kyant_right_redraw_28816"
    private const val LAYER_TRACE_28817_KEY = "liquidglass_kyant_layer_trace_28817"
    private const val REBOUND_AB_28818_KEY = "liquidglass_kyant_rebound_layer_ab_28818"
    // -1 preserves the legacy modes; 0..3 isolate the wallpaper display pipeline.
    private const val PIPELINE_AB_28819_KEY = "liquidglass_kyant_pipeline_ab_28819"
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
        KyantOptics28827.ensure(anchor.context)
        val pipelineMode28819 = try {
            Settings.Global.getInt(anchor.context.contentResolver, PIPELINE_AB_28819_KEY, -1).coerceIn(-1, 3)
        } catch (_: Throwable) { -1 }
        val frameSync28822 = if (pipelineMode28819 == 2) try {
            Settings.Global.getInt(anchor.context.contentResolver, FRAME_SYNC_AB_28822_KEY, 0).coerceIn(0, 1)
        } catch (_: Throwable) { 0 } else 0
        if (pipelineMode28819 == 2) XposedBridge.log("KYANT28822_FRAME_SYNC host=${System.identityHashCode(anchor)} mode=$frameSync28822 pipeline=$pipelineMode28819")
        val releaseTrace28820 = try {
            Settings.Global.getInt(anchor.context.contentResolver, RELEASE_TRACE_28821_KEY, 0) == 1
        } catch (_: Throwable) { false }
        if (releaseTrace28820) {
            installTouchHooks28820(anchor)
            XposedBridge.log("KYANT28821_TRACE_ARMED host=${System.identityHashCode(anchor)} pipeline=$pipelineMode28819")
        }
        val samplingMode = if (pipelineMode28819 >= 0) 1 else try {
            Settings.Global.getInt(anchor.context.contentResolver, SAMPLE_MODE_KEY, 1).coerceIn(0, 1)
        } catch (_: Throwable) { 1 }
        // 0: original optics + original bitmap sampling; 1: cropped bitmap source,
        // 2: original bitmap sampling with isolated offscreen compositing layer.
        // Read once on Host creation; switching modes requires Launcher restart.
        val renderAbMode = if (pipelineMode28819 >= 0) 0 else try {
            Settings.Global.getInt(anchor.context.contentResolver, RENDER_AB_KEY, 0).coerceIn(0, 2)
        } catch (_: Throwable) { 0 }
        // Motion diagnostic is independent of the earlier render A/B switch.
        // 0 = baseline, 1 = freeze wallpaper source at its first draw,
        // 2 = live wallpaper source without lens refraction.
        val motionAbMode = if (pipelineMode28819 >= 0) 2 else try {
            Settings.Global.getInt(anchor.context.contentResolver, MOTION_AB_KEY, 0).coerceIn(0, 2)
        } catch (_: Throwable) { 0 }
        val bridgeSyncMode = if (pipelineMode28819 >= 0) 0 else try {
            Settings.Global.getInt(anchor.context.contentResolver, BRIDGE_SYNC_KEY, 1).coerceIn(0, 2)
        } catch (_: Throwable) { 1 }
        XposedBridge.log("KYANT28810_BRIDGE_SYNC mode=$bridgeSyncMode")
        // 0=legacy switches, 1=native Canvas full overlay, 2=Compose Bitmap without
        // Kyant optics, 3=Compose Bitmap with full Kyant optics.
        val displayIsolationMode = if (pipelineMode28819 >= 0) {
            if (pipelineMode28819 == 0) 1 else 2
        } else try {
            Settings.Global.getInt(anchor.context.contentResolver, DISPLAY_ISOLATION_KEY, 3).coerceIn(0, 3)
        } catch (_: Throwable) { 3 }
        XposedBridge.log("KYANT28811_DISPLAY_ISOLATION mode=$displayIsolationMode")
        // 0=legacy isolation, 1=native above Compose, 2=native behind Compose
        // with 0-alpha source experiment, 3=native behind Compose with 2% source.
        val nativeLayerMode = if (pipelineMode28819 >= 0) 0 else try {
            Settings.Global.getInt(anchor.context.contentResolver, NATIVE_LAYER_KEY, 0).coerceIn(0, 3)
        } catch (_: Throwable) { 0 }
        XposedBridge.log("KYANT28812_NATIVE_LAYER mode=$nativeLayerMode isolation=$displayIsolationMode")
        val rightStartTraceEnabled = if (pipelineMode28819 >= 0) false else try {
            Settings.Global.getInt(anchor.context.contentResolver, RIGHT_START_TRACE_KEY, 1) == 1
        } catch (_: Throwable) { true }
        XposedBridge.log("KYANT28813_TRACE_ENABLED enabled=$rightStartTraceEnabled host=${System.identityHashCode(anchor)} nativeLayer=$nativeLayerMode")
        var onsetPrevX = Float.NaN
        var onsetPrevY = Float.NaN
        var onsetPrevHostX = Float.NaN
        var onsetPrevScrollX = Int.MIN_VALUE
        var onsetPrevNs = 0L
        var onsetQuietFrames = 0
        var onsetDirection = 0
        var onsetFramesRemaining = 0
        var onsetSerial = 0
        var onsetLogCount = 0
        var onsetNativeDrawCount = 0
        val matrixTrace28814 = if (pipelineMode28819 >= 0) false else try {
            Settings.Global.getInt(anchor.context.contentResolver, MATRIX_TRACE_28814_KEY, 1) == 1
        } catch (_: Throwable) { true }
        XposedBridge.log("KYANT28814_MATRIX_TRACE enabled=$matrixTrace28814 host=${System.identityHashCode(anchor)}")
        var trace28814PrevSample = Float.NaN
        var trace28814PrevHost = Float.NaN
        var trace28814PrevAnchor = Float.NaN
        var trace28814PredrawSerial = 0L
        var trace28814DrawSerial = 0L
        var trace28814DrawRemaining = 0
        var trace28814LastDirection = 0
        var trace28814Events = 0
        val trace28814AnchorMatrix = Matrix()
        val trace28814HostMatrix = Matrix()
        val trace28814AnchorPoint = FloatArray(2)
        val trace28814HostPoint = FloatArray(2)
        val trace28814RootScreen = IntArray(2)
        val trace28814RootWindow = IntArray(2)
        // V288.15: diagnostics only. A UI-thread draw callback does not prove GPU presentation.
        val frameTrace28815 = if (pipelineMode28819 >= 0) false else try {
            Settings.Global.getInt(anchor.context.contentResolver, FRAME_TRACE_28815_KEY, 0) == 1
        } catch (_: Throwable) { false }
        val redrawMode28816 = if (pipelineMode28819 >= 0) 0 else try {
            Settings.Global.getInt(anchor.context.contentResolver, REDRAW_AB_28816_KEY, 0).coerceIn(0, 1)
        } catch (_: Throwable) { 0 }
        var redraw28816Requests = 0
        var redraw28816Draws = 0
        var redraw28816Pending = false
        var redraw28816PendingNs = 0L
        var redraw28816PendingX = Float.NaN
        val layerTrace28817 = if (pipelineMode28819 >= 0) false else try { Settings.Global.getInt(anchor.context.contentResolver, LAYER_TRACE_28817_KEY, 0) == 1 } catch (_: Throwable) { false }
        var layerTrace28817Count = 0
        var layerTrace28817Remaining = 0
        var layerTrace28817Direction = 0
        val reboundMode28818 = if (pipelineMode28819 >= 0) 0 else try { Settings.Global.getInt(anchor.context.contentResolver, REBOUND_AB_28818_KEY, 0).coerceIn(0, 1) } catch (_: Throwable) { 0 }
        var rebound28818LastX = Float.NaN
        var rebound28818RightDrag = false
        var rebound28818LastPositiveNs = 0L
        var rebound28818Count = 0
        var rebound28818Target: View? = null
        var rebound28818OldLayer = View.LAYER_TYPE_NONE
        var rebound28818Restore: Runnable? = null
        val trace28815Host = System.identityHashCode(anchor)
        XposedBridge.log("KYANT28818_AB host=$trace28815Host mode=$reboundMode28818")
        if (layerTrace28817) XposedBridge.log("KYANT28817_TRACE_ENABLED host=$trace28815Host nativeLayer=$nativeLayerMode")
        XposedBridge.log("KYANT28816_REDRAW_AB host=$trace28815Host mode=$redrawMode28816 nativeLayer=$nativeLayerMode")

        var trace28815PreSerial = 0L
        var trace28815DrawSerial = 0L
        var trace28815LastPreNs = 0L
        var trace28815LastDrawNs = 0L
        var trace28815LastPreX = Float.NaN
        var trace28815LastDrawX = Float.NaN
        var trace28815LastPreSerialAtDraw = 0L
        var trace28815Quiet = 0
        var trace28815Remaining = 0
        var trace28815Direction = 0
        var trace28815Events = 0
        if (frameTrace28815) XposedBridge.log(
            "KYANT28815_TRACE_ENABLED host=$trace28815Host native=${nativeLayerMode in 1..3} sourceId=${System.identityHashCode(reference)}")
        val onsetSourceId = System.identityHashCode(reference)
        val nativeBridgeMode = if (pipelineMode28819 >= 0) {
            if (pipelineMode28819 <= 1) 0 else 1
        } else if (nativeLayerMode in 1..3) 0 else if (displayIsolationMode in 1..3) {
            if (displayIsolationMode == 1) 0 else 1
        } else try {
            Settings.Global.getInt(anchor.context.contentResolver, NATIVE_BRIDGE_KEY, 0).coerceIn(0, 1)
        } catch (_: Throwable) { 0 }
        XposedBridge.log("KYANT2888_NATIVE_BRIDGE mode=$nativeBridgeMode (0=baseline 1=native_bitmap_bridge)")
        val nativeCompareMode = if (pipelineMode28819 >= 0) 0 else try {
            Settings.Global.getInt(anchor.context.contentResolver, NATIVE_COMPARISON_KEY, 0).coerceIn(0, 1)
        } catch (_: Throwable) { 0 }
        XposedBridge.log("KYANT2887_NATIVE_COMPARE mode=$nativeCompareMode (0=full_kyant 1=left_native_right_kyant)")
        val legacySyncAbMode = if (pipelineMode28819 >= 0) 1 else try {
            Settings.Global.getInt(anchor.context.contentResolver, LEGACY_SYNC_AB_KEY, 0).coerceIn(0, 1)
        } catch (_: Throwable) { 0 }
        if (pipelineMode28819 >= 0) XposedBridge.log(
            "KYANT28819_PIPELINE host=${System.identityHashCode(anchor)} mode=$pipelineMode28819 " +
            "native=$nativeLayerMode isolation=$displayIsolationMode bridge=$nativeBridgeMode " +
            "optics=false bridgeSync=$bridgeSyncMode sourceId=${System.identityHashCode(reference)}")
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
        // Large square folders expose missed source position updates much more
        // visibly than small or strip-shaped previews.  Keep the original
        // shader, but avoid repeated OEM geometry reflection and multiple
        // global-matrix reads inside the same pre-draw frame.
        val largeSquare28829 = previewWidth >= displayWidth * .30f &&
            previewHeight >= previewWidth * .72f && previewHeight <= previewWidth * 1.4f &&
            (try { Settings.Global.getInt(anchor.context.contentResolver,
                "liquidglass_kyant_large_square_opt_28829", 1) != 0 }
             catch (_: Throwable) { true })
        // V288.30: large squares expose pixel stepping at the changing
        // cropped-source Rect boundaries. Keep a stable full reference Bitmap
        // and apply a floating-point destination transform instead (A/B).
        // No extra Bitmap copy, no downsampling, no delayed frame.
        val largeContinuousSource28830 = largeSquare28829 && try {
            Settings.Global.getInt(anchor.context.contentResolver,
                "liquidglass_kyant_large_square_source_28830", 1) == 1
        } catch (_: Throwable) { true }
        if (largeSquare28829) XposedBridge.log(
            "KYANT28830_LARGE_SOURCE mode=${if (largeContinuousSource28830) 1 else 0} " +
            "size=${previewWidth}x${previewHeight} reference=${reference.width}x${reference.height}")
        var largeGeometryDirty28829 = false
        if (largeSquare28829) XposedBridge.log("KYANT28829_LARGE_FOLDER_OPTIMIZED size=${previewWidth}x${previewHeight} screenW=$displayWidth")
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
        // V288.24: the native sampler consumes the same pre-draw coordinate snapshot.
        // This is a local View/Canvas-only change; no mutable Bitmap bridge is involved.
        // V288.26: compare pre-draw snapshot with the native View's actual
        // transformed origin. Mode 1 uses the same View that consumes Canvas.
        // Mode 0 is the unchanged V288.25 control; mode 2 re-reads anchor.
        val nativeCoordinateMode28826 = try {
            Settings.Global.getInt(anchor.context.contentResolver,
                "liquidglass_kyant_native_coordinate_ab_28826", 0).coerceIn(0, 2)
        } catch (_: Throwable) { 0 }
        XposedBridge.log("KYANT28826_COORD_MODE host=$trace28815Host mode=$nativeCoordinateMode28826")
        var nativePredrawPosition28824 = samplePosition.value
        var nativePredrawSerial28824 = 0L
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
        lateinit var releaseTracePulse28820: (String, Offset?, String) -> Unit
        val frame = object : FrameLayout(anchor.context) {
            override fun dispatchDraw(canvas: Canvas) {
                // Never mutate Compose state during View.dispatchDraw: the
                // V288 experiment stalled the source snapshot on real devices.
                super.dispatchDraw(canvas)
                val nowNs = SystemClock.elapsedRealtimeNanos()
                if (releaseTrace28820) releaseTracePulse28820("HOST_DRAW", glassScreenPosition(lastBounds),
                    "frameVisible=$visibility frameLayer=$layerType childCount=$childCount")
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
            // Keep the composition owner alive; native A/B modes render their own pixels.
            if (pipelineMode28819 == 0 || pipelineMode28819 == 3) alpha = 0f
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
        var traceBudget28821 = 0
        var traceSeenSeq28821 = 0L
        fun releaseTrace28820(stage: String, pos: Offset?, details: String = "") {
            if (!releaseTrace28820 || trace28815Host !in selectedHostIds28821) return
            val seq = releaseSeq28820.get()
            if (seq == 0L) return
            val ageNs = SystemClock.elapsedRealtimeNanos() - releaseNs28820.get()
            if (ageNs < 0L || ageNs > 260_000_000L) return
            // Reset budget for EVERY release, so later right-swipes are traced too.
            if (traceSeenSeq28821 != seq) {
                traceSeenSeq28821 = seq
                traceBudget28821 = 0
            }
            if (traceBudget28821 >= 160) return
            traceBudget28821++
            val nowVersion = bridgeVersion.intValue
            val published = bridgePublishedPosition.get()
            // Pixel fingerprints only on actual native sampling / Compose drawing,
            // not every PreDraw; diagnostic overhead must not create extra jank.
            val fingerprint = if (stage == "NATIVE_SAMPLE" || stage == "COMPOSE_DRAW") try {
                bridgeBitmap?.let { b ->
                    val a = b.getPixel(b.width / 2, b.height / 2)
                    val c = b.getPixel(b.width / 4, b.height / 4)
                    val d = b.getPixel((b.width * 3) / 4, (b.height * 3) / 4)
                    Integer.toHexString(a xor (c shl 1) xor (d ushr 1))
                } ?: "null"
            } catch (_: Throwable) { "error" } else "skip"
            XposedBridge.log("KYANT28821_$stage host=$trace28815Host seq=$seq " +
                "afterReleaseMs=${ageNs / 1_000_000L} ageNs=$ageNs " +
                "dx=$releaseDx28820 action=$releaseAction28820 " +
                "x=${pos?.x} y=${pos?.y} publishedX=${published?.x} publishedY=${published?.y} " +
                "deltaX=${if (pos != null && published != null) pos.x - published.x else Float.NaN} " +
                "version=$nowVersion publishes=${bridgeDrawCount.get()} bitmapSig=$fingerprint $details")
        }
        releaseTracePulse28820 = { stage, pos, details -> releaseTrace28820(stage, pos, details) }
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
                // V288.22 mode 1: Compose draw itself samples the reference frame.
                // Do not publish an independent native Bitmap frame first.
                if (frameSync28822 != 1) sampleBridgeAt(pos)
                releaseTrace28820("NATIVE_SAMPLE", pos, "draw=${count + 1L} bridgeW=${bridgeBitmap?.width} bridgeH=${bridgeBitmap?.height}")
                // Mode 3: sample into exactly the same bridge Bitmap as mode 2,
                // but render it in the native View's own Canvas (no Compose handoff).
                if (pipelineMode28819 == 3) {
                    // Native bridge mode: the Bitmap is allocated when bridgeView exists.
                    val buffer = bridgeBitmap
                    if (buffer != null) canvas.drawBitmap(buffer, 0f, 0f, bridgePaint)
                }
                count++
                if (frameSync28822 != 1 && pipelineMode28819 != 3 && bridgeNotifyPending.compareAndSet(false, true)) {
                    post {
                        bridgeNotifyPending.set(false)
                        if (!disposed) {
                            bridgeVersion.intValue++
                            releaseTrace28820("COMPOSE_NOTIFY", bridgePublishedPosition.get(), "pending=false")
                        }
                    }
                }
                if (count % 60L == 1L && pipelineMode28819 >= 0) XposedBridge.log(
                    "KYANT28819_NATIVE_BRIDGE host=$trace28815Host mode=$pipelineMode28819 " +
                    "draws=$count x=${pos.x} y=${pos.y} version=${bridgeVersion.intValue}")
                if (count % 120L == 0L) XposedBridge.log(
                    "KYANT2889_NATIVE_PUBLISH draws=$count x=${pos.x} y=${pos.y} version=${bridgeVersion.intValue}")
            }
        }.apply { isClickable = false; isFocusable = false; setWillNotDraw(false) } else null
        if (releaseTrace28820) {
            liveHostProbes28821[trace28815Host] = ReleaseHostProbe28821(WeakReference(anchor)) {
                val livePos = glassScreenPosition(lastBounds)
                val published = bridgePublishedPosition.get()
                "sampleX=${livePos.x} sampleY=${livePos.y} publishedX=${published?.x} " +
                    "publishedY=${published?.y} committedX=${samplePosition.value.x} " +
                    "version=${bridgeVersion.intValue} samples=${bridgeDrawCount.get()} " +
                    "hostX=${frame.x} hostTx=${frame.translationX} frameVisible=${frame.visibility} " +
                    "anchorVisible=${anchor.isShown} frameLayer=${frame.layerType}"
            }
            XposedBridge.log("KYANT28821_HOST_REGISTER host=$trace28815Host source=${System.identityHashCode(reference)}")
        }
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
            private val nativeMatrix28826 = Matrix()
            private val nativePoint28826 = FloatArray(2)
            private val nativeScreen28826 = IntArray(2)
            private val nativeWindow28826 = IntArray(2)
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (width <= 0 || height <= 0 || disposed) return
                // Do not read a second Workspace transform after onPreDraw has
                // already scheduled this frame's native redraw.
                val position = when (nativeCoordinateMode28826) {
                    1 -> try {
                        // Compute from the native sampling View's own transformed
                        // origin; avoids mixing anchor and host transform epochs.
                        nativeMatrix28826.reset()
                        transformMatrixToGlobal(nativeMatrix28826)
                        nativePoint28826[0] = 0f
                        nativePoint28826[1] = 0f
                        nativeMatrix28826.mapPoints(nativePoint28826)
                        root.getLocationOnScreen(nativeScreen28826)
                        root.getLocationInWindow(nativeWindow28826)
                        Offset(nativePoint28826[0] + nativeScreen28826[0] - nativeWindow28826[0],
                            nativePoint28826[1] + nativeScreen28826[1] - nativeWindow28826[1])
                    } catch (_: Throwable) { nativePredrawPosition28824 }
                    2 -> try { glassScreenPosition(lastBounds) }
                        catch (_: Throwable) { nativePredrawPosition28824 }
                    else -> nativePredrawPosition28824
                }
                if (releaseTrace28820) releaseTrace28820("COORD28826", position,
                    "mode=$nativeCoordinateMode28826 snapshotX=${nativePredrawPosition28824.x} deltaX=${position.x-nativePredrawPosition28824.x}")
                // V288.25: native Canvas diagnostics also work in pipeline=-1.
                // Record only during the bounded release window; no bitmap readback.
                if (releaseTrace28820) releaseTrace28820("NATIVE_DRAW", position,
                    "nativeLayer=$layerType frameLayer=${frame.layerType} hardware=${canvas.isHardwareAccelerated} sourceId=${System.identityHashCode(reference)}")
                if (rightStartTraceEnabled && onsetFramesRemaining > 0 && onsetNativeDrawCount < 90) {
                    XposedBridge.log("KYANT28813_NATIVE_DRAW onset=$onsetSerial direction=$onsetDirection x=${position.x} y=${position.y} sourceId=$onsetSourceId")
                    onsetNativeDrawCount++
                }
                if (matrixTrace28814 && trace28814DrawRemaining > 0 && trace28814Events < 180) {
                    try {
                        trace28814DrawSerial++
                        trace28814AnchorMatrix.reset()
                        anchor.transformMatrixToGlobal(trace28814AnchorMatrix)
                        trace28814AnchorPoint[0] = lastBounds.left.toFloat()
                        trace28814AnchorPoint[1] = lastBounds.top.toFloat()
                        trace28814AnchorMatrix.mapPoints(trace28814AnchorPoint)
                        trace28814HostMatrix.reset()
                        frame.transformMatrixToGlobal(trace28814HostMatrix)
                        trace28814HostPoint[0] = 0f
                        trace28814HostPoint[1] = 0f
                        trace28814HostMatrix.mapPoints(trace28814HostPoint)
                        root.getLocationOnScreen(trace28814RootScreen)
                        root.getLocationInWindow(trace28814RootWindow)
                        val windowOffset = trace28814RootScreen[0] - trace28814RootWindow[0]
                        val anchorScreen = trace28814AnchorPoint[0] + windowOffset
                        val hostScreen = trace28814HostPoint[0] + windowOffset
                        XposedBridge.log("KYANT28814_DRAW_MATRIX host=${System.identityHashCode(anchor)} onset=$onsetSerial dir=$trace28814LastDirection predraw=$trace28814PredrawSerial draw=$trace28814DrawSerial sampleX=${position.x} anchorX=$anchorScreen hostX=$hostScreen sampleAnchorDelta=${position.x-anchorScreen} sampleHostDelta=${position.x-hostScreen} rootOffset=$windowOffset frameLeft=${frame.left} frameTx=${frame.translationX} sourceId=$onsetSourceId")
                        trace28814Events++
                    } catch (t: Throwable) {
                        XposedBridge.log("KYANT28814_DRAW_ERROR ${t.javaClass.simpleName}")
                    }
                    trace28814DrawRemaining--
                }
                if (frameTrace28815 && trace28815Remaining > 0 && trace28815Events < 160) {
                    val now28815 = SystemClock.elapsedRealtimeNanos()
                    trace28815DrawSerial++
                    val gapMs = if (trace28815LastDrawNs > 0L)
                        (now28815 - trace28815LastDrawNs) / 1000000L else -1L
                    val drawDx = if (trace28815LastDrawX.isFinite())
                        position.x - trace28815LastDrawX else 0f
                    val preAgeMs = if (trace28815LastPreNs > 0L)
                        (now28815 - trace28815LastPreNs) / 1000000L else -1L
                    XposedBridge.log(
                        "KYANT28815_NATIVE_DRAW host=$trace28815Host dir=$trace28815Direction draw=$trace28815DrawSerial pre=$trace28815PreSerial preAdvance=${trace28815PreSerial-trace28815LastPreSerialAtDraw} x=${position.x} dx=$drawDx preX=$trace28815LastPreX preDiff=${position.x-trace28815LastPreX} preAgeMs=$preAgeMs drawGapMs=$gapMs hardware=${canvas.isHardwareAccelerated} layerType=$layerType sourceId=${System.identityHashCode(reference)}")
                    trace28815LastPreSerialAtDraw = trace28815PreSerial
                    trace28815LastDrawNs = now28815
                    trace28815LastDrawX = position.x
                    trace28815Remaining--
                    trace28815Events++
                }
                if (redraw28816Pending) {
                    redraw28816Draws++
                    val latencyMs = (SystemClock.elapsedRealtimeNanos() - redraw28816PendingNs) / 1000000L
                    XposedBridge.log("KYANT28816_REDRAW_DRAW host=$trace28815Host request=$redraw28816Requests draw=$redraw28816Draws latencyMs=$latencyMs requestX=$redraw28816PendingX drawX=${position.x} delta=${position.x-redraw28816PendingX}")
                    redraw28816Pending = false
                }
                if (layerTrace28817 && layerTrace28817Remaining > 0 && layerTrace28817Count < 120) {
                    val hostLoc = IntArray(2); val nativeLoc = IntArray(2)
                    frame.getLocationOnScreen(hostLoc); getLocationOnScreen(nativeLoc)
                    XposedBridge.log("KYANT28817_DRAW_STATE host=$trace28815Host dir=$layerTrace28817Direction x=${position.x} frameX=${hostLoc[0]} nativeX=${nativeLoc[0]} frameTx=${frame.translationX} nativeTx=$translationX frameLayer=${frame.layerType} nativeLayer=$layerType frameVis=${frame.visibility} nativeVis=$visibility frameAlpha=${frame.alpha} nativeAlpha=$alpha attached=$isAttachedToWindow hardware=${canvas.isHardwareAccelerated}")
                    layerTrace28817Remaining--; layerTrace28817Count++
                }
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
        fun restoreRebound28818(reason: String) {
            val target = rebound28818Target ?: return
            rebound28818Restore?.let { target.removeCallbacks(it) }
            try {
                // Restore only if this experiment still owns the software layer.
                if (target.layerType == View.LAYER_TYPE_SOFTWARE) target.setLayerType(rebound28818OldLayer, null)
                XposedBridge.log("KYANT28818_RESTORE host=$trace28815Host reason=$reason old=$rebound28818OldLayer")
            } catch (t: Throwable) { XposedBridge.log("KYANT28818_RESTORE_ERROR ${t.javaClass.simpleName}") }
            rebound28818Target = null
            rebound28818Restore = null
            reboundLayerGuard28818.set(false)
        }
        fun dispose(reason: String) {
            restoreRebound28818("dispose")
            if (disposed) return
            disposed = true
            if (releaseTrace28820) liveHostProbes28821.remove(trace28815Host)
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
                largeContinuousSource28830, renderAbMode, motionAbMode, legacySyncAbMode, if (nativeLayerMode in 2..3) 3 else displayIsolationMode, nativeLayerMode, bridgeBitmap, bridgeVersion,
                if (displayIsolationMode in 1..3) 0 else bridgeSyncMode,
                pipelineMode28819, frameSync28822,
                bridgePublishedPosition, bridgeMismatchObserved, { pos -> sampleBridgeAt(pos) }, predrawMatrixSnapshot,
                { glassScreenPosition(lastBounds) }, { stage, pos, info -> releaseTrace28820(stage, pos, info) }) {
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
                    if (largeSquare28829) largeGeometryDirty28829 = true
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
                    if ((frameTrace28815 || layerTrace28817) &&
                        frameChecks % 30L == 0L && dragDiagnosticCount < 12L) {
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
                    nativePredrawPosition28824 = actualSample
                    nativePredrawSerial28824++
                    releaseTrace28820("PREDRAW", actualSample,
                        "committedX=${samplePosition.value.x} committedY=${samplePosition.value.y} frameX=${frame.x} frameTx=${frame.translationX}")
                    // V288.14: compare sample, anchor and host in the same PreDraw.
                    // Diagnostic only: no changes to wallpaper sampling or transforms.
                    // Ignore only transform-matrix numerical noise (< 1/64 px).
                    // Keep real fractional Workspace movement live.
                    val sampleDelta = kotlin.math.abs(actualSample.x - lastSample.x) +
                        kotlin.math.abs(actualSample.y - lastSample.y)
                    // V288.18: detect a rightward drag followed by leftward spring-back.
                    // The comparison is in screen space, not Workspace scroll coordinates.
                    val reboundNow28818 = SystemClock.elapsedRealtimeNanos()
                    if (pipelineMode28819 < 0 && anchor.isShown) {
                        val reboundDx28818 = if (rebound28818LastX.isFinite()) actualSample.x - rebound28818LastX else 0f
                        if (reboundDx28818 > 0.5f) {
                            rebound28818RightDrag = true
                            rebound28818LastPositiveNs = reboundNow28818
                        } else if (reboundDx28818 < -0.5f && rebound28818RightDrag) {
                            val ageMs = (reboundNow28818 - rebound28818LastPositiveNs) / 1000000L
                            if (ageMs in 0..800 && rebound28818Count < 6) {
                                rebound28818Count++
                                XposedBridge.log("KYANT28818_REVERSAL host=$trace28815Host mode=$reboundMode28818 dx=$reboundDx28818 x=${actualSample.x} ageMs=$ageMs")
                                if (reboundMode28818 == 1 && reboundLayerGuard28818.compareAndSet(false, true)) {
                                    var cell28818: View? = anchor.parent as? View
                                    var hops28818 = 0
                                    while (cell28818 != null && hops28818++ < 6 && !cell28818.javaClass.name.contains("CellLayout", ignoreCase = true)) cell28818 = cell28818.parent as? View
                                    if (cell28818 != null && cell28818.layerType == View.LAYER_TYPE_HARDWARE) {
                                        try {
                                            rebound28818Target = cell28818
                                            rebound28818OldLayer = cell28818.layerType
                                            cell28818.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                                            XposedBridge.log("KYANT28818_LAYER_TEST host=$trace28815Host old=$rebound28818OldLayer new=${cell28818.layerType} class=${cell28818.javaClass.simpleName}")
                                            val restore28818 = Runnable { restoreRebound28818("timeout") }
                                            rebound28818Restore = restore28818
                                            cell28818.postDelayed(restore28818, 220L)
                                        } catch (t: Throwable) {
                                            XposedBridge.log("KYANT28818_LAYER_ERROR ${t.javaClass.simpleName}")
                                            restoreRebound28818("error")
                                        }
                                    } else {
                                        XposedBridge.log("KYANT28818_SKIP host=$trace28815Host reason=no_hardware_cell layer=${cell28818?.layerType}")
                                        reboundLayerGuard28818.set(false)
                                    }
                                }
                            }
                            rebound28818RightDrag = false
                        }
                        if (rebound28818RightDrag && (reboundNow28818 - rebound28818LastPositiveNs) > 800000000L) rebound28818RightDrag = false
                        rebound28818LastX = actualSample.x
                    }
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
                    val mustResolveGeometry28829 = if (largeSquare28829) {
                        frame.visibility == View.INVISIBLE || largeGeometryDirty28829 ||
                            frameChecks % 8L == 0L
                    } else frame.visibility == View.INVISIBLE || moved || frameChecks % 20L == 0L
                    val b = if (mustResolveGeometry28829)
                        FolderGlassGeometry272.resolve(anchor) else lastBounds
                    if (largeSquare28829 && mustResolveGeometry28829) largeGeometryDirty28829 = false
                    val boundsChangedNow = b != lastBounds
                    // On large squares use ONE immutable screen position for
                    // state commit + native snapshot within this pre-draw pass.
                    // If OEM local geometry changed, calculate it once for b.
                    val largeFrameSample28829 = if (largeSquare28829) {
                        if (boundsChangedNow) glassScreenPosition(b) else actualSample
                    } else null
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
                    if ((frameTrace28815 || layerTrace28817) && visible && trace28815Events < 160) {
                        val now28815 = SystemClock.elapsedRealtimeNanos()
                        trace28815PreSerial++
                        val dx28815 = if (trace28815LastPreX.isFinite())
                            actualSample.x - trace28815LastPreX else 0f
                        val gap28815 = if (trace28815LastPreNs > 0L)
                            (now28815 - trace28815LastPreNs) / 1000000L else -1L
                        if (kotlin.math.abs(dx28815) < 0.15f) trace28815Quiet++
                        else {
                            if (trace28815Quiet >= 3) {
                                trace28815Direction = if (dx28815 > 0f) 1 else -1
                                trace28815Remaining = 12
                                if (layerTrace28817 && layerTrace28817Count < 120) {
                                    layerTrace28817Direction = trace28815Direction
                                    layerTrace28817Remaining = 8
                                    val hostLoc = IntArray(2); val nativeLoc = IntArray(2)
                                    frame.getLocationOnScreen(hostLoc)
                                    nativeSampleView?.getLocationOnScreen(nativeLoc)
                                    XposedBridge.log("KYANT28817_ONSET_LAYER host=$trace28815Host dir=$layerTrace28817Direction dx=$dx28815 sampleX=${actualSample.x} hostX=${hostLoc[0]} nativeX=${nativeLoc[0]} frameLayer=${frame.layerType} nativeLayer=${nativeSampleView?.layerType} frameTx=${frame.translationX} nativeTx=${nativeSampleView?.translationX} frameAlpha=${frame.alpha} nativeAlpha=${nativeSampleView?.alpha} frameVisible=${frame.visibility} nativeVisible=${nativeSampleView?.visibility} frameAttached=${frame.isAttachedToWindow} nativeAttached=${nativeSampleView?.isAttachedToWindow}")
                                    var ancestor28817: View? = anchor
                                    var depth28817 = 0
                                    while (ancestor28817 != null && depth28817 < 7) {
                                        val loc28817 = IntArray(2)
                                        ancestor28817.getLocationOnScreen(loc28817)
                                        XposedBridge.log("KYANT28817_ANCESTOR host=$trace28815Host dir=$layerTrace28817Direction depth=$depth28817 class=${ancestor28817.javaClass.simpleName} x=${loc28817[0]} tx=${ancestor28817.translationX} scrollX=${ancestor28817.scrollX} layer=${ancestor28817.layerType} alpha=${ancestor28817.alpha} clip=${(ancestor28817 as? ViewGroup)?.clipChildren} attached=${ancestor28817.isAttachedToWindow}")
                                        ancestor28817 = ancestor28817.parent as? View
                                        depth28817++
                                    }
                                    layerTrace28817Count += depth28817 + 1
                                }
                                if (trace28815Direction > 0 && redrawMode28816 == 1 &&
                                    redraw28816Requests < 24 && nativeSampleView != null) {
                                    redraw28816Requests++
                                    redraw28816Pending = true
                                    redraw28816PendingNs = now28815
                                    redraw28816PendingX = actualSample.x
                                    // One-shot request on rightward motion onset only.
                                    // Do not request invalidate from onDraw or every PreDraw.
                                    nativeSampleView.postInvalidateOnAnimation()
                                    XposedBridge.log("KYANT28816_REDRAW_REQUEST host=$trace28815Host request=$redraw28816Requests pre=$trace28815PreSerial x=${actualSample.x} dx=$dx28815")
                                }
                                XposedBridge.log(
                                    "KYANT28815_MOTION_ONSET host=$trace28815Host dir=$trace28815Direction dx=$dx28815 x=${actualSample.x} predraw=$trace28815PreSerial preGapMs=$gap28815 scrollX=$workspaceScrollX frameLayer=${frame.layerType} nativeLayer=${nativeSampleView?.layerType} sourceId=${System.identityHashCode(reference)}")
                                trace28815Events++
                            }
                            trace28815Quiet = 0
                        }
                        if (trace28815Remaining > 0 && trace28815Events < 160) {
                            XposedBridge.log(
                                "KYANT28815_PREDRAW host=$trace28815Host dir=$trace28815Direction serial=$trace28815PreSerial x=${actualSample.x} dx=$dx28815 gapMs=$gap28815 draw=$trace28815DrawSerial sourceId=${System.identityHashCode(reference)}")
                            trace28815Events++
                        }
                        trace28815LastPreX = actualSample.x
                        trace28815LastPreNs = now28815
                    }
                    if (matrixTrace28814 && visible && trace28814Events < 180) {
                        try {
                            trace28814PredrawSerial++
                            trace28814AnchorMatrix.reset()
                            anchor.transformMatrixToGlobal(trace28814AnchorMatrix)
                            trace28814AnchorPoint[0] = lastBounds.left.toFloat()
                            trace28814AnchorPoint[1] = lastBounds.top.toFloat()
                            trace28814AnchorMatrix.mapPoints(trace28814AnchorPoint)
                            trace28814HostMatrix.reset()
                            frame.transformMatrixToGlobal(trace28814HostMatrix)
                            trace28814HostPoint[0] = 0f
                            trace28814HostPoint[1] = 0f
                            trace28814HostMatrix.mapPoints(trace28814HostPoint)
                            root.getLocationOnScreen(trace28814RootScreen)
                            root.getLocationInWindow(trace28814RootWindow)
                            val offset = trace28814RootScreen[0] - trace28814RootWindow[0]
                            val anchorX = trace28814AnchorPoint[0] + offset
                            val hostX = trace28814HostPoint[0] + offset
                            val sampleDx = if (trace28814PrevSample.isFinite()) actualSample.x - trace28814PrevSample else 0f
                            val hostDx = if (trace28814PrevHost.isFinite()) hostX - trace28814PrevHost else 0f
                            val anchorDx = if (trace28814PrevAnchor.isFinite()) anchorX - trace28814PrevAnchor else 0f
                            // Observe first motion after >=3 quiet frames, then capture next draws.
                            val start = kotlin.math.abs(sampleDx) > 0.35f &&
                                (trace28814PrevSample.isFinite()) && onsetQuietFrames >= 3
                            if (start) {
                                trace28814LastDirection = if (sampleDx > 0f) 1 else -1
                                trace28814DrawRemaining = 12
                                XposedBridge.log("KYANT28814_PREDRAW_START host=${System.identityHashCode(anchor)} dir=$trace28814LastDirection sampleDx=$sampleDx hostDx=$hostDx anchorDx=$anchorDx sampleX=${actualSample.x} anchorX=$anchorX hostX=$hostX sampleHostDelta=${actualSample.x-hostX} boundsLeft=${lastBounds.left} frameLeft=${frame.left} frameTx=${frame.translationX} workspaceScrollX=$workspaceScrollX workspaceTx=$workspaceTranslationX sourceId=$onsetSourceId")
                                trace28814Events++
                            }
                            if (trace28814DrawRemaining > 0 && trace28814Events < 180) {
                                XposedBridge.log("KYANT28814_PREDRAW_FRAME host=${System.identityHashCode(anchor)} dir=$trace28814LastDirection serial=$trace28814PredrawSerial sampleX=${actualSample.x} anchorX=$anchorX hostX=$hostX sampleDx=$sampleDx hostDx=$hostDx anchorDx=$anchorDx sampleHostDelta=${actualSample.x-hostX} sourceId=$onsetSourceId")
                                trace28814Events++
                            }
                            trace28814PrevSample = actualSample.x
                            trace28814PrevHost = hostX
                            trace28814PrevAnchor = anchorX
                        } catch (t: Throwable) {
                            XposedBridge.log("KYANT28814_PREDRAW_ERROR ${t.javaClass.simpleName}")
                        }
                    }
                    // Track the real host/anchor relationship (including parent transforms),
                    // separately from the reference Bitmap's sampling coordinate.
                    // Throttle diagnostics to avoid log I/O every animation frame.
                    if (rightStartTraceEnabled && visible && onsetLogCount < 180) {
                        val dx = if (onsetPrevX.isFinite()) actualSample.x - onsetPrevX else 0f
                        val dy = if (onsetPrevY.isFinite()) actualSample.y - onsetPrevY else 0f
                        val moving = kotlin.math.abs(dx) > 0.35f
                        if (moving) onsetQuietFrames = 0 else onsetQuietFrames++
                        if (onsetDirection == 0 && moving && onsetPrevX.isFinite()) {
                            onsetDirection = if (dx > 0f) 1 else -1
                            onsetFramesRemaining = 10
                            onsetSerial++
                            XposedBridge.log("KYANT28813_MOTION_START host=${System.identityHashCode(anchor)} onset=$onsetSerial direction=$onsetDirection dx=$dx x=${actualSample.x} workspaceScrollX=$workspaceScrollX sourceId=$onsetSourceId")
                            onsetLogCount++
                        }
                        val hostMatrix28813 = Matrix()
                        frame.transformMatrixToGlobal(hostMatrix28813)
                        val hostPoint28813 = floatArrayOf(0f, 0f)
                        hostMatrix28813.mapPoints(hostPoint28813)
                        val nowNs28813 = SystemClock.elapsedRealtimeNanos()
                        if (onsetFramesRemaining > 0 && onsetLogCount < 180) {
                            val scrollDelta28813 = if (onsetPrevScrollX == Int.MIN_VALUE) 0 else workspaceScrollX - onsetPrevScrollX
                            XposedBridge.log("KYANT28813_ONSET_FRAME host=${System.identityHashCode(anchor)} onset=$onsetSerial direction=$onsetDirection remain=$onsetFramesRemaining x=${actualSample.x} dx=$dx y=${actualSample.y} dy=$dy anchorX=${xy[0]} hostX=${hostPoint28813[0]} hostDx=${hostPoint28813[0]-onsetPrevHostX} scrollX=$workspaceScrollX scrollDx=$scrollDelta28813 workspaceTx=$workspaceTranslationX boundsLeft=${lastBounds.left} sourceId=$onsetSourceId dtMs=${if (onsetPrevNs > 0L) (nowNs28813-onsetPrevNs)/1000000L else -1L}")
                            onsetLogCount++
                            onsetFramesRemaining--
                        }
                        if (onsetFramesRemaining == 0 && onsetQuietFrames >= 3) onsetDirection = 0
                        onsetPrevX = actualSample.x
                        onsetPrevY = actualSample.y
                        onsetPrevHostX = hostPoint28813[0]
                        onsetPrevScrollX = workspaceScrollX
                        onsetPrevNs = nowNs28813
                    }
                    val traceNowMs = SystemClock.uptimeMillis()
                    if ((frameTrace28815 || layerTrace28817 || matrixTrace28814) &&
                        visible && (hostMisplaced ||
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
                            val position = largeFrameSample28829 ?: glassScreenPosition(b)
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
                    val currentSample = largeFrameSample28829 ?: glassScreenPosition(b)
                    // Geometry may have changed since the initial pre-draw read.
                    nativePredrawPosition28824 = currentSample
                    predrawMatrixSnapshot.set(Pair(currentSample, SystemClock.elapsedRealtimeNanos()))
                    releaseTrace28820("POST_PREDRAW", currentSample,
                        "committedX=${samplePosition.value.x} committedY=${samplePosition.value.y} changed=$updatePosition")
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
        cornerRadiusPx: Float, samplingMode: Int,
        largeContinuousSource28830: Boolean, renderAbMode: Int, motionAbMode: Int,
        legacySyncAbMode: Int, displayIsolationMode: Int, nativeLayerMode: Int, bridgeBitmap: Bitmap?,
        bridgeVersion: androidx.compose.runtime.State<Int>,
        bridgeSyncMode: Int,
        pipelineMode28819: Int, frameSync28822: Int,
        bridgePublishedPosition: java.util.concurrent.atomic.AtomicReference<Offset?>,
        bridgeMismatchObserved: java.util.concurrent.atomic.AtomicLong,
        bridgeResample: (Offset) -> Unit,
        predrawMatrixSnapshot: java.util.concurrent.atomic.AtomicReference<Pair<Offset, Long>>,
        currentNativePosition: () -> Offset,
        trace28820: (String, Offset?, String) -> Unit,
        onComposed: () -> Unit
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        val optics28827 = KyantOptics28827.material
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
            // The four pipeline A/B paths intentionally avoid capturing a Kyant
            // backdrop or introducing a Compose graphicsLayer on the source canvas.
            val sourceModifier = if (pipelineMode28819 >= 0) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxSize().layerBackdrop(backdrop).graphicsLayer { alpha = sourceAlpha }
            }
            ComposeCanvas(sourceModifier) {
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
                    if (pipelineMode28819 == 2 && frameSync28822 == 1) {
                        // Same callback: sample at the exact position used to draw.
                        // No post() handoff and no independent bridge-version update.
                        bridgeResample(drawPosition)
                        val n = drawCadenceCount.get()
                        if (n % 60L == 1L) XposedBridge.log("KYANT28822_SAME_DRAW posX=${drawPosition.x} publishedX=${bridgePublishedPosition.get()?.x} draw=$n")
                    }
                    if (pipelineMode28819 == 2) trace28820("COMPOSE_DRAW", drawPosition,
                        "consumedVersion=$version prePublished=${bridgePublishedPosition.get()?.x}")
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
                } else if (renderAbMode != 1 && (samplingMode == 1 || largeContinuousSource28830)) {
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
                if (pipelineMode28819 >= 0 && draws % 60L == 1L) XposedBridge.log(
                    "KYANT28819_COMPOSE_DRAW mode=$pipelineMode28819 draws=$draws " +
                    "sourceX=$rootScreenX bridge=${bridgeBitmap != null} " +
                    "bridgeVersion=${bridgeVersion.value} publishedX=${bridgePublishedPosition.get()?.x}")
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
            if (pipelineMode28819 < 0 && (displayIsolationMode != 2 || nativeLayerMode in 2..3)) Box(Modifier.fillMaxSize().drawBackdrop(
                backdrop = backdrop,
                // The OEM radius is in physical pixels. Convert to dp and clamp to avoid a circle.
                shape = { RoundedRectangle(cornerDp) },
                effects = {
                    val m = size.minDimension
                    blur(optics28827.blur.dp.toPx())
                    if (motionAbMode != 2 && optics28827.strength > 0) {
                        val amount28827 = optics28827.strength / 100f
                        lens(refractionHeight = .1f * m * amount28827,
                            refractionAmount = .2f * m * amount28827,
                            depthEffect = true, chromaticAberration = false)
                    }
                },
                highlight = { Highlight(alpha = optics28827.highlight / 100f,
                    style = com.kyant.backdrop.highlight.HighlightStyle.Plain) }
            ))
        }
        SideEffect { onComposed() }
    }
}

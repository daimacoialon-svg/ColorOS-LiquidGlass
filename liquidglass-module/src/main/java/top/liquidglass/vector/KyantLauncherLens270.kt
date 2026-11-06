package top.liquidglass.vector

import android.provider.Settings
import android.graphics.Canvas
import android.graphics.Bitmap
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
import androidx.compose.ui.draw.clipToBounds
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
    private const val KEY = "liquidglass_kyant_folder_probe_275"

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
        if (!anchor.isAttachedToWindow) return
        val reference = KyantLauncherFrameBridge266.peekReferenceFrame270()
        if (reference == null) {
            XposedBridge.log("KYANT275_REFERENCE_NOT_READY one_shot_not_consumed")
            return
        }
        val root = anchor.rootView as? ViewGroup ?: return
        // Place the Compose host as the FIRST child of FolderIcon, before native icon children.
        // This preserves the original draw ordering without a DecorView overlay.
        val folder = anchor as? ViewGroup ?: run {
            XposedBridge.log("KYANT275_ABORT anchor_not_viewgroup")
            return
        }
        // One-shot disarm BEFORE attaching Compose: a Launcher crash must not cause a boot loop.
        val disarmed = try {
            Settings.Global.putInt(anchor.context.contentResolver, KEY, 0)
        } catch (t: Throwable) {
            XposedBridge.log("KYANT275_GUARD_ERROR " + t.javaClass.simpleName)
            false
        }
        if (!disarmed) {
            XposedBridge.log("KYANT275_GUARD_ABORT cannot_disarm_test")
            return
        }
        XposedBridge.log("KYANT275_ONE_SHOT_DISARMED key=" + KEY)

        val startedAt = SystemClock.uptimeMillis()
        val displayWidth = root.width.coerceAtLeast(1)
        val displayHeight = root.height.coerceAtLeast(1)
        val initialBounds = FolderGlassGeometry272.resolve(anchor)
        val nativeRadius = HookEntry.kyantFolderGlassRadius273(anchor)
        XposedBridge.log("KYANT275_NATIVE_GEOMETRY rect=$initialBounds radiusPx=$nativeRadius")
        val previewWidth = initialBounds.width().coerceAtLeast(1)
        val previewHeight = initialBounds.height().coerceAtLeast(1)
        val anchorXY = IntArray(2)
        anchor.getLocationOnScreen(anchorXY)
        // Place the diagnostic preview at the upper-left of the Launcher root.
        // The bitmap sample coordinates are aligned to the root's screen position.
        
        val compositionSeen = AtomicBoolean(false)
        val drawSeen = AtomicBoolean(false)
        val sampleX = androidx.compose.runtime.mutableIntStateOf(anchorXY[0] + initialBounds.left)
        val sampleY = androidx.compose.runtime.mutableIntStateOf(anchorXY[1] + initialBounds.top)
        val sampleWidth = androidx.compose.runtime.mutableIntStateOf(previewWidth)
        val sampleHeight = androidx.compose.runtime.mutableIntStateOf(previewHeight)
        val sampleRadiusPx = androidx.compose.runtime.mutableFloatStateOf(
            nativeRadius.coerceIn(0f, minOf(previewWidth, previewHeight) * 0.49f))
        XposedBridge.log("KYANT275_SHAPE size=${previewWidth}x${previewHeight} radiusPx=${sampleRadiusPx.floatValue} radiusDp=${sampleRadiusPx.floatValue / anchor.resources.displayMetrics.density} source=legacy")
        var followListener: android.view.ViewTreeObserver.OnPreDrawListener? = null
        val owner = Owner()
        // ComposeView is final in the Compose UI version used by this project.
        // Observe the enclosing FrameLayout instead of subclassing ComposeView.
        val frame = object : FrameLayout(anchor.context) {
            override fun dispatchDraw(canvas: Canvas) {
                super.dispatchDraw(canvas)
                if (drawSeen.compareAndSet(false, true)) {
                    XposedBridge.log("KYANT275_FIRST_DRAW elapsedMs=" +
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
            KyantLauncherLensProbe270.setActiveFor273(anchor, false)
            try { followListener?.let { if (folder.viewTreeObserver.isAlive) folder.viewTreeObserver.removeOnPreDrawListener(it) } } catch (_: Throwable) {}
            try { compose.disposeComposition() } catch (t: Throwable) {
                XposedBridge.log("KYANT275_DISPOSE_COMPOSE_ERROR " + t.javaClass.simpleName)
            }
            try { if (frame.parent === folder) folder.removeView(frame) } catch (t: Throwable) {
                XposedBridge.log("KYANT275_DISPOSE_VIEW_ERROR " + t.javaClass.simpleName)
            }
            try { recomposer?.close() } catch (_: Throwable) {}
            try { scope?.cancel() } catch (_: Throwable) {}
            try { owner.stop() } catch (t: Throwable) {
                XposedBridge.log("KYANT275_DISPOSE_OWNER_ERROR " + t.javaClass.simpleName)
            }
            XposedBridge.log("KYANT275_DISPOSE_CHECK reason=" + reason +
                " composition=" + compositionSeen.get() +
                " draw=" + drawSeen.get() +
                " removed=" + (frame.parent == null) +
                " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt))
            XposedBridge.log("KYANT275_HOST_DISPOSED reason=" + reason)
        }
        try {
            owner.start()
            // Explicit parent CompositionContext avoids DecorView's missing LifecycleOwner.
            // AndroidUiDispatcher.Main supplies Android's UI frame clock.
            val dispatcher = AndroidUiDispatcher.Main
            val exceptionHandler = CoroutineExceptionHandler { _, t ->
                XposedBridge.log("KYANT275_RECOMPOSER_ERROR " + t.javaClass.simpleName + " " + (t.message ?: ""))
                frame.post { dispose("recomposer_error") }
            }
            val newScope = CoroutineScope(dispatcher + SupervisorJob() + exceptionHandler)
            scope = newScope
            val newRecomposer = Recomposer(newScope.coroutineContext)
            recomposer = newRecomposer
            compose.setParentCompositionContext(newRecomposer)
            frame.addView(compose, FrameLayout.LayoutParams(previewWidth, previewHeight))
            compose.setContent { LensContent270(reference, sampleWidth.intValue, sampleHeight.intValue,
                displayWidth, displayHeight, sampleX.intValue, sampleY.intValue, sampleRadiusPx.floatValue) {
                if (compositionSeen.compareAndSet(false, true)) {
                    XposedBridge.log("KYANT275_COMPOSITION_CONFIRMED elapsedMs=" +
                        (SystemClock.uptimeMillis() - startedAt))
                }
            } }
            newScope.launch { newRecomposer.runRecomposeAndApplyChanges() }
            // A normal child at index 0 is drawn by FolderIcon.dispatchDraw before icons.
            // Explicitly lay it out because OEM FolderIcon may not manage injected children.
            folder.addView(frame, 0, ViewGroup.LayoutParams(previewWidth, previewHeight))
            frame.layout(initialBounds.left, initialBounds.top,
                initialBounds.left + previewWidth, initialBounds.top + previewHeight)
            // Mark old glass inactive only AFTER the Kyant child is successfully attached.
            KyantLauncherLensProbe270.setActiveFor273(anchor, true)
            XposedBridge.log("KYANT275_MUTUAL_EXCLUSION active=true layer=below_icons fallback=automatic_on_dispose")
            var lastX = anchorXY[0]
            var lastY = anchorXY[1]
            var lastBounds = android.graphics.Rect(initialBounds)
            val listener = android.view.ViewTreeObserver.OnPreDrawListener {
                if (!disposed && anchor.isAttachedToWindow) {
                    val xy = IntArray(2)
                    anchor.getLocationOnScreen(xy)
                    val b = FolderGlassGeometry272.resolve(anchor)
                    if (xy[0] != lastX || xy[1] != lastY || b != lastBounds) {
                        lastX = xy[0]; lastY = xy[1]; lastBounds = android.graphics.Rect(b)
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
                        sampleRadiusPx.floatValue = HookEntry.kyantFolderGlassRadius273(anchor)
                            .coerceIn(0f, minOf(w, h) * 0.49f)
                        frame.layout(b.left, b.top, b.left + w, b.top + h)
                        sampleX.intValue = lastX + b.left
                        sampleY.intValue = lastY + b.top
                    }
                }
                true
            }
            followListener = listener
            folder.viewTreeObserver.addOnPreDrawListener(listener)
            frame.translationX = 0f
            frame.translationY = 0f
            XposedBridge.log("KYANT275_HOST_ATTACHED layer=folder_child_index_0 owners=isolated recomposer=explicit view=${anchor.width}x${anchor.height} glassBounds=$initialBounds lens=${previewWidth}x${previewHeight} reference=${reference.width}x${reference.height}")
            frame.postDelayed({
                XposedBridge.log("KYANT275_PRE_DISPOSE composition=" + compositionSeen.get() +
                    " draw=" + drawSeen.get() +
                    " attached=" + compose.isAttachedToWindow +
                    " recomposerState=" + (recomposer?.currentState?.value?.toString() ?: "null"))
                dispose(if (compositionSeen.get() && drawSeen.get()) "timeout_success" else "timeout_incomplete")
            }, 6000L)
        } catch (t: Throwable) {
            XposedBridge.log("KYANT275_HOST_ERROR " + t.javaClass.simpleName + " " + (t.message ?: ""))
            dispose("setup_error")
        }
    }

    /** Real wallpaper pixels sampled from the existing Launcher Bitmap, without a CPU crop. */
    @Composable private fun LensContent270(
        reference: Bitmap, previewWidth: Int, previewHeight: Int,
        rootWidth: Int, rootHeight: Int, rootScreenX: Int, rootScreenY: Int,
        cornerRadiusPx: Float, onComposed: () -> Unit
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current.density
        val backdrop = rememberLayerBackdrop()
        val image = androidx.compose.runtime.remember(reference) { reference.asImageBitmap() }
        Box(Modifier.size(
            (previewWidth / androidx.compose.ui.platform.LocalDensity.current.density).dp,
            (previewHeight / androidx.compose.ui.platform.LocalDensity.current.density).dp
        ).clipToBounds()) {
            ComposeCanvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                // Reference is screen-aligned; root offset is accounted for explicitly.
                val x = (rootScreenX.toFloat() / android.content.res.Resources.getSystem().displayMetrics.widthPixels.coerceAtLeast(1) * reference.width).roundToInt()
                val y = (rootScreenY.toFloat() / android.content.res.Resources.getSystem().displayMetrics.heightPixels.coerceAtLeast(1) * reference.height).roundToInt()
                val sw = (previewWidth.toFloat() / android.content.res.Resources.getSystem().displayMetrics.widthPixels.coerceAtLeast(1) * reference.width).roundToInt()
                val sh = (previewHeight.toFloat() / android.content.res.Resources.getSystem().displayMetrics.heightPixels.coerceAtLeast(1) * reference.height).roundToInt()
                val sx = x.coerceIn(0, reference.width - 1)
                val sy = y.coerceIn(0, reference.height - 1)
                val cw = sw.coerceAtLeast(1).coerceAtMost(reference.width - sx)
                val ch = sh.coerceAtLeast(1).coerceAtMost(reference.height - sy)
                drawImage(image, srcOffset = IntOffset(sx, sy), srcSize = IntSize(cw, ch),
                    dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)))
            }
            Box(Modifier.fillMaxSize().drawBackdrop(
                backdrop = backdrop,
                // The OEM radius is in physical pixels. Convert to dp and clamp to avoid a circle.
                shape = { RoundedRectangle((cornerRadiusPx / density).dp) },
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

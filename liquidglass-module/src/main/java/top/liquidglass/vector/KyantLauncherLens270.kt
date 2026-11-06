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
    private const val KEY = "liquidglass_kyant_lens_probe_270"

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
            XposedBridge.log("KYANT270_REFERENCE_NOT_READY one_shot_not_consumed")
            return
        }
        val root = anchor.rootView as? ViewGroup ?: return
        // One-shot disarm BEFORE attaching Compose: a Launcher crash must not cause a boot loop.
        val disarmed = try {
            Settings.Global.putInt(anchor.context.contentResolver, KEY, 0)
        } catch (t: Throwable) {
            XposedBridge.log("KYANT270_GUARD_ERROR " + t.javaClass.simpleName)
            false
        }
        if (!disarmed) {
            XposedBridge.log("KYANT270_GUARD_ABORT cannot_disarm_test")
            return
        }
        XposedBridge.log("KYANT270_ONE_SHOT_DISARMED key=" + KEY)

        val startedAt = SystemClock.uptimeMillis()
        val displayWidth = root.width.coerceAtLeast(1)
        val displayHeight = root.height.coerceAtLeast(1)
        val previewPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200f,
            anchor.resources.displayMetrics).roundToInt().coerceAtLeast(80)
        val previewWidth = minOf(previewPx, displayWidth)
        val previewHeight = minOf(previewPx, displayHeight)
        // Place the diagnostic preview at the upper-left of the Launcher root.
        // The bitmap sample coordinates are aligned to the root's screen position.
        val rootXY = IntArray(2)
        root.getLocationOnScreen(rootXY)
        val compositionSeen = AtomicBoolean(false)
        val drawSeen = AtomicBoolean(false)
        val owner = Owner()
        // ComposeView is final in the Compose UI version used by this project.
        // Observe the enclosing FrameLayout instead of subclassing ComposeView.
        val frame = object : FrameLayout(anchor.context) {
            override fun dispatchDraw(canvas: Canvas) {
                super.dispatchDraw(canvas)
                if (drawSeen.compareAndSet(false, true)) {
                    XposedBridge.log("KYANT270_FIRST_DRAW elapsedMs=" +
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
            try { compose.disposeComposition() } catch (t: Throwable) {
                XposedBridge.log("KYANT270_DISPOSE_COMPOSE_ERROR " + t.javaClass.simpleName)
            }
            try { if (frame.parent === root) root.removeView(frame) } catch (t: Throwable) {
                XposedBridge.log("KYANT270_DISPOSE_VIEW_ERROR " + t.javaClass.simpleName)
            }
            try { recomposer?.close() } catch (_: Throwable) {}
            try { scope?.cancel() } catch (_: Throwable) {}
            try { owner.stop() } catch (t: Throwable) {
                XposedBridge.log("KYANT270_DISPOSE_OWNER_ERROR " + t.javaClass.simpleName)
            }
            XposedBridge.log("KYANT270_DISPOSE_CHECK reason=" + reason +
                " composition=" + compositionSeen.get() +
                " draw=" + drawSeen.get() +
                " removed=" + (frame.parent == null) +
                " elapsedMs=" + (SystemClock.uptimeMillis() - startedAt))
            XposedBridge.log("KYANT270_HOST_DISPOSED reason=" + reason)
        }
        try {
            owner.start()
            // Explicit parent CompositionContext avoids DecorView's missing LifecycleOwner.
            // AndroidUiDispatcher.Main supplies Android's UI frame clock.
            val dispatcher = AndroidUiDispatcher.Main
            val exceptionHandler = CoroutineExceptionHandler { _, t ->
                XposedBridge.log("KYANT270_RECOMPOSER_ERROR " + t.javaClass.simpleName + " " + (t.message ?: ""))
                frame.post { dispose("recomposer_error") }
            }
            val newScope = CoroutineScope(dispatcher + SupervisorJob() + exceptionHandler)
            scope = newScope
            val newRecomposer = Recomposer(newScope.coroutineContext)
            recomposer = newRecomposer
            compose.setParentCompositionContext(newRecomposer)
            frame.addView(compose, FrameLayout.LayoutParams(previewWidth, previewHeight))
            compose.setContent { LensContent270(reference, previewWidth, previewHeight,
                displayWidth, displayHeight, rootXY[0], rootXY[1]) {
                if (compositionSeen.compareAndSet(false, true)) {
                    XposedBridge.log("KYANT270_COMPOSITION_CONFIRMED elapsedMs=" +
                        (SystemClock.uptimeMillis() - startedAt))
                }
            } }
            newScope.launch { newRecomposer.runRecomposeAndApplyChanges() }
            root.addView(frame, ViewGroup.LayoutParams(previewWidth, previewHeight))
            XposedBridge.log("KYANT270_HOST_ATTACHED owners=isolated recomposer=explicit preview=${previewWidth}x${previewHeight} reference=${reference.width}x${reference.height}")
            frame.postDelayed({
                XposedBridge.log("KYANT270_PRE_DISPOSE composition=" + compositionSeen.get() +
                    " draw=" + drawSeen.get() +
                    " attached=" + compose.isAttachedToWindow +
                    " recomposerState=" + (recomposer?.currentState?.value?.toString() ?: "null"))
                dispose(if (compositionSeen.get() && drawSeen.get()) "timeout_success" else "timeout_incomplete")
            }, 2500L)
        } catch (t: Throwable) {
            XposedBridge.log("KYANT270_HOST_ERROR " + t.javaClass.simpleName + " " + (t.message ?: ""))
            dispose("setup_error")
        }
    }

    /** Real wallpaper pixels sampled from the existing Launcher Bitmap, without a CPU crop. */
    @Composable private fun LensContent270(
        reference: Bitmap, previewWidth: Int, previewHeight: Int,
        rootWidth: Int, rootHeight: Int, rootScreenX: Int, rootScreenY: Int,
        onComposed: () -> Unit
    ) {
        val backdrop = rememberLayerBackdrop()
        val image = androidx.compose.runtime.remember(reference) { reference.asImageBitmap() }
        Box(Modifier.size(
            (previewWidth / androidx.compose.ui.platform.LocalDensity.current.density).dp,
            (previewHeight / androidx.compose.ui.platform.LocalDensity.current.density).dp
        ).clipToBounds()) {
            ComposeCanvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                // Reference is screen-aligned; root offset is accounted for explicitly.
                val x = (rootScreenX.toFloat() / rootWidth * reference.width).roundToInt()
                val y = (rootScreenY.toFloat() / rootHeight * reference.height).roundToInt()
                val sw = (previewWidth.toFloat() / rootWidth * reference.width).roundToInt()
                val sh = (previewHeight.toFloat() / rootHeight * reference.height).roundToInt()
                val sx = x.coerceIn(0, reference.width - 1)
                val sy = y.coerceIn(0, reference.height - 1)
                val cw = sw.coerceAtLeast(1).coerceAtMost(reference.width - sx)
                val ch = sh.coerceAtLeast(1).coerceAtMost(reference.height - sy)
                drawImage(image, srcOffset = IntOffset(sx, sy), srcSize = IntSize(cw, ch),
                    dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)))
            }
            Box(Modifier.align(Alignment.Center).size(145.dp).drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(38.dp) },
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

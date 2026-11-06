package top.liquidglass.vector

import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
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

/** V268.2: isolated Compose test with explicit Recomposer; never replaces Launcher glass. */
object KyantIsolatedHost268 {
    private const val KEY = "liquidglass_kyant_host_probe_268"

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
        val root = anchor.rootView as? ViewGroup ?: return
        // One-shot disarm BEFORE attaching Compose: a Launcher crash must not cause a boot loop.
        val disarmed = try {
            Settings.Global.putInt(anchor.context.contentResolver, KEY, 0)
        } catch (t: Throwable) {
            XposedBridge.log("KYANT268_GUARD_ERROR " + t.javaClass.simpleName)
            false
        }
        if (!disarmed) {
            XposedBridge.log("KYANT268_GUARD_ABORT cannot_disarm_test")
            return
        }
        XposedBridge.log("KYANT268_ONE_SHOT_DISARMED key=" + KEY)

        val owner = Owner()
        val frame = FrameLayout(anchor.context).apply {
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
                XposedBridge.log("KYANT268_DISPOSE_COMPOSE_ERROR " + t.javaClass.simpleName)
            }
            try { if (frame.parent === root) root.removeView(frame) } catch (t: Throwable) {
                XposedBridge.log("KYANT268_DISPOSE_VIEW_ERROR " + t.javaClass.simpleName)
            }
            try { recomposer?.close() } catch (_: Throwable) {}
            try { scope?.cancel() } catch (_: Throwable) {}
            try { owner.stop() } catch (t: Throwable) {
                XposedBridge.log("KYANT268_DISPOSE_OWNER_ERROR " + t.javaClass.simpleName)
            }
            XposedBridge.log("KYANT268_HOST_DISPOSED reason=" + reason)
        }
        try {
            owner.start()
            // Explicit parent CompositionContext avoids DecorView's missing LifecycleOwner.
            // AndroidUiDispatcher.Main supplies Android's UI frame clock.
            val dispatcher = AndroidUiDispatcher.Main
            val exceptionHandler = CoroutineExceptionHandler { _, t ->
                XposedBridge.log("KYANT268_RECOMPOSER_ERROR " + t.javaClass.simpleName + " " + (t.message ?: ""))
                frame.post { dispose("recomposer_error") }
            }
            val newScope = CoroutineScope(dispatcher + SupervisorJob() + exceptionHandler)
            scope = newScope
            val newRecomposer = Recomposer(newScope.coroutineContext)
            recomposer = newRecomposer
            compose.setParentCompositionContext(newRecomposer)
            frame.addView(compose, FrameLayout.LayoutParams(1, 1))
            compose.setContent { HostContent() }
            newScope.launch { newRecomposer.runRecomposeAndApplyChanges() }
            root.addView(frame, ViewGroup.LayoutParams(1, 1))
            XposedBridge.log("KYANT268_HOST_ATTACHED owners=isolated recomposer=explicit size=1x1")
            frame.postDelayed({ dispose("timeout_success") }, 2500L)
        } catch (t: Throwable) {
            XposedBridge.log("KYANT268_HOST_ERROR " + t.javaClass.simpleName + " " + (t.message ?: ""))
            dispose("setup_error")
        }
    }

    @Composable private fun HostContent() {
        Box {}
        SideEffect { XposedBridge.log("KYANT268_COMPOSITION_ACTIVE size=1x1") }
    }
}

package top.liquidglass.vector

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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

/** V268: single, 1x1 noninteractive host test; no wallpaper, no glass replacement. */
object KyantIsolatedHost268 {
    private class Owner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store
        fun start() {
            controller.performAttach()
            controller.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        fun stop() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }

    @JvmStatic fun run(anchor: View) {
        if (!anchor.isAttachedToWindow) return
        val root = anchor.rootView as? ViewGroup ?: return
        val owner = Owner()
        val frame = FrameLayout(anchor.context)
        frame.isClickable = false
        frame.isFocusable = false
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        frame.setViewTreeViewModelStoreOwner(owner)
        val compose = ComposeView(anchor.context)
        compose.isClickable = false
        compose.setContent { HostContent() }
        frame.addView(compose, FrameLayout.LayoutParams(1, 1))
        var attached = false
        try {
            owner.start()
            root.addView(frame, ViewGroup.LayoutParams(1, 1))
            attached = true
            XposedBridge.log("KYANT268_HOST_ATTACHED owners=isolated size=1x1 root=" + root.javaClass.simpleName)
            frame.postDelayed({
                try {
                    if (frame.parent === root) root.removeView(frame)
                    compose.disposeComposition()
                    owner.stop()
                    XposedBridge.log("KYANT268_HOST_DISPOSED success=true")
                } catch (t: Throwable) { XposedBridge.log("KYANT268_DISPOSE_ERROR " + t.javaClass.simpleName) }
            }, 2500L)
        } catch (t: Throwable) {
            XposedBridge.log("KYANT268_HOST_ERROR " + t.javaClass.simpleName + " " + (t.message ?: ""))
            try { if (attached && frame.parent === root) root.removeView(frame) } catch (_: Throwable) {}
            try { compose.disposeComposition() } catch (_: Throwable) {}
            try { owner.stop() } catch (_: Throwable) {}
        }
    }

    @Composable private fun HostContent() {
        Box {}
        SideEffect { XposedBridge.log("KYANT268_COMPOSITION_ACTIVE size=1x1") }
    }
}

package top.liquidglass.vector

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.robv.android.xposed.XposedBridge

/** Shared optical material, read from Settings.Global by both real Kyant hosts.
 *  These keys are intentionally independent of V229/V230 Shader settings.
 */
object KyantOptics28827 {
    const val DOCK_KEY = "liquidglass_kyant_dock_28827"
    const val STRENGTH_KEY = "liquidglass_kyant_optical_strength_28827"
    const val BLUR_KEY = "liquidglass_kyant_optical_blur_28827"
    const val HIGHLIGHT_KEY = "liquidglass_kyant_optical_highlight_28827"

    data class Material(val strength: Int = 100, val blur: Int = 0, val highlight: Int = 100)
    @Volatile private var registered = false
    private var observer: ContentObserver? = null
    var material by mutableStateOf(Material())
        private set

    @JvmStatic fun ensure(context: Context) {
        if (registered) return
        synchronized(this) {
            if (registered) return
            val app = context.applicationContext ?: context
            val handler = Handler(Looper.getMainLooper())
            val listener = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) { refresh(app) }
            }
            try {
                app.contentResolver.registerContentObserver(Settings.Global.CONTENT_URI, true, listener)
                observer = listener
                registered = true
            } catch (t: Throwable) {
                XposedBridge.log("KYANT28827_GLOBAL_OBSERVER_ERROR ${t.javaClass.simpleName}")
            }
            refresh(app)
        }
    }

    @JvmStatic fun refresh(context: Context) {
        try {
            val resolver = context.contentResolver
            val next = Material(
                Settings.Global.getInt(resolver, STRENGTH_KEY, 100).coerceIn(0, 200),
                Settings.Global.getInt(resolver, BLUR_KEY, 0).coerceIn(0, 20),
                Settings.Global.getInt(resolver, HIGHLIGHT_KEY, 100).coerceIn(0, 100)
            )
            if (material != next) material = next
        } catch (t: Throwable) {
            XposedBridge.log("KYANT28827_MATERIAL_READ_ERROR ${t.javaClass.simpleName}")
        }
    }
}

package top.liquidglass.vector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Build-time proof that the module compiles against the real upstream Backdrop API.
 * Visual parity with the upstream demo and Launcher capture are NOT implemented yet.
 */
class KyantEngine253Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EngineDependencyProbe253() }
    }
}

@Composable
private fun EngineDependencyProbe253() {
    val backdrop: Backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) { BasicText("Kyant Backdrop linked: ${backdrop.javaClass.simpleName}") }
}

package top.liquidglass.vector

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.os.Bundle
import android.graphics.BitmapFactory
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

/**
 * V256: native Android View -> Bitmap snapshot -> Compose layerBackdrop -> original Kyant lens.
 * This is an opt-in, single-frame bridge test, NOT a Launcher background capture or Hook.
 * The source view is deliberately rendered off-screen in our own process; no protected
 * windows or other apps are captured. Manual refresh avoids feedback loops and jank.
 */
class KyantReferenceFrame265Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReferenceFrameTest265() }
    }
}

@Composable
private fun ReferenceFrameTest265() {
    val context = LocalContext.current
    val backdrop = rememberLayerBackdrop()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    var mode by remember { mutableIntStateOf(0) }
    var captureCount by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("准备读取 Launcher 已缓存的壁纸参考帧") }
    var sourceInfo by remember { mutableStateOf("") }
    val density = context.resources.displayMetrics.density
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF142B38))) {
        val w = (constraints.maxWidth).coerceAtLeast(1)
        val h = (constraints.maxHeight).coerceAtLeast(1)
        // Explicit test only: the Launcher and module Activity are different processes.
        // Read the already-synced Launcher reference, not WallpaperManager or a screen capture.
        // The root subprocess runs off the main thread. This is an IPC diagnostic,
        // not the final zero-copy Launcher rendering bridge.
        LaunchedEffect(captureCount) {
            status = "读取中（后台线程）…"
            val result = withContext(Dispatchers.IO) { loadLauncherReference265(context) }
            result.first?.let { bitmap = it }
            status = result.second
            sourceInfo = if (result.first != null) "size=${result.first!!.width}×${result.first!!.height} · ARGB_8888" else ""
        }
        bitmap?.let { frame ->
            Image(
                bitmap = frame.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)
            )
        }
        val glassWidth = if (mode == 1) 300.dp else 240.dp
        val glassHeight = if (mode == 1) 92.dp else 240.dp
        Box(
            Modifier.align(Alignment.Center)
                .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                .size(glassWidth, glassHeight)
                .pointerInput(mode) {
                    detectDragGestures { change, delta ->
                        change.consume(); dx += delta.x; dy += delta.y
                    }
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(if (mode == 1) 46.dp else 60.dp) },
                    effects = {
                        val m = size.minDimension
                        vibrancy()
                        blur(0.dp.toPx())
                        lens(refractionHeight = .1f * m, refractionAmount = .2f * m,
                            depthEffect = true, chromaticAberration = false)
                    },
                    highlight = { Highlight.Plain }
                )
        )
        Column(Modifier.align(Alignment.TopCenter).padding(top = 36.dp, start = 12.dp, end = 12.dp)) {
            BasicText("V265 · 壁纸参考帧 → Kyant", style = TextStyle(color = Color.White, fontSize = 20.sp))
            Spacer(Modifier.height(6.dp))
            BasicText("Root 读取现有 Launcher 参考帧；仅独立测试界面", style = TextStyle(color = Color.White, fontSize = 13.sp))
            Spacer(Modifier.height(5.dp))
            BasicText(status, style = TextStyle(color = Color.White, fontSize = 12.sp))
            BasicText(sourceInfo, style = TextStyle(color = Color.White, fontSize = 12.sp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("文件夹", "Dock", "重新读取", "复位").forEachIndexed { i, label ->
                    BasicText(label, Modifier.background(Color(0xD9275663)).clickable {
                        when(i) { 0,1 -> { mode=i; dx=0f; dy=0f }; 2 -> captureCount++; 3 -> { dx=0f; dy=0f } }
                    }.padding(8.dp), style = TextStyle(color = Color.White, fontSize = 12.sp))
                }
            }
        }
    }
}

/** Read only a known Launcher-owned wallpaper reference file. No screenshot or app-content capture. */
private fun loadLauncherReference265(context: android.content.Context): Pair<Bitmap?, String> {
    val sources = listOf(
        "/data/user/0/com.android.launcher/files/liquidglass_wallpaper_reference.jpg",
        "/data/user_de/0/com.android.launcher/files/liquidglass_wallpaper_reference.jpg"
    )
    val dest = File(context.cacheDir, "kyant265_wallpaper_reference.tmp")
    for (source in sources) {
        try {
            // Do not interpolate user input; source paths are fixed and shell-quoted.
            val process = ProcessBuilder("su", "-c", "cat '$source'").start()
            FileOutputStream(dest).use { output ->
                process.inputStream.use { input -> input.copyTo(output, 64 * 1024) }
            }
            if (!process.waitFor(8, TimeUnit.SECONDS)) { process.destroyForcibly(); continue }
            if (process.exitValue() != 0 || dest.length() < 1024L) continue
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val decoded = BitmapFactory.decodeFile(dest.absolutePath, options)
            if (decoded != null && decoded.width >= 320 && decoded.height >= 640) {
                return decoded to "已加载现有壁纸参考帧（独立进程测试副本）"
            }
        } catch (_: Exception) { /* continue to DE mirror */ }
        finally { dest.delete() }
    }
    return null to "无法读取 Launcher 参考帧；请确认 Root 授权及原有壁纸缓存已生成"
}

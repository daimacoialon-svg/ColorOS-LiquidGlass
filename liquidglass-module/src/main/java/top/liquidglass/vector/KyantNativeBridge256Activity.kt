package top.liquidglass.vector

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.os.Bundle
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
class KyantNativeBridge256Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NativeBridgeTest256() }
    }
}

private class NativeDiagnosticView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)
        canvas.drawColor(AndroidColor.rgb(20, 43, 56))
        val step = 60f * resources.displayMetrics.density
        paint.color = AndroidColor.rgb(76, 199, 185)
        paint.strokeWidth = 2f * resources.displayMetrics.density
        var x = 0f
        while (x <= width) { canvas.drawLine(x, 0f, x, height.toFloat(), paint); x += step }
        var y = 0f
        while (y <= height) { canvas.drawLine(0f, y, width.toFloat(), y, paint); y += step }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f * resources.displayMetrics.density
        paint.color = AndroidColor.rgb(255, 170, 74)
        canvas.drawCircle(width * .72f, height * .4f, width * .19f, paint)
        paint.color = AndroidColor.rgb(110, 165, 250)
        canvas.drawCircle(width * .28f, height * .68f, width * .16f, paint)
        paint.style = Paint.Style.FILL
    }
}

@Composable
private fun NativeBridgeTest256() {
    val context = LocalContext.current
    val backdrop = rememberLayerBackdrop()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    var mode by remember { mutableIntStateOf(0) }
    var captureCount by remember { mutableIntStateOf(0) }
    val density = context.resources.displayMetrics.density
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF142B38))) {
        val w = (constraints.maxWidth).coerceAtLeast(1)
        val h = (constraints.maxHeight).coerceAtLeast(1)
        // Snapshot is generated from a real native View's onDraw, not from a Compose Canvas.
        LaunchedEffect(w, h, captureCount) {
            val view = NativeDiagnosticView(context)
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
            val newBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(AndroidCanvas(newBitmap))
            val previous = bitmap
            bitmap = newBitmap
            // Do not recycle previous while Compose may still be drawing it.
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
            BasicText("V256 · Native View → Kyant", style = TextStyle(color = Color.White, fontSize = 20.sp))
            Spacer(Modifier.height(6.dp))
            BasicText("单帧 View 快照桥接；不是 Launcher 实时背景", style = TextStyle(color = Color.White, fontSize = 13.sp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("文件夹", "Dock", "重新采样", "复位").forEachIndexed { i, label ->
                    BasicText(label, Modifier.background(Color(0xD9275663)).clickable {
                        when(i) { 0,1 -> { mode=i; dx=0f; dy=0f }; 2 -> captureCount++; 3 -> { dx=0f; dy=0f } }
                    }.padding(8.dp), style = TextStyle(color = Color.White, fontSize = 12.sp))
                }
            }
        }
    }
}

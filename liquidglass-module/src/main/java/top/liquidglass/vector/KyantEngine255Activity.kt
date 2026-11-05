package top.liquidglass.vector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle

/** Genuine upstream Backdrop capture + original optical pipeline; not the V251 Java shader. */
class KyantEngine255Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KyantOpticalDemo255() }
    }
}

@Composable
private fun KyantOpticalDemo255() {
    val backdrop = rememberLayerBackdrop()
    var scene by remember { mutableIntStateOf(0) } // 0 grid, 1 original upstream wallpaper, 2 contrast
    var shapeMode by remember { mutableIntStateOf(0) } // square, dock, tall
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val widthDp = when (shapeMode) { 1 -> 304.dp; 2 -> 132.dp; else -> 256.dp }
    val heightDp = when (shapeMode) { 1 -> 94.dp; 2 -> 310.dp; else -> 256.dp }
    val cornerDp = when (shapeMode) { 1 -> 47.dp; 2 -> 46.dp; else -> 64.dp }

    Box(Modifier.fillMaxSize().background(Color(0xFF122A38))) {
        // Only the background is captured. The glass is rendered above it using upstream Backdrop.
        if (scene == 1) {
            Image(
                painter = painterResource(id = R.drawable.kyant_wallpaper_255),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)
            )
        } else {
            Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                val cell = if (scene == 0) 32.dp.toPx() else 18.dp.toPx()
                val line = if (scene == 0) Color(0xFF50C7B8) else Color(0xFFFFDF73)
                var x = 0f
                while (x < size.width) {
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), (if (scene == 0) 1.5f else 2f).dp.toPx())
                    x += cell
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(line, Offset(0f, y), Offset(size.width, y), (if (scene == 0) 1.5f else 2f).dp.toPx())
                    y += cell
                }
                drawCircle(Color(0xFFEF9D50), radius = size.minDimension * .23f, center = Offset(size.width * .68f, size.height * .37f), style = Stroke(width = 8.dp.toPx()))
                drawCircle(Color(0xFF75A9F5), radius = size.minDimension * .17f, center = Offset(size.width * .3f, size.height * .65f), style = Stroke(width = 7.dp.toPx()))
            }
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                .size(widthDp, heightDp)
                .pointerInput(shapeMode) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        dx += dragAmount.x
                        dy += dragAmount.y
                    }
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(cornerDp) },
                    effects = {
                        val minDimension = size.minDimension
                        vibrancy()
                        blur(0.dp.toPx())
                        lens(
                            refractionHeight = 0.2f * minDimension * 0.5f,
                            refractionAmount = 0.2f * minDimension,
                            depthEffect = true,
                            chromaticAberration = false
                        )
                    },
                    highlight = { Highlight.Plain }
                )
        )
        Column(Modifier.align(Alignment.TopCenter).padding(top = 45.dp, start = 12.dp, end = 12.dp)) {
            BasicText("Kyant Backdrop · V255", style = TextStyle(color = Color.White, fontSize = 20.sp))
            Spacer(Modifier.height(8.dp))
            BasicText("Original upstream lens · drag glass to test edges", style = TextStyle(color = Color.White, fontSize = 13.sp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("网格", "原版壁纸", "高对比").forEachIndexed { index, label ->
                    BasicText(label, modifier = Modifier.background(if (scene == index) Color(0xFF147F78) else Color(0xBB263A4B)).clickable { scene = index }.padding(8.dp), style = TextStyle(color = Color.White, fontSize = 13.sp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("文件夹", "Dock", "竖条", "复位").forEachIndexed { index, label ->
                    BasicText(label, modifier = Modifier.background(if (shapeMode == index && index < 3) Color(0xFF147F78) else Color(0xBB263A4B)).clickable {
                        if (index == 3) { dx = 0f; dy = 0f } else { shapeMode = index; dx = 0f; dy = 0f }
                    }.padding(8.dp), style = TextStyle(color = Color.White, fontSize = 13.sp))
                }
            }
        }
    }
}

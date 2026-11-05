package top.liquidglass.vector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle

/** Genuine upstream Backdrop capture + original optical pipeline; not the V251 Java shader. */
class KyantEngine254Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KyantOpticalDemo254() }
    }
}

@Composable
private fun KyantOpticalDemo254() {
    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(Color(0xFF122A38))) {
        Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            val cell = 32.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(Color(0xFF50C7B8), Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                x += cell
            }
            var y = 0f
            while (y < size.height) {
                drawLine(Color(0xFF50C7B8), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
                y += cell
            }
            drawCircle(Color(0xFFEF9D50), radius = size.minDimension * .23f, center = Offset(size.width * .68f, size.height * .37f), style = Stroke(width = 8.dp.toPx()))
            drawCircle(Color(0xFF75A9F5), radius = size.minDimension * .17f, center = Offset(size.width * .3f, size.height * .65f), style = Stroke(width = 7.dp.toPx()))
        }
        Box(
            Modifier
                .size(256.dp)
                .align(Alignment.Center)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(64.dp) },
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
        Column(Modifier.align(Alignment.TopCenter).padding(top = 48.dp, start = 12.dp, end = 12.dp)) {
            BasicText("Kyant Backdrop · V254", style = TextStyle(color = Color.White, fontSize = 20.sp))
            Spacer(Modifier.height(8.dp))
            BasicText("Original lens + layerBackdrop capture. Grid is diagnostic background.", style = TextStyle(color = Color.White, fontSize = 13.sp))
        }
    }
}

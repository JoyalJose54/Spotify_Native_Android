package com.example.spotify_clone.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class PaletteColors(
    val dominant: Color = Color(0xFF242424),
    val vibrant: Color = Color(0xFF1DB954),
    val muted: Color = Color(0xFF181818),
    val bgColor: Color = Color(0xFF242424),
    val bgColorDark: Color = Color(0xFF161616),
    val bgColorBottom: Color = Color(0xFF0A0A0A)
)

object PaletteCache {
    private val cache = ConcurrentHashMap<String, PaletteColors>()

    fun get(url: String): PaletteColors? = cache[url]

    fun put(url: String, colors: PaletteColors) {
        cache[url] = colors
    }

    fun contains(url: String): Boolean = cache.containsKey(url)

    suspend fun extractColors(context: Context, url: String): PaletteColors {
        if (url.isBlank()) return PaletteColors()
        get(url)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Decode image with Coil (disallowing hardware bitmap for Palette compatibility)
                var rawBitmap: Bitmap? = null
                try {
                    val imageLoader = coil3.SingletonImageLoader.get(context)
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .size(180, 180)
                        .build()
                    val result = imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val img = result.image.toBitmap()
                        rawBitmap = if (img.config == Bitmap.Config.HARDWARE) {
                            img.copy(Bitmap.Config.ARGB_8888, false)
                        } else {
                            img
                        }
                    }
                } catch (coilErr: Exception) {
                    android.util.Log.d("PaletteHelper", "Coil fetch failed, attempting direct connection: ${coilErr.message}")
                }

                // Step 2: Direct connection fallback if Coil failed
                if (rawBitmap == null) {
                    try {
                        val conn = URL(url).openConnection() as HttpURLConnection
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        conn.doInput = true
                        conn.connect()
                        val input = conn.inputStream
                        val decoded = BitmapFactory.decodeStream(input)
                        input.close()
                        conn.disconnect()
                        rawBitmap = decoded
                    } catch (netErr: Exception) {
                        android.util.Log.w("PaletteHelper", "Direct fetch failed for $url: ${netErr.message}")
                    }
                }

                if (rawBitmap == null) {
                    return@withContext PaletteColors()
                }

                // Step 3: Inner crop (70% area) to completely eliminate top/bottom letterbox black bars
                // from YouTube thumbnails or letterboxed cover art
                val bitmapToAnalyze = if (rawBitmap.width > 40 && rawBitmap.height > 40) {
                    val insetX = (rawBitmap.width * 0.08f).toInt()
                    val insetY = (rawBitmap.height * 0.14f).toInt()
                    val cropW = (rawBitmap.width - insetX * 2).coerceAtLeast(10)
                    val cropH = (rawBitmap.height - insetY * 2).coerceAtLeast(10)
                    try {
                        Bitmap.createBitmap(rawBitmap, insetX, insetY, cropW, cropH)
                    } catch (_: Exception) {
                        rawBitmap
                    }
                } else {
                    rawBitmap
                }

                val palette = Palette.from(bitmapToAnalyze).maximumColorCount(24).generate()

                // Step 4: Filter out black bars/borders (lum < 0.08) and washed out white (lum > 0.92)
                val candidateSwatches = palette.swatches.filter { swatch ->
                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(swatch.rgb, hsl)
                    hsl[2] in 0.08f..0.92f && hsl[1] >= 0.08f
                }

                // Strictly select the cover art's true main color:
                // Prioritize vibrant / colorful swatches with high saturation and strong population
                val bestSwatch = candidateSwatches.maxByOrNull { swatch ->
                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(swatch.rgb, hsl)
                    swatch.population.toFloat() * (hsl[1] * hsl[1] + 0.25f)
                } ?: palette.vibrantSwatch
                  ?: palette.dominantSwatch
                  ?: palette.darkVibrantSwatch
                  ?: palette.lightVibrantSwatch
                  ?: palette.mutedSwatch
                  ?: palette.swatches.maxByOrNull { it.population }

                val defaultDominant = android.graphics.Color.parseColor("#1DB954")
                val chosenRgb = bestSwatch?.rgb ?: defaultDominant
                val chosenHsl = FloatArray(3)
                ColorUtils.colorToHSL(chosenRgb, chosenHsl)

                val hue = chosenHsl[0]
                val sat = chosenHsl[1]
                val lum = chosenHsl[2]

                // Top: Saturated and vivid cover art color strictly following the artwork
                val topSat = sat.coerceIn(0.40f, 0.95f)
                val topLum = lum.coerceIn(0.24f, 0.42f)
                val topRgb = ColorUtils.HSLToColor(floatArrayOf(hue, topSat, topLum))

                // Middle: Rich deep version of the exact same cover art hue
                val midSat = (topSat * 0.85f).coerceIn(0.25f, 0.85f)
                val midLum = (topLum * 0.48f).coerceIn(0.12f, 0.22f)
                val midRgb = ColorUtils.HSLToColor(floatArrayOf(hue, midSat, midLum))

                // Bottom: Subtle deep base fading into black
                val bottomSat = (topSat * 0.40f).coerceIn(0.05f, 0.40f)
                val bottomLum = 0.06f
                val bottomRgb = ColorUtils.HSLToColor(floatArrayOf(hue, bottomSat, bottomLum))

                val colors = PaletteColors(
                    dominant = Color(chosenRgb),
                    vibrant = Color(palette.vibrantSwatch?.rgb ?: chosenRgb),
                    muted = Color(palette.mutedSwatch?.rgb ?: midRgb),
                    bgColor = Color(topRgb),
                    bgColorDark = Color(midRgb),
                    bgColorBottom = Color(bottomRgb)
                )

                put(url, colors)
                colors
            } catch (e: Exception) {
                android.util.Log.w("PaletteHelper", "Color extraction failed for $url: ${e.message}")
                PaletteColors()
            }
        }
    }
}

@Composable
fun rememberPaletteColors(imageUrl: String): PaletteColors {
    val context = LocalContext.current
    val initial = remember(imageUrl) { PaletteCache.get(imageUrl) ?: PaletteColors() }
    val colorsState = remember(imageUrl) { mutableStateOf(initial) }

    LaunchedEffect(imageUrl) {
        if (imageUrl.isNotEmpty()) {
            val extracted = PaletteCache.extractColors(context, imageUrl)
            colorsState.value = extracted
        }
    }

    return colorsState.value
}

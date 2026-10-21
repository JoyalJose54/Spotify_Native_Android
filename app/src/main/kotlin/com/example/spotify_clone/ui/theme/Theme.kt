package com.example.spotify_clone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.spotify_clone.R

object SpotifyColors {
    val green = Color(0xFF1DB954)
    val greenLight = Color(0xFF1ED760)
    val black = Color(0xFF121212)
    val darkBg = Color(0xFF191414)
    val surface = Color(0xFF282828)
    val surfaceLight = Color(0xFF3E3E3E)
    val cardBg = Color(0xFF1E1E1E)
    val white = Color(0xFFFFFFFF)
    val lightGrey = Color(0xFFB3B3B3)
    val medGrey = Color(0xFF535353)
    val darkGrey = Color(0xFF282828)
    val error = Color(0xFFE91429)
}

val SpotifyMixFontFamily = FontFamily(
    Font(R.font.spotify_mix_regular, FontWeight.Normal),
    Font(R.font.spotify_mix_bold, FontWeight.Bold),
    Font(R.font.spotify_mix_title_bold, FontWeight.SemiBold),
    Font(R.font.spotify_mix_title_extrabold, FontWeight.ExtraBold)
)

val SpotifyMixMonoFontFamily = FontFamily(
    Font(R.font.spotify_mix_mono, FontWeight.Normal)
)

private val DarkColorScheme = darkColorScheme(
    primary = SpotifyColors.green,
    onPrimary = Color.Black,
    secondary = SpotifyColors.greenLight,
    background = SpotifyColors.black,
    onBackground = SpotifyColors.white,
    surface = SpotifyColors.surface,
    onSurface = SpotifyColors.white,
    surfaceVariant = SpotifyColors.cardBg,
    onSurfaceVariant = SpotifyColors.lightGrey
)

@Composable
fun SpotifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = androidx.compose.material3.Typography(
            titleLarge = TextStyle(
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = SpotifyColors.white
            ),
            titleMedium = TextStyle(
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = SpotifyColors.white
            ),
            titleSmall = TextStyle(
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = SpotifyColors.white
            ),
            bodyLarge = TextStyle(
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                color = SpotifyColors.white
            ),
            bodyMedium = TextStyle(
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = SpotifyColors.lightGrey
            ),
            bodySmall = TextStyle(
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                color = SpotifyColors.lightGrey
            ),
            labelSmall = TextStyle(
                fontFamily = SpotifyMixFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                color = SpotifyColors.lightGrey
            )
        ),
        content = content
    )
}

val SpotifyGreen = SpotifyColors.green
val SpotifyBlack = SpotifyColors.black
val SpotifyDarkSurface = SpotifyColors.surface
val SpotifyElevated = SpotifyColors.cardBg
val SpotifyTextPrimary = SpotifyColors.white
val SpotifyTextSecondary = SpotifyColors.lightGrey

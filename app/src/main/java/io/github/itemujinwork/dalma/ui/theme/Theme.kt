package io.github.itemujinwork.dalma.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.itemujinwork.dalma.R

/** The palette from the character sheet. Always dark: the night sky is the background. */
object DalmaColors {
    val Night = Color(0xFF0E1B19)
    val DeepGreen = Color(0xFF16302B)
    val DeepTeal = Color(0xFF24564B)
    val Mint = Color(0xFF8FD3BE)
    val Milk = Color(0xFFF4FBF8)
    val MilkDim = Color(0xB3F4FBF8)
}

/** Fraunces for the one sentence, Nunito for everything else. Both are variable fonts. */
val Fraunces: FontFamily = FontFamily(
    Font(
        R.font.fraunces,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
            FontVariation.Setting("opsz", 36f),
            FontVariation.Setting("SOFT", 40f),
            FontVariation.Setting("WONK", 0f),
        ),
    ),
)

val Nunito: FontFamily = FontFamily(
    Font(R.font.nunito, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.nunito, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

private val colorScheme = darkColorScheme(
    primary = DalmaColors.Mint,
    onPrimary = DalmaColors.Night,
    secondary = DalmaColors.Milk,
    onSecondary = DalmaColors.Night,
    tertiary = DalmaColors.DeepTeal,
    onTertiary = DalmaColors.Milk,
    background = DalmaColors.Night,
    onBackground = DalmaColors.Milk,
    surface = DalmaColors.Night,
    onSurface = DalmaColors.Milk,
    surfaceVariant = DalmaColors.DeepGreen,
    onSurfaceVariant = DalmaColors.Milk,
    surfaceContainer = DalmaColors.DeepGreen,
    surfaceContainerHigh = DalmaColors.DeepGreen,
    surfaceContainerLow = DalmaColors.Night,
    outline = DalmaColors.DeepTeal,
    outlineVariant = DalmaColors.DeepTeal,
    scrim = Color(0xFF000000),
)

private val typography = Typography(
    // The sentence Dalma says at the end.
    headlineMedium = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
        color = DalmaColors.Mint,
    ),
    titleMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, color = DalmaColors.Milk),
    bodyLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, color = DalmaColors.Milk),
    bodyMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = DalmaColors.MilkDim),
    labelLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, color = DalmaColors.Milk),
)

@Composable
fun DalmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}

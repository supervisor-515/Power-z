package com.powerz.km003c.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.powerz.km003c.R

// Words: Montserrat (stand-in for Proxima Nova). Numbers: Barlow (DIN-style, stand-in for DINPro).
@OptIn(ExperimentalTextApi::class)
private fun montserrat(weight: FontWeight) = Font(
    resId = R.font.montserrat_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val WordsFont = FontFamily(
    montserrat(FontWeight.Medium),
    montserrat(FontWeight.SemiBold),
    montserrat(FontWeight.Bold),
    montserrat(FontWeight.ExtraBold)
)

val NumbersFont = FontFamily(
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold)
)

private const val TNUM = "tnum"

/** Type scale from design/DESIGN.md. Use these instead of ad-hoc sizes. */
object PzType {
    val headline = TextStyle(fontFamily = WordsFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.10.em)
    val section = TextStyle(fontFamily = WordsFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.10.em)
    val bodyLg = TextStyle(fontFamily = WordsFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    val body = TextStyle(fontFamily = WordsFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val bodySm = TextStyle(fontFamily = WordsFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    val caption = TextStyle(fontFamily = WordsFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    val label = TextStyle(fontFamily = WordsFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.12.em)

    val heroNumber = TextStyle(fontFamily = NumbersFont, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 72.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TNUM)
    val metricLg = TextStyle(fontFamily = NumbersFont, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TNUM)
    val metric = TextStyle(fontFamily = NumbersFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 28.sp, fontFeatureSettings = TNUM)
    val metricSm = TextStyle(fontFamily = NumbersFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM)
    val unit = TextStyle(fontFamily = WordsFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp)
}

val PowerZTypography = Typography(
    titleLarge = PzType.bodyLg.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleMedium = PzType.bodyLg,
    titleSmall = PzType.body,
    bodyLarge = PzType.bodyLg,
    bodyMedium = PzType.body,
    bodySmall = PzType.bodySm,
    labelLarge = PzType.body.copy(fontWeight = FontWeight.Bold),
    labelMedium = PzType.caption.copy(fontWeight = FontWeight.Bold),
    labelSmall = PzType.caption
)

object PzRadius {
    val sm = 4.dp
    val md = 8.dp
    val lg = 16.dp
}

val PowerZShapes = Shapes(
    extraSmall = RoundedCornerShape(PzRadius.sm),
    small = RoundedCornerShape(PzRadius.md),
    medium = RoundedCornerShape(PzRadius.md),
    large = RoundedCornerShape(PzRadius.lg),
    extraLarge = RoundedCornerShape(PzRadius.lg)
)

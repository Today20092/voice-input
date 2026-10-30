package org.futo.voiceinput.theme.presets

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.futo.voiceinput.BuildConfig
import org.futo.voiceinput.R
import org.futo.voiceinput.theme.ThemeOption
import org.futo.voiceinput.theme.selector.ThemePreview

val Primary = Color(0xFFA39B50)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFE6E2C0)
val OnPrimaryContainer = Color(0xFF333019)
val Secondary = Color(0xFF6B705B)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFE3E6D8)
val OnSecondaryContainer = Color(0xFF313329)
val Tertiary = Color(0xFF7D7452)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFE6E0CD)
val OnTertiaryContainer = Color(0xFF332F22)
val Error = Color(0xFFB3261E)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFE6ACA9)
val OnErrorContainer = Color(0xFF330B09)
val Background = Color(0xFFfcfcfc)
val OnBackground = Color(0xFF333332)
val Surface = Color(0xFFfcfcfc)
val OnSurface = Color(0xFF333332)
val SurfaceVariant = Color(0xFFe6e5de)
val OnSurfaceVariant = Color(0xFF66655c)
val Outline = Color(0xFF999789)
val SurfaceDim = Color(0xFFDDDDD6)
val SurfaceBright = Color(0xFFFCFCFC)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF6F6F3)
val SurfaceContainer = Color(0xFFF1F1ED)
val SurfaceContainerHigh = Color(0xFFEBEBE7)
val SurfaceContainerHighest = Color(0xFFE5E5E1)
val OutlineVariant = Color(0xFFCCCBC0)
val Scrim = Color(0xFF000000)
val InverseSurface = Color(0xFF333332)
val InverseOnSurface = Color(0xFFF4F4F1)
val InversePrimary = Color(0xFFE6E0B1)

val PrimaryDark = Color(0xFFE6E0B1)
val OnPrimaryDark = Color(0xFF4C4925)
val PrimaryContainerDark = Color(0xFF666132)
val OnPrimaryContainerDark = Color(0xFFE6E2C0)
val SecondaryDark = Color(0xFFE1E6D2)
val OnSecondaryDark = Color(0xFF494C3E)
val SecondaryContainerDark = Color(0xFF626653)
val OnSecondaryContainerDark = Color(0xFFE3E6D8)
val TertiaryDark = Color(0xFFE6DEC3)
val OnTertiaryDark = Color(0xFF4C4732)
val TertiaryContainerDark = Color(0xFF665F43)
val OnTertiaryContainerDark = Color(0xFFE6E0CD)
val ErrorDark = Color(0xFFE69490)
val OnErrorDark = Color(0xFF4C100D)
val ErrorContainerDark = Color(0xFF661511)
val OnErrorContainerDark = Color(0xFFE6ACA9)
val BackgroundDark = Color(0xFF333332)
val OnBackgroundDark = Color(0xFFe6e5e4)
val SurfaceDark = Color(0xFF333332)
val OnSurfaceDark = Color(0xFFe6e5e4)
val SurfaceVariantDark = Color(0xFF66655c)
val OnSurfaceVariantDark = Color(0xFFe6e4db)
val OutlineDark = Color(0xFFb3b1a7)
val SurfaceDimDark = Color(0xFF333332)
val SurfaceBrightDark = Color(0xFF595957)
val SurfaceContainerLowestDark = Color(0xFF2E2E2D)
val SurfaceContainerLowDark = Color(0xFF3B3B3A)
val SurfaceContainerDark = Color(0xFF3F3F3E)
val SurfaceContainerHighDark = Color(0xFF4A4A48)
val SurfaceContainerHighestDark = Color(0xFF545452)
val OutlineVariantDark = Color(0xFF66655C)
val ScrimDark = Color(0xFF000000)
val InverseSurfaceDark = Color(0xFFE6E5E4)
val InverseOnSurfaceDark = Color(0xFF333332)
val InversePrimaryDark = Color(0xFFA39B50)


private val lightColorSchemeDev = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    surfaceDim = SurfaceDim,
    surfaceBright = SurfaceBright,
    surfaceContainer = SurfaceContainer,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerHighest = SurfaceContainerHighest,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    inversePrimary = InversePrimary,
    scrim = Scrim,
    outlineVariant = OutlineVariant
)

private val darkColorSchemeDev = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    surfaceDim = SurfaceDimDark,
    surfaceBright = SurfaceBrightDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = InversePrimaryDark,
    scrim = ScrimDark,
    outlineVariant = OutlineVariantDark
)

val DevThemeYellow = ThemeOption(
    dynamic = false,
    key = "DevThemeYellow",
    name = R.string.dev_theme_name,
    available = {
        (BuildConfig.FLAVOR == "dev") || (BuildConfig.FLAVOR == "devSameId")
    }
) {
    darkColorSchemeDev
}

@Composable
@Preview
private fun PreviewTheme() {
    ThemePreview(DevThemeYellow)
}
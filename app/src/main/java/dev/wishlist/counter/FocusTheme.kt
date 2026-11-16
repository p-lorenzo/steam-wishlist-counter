package dev.wishlist.counter

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light=lightColorScheme(
    primary=Color(0xFF74539A),onPrimary=Color.White,primaryContainer=Color(0xFFE9DFF4),onPrimaryContainer=Color(0xFF30283C),
    secondary=Color(0xFF62546F),secondaryContainer=Color(0xFFE4D5F1),onSecondaryContainer=Color(0xFF30283C),
    background=Color(0xFFFAF8FD),onBackground=Color(0xFF30283C),surface=Color(0xFFFAF8FD),onSurface=Color(0xFF30283C),
    surfaceVariant=Color(0xFFE9DFF4),onSurfaceVariant=Color(0xFF6C6178),outline=Color(0xFF82748F),outlineVariant=Color(0xFFDED7E8),
)
private val Dark=darkColorScheme(
    primary=Color(0xFFD7C3FA),onPrimary=Color(0xFF302140),primaryContainer=Color(0xFF40364F),onPrimaryContainer=Color(0xFFF0EDF7),
    secondary=Color(0xFFCFC0DD),secondaryContainer=Color(0xFF534266),onSecondaryContainer=Color(0xFFF0EDF7),
    background=Color(0xFF201F2B),onBackground=Color(0xFFF0EDF7),surface=Color(0xFF201F2B),onSurface=Color(0xFFF0EDF7),
    surfaceVariant=Color(0xFF40364F),onSurfaceVariant=Color(0xFFBCB7CE),outline=Color(0xFF9687A3),outlineVariant=Color(0xFF494553),
)
@Composable fun FocusTheme(appearance:Appearance,content: @Composable () -> Unit){
    val dark=when(appearance){Appearance.SYSTEM->isSystemInDarkTheme();Appearance.DARK->true;Appearance.LIGHT->false}
    MaterialTheme(colorScheme=if(dark)Dark else Light,content=content)
}

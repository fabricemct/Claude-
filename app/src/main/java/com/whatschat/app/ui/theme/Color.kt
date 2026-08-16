package com.whatschat.app.ui.theme

import androidx.compose.ui.graphics.Color

val WaGreenDark = Color(0xFF075E54)
val WaGreen = Color(0xFF128C7E)
val WaGreenLight = Color(0xFF25D366)
val WaTeal = Color(0xFF34B7F1)
val WaBackground = Color(0xFFECE5DD)
val WaBubbleOutgoing = Color(0xFFDCF8C6)
val WaBubbleIncoming = Color(0xFFFFFFFF)

/** Bubbles are always light regardless of app/system dark mode, so their text must always be dark too — never the theme's dynamic text color. */
val WaBubbleText = Color(0xFF1B1B1B)
val WaBubbleTimestamp = Color(0xFF5B5B5B)

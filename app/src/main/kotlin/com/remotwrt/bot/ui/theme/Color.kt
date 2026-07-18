package com.remotwrt.bot.ui.theme

import androidx.compose.ui.graphics.Color

// Primary accent: a signal/network teal, not the generic "AI assistant blue" --
// meant to evoke a live connection/uplink rather than a generic brand color.
val RemotTeal = Color(0xFF17B8A6)
val RemotTealBright = Color(0xFF4DE8D4)

// Semantic status colors -- these carry meaning (online/offline/warning) so
// they stay separate from the brand accent above and never get reused for
// decoration.
val RemotGreen = Color(0xFF2FB380)
val RemotRed = Color(0xFFE5484D)
val RemotAmber = Color(0xFFE5A83D)

// Backwards-compat alias (older code referenced RemotBlue directly).
val RemotBlue = RemotTeal

val RemotBackground = Color(0xFF0A0E13)
val RemotSurface = Color(0xFF141B22)
val RemotSurfaceElevated = Color(0xFF1C252E)

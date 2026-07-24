package com.remotwrt.bot.ui.theme

import androidx.compose.ui.graphics.Color

// Primary accent: indigo, matching the "Modern Dark" reference mockup
// (indigo-400/500/900 in Tailwind terms).
val RemotIndigo = Color(0xFF6366F1)       // indigo-500
val RemotIndigoLight = Color(0xFF818CF8)  // indigo-400
val RemotIndigoDark = Color(0xFF312E81)   // indigo-900, used for gradient cards

// Semantic status colors -- these carry meaning (online/offline/warning) so
// they stay separate from the brand accent above and never get reused for
// decoration.
val RemotGreen = Color(0xFF2FB380)
val RemotRed = Color(0xFFE5484D)
val RemotAmber = Color(0xFFE5A83D)

// Backwards-compat aliases (older code referenced these names directly).
val RemotBlue = RemotIndigo
val RemotTeal = RemotIndigo
val RemotTealBright = RemotIndigoLight

val RemotBackground = Color(0xFF0A0B12)
val RemotSurface = Color(0xFF14151F)
val RemotSurfaceElevated = Color(0xFF1C1E2B)

package com.speedbump.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Speedbump is dark-first. The light scheme exists so the app is usable in
 * bright sun, but every design decision is made against the dark palette.
 *
 * The palette is deliberately calm: the interception screen is shown to
 * someone in the grip of an impulse, and a loud UI reads as a punishment.
 * Amber is the only saturated colour and it is reserved for the speedbump
 * itself - the moment of friction.
 */
internal object SpeedbumpPalette {
    val Amber = Color(0xFFFFC24B)
    val AmberDim = Color(0xFF8A6417)
    val AmberContainer = Color(0xFF3A2C0C)

    val Ink = Color(0xFF0B0B0F)
    val InkElevated = Color(0xFF14141B)
    val InkCard = Color(0xFF1B1B24)
    val InkOutline = Color(0xFF33333F)

    val Bone = Color(0xFFEDEDF2)
    val BoneDim = Color(0xFFA0A0AE)

    val Sage = Color(0xFF7FD1A4)
    val SageContainer = Color(0xFF13341F)

    val Rust = Color(0xFFE8785A)
    val RustContainer = Color(0xFF3D1A12)

    val PaperWhite = Color(0xFFFBFBFD)
    val PaperCard = Color(0xFFF0F0F5)
    val PaperOutline = Color(0xFFC9C9D4)
    val PaperInk = Color(0xFF16161C)
    val PaperSage = Color(0xFF2E6B48)
}

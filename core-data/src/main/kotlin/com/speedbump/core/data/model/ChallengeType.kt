package com.speedbump.core.data.model

/**
 * What the user must do to get past the speedbump, beyond simply waiting.
 *
 * The ordering is roughly by effort. [NFC_TAG] and [QR_CODE] are the physical
 * ones: you put a tag in another room, and reaching for the blocked app costs
 * you a walk.
 */
enum class ChallengeType {
    /** Wait out the delay. Nothing more. */
    NONE,

    /** Retype a randomly generated sentence. Defeats muscle memory. */
    TYPING,

    /** Solve arithmetic. Engages the part of the brain the feed switches off. */
    MATH,

    /** Hold a button continuously. Cheap to build, surprisingly effective. */
    HOLD,

    /** Scan an NFC tag you physically placed somewhere inconvenient. */
    NFC_TAG,

    /** Scan a QR code you printed and stuck somewhere inconvenient. */
    QR_CODE,
    ;

    val requiresPhysicalObject: Boolean get() = this == NFC_TAG || this == QR_CODE
}

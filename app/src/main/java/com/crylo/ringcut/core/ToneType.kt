package com.crylo.ringcut.core

/**
 * What a saved tone is for. Decides the shared folder it lands in and which MediaStore
 * flag it carries, which in turn decides which system picker lists it.
 */
enum class ToneType {
    RINGTONE,
    NOTIFICATION,
    ALARM,
    MUSIC;

    /** Music has no system "default" slot, so only the other three can be set as one. */
    val canBeDefault: Boolean get() = this != MUSIC
}

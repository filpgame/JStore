package com.aurora.store.data.model

/**
 * Supported installers for Aurora Store
 */
enum class Installer {
    SESSION,
    NATIVE,
    ROOT,
    SERVICE,
    AM,
    SHIZUKU,
    MICROG,

    // Keep this entry last: installer preferences persist enum ordinals.
    JAECOO
}

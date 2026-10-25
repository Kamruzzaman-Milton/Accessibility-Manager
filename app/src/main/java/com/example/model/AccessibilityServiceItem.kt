package com.example.model

import android.content.ComponentName
import android.graphics.Bitmap

/**
 * Represents an installed accessibility service on the device.
 */
data class AccessibilityServiceItem(
    val appName: String,
    val packageName: String,
    val serviceClassName: String,
    val serviceSimpleName: String,
    val serviceLabel: String,
    val isEnabled: Boolean,
    val isPinned: Boolean = false,
    val isMultipleForApp: Boolean = false,
    val description: String? = null,
    val appIcon: Bitmap? = null
) {
    val componentName: ComponentName
        get() = ComponentName(packageName, serviceClassName)

    val componentFlattened: String
        get() = "$packageName/$serviceClassName"

    val componentShort: String
        get() = componentName.flattenToShortString()
}

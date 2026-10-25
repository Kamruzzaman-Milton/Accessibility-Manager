package com.example.model

data class AppGroupUiModel(
    val id: String,
    val name: String,
    val memberServices: List<AccessibilityServiceItem> = emptyList(),
    val isToggling: Boolean = false
) {
    val totalCount: Int get() = memberServices.size
    val enabledCount: Int get() = memberServices.count { it.isEnabled }
    val isAllEnabled: Boolean get() = memberServices.isNotEmpty() && memberServices.all { it.isEnabled }
    val isPartiallyEnabled: Boolean get() = memberServices.any { it.isEnabled } && !isAllEnabled
}

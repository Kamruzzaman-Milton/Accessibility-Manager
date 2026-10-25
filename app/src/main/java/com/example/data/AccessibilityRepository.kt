package com.example.data

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.example.data.local.AppDatabase
import com.example.data.local.AppGroupEntity
import com.example.data.local.GroupServiceCrossRef
import com.example.data.local.PinnedServiceEntity
import com.example.model.AccessibilityServiceItem
import com.example.model.AppGroupUiModel
import com.example.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AccessibilityRepository(
    private val context: Context,
    private val shizukuManager: ShizukuManager
) {
    private val TAG = "AccessibilityRepository"
    private val iconCache = ConcurrentHashMap<String, Bitmap?>()
    private val dao = AppDatabase.getInstance(context).appDao()

    val pinnedServiceKeys: Flow<Set<String>> = dao.getAllPinnedServices().map { list ->
        list.map { it.componentKey }.toSet()
    }

    val allGroups: Flow<List<AppGroupEntity>> = dao.getAllGroups()
    val allGroupServices: Flow<List<GroupServiceCrossRef>> = dao.getAllGroupServices()

    suspend fun togglePinService(componentKey: String, pin: Boolean) = withContext(Dispatchers.IO) {
        if (pin) {
            dao.pinService(PinnedServiceEntity(componentKey = componentKey))
        } else {
            dao.unpinService(componentKey)
        }
    }

    suspend fun saveGroup(id: String, name: String, componentKeys: List<String>) = withContext(Dispatchers.IO) {
        dao.saveGroupWithServices(AppGroupEntity(id = id, name = name), componentKeys)
    }

    suspend fun deleteGroup(groupId: String) = withContext(Dispatchers.IO) {
        dao.deleteGroupAndMembers(groupId)
    }

    /**
     * Reads the current raw string of enabled accessibility services from Android settings.
     */
    fun getRawEnabledServices(): String {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read ENABLED_ACCESSIBILITY_SERVICES from ContentResolver", e)
            ""
        }
    }

    /**
     * Checks if accessibility is enabled globally.
     */
    fun isAccessibilityEnabledGlobally(): Boolean {
        return try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Parses the colon-separated list of enabled services.
     */
    fun parseEnabledServices(raw: String): List<String> {
        if (raw.isBlank() || raw.equals("null", ignoreCase = true)) return emptyList()
        return raw.split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Checks if a specific service component matches an entry from the enabled services list.
     */
    fun matchesService(entry: String, packageName: String, serviceClassName: String): Boolean {
        val trimmed = entry.trim()
        if (trimmed.isEmpty()) return false

        val cn = ComponentName.unflattenFromString(trimmed)
        if (cn != null && cn.packageName == packageName && cn.className == serviceClassName) {
            return true
        }

        val fullForm = "$packageName/$serviceClassName"
        if (trimmed.equals(fullForm, ignoreCase = true)) return true

        // Handle short form variations like com.foo/.BarService or com.foo/BarService
        if (trimmed.startsWith("$packageName/")) {
            val classPart = trimmed.substring(packageName.length + 1).removePrefix(".")
            val targetSimple = serviceClassName.substringAfterLast('.')
            if (classPart == serviceClassName || classPart == targetSimple) {
                return true
            }
        }
        return false
    }

    /**
     * Discovers all installed apps declaring an AccessibilityService.
     */
    suspend fun loadInstalledAccessibilityServices(pinnedKeys: Set<String> = emptySet()): List<AccessibilityServiceItem> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val rawEnabled = getRawEnabledServices()
            val enabledList = parseEnabledServices(rawEnabled)

            // Collect all unique component names
            val foundServices = LinkedHashMap<String, ServiceMeta>()

            // 1. Query from AccessibilityManager
            try {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                val list = am?.getInstalledAccessibilityServiceList().orEmpty()
                for (info in list) {
                    val resolveInfo = info.resolveInfo
                    val serviceInfo = resolveInfo?.serviceInfo ?: continue
                    val pkg = serviceInfo.packageName
                    val cls = serviceInfo.name
                    if (pkg == context.packageName) continue // Skip our own app

                    val key = "$pkg/$cls"
                    val label = try {
                        info.resolveInfo.loadLabel(pm)?.toString()
                            ?: serviceInfo.loadLabel(pm)?.toString()
                            ?: serviceInfo.applicationInfo?.loadLabel(pm)?.toString()
                            ?: cls.substringAfterLast('.')
                    } catch (e: Exception) {
                        cls.substringAfterLast('.')
                    }

                    val appName = try {
                        serviceInfo.applicationInfo?.loadLabel(pm)?.toString() ?: pkg
                    } catch (e: Exception) {
                        pkg
                    }

                    val description = try {
                        info.loadDescription(pm)
                    } catch (e: Exception) {
                        null
                    }

                    foundServices[key] = ServiceMeta(
                        packageName = pkg,
                        serviceClassName = cls,
                        appName = appName,
                        serviceLabel = label,
                        description = description
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying AccessibilityManager services", e)
            }

            // 2. Query from PackageManager via intent filter to catch all services
            try {
                val intent = Intent(AccessibilityService.SERVICE_INTERFACE)
                val flags = PackageManager.MATCH_ALL
                val resolveInfos = pm.queryIntentServices(intent, flags)
                for (resolve in resolveInfos) {
                    val serviceInfo = resolve.serviceInfo ?: continue
                    val pkg = serviceInfo.packageName
                    val cls = serviceInfo.name
                    if (pkg == context.packageName) continue

                    val key = "$pkg/$cls"
                    if (!foundServices.containsKey(key)) {
                        val label = try {
                            resolve.loadLabel(pm)?.toString()
                                ?: serviceInfo.loadLabel(pm)?.toString()
                                ?: serviceInfo.applicationInfo?.loadLabel(pm)?.toString()
                                ?: cls.substringAfterLast('.')
                        } catch (e: Exception) {
                            cls.substringAfterLast('.')
                        }

                        val appName = try {
                            serviceInfo.applicationInfo?.loadLabel(pm)?.toString() ?: pkg
                        } catch (e: Exception) {
                            pkg
                        }

                        foundServices[key] = ServiceMeta(
                            packageName = pkg,
                            serviceClassName = cls,
                            appName = appName,
                            serviceLabel = label,
                            description = null
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying PackageManager accessibility services", e)
            }

            // Count services per package to mark multiple services
            val packageServiceCounts = foundServices.values.groupBy { it.packageName }
                .mapValues { it.value.size }

            val items = foundServices.values.map { meta ->
                val isEnabled = enabledList.any { entry ->
                    matchesService(entry, meta.packageName, meta.serviceClassName)
                }
                val simpleName = meta.serviceClassName.substringAfterLast('.')
                val isMultiple = (packageServiceCounts[meta.packageName] ?: 1) > 1
                val key = "${meta.packageName}/${meta.serviceClassName}"
                val isPinned = pinnedKeys.contains(key)

                val icon = getAppIcon(meta.packageName, pm)

                AccessibilityServiceItem(
                    appName = meta.appName,
                    packageName = meta.packageName,
                    serviceClassName = meta.serviceClassName,
                    serviceSimpleName = simpleName,
                    serviceLabel = meta.serviceLabel,
                    isEnabled = isEnabled,
                    isPinned = isPinned,
                    isMultipleForApp = isMultiple,
                    description = meta.description,
                    appIcon = icon
                )
            }.sortedWith(
                compareByDescending<AccessibilityServiceItem> { it.isPinned }
                    .thenByDescending { it.isEnabled }
                    .thenBy { it.appName.lowercase() }
                    .thenBy { it.serviceLabel.lowercase() }
            )

            items
        }

    /**
     * Toggles a single accessibility service while strictly preserving all other enabled services.
     */
    suspend fun toggleService(
        item: AccessibilityServiceItem,
        targetEnabled: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val rawEnabled = getRawEnabledServices()
        val currentList = parseEnabledServices(rawEnabled).toMutableList()

        val currentlyMatches = currentList.any { entry ->
            matchesService(entry, item.packageName, item.serviceClassName)
        }

        if (currentlyMatches == targetEnabled) {
            // Already in target state
            return@withContext Result.success(Unit)
        }

        val updatedList: List<String> = if (targetEnabled) {
            // Add component preserving all existing entries
            val filtered = currentList.filterNot { entry ->
                matchesService(entry, item.packageName, item.serviceClassName)
            }.toMutableList()
            filtered.add(item.componentFlattened)
            filtered
        } else {
            // Remove only this service
            currentList.filterNot { entry ->
                matchesService(entry, item.packageName, item.serviceClassName)
            }
        }

        return@withContext writeEnabledServices(updatedList)
    }

    /**
     * Toggles all accessibility services under a group.
     * When turning ON: enables all member services while preserving other active services.
     * When turning OFF: disables all member services while preserving other active services.
     */
    suspend fun toggleGroupServices(
        memberServices: List<AccessibilityServiceItem>,
        targetEnabled: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (memberServices.isEmpty()) return@withContext Result.success(Unit)

        val rawEnabled = getRawEnabledServices()
        val currentList = parseEnabledServices(rawEnabled).toMutableList()

        val updatedList: List<String> = if (targetEnabled) {
            val filtered = currentList.toMutableList()
            for (member in memberServices) {
                val exists = filtered.any { matchesService(it, member.packageName, member.serviceClassName) }
                if (!exists) {
                    filtered.add(member.componentFlattened)
                }
            }
            filtered
        } else {
            currentList.filterNot { entry ->
                memberServices.any { member ->
                    matchesService(entry, member.packageName, member.serviceClassName)
                }
            }
        }

        return@withContext writeEnabledServices(updatedList)
    }

    /**
     * Enables all services in the provided list, preserving already-enabled external services.
     */
    suspend fun enableAllServices(services: List<AccessibilityServiceItem>): Result<Unit> =
        withContext(Dispatchers.IO) {
            val rawEnabled = getRawEnabledServices()
            val currentList = parseEnabledServices(rawEnabled).toMutableList()

            for (service in services) {
                val exists = currentList.any { entry ->
                    matchesService(entry, service.packageName, service.serviceClassName)
                }
                if (!exists) {
                    currentList.add(service.componentFlattened)
                }
            }

            writeEnabledServices(currentList)
        }

    /**
     * Disables all accessibility services safely.
     */
    suspend fun disableAllServices(): Result<Unit> = withContext(Dispatchers.IO) {
        writeEnabledServices(emptyList())
    }

    /**
     * Writes the new list of enabled accessibility services using Shizuku privileged shell
     * and reinforces with ContentResolver if permission is granted.
     */
    private suspend fun writeEnabledServices(services: List<String>): Result<Unit> {
        val joined = services.joinToString(":")
        val globalEnabled = if (services.isNotEmpty()) "1" else "0"

        Log.d(TAG, "Writing enabled_accessibility_services: '$joined', global: $globalEnabled")

        // 1. Execute via Shizuku
        val commandResult = if (services.isEmpty()) {
            // Empty services list
            val res1 = shizukuManager.executeCommand("settings", "put", "secure", "enabled_accessibility_services", "''")
            val res2 = shizukuManager.executeCommand("settings", "put", "secure", "accessibility_enabled", "0")
            if (res1.success || res2.success) res1 else res1
        } else {
            val res1 = shizukuManager.executeCommand("settings", "put", "secure", "enabled_accessibility_services", joined)
            val res2 = shizukuManager.executeCommand("settings", "put", "secure", "accessibility_enabled", globalEnabled)
            if (res1.success) res2 else res1
        }

        // 2. Also attempt direct ContentResolver write as reinforcement
        try {
            Settings.Secure.putString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                joined
            )
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                if (services.isNotEmpty()) 1 else 0
            )
        } catch (e: Exception) {
            // Ignored if WRITE_SECURE_SETTINGS not yet granted to app process directly
            Log.d(TAG, "Direct ContentResolver write skipped/not permitted: ${e.message}")
        }

        if (!commandResult.success) {
            // If Shizuku command failed, return informative failure
            val errorMsg = if (commandResult.error.isNotBlank()) {
                commandResult.error
            } else {
                "Command failed with exit code ${commandResult.exitCode}"
            }
            return Result.failure(Exception(errorMsg))
        }

        return Result.success(Unit)
    }

    private fun getAppIcon(packageName: String, pm: PackageManager): Bitmap? {
        return iconCache.getOrPut(packageName) {
            try {
                val drawable = pm.getApplicationIcon(packageName)
                drawableToBitmap(drawable)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private data class ServiceMeta(
        val packageName: String,
        val serviceClassName: String,
        val appName: String,
        val serviceLabel: String,
        val description: String?
    )
}

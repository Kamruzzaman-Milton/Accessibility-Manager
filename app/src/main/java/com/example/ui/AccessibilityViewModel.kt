package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccessibilityRepository
import com.example.data.local.AppGroupEntity
import com.example.data.local.GroupServiceCrossRef
import com.example.model.AccessibilityServiceItem
import com.example.model.AppGroupUiModel
import com.example.shizuku.ShizukuManager
import com.example.shizuku.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppTab {
    SERVICES, GROUPS
}

enum class FilterMode {
    ALL, ENABLED_ONLY, DISABLED_ONLY
}

data class AccessibilityUiState(
    val currentTab: AppTab = AppTab.SERVICES,
    val allServices: List<AccessibilityServiceItem> = emptyList(),
    val filteredServices: List<AccessibilityServiceItem> = emptyList(),
    val pinnedKeys: Set<String> = emptySet(),
    val groups: List<AppGroupUiModel> = emptyList(),
    val searchQuery: String = "",
    val filterMode: FilterMode = FilterMode.ALL,
    val isLoading: Boolean = false,
    val isToggling: Boolean = false,
    val togglingServiceKey: String? = null,
    val togglingGroupId: String? = null,
    val shizukuState: ShizukuState = ShizukuState.Checking,
    val showConfirmEnableAllDialog: Boolean = false,
    val showConfirmDisableAllDialog: Boolean = false,
    val showCreateOrEditGroupDialog: Boolean = false,
    val editingGroup: AppGroupUiModel? = null,
    val userFeedbackMessage: String? = null,
    val isErrorFeedback: Boolean = false
) {
    val enabledCount: Int get() = allServices.count { it.isEnabled }
    val totalCount: Int get() = allServices.size
    val pinnedCount: Int get() = allServices.count { it.isPinned }
}

class AccessibilityViewModel(application: Application) : AndroidViewModel(application) {

    val shizukuManager = ShizukuManager(application)
    private val repository = AccessibilityRepository(application, shizukuManager)

    private val _uiState = MutableStateFlow(AccessibilityUiState())
    val uiState: StateFlow<AccessibilityUiState> = _uiState.asStateFlow()

    private var rawGroupsList: List<AppGroupEntity> = emptyList()
    private var rawGroupServicesList: List<GroupServiceCrossRef> = emptyList()

    init {
        shizukuManager.init()

        // Observe Shizuku state
        viewModelScope.launch {
            shizukuManager.state.collect { state ->
                _uiState.update { it.copy(shizukuState = state) }
            }
        }

        // Observe Pinned Services
        viewModelScope.launch {
            repository.pinnedServiceKeys.collect { pinned ->
                _uiState.update { state ->
                    val updatedAll = state.allServices.map {
                        it.copy(isPinned = pinned.contains(it.componentFlattened))
                    }.sortedWith(
                        compareByDescending<AccessibilityServiceItem> { it.isPinned }
                            .thenByDescending { it.isEnabled }
                            .thenBy { it.appName.lowercase() }
                            .thenBy { it.serviceLabel.lowercase() }
                    )
                    val filtered = applyFilterAndSearch(updatedAll, state.searchQuery, state.filterMode)
                    state.copy(
                        pinnedKeys = pinned,
                        allServices = updatedAll,
                        filteredServices = filtered
                    )
                }
            }
        }

        // Observe Groups and Group Services
        viewModelScope.launch {
            combine(
                repository.allGroups,
                repository.allGroupServices
            ) { groups, crossRefs ->
                Pair(groups, crossRefs)
            }.collect { (groups, crossRefs) ->
                rawGroupsList = groups
                rawGroupServicesList = crossRefs
                rebuildGroupUiModels()
            }
        }

        refresh()
    }

    override fun onCleared() {
        super.onCleared()
        shizukuManager.destroy()
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            shizukuManager.checkStatus()

            try {
                val currentPinned = _uiState.value.pinnedKeys
                val services = repository.loadInstalledAccessibilityServices(currentPinned)
                _uiState.update { state ->
                    val filtered = applyFilterAndSearch(services, state.searchQuery, state.filterMode)
                    state.copy(
                        allServices = services,
                        filteredServices = filtered,
                        isLoading = false
                    )
                }
                rebuildGroupUiModels()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userFeedbackMessage = "Failed to load services: ${e.localizedMessage}",
                        isErrorFeedback = true
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val filtered = applyFilterAndSearch(state.allServices, query, state.filterMode)
            state.copy(searchQuery = query, filteredServices = filtered)
        }
    }

    fun onFilterModeChanged(filterMode: FilterMode) {
        _uiState.update { state ->
            val filtered = applyFilterAndSearch(state.allServices, state.searchQuery, filterMode)
            state.copy(filterMode = filterMode, filteredServices = filtered)
        }
    }

    fun togglePin(item: AccessibilityServiceItem) {
        val willPin = !item.isPinned
        viewModelScope.launch {
            repository.togglePinService(item.componentFlattened, willPin)
            _uiState.update {
                it.copy(
                    userFeedbackMessage = if (willPin) "Pinned ${item.appName} to top" else "Unpinned ${item.appName}",
                    isErrorFeedback = false
                )
            }
        }
    }

    fun toggleService(item: AccessibilityServiceItem, targetEnabled: Boolean) {
        if (_uiState.value.shizukuState !is ShizukuState.Ready) {
            _uiState.update {
                it.copy(
                    userFeedbackMessage = "Shizuku is not ready. Please authorize Shizuku first.",
                    isErrorFeedback = true
                )
            }
            return
        }

        viewModelScope.launch {
            val serviceKey = item.componentFlattened
            _uiState.update { it.copy(isToggling = true, togglingServiceKey = serviceKey) }

            val result = repository.toggleService(item, targetEnabled)

            if (result.isSuccess) {
                val actionWord = if (targetEnabled) "Enabled" else "Disabled"
                _uiState.update { state ->
                    val updatedList = state.allServices.map {
                        if (it.packageName == item.packageName && it.serviceClassName == item.serviceClassName) {
                            it.copy(isEnabled = targetEnabled)
                        } else {
                            it
                        }
                    }
                    val filtered = applyFilterAndSearch(updatedList, state.searchQuery, state.filterMode)
                    state.copy(
                        allServices = updatedList,
                        filteredServices = filtered,
                        isToggling = false,
                        togglingServiceKey = null,
                        userFeedbackMessage = "$actionWord ${item.appName}",
                        isErrorFeedback = false
                    )
                }
                rebuildGroupUiModels()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Operation failed"
                _uiState.update {
                    it.copy(
                        isToggling = false,
                        togglingServiceKey = null,
                        userFeedbackMessage = "Failed: $errorMsg",
                        isErrorFeedback = true
                    )
                }
            }

            // Sync with system in background
            try {
                val fresh = repository.loadInstalledAccessibilityServices(_uiState.value.pinnedKeys)
                _uiState.update { state ->
                    val filtered = applyFilterAndSearch(fresh, state.searchQuery, state.filterMode)
                    state.copy(allServices = fresh, filteredServices = filtered)
                }
                rebuildGroupUiModels()
            } catch (e: Exception) {
                // Ignored
            }
        }
    }

    /**
     * Toggles all accessibility permissions for all apps in the given group.
     */
    fun toggleGroup(group: AppGroupUiModel, targetEnabled: Boolean) {
        if (_uiState.value.shizukuState !is ShizukuState.Ready) {
            _uiState.update {
                it.copy(
                    userFeedbackMessage = "Shizuku is not ready. Please authorize Shizuku first.",
                    isErrorFeedback = true
                )
            }
            return
        }

        if (group.memberServices.isEmpty()) {
            _uiState.update {
                it.copy(
                    userFeedbackMessage = "Group '${group.name}' has no apps assigned.",
                    isErrorFeedback = true
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(togglingGroupId = group.id) }

            val result = repository.toggleGroupServices(group.memberServices, targetEnabled)

            if (result.isSuccess) {
                val actionWord = if (targetEnabled) "Enabled" else "Disabled"
                _uiState.update { state ->
                    val memberKeys = group.memberServices.map { it.componentFlattened }.toSet()
                    val updatedServices = state.allServices.map {
                        if (memberKeys.contains(it.componentFlattened)) {
                            it.copy(isEnabled = targetEnabled)
                        } else {
                            it
                        }
                    }
                    val filtered = applyFilterAndSearch(updatedServices, state.searchQuery, state.filterMode)
                    state.copy(
                        allServices = updatedServices,
                        filteredServices = filtered,
                        togglingGroupId = null,
                        userFeedbackMessage = "$actionWord all apps in '${group.name}'",
                        isErrorFeedback = false
                    )
                }
                rebuildGroupUiModels()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Group toggle failed"
                _uiState.update {
                    it.copy(
                        togglingGroupId = null,
                        userFeedbackMessage = "Failed: $errorMsg",
                        isErrorFeedback = true
                    )
                }
            }

            // Sync with system in background
            try {
                val fresh = repository.loadInstalledAccessibilityServices(_uiState.value.pinnedKeys)
                _uiState.update { state ->
                    val filtered = applyFilterAndSearch(fresh, state.searchQuery, state.filterMode)
                    state.copy(allServices = fresh, filteredServices = filtered)
                }
                rebuildGroupUiModels()
            } catch (e: Exception) {
                // Ignored
            }
        }
    }

    fun openCreateGroupDialog() {
        _uiState.update {
            it.copy(
                showCreateOrEditGroupDialog = true,
                editingGroup = null
            )
        }
    }

    fun openEditGroupDialog(group: AppGroupUiModel) {
        _uiState.update {
            it.copy(
                showCreateOrEditGroupDialog = true,
                editingGroup = group
            )
        }
    }

    fun dismissGroupDialog() {
        _uiState.update {
            it.copy(
                showCreateOrEditGroupDialog = false,
                editingGroup = null
            )
        }
    }

    fun saveGroup(name: String, selectedKeys: List<String>) {
        if (name.isBlank()) return
        val currentEditing = _uiState.value.editingGroup
        val groupId = currentEditing?.id ?: UUID.randomUUID().toString()

        viewModelScope.launch {
            repository.saveGroup(groupId, name.trim(), selectedKeys)
            _uiState.update {
                it.copy(
                    showCreateOrEditGroupDialog = false,
                    editingGroup = null,
                    userFeedbackMessage = if (currentEditing != null) "Group updated" else "Group '${name.trim()}' created",
                    isErrorFeedback = false
                )
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            repository.deleteGroup(groupId)
            _uiState.update {
                it.copy(
                    userFeedbackMessage = "Group deleted",
                    isErrorFeedback = false
                )
            }
        }
    }

    fun requestEnableAll() {
        if (_uiState.value.shizukuState !is ShizukuState.Ready) {
            _uiState.update {
                it.copy(
                    userFeedbackMessage = "Shizuku is not ready. Please authorize Shizuku first.",
                    isErrorFeedback = true
                )
            }
            return
        }
        _uiState.update { it.copy(showConfirmEnableAllDialog = true) }
    }

    fun confirmEnableAll() {
        _uiState.update { it.copy(showConfirmEnableAllDialog = false, isLoading = true) }
        viewModelScope.launch {
            val targetList = _uiState.value.allServices
            val result = repository.enableAllServices(targetList)
            if (result.isSuccess) {
                refresh()
                _uiState.update {
                    it.copy(
                        userFeedbackMessage = "All accessibility services enabled.",
                        isErrorFeedback = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userFeedbackMessage = "Failed: ${result.exceptionOrNull()?.message}",
                        isErrorFeedback = true
                    )
                }
            }
        }
    }

    fun dismissEnableAllDialog() {
        _uiState.update { it.copy(showConfirmEnableAllDialog = false) }
    }

    fun requestDisableAll() {
        if (_uiState.value.shizukuState !is ShizukuState.Ready) {
            _uiState.update {
                it.copy(
                    userFeedbackMessage = "Shizuku is not ready. Please authorize Shizuku first.",
                    isErrorFeedback = true
                )
            }
            return
        }
        _uiState.update { it.copy(showConfirmDisableAllDialog = true) }
    }

    fun confirmDisableAll() {
        _uiState.update { it.copy(showConfirmDisableAllDialog = false, isLoading = true) }
        viewModelScope.launch {
            val result = repository.disableAllServices()
            if (result.isSuccess) {
                refresh()
                _uiState.update {
                    it.copy(
                        userFeedbackMessage = "All accessibility services disabled.",
                        isErrorFeedback = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        userFeedbackMessage = "Failed: ${result.exceptionOrNull()?.message}",
                        isErrorFeedback = true
                    )
                }
            }
        }
    }

    fun dismissDisableAllDialog() {
        _uiState.update { it.copy(showConfirmDisableAllDialog = false) }
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(userFeedbackMessage = null) }
    }

    private fun rebuildGroupUiModels() {
        val currentServices = _uiState.value.allServices
        val servicesByKey = currentServices.associateBy { it.componentFlattened }

        val crossRefsByGroup = rawGroupServicesList.groupBy { it.groupId }

        val uiGroups = rawGroupsList.map { groupEntity ->
            val memberKeys = crossRefsByGroup[groupEntity.id].orEmpty().map { it.componentKey }
            val members = memberKeys.mapNotNull { servicesByKey[it] }

            AppGroupUiModel(
                id = groupEntity.id,
                name = groupEntity.name,
                memberServices = members,
                isToggling = (_uiState.value.togglingGroupId == groupEntity.id)
            )
        }

        _uiState.update { it.copy(groups = uiGroups) }
    }

    private fun applyFilterAndSearch(
        list: List<AccessibilityServiceItem>,
        query: String,
        filterMode: FilterMode
    ): List<AccessibilityServiceItem> {
        val trimmedQuery = query.trim().lowercase()

        return list.filter { item ->
            val matchesFilter = when (filterMode) {
                FilterMode.ALL -> true
                FilterMode.ENABLED_ONLY -> item.isEnabled
                FilterMode.DISABLED_ONLY -> !item.isEnabled
            }

            val matchesQuery = if (trimmedQuery.isEmpty()) {
                true
            } else {
                item.appName.lowercase().contains(trimmedQuery) ||
                        item.packageName.lowercase().contains(trimmedQuery) ||
                        item.serviceLabel.lowercase().contains(trimmedQuery) ||
                        item.serviceClassName.lowercase().contains(trimmedQuery) ||
                        item.serviceSimpleName.lowercase().contains(trimmedQuery)
            }

            matchesFilter && matchesQuery
        }
    }
}

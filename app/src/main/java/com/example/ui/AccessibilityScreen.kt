package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.shizuku.ShizukuState
import com.example.ui.components.CreateOrEditGroupDialog
import com.example.ui.components.DisableAllConfirmationDialog
import com.example.ui.components.EnableAllConfirmationDialog
import com.example.ui.components.GroupItemCard
import com.example.ui.components.SearchAndFilterBar
import com.example.ui.components.ServiceItemCard
import com.example.ui.components.ShizukuStatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityScreen(
    viewModel: AccessibilityViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var groupToDeleteId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.userFeedbackMessage) {
        val message = uiState.userFeedbackMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearFeedbackMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Accessibility Manager",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "MIUI 14 Direct Shell Control",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.testTag("refresh_action_button")
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh list"
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("more_options_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More actions"
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Create New Group...") },
                                leadingIcon = {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.openCreateGroupDialog()
                                },
                                modifier = Modifier.testTag("menu_create_group")
                            )
                            DropdownMenuItem(
                                text = { Text("Enable All Services...") },
                                onClick = {
                                    showMenu = false
                                    viewModel.requestEnableAll()
                                },
                                modifier = Modifier.testTag("menu_enable_all")
                            )
                            DropdownMenuItem(
                                text = { Text("Disable All Services...") },
                                onClick = {
                                    showMenu = false
                                    viewModel.requestDisableAll()
                                },
                                modifier = Modifier.testTag("menu_disable_all")
                            )
                            DropdownMenuItem(
                                text = { Text("Shizuku & MIUI Info") },
                                onClick = {
                                    showMenu = false
                                    showHelpDialog = true
                                },
                                modifier = Modifier.testTag("menu_help_info")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (uiState.currentTab == AppTab.GROUPS) {
                FloatingActionButton(
                    onClick = { viewModel.openCreateGroupDialog() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_create_group")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Create Group")
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Shizuku Connection Status
            ShizukuStatusCard(
                state = uiState.shizukuState,
                onRequestPermission = { viewModel.requestShizukuPermission() },
                onOpenShizuku = {
                    val intent = viewModel.shizukuManager.getLaunchShizukuIntent()
                    if (intent != null) {
                        context.startActivity(intent)
                    } else {
                        try {
                            val marketIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=${viewModel.shizukuManager.SHIZUKU_PACKAGE}")
                            )
                            context.startActivity(marketIntent)
                        } catch (e: Exception) {
                            val browserIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://shizuku.rikka.app/")
                            )
                            context.startActivity(browserIntent)
                        }
                    }
                },
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Primary Tabs: Services vs Groups
            PrimaryTabRow(
                selectedTabIndex = uiState.currentTab.ordinal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = uiState.currentTab == AppTab.SERVICES,
                    onClick = { viewModel.selectTab(AppTab.SERVICES) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FormatListBulleted,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Services (${uiState.totalCount})")
                        }
                    },
                    modifier = Modifier.testTag("tab_services")
                )

                Tab(
                    selected = uiState.currentTab == AppTab.GROUPS,
                    onClick = { viewModel.selectTab(AppTab.GROUPS) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Groups (${uiState.groups.size})")
                        }
                    },
                    modifier = Modifier.testTag("tab_groups")
                )
            }

            // Tab Content
            when (uiState.currentTab) {
                AppTab.SERVICES -> {
                    ServicesTabContent(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
                AppTab.GROUPS -> {
                    GroupsTabContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onRequestDelete = { groupId -> groupToDeleteId = groupId }
                    )
                }
            }
        }
    }

    // Create / Edit Group Dialog
    if (uiState.showCreateOrEditGroupDialog) {
        CreateOrEditGroupDialog(
            editingGroup = uiState.editingGroup,
            availableServices = uiState.allServices,
            onSave = { name, keys ->
                viewModel.saveGroup(name, keys)
            },
            onDismiss = {
                viewModel.dismissGroupDialog()
            }
        )
    }

    // Delete Group Confirmation Dialog
    if (groupToDeleteId != null) {
        val targetGroupId = groupToDeleteId!!
        AlertDialog(
            onDismissRequest = { groupToDeleteId = null },
            title = { Text("Delete Group?") },
            text = { Text("Are you sure you want to delete this group? The apps themselves will not be affected.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGroup(targetGroupId)
                        groupToDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDeleteId = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation Dialogs
    if (uiState.showConfirmEnableAllDialog) {
        EnableAllConfirmationDialog(
            count = uiState.totalCount,
            onConfirm = { viewModel.confirmEnableAll() },
            onDismiss = { viewModel.dismissEnableAllDialog() }
        )
    }

    if (uiState.showConfirmDisableAllDialog) {
        DisableAllConfirmationDialog(
            onConfirm = { viewModel.confirmDisableAll() },
            onDismiss = { viewModel.dismissDisableAllDialog() }
        )
    }

    // Shizuku & MIUI Info Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("About Accessibility Manager")
            },
            text = {
                Column {
                    Text(
                        text = "Why this app exists:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "On Xiaomi MIUI 14, enabling any accessibility service through system settings displays an enforced 10-second dangerous permissions countdown screen. This app directly toggles the secure settings via Shizuku ADB, bypassing that wait entirely while safely preserving all other active services.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "New Features:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Pin commonly used apps: Tap the pin icon on any app to keep it at the top of your list.\n" +
                                "• Custom Groups: Group your accessibility services together and toggle them simultaneously with a single master switch.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Xiaomi / MIUI 14 Prerequisites:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Shizuku must be running via Wireless Debugging or ADB.\n" +
                                "• Developer Options > 'USB Debugging (Security Settings)' must be ON (required by MIUI to allow shell writes to secure settings).\n" +
                                "• Shizuku authorization must be granted to this app.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun ServicesTabContent(
    uiState: AccessibilityUiState,
    viewModel: AccessibilityViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search and Filter Bar
        SearchAndFilterBar(
            query = uiState.searchQuery,
            onQueryChanged = { viewModel.onSearchQueryChanged(it) },
            filterMode = uiState.filterMode,
            onFilterModeChanged = { viewModel.onFilterModeChanged(it) },
            totalCount = uiState.totalCount,
            enabledCount = uiState.enabledCount,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Pinned count indicator if any
        if (uiState.pinnedCount > 0 && uiState.searchQuery.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${uiState.pinnedCount} pinned app${if (uiState.pinnedCount == 1) "" else "s"} at top",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Services List or Empty State
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (uiState.filteredServices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.Accessibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotEmpty()) {
                                "No services matching '${uiState.searchQuery}'"
                            } else if (uiState.allServices.isEmpty()) {
                                "No Accessibility Services Detected"
                            } else {
                                "No services in this category"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (uiState.allServices.isEmpty()) {
                                "Make sure apps declaring an AccessibilityService are installed, then tap Refresh."
                            } else {
                                "Try adjusting your search terms or filter selection."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("services_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.filteredServices,
                        key = { it.componentFlattened }
                    ) { item ->
                        val isTogglingThis = uiState.isToggling &&
                                uiState.togglingServiceKey == item.componentFlattened

                        ServiceItemCard(
                            item = item,
                            isToggling = isTogglingThis,
                            isShizukuReady = uiState.shizukuState is ShizukuState.Ready,
                            onToggle = { targetState ->
                                viewModel.toggleService(item, targetState)
                            },
                            onTogglePin = {
                                viewModel.togglePin(item)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupsTabContent(
    uiState: AccessibilityUiState,
    viewModel: AccessibilityViewModel,
    onRequestDelete: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("groups_tab_content")
    ) {
        if (uiState.groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No App Groups Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Create groups (e.g. Work Tools, Night Mode, Battery Saver) to toggle accessibility permissions for multiple apps at once with a single tap.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.openCreateGroupDialog() },
                        modifier = Modifier.testTag("button_create_first_group")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Your First Group")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("groups_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = uiState.groups,
                    key = { it.id }
                ) { group ->
                    GroupItemCard(
                        group = group,
                        isShizukuReady = uiState.shizukuState is ShizukuState.Ready,
                        onToggleGroup = { targetState ->
                            viewModel.toggleGroup(group, targetState)
                        },
                        onToggleService = { service, targetState ->
                            viewModel.toggleService(service, targetState)
                        },
                        onEditGroup = {
                            viewModel.openEditGroupDialog(group)
                        },
                        onDeleteGroup = {
                            onRequestDelete(group.id)
                        }
                    )
                }
            }
        }
    }
}

package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.toxicbase.ui.ApiKeysScreen
import com.example.toxicbase.ui.AuthenticationAndOtpScreen
import com.example.toxicbase.ui.DashboardOverviewScreen
import com.example.toxicbase.ui.DatabaseExplorerScreen
import com.example.toxicbase.ui.LogsAndUsageScreen
import com.example.toxicbase.ui.ProjectsManagementScreen
import com.example.toxicbase.ui.SecurityRulesScreen
import com.example.toxicbase.ui.SettingsAndDeploymentScreen
import com.example.toxicbase.ui.UsersManagementScreen
import com.example.toxicbase.viewmodel.ConsoleSection
import com.example.toxicbase.viewmodel.ToxicBaseConsoleViewModel
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToxicGreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ToxicBaseAppRoot()
            }
        }
    }
}

@Composable
fun ToxicBaseAppRoot(vm: ToxicBaseConsoleViewModel = viewModel()) {
    val currentSection by vm.currentSection.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val selectedProject by vm.selectedProject.collectAsStateWithLifecycle()
    val apiKeys by vm.projectApiKeys.collectAsStateWithLifecycle()
    val users by vm.projectUsers.collectAsStateWithLifecycle()
    val documents by vm.projectDocuments.collectAsStateWithLifecycle()
    val collections by vm.projectCollections.collectAsStateWithLifecycle()
    val indexes by vm.projectIndexes.collectAsStateWithLifecycle()
    val logs by vm.projectLogs.collectAsStateWithLifecycle()
    val activeSessions by vm.activeSessionsCount.collectAsStateWithLifecycle()
    val sdkSession by vm.sdkSession.collectAsStateWithLifecycle()
    val deviceSmsInbox by vm.deviceSmsInbox.collectAsStateWithLifecycle()
    val serverOnline by vm.serverOnline.collectAsStateWithLifecycle()
    val backendUrl by vm.backendBaseUrl.collectAsStateWithLifecycle()
    val activeSdkKey by vm.activeSdkApiKey.collectAsStateWithLifecycle()
    val statusBanner by vm.statusBanner.collectAsStateWithLifecycle()
    val isBusy by vm.isBusy.collectAsStateWithLifecycle()
    val otpCooldown by vm.otpCooldownRemaining.collectAsStateWithLifecycle()
    val selectedCollection by vm.selectedCollection.collectAsStateWithLifecycle()
    val queryPage by vm.queryPageResult.collectAsStateWithLifecycle()
    val selectedDoc by vm.selectedDocumentDetail.collectAsStateWithLifecycle()
    val ruleSimResult by vm.ruleSimResult.collectAsStateWithLifecycle()

    // Request notification permission on Android 13+ for real-time SMS OTP notifications
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (currentSection != ConsoleSection.DASHBOARD) {
        BackHandler {
            vm.navigateTo(ConsoleSection.DASHBOARD)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface)
            ) {
                // Top Brand & Server Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (serverOnline) ToxicGreen else CrimsonError)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "TOXICBASE",
                                style = MaterialTheme.typography.titleLarge,
                                color = ToxicGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedProject?.name ?: "Platform"} • ${currentSection.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianCard)
                                .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                                .clickable { vm.navigateTo(ConsoleSection.PROJECTS) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = selectedProject?.id?.take(14) ?: "Projects",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyberCyan
                            )
                        }
                    }
                }

                // Scrollable 10-Module Console Strip (All 10 requested sections accessible anywhere)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ConsoleSection.entries.forEach { section ->
                        val active = currentSection == section
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (active) ToxicGreen else ObsidianCard)
                                .border(
                                    1.dp,
                                    if (active) ToxicGreen else ObsidianBorder,
                                    RoundedCornerShape(50)
                                )
                                .clickable { vm.navigateTo(section) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("nav_chip_${section.name.lowercase()}")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = iconForSection(section),
                                    contentDescription = section.title,
                                    tint = if (active) Color.Black else TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) Color.Black else Color.White,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                if (isBusy) {
                    LinearProgressIndicator(
                        color = ToxicGreen,
                        trackColor = ObsidianSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                    )
                }

                // Live Operation Banner
                AnimatedVisibility(visible = statusBanner != null) {
                    val banner = statusBanner
                    if (banner != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (banner.isError) CrimsonError.copy(alpha = 0.2f)
                                    else ToxicGreen.copy(alpha = 0.16f)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = banner.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (banner.isError) CrimsonError else ToxicGreen,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { vm.clearBanner() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss notification",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            val primaryTabs = listOf(
                ConsoleSection.DASHBOARD,
                ConsoleSection.AUTHENTICATION,
                ConsoleSection.DATABASE,
                ConsoleSection.USERS,
                ConsoleSection.SETTINGS
            )
            NavigationBar(
                containerColor = ObsidianSurface,
                tonalElevation = 8.dp
            ) {
                primaryTabs.forEach { tab ->
                    val selected = currentSection == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { vm.navigateTo(tab) },
                        icon = {
                            Icon(
                                imageVector = iconForSection(tab),
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = when (tab) {
                                    ConsoleSection.DASHBOARD -> "Console"
                                    ConsoleSection.AUTHENTICATION -> "Auth/OTP"
                                    ConsoleSection.DATABASE -> "Database"
                                    ConsoleSection.USERS -> "Users"
                                    ConsoleSection.SETTINGS -> "SDK/Deploy"
                                    else -> tab.title
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = ToxicGreen,
                            indicatorColor = ToxicGreen,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("bottom_nav_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentSection) {
                ConsoleSection.DASHBOARD -> DashboardOverviewScreen(
                    project = selectedProject,
                    projects = projects,
                    users = users,
                    documents = documents,
                    collections = collections,
                    logs = logs,
                    activeSessions = activeSessions,
                    sdkSession = sdkSession,
                    serverOnline = serverOnline,
                    backendUrl = backendUrl,
                    activeApiKey = activeSdkKey,
                    onSelectProject = { vm.selectProject(it) },
                    onNavigate = { vm.navigateTo(it) }
                )

                ConsoleSection.PROJECTS -> ProjectsManagementScreen(
                    projects = projects,
                    selectedProject = selectedProject,
                    onSelectProject = { vm.selectProject(it) },
                    onCreateProject = { name, region -> vm.createNewProject(name, region) },
                    onDeleteProject = { vm.deleteProject(it) },
                    onNavigateToKeys = { vm.navigateTo(ConsoleSection.API_KEYS) },
                    onNavigateToRules = { vm.navigateTo(ConsoleSection.SECURITY_RULES) }
                )

                ConsoleSection.AUTHENTICATION -> AuthenticationAndOtpScreen(
                    project = selectedProject,
                    sdkSession = sdkSession,
                    otpCooldownRemaining = otpCooldown,
                    deviceSmsInbox = deviceSmsInbox,
                    onSendOtp = { vm.sendPhoneOtp(it) },
                    onVerifyOtp = { phone, code -> vm.verifyPhoneOtp(phone, code) },
                    onRegisterEmail = { email, pass, phone -> vm.registerEmailUser(email, pass, phone) },
                    onLoginEmail = { email, pass -> vm.loginEmailUser(email, pass) },
                    onRefreshToken = { vm.refreshCurrentUserSession() },
                    onLogout = { vm.logoutCurrentUser() },
                    onDismissSms = { vm.dismissSmsMessage(it) },
                    onSaveAuthConfig = { p, e, exp, att, cool, rate ->
                        vm.updateProjectAuthConfig(p, e, exp, att, cool, rate)
                    }
                )

                ConsoleSection.USERS -> UsersManagementScreen(
                    users = users,
                    onUpdateUserStatus = { uid, st, em, ph -> vm.updateUserRecord(uid, st, em, ph) },
                    onDeleteUser = { vm.deleteUserRecord(it) }
                )

                ConsoleSection.DATABASE -> DatabaseExplorerScreen(
                    collections = collections,
                    selectedCollection = selectedCollection,
                    queryPage = queryPage,
                    selectedDocument = selectedDoc,
                    indexes = indexes,
                    onSelectCollection = { vm.selectCollection(it) },
                    onCreateDocument = { col, id, json -> vm.createDocumentInCollection(col, id, json) },
                    onSelectDocument = { col, id -> vm.fetchSingleDocument(col, id) },
                    onUpdateDocument = { col, id, json, merge -> vm.updateDocumentInCollection(col, id, json, merge) },
                    onDeleteDocument = { col, id -> vm.deleteDocumentFromCollection(col, id) },
                    onRunQuery = { fField, fOp, fVal, sBy, sOrd, pg, lim ->
                        vm.refreshDatabaseCollectionQuery(fField, fOp, fVal, sBy, sOrd, pg, lim)
                    },
                    onCreateIndex = { col, field, ord -> vm.createCollectionIndex(col, field, ord) },
                    onDeleteIndex = { vm.deleteCollectionIndex(it) }
                )

                ConsoleSection.API_KEYS -> ApiKeysScreen(
                    apiKeys = apiKeys,
                    activeSdkKey = activeSdkKey,
                    getRevealedKey = { vm.getRevealedKey(it) },
                    onGenerateKey = { label, role -> vm.generateApiKey(label, role) },
                    onBindSdkKey = { vm.bindSdkApiKey(it) },
                    onToggleKeyActive = { vm.toggleApiKeyStatus(it) },
                    onDeleteKey = { vm.deleteApiKey(it) }
                )

                ConsoleSection.SECURITY_RULES -> SecurityRulesScreen(
                    project = selectedProject,
                    simulationResult = ruleSimResult,
                    onSaveRules = { r, w, json -> vm.saveSecurityRules(r, w, json) },
                    onSimulateRule = { col, op, role, uid, owner ->
                        vm.simulateSecurityRule(col, op, role, uid, owner)
                    }
                )

                ConsoleSection.LOGS -> LogsAndUsageScreen(
                    mode = "LOGS",
                    project = selectedProject,
                    users = users,
                    documents = documents,
                    collections = collections,
                    logs = logs,
                    activeSessions = activeSessions,
                    onClearLogs = { vm.clearRequestLogs() }
                )

                ConsoleSection.USAGE -> LogsAndUsageScreen(
                    mode = "USAGE",
                    project = selectedProject,
                    users = users,
                    documents = documents,
                    collections = collections,
                    logs = logs,
                    activeSessions = activeSessions,
                    onClearLogs = { vm.clearRequestLogs() }
                )

                ConsoleSection.SETTINGS -> SettingsAndDeploymentScreen(
                    backendUrl = backendUrl,
                    embeddedUrl = vm.embeddedServer.getBaseUrl(),
                    activeApiKey = activeSdkKey,
                    onUpdateBackendUrl = { vm.updateBackendUrl(it) }
                )
            }
        }
    }
}

private fun iconForSection(section: ConsoleSection): ImageVector = when (section) {
    ConsoleSection.DASHBOARD -> Icons.Default.Dashboard
    ConsoleSection.PROJECTS -> Icons.Default.FolderSpecial
    ConsoleSection.AUTHENTICATION -> Icons.Default.Lock
    ConsoleSection.USERS -> Icons.Default.People
    ConsoleSection.DATABASE -> Icons.Default.Storage
    ConsoleSection.API_KEYS -> Icons.Default.Key
    ConsoleSection.SECURITY_RULES -> Icons.Default.Security
    ConsoleSection.LOGS -> Icons.Default.Dns
    ConsoleSection.USAGE -> Icons.Default.Analytics
    ConsoleSection.SETTINGS -> Icons.Default.Code
}

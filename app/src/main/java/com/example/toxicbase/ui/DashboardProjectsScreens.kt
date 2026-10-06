package com.example.toxicbase.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.toxicbase.data.DocumentEntity
import com.example.toxicbase.data.ProjectEntity
import com.example.toxicbase.data.RequestLogEntity
import com.example.toxicbase.data.UserEntity
import com.example.toxicbase.sdk.ToxicSession
import com.example.toxicbase.viewmodel.ConsoleSection
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToxicGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardOverviewScreen(
    project: ProjectEntity?,
    projects: List<ProjectEntity>,
    users: List<UserEntity>,
    documents: List<DocumentEntity>,
    collections: List<String>,
    logs: List<RequestLogEntity>,
    activeSessions: Int,
    sdkSession: ToxicSession?,
    serverOnline: Boolean,
    backendUrl: String,
    activeApiKey: String,
    onSelectProject: (String) -> Unit,
    onNavigate: (ConsoleSection) -> Unit
) {
    val now = System.currentTimeMillis()
    val dayAgo = now - 24L * 3600L * 1000L
    val newUsers24h = users.count { it.createdAt >= dayAgo }
    val activeUsersCount = users.count { it.status == "ACTIVE" && it.lastLoginAt != null }
    val totalStorageBytes = documents.sumOf { it.sizeBytes }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Hero Infrastructure Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(20.dp))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_toxicbase_hero_1791250300269),
                        contentDescription = stringResource(R.string.hero_banner_desc),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x99070A10),
                                        Color(0xEE070A10)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (serverOnline) ToxicGreen.copy(alpha = 0.16f) else CrimsonError.copy(alpha = 0.2f))
                                    .border(
                                        1.dp,
                                        if (serverOnline) ToxicGreen else CrimsonError,
                                        RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (serverOnline) ToxicGreen else CrimsonError)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (serverOnline) "BAAS ENGINE ONLINE" else "SERVER OFFLINE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (serverOnline) ToxicGreen else CrimsonError
                                )
                            }

                            Text(
                                text = project?.region?.uppercase() ?: "US-EAST-1",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyberCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = project?.name ?: "ToxicBase Platform",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White
                        )
                        Text(
                            text = "Project ID: ${project?.id ?: "Initializing..."} • Endpoint: $backendUrl",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                label = "SDK Key: ${activeApiKey.take(14)}...",
                                color = ToxicGreen
                            )
                            StatusPill(
                                label = if (sdkSession != null) "Auth: ${sdkSession.user.id.take(12)}" else "Auth: Signed Out",
                                color = if (sdkSession != null) CyberCyan else AmberWarn
                            )
                        }
                    }
                }
            }
        }

        // Quick Project Switcher Row
        if (projects.size > 1) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    projects.forEach { p ->
                        val selected = p.id == project?.id
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectProject(p.id) },
                            label = { Text("${p.name} (${p.id.takeLast(6)})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ToxicGreen.copy(alpha = 0.2f),
                                selectedLabelColor = ToxicGreen
                            )
                        )
                    }
                }
            }
        }

        // Core Platform Metrics Grid
        item {
            Text(
                text = "REAL-TIME TELEMETRY & METRICS",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "Total Users",
                        value = users.size.toString(),
                        subtitle = "+$newUsers24h in last 24h",
                        icon = Icons.Default.People,
                        accent = ToxicGreen,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(ConsoleSection.USERS) }
                    )
                    MetricStatCard(
                        title = "Active Users",
                        value = activeUsersCount.toString(),
                        subtitle = "$activeSessions active JWT sessions",
                        icon = Icons.Default.PhoneAndroid,
                        accent = CyberCyan,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(ConsoleSection.AUTHENTICATION) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "NoSQL Documents",
                        value = documents.size.toString(),
                        subtitle = "${collections.size} collections • ${formatBytes(totalStorageBytes)}",
                        icon = Icons.Default.Storage,
                        accent = AmberWarn,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(ConsoleSection.DATABASE) }
                    )
                    MetricStatCard(
                        title = "REST API Calls",
                        value = logs.size.toString(),
                        subtitle = "Avg ${ if (logs.isNotEmpty()) logs.map { it.latencyMs }.average().toInt() else 1 } ms latency",
                        icon = Icons.Default.Dns,
                        accent = ToxicGreen,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(ConsoleSection.LOGS) }
                    )
                }
            }
        }

        // Quick Launch Modules
        item {
            Text(
                text = "CORE BAAS MODULES & SDK WORKBENCH",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickModuleRow(
                    title = "Phone SMS OTP & JWT Authentication",
                    desc = "Test real SMS OTP dispatch, cooldowns, attempt limits, and Email/Password login via ToxicBase.auth",
                    icon = Icons.Default.Lock,
                    accent = ToxicGreen,
                    tag = "quick_nav_auth",
                    onClick = { onNavigate(ConsoleSection.AUTHENTICATION) }
                )
                QuickModuleRow(
                    title = "NoSQL Document Database Explorer",
                    desc = "Create collections, CRUD JSON documents, filter queries, pagination & indexes via ToxicBase.database",
                    icon = Icons.Default.Storage,
                    accent = CyberCyan,
                    tag = "quick_nav_database",
                    onClick = { onNavigate(ConsoleSection.DATABASE) }
                )
                QuickModuleRow(
                    title = "Project API Keys & Security Rules Engine",
                    desc = "Provision client/admin keys, configure collection access rules, and run live rule simulations",
                    icon = Icons.Default.Security,
                    accent = AmberWarn,
                    tag = "quick_nav_rules",
                    onClick = { onNavigate(ConsoleSection.SECURITY_RULES) }
                )
                QuickModuleRow(
                    title = "Android SDK & Cloud Deployment Guide",
                    desc = "Copy ToxicBase Android SDK code, configure remote Node.js + PostgreSQL + Redis + Twilio backend",
                    icon = Icons.Default.Code,
                    accent = ToxicGreen,
                    tag = "quick_nav_settings",
                    onClick = { onNavigate(ConsoleSection.SETTINGS) }
                )
            }
        }

        // Recent Live HTTP Request Audit Stream
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE REST API AUDIT STREAM",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Text(
                    text = "View All ->",
                    style = MaterialTheme.typography.labelMedium,
                    color = ToxicGreen,
                    modifier = Modifier.clickable { onNavigate(ConsoleSection.LOGS) }
                )
            }
        }

        items(logs.take(5)) { log ->
            CompactLogRow(log)
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

@Composable
fun ProjectsManagementScreen(
    projects: List<ProjectEntity>,
    selectedProject: ProjectEntity?,
    onSelectProject: (String) -> Unit,
    onCreateProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onNavigateToKeys: () -> Unit,
    onNavigateToRules: () -> Unit
) {
    var newProjectName by remember { mutableStateOf("") }
    var selectedRegion by remember { mutableStateOf("us-east-1") }
    val regions = listOf("us-east-1", "us-west-2", "eu-central-1", "ap-south-1")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = ToxicGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CREATE ISOLATED BAAS PROJECT",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Each project provisions an isolated namespace for Users, NoSQL Collections, Indexes, Security Rules, and HMAC-SHA256 API Keys.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("Project Name (e.g. FinTech-Mobile-Prod)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_project_name_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Cluster Region",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        regions.forEach { reg ->
                            FilterChip(
                                selected = selectedRegion == reg,
                                onClick = { selectedRegion = reg },
                                label = { Text(reg) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (newProjectName.isNotBlank()) {
                                onCreateProject(newProjectName, selectedRegion)
                                newProjectName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ToxicGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_project_submit_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Provision Project & Generate Keys", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "PROVISIONED PROJECTS (${projects.size})",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
        }

        items(projects, key = { it.id }) { proj ->
            val isCurrent = proj.id == selectedProject?.id
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) ObsidianCard.copy(alpha = 0.95f) else ObsidianSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isCurrent) 1.5.dp else 1.dp,
                        color = if (isCurrent) ToxicGreen else ObsidianBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = proj.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                if (isCurrent) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    StatusPill(label = "ACTIVE", color = ToxicGreen)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ID: ${proj.id} • Region: ${proj.region}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberCyan
                            )
                            Text(
                                text = "Created: ${formatTimestamp(proj.createdAt)} • Rate Limit: ${proj.rateLimitPerMinute}/min",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }

                        if (projects.size > 1) {
                            IconButton(
                                onClick = { onDeleteProject(proj.id) },
                                modifier = Modifier.testTag("delete_project_${proj.id}")
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete project",
                                    tint = CrimsonError
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isCurrent) {
                            Button(
                                onClick = { onSelectProject(proj.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Switch Active", color = Color.Black)
                            }
                        }
                        Button(
                            onClick = {
                                onSelectProject(proj.id)
                                onNavigateToKeys()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = ToxicGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("API Keys", color = Color.White)
                        }
                        Button(
                            onClick = {
                                onSelectProject(proj.id)
                                onNavigateToRules()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = AmberWarn, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rules", color = Color.White)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

@Composable
fun MetricStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        modifier = modifier.border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun QuickModuleRow(
    title: String,
    desc: String,
    icon: ImageVector,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f))
            ) {
                Icon(icon, contentDescription = title, tint = accent)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun CompactLogRow(log: RequestLogEntity) {
    val statusColor = when {
        log.statusCode in 200..299 -> ToxicGreen
        log.statusCode in 400..499 -> AmberWarn
        else -> CrimsonError
    }
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                StatusPill(label = "${log.method} ${log.statusCode}", color = statusColor)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = log.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (log.detail.isNotBlank()) {
                        Text(
                            text = log.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Text(
                text = "${log.latencyMs}ms",
                style = MaterialTheme.typography.labelMedium,
                color = CyberCyan
            )
        }
    }
}

@Composable
fun StatusPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

fun formatTimestamp(ts: Long?): String {
    if (ts == null || ts <= 0L) return "Never"
    val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US)
    return sdf.format(Date(ts))
}

fun formatBytes(bytes: Int): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    return String.format(Locale.US, "%.1f KB", kb)
}

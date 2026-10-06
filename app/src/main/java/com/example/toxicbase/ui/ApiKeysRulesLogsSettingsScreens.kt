package com.example.toxicbase.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.toxicbase.data.ApiKeyEntity
import com.example.toxicbase.data.DocumentEntity
import com.example.toxicbase.data.ProjectEntity
import com.example.toxicbase.data.RequestLogEntity
import com.example.toxicbase.data.UserEntity
import com.example.toxicbase.viewmodel.RuleSimulationResult
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToxicGreen

@Composable
fun ApiKeysScreen(
    apiKeys: List<ApiKeyEntity>,
    activeSdkKey: String,
    getRevealedKey: (String) -> String?,
    onGenerateKey: (String, String) -> Unit,
    onBindSdkKey: (String) -> Unit,
    onToggleKeyActive: (ApiKeyEntity) -> Unit,
    onDeleteKey: (ApiKeyEntity) -> Unit
) {
    val context = LocalContext.current
    var keyLabel by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("client") }
    val roles = listOf("client", "server_admin", "readonly")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        Icon(Icons.Default.Key, contentDescription = null, tint = ToxicGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PROVISION PROJECT API KEY",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "API keys are hashed with HMAC-SHA256 in the database. Use 'client' for Android apps, 'readonly' for analytics, and 'server_admin' for backend microservices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = keyLabel,
                        onValueChange = { keyLabel = it },
                        label = { Text("Key Label (e.g. Production Android App)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_label_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        roles.forEach { r ->
                            FilterChip(
                                selected = selectedRole == r,
                                onClick = { selectedRole = r },
                                label = { Text(r) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onGenerateKey(keyLabel.ifBlank { "Android SDK Key" }, selectedRole)
                            keyLabel = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ToxicGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_api_key_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate New API Key", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(apiKeys, key = { it.id }) { key ->
            val revealedRaw = getRevealedKey(key.id)
            val isBound = revealedRaw != null && revealedRaw == activeSdkKey
            val roleColor = when (key.role) {
                "server_admin" -> AmberWarn
                "readonly" -> CyberCyan
                else -> ToxicGreen
            }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isBound) ToxicGreen else ObsidianBorder,
                        RoundedCornerShape(14.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = key.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = revealedRaw ?: key.keyPrefix,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (key.isActive) ToxicGreen else CrimsonError,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusPill(label = key.role.uppercase(), color = roleColor)
                            StatusPill(
                                label = if (key.isActive) "ACTIVE" else "REVOKED",
                                color = if (key.isActive) ToxicGreen else CrimsonError
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "SHA256 Fingerprint: ${key.keyHash.take(24)}... • Last used: ${formatTimestamp(key.lastUsedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (revealedRaw != null && key.isActive) {
                            Button(
                                onClick = { onBindSdkKey(revealedRaw) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBound) ToxicGreen.copy(alpha = 0.25f) else CyberCyan
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (isBound) "Bound to SDK" else "Bind to SDK",
                                    color = if (isBound) ToxicGreen else Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("ToxicBase API Key", revealedRaw))
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy API key", tint = Color.White)
                            }
                        }
                        OutlinedButton(
                            onClick = { onToggleKeyActive(key) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (key.isActive) "Revoke" else "Activate")
                        }
                        IconButton(onClick = { onDeleteKey(key) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete API key", tint = CrimsonError)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

@Composable
fun SecurityRulesScreen(
    project: ProjectEntity?,
    simulationResult: RuleSimulationResult?,
    onSaveRules: (String, String, String) -> Unit,
    onSimulateRule: (String, String, String, String?, String?) -> Unit
) {
    val ruleModes = listOf("public", "authenticated", "owner_only", "admin_only")
    var defaultRead by remember(project?.id, project?.defaultReadRule) {
        mutableStateOf(project?.defaultReadRule ?: "authenticated")
    }
    var defaultWrite by remember(project?.id, project?.defaultWriteRule) {
        mutableStateOf(project?.defaultWriteRule ?: "authenticated")
    }
    var collectionRulesText by remember(project?.id, project?.collectionRulesJson) {
        mutableStateOf(project?.collectionRulesJson ?: "{}")
    }

    // Simulator inputs
    var simCollection by remember { mutableStateOf("orders") }
    var simOperation by remember { mutableStateOf("write") }
    var simRole by remember { mutableStateOf("client") }
    var simAuthUserId by remember { mutableStateOf("tb_usr_demo01") }
    var simOwnerUserId by remember { mutableStateOf("tb_usr_demo01") }

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
                        Icon(Icons.Default.Security, contentDescription = null, tint = AmberWarn)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DATABASE ACCESS & SECURITY RULES ENGINE",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Default Read Rule:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ruleModes.forEach { mode ->
                            FilterChip(
                                selected = defaultRead == mode,
                                onClick = { defaultRead = mode },
                                label = { Text(mode) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Default Write Rule:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ruleModes.forEach { mode ->
                            FilterChip(
                                selected = defaultWrite == mode,
                                onClick = { defaultWrite = mode },
                                label = { Text(mode) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = collectionRulesText,
                        onValueChange = { collectionRulesText = it },
                        label = { Text("Per-Collection Rules JSON Override") },
                        minLines = 4,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("collection_rules_json_input")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onSaveRules(defaultRead, defaultWrite, collectionRulesText) },
                        colors = ButtonDefaults.buttonColors(containerColor = ToxicGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("deploy_rules_button")
                    ) {
                        Text("Deploy Security Rules", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Interactive Security Rule Simulator
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCyan, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LIVE SECURITY RULES SIMULATOR",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyberCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = simCollection,
                            onValueChange = { simCollection = it },
                            label = { Text("Collection") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = simOperation == "read",
                            onClick = { simOperation = "read" },
                            label = { Text("READ") }
                        )
                        FilterChip(
                            selected = simOperation == "write",
                            onClick = { simOperation = "write" },
                            label = { Text("WRITE") }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("client", "readonly", "server_admin").forEach { r ->
                            FilterChip(
                                selected = simRole == r,
                                onClick = { simRole = r },
                                label = { Text("Key: $r") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = simAuthUserId,
                            onValueChange = { simAuthUserId = it },
                            label = { Text("JWT User ID (blank = anon)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = simOwnerUserId,
                            onValueChange = { simOwnerUserId = it },
                            label = { Text("Doc Owner User ID") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onSimulateRule(
                                simCollection,
                                simOperation,
                                simRole,
                                simAuthUserId.takeIf { it.isNotBlank() },
                                simOwnerUserId.takeIf { it.isNotBlank() }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_rule_simulator_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Evaluate Access Rule", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    if (simulationResult != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (simulationResult.allowed) ToxicGreen.copy(alpha = 0.15f)
                                    else CrimsonError.copy(alpha = 0.15f)
                                )
                                .border(
                                    1.dp,
                                    if (simulationResult.allowed) ToxicGreen else CrimsonError,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = simulationResult.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (simulationResult.allowed) ToxicGreen else CrimsonError
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

@Composable
fun LogsAndUsageScreen(
    mode: String, // "LOGS" or "USAGE"
    project: ProjectEntity?,
    users: List<UserEntity>,
    documents: List<DocumentEntity>,
    collections: List<String>,
    logs: List<RequestLogEntity>,
    activeSessions: Int,
    onClearLogs: () -> Unit
) {
    if (mode == "LOGS") {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "HTTP REST REQUEST AUDIT LOGS (${logs.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = "Every API call to /auth/*, /database/*, /users/*, /projects/* is recorded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    OutlinedButton(onClick = onClearLogs) {
                        Text("Clear")
                    }
                }
            }

            items(logs, key = { it.id }) { log ->
                CompactLogRow(log)
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    } else {
        val totalBytes = documents.sumOf { it.sizeBytes }
        val storageQuotaBytes = 50 * 1024 * 1024 // 50 MB quota visualization
        val authCalls = logs.count { it.path.startsWith("/auth") }
        val dbCalls = logs.count { it.path.startsWith("/database") }
        val userCalls = logs.count { it.path.startsWith("/users") }
        val rateLimitedCalls = logs.count { it.statusCode == 429 }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        Text(
                            text = "PROJECT RESOURCE USAGE & QUOTAS",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        UsageProgressRow("NoSQL Document Storage", formatBytes(totalBytes), "50 MB", (totalBytes.toFloat() / storageQuotaBytes).coerceIn(0.02f, 1f), ToxicGreen)
                        Spacer(modifier = Modifier.height(10.dp))
                        UsageProgressRow("Provisioned Users", users.size.toString(), "100,000", (users.size / 100f).coerceIn(0.02f, 1f), CyberCyan)
                        Spacer(modifier = Modifier.height(10.dp))
                        UsageProgressRow("Rate Limit Window", "${logs.take(20).size} req/min", "${project?.rateLimitPerMinute ?: 120} req/min", 0.15f, AmberWarn)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "Auth & OTP Calls",
                        value = authCalls.toString(),
                        subtitle = "$activeSessions active sessions",
                        icon = Icons.Default.Security,
                        accent = ToxicGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Database Ops",
                        value = dbCalls.toString(),
                        subtitle = "${collections.size} collections",
                        icon = Icons.Default.Storage,
                        accent = CyberCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = "User API Calls",
                        value = userCalls.toString(),
                        subtitle = "GET/PUT /users",
                        icon = Icons.Default.Dns,
                        accent = AmberWarn,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "429 Rate Blocks",
                        value = rateLimitedCalls.toString(),
                        subtitle = "Abuse protection",
                        icon = Icons.Default.CheckCircle,
                        accent = CrimsonError,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun UsageProgressRow(
    label: String,
    current: String,
    limit: String,
    progress: Float,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text("$current / $limit", style = MaterialTheme.typography.bodySmall, color = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = ObsidianSurface,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
fun SettingsAndDeploymentScreen(
    backendUrl: String,
    embeddedUrl: String,
    activeApiKey: String,
    onUpdateBackendUrl: (String) -> Unit
) {
    val context = LocalContext.current
    var urlInput by remember(backendUrl) { mutableStateOf(backendUrl) }

    val sdkSnippet = remember(activeApiKey, backendUrl) {
        """
        // 1. Initialize TOXICBASE Android SDK
        ToxicBase.initialize(
            apiKey = "$activeApiKey",
            baseUrl = "$backendUrl"
        )

        // 2. Real Phone SMS OTP Authentication
        ToxicBase.auth.sendOTP("+14155552671")
        val session = ToxicBase.auth.verifyOTP("+14155552671", "654321")
        val user = ToxicBase.auth.currentUser()

        // 3. NoSQL Document Database CRUD
        val usersCol = ToxicBase.database.collection("users")
        usersCol.create(mapOf("name" to "Ada Lovelace", "role" to "admin"), "doc_ada")
        val doc = ToxicBase.database.get("users", "doc_ada")
        ToxicBase.database.update("users", "doc_ada", mapOf("verified" to true))
        """.trimIndent()
    }

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
                    Text(
                        text = "BAAS BACKEND ENDPOINT CONFIGURATION",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Connect the ToxicBase Android SDK to the on-device Embedded HTTP BaaS Server ($embeddedUrl) or to your external Node.js + Express + PostgreSQL + Redis deployment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("ToxicBase Server Base URL") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_backend_url_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onUpdateBackendUrl(urlInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = ToxicGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Apply Server URL", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                urlInput = embeddedUrl
                                onUpdateBackendUrl(embeddedUrl)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Use Embedded Server")
                        }
                    }
                }
            }
        }

        // Android SDK Integration Snippet Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCyan, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOXICBASE ANDROID SDK QUICKSTART",
                            style = MaterialTheme.typography.titleMedium,
                            color = CyberCyan
                        )
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("ToxicBase SDK", sdkSnippet))
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy SDK code", tint = CyberCyan)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF05080D))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = sdkSnippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = ToxicGreen
                        )
                    }
                }
            }
        }

        // Complete Production Deployment Checklist
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "PRODUCTION INFRASTRUCTURE & DEPLOYMENT GUIDE",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    DeploymentStepItem(
                        step = "1. Node.js + Express Backend (/backend/src/server.js)",
                        detail = "Run `cd backend && npm install && npm start`. Exposes /auth/*, /users/*, /projects/*, and /database/* with Helmet, CORS, and Express Rate Limit."
                    )
                    DeploymentStepItem(
                        step = "2. PostgreSQL 16 + JSONB GIN Indexes (/backend/sql/schema.sql)",
                        detail = "Run `psql \$DATABASE_URL -f backend/sql/schema.sql` to create isolated projects, api_keys, users, sessions, documents (with GIN index), and request_logs tables."
                    )
                    DeploymentStepItem(
                        step = "3. Redis 7 OTP & Rate-Limit Store",
                        detail = "Stores HMAC-SHA256 OTP challenges (`otp:record:{projectId}:{phone}`) with 300s EXPIRE and 60s resend cooldown (`otp:cooldown:{projectId}:{phone}`)."
                    )
                    DeploymentStepItem(
                        step = "4. Twilio Programmable SMS Setup",
                        detail = "Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, and TWILIO_FROM_NUMBER in server environment or AI Studio Secrets panel. Credentials never leave the server."
                    )
                    DeploymentStepItem(
                        step = "5. Next.js Admin Dashboard (/dashboard) & Nginx HTTPS",
                        detail = "Deploy Next.js dashboard on port 3000 and terminate TLS 1.3 via Nginx + Let's Encrypt Certbot (`certbot --nginx -d api.toxicbase.yourdomain.com`)."
                    )
                    DeploymentStepItem(
                        step = "6. Automated PostgreSQL Backups",
                        detail = "Schedule daily `pg_dump \"\$DATABASE_URL\" -Fc -f /var/backups/toxicbase/tb_\$(date +%F).dump` cron job with 14-day retention."
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

@Composable
private fun DeploymentStepItem(step: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianSurface)
            .padding(12.dp)
    ) {
        Text(step, style = MaterialTheme.typography.titleMedium, color = ToxicGreen)
        Spacer(modifier = Modifier.height(4.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

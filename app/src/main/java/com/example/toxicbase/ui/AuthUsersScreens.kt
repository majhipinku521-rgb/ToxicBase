package com.example.toxicbase.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.toxicbase.data.ProjectEntity
import com.example.toxicbase.data.UserEntity
import com.example.toxicbase.sdk.ToxicSession
import com.example.toxicbase.server.IncomingDeviceSmsMessage
import com.example.toxicbase.server.SmsProviderGateway
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
fun AuthenticationAndOtpScreen(
    project: ProjectEntity?,
    sdkSession: ToxicSession?,
    otpCooldownRemaining: Int,
    deviceSmsInbox: List<IncomingDeviceSmsMessage>,
    onSendOtp: (String) -> Unit,
    onVerifyOtp: (String, String) -> Unit,
    onRegisterEmail: (String, String, String) -> Unit,
    onLoginEmail: (String, String) -> Unit,
    onRefreshToken: () -> Unit,
    onLogout: () -> Unit,
    onDismissSms: (String) -> Unit,
    onSaveAuthConfig: (Boolean, Boolean, Int, Int, Int, Int) -> Unit
) {
    var authTab by remember { mutableIntStateOf(0) } // 0 = Phone OTP, 1 = Email/Password, 2 = Policy Config
    var phoneInput by remember { mutableStateOf("+14155552671") }
    var otpInput by remember { mutableStateOf("") }

    var emailInput by remember { mutableStateOf("developer@toxicbase.io") }
    var passwordInput by remember { mutableStateOf("ToxicPass#2026") }
    var emailPhoneOptional by remember { mutableStateOf("+14155559900") }

    // Policy Config state synchronized with project
    var phoneAuthEnabled by remember(project?.id, project?.authPhoneEnabled) {
        mutableStateOf(project?.authPhoneEnabled ?: true)
    }
    var emailAuthEnabled by remember(project?.id, project?.authEmailEnabled) {
        mutableStateOf(project?.authEmailEnabled ?: true)
    }
    var otpExpiryText by remember(project?.id, project?.otpExpirySeconds) {
        mutableStateOf((project?.otpExpirySeconds ?: 300).toString())
    }
    var otpAttemptsText by remember(project?.id, project?.otpMaxAttempts) {
        mutableStateOf((project?.otpMaxAttempts ?: 5).toString())
    }
    var otpCooldownText by remember(project?.id, project?.otpCooldownSeconds) {
        mutableStateOf((project?.otpCooldownSeconds ?: 60).toString())
    }
    var rateLimitText by remember(project?.id, project?.rateLimitPerMinute) {
        mutableStateOf((project?.rateLimitPerMinute ?: 120).toString())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Incoming Device SMS Receiver Banner (Simulates physical phone SMS tray when OTP arrives)
            val latestSms = deviceSmsInbox.firstOrNull()
            AnimatedVisibility(visible = latestSms != null) {
                if (latestSms != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A2E1D)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, ToxicGreen, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Message, contentDescription = null, tint = ToxicGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "INCOMING DEVICE SMS • ${latestSms.providerUsed}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ToxicGreen
                                        )
                                        Text(
                                            text = "From: ${latestSms.senderNumber} -> To: ${latestSms.recipientPhone}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onDismissSms(latestSms.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss SMS", tint = TextSecondary)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = latestSms.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    phoneInput = latestSms.recipientPhone
                                    otpInput = latestSms.otpCode
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ToxicGreen),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("autofill_received_otp_button")
                            ) {
                                Text(
                                    text = "Auto-Fill Code (${latestSms.otpCode}) into Verification Box",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mode Selector Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = authTab == 0,
                    onClick = { authTab = 0 },
                    label = { Text("Phone SMS OTP") },
                    leadingIcon = {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("auth_tab_phone_otp")
                )
                FilterChip(
                    selected = authTab == 1,
                    onClick = { authTab = 1 },
                    label = { Text("Email & Password") },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("auth_tab_email")
                )
                FilterChip(
                    selected = authTab == 2,
                    onClick = { authTab = 2 },
                    label = { Text("Security Policy") },
                    leadingIcon = {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("auth_tab_policy")
                )
            }
        }

        // Tab 0: Real Phone SMS OTP Authentication
        if (authTab == 0) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "1. DISPATCH REAL SMS OTP",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            StatusPill(
                                label = if (SmsProviderGateway.isTwilioConfigured()) "TWILIO LIVE" else "DEVICE MODEM SMS",
                                color = ToxicGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Calls ToxicBase.auth.sendOTP(phoneNumber) -> POST /auth/send-otp. Generates a cryptographic 6-digit code, hashes it with HMAC-SHA256 + salt, enforces cooldown & rate limits, and never exposes the code in the HTTP response.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text("Phone Number (E.164 format, e.g. +14155552671)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("otp_phone_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onSendOtp(phoneInput) },
                            enabled = otpCooldownRemaining == 0,
                            colors = ButtonDefaults.buttonColors(containerColor = ToxicGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("send_otp_button")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (otpCooldownRemaining > 0) {
                                    "Resend Cooldown Active (${otpCooldownRemaining}s)"
                                } else {
                                    "ToxicBase.auth.sendOTP()"
                                },
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "2. VERIFY OTP & ISSUE JWT SESSION",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Calls ToxicBase.auth.verifyOTP(phoneNumber, otp) -> POST /auth/verify-otp. Verifies constant-time HMAC-SHA256 digest (max ${project?.otpMaxAttempts ?: 5} attempts, ${project?.otpExpirySeconds ?: 300}s TTL).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = otpInput,
                            onValueChange = { if (it.length <= 6) otpInput = it },
                            label = { Text("6-Digit SMS Verification Code") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("otp_code_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onVerifyOtp(phoneInput, otpInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("verify_otp_button")
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ToxicBase.auth.verifyOTP()",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Tab 1: Email & Password Authentication
        if (authTab == 1) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "EMAIL & PASSWORD AUTHENTICATION",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Passwords are salted & hashed using PBKDF2WithHmacSHA256 (65,536 iterations, 256-bit key) on the server before storage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email Address") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password (min 6 chars)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = emailPhoneOptional,
                            onValueChange = { emailPhoneOptional = it },
                            label = { Text("Phone Number (Optional E.164 for registration)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onRegisterEmail(emailInput, passwordInput, emailPhoneOptional) },
                                colors = ButtonDefaults.buttonColors(containerColor = ToxicGreen),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_register_button")
                            ) {
                                Text("Register User", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onLoginEmail(emailInput, passwordInput) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_login_button")
                            ) {
                                Text("Login User", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Tab 2: Project Auth & OTP Security Policy Config
        if (authTab == 2) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "PROJECT AUTH & OTP SECURITY POLICY",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Phone SMS OTP Authentication", color = Color.White)
                                Text("Allow /auth/send-otp & /auth/verify-otp", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Switch(
                                checked = phoneAuthEnabled,
                                onCheckedChange = { phoneAuthEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = ToxicGreen)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Email & Password Authentication", color = Color.White)
                                Text("Allow /auth/login & registration", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            Switch(
                                checked = emailAuthEnabled,
                                onCheckedChange = { emailAuthEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = ToxicGreen)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = otpExpiryText,
                                onValueChange = { otpExpiryText = it },
                                label = { Text("OTP Expiry (sec)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = otpAttemptsText,
                                onValueChange = { otpAttemptsText = it },
                                label = { Text("Max Attempts") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = otpCooldownText,
                                onValueChange = { otpCooldownText = it },
                                label = { Text("Resend Cooldown (s)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = rateLimitText,
                                onValueChange = { rateLimitText = it },
                                label = { Text("Rate Limit / min") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                onSaveAuthConfig(
                                    phoneAuthEnabled,
                                    emailAuthEnabled,
                                    otpExpiryText.toIntOrNull() ?: 300,
                                    otpAttemptsText.toIntOrNull() ?: 5,
                                    otpCooldownText.toIntOrNull() ?: 60,
                                    rateLimitText.toIntOrNull() ?: 120
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ToxicGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Authentication Policy", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active Session & JWT Token Inspector Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (sdkSession != null) ToxicGreen else ObsidianBorder,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = null,
                                tint = if (sdkSession != null) ToxicGreen else TextMuted
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ToxicBase.auth.currentUser() SESSION",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                        StatusPill(
                            label = if (sdkSession != null) "AUTHENTICATED" else "NO SESSION",
                            color = if (sdkSession != null) ToxicGreen else AmberWarn
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    if (sdkSession == null) {
                        Text(
                            text = "No active user session. Authenticate above via Phone SMS OTP or Email/Password to inspect live HS256 JWT Access & Refresh tokens.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    } else {
                        val u = sdkSession.user
                        Text("User ID: ${u.id}", style = MaterialTheme.typography.bodySmall, color = ToxicGreen)
                        Text("Project ID: ${u.projectId}", style = MaterialTheme.typography.bodySmall, color = CyberCyan)
                        Text("Phone: ${u.phoneNumber ?: "—"} | Email: ${u.email ?: "—"}", style = MaterialTheme.typography.bodySmall, color = Color.White)
                        Text("Status: ${u.status} | Expires In: ${sdkSession.expiresInSeconds}s", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "JWT Access Token (HS256):",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = sdkSession.accessToken,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Refresh Token (Hashed in DB):",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = sdkSession.refreshToken,
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberWarn,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRefreshToken,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rotate Token")
                            }
                            Button(
                                onClick = onLogout,
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auth_logout_button")
                            ) {
                                Icon(Icons.Default.Logout, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Logout", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

@Composable
fun UsersManagementScreen(
    users: List<UserEntity>,
    onUpdateUserStatus: (String, String, String?, String?) -> Unit,
    onDeleteUser: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val now = System.currentTimeMillis()
    val dayAgo = now - 24L * 3600L * 1000L
    val newUsersCount = users.count { it.createdAt >= dayAgo }
    val activeUsersCount = users.count { it.status == "ACTIVE" }

    val filteredUsers = remember(users, searchQuery) {
        if (searchQuery.isBlank()) users
        else users.filter {
            it.id.contains(searchQuery, ignoreCase = true) ||
                (it.phoneNumber?.contains(searchQuery, ignoreCase = true) == true) ||
                (it.email?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricStatCard(
                    title = "Total Users",
                    value = users.size.toString(),
                    subtitle = "Project isolated",
                    icon = Icons.Default.Person,
                    accent = ToxicGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "New (24h)",
                    value = newUsersCount.toString(),
                    subtitle = "$activeUsersCount active",
                    icon = Icons.Default.CheckCircle,
                    accent = CyberCyan,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search users by User ID, Phone, or Email...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("users_search_input")
            )
        }

        if (filteredUsers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Users Found", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            "Authenticate with Phone SMS OTP or Email/Password in the Auth tab to create real users.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(filteredUsers, key = { it.id }) { user ->
                val isActive = user.status == "ACTIVE"
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = user.id,
                                style = MaterialTheme.typography.bodySmall,
                                color = CyberCyan,
                                fontWeight = FontWeight.Bold
                            )
                            StatusPill(
                                label = user.status,
                                color = if (isActive) ToxicGreen else CrimsonError
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Phone: ${user.phoneNumber ?: "Not linked"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "Email: ${user.email ?: "Not linked"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Created: ${formatTimestamp(user.createdAt)} • Last Login: ${formatTimestamp(user.lastLoginAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val nextStatus = if (isActive) "SUSPENDED" else "ACTIVE"
                                    onUpdateUserStatus(user.id, nextStatus, user.email, user.phoneNumber)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (isActive) Icons.Default.Block else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isActive) AmberWarn else ToxicGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isActive) "Suspend User" else "Activate User")
                            }
                            IconButton(onClick = { onDeleteUser(user.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete user", tint = CrimsonError)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

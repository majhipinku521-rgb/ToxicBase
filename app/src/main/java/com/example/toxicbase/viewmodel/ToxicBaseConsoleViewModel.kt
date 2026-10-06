package com.example.toxicbase.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.toxicbase.data.ApiKeyEntity
import com.example.toxicbase.data.CollectionIndexEntity
import com.example.toxicbase.data.DocumentEntity
import com.example.toxicbase.data.ProjectEntity
import com.example.toxicbase.data.RequestLogEntity
import com.example.toxicbase.data.ToxicBaseDao
import com.example.toxicbase.data.ToxicBaseDatabase
import com.example.toxicbase.data.UserEntity
import com.example.toxicbase.sdk.ToxicBase
import com.example.toxicbase.sdk.ToxicDocument
import com.example.toxicbase.sdk.ToxicQueryPage
import com.example.toxicbase.sdk.ToxicResult
import com.example.toxicbase.sdk.ToxicSession
import com.example.toxicbase.server.IncomingDeviceSmsMessage
import com.example.toxicbase.server.JwtClaims
import com.example.toxicbase.server.SmsProviderGateway
import com.example.toxicbase.server.ToxicBaseEmbeddedServer
import com.example.toxicbase.server.ToxicCryptoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class ConsoleSection(val title: String) {
    DASHBOARD("Dashboard"),
    PROJECTS("Projects"),
    AUTHENTICATION("Authentication"),
    USERS("Users"),
    DATABASE("Database"),
    API_KEYS("API Keys"),
    SECURITY_RULES("Security Rules"),
    LOGS("Logs"),
    USAGE("Usage"),
    SETTINGS("Settings & Deploy")
}

data class StatusBanner(
    val message: String,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class RuleSimulationResult(
    val allowed: Boolean,
    val collection: String,
    val operation: String,
    val roleUsed: String,
    val ruleEvaluated: String,
    val explanation: String
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ToxicBaseConsoleViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("toxicbase_console_prefs", Context.MODE_PRIVATE)
    private val database = ToxicBaseDatabase.getInstance(application)
    private val dao: ToxicBaseDao = database.dao()
    val embeddedServer: ToxicBaseEmbeddedServer = ToxicBaseEmbeddedServer.getInstance(application)

    private val _currentSection = MutableStateFlow(ConsoleSection.DASHBOARD)
    val currentSection: StateFlow<ConsoleSection> = _currentSection.asStateFlow()

    private val _selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProjectId: StateFlow<String?> = _selectedProjectId.asStateFlow()

    private val _activeSdkApiKey = MutableStateFlow("")
    val activeSdkApiKey: StateFlow<String> = _activeSdkApiKey.asStateFlow()

    private val _backendBaseUrl = MutableStateFlow("http://127.0.0.1:8765")
    val backendBaseUrl: StateFlow<String> = _backendBaseUrl.asStateFlow()

    private val _statusBanner = MutableStateFlow<StatusBanner?>(null)
    val statusBanner: StateFlow<StatusBanner?> = _statusBanner.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    // OTP Cooldown countdown timer
    private val _otpCooldownRemaining = MutableStateFlow(0)
    val otpCooldownRemaining: StateFlow<Int> = _otpCooldownRemaining.asStateFlow()
    private var cooldownJob: Job? = null

    // Database Explorer & Query State
    private val _selectedCollection = MutableStateFlow("users_profile")
    val selectedCollection: StateFlow<String> = _selectedCollection.asStateFlow()

    private val _queryPageResult = MutableStateFlow<ToxicQueryPage?>(null)
    val queryPageResult: StateFlow<ToxicQueryPage?> = _queryPageResult.asStateFlow()

    private val _selectedDocumentDetail = MutableStateFlow<ToxicDocument?>(null)
    val selectedDocumentDetail: StateFlow<ToxicDocument?> = _selectedDocumentDetail.asStateFlow()

    private val _ruleSimResult = MutableStateFlow<RuleSimulationResult?>(null)
    val ruleSimResult: StateFlow<RuleSimulationResult?> = _ruleSimResult.asStateFlow()

    // Reactive Flows from Room
    val projects: StateFlow<List<ProjectEntity>> = dao.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedProject: StateFlow<ProjectEntity?> = combine(projects, _selectedProjectId) { list, id ->
        list.find { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val projectApiKeys: StateFlow<List<ApiKeyEntity>> = selectedProject.flatMapLatest { proj ->
        if (proj == null) flowOf(emptyList()) else dao.observeApiKeys(proj.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectUsers: StateFlow<List<UserEntity>> = selectedProject.flatMapLatest { proj ->
        if (proj == null) flowOf(emptyList()) else dao.observeUsers(proj.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectDocuments: StateFlow<List<DocumentEntity>> = selectedProject.flatMapLatest { proj ->
        if (proj == null) flowOf(emptyList()) else dao.observeAllProjectDocuments(proj.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectCollections: StateFlow<List<String>> = selectedProject.flatMapLatest { proj ->
        if (proj == null) flowOf(emptyList()) else dao.observeCollectionNames(proj.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectIndexes: StateFlow<List<CollectionIndexEntity>> = selectedProject.flatMapLatest { proj ->
        if (proj == null) flowOf(emptyList()) else dao.observeIndexes(proj.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectLogs: StateFlow<List<RequestLogEntity>> = selectedProject.flatMapLatest { proj ->
        if (proj == null) flowOf(emptyList()) else dao.observeRequestLogs(proj.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSessionsCount: StateFlow<Int> = selectedProject.flatMapLatest { proj ->
        if (proj == null) flowOf(0) else dao.observeActiveSessionCount(proj.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val sdkSession: StateFlow<ToxicSession?> = ToxicBase.sessionFlow
    val deviceSmsInbox: StateFlow<List<IncomingDeviceSmsMessage>> = SmsProviderGateway.deviceSmsInbox
    val serverOnline: StateFlow<Boolean> = embeddedServer.serverOnline

    init {
        bootstrapPlatform()
    }

    private fun bootstrapPlatform() {
        embeddedServer.startServer(8765)
        viewModelScope.launch {
            // Wait briefly for socket bind
            delay(150)
            val configuredRemote = try {
                BuildConfig.TOXICBASE_REMOTE_BACKEND_URL.trim()
            } catch (_: Throwable) {
                ""
            }
            val initialUrl = if (configuredRemote.isNotBlank() && !configuredRemote.contains("127.0.0.1")) {
                configuredRemote
            } else {
                embeddedServer.getBaseUrl()
            }
            _backendBaseUrl.value = initialUrl

            val existingProjects = dao.getAllProjects()
            if (existingProjects.isEmpty()) {
                initializeFirstProductionProject(initialUrl)
            } else {
                val firstProj = existingProjects.first()
                _selectedProjectId.value = firstProj.id
                ensureSdkKeyForProject(firstProj.id, initialUrl)
                refreshDatabaseCollectionQuery()
            }

            // Listen for real-time database events to automatically refresh active collection queries
            launch {
                embeddedServer.realtimeEvents.collect { evt ->
                    if (evt.projectId == selectedProject.value?.id && evt.collection == _selectedCollection.value) {
                        refreshDatabaseCollectionQuery()
                    }
                }
            }
        }
    }

    private suspend fun initializeFirstProductionProject(baseUrl: String) = withContext(Dispatchers.IO) {
        val projectId = ToxicCryptoEngine.generateProjectId()
        val rawAdminKey = ToxicCryptoEngine.generateApiKey("server_admin")
        val rawClientKey = ToxicCryptoEngine.generateApiKey("client")

        val defaultRulesJson = JSONObject()
            .put("users_profile", JSONObject().put("read", "public").put("write", "public"))
            .put("orders", JSONObject().put("read", "authenticated").put("write", "owner_only"))
            .put("audit_events", JSONObject().put("read", "admin_only").put("write", "admin_only"))
            .toString()

        val project = ProjectEntity(
            id = projectId,
            name = "ToxicCore-Prod",
            region = "us-east-1",
            authPhoneEnabled = true,
            authEmailEnabled = true,
            otpExpirySeconds = 300,
            otpMaxAttempts = 5,
            otpCooldownSeconds = 60,
            rateLimitPerMinute = 120,
            defaultReadRule = "public",
            defaultWriteRule = "public",
            collectionRulesJson = defaultRulesJson
        )
        dao.insertProject(project)

        val adminKeyEntity = ApiKeyEntity(
            id = "key_${ToxicCryptoEngine.generateRandomHex(6)}",
            projectId = projectId,
            label = "Master Server Key",
            keyPrefix = rawAdminKey.take(16) + "...",
            keyHash = ToxicCryptoEngine.hmacSha256Hex(rawAdminKey),
            role = "server_admin"
        )
        val clientKeyEntity = ApiKeyEntity(
            id = "key_${ToxicCryptoEngine.generateRandomHex(6)}",
            projectId = projectId,
            label = "Android SDK Public Key",
            keyPrefix = rawClientKey.take(16) + "...",
            keyHash = ToxicCryptoEngine.hmacSha256Hex(rawClientKey),
            role = "client"
        )
        dao.insertApiKey(adminKeyEntity)
        dao.insertApiKey(clientKeyEntity)

        embeddedServer.registerRawKeyInSession(adminKeyEntity.id, rawAdminKey)
        embeddedServer.registerRawKeyInSession(clientKeyEntity.id, rawClientKey)
        prefs.edit()
            .putString("raw_key_${adminKeyEntity.id}", rawAdminKey)
            .putString("raw_key_${clientKeyEntity.id}", rawClientKey)
            .putString("active_key_$projectId", rawAdminKey)
            .apply()

        // Create default indexes
        dao.insertIndex(
            CollectionIndexEntity(
                id = "idx_${ToxicCryptoEngine.generateRandomHex(6)}",
                projectId = projectId,
                collectionName = "users_profile",
                fieldPath = "role",
                sortOrder = "ASC"
            )
        )

        _selectedProjectId.value = projectId
        _activeSdkApiKey.value = rawAdminKey
        ToxicBase.initialize(rawAdminKey, baseUrl)

        // Create initial real document through the ToxicBase Android SDK over HTTP
        ToxicBase.database.collection("users_profile").create(
            data = mapOf(
                "username" to "root_operator",
                "role" to "architect",
                "tier" to "enterprise",
                "verified" to true
            ),
            documentId = "doc_operator_01"
        )
        refreshDatabaseCollectionQuery()
    }

    private suspend fun ensureSdkKeyForProject(projectId: String, baseUrl: String) {
        val savedKey = prefs.getString("active_key_$projectId", null)
        if (!savedKey.isNullOrBlank()) {
            _activeSdkApiKey.value = savedKey
            ToxicBase.initialize(savedKey, baseUrl)
            return
        }
        // Generate a fresh admin key if no raw key is stored in local vault
        val rawKey = ToxicCryptoEngine.generateApiKey("server_admin")
        val keyEntity = ApiKeyEntity(
            id = "key_${ToxicCryptoEngine.generateRandomHex(6)}",
            projectId = projectId,
            label = "Auto-Provisioned Console Key",
            keyPrefix = rawKey.take(16) + "...",
            keyHash = ToxicCryptoEngine.hmacSha256Hex(rawKey),
            role = "server_admin"
        )
        dao.insertApiKey(keyEntity)
        embeddedServer.registerRawKeyInSession(keyEntity.id, rawKey)
        prefs.edit()
            .putString("raw_key_${keyEntity.id}", rawKey)
            .putString("active_key_$projectId", rawKey)
            .apply()
        _activeSdkApiKey.value = rawKey
        ToxicBase.initialize(rawKey, baseUrl)
    }

    fun navigateTo(section: ConsoleSection) {
        _currentSection.value = section
        if (section == ConsoleSection.DATABASE) {
            refreshDatabaseCollectionQuery()
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun dismissSmsMessage(id: String) {
        SmsProviderGateway.dismissDeviceSms(id)
    }

    fun getRevealedKey(keyId: String): String? {
        return embeddedServer.getRawKeyIfAvailable(keyId) ?: prefs.getString("raw_key_$keyId", null)
    }

    fun selectProject(projectId: String) {
        _selectedProjectId.value = projectId
        viewModelScope.launch {
            ensureSdkKeyForProject(projectId, _backendBaseUrl.value)
            refreshDatabaseCollectionQuery()
            postBanner("Switched active project to $projectId")
        }
    }

    fun bindSdkApiKey(rawKey: String) {
        val clean = rawKey.trim()
        if (clean.isBlank()) return
        _activeSdkApiKey.value = clean
        selectedProject.value?.id?.let { pid ->
            prefs.edit().putString("active_key_$pid", clean).apply()
        }
        ToxicBase.initialize(clean, _backendBaseUrl.value)
        postBanner("ToxicBase.initialize() bound to key ${clean.take(14)}...")
        refreshDatabaseCollectionQuery()
    }

    fun updateBackendUrl(newUrl: String) {
        val clean = newUrl.trim().trimEnd('/')
        if (clean.isBlank()) return
        _backendBaseUrl.value = clean
        ToxicBase.setBaseUrl(clean)
        postBanner("ToxicBase SDK endpoint updated to $clean")
    }

    // =========================================================================
    // PROJECT ACTIONS (REST /projects)
    // =========================================================================
    fun createNewProject(name: String, region: String) {
        if (name.isBlank()) {
            postBanner("Project name is required", isError = true)
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val payload = JSONObject()
                .put("name", name.trim())
                .put("region", region.trim())
            val res = withContext(Dispatchers.IO) {
                ToxicBase.executeHttp("POST", "/projects", payload)
            }
            _isBusy.value = false
            when (res) {
                is ToxicResult.Success -> {
                    val projObj = res.data.getJSONObject("project")
                    val pid = projObj.getString("id")
                    val adminKey = res.data.optString("adminApiKey")
                    if (adminKey.isNotBlank()) {
                        prefs.edit().putString("active_key_$pid", adminKey).apply()
                        _activeSdkApiKey.value = adminKey
                        ToxicBase.initialize(adminKey, _backendBaseUrl.value)
                    }
                    _selectedProjectId.value = pid
                    postBanner("Created project '${name.trim()}' ($pid)")
                }
                is ToxicResult.Error -> postBanner(res.message, isError = true)
            }
        }
    }

    fun deleteProject(projectId: String) {
        if (projects.value.size <= 1) {
            postBanner("Cannot delete the last remaining active project", isError = true)
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val res = withContext(Dispatchers.IO) {
                ToxicBase.executeHttp("DELETE", "/projects/$projectId", null)
            }
            _isBusy.value = false
            when (res) {
                is ToxicResult.Success -> {
                    val remaining = dao.getAllProjects()
                    val next = remaining.firstOrNull()
                    if (next != null) {
                        selectProject(next.id)
                    }
                    postBanner("Deleted project $projectId")
                }
                is ToxicResult.Error -> postBanner(res.message, isError = true)
            }
        }
    }

    fun updateProjectAuthConfig(
        phoneEnabled: Boolean,
        emailEnabled: Boolean,
        otpExpirySec: Int,
        otpMaxAttempts: Int,
        otpCooldownSec: Int,
        rateLimitPerMin: Int
    ) {
        val current = selectedProject.value ?: return
        viewModelScope.launch {
            val updated = current.copy(
                authPhoneEnabled = phoneEnabled,
                authEmailEnabled = emailEnabled,
                otpExpirySeconds = otpExpirySec.coerceIn(60, 1800),
                otpMaxAttempts = otpMaxAttempts.coerceIn(1, 10),
                otpCooldownSeconds = otpCooldownSec.coerceIn(10, 300),
                rateLimitPerMinute = rateLimitPerMin.coerceIn(10, 5000)
            )
            dao.updateProject(updated)
            postBanner("Updated authentication & OTP security policy for ${current.name}")
        }
    }

    // =========================================================================
    // API KEYS MANAGEMENT
    // =========================================================================
    fun generateApiKey(label: String, role: String) {
        val proj = selectedProject.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val payload = JSONObject()
                .put("label", label.ifBlank { "Custom SDK Key" })
                .put("role", role)
            val res = withContext(Dispatchers.IO) {
                ToxicBase.executeHttp("POST", "/projects/${proj.id}/keys", payload)
            }
            _isBusy.value = false
            when (res) {
                is ToxicResult.Success -> {
                    val keyId = res.data.optString("keyId")
                    val rawKey = res.data.optString("apiKey")
                    if (keyId.isNotBlank() && rawKey.isNotBlank()) {
                        prefs.edit().putString("raw_key_$keyId", rawKey).apply()
                    }
                    postBanner("Generated new $role API key: ${rawKey.take(16)}...")
                }
                is ToxicResult.Error -> postBanner(res.message, isError = true)
            }
        }
    }

    fun toggleApiKeyStatus(key: ApiKeyEntity) {
        viewModelScope.launch {
            dao.setApiKeyActive(key.projectId, key.id, !key.isActive)
            postBanner(if (!key.isActive) "Activated API key ${key.label}" else "Revoked API key ${key.label}")
        }
    }

    fun deleteApiKey(key: ApiKeyEntity) {
        viewModelScope.launch {
            dao.deleteApiKey(key.projectId, key.id)
            postBanner("Deleted API key ${key.label}")
        }
    }

    // =========================================================================
    // AUTHENTICATION & REAL SMS OTP ACTIONS (Via ToxicBase.auth SDK)
    // =========================================================================
    fun sendPhoneOtp(phoneNumber: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = ToxicBase.auth.sendOTP(phoneNumber)
            _isBusy.value = false
            when (result) {
                is ToxicResult.Success -> {
                    val data = result.data
                    startCooldownCountdown(data.cooldownSeconds)
                    postBanner("OTP sent to ${data.phoneNumber} via ${data.provider}. Check incoming SMS notification!")
                }
                is ToxicResult.Error -> {
                    postBanner(result.message, isError = true)
                }
            }
        }
    }

    fun verifyPhoneOtp(phoneNumber: String, otpCode: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = ToxicBase.auth.verifyOTP(phoneNumber, otpCode)
            _isBusy.value = false
            when (result) {
                is ToxicResult.Success -> {
                    postBanner("OTP Verified! Signed in as ${result.data.user.id} (${result.data.user.phoneNumber})")
                }
                is ToxicResult.Error -> {
                    postBanner(result.message, isError = true)
                }
            }
        }
    }

    fun registerEmailUser(email: String, password: String, phoneOptional: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = ToxicBase.auth.register(email, password, phoneOptional.takeIf { it.isNotBlank() })
            _isBusy.value = false
            when (result) {
                is ToxicResult.Success -> {
                    postBanner("Registered & signed in as ${result.data.user.email} (${result.data.user.id})")
                }
                is ToxicResult.Error -> postBanner(result.message, isError = true)
            }
        }
    }

    fun loginEmailUser(email: String, password: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = ToxicBase.auth.login(email, password)
            _isBusy.value = false
            when (result) {
                is ToxicResult.Success -> {
                    postBanner("Authenticated as ${result.data.user.email} (${result.data.user.id})")
                }
                is ToxicResult.Error -> postBanner(result.message, isError = true)
            }
        }
    }

    fun refreshCurrentUserSession() {
        viewModelScope.launch {
            _isBusy.value = true
            val result = ToxicBase.auth.refreshToken()
            _isBusy.value = false
            when (result) {
                is ToxicResult.Success -> postBanner("Rotated refresh token & issued fresh 15m JWT access token")
                is ToxicResult.Error -> postBanner(result.message, isError = true)
            }
        }
    }

    fun logoutCurrentUser() {
        viewModelScope.launch {
            _isBusy.value = true
            ToxicBase.auth.logout()
            _isBusy.value = false
            postBanner("Logged out and revoked refresh token on server")
        }
    }

    private fun startCooldownCountdown(seconds: Int) {
        cooldownJob?.cancel()
        _otpCooldownRemaining.value = seconds
        cooldownJob = viewModelScope.launch {
            while (_otpCooldownRemaining.value > 0) {
                delay(1000)
                _otpCooldownRemaining.value = (_otpCooldownRemaining.value - 1).coerceAtLeast(0)
            }
        }
    }

    // =========================================================================
    // USER MANAGEMENT ACTIONS (REST /users/:id)
    // =========================================================================
    fun updateUserRecord(userId: String, status: String, email: String?, phone: String?) {
        viewModelScope.launch {
            _isBusy.value = true
            val payload = JSONObject().put("status", status)
            if (email != null) payload.put("email", email)
            if (phone != null) payload.put("phoneNumber", phone)

            val res = withContext(Dispatchers.IO) {
                ToxicBase.executeHttp("PUT", "/users/$userId", payload)
            }
            _isBusy.value = false
            when (res) {
                is ToxicResult.Success -> postBanner("Updated user $userId (Status: $status)")
                is ToxicResult.Error -> postBanner(res.message, isError = true)
            }
        }
    }

    fun deleteUserRecord(userId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = withContext(Dispatchers.IO) {
                ToxicBase.executeHttp("DELETE", "/users/$userId", null)
            }
            _isBusy.value = false
            when (res) {
                is ToxicResult.Success -> postBanner("Deleted user $userId")
                is ToxicResult.Error -> postBanner(res.message, isError = true)
            }
        }
    }

    // =========================================================================
    // NOSQL DOCUMENT DATABASE ACTIONS (Via ToxicBase.database SDK)
    // =========================================================================
    fun selectCollection(collectionName: String) {
        if (collectionName.isBlank()) return
        _selectedCollection.value = collectionName.trim()
        _selectedDocumentDetail.value = null
        refreshDatabaseCollectionQuery()
    }

    fun refreshDatabaseCollectionQuery(
        filterField: String? = null,
        filterOp: String = "==",
        filterValue: String? = null,
        sortBy: String? = null,
        sortOrder: String = "DESC",
        page: Int = 1,
        limit: Int = 20
    ) {
        val col = _selectedCollection.value
        if (col.isBlank()) return
        viewModelScope.launch {
            val res = ToxicBase.database.collection(col).query(
                filterField = filterField,
                filterOp = filterOp,
                filterValue = filterValue,
                sortBy = sortBy,
                sortOrder = sortOrder,
                page = page,
                limit = limit
            )
            when (res) {
                is ToxicResult.Success -> {
                    _queryPageResult.value = res.data
                }
                is ToxicResult.Error -> {
                    postBanner(res.message, isError = true)
                }
            }
        }
    }

    fun createDocumentInCollection(collection: String, documentId: String?, rawJson: String) {
        val targetCol = collection.trim().ifBlank { _selectedCollection.value }
        viewModelScope.launch {
            _isBusy.value = true
            val res = ToxicBase.database.collection(targetCol).createFromJson(rawJson, documentId)
            _isBusy.value = false
            when (res) {
                is ToxicResult.Success -> {
                    _selectedCollection.value = targetCol
                    _selectedDocumentDetail.value = res.data
                    refreshDatabaseCollectionQuery()
                    postBanner("Created document ${res.data.id} in '$targetCol'")
                }
                is ToxicResult.Error -> postBanner(res.message, isError = true)
            }
        }
    }

    fun fetchSingleDocument(collection: String, documentId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = ToxicBase.database.get(collection, documentId)
            _isBusy.value = false
            when (res) {
                is ToxicResult.Success -> {
                    _selectedDocumentDetail.value = res.data
                }
                is ToxicResult.Error -> postBanner(res.message, isError = true)
            }
        }
    }

    fun updateDocumentInCollection(collection: String, documentId: String, rawJson: String, merge: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = ToxicBase.database.collection(collection).updateFromJson(documentId, rawJson, merge)
            _isBusy.value = false
            when (res) {
                is ToxicResult.Success -> {
                    _selectedDocumentDetail.value = res.data
                    refreshDatabaseCollectionQuery()
                    postBanner("Updated document $collection/$documentId (v${res.data.version})")
                }
                is ToxicResult.Error -> postBanner(res.message, isError = true)
            }
        }
    }

    fun deleteDocumentFromCollection(collection: String, documentId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = ToxicBase.database.delete(collection, documentId)
            _isBusy.value = false
            when (res) {
                is ToxicResult.Success -> {
                    if (_selectedDocumentDetail.value?.id == documentId) {
                        _selectedDocumentDetail.value = null
                    }
                    refreshDatabaseCollectionQuery()
                    postBanner("Deleted document $collection/$documentId")
                }
                is ToxicResult.Error -> postBanner(res.message, isError = true)
            }
        }
    }

    fun createCollectionIndex(collection: String, fieldPath: String, sortOrder: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val payload = JSONObject()
                .put("fieldPath", fieldPath.trim())
                .put("sortOrder", sortOrder)
            val res = withContext(Dispatchers.IO) {
                ToxicBase.executeHttp("POST", "/database/${collection.trim()}/indexes", payload)
            }
            _isBusy.value = false
            when (res) {
                is ToxicResult.Success -> postBanner("Created index on ${collection.trim()}.${fieldPath.trim()} ($sortOrder)")
                is ToxicResult.Error -> postBanner(res.message, isError = true)
            }
        }
    }

    fun deleteCollectionIndex(indexId: String) {
        val proj = selectedProject.value ?: return
        viewModelScope.launch {
            dao.deleteIndex(proj.id, indexId)
            postBanner("Removed index $indexId")
        }
    }

    // =========================================================================
    // SECURITY RULES CONFIGURATION & SIMULATOR
    // =========================================================================
    fun saveSecurityRules(
        defaultRead: String,
        defaultWrite: String,
        collectionRulesJson: String
    ) {
        val proj = selectedProject.value ?: return
        val validatedJson = try {
            JSONObject(collectionRulesJson).toString()
        } catch (e: Throwable) {
            postBanner("Invalid collection rules JSON: ${e.message}", isError = true)
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val payload = JSONObject()
                .put("defaultReadRule", defaultRead)
                .put("defaultWriteRule", defaultWrite)
                .put("collectionRulesJson", validatedJson)
            val res = withContext(Dispatchers.IO) {
                ToxicBase.executeHttp("PUT", "/projects/${proj.id}/rules", payload)
            }
            _isBusy.value = false
            when (res) {
                is ToxicResult.Success -> postBanner("Deployed Security Rules for ${proj.name}")
                is ToxicResult.Error -> postBanner(res.message, isError = true)
            }
        }
    }

    fun simulateSecurityRule(
        collection: String,
        operation: String, // "read" or "write"
        apiKeyRole: String, // "client", "readonly", "server_admin"
        authenticatedUserId: String?,
        documentOwnerId: String?
    ) {
        val proj = selectedProject.value ?: return
        val mockKey = ApiKeyEntity(
            id = "sim_key",
            projectId = proj.id,
            label = "Simulator Key",
            keyPrefix = "tb_sim_",
            keyHash = "sim",
            role = apiKeyRole
        )
        val mockClaims = authenticatedUserId?.takeIf { it.isNotBlank() }?.let { uid ->
            JwtClaims(
                sub = uid,
                projectId = proj.id,
                phone = null,
                email = null,
                iat = System.currentTimeMillis() / 1000L,
                exp = (System.currentTimeMillis() / 1000L) + 900L,
                jti = "sim_jti"
            )
        }

        val allowed = embeddedServer.evaluateSecurityRule(
            project = proj,
            apiKey = mockKey,
            jwtClaims = mockClaims,
            collectionName = collection.trim(),
            operation = operation,
            resourceOwnerId = documentOwnerId?.takeIf { it.isNotBlank() }
        )

        val ruleMode = try {
            val obj = JSONObject(proj.collectionRulesJson).optJSONObject(collection.trim())
            obj?.optString(operation)?.takeIf { it.isNotBlank() }
                ?: if (operation == "read") proj.defaultReadRule else proj.defaultWriteRule
        } catch (_: Throwable) {
            if (operation == "read") proj.defaultReadRule else proj.defaultWriteRule
        }

        _ruleSimResult.value = RuleSimulationResult(
            allowed = allowed,
            collection = collection.trim(),
            operation = operation.uppercase(),
            roleUsed = apiKeyRole,
            ruleEvaluated = ruleMode,
            explanation = if (allowed) {
                "ACCESS GRANTED: Role '$apiKeyRole' with user '${authenticatedUserId ?: "anonymous"}' satisfies '$ruleMode' on '$collection'."
            } else {
                "ACCESS DENIED: Role '$apiKeyRole' with user '${authenticatedUserId ?: "anonymous"}' failed '$ruleMode' check on '$collection'."
            }
        )
    }

    fun clearRequestLogs() {
        val proj = selectedProject.value ?: return
        viewModelScope.launch {
            dao.clearLogs(proj.id)
            postBanner("Cleared audit logs for ${proj.name}")
        }
    }

    private fun postBanner(message: String, isError: Boolean = false) {
        _statusBanner.value = StatusBanner(message = message, isError = isError)
    }
}

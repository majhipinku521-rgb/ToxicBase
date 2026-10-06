package com.example.toxicbase.server

import android.content.Context
import com.example.toxicbase.data.ApiKeyEntity
import com.example.toxicbase.data.CollectionIndexEntity
import com.example.toxicbase.data.DocumentEntity
import com.example.toxicbase.data.OtpChallengeEntity
import com.example.toxicbase.data.ProjectEntity
import com.example.toxicbase.data.RequestLogEntity
import com.example.toxicbase.data.SessionEntity
import com.example.toxicbase.data.ToxicBaseDao
import com.example.toxicbase.data.ToxicBaseDatabase
import com.example.toxicbase.data.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class RealtimeDatabaseEvent(
    val projectId: String,
    val collection: String,
    val documentId: String,
    val action: String, // "CREATE", "UPDATE", "DELETE"
    val timestamp: Long = System.currentTimeMillis()
)

class ToxicBaseEmbeddedServer(
    private val appContext: Context,
    private val dao: ToxicBaseDao
) {
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var isRunning = false

    private val _boundPort = MutableStateFlow(8765)
    val boundPort: StateFlow<Int> = _boundPort.asStateFlow()

    private val _serverOnline = MutableStateFlow(false)
    val serverOnline: StateFlow<Boolean> = _serverOnline.asStateFlow()

    // Real-time event bus for live document subscriptions
    private val _realtimeEvents = MutableSharedFlow<RealtimeDatabaseEvent>(extraBufferCapacity = 64)
    val realtimeEvents: SharedFlow<RealtimeDatabaseEvent> = _realtimeEvents.asSharedFlow()

    // In-memory store for newly generated raw API keys during the session so the console can copy/bind them,
    // while the database itself only stores the HMAC-SHA256 hash.
    private val sessionRawKeyVault = ConcurrentHashMap<String, String>()

    // Sliding window rate limit trackers
    private val ipRequestTimestamps = ConcurrentHashMap<String, MutableList<Long>>()
    private val otpRequestTimestamps = ConcurrentHashMap<String, MutableList<Long>>()

    fun getRawKeyIfAvailable(keyId: String): String? = sessionRawKeyVault[keyId]

    fun registerRawKeyInSession(keyId: String, rawKey: String) {
        sessionRawKeyVault[keyId] = rawKey
    }

    fun getBaseUrl(): String = "http://127.0.0.1:${_boundPort.value}"

    @Synchronized
    fun startServer(preferredPort: Int = 8765) {
        if (isRunning) return
        serverScope.launch {
            val socket = try {
                ServerSocket(preferredPort, 50, InetAddress.getByName("127.0.0.1"))
            } catch (_: Throwable) {
                ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            }
            serverSocket = socket
            _boundPort.value = socket.localPort
            isRunning = true
            _serverOnline.value = true

            while (isRunning) {
                try {
                    val client = socket.accept()
                    serverScope.launch { handleClientSocket(client) }
                } catch (_: Throwable) {
                    if (!isRunning) break
                }
            }
        }
    }

    @Synchronized
    fun stopServer() {
        isRunning = false
        _serverOnline.value = false
        try {
            serverSocket?.close()
        } catch (_: Throwable) {
        }
        serverSocket = null
    }

    private fun handleClientSocket(socket: Socket) {
        val startMs = System.currentTimeMillis()
        var method = "GET"
        var rawPath = "/"
        var statusCode = 200
        var resolvedProjectId = "GLOBAL"
        var resolvedUserId: String? = null
        var logDetail = ""

        try {
            socket.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            method = parts[0].uppercase()
            rawPath = parts[1]

            val headers = mutableMapOf<String, String>()
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            val bodyString = if (contentLength > 0) {
                val buffer = CharArray(contentLength.coerceAtMost(1024 * 1024))
                var totalRead = 0
                while (totalRead < buffer.size) {
                    val r = reader.read(buffer, totalRead, buffer.size - totalRead)
                    if (r == -1) break
                    totalRead += r
                }
                String(buffer, 0, totalRead)
            } else {
                ""
            }

            if (method == "OPTIONS") {
                writeHttpResponse(socket.getOutputStream(), 204, "")
                return
            }

            val response = runBlocking {
                routeHttpRequest(
                    method = method,
                    rawUri = rawPath,
                    headers = headers,
                    body = bodyString,
                    remoteIp = socket.inetAddress?.hostAddress ?: "127.0.0.1"
                )
            }

            statusCode = response.statusCode
            resolvedProjectId = response.projectId
            resolvedUserId = response.userId
            logDetail = response.detail
            writeHttpResponse(socket.getOutputStream(), response.statusCode, response.jsonBody)
        } catch (e: Throwable) {
            statusCode = 500
            logDetail = e.message ?: "Internal server error"
            try {
                val errJson = JSONObject().put("error", logDetail).toString()
                writeHttpResponse(socket.getOutputStream(), 500, errJson)
            } catch (_: Throwable) {
            }
        } finally {
            val latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            serverScope.launch {
                try {
                    dao.insertLog(
                        RequestLogEntity(
                            projectId = resolvedProjectId,
                            method = method,
                            path = rawPath,
                            statusCode = statusCode,
                            latencyMs = latencyMs,
                            ipAddress = "127.0.0.1",
                            userId = resolvedUserId,
                            detail = logDetail
                        )
                    )
                } catch (_: Throwable) {
                }
            }
            try {
                socket.close()
            } catch (_: Throwable) {
            }
        }
    }

    private data class HttpRouteResult(
        val statusCode: Int,
        val jsonBody: String,
        val projectId: String = "GLOBAL",
        val userId: String? = null,
        val detail: String = ""
    )

    private suspend fun routeHttpRequest(
        method: String,
        rawUri: String,
        headers: Map<String, String>,
        body: String,
        remoteIp: String
    ): HttpRouteResult {
        val uriParts = rawUri.split("?", limit = 2)
        val path = uriParts[0].trimEnd('/').ifEmpty { "/" }
        val queryParams = parseQueryString(uriParts.getOrNull(1).orEmpty())
        val segments = path.trim('/').split('/').filter { it.isNotBlank() }

        // 1. Health & Server Status
        if (method == "GET" && (path == "/" || path == "/health")) {
            val json = JSONObject()
                .put("status", "ONLINE")
                .put("platform", "TOXICBASE BaaS Server")
                .put("version", "1.0.0")
                .put("port", _boundPort.value)
            return HttpRouteResult(200, json.toString(), "GLOBAL", null, "Health check")
        }

        // 2. Project Management Endpoints (/projects, /projects/:id)
        if (segments.firstOrNull() == "projects") {
            return handleProjectsEndpoint(method, segments, body)
        }

        // 3. Authenticate Project API Key (X-ToxicBase-Key or X-Api-Key)
        val rawApiKey = headers["x-toxicbase-key"] ?: headers["x-api-key"]
        if (rawApiKey.isNullOrBlank()) {
            return HttpRouteResult(
                401,
                JSONObject().put("error", "Missing X-ToxicBase-Key header").toString(),
                "GLOBAL",
                null,
                "Rejected: missing API key"
            )
        }

        val keyHash = ToxicCryptoEngine.hmacSha256Hex(rawApiKey.trim())
        val apiKeyEntity = dao.findActiveKeyByHash(keyHash)
            ?: return HttpRouteResult(
                403,
                JSONObject().put("error", "Invalid or revoked ToxicBase API key").toString(),
                "GLOBAL",
                null,
                "Rejected: invalid API key"
            )

        val project = dao.getProjectById(apiKeyEntity.projectId)
            ?: return HttpRouteResult(
                404,
                JSONObject().put("error", "Project not found for API key").toString(),
                apiKeyEntity.projectId,
                null,
                "Project missing"
            )

        dao.touchApiKey(apiKeyEntity.id)

        // 4. Sliding-Window Rate Limiting per Project + IP
        if (!checkRateLimit("${project.id}:$remoteIp", project.rateLimitPerMinute, 60_000L, ipRequestTimestamps)) {
            return HttpRouteResult(
                429,
                JSONObject().put("error", "Rate limit exceeded (${project.rateLimitPerMinute} req/min)").toString(),
                project.id,
                null,
                "Rate limit exceeded"
            )
        }

        // 5. Parse Optional Bearer JWT Access Token
        val authHeader = headers["authorization"]
        val jwtClaims = if (authHeader != null && authHeader.startsWith("Bearer ", ignoreCase = true)) {
            val token = authHeader.substring(7).trim()
            val claims = ToxicCryptoEngine.verifyJwtAccessToken(token)
            if (claims != null && claims.projectId == project.id) claims else null
        } else {
            null
        }

        val firstSeg = segments.firstOrNull() ?: ""
        return when (firstSeg) {
            "auth" -> handleAuthEndpoint(method, segments, body, project, jwtClaims, remoteIp)
            "users" -> handleUsersEndpoint(method, segments, body, project, apiKeyEntity, jwtClaims)
            "database" -> handleDatabaseEndpoint(method, segments, queryParams, body, project, apiKeyEntity, jwtClaims)
            "logs" -> {
                val logs = dao.getRecentLogs(project.id, 100)
                val arr = JSONArray()
                logs.forEach { l ->
                    arr.put(
                        JSONObject()
                            .put("id", l.id)
                            .put("projectId", l.projectId)
                            .put("method", l.method)
                            .put("path", l.path)
                            .put("statusCode", l.statusCode)
                            .put("latencyMs", l.latencyMs)
                            .put("userId", l.userId ?: JSONObject.NULL)
                            .put("detail", l.detail)
                            .put("createdAt", l.createdAt)
                    )
                }
                HttpRouteResult(
                    200,
                    JSONObject().put("logs", arr).toString(),
                    project.id,
                    jwtClaims?.sub,
                    "Fetched ${logs.size} logs"
                )
            }
            "usage" -> {
                val users = dao.getUsersByProject(project.id)
                val collections = dao.getCollectionNames(project.id)
                var docCount = 0
                var storageBytes = 0
                collections.forEach { col ->
                    val docs = dao.getDocumentsInCollection(project.id, col)
                    docCount += docs.size
                    storageBytes += docs.sumOf { it.sizeBytes }
                }
                val logs = dao.getRecentLogs(project.id, 250)
                val json = JSONObject()
                    .put("projectId", project.id)
                    .put("totalUsers", users.size)
                    .put("totalCollections", collections.size)
                    .put("totalDocuments", docCount)
                    .put("storageBytes", storageBytes)
                    .put("recentApiCalls", logs.size)
                HttpRouteResult(200, json.toString(), project.id, jwtClaims?.sub, "Usage metrics")
            }
            else -> HttpRouteResult(
                404,
                JSONObject().put("error", "Endpoint not found: $path").toString(),
                project.id,
                jwtClaims?.sub,
                "404 Not Found"
            )
        }
    }

    // =========================================================================
    // AUTHENTICATION & REAL OTP HANDLERS
    // =========================================================================
    private suspend fun handleAuthEndpoint(
        method: String,
        segments: List<String>,
        body: String,
        project: ProjectEntity,
        jwtClaims: JwtClaims?,
        remoteIp: String
    ): HttpRouteResult {
        val action = segments.getOrNull(1) ?: ""
        val jsonBody = try {
            if (body.isBlank()) JSONObject() else JSONObject(body)
        } catch (_: Throwable) {
            return HttpRouteResult(400, JSONObject().put("error", "Invalid JSON payload").toString(), project.id)
        }

        return when {
            method == "POST" && action == "send-otp" -> {
                if (!project.authPhoneEnabled) {
                    return HttpRouteResult(
                        403,
                        JSONObject().put("error", "Phone authentication is disabled for project ${project.name}").toString(),
                        project.id,
                        null,
                        "Phone auth disabled"
                    )
                }
                val phoneNumber = jsonBody.optString("phoneNumber", "").trim()
                if (!phoneNumber.matches(Regex("^\\+[1-9]\\d{6,14}$"))) {
                    return HttpRouteResult(
                        400,
                        JSONObject().put("error", "Phone number must be in E.164 format (e.g. +14155552671)").toString(),
                        project.id,
                        null,
                        "Invalid E.164 phone"
                    )
                }

                // OTP Rate Limit (max 5 requests per 10 minutes per phone)
                if (!checkRateLimit("otp:${project.id}:$phoneNumber:$remoteIp", 5, 10 * 60_000L, otpRequestTimestamps)) {
                    return HttpRouteResult(
                        429,
                        JSONObject().put("error", "OTP rate limit exceeded (max 5 per 10m). Try again later.").toString(),
                        project.id,
                        null,
                        "OTP rate limit hit"
                    )
                }

                val now = System.currentTimeMillis()
                dao.purgeExpiredOtpChallenges(now)
                val challengeKey = "${project.id}:$phoneNumber"
                val existing = dao.getOtpChallenge(challengeKey)
                if (existing != null && existing.cooldownUntil > now) {
                    val waitSec = ((existing.cooldownUntil - now) / 1000L).coerceAtLeast(1L)
                    return HttpRouteResult(
                        429,
                        JSONObject()
                            .put("error", "Resend OTP cooldown active. Please wait ${waitSec}s.")
                            .put("retryAfterSeconds", waitSec)
                            .toString(),
                        project.id,
                        null,
                        "OTP cooldown (${waitSec}s remaining)"
                    )
                }

                // Generate cryptographic 6-digit OTP and hash immediately with random salt
                val otpPlaintext = ToxicCryptoEngine.generateSecureOtp()
                val salt = ToxicCryptoEngine.generateRandomHex(12)
                val otpHash = ToxicCryptoEngine.hashOtp(project.id, phoneNumber, salt, otpPlaintext)
                val expiresAt = now + (project.otpExpirySeconds * 1000L)
                val cooldownUntil = now + (project.otpCooldownSeconds * 1000L)

                dao.upsertOtpChallenge(
                    OtpChallengeEntity(
                        challengeKey = challengeKey,
                        projectId = project.id,
                        phoneNumber = phoneNumber,
                        otpHash = otpHash,
                        salt = salt,
                        attempts = 0,
                        maxAttempts = project.otpMaxAttempts,
                        expiresAt = expiresAt,
                        cooldownUntil = cooldownUntil,
                        createdAt = now
                    )
                )

                // Dispatch SMS via server-side SMS provider gateway
                val dispatchResult = SmsProviderGateway.dispatchOtpSms(
                    context = appContext,
                    projectName = project.name,
                    recipientPhone = phoneNumber,
                    otpPlaintext = otpPlaintext,
                    expiryMinutes = (project.otpExpirySeconds / 60).coerceAtLeast(1)
                )

                // NEVER expose OTP in HTTP response!
                val resp = JSONObject()
                    .put("success", true)
                    .put("phoneNumber", phoneNumber)
                    .put("expiresInSeconds", project.otpExpirySeconds)
                    .put("cooldownSeconds", project.otpCooldownSeconds)
                    .put("provider", dispatchResult.provider)
                    .put("messageSid", dispatchResult.messageSid)
                    .put("message", "OTP dispatched via SMS. Never shared in API response.")
                HttpRouteResult(200, resp.toString(), project.id, null, "Dispatched OTP to $phoneNumber")
            }

            method == "POST" && action == "verify-otp" -> {
                val phoneNumber = jsonBody.optString("phoneNumber", "").trim()
                val otpInput = jsonBody.optString("otp", "").trim()
                if (phoneNumber.isBlank() || otpInput.isBlank()) {
                    return HttpRouteResult(
                        400,
                        JSONObject().put("error", "phoneNumber and otp are required").toString(),
                        project.id,
                        null,
                        "Missing phone or OTP"
                    )
                }

                val now = System.currentTimeMillis()
                val challengeKey = "${project.id}:$phoneNumber"
                val challenge = dao.getOtpChallenge(challengeKey)
                if (challenge == null || challenge.expiresAt < now) {
                    if (challenge != null) dao.deleteOtpChallenge(challengeKey)
                    return HttpRouteResult(
                        400,
                        JSONObject().put("error", "OTP has expired or was not requested").toString(),
                        project.id,
                        null,
                        "OTP expired/not found"
                    )
                }

                val nextAttempt = challenge.attempts + 1
                if (nextAttempt > challenge.maxAttempts) {
                    dao.deleteOtpChallenge(challengeKey)
                    return HttpRouteResult(
                        429,
                        JSONObject().put("error", "Maximum OTP verification attempts (${challenge.maxAttempts}) exceeded. Request a new OTP.").toString(),
                        project.id,
                        null,
                        "OTP max attempts exceeded"
                    )
                }

                val candidateHash = ToxicCryptoEngine.hashOtp(project.id, phoneNumber, challenge.salt, otpInput)
                val matched = ToxicCryptoEngine.constantTimeEquals(challenge.otpHash, candidateHash)
                if (!matched) {
                    if (nextAttempt >= challenge.maxAttempts) {
                        dao.deleteOtpChallenge(challengeKey)
                        return HttpRouteResult(
                            429,
                            JSONObject()
                                .put("error", "Invalid OTP. Maximum attempts (${challenge.maxAttempts}) reached; code invalidated.")
                                .put("remainingAttempts", 0)
                                .toString(),
                            project.id,
                            null,
                            "OTP invalidated after max failed attempts"
                        )
                    } else {
                        dao.updateOtpAttempts(challengeKey, nextAttempt)
                        val remaining = challenge.maxAttempts - nextAttempt
                        return HttpRouteResult(
                            401,
                            JSONObject()
                                .put("error", "Invalid OTP code. $remaining attempt(s) remaining.")
                                .put("remainingAttempts", remaining)
                                .toString(),
                            project.id,
                            null,
                            "Failed OTP attempt ($nextAttempt/${challenge.maxAttempts})"
                        )
                    }
                }

                // Valid OTP! Consume immediately so it can never be replayed
                dao.deleteOtpChallenge(challengeKey)

                var user = dao.getUserByPhone(project.id, phoneNumber)
                if (user == null) {
                    user = UserEntity(
                        id = ToxicCryptoEngine.generateUserId(),
                        projectId = project.id,
                        phoneNumber = phoneNumber,
                        status = "ACTIVE",
                        createdAt = now,
                        lastLoginAt = now
                    )
                    dao.insertUser(user)
                } else {
                    if (user.status != "ACTIVE") {
                        return HttpRouteResult(
                            403,
                            JSONObject().put("error", "User account is suspended").toString(),
                            project.id,
                            user.id,
                            "Suspended user blocked"
                        )
                    }
                    user = user.copy(lastLoginAt = now)
                    dao.updateUser(user)
                }

                val authPayload = createSessionAndBuildAuthResponse(user, project.id)
                HttpRouteResult(200, authPayload.toString(), project.id, user.id, "Phone OTP verified for ${user.id}")
            }

            method == "POST" && (action == "login" || action == "register") -> {
                if (!project.authEmailEnabled) {
                    return HttpRouteResult(
                        403,
                        JSONObject().put("error", "Email/Password authentication is disabled for project ${project.name}").toString(),
                        project.id,
                        null,
                        "Email auth disabled"
                    )
                }
                val email = jsonBody.optString("email", "").trim().lowercase()
                val password = jsonBody.optString("password", "")
                val phoneOptional = jsonBody.optString("phoneNumber", "").trim().takeIf { it.isNotBlank() }
                val mode = if (action == "register") "register" else jsonBody.optString("mode", "login")

                if (email.isBlank() || !email.contains("@") || password.length < 6) {
                    return HttpRouteResult(
                        400,
                        JSONObject().put("error", "Valid email and password (min 6 chars) are required").toString(),
                        project.id,
                        null,
                        "Invalid email/password input"
                    )
                }

                val now = System.currentTimeMillis()
                if (mode == "register") {
                    val existing = dao.getUserByEmail(project.id, email)
                    if (existing != null) {
                        return HttpRouteResult(
                            409,
                            JSONObject().put("error", "Email is already registered in this project").toString(),
                            project.id,
                            null,
                            "Duplicate email registration"
                        )
                    }
                    val salt = ToxicCryptoEngine.generateRandomHex(16)
                    val passwordHash = ToxicCryptoEngine.hashPassword(password, salt)
                    val newUser = UserEntity(
                        id = ToxicCryptoEngine.generateUserId(),
                        projectId = project.id,
                        phoneNumber = phoneOptional,
                        email = email,
                        passwordHash = passwordHash,
                        passwordSalt = salt,
                        status = "ACTIVE",
                        createdAt = now,
                        lastLoginAt = now
                    )
                    dao.insertUser(newUser)
                    val authPayload = createSessionAndBuildAuthResponse(newUser, project.id)
                    return HttpRouteResult(201, authPayload.toString(), project.id, newUser.id, "Registered user ${newUser.id}")
                } else {
                    val user = dao.getUserByEmail(project.id, email)
                    if (user == null || user.passwordHash == null || user.passwordSalt == null) {
                        return HttpRouteResult(
                            401,
                            JSONObject().put("error", "Invalid email or password").toString(),
                            project.id,
                            null,
                            "Login failed: user not found"
                        )
                    }
                    if (!ToxicCryptoEngine.verifyPassword(password, user.passwordSalt, user.passwordHash)) {
                        return HttpRouteResult(
                            401,
                            JSONObject().put("error", "Invalid email or password").toString(),
                            project.id,
                            user.id,
                            "Login failed: wrong password"
                        )
                    }
                    if (user.status != "ACTIVE") {
                        return HttpRouteResult(
                            403,
                            JSONObject().put("error", "User account is suspended").toString(),
                            project.id,
                            user.id,
                            "Suspended account login blocked"
                        )
                    }
                    val updatedUser = user.copy(lastLoginAt = now)
                    dao.updateUser(updatedUser)
                    val authPayload = createSessionAndBuildAuthResponse(updatedUser, project.id)
                    return HttpRouteResult(200, authPayload.toString(), project.id, updatedUser.id, "Email login ${updatedUser.id}")
                }
            }

            method == "POST" && action == "refresh" -> {
                val refreshToken = jsonBody.optString("refreshToken", "").trim()
                if (refreshToken.isBlank()) {
                    return HttpRouteResult(400, JSONObject().put("error", "refreshToken is required").toString(), project.id)
                }
                val hash = ToxicCryptoEngine.hmacSha256Hex(refreshToken)
                val session = dao.getActiveSessionByRefreshHash(hash)
                val now = System.currentTimeMillis()
                if (session == null || session.projectId != project.id || session.expiresAt < now) {
                    return HttpRouteResult(401, JSONObject().put("error", "Invalid or expired refresh token").toString(), project.id)
                }
                val user = dao.getUserById(project.id, session.userId)
                if (user == null || user.status != "ACTIVE") {
                    return HttpRouteResult(403, JSONObject().put("error", "User inactive or deleted").toString(), project.id)
                }
                // Rotate refresh token
                dao.revokeSessionByRefreshHash(hash)
                val authPayload = createSessionAndBuildAuthResponse(user, project.id)
                HttpRouteResult(200, authPayload.toString(), project.id, user.id, "Refreshed token for ${user.id}")
            }

            method == "POST" && action == "logout" -> {
                val refreshToken = jsonBody.optString("refreshToken", "").trim()
                if (refreshToken.isNotBlank()) {
                    val hash = ToxicCryptoEngine.hmacSha256Hex(refreshToken)
                    dao.revokeSessionByRefreshHash(hash)
                }
                if (jwtClaims != null) {
                    dao.revokeAllUserSessions(project.id, jwtClaims.sub)
                }
                HttpRouteResult(
                    200,
                    JSONObject().put("success", true).put("message", "Session terminated").toString(),
                    project.id,
                    jwtClaims?.sub,
                    "User logged out"
                )
            }

            method == "GET" && action == "me" -> {
                if (jwtClaims == null) {
                    return HttpRouteResult(401, JSONObject().put("error", "Invalid or missing Bearer access token").toString(), project.id)
                }
                val user = dao.getUserById(project.id, jwtClaims.sub)
                    ?: return HttpRouteResult(404, JSONObject().put("error", "User not found").toString(), project.id)
                HttpRouteResult(
                    200,
                    JSONObject().put("user", userToJson(user)).toString(),
                    project.id,
                    user.id,
                    "Fetched current user"
                )
            }

            else -> HttpRouteResult(404, JSONObject().put("error", "Unknown auth endpoint").toString(), project.id)
        }
    }

    private suspend fun createSessionAndBuildAuthResponse(user: UserEntity, projectId: String): JSONObject {
        val accessToken = ToxicCryptoEngine.signJwtAccessToken(
            userId = user.id,
            projectId = projectId,
            phone = user.phoneNumber,
            email = user.email,
            ttlSeconds = 900L
        )
        val refreshToken = ToxicCryptoEngine.generateRefreshToken()
        val refreshHash = ToxicCryptoEngine.hmacSha256Hex(refreshToken)
        val now = System.currentTimeMillis()
        dao.insertSession(
            SessionEntity(
                id = "sess_${ToxicCryptoEngine.generateRandomHex(8)}",
                userId = user.id,
                projectId = projectId,
                refreshTokenHash = refreshHash,
                expiresAt = now + 30L * 24L * 3600L * 1000L,
                revoked = false,
                createdAt = now
            )
        )
        return JSONObject()
            .put("user", userToJson(user))
            .put("accessToken", accessToken)
            .put("refreshToken", refreshToken)
            .put("expiresIn", 900)
    }

    // =========================================================================
    // USER MANAGEMENT HANDLERS (/users, /users/:id)
    // =========================================================================
    private suspend fun handleUsersEndpoint(
        method: String,
        segments: List<String>,
        body: String,
        project: ProjectEntity,
        apiKey: ApiKeyEntity,
        jwtClaims: JwtClaims?
    ): HttpRouteResult {
        val targetUserId = segments.getOrNull(1)
        return when {
            method == "GET" && targetUserId == null -> {
                val users = dao.getUsersByProject(project.id)
                val now = System.currentTimeMillis()
                val dayAgo = now - 24L * 3600L * 1000L
                val arr = JSONArray()
                users.forEach { arr.put(userToJson(it)) }
                val out = JSONObject()
                    .put("totalUsers", users.size)
                    .put("newUsers24h", users.count { it.createdAt >= dayAgo })
                    .put("activeUsers", users.count { it.status == "ACTIVE" && it.lastLoginAt != null })
                    .put("users", arr)
                HttpRouteResult(200, out.toString(), project.id, jwtClaims?.sub, "Listed ${users.size} users")
            }

            method == "GET" && targetUserId != null -> {
                val user = dao.getUserById(project.id, targetUserId)
                    ?: return HttpRouteResult(404, JSONObject().put("error", "User not found").toString(), project.id)
                HttpRouteResult(200, JSONObject().put("user", userToJson(user)).toString(), project.id, jwtClaims?.sub, "Read user $targetUserId")
            }

            method == "PUT" && targetUserId != null -> {
                if (apiKey.role == "readonly") {
                    return HttpRouteResult(403, JSONObject().put("error", "Readonly API key cannot modify users").toString(), project.id)
                }
                val existing = dao.getUserById(project.id, targetUserId)
                    ?: return HttpRouteResult(404, JSONObject().put("error", "User not found").toString(), project.id)
                val json = JSONObject(body.ifBlank { "{}" })
                val newStatus = json.optString("status", existing.status)
                val newEmail = if (json.has("email")) json.optString("email").takeIf { it.isNotBlank() } else existing.email
                val newPhone = if (json.has("phoneNumber")) json.optString("phoneNumber").takeIf { it.isNotBlank() } else existing.phoneNumber

                val updated = existing.copy(
                    status = newStatus,
                    email = newEmail,
                    phoneNumber = newPhone
                )
                dao.updateUser(updated)
                if (newStatus != "ACTIVE") {
                    dao.revokeAllUserSessions(project.id, updated.id)
                }
                HttpRouteResult(200, JSONObject().put("user", userToJson(updated)).toString(), project.id, jwtClaims?.sub, "Updated user $targetUserId")
            }

            method == "DELETE" && targetUserId != null -> {
                if (apiKey.role != "server_admin") {
                    return HttpRouteResult(403, JSONObject().put("error", "Only server_admin key can delete users").toString(), project.id)
                }
                dao.revokeAllUserSessions(project.id, targetUserId)
                dao.deleteUser(project.id, targetUserId)
                HttpRouteResult(200, JSONObject().put("deleted", true).put("id", targetUserId).toString(), project.id, jwtClaims?.sub, "Deleted user $targetUserId")
            }

            else -> HttpRouteResult(404, JSONObject().put("error", "Unknown users route").toString(), project.id)
        }
    }

    // =========================================================================
    // PROJECT MANAGEMENT HANDLERS (/projects, /projects/:id)
    // =========================================================================
    private suspend fun handleProjectsEndpoint(
        method: String,
        segments: List<String>,
        body: String
    ): HttpRouteResult {
        val projectId = segments.getOrNull(1)
        val subResource = segments.getOrNull(2)

        return when {
            method == "GET" && projectId == null -> {
                val projects = dao.getAllProjects()
                val arr = JSONArray()
                projects.forEach { p -> arr.put(projectToJson(p)) }
                HttpRouteResult(200, JSONObject().put("projects", arr).toString(), "GLOBAL", null, "Listed ${projects.size} projects")
            }

            method == "POST" && projectId == null -> {
                val json = JSONObject(body.ifBlank { "{}" })
                val name = json.optString("name", "").trim()
                val region = json.optString("region", "us-east-1").trim()
                if (name.isBlank()) {
                    return HttpRouteResult(400, JSONObject().put("error", "Project name is required").toString(), "GLOBAL")
                }
                val newProjectId = ToxicCryptoEngine.generateProjectId()
                val rawClientKey = ToxicCryptoEngine.generateApiKey("client")
                val rawAdminKey = ToxicCryptoEngine.generateApiKey("server_admin")

                val project = ProjectEntity(
                    id = newProjectId,
                    name = name,
                    region = region,
                    authPhoneEnabled = json.optBoolean("authPhoneEnabled", true),
                    authEmailEnabled = json.optBoolean("authEmailEnabled", true)
                )
                dao.insertProject(project)

                val clientKeyEntity = ApiKeyEntity(
                    id = "key_${ToxicCryptoEngine.generateRandomHex(6)}",
                    projectId = newProjectId,
                    label = "Android SDK Client Key",
                    keyPrefix = rawClientKey.take(14) + "...",
                    keyHash = ToxicCryptoEngine.hmacSha256Hex(rawClientKey),
                    role = "client"
                )
                val adminKeyEntity = ApiKeyEntity(
                    id = "key_${ToxicCryptoEngine.generateRandomHex(6)}",
                    projectId = newProjectId,
                    label = "Server Admin Master Key",
                    keyPrefix = rawAdminKey.take(14) + "...",
                    keyHash = ToxicCryptoEngine.hmacSha256Hex(rawAdminKey),
                    role = "server_admin"
                )
                dao.insertApiKey(clientKeyEntity)
                dao.insertApiKey(adminKeyEntity)
                registerRawKeyInSession(clientKeyEntity.id, rawClientKey)
                registerRawKeyInSession(adminKeyEntity.id, rawAdminKey)

                val out = JSONObject()
                    .put("project", projectToJson(project))
                    .put("clientApiKey", rawClientKey)
                    .put("adminApiKey", rawAdminKey)
                HttpRouteResult(201, out.toString(), newProjectId, null, "Created project $newProjectId")
            }

            method == "DELETE" && projectId != null && subResource == null -> {
                dao.deleteDocumentsByProject(projectId)
                dao.deleteUsersByProject(projectId)
                dao.deleteApiKeysByProject(projectId)
                dao.deleteProject(projectId)
                HttpRouteResult(200, JSONObject().put("deleted", true).put("projectId", projectId).toString(), projectId, null, "Deleted project $projectId")
            }

            method == "PUT" && projectId != null && subResource == "rules" -> {
                val existing = dao.getProjectById(projectId)
                    ?: return HttpRouteResult(404, JSONObject().put("error", "Project not found").toString(), projectId)
                val json = JSONObject(body.ifBlank { "{}" })
                val updated = existing.copy(
                    defaultReadRule = json.optString("defaultReadRule", existing.defaultReadRule),
                    defaultWriteRule = json.optString("defaultWriteRule", existing.defaultWriteRule),
                    collectionRulesJson = json.optString("collectionRulesJson", existing.collectionRulesJson)
                )
                dao.updateProject(updated)
                HttpRouteResult(200, JSONObject().put("project", projectToJson(updated)).toString(), projectId, null, "Updated security rules")
            }

            method == "POST" && projectId != null && subResource == "keys" -> {
                val json = JSONObject(body.ifBlank { "{}" })
                val label = json.optString("label", "Custom API Key")
                val role = json.optString("role", "client")
                val rawKey = ToxicCryptoEngine.generateApiKey(role)
                val keyEntity = ApiKeyEntity(
                    id = "key_${ToxicCryptoEngine.generateRandomHex(6)}",
                    projectId = projectId,
                    label = label,
                    keyPrefix = rawKey.take(14) + "...",
                    keyHash = ToxicCryptoEngine.hmacSha256Hex(rawKey),
                    role = role
                )
                dao.insertApiKey(keyEntity)
                registerRawKeyInSession(keyEntity.id, rawKey)
                val out = JSONObject()
                    .put("keyId", keyEntity.id)
                    .put("label", label)
                    .put("role", role)
                    .put("apiKey", rawKey)
                HttpRouteResult(201, out.toString(), projectId, null, "Generated $role API key")
            }

            else -> HttpRouteResult(404, JSONObject().put("error", "Unknown projects route").toString(), "GLOBAL")
        }
    }

    // =========================================================================
    // NOSQL DOCUMENT DATABASE HANDLERS (/database/:collection, /database/:collection/:document)
    // =========================================================================
    private suspend fun handleDatabaseEndpoint(
        method: String,
        segments: List<String>,
        queryParams: Map<String, String>,
        body: String,
        project: ProjectEntity,
        apiKey: ApiKeyEntity,
        jwtClaims: JwtClaims?
    ): HttpRouteResult {
        val collection = segments.getOrNull(1)
        if (collection.isNullOrBlank()) {
            val cols = dao.getCollectionNames(project.id)
            return HttpRouteResult(
                200,
                JSONObject().put("collections", JSONArray(cols)).toString(),
                project.id,
                jwtClaims?.sub,
                "Listed collections"
            )
        }
        val docIdSegment = segments.getOrNull(2)

        // Check if creating an index on /database/:collection/indexes
        if (method == "POST" && docIdSegment == "indexes") {
            val json = JSONObject(body.ifBlank { "{}" })
            val fieldPath = json.optString("fieldPath", "").trim()
            val sortOrder = json.optString("sortOrder", "ASC").uppercase()
            if (fieldPath.isBlank()) {
                return HttpRouteResult(400, JSONObject().put("error", "fieldPath is required").toString(), project.id)
            }
            val index = CollectionIndexEntity(
                id = "idx_${ToxicCryptoEngine.generateRandomHex(6)}",
                projectId = project.id,
                collectionName = collection,
                fieldPath = fieldPath,
                sortOrder = if (sortOrder == "DESC") "DESC" else "ASC"
            )
            dao.insertIndex(index)
            return HttpRouteResult(
                201,
                JSONObject().put("indexId", index.id).put("collection", collection).put("fieldPath", fieldPath).toString(),
                project.id,
                jwtClaims?.sub,
                "Created index on $collection.$fieldPath"
            )
        }

        return when {
            // CREATE DOCUMENT: POST /database/:collection
            method == "POST" && docIdSegment == null -> {
                if (!evaluateSecurityRule(project, apiKey, jwtClaims, collection, "write", null)) {
                    return HttpRouteResult(
                        403,
                        JSONObject().put("error", "Permission denied by Security Rules for write on '$collection'").toString(),
                        project.id,
                        jwtClaims?.sub,
                        "Rule denied POST /database/$collection"
                    )
                }

                val parsedBody = try {
                    JSONObject(body.ifBlank { "{}" })
                } catch (_: Throwable) {
                    return HttpRouteResult(400, JSONObject().put("error", "Invalid document JSON").toString(), project.id)
                }

                val docId = parsedBody.optString("id", "").takeIf { it.isNotBlank() }
                    ?: ToxicCryptoEngine.generateDocumentId()
                val dataObj = parsedBody.optJSONObject("data") ?: parsedBody
                val now = System.currentTimeMillis()
                val existing = dao.getDocument(project.id, collection, docId)
                val ownerId = existing?.ownerUserId ?: jwtClaims?.sub

                val entity = DocumentEntity(
                    projectId = project.id,
                    collectionName = collection,
                    id = docId,
                    jsonData = dataObj.toString(),
                    ownerUserId = ownerId,
                    version = (existing?.version ?: 0) + 1,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
                dao.upsertDocument(entity)
                _realtimeEvents.tryEmit(RealtimeDatabaseEvent(project.id, collection, docId, "CREATE"))

                HttpRouteResult(
                    201,
                    JSONObject().put("document", documentToJson(entity)).toString(),
                    project.id,
                    jwtClaims?.sub,
                    "Created document $collection/$docId"
                )
            }

            // QUERY / LIST DOCUMENTS WITH FILTERING & PAGINATION: GET /database/:collection
            method == "GET" && docIdSegment == null -> {
                if (!evaluateSecurityRule(project, apiKey, jwtClaims, collection, "read", null)) {
                    return HttpRouteResult(
                        403,
                        JSONObject().put("error", "Permission denied by Security Rules for read on '$collection'").toString(),
                        project.id,
                        jwtClaims?.sub,
                        "Rule denied GET /database/$collection"
                    )
                }

                val allDocs = dao.getDocumentsInCollection(project.id, collection)
                val filterField = queryParams["filterField"]?.trim()
                val filterOp = queryParams["filterOp"]?.trim() ?: "=="
                val filterValue = queryParams["filterValue"]
                val sortBy = queryParams["sortBy"]?.trim()
                val sortOrder = queryParams["sortOrder"]?.uppercase() ?: "DESC"
                val page = (queryParams["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                val limit = (queryParams["limit"]?.toIntOrNull() ?: 25).coerceIn(1, 100)

                val ruleFiltered = allDocs.filter { doc ->
                    evaluateSecurityRule(project, apiKey, jwtClaims, collection, "read", doc.ownerUserId)
                }

                val queryFiltered = if (!filterField.isNullOrBlank() && filterValue != null) {
                    ruleFiltered.filter { doc ->
                        matchesDocumentFilter(doc.jsonData, filterField, filterOp, filterValue)
                    }
                } else {
                    ruleFiltered
                }

                val sorted = if (!sortBy.isNullOrBlank()) {
                    val comparator = Comparator<DocumentEntity> { a, b ->
                        val va = extractFieldComparable(a.jsonData, sortBy)
                        val vb = extractFieldComparable(b.jsonData, sortBy)
                        compareValues(va, vb)
                    }
                    if (sortOrder == "ASC") queryFiltered.sortedWith(comparator)
                    else queryFiltered.sortedWith(comparator.reversed())
                } else {
                    if (sortOrder == "ASC") queryFiltered.sortedBy { it.updatedAt }
                    else queryFiltered.sortedByDescending { it.updatedAt }
                }

                val totalMatches = sorted.size
                val totalPages = ((totalMatches + limit - 1) / limit).coerceAtLeast(1)
                val offset = (page - 1) * limit
                val paged = sorted.drop(offset).take(limit)

                val arr = JSONArray()
                paged.forEach { arr.put(documentToJson(it)) }

                val out = JSONObject()
                    .put("collection", collection)
                    .put("page", page)
                    .put("limit", limit)
                    .put("totalDocuments", totalMatches)
                    .put("totalPages", totalPages)
                    .put("documents", arr)
                HttpRouteResult(
                    200,
                    out.toString(),
                    project.id,
                    jwtClaims?.sub,
                    "Queried $collection (${paged.size}/$totalMatches docs)"
                )
            }

            // READ SINGLE DOCUMENT: GET /database/:collection/:document
            method == "GET" && docIdSegment != null -> {
                val doc = dao.getDocument(project.id, collection, docIdSegment)
                    ?: return HttpRouteResult(404, JSONObject().put("error", "Document not found").toString(), project.id, jwtClaims?.sub)

                if (!evaluateSecurityRule(project, apiKey, jwtClaims, collection, "read", doc.ownerUserId)) {
                    return HttpRouteResult(
                        403,
                        JSONObject().put("error", "Permission denied by Security Rules").toString(),
                        project.id,
                        jwtClaims?.sub,
                        "Rule denied GET /database/$collection/$docIdSegment"
                    )
                }

                HttpRouteResult(
                    200,
                    JSONObject().put("document", documentToJson(doc)).toString(),
                    project.id,
                    jwtClaims?.sub,
                    "Read document $collection/$docIdSegment"
                )
            }

            // UPDATE DOCUMENT: PUT /database/:collection/:document
            method == "PUT" && docIdSegment != null -> {
                val existing = dao.getDocument(project.id, collection, docIdSegment)
                    ?: return HttpRouteResult(404, JSONObject().put("error", "Document not found").toString(), project.id, jwtClaims?.sub)

                if (!evaluateSecurityRule(project, apiKey, jwtClaims, collection, "write", existing.ownerUserId)) {
                    return HttpRouteResult(
                        403,
                        JSONObject().put("error", "Permission denied by Security Rules").toString(),
                        project.id,
                        jwtClaims?.sub,
                        "Rule denied PUT /database/$collection/$docIdSegment"
                    )
                }

                val parsedBody = try {
                    JSONObject(body.ifBlank { "{}" })
                } catch (_: Throwable) {
                    return HttpRouteResult(400, JSONObject().put("error", "Invalid JSON body").toString(), project.id)
                }
                val dataObj = parsedBody.optJSONObject("data") ?: parsedBody
                val merge = parsedBody.optBoolean("merge", false)

                val finalDataJson = if (merge) {
                    val merged = JSONObject(existing.jsonData)
                    val keys = dataObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        merged.put(k, dataObj.get(k))
                    }
                    merged.toString()
                } else {
                    dataObj.toString()
                }

                val updated = existing.copy(
                    jsonData = finalDataJson,
                    version = existing.version + 1,
                    sizeBytes = finalDataJson.toByteArray().size,
                    updatedAt = System.currentTimeMillis()
                )
                dao.upsertDocument(updated)
                _realtimeEvents.tryEmit(RealtimeDatabaseEvent(project.id, collection, docIdSegment, "UPDATE"))

                HttpRouteResult(
                    200,
                    JSONObject().put("document", documentToJson(updated)).toString(),
                    project.id,
                    jwtClaims?.sub,
                    "Updated document $collection/$docIdSegment (v${updated.version})"
                )
            }

            // DELETE DOCUMENT: DELETE /database/:collection/:document
            method == "DELETE" && docIdSegment != null -> {
                val existing = dao.getDocument(project.id, collection, docIdSegment)
                    ?: return HttpRouteResult(404, JSONObject().put("error", "Document not found").toString(), project.id, jwtClaims?.sub)

                if (!evaluateSecurityRule(project, apiKey, jwtClaims, collection, "write", existing.ownerUserId)) {
                    return HttpRouteResult(
                        403,
                        JSONObject().put("error", "Permission denied by Security Rules").toString(),
                        project.id,
                        jwtClaims?.sub,
                        "Rule denied DELETE /database/$collection/$docIdSegment"
                    )
                }

                dao.deleteDocument(project.id, collection, docIdSegment)
                _realtimeEvents.tryEmit(RealtimeDatabaseEvent(project.id, collection, docIdSegment, "DELETE"))

                HttpRouteResult(
                    200,
                    JSONObject().put("deleted", true).put("collection", collection).put("id", docIdSegment).toString(),
                    project.id,
                    jwtClaims?.sub,
                    "Deleted document $collection/$docIdSegment"
                )
            }

            else -> HttpRouteResult(404, JSONObject().put("error", "Unknown database route").toString(), project.id)
        }
    }

    // =========================================================================
    // SECURITY RULES ENGINE
    // =========================================================================
    fun evaluateSecurityRule(
        project: ProjectEntity,
        apiKey: ApiKeyEntity,
        jwtClaims: JwtClaims?,
        collectionName: String,
        operation: String, // "read" or "write"
        resourceOwnerId: String?
    ): Boolean {
        if (apiKey.role == "server_admin") return true
        if (apiKey.role == "readonly" && operation == "write") return false

        var ruleMode = if (operation == "read") project.defaultReadRule else project.defaultWriteRule
        try {
            val colRules = JSONObject(project.collectionRulesJson)
            val specific = colRules.optJSONObject(collectionName)
            if (specific != null) {
                val overrideMode = specific.optString(operation, "")
                if (overrideMode.isNotBlank()) {
                    ruleMode = overrideMode
                }
            }
        } catch (_: Throwable) {
        }

        return when (ruleMode) {
            "public" -> true
            "authenticated" -> jwtClaims != null && jwtClaims.sub.isNotBlank()
            "owner_only" -> {
                if (jwtClaims == null || jwtClaims.sub.isBlank()) false
                else resourceOwnerId == null || resourceOwnerId == jwtClaims.sub
            }
            "admin_only" -> apiKey.role == "server_admin"
            else -> false
        }
    }

    // =========================================================================
    // QUERY FILTERING & HELPERS
    // =========================================================================
    private fun matchesDocumentFilter(
        jsonData: String,
        fieldPath: String,
        op: String,
        targetValue: String
    ): Boolean {
        return try {
            val obj = JSONObject(jsonData)
            if (!obj.has(fieldPath)) return false
            val rawVal = obj.get(fieldPath)
            val docStr = rawVal.toString()
            val docNum = docStr.toDoubleOrNull()
            val targetNum = targetValue.toDoubleOrNull()

            when (op) {
                "==" -> if (docNum != null && targetNum != null) docNum == targetNum else docStr.equals(targetValue, ignoreCase = true)
                "!=" -> if (docNum != null && targetNum != null) docNum != targetNum else !docStr.equals(targetValue, ignoreCase = true)
                ">" -> docNum != null && targetNum != null && docNum > targetNum
                ">=" -> docNum != null && targetNum != null && docNum >= targetNum
                "<" -> docNum != null && targetNum != null && docNum < targetNum
                "<=" -> docNum != null && targetNum != null && docNum <= targetNum
                "contains" -> docStr.contains(targetValue, ignoreCase = true)
                else -> docStr == targetValue
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun extractFieldComparable(jsonData: String, fieldPath: String): String {
        return try {
            val obj = JSONObject(jsonData)
            obj.optString(fieldPath, "")
        } catch (_: Throwable) {
            ""
        }
    }

    private fun checkRateLimit(
        bucketKey: String,
        maxRequests: Int,
        windowMs: Long,
        store: ConcurrentHashMap<String, MutableList<Long>>
    ): Boolean {
        val now = System.currentTimeMillis()
        val list = store.getOrPut(bucketKey) { mutableListOf() }
        synchronized(list) {
            list.removeAll { it < now - windowMs }
            if (list.size >= maxRequests) return false
            list.add(now)
            return true
        }
    }

    private fun userToJson(u: UserEntity): JSONObject = JSONObject()
        .put("id", u.id)
        .put("projectId", u.projectId)
        .put("phoneNumber", u.phoneNumber ?: JSONObject.NULL)
        .put("email", u.email ?: JSONObject.NULL)
        .put("status", u.status)
        .put("createdAt", u.createdAt)
        .put("lastLoginAt", u.lastLoginAt ?: JSONObject.NULL)

    private fun projectToJson(p: ProjectEntity): JSONObject = JSONObject()
        .put("id", p.id)
        .put("name", p.name)
        .put("region", p.region)
        .put("authPhoneEnabled", p.authPhoneEnabled)
        .put("authEmailEnabled", p.authEmailEnabled)
        .put("otpExpirySeconds", p.otpExpirySeconds)
        .put("otpMaxAttempts", p.otpMaxAttempts)
        .put("otpCooldownSeconds", p.otpCooldownSeconds)
        .put("rateLimitPerMinute", p.rateLimitPerMinute)
        .put("defaultReadRule", p.defaultReadRule)
        .put("defaultWriteRule", p.defaultWriteRule)
        .put("collectionRulesJson", p.collectionRulesJson)
        .put("createdAt", p.createdAt)

    private fun documentToJson(d: DocumentEntity): JSONObject = JSONObject()
        .put("id", d.id)
        .put("projectId", d.projectId)
        .put("collection", d.collectionName)
        .put("data", JSONObject(d.jsonData))
        .put("ownerUserId", d.ownerUserId ?: JSONObject.NULL)
        .put("version", d.version)
        .put("sizeBytes", d.sizeBytes)
        .put("createdAt", d.createdAt)
        .put("updatedAt", d.updatedAt)

    private fun parseQueryString(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        query.split("&").forEach { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                val k = URLDecoder.decode(parts[0], "UTF-8")
                val v = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
                map[k] = v
            }
        }
        return map
    }

    private fun writeHttpResponse(out: OutputStream, statusCode: Int, body: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            201 -> "Created"
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            409 -> "Conflict"
            429 -> "Too Many Requests"
            else -> "Server Error"
        }
        val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type, Authorization, X-ToxicBase-Key, X-Api-Key\r\n")
            append("X-Powered-By: TOXICBASE-BaaS/1.0\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        out.write(headers.toByteArray(StandardCharsets.UTF_8))
        if (bodyBytes.isNotEmpty()) {
            out.write(bodyBytes)
        }
        out.flush()
    }

    companion object {
        @Volatile
        private var INSTANCE: ToxicBaseEmbeddedServer? = null

        fun getInstance(context: Context): ToxicBaseEmbeddedServer {
            return INSTANCE ?: synchronized(this) {
                val db = ToxicBaseDatabase.getInstance(context)
                val server = ToxicBaseEmbeddedServer(context.applicationContext, db.dao())
                INSTANCE = server
                server
            }
        }
    }
}

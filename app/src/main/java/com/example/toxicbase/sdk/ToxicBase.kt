package com.example.toxicbase.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ToxicUser(
    val id: String,
    val projectId: String,
    val phoneNumber: String?,
    val email: String?,
    val status: String,
    val createdAt: Long,
    val lastLoginAt: Long?
)

data class ToxicSession(
    val user: ToxicUser,
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Int
)

data class ToxicOtpDispatchResponse(
    val success: Boolean,
    val phoneNumber: String,
    val expiresInSeconds: Int,
    val cooldownSeconds: Int,
    val provider: String,
    val messageSid: String,
    val message: String
)

data class ToxicDocument(
    val id: String,
    val projectId: String,
    val collection: String,
    val data: Map<String, Any?>,
    val rawJson: String,
    val ownerUserId: String?,
    val version: Int,
    val sizeBytes: Int,
    val createdAt: Long,
    val updatedAt: Long
)

data class ToxicQueryPage(
    val collection: String,
    val page: Int,
    val limit: Int,
    val totalDocuments: Int,
    val totalPages: Int,
    val documents: List<ToxicDocument>
)

sealed class ToxicResult<out T> {
    data class Success<T>(val data: T, val statusCode: Int = 200) : ToxicResult<T>()
    data class Error(val message: String, val statusCode: Int = 400, val remainingAttempts: Int? = null) : ToxicResult<Nothing>()
}

/**
 * TOXICBASE Official Android Client SDK.
 * Connects any Android app to a TOXICBASE Backend-as-a-Service instance over HTTP/REST.
 *
 * Usage:
 * ```
 * ToxicBase.initialize(API_KEY)
 * ToxicBase.auth.sendOTP("+14155552671")
 * ToxicBase.auth.verifyOTP("+14155552671", "123456")
 * val user = ToxicBase.auth.currentUser()
 * ToxicBase.database.collection("users").create(mapOf("name" to "Ada"))
 * ```
 */
object ToxicBase {
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var configuredApiKey: String = ""

    @Volatile
    private var configuredBaseUrl: String = "http://127.0.0.1:8765"

    private val _currentSession = MutableStateFlow<ToxicSession?>(null)
    val sessionFlow: StateFlow<ToxicSession?> = _currentSession.asStateFlow()

    val auth: ToxicAuthClient = ToxicAuthClient()
    val database: ToxicDatabaseClient = ToxicDatabaseClient()

    /**
     * Initializes the ToxicBase Android SDK with a Project API Key and optional Backend URL.
     */
    fun initialize(apiKey: String, baseUrl: String = configuredBaseUrl) {
        configuredApiKey = apiKey.trim()
        configuredBaseUrl = baseUrl.trim().trimEnd('/')
    }

    fun setBaseUrl(baseUrl: String) {
        configuredBaseUrl = baseUrl.trim().trimEnd('/')
    }

    fun getApiKey(): String = configuredApiKey
    fun getBaseUrl(): String = configuredBaseUrl

    // =========================================================================
    // AUTHENTICATION CLIENT (ToxicBase.auth)
    // =========================================================================
    class ToxicAuthClient internal constructor() {

        /**
         * Returns the currently authenticated ToxicUser, or null if signed out.
         */
        fun currentUser(): ToxicUser? = _currentSession.value?.user

        fun currentSession(): ToxicSession? = _currentSession.value

        /**
         * Requests a real SMS OTP for the given E.164 phoneNumber via POST /auth/send-otp.
         */
        suspend fun sendOTP(phoneNumber: String): ToxicResult<ToxicOtpDispatchResponse> = withContext(Dispatchers.IO) {
            val payload = JSONObject().put("phoneNumber", phoneNumber.trim())
            when (val res = executeHttp("POST", "/auth/send-otp", payload)) {
                is ToxicResult.Success -> {
                    val json = res.data
                    ToxicResult.Success(
                        ToxicOtpDispatchResponse(
                            success = json.optBoolean("success", true),
                            phoneNumber = json.optString("phoneNumber", phoneNumber),
                            expiresInSeconds = json.optInt("expiresInSeconds", 300),
                            cooldownSeconds = json.optInt("cooldownSeconds", 60),
                            provider = json.optString("provider", "SMS Gateway"),
                            messageSid = json.optString("messageSid", ""),
                            message = json.optString("message", "OTP dispatched")
                        ),
                        res.statusCode
                    )
                }
                is ToxicResult.Error -> res
            }
        }

        /**
         * Verifies an SMS OTP code via POST /auth/verify-otp and establishes a secure JWT session.
         */
        suspend fun verifyOTP(phoneNumber: String, otp: String): ToxicResult<ToxicSession> = withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("phoneNumber", phoneNumber.trim())
                .put("otp", otp.trim())
            when (val res = executeHttp("POST", "/auth/verify-otp", payload)) {
                is ToxicResult.Success -> {
                    val session = parseSessionJson(res.data)
                    _currentSession.value = session
                    ToxicResult.Success(session, res.statusCode)
                }
                is ToxicResult.Error -> res
            }
        }

        /**
         * Registers a new user with email & password via POST /auth/login (mode=register).
         */
        suspend fun register(
            email: String,
            password: String,
            phoneNumber: String? = null
        ): ToxicResult<ToxicSession> = withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("mode", "register")
            if (!phoneNumber.isNullOrBlank()) {
                payload.put("phoneNumber", phoneNumber.trim())
            }
            when (val res = executeHttp("POST", "/auth/login", payload)) {
                is ToxicResult.Success -> {
                    val session = parseSessionJson(res.data)
                    _currentSession.value = session
                    ToxicResult.Success(session, res.statusCode)
                }
                is ToxicResult.Error -> res
            }
        }

        /**
         * Authenticates an existing user with email & password via POST /auth/login.
         */
        suspend fun login(email: String, password: String): ToxicResult<ToxicSession> = withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("mode", "login")
            when (val res = executeHttp("POST", "/auth/login", payload)) {
                is ToxicResult.Success -> {
                    val session = parseSessionJson(res.data)
                    _currentSession.value = session
                    ToxicResult.Success(session, res.statusCode)
                }
                is ToxicResult.Error -> res
            }
        }

        /**
         * Rotates the refresh token and obtains a fresh JWT access token via POST /auth/refresh.
         */
        suspend fun refreshToken(): ToxicResult<ToxicSession> = withContext(Dispatchers.IO) {
            val currentRt = _currentSession.value?.refreshToken
                ?: return@withContext ToxicResult.Error("No active refresh token available", 401)
            val payload = JSONObject().put("refreshToken", currentRt)
            when (val res = executeHttp("POST", "/auth/refresh", payload)) {
                is ToxicResult.Success -> {
                    val session = parseSessionJson(res.data)
                    _currentSession.value = session
                    ToxicResult.Success(session, res.statusCode)
                }
                is ToxicResult.Error -> res
            }
        }

        /**
         * Revokes the current user's session & refresh token on the server via POST /auth/logout.
         */
        suspend fun logout(): ToxicResult<Boolean> = withContext(Dispatchers.IO) {
            val rt = _currentSession.value?.refreshToken ?: ""
            val payload = JSONObject().put("refreshToken", rt)
            val res = executeHttp("POST", "/auth/logout", payload)
            _currentSession.value = null
            when (res) {
                is ToxicResult.Success -> ToxicResult.Success(true, res.statusCode)
                is ToxicResult.Error -> res
            }
        }
    }

    // =========================================================================
    // DATABASE CLIENT (ToxicBase.database)
    // =========================================================================
    class ToxicDatabaseClient internal constructor() {

        /**
         * Returns a fluent reference to a collection: `ToxicBase.database.collection("users")`
         */
        fun collection(name: String): ToxicCollectionRef = ToxicCollectionRef(name.trim())

        /**
         * Creates a document in the specified collection via POST /database/:collection
         */
        suspend fun create(
            collection: String,
            data: Map<String, Any?>,
            documentId: String? = null
        ): ToxicResult<ToxicDocument> = collection(collection).create(data, documentId)

        /**
         * Reads a document by ID via GET /database/:collection/:document
         */
        suspend fun get(
            collection: String,
            documentId: String
        ): ToxicResult<ToxicDocument> = collection(collection).get(documentId)

        /**
         * Updates an existing document via PUT /database/:collection/:document
         */
        suspend fun update(
            collection: String,
            documentId: String,
            data: Map<String, Any?>,
            merge: Boolean = true
        ): ToxicResult<ToxicDocument> = collection(collection).update(documentId, data, merge)

        /**
         * Deletes a document via DELETE /database/:collection/:document
         */
        suspend fun delete(
            collection: String,
            documentId: String
        ): ToxicResult<Boolean> = collection(collection).delete(documentId)
    }

    class ToxicCollectionRef internal constructor(val collectionName: String) {

        suspend fun create(
            data: Map<String, Any?>,
            documentId: String? = null
        ): ToxicResult<ToxicDocument> = withContext(Dispatchers.IO) {
            val payload = JSONObject()
            if (!documentId.isNullOrBlank()) {
                payload.put("id", documentId.trim())
            }
            payload.put("data", mapToJsonObject(data))
            when (val res = executeHttp("POST", "/database/$collectionName", payload)) {
                is ToxicResult.Success -> {
                    val docJson = res.data.getJSONObject("document")
                    ToxicResult.Success(parseDocumentJson(docJson), res.statusCode)
                }
                is ToxicResult.Error -> res
            }
        }

        suspend fun createFromJson(
            rawJsonData: String,
            documentId: String? = null
        ): ToxicResult<ToxicDocument> = withContext(Dispatchers.IO) {
            val dataObj = try {
                JSONObject(rawJsonData)
            } catch (e: Throwable) {
                return@withContext ToxicResult.Error("Invalid JSON document: ${e.message}", 400)
            }
            val payload = JSONObject()
            if (!documentId.isNullOrBlank()) {
                payload.put("id", documentId.trim())
            }
            payload.put("data", dataObj)
            when (val res = executeHttp("POST", "/database/$collectionName", payload)) {
                is ToxicResult.Success -> {
                    val docJson = res.data.getJSONObject("document")
                    ToxicResult.Success(parseDocumentJson(docJson), res.statusCode)
                }
                is ToxicResult.Error -> res
            }
        }

        suspend fun get(documentId: String): ToxicResult<ToxicDocument> = withContext(Dispatchers.IO) {
            when (val res = executeHttp("GET", "/database/$collectionName/${documentId.trim()}", null)) {
                is ToxicResult.Success -> {
                    val docJson = res.data.getJSONObject("document")
                    ToxicResult.Success(parseDocumentJson(docJson), res.statusCode)
                }
                is ToxicResult.Error -> res
            }
        }

        suspend fun update(
            documentId: String,
            data: Map<String, Any?>,
            merge: Boolean = true
        ): ToxicResult<ToxicDocument> = withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("data", mapToJsonObject(data))
                .put("merge", merge)
            when (val res = executeHttp("PUT", "/database/$collectionName/${documentId.trim()}", payload)) {
                is ToxicResult.Success -> {
                    val docJson = res.data.getJSONObject("document")
                    ToxicResult.Success(parseDocumentJson(docJson), res.statusCode)
                }
                is ToxicResult.Error -> res
            }
        }

        suspend fun updateFromJson(
            documentId: String,
            rawJsonData: String,
            merge: Boolean = false
        ): ToxicResult<ToxicDocument> = withContext(Dispatchers.IO) {
            val dataObj = try {
                JSONObject(rawJsonData)
            } catch (e: Throwable) {
                return@withContext ToxicResult.Error("Invalid JSON document: ${e.message}", 400)
            }
            val payload = JSONObject()
                .put("data", dataObj)
                .put("merge", merge)
            when (val res = executeHttp("PUT", "/database/$collectionName/${documentId.trim()}", payload)) {
                is ToxicResult.Success -> {
                    val docJson = res.data.getJSONObject("document")
                    ToxicResult.Success(parseDocumentJson(docJson), res.statusCode)
                }
                is ToxicResult.Error -> res
            }
        }

        suspend fun delete(documentId: String): ToxicResult<Boolean> = withContext(Dispatchers.IO) {
            when (val res = executeHttp("DELETE", "/database/$collectionName/${documentId.trim()}", null)) {
                is ToxicResult.Success -> ToxicResult.Success(true, res.statusCode)
                is ToxicResult.Error -> res
            }
        }

        suspend fun query(
            filterField: String? = null,
            filterOp: String = "==",
            filterValue: String? = null,
            sortBy: String? = null,
            sortOrder: String = "DESC",
            page: Int = 1,
            limit: Int = 25
        ): ToxicResult<ToxicQueryPage> = withContext(Dispatchers.IO) {
            val queryMap = mutableMapOf(
                "page" to page.toString(),
                "limit" to limit.toString(),
                "sortOrder" to sortOrder
            )
            if (!filterField.isNullOrBlank() && filterValue != null) {
                queryMap["filterField"] = filterField
                queryMap["filterOp"] = filterOp
                queryMap["filterValue"] = filterValue
            }
            if (!sortBy.isNullOrBlank()) {
                queryMap["sortBy"] = sortBy
            }

            when (val res = executeHttp("GET", "/database/$collectionName", null, queryMap)) {
                is ToxicResult.Success -> {
                    val json = res.data
                    val arr = json.optJSONArray("documents")
                    val list = mutableListOf<ToxicDocument>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            list.add(parseDocumentJson(arr.getJSONObject(i)))
                        }
                    }
                    ToxicResult.Success(
                        ToxicQueryPage(
                            collection = json.optString("collection", collectionName),
                            page = json.optInt("page", page),
                            limit = json.optInt("limit", limit),
                            totalDocuments = json.optInt("totalDocuments", list.size),
                            totalPages = json.optInt("totalPages", 1),
                            documents = list
                        ),
                        res.statusCode
                    )
                }
                is ToxicResult.Error -> res
            }
        }
    }

    // =========================================================================
    // INTERNAL HTTP TRANSPORT
    // =========================================================================
    internal fun executeHttp(
        method: String,
        endpoint: String,
        bodyJson: JSONObject? = null,
        queryParams: Map<String, String> = emptyMap()
    ): ToxicResult<JSONObject> {
        if (configuredApiKey.isBlank()) {
            return ToxicResult.Error("ToxicBase SDK not initialized: call ToxicBase.initialize(API_KEY) first", 401)
        }
        return try {
            val rawUrl = "$configuredBaseUrl$endpoint"
            val httpUrlBuilder = rawUrl.toHttpUrlOrNull()?.newBuilder()
                ?: return ToxicResult.Error("Invalid ToxicBase Server URL: $rawUrl", 400)

            queryParams.forEach { (k, v) ->
                httpUrlBuilder.addQueryParameter(k, v)
            }

            val reqBuilder = Request.Builder()
                .url(httpUrlBuilder.build())
                .header("X-ToxicBase-Key", configuredApiKey)
                .header("Accept", "application/json")

            val activeToken = _currentSession.value?.accessToken
            if (!activeToken.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $activeToken")
            }

            val requestBody = bodyJson?.toString()?.toRequestBody(JSON_MEDIA)
                ?: "".toRequestBody(JSON_MEDIA)

            when (method.uppercase()) {
                "GET" -> reqBuilder.get()
                "POST" -> reqBuilder.post(requestBody)
                "PUT" -> reqBuilder.put(requestBody)
                "DELETE" -> reqBuilder.delete()
                else -> reqBuilder.method(method.uppercase(), requestBody)
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = if (text.isNotBlank()) JSONObject(text) else JSONObject()
                if (response.isSuccessful) {
                    ToxicResult.Success(json, response.code)
                } else {
                    val errMsg = json.optString("error", "HTTP ${response.code} error")
                    val remAttempts = if (json.has("remainingAttempts")) json.optInt("remainingAttempts") else null
                    ToxicResult.Error(errMsg, response.code, remAttempts)
                }
            }
        } catch (e: Throwable) {
            ToxicResult.Error("Network error communicating with ToxicBase Server: ${e.message}", 503)
        }
    }

    private fun parseSessionJson(json: JSONObject): ToxicSession {
        val uObj = json.getJSONObject("user")
        val user = ToxicUser(
            id = uObj.getString("id"),
            projectId = uObj.getString("projectId"),
            phoneNumber = uObj.optString("phoneNumber").takeIf { it.isNotBlank() && it != "null" },
            email = uObj.optString("email").takeIf { it.isNotBlank() && it != "null" },
            status = uObj.optString("status", "ACTIVE"),
            createdAt = uObj.optLong("createdAt", System.currentTimeMillis()),
            lastLoginAt = if (uObj.has("lastLoginAt") && !uObj.isNull("lastLoginAt")) uObj.optLong("lastLoginAt") else null
        )
        return ToxicSession(
            user = user,
            accessToken = json.getString("accessToken"),
            refreshToken = json.getString("refreshToken"),
            expiresInSeconds = json.optInt("expiresIn", 900)
        )
    }

    private fun parseDocumentJson(docObj: JSONObject): ToxicDocument {
        val dataJsonObj = docObj.optJSONObject("data") ?: JSONObject()
        val dataMap = mutableMapOf<String, Any?>()
        val keys = dataJsonObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = dataJsonObj.get(k)
            dataMap[k] = if (v == JSONObject.NULL) null else v
        }
        return ToxicDocument(
            id = docObj.getString("id"),
            projectId = docObj.getString("projectId"),
            collection = docObj.getString("collection"),
            data = dataMap,
            rawJson = dataJsonObj.toString(2),
            ownerUserId = docObj.optString("ownerUserId").takeIf { it.isNotBlank() && it != "null" },
            version = docObj.optInt("version", 1),
            sizeBytes = docObj.optInt("sizeBytes", 0),
            createdAt = docObj.optLong("createdAt", 0L),
            updatedAt = docObj.optLong("updatedAt", 0L)
        )
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        map.forEach { (k, v) ->
            obj.put(k, v ?: JSONObject.NULL)
        }
        return obj
    }
}

package com.example.toxicbase.server

import android.util.Base64
import com.example.BuildConfig
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class JwtClaims(
    val sub: String,
    val projectId: String,
    val phone: String?,
    val email: String?,
    val iat: Long,
    val exp: Long,
    val jti: String
)

object ToxicCryptoEngine {
    private val secureRandom = SecureRandom()

    private val serverSigningSecret: ByteArray by lazy {
        val raw = try {
            BuildConfig.TOXICBASE_JWT_SECRET.ifBlank {
                "tb_default_hmac_sha256_internal_key_9f8e7d6c5b4a3f2e1d0c"
            }
        } catch (_: Throwable) {
            "tb_default_hmac_sha256_internal_key_9f8e7d6c5b4a3f2e1d0c"
        }
        raw.toByteArray(StandardCharsets.UTF_8)
    }

    /**
     * Generates a cryptographically secure 6-digit OTP using SecureRandom.
     */
    fun generateSecureOtp(): String {
        val code = 100000 + secureRandom.nextInt(900000)
        return code.toString()
    }

    fun generateRandomHex(byteCount: Int = 16): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun generateProjectId(): String = "tb_proj_${generateRandomHex(6)}"

    fun generateUserId(): String = "tb_usr_${generateRandomHex(8)}"

    fun generateDocumentId(): String = "doc_${generateRandomHex(8)}"

    fun generateApiKey(role: String = "client"): String {
        val prefix = when (role) {
            "server_admin" -> "tb_secret_"
            "readonly" -> "tb_ro_"
            else -> "tb_live_"
        }
        return prefix + generateRandomHex(18)
    }

    fun generateRefreshToken(): String = "tb_rt_${generateRandomHex(24)}"

    /**
     * Computes HMAC-SHA256 digest of a value using the internal server secret.
     * Used for API keys, refresh tokens, and salted OTP challenges.
     */
    fun hmacSha256Hex(input: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(serverSigningSecret, "HmacSHA256"))
        val digest = mac.doFinal(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Hashes an OTP with projectId, phoneNumber, and random salt so plaintext OTP is never stored.
     */
    fun hashOtp(projectId: String, phoneNumber: String, salt: String, otp: String): String {
        return hmacSha256Hex("OTP:$projectId:$phoneNumber:$salt:${otp.trim()}")
    }

    /**
     * Constant-time comparison to prevent timing side-channel attacks on OTP and token hashes.
     */
    fun constantTimeEquals(expectedHex: String, actualHex: String): Boolean {
        val a = expectedHex.toByteArray(StandardCharsets.UTF_8)
        val b = actualHex.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(a, b)
    }

    /**
     * PBKDF2WithHmacSHA256 password hashing (65,536 iterations, 256-bit key).
     */
    fun hashPassword(password: String, saltHex: String): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            saltHex.toByteArray(StandardCharsets.UTF_8),
            65536,
            256
        )
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(password: String, saltHex: String, expectedHashHex: String): Boolean {
        val actual = hashPassword(password, saltHex)
        return constantTimeEquals(expectedHashHex, actual)
    }

    /**
     * Signs a real RFC 7519 HS256 JWT Access Token.
     */
    fun signJwtAccessToken(
        userId: String,
        projectId: String,
        phone: String?,
        email: String?,
        ttlSeconds: Long = 900L // 15 minutes
    ): String {
        val nowSec = System.currentTimeMillis() / 1000L
        val expSec = nowSec + ttlSeconds
        val jti = generateRandomHex(8)

        val headerJson = JSONObject().apply {
            put("alg", "HS256")
            put("typ", "JWT")
        }.toString()

        val payloadJson = JSONObject().apply {
            put("iss", "toxicbase-baas")
            put("sub", userId)
            put("projectId", projectId)
            if (!phone.isNullOrBlank()) put("phone", phone)
            if (!email.isNullOrBlank()) put("email", email)
            put("iat", nowSec)
            put("exp", expSec)
            put("jti", jti)
        }.toString()

        val encodedHeader = base64UrlEncode(headerJson.toByteArray(StandardCharsets.UTF_8))
        val encodedPayload = base64UrlEncode(payloadJson.toByteArray(StandardCharsets.UTF_8))
        val signingInput = "$encodedHeader.$encodedPayload"
        val signature = hmacSha256Base64Url(signingInput)
        return "$signingInput.$signature"
    }

    /**
     * Verifies and decodes an HS256 JWT Access Token. Returns null if invalid or expired.
     */
    fun verifyJwtAccessToken(token: String): JwtClaims? {
        return try {
            val parts = token.trim().split(".")
            if (parts.size != 3) return null
            val signingInput = "${parts[0]}.${parts[1]}"
            val expectedSig = hmacSha256Base64Url(signingInput)
            if (!constantTimeEquals(expectedSig, parts[2])) return null

            val payloadStr = String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8)
            val json = JSONObject(payloadStr)
            val exp = json.optLong("exp", 0L)
            val nowSec = System.currentTimeMillis() / 1000L
            if (exp <= nowSec) return null

            JwtClaims(
                sub = json.getString("sub"),
                projectId = json.getString("projectId"),
                phone = json.optString("phone").takeIf { it.isNotBlank() },
                email = json.optString("email").takeIf { it.isNotBlank() },
                iat = json.optLong("iat", nowSec),
                exp = exp,
                jti = json.optString("jti", "")
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun hmacSha256Base64Url(signingInput: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(serverSigningSecret, "HmacSHA256"))
        val sigBytes = mac.doFinal(signingInput.toByteArray(StandardCharsets.UTF_8))
        return base64UrlEncode(sigBytes)
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private fun base64UrlDecode(str: String): ByteArray {
        return Base64.decode(str, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}

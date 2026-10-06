package com.example.toxicbase.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val region: String = "us-east-1",
    val authPhoneEnabled: Boolean = true,
    val authEmailEnabled: Boolean = true,
    val otpExpirySeconds: Int = 300,
    val otpMaxAttempts: Int = 5,
    val otpCooldownSeconds: Int = 60,
    val rateLimitPerMinute: Int = 120,
    val defaultReadRule: String = "authenticated",
    val defaultWriteRule: String = "authenticated",
    val collectionRulesJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "api_keys",
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["keyHash"], unique = true)
    ]
)
data class ApiKeyEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val label: String,
    val keyPrefix: String,
    val keyHash: String,
    val role: String = "client", // "client", "server_admin", "readonly"
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["projectId", "phoneNumber"]),
        Index(value = ["projectId", "email"]),
        Index(value = ["projectId", "createdAt"])
    ]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val phoneNumber: String? = null,
    val email: String? = null,
    val passwordHash: String? = null,
    val passwordSalt: String? = null,
    val status: String = "ACTIVE", // "ACTIVE" or "SUSPENDED"
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long? = null
)

/**
 * Stores hashed OTP challenges with strict TTL and attempt tracking.
 * NEVER stores plaintext OTP codes.
 */
@Entity(
    tableName = "otp_challenges",
    indices = [Index(value = ["projectId", "phoneNumber"], unique = true)]
)
data class OtpChallengeEntity(
    @PrimaryKey val challengeKey: String, // "$projectId:$phoneNumber"
    val projectId: String,
    val phoneNumber: String,
    val otpHash: String,
    val salt: String,
    val attempts: Int = 0,
    val maxAttempts: Int = 5,
    val expiresAt: Long,
    val cooldownUntil: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["projectId", "userId"]),
        Index(value = ["refreshTokenHash"], unique = true)
    ]
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val projectId: String,
    val refreshTokenHash: String,
    val ipAddress: String = "127.0.0.1",
    val userAgent: String = "ToxicBase-Android-SDK/1.0",
    val expiresAt: Long,
    val revoked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "documents",
    primaryKeys = ["projectId", "collectionName", "id"],
    indices = [
        Index(value = ["projectId", "collectionName", "updatedAt"]),
        Index(value = ["projectId", "ownerUserId"])
    ]
)
data class DocumentEntity(
    val projectId: String,
    val collectionName: String,
    val id: String,
    val jsonData: String,
    val ownerUserId: String? = null,
    val version: Int = 1,
    val sizeBytes: Int = jsonData.toByteArray().size,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collection_indexes",
    indices = [Index(value = ["projectId", "collectionName", "fieldPath", "sortOrder"], unique = true)]
)
data class CollectionIndexEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val collectionName: String,
    val fieldPath: String,
    val sortOrder: String = "ASC", // "ASC" or "DESC"
    val status: String = "READY",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "request_logs",
    indices = [Index(value = ["projectId", "createdAt"])]
)
data class RequestLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: String,
    val method: String,
    val path: String,
    val statusCode: Int,
    val latencyMs: Long,
    val ipAddress: String = "127.0.0.1",
    val userId: String? = null,
    val detail: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

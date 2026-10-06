package com.example.toxicbase.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ToxicBaseDao {

    // ---------------- PROJECTS ----------------
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    suspend fun getAllProjects(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: String)

    // ---------------- API KEYS ----------------
    @Query("SELECT * FROM api_keys WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeApiKeys(projectId: String): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys WHERE projectId = :projectId ORDER BY createdAt DESC")
    suspend fun getApiKeysForProject(projectId: String): List<ApiKeyEntity>

    @Query("SELECT * FROM api_keys WHERE keyHash = :keyHash AND isActive = 1 LIMIT 1")
    suspend fun findActiveKeyByHash(keyHash: String): ApiKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApiKey(apiKey: ApiKeyEntity)

    @Query("UPDATE api_keys SET lastUsedAt = :timestamp WHERE id = :keyId")
    suspend fun touchApiKey(keyId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE api_keys SET isActive = :active WHERE id = :keyId AND projectId = :projectId")
    suspend fun setApiKeyActive(projectId: String, keyId: String, active: Boolean)

    @Query("DELETE FROM api_keys WHERE id = :keyId AND projectId = :projectId")
    suspend fun deleteApiKey(projectId: String, keyId: String)

    @Query("DELETE FROM api_keys WHERE projectId = :projectId")
    suspend fun deleteApiKeysByProject(projectId: String)

    // ---------------- USERS ----------------
    @Query("SELECT * FROM users WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeUsers(projectId: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE projectId = :projectId ORDER BY createdAt DESC")
    suspend fun getUsersByProject(projectId: String): List<UserEntity>

    @Query("SELECT * FROM users WHERE projectId = :projectId AND id = :userId LIMIT 1")
    suspend fun getUserById(projectId: String, userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE projectId = :projectId AND phoneNumber = :phone LIMIT 1")
    suspend fun getUserByPhone(projectId: String, phone: String): UserEntity?

    @Query("SELECT * FROM users WHERE projectId = :projectId AND LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmail(projectId: String, email: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users WHERE projectId = :projectId AND id = :userId")
    suspend fun deleteUser(projectId: String, userId: String)

    @Query("DELETE FROM users WHERE projectId = :projectId")
    suspend fun deleteUsersByProject(projectId: String)

    // ---------------- OTP CHALLENGES ----------------
    @Query("SELECT * FROM otp_challenges WHERE challengeKey = :challengeKey LIMIT 1")
    suspend fun getOtpChallenge(challengeKey: String): OtpChallengeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOtpChallenge(challenge: OtpChallengeEntity)

    @Query("UPDATE otp_challenges SET attempts = :attempts WHERE challengeKey = :challengeKey")
    suspend fun updateOtpAttempts(challengeKey: String, attempts: Int)

    @Query("DELETE FROM otp_challenges WHERE challengeKey = :challengeKey")
    suspend fun deleteOtpChallenge(challengeKey: String)

    @Query("DELETE FROM otp_challenges WHERE expiresAt < :now")
    suspend fun purgeExpiredOtpChallenges(now: Long = System.currentTimeMillis())

    // ---------------- SESSIONS ----------------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE refreshTokenHash = :hash AND revoked = 0 LIMIT 1")
    suspend fun getActiveSessionByRefreshHash(hash: String): SessionEntity?

    @Query("UPDATE sessions SET revoked = 1 WHERE refreshTokenHash = :hash")
    suspend fun revokeSessionByRefreshHash(hash: String)

    @Query("UPDATE sessions SET revoked = 1 WHERE projectId = :projectId AND userId = :userId")
    suspend fun revokeAllUserSessions(projectId: String, userId: String)

    @Query("SELECT COUNT(*) FROM sessions WHERE projectId = :projectId AND revoked = 0 AND expiresAt > :now")
    fun observeActiveSessionCount(projectId: String, now: Long = System.currentTimeMillis()): Flow<Int>

    // ---------------- DOCUMENTS (NOSQL STORE) ----------------
    @Query("SELECT * FROM documents WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun observeAllProjectDocuments(projectId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE projectId = :projectId AND collectionName = :collection ORDER BY updatedAt DESC")
    fun observeCollectionDocuments(projectId: String, collection: String): Flow<List<DocumentEntity>>

    @Query("SELECT DISTINCT collectionName FROM documents WHERE projectId = :projectId ORDER BY collectionName ASC")
    fun observeCollectionNames(projectId: String): Flow<List<String>>

    @Query("SELECT DISTINCT collectionName FROM documents WHERE projectId = :projectId ORDER BY collectionName ASC")
    suspend fun getCollectionNames(projectId: String): List<String>

    @Query("SELECT * FROM documents WHERE projectId = :projectId AND collectionName = :collection ORDER BY updatedAt DESC")
    suspend fun getDocumentsInCollection(projectId: String, collection: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE projectId = :projectId AND collectionName = :collection AND id = :docId LIMIT 1")
    suspend fun getDocument(projectId: String, collection: String, docId: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE projectId = :projectId AND collectionName = :collection AND id = :docId")
    suspend fun deleteDocument(projectId: String, collection: String, docId: String): Int

    @Query("DELETE FROM documents WHERE projectId = :projectId AND collectionName = :collection")
    suspend fun deleteCollection(projectId: String, collection: String)

    @Query("DELETE FROM documents WHERE projectId = :projectId")
    suspend fun deleteDocumentsByProject(projectId: String)

    // ---------------- COLLECTION INDEXES ----------------
    @Query("SELECT * FROM collection_indexes WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeIndexes(projectId: String): Flow<List<CollectionIndexEntity>>

    @Query("SELECT * FROM collection_indexes WHERE projectId = :projectId ORDER BY createdAt DESC")
    suspend fun getIndexes(projectId: String): List<CollectionIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIndex(index: CollectionIndexEntity)

    @Query("DELETE FROM collection_indexes WHERE id = :indexId AND projectId = :projectId")
    suspend fun deleteIndex(projectId: String, indexId: String)

    // ---------------- REQUEST LOGS ----------------
    @Query("SELECT * FROM request_logs WHERE projectId = :projectId OR projectId = 'GLOBAL' ORDER BY createdAt DESC LIMIT 250")
    fun observeRequestLogs(projectId: String): Flow<List<RequestLogEntity>>

    @Query("SELECT * FROM request_logs WHERE projectId = :projectId OR projectId = 'GLOBAL' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentLogs(projectId: String, limit: Int = 100): List<RequestLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: RequestLogEntity)

    @Query("DELETE FROM request_logs WHERE projectId = :projectId")
    suspend fun clearLogs(projectId: String)
}

package com.coursework.unifiedmail.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(item: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE id = :id")
    suspend fun getById(id: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAtEpochMillis ASC")
    suspend fun getUnsent(): List<OutboxEntity>

    /** Permanently-failed sends, newest first — see MailRepository.observeFailedOutbox. [getUnsent] still includes these too, so a background flush keeps retrying them on its own. */
    @Query("SELECT * FROM outbox WHERE status = 'FAILED' ORDER BY createdAtEpochMillis DESC")
    fun observeFailed(): Flow<List<OutboxEntity>>

    @Query("UPDATE outbox SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: String, status: OutboxStatus, errorMessage: String?)

    /** Discarding a failed item for good — see MailRepository.discardOutboxItem. */
    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: String)
}

package com.app.assistant.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity): Long

    @Query("SELECT * FROM `groups` ORDER BY group_id DESC")
    suspend fun getAllGroups(): List<GroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessagesByIds(ids: List<Long>)

    @Query("SELECT file_path FROM attachments WHERE message_id IN (SELECT id FROM messages WHERE group_id = :groupId)")
    suspend fun getAttachmentFilePathsForGroup(groupId: Long): List<String>

    @Query("DELETE FROM attachments WHERE message_id IN (SELECT id FROM messages WHERE group_id = :groupId)")
    suspend fun deleteAttachmentsForGroup(groupId: Long)

    @Query("DELETE FROM messages WHERE group_id = :groupId")
    suspend fun deleteMessagesForGroup(groupId: Long)

    @Query("DELETE FROM `groups` WHERE group_id = :groupId")
    suspend fun deleteGroupById(groupId: Long)

    @Query("SELECT * FROM messages WHERE group_id = :groupId ORDER BY id ASC")
    suspend fun getMessagesForGroup(groupId: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE message_id = :messageId ORDER BY id ASC")
    suspend fun getAttachmentsForMessage(messageId: Long): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE message_id = :messageId")
    suspend fun deleteAttachmentsForMessage(messageId: Long)
}

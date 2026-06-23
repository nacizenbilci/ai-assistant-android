package com.app.assistant.db

import android.content.Context
import com.app.assistant.model.Conversation
import com.app.assistant.model.Group
import com.app.assistant.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DynamicConversationRepository(
    context: Context,
) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.conversationDao()
    var currentGroupId: Long = -1L
    private val writeMutex = Mutex()

    private suspend fun startNewChat(msg: String): Long {
        return withContext(Dispatchers.IO) {
            val title = generateChatTitle(msg)
            val group = GroupEntity(title = title, createdAt = System.nanoTime())
            currentGroupId = dao.insertGroup(group)
            currentGroupId
        }
    }

    private fun generateChatTitle(msg: String): String {
        val date = Date()
        val words = msg.split(" ").take(2).joinToString(" ")
        val time = SimpleDateFormat("h:mm aa - EEE, MMM d", Locale.getDefault()).format(date)
        return "$words $time"
    }

    suspend fun addMessage(conversation: Conversation) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                if (currentGroupId == -1L) {
                    currentGroupId = startNewChat(conversation.text)
                }

                val iv = EncryptionUtil.generateIV()
                val (encryptedText, _) = EncryptionUtil.encrypt(conversation.text, iv)

                val messageEntity = MessageEntity(
                    id = conversation.id,
                    text = encryptedText,
                    isMe = conversation.isMe,
                    category = conversation.category,
                    contentURL = conversation.contentURL,
                    navigationURI = conversation.navigationURI,
                    iv = iv,
                    groupId = currentGroupId
                )
                dao.insertMessage(messageEntity)

                // Insert attachments
                conversation.attachments.forEach { attachment ->
                    val attachmentEntity = AttachmentEntity(
                        id = attachment.id,
                        messageId = conversation.id,
                        filePath = attachment.filePath,
                        mimeType = attachment.mimeType,
                        fileName = attachment.fileName,
                        iv = attachment.iv
                    )
                    dao.insertAttachment(attachmentEntity)
                }
            }
        }
    }

    suspend fun deleteMessage(id: Long) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val attachmentEntities = dao.getAttachmentsForMessage(id)
                attachmentEntities.forEach { att ->
                    try {
                        val file = java.io.File(att.filePath)
                        if (file.exists()) {
                            file.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                dao.deleteAttachmentsForMessage(id)
                dao.deleteMessageById(id)
            }
        }
    }

    suspend fun updateMessage(
        oldConversation: Conversation,
        newConversation: Conversation,
    ) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val iv = EncryptionUtil.generateIV()
                val (encryptedText, _) = EncryptionUtil.encrypt(newConversation.text, iv)

                val messageEntity = MessageEntity(
                    id = newConversation.id,
                    text = encryptedText,
                    isMe = newConversation.isMe,
                    category = newConversation.category,
                    contentURL = newConversation.contentURL,
                    navigationURI = newConversation.navigationURI,
                    iv = iv,
                    groupId = currentGroupId
                )
                dao.updateMessage(messageEntity)

                // Delete old attachments first and insert new ones
                dao.deleteAttachmentsForMessage(newConversation.id)
                newConversation.attachments.forEach { attachment ->
                    val attachmentEntity = AttachmentEntity(
                        id = attachment.id,
                        messageId = newConversation.id,
                        filePath = attachment.filePath,
                        mimeType = attachment.mimeType,
                        fileName = attachment.fileName,
                        iv = attachment.iv
                    )
                    dao.insertAttachment(attachmentEntity)
                }
            }
        }
    }

    suspend fun clearMessages(conversations: List<Conversation>) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                conversations.forEach { conversation ->
                    val attachmentEntities = dao.getAttachmentsForMessage(conversation.id)
                    attachmentEntities.forEach { att ->
                        try {
                            val file = java.io.File(att.filePath)
                            if (file.exists()) {
                                file.delete()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    dao.deleteAttachmentsForMessage(conversation.id)
                }
                val ids = conversations.map { it.id }
                dao.deleteMessagesByIds(ids)
                dao.deleteGroupById(currentGroupId)
                currentGroupId = -1L
            }
        }
    }

    suspend fun deleteGroup(groupId: Long) {
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                val filePaths = dao.getAttachmentFilePathsForGroup(groupId)
                filePaths.forEach { filePath ->
                    try {
                        val file = java.io.File(filePath)
                        if (file.exists()) {
                            file.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                dao.deleteAttachmentsForGroup(groupId)
                dao.deleteMessagesForGroup(groupId)
                dao.deleteGroupById(groupId)
            }
        }
    }

    suspend fun loadAllGroups(): MutableList<Group> {
        return withContext(Dispatchers.IO) {
            dao.getAllGroups().map { entity ->
                Group(groupId = entity.groupId, title = entity.title)
            }.toMutableList()
        }
    }

    suspend fun loadMessagesForGroup(groupId: Long): MutableList<Conversation> {
        return withContext(Dispatchers.IO) {
            dao.getMessagesForGroup(groupId).map { entity ->
                val decryptedText = EncryptionUtil.decrypt(entity.text, entity.iv)
                val attachmentEntities = dao.getAttachmentsForMessage(entity.id)
                val attachments = attachmentEntities.map { attEntity ->
                    Attachment(
                        id = attEntity.id,
                        filePath = attEntity.filePath,
                        mimeType = attEntity.mimeType,
                        fileName = attEntity.fileName,
                        iv = attEntity.iv
                    )
                }

                Conversation(
                    id = entity.id,
                    text = decryptedText,
                    isMe = entity.isMe,
                    isLoading = false,
                    category = entity.category,
                    contentURL = entity.contentURL,
                    navigationURI = entity.navigationURI,
                    attachments = attachments
                )
            }.toMutableList()
        }
    }
}

package com.app.assistant.db

import android.content.Context
import com.app.assistant.model.Conversation
import com.app.assistant.model.Group
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DynamicConversationRepository(
    context: Context,
) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.conversationDao()
    var currentGroupId: Long = -1L

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
        withContext(Dispatchers.IO) {
            if (currentGroupId == -1L) {
                currentGroupId = startNewChat(conversation.englishText.takeIf { it.isNotEmpty() } ?: conversation.translatedText)
            }

            val iv = EncryptionUtil.generateIV()
            val (encryptedEnglish, _) = EncryptionUtil.encrypt(conversation.englishText, iv)
            val (encryptedTranslated, _) = EncryptionUtil.encrypt(conversation.translatedText, iv)

            val messageEntity = MessageEntity(
                id = conversation.id,
                englishText = encryptedEnglish,
                translatedText = encryptedTranslated,
                isMe = conversation.isMe,
                category = conversation.category,
                contentURL = conversation.contentURL,
                navigationURI = conversation.navigationURI,
                iv = iv,
                groupId = currentGroupId
            )
            dao.insertMessage(messageEntity)
        }
    }

    suspend fun deleteMessage(id: Long) {
        withContext(Dispatchers.IO) {
            dao.deleteMessageById(id)
        }
    }

    suspend fun updateMessage(
        oldConversation: Conversation,
        newConversation: Conversation,
    ) {
        withContext(Dispatchers.IO) {
            val iv = EncryptionUtil.generateIV()
            val (encryptedEnglish, _) = EncryptionUtil.encrypt(newConversation.englishText, iv)
            val (encryptedTranslated, _) = EncryptionUtil.encrypt(newConversation.translatedText, iv)

            val messageEntity = MessageEntity(
                id = newConversation.id,
                englishText = encryptedEnglish,
                translatedText = encryptedTranslated,
                isMe = newConversation.isMe,
                category = newConversation.category,
                contentURL = newConversation.contentURL,
                navigationURI = newConversation.navigationURI,
                iv = iv,
                groupId = currentGroupId
            )
            dao.updateMessage(messageEntity)
        }
    }

    suspend fun clearMessages(conversations: List<Conversation>) {
        withContext(Dispatchers.IO) {
            val ids = conversations.map { it.id }
            dao.deleteMessagesByIds(ids)
            dao.deleteGroupById(currentGroupId)
            currentGroupId = -1L
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
                val decryptedEnglish = EncryptionUtil.decrypt(entity.englishText, entity.iv)
                val decryptedTranslated = EncryptionUtil.decrypt(entity.translatedText, entity.iv)

                Conversation(
                    id = entity.id,
                    englishText = decryptedEnglish,
                    translatedText = decryptedTranslated,
                    isMe = entity.isMe,
                    isLoading = false,
                    category = entity.category,
                    contentURL = entity.contentURL,
                    navigationURI = entity.navigationURI
                )
            }.toMutableList()
        }
    }
}

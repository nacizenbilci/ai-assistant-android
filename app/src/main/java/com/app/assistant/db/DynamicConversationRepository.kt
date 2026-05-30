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
            val messageEntity = MessageEntity(
                id = conversation.id,
                englishText = conversation.englishText,
                translatedText = conversation.translatedText,
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
            val messageEntity = MessageEntity(
                id = newConversation.id,
                englishText = newConversation.englishText,
                translatedText = newConversation.translatedText,
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
                Conversation(
                    id = entity.id,
                    englishText = entity.englishText,
                    translatedText = entity.translatedText,
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

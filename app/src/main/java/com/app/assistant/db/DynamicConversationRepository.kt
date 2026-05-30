package com.app.assistant.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.app.assistant.model.Conversation
import com.app.assistant.model.Group
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

class DynamicConversationRepository(
    private val context: Context,
) {
    companion object {
        private const val TABLE_GROUPS = "groups"
        private const val COLUMN_GROUP_ID = "group_id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_CREATED_AT = "created_at"
        private const val TABLE_MESSAGES = "messages"
        private const val COLUMN_MESSAGE_ID = "id"
    }

    private val dbHelper = DynamicConversationDbHelper(context)
    var currentGroupId: Long = -1L

    private suspend fun startNewChat(msg: String): Long {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            val contentValues =
                ContentValues().apply {
                    put(COLUMN_TITLE, generateChatTitle(msg))
                    put(COLUMN_CREATED_AT, System.nanoTime())
                }
            currentGroupId = db.insert(TABLE_GROUPS, null, contentValues)
        }
        return currentGroupId
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

            val db = dbHelper.writableDatabase
            val (encryptedMap, iv) = encryptConversation(conversation)
            val values =
                ContentValues().apply {
                    encryptedMap.forEach { (key, value) -> put(key, value) }
                    put("iv", iv)
                    put(COLUMN_GROUP_ID, currentGroupId)
                }
            try {
                db.insert(TABLE_MESSAGES, null, values)
            } catch (e: Exception) {
                e.message?.let { Log.d("Test", it) }
            }
        }
    }

    suspend fun deleteMessage(id: Long) {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            db.delete(
                TABLE_MESSAGES,
                "id = ?",
                arrayOf(id.toString()),
            )
        }
    }

    suspend fun updateMessage(
        oldConversation: Conversation,
        newConversation: Conversation,
    ) {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            val (encryptedMap, iv) = encryptConversation(newConversation)
            val values =
                ContentValues().apply {
                    encryptedMap.forEach { (key, value) -> put(key, value) }
                    put("iv", iv)
                }
            db.update(
                TABLE_MESSAGES,
                values,
                "id = ?",
                arrayOf(oldConversation.id.toString()),
            )
        }
    }

    suspend fun clearMessages(conversations: List<Conversation>) {
        withContext(Dispatchers.IO) {
            val db = dbHelper.writableDatabase
            val ids = conversations.joinToString(",") { it.id.toString() }
            db.execSQL("DELETE FROM $TABLE_MESSAGES WHERE id IN ($ids)")
            db.execSQL("DELETE FROM $TABLE_GROUPS WHERE $COLUMN_GROUP_ID == $currentGroupId")
            currentGroupId = -1L
        }
    }

    suspend fun loadAllGroups(): MutableList<Group> {
        val groupList = mutableListOf<Group>()
        withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            val cursor: Cursor =
                db.query(
                    TABLE_GROUPS,
                    arrayOf(COLUMN_GROUP_ID, COLUMN_TITLE),
                    null,
                    null,
                    null,
                    null,
                    "$COLUMN_GROUP_ID DESC", // Sort by latest created
                )

            while (cursor.moveToNext()) {
                val groupId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_GROUP_ID))
                val title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE))
                groupList.add(Group(groupId, title))
            }
            cursor.close()
        }
        return groupList
    }

    suspend fun loadMessagesForGroup(groupId: Long): MutableList<Conversation> {
        val tempList = mutableListOf<Conversation>()
        withContext(Dispatchers.IO) {
            val db = dbHelper.readableDatabase
            val cursor: Cursor =
                db.query(
                    TABLE_MESSAGES,
                    null,
                    "$COLUMN_GROUP_ID = ?",
                    arrayOf(groupId.toString()),
                    null,
                    null,
                    COLUMN_MESSAGE_ID, // Sort by latest created
                )

            while (cursor.moveToNext()) {
                val conversation = cursorToConversation(cursor)
                tempList.add(conversation)
            }
            cursor.close()
        }
        return tempList
    }

    private fun cursorToConversation(cursor: Cursor): Conversation {
        val properties = Conversation::class.memberProperties
        val fieldMap = mutableMapOf<String, Any?>()
        val iv = cursor.getString(cursor.getColumnIndexOrThrow("iv"))

        properties.filter { it.name != "isLoading" }.forEach { property ->
            val columnName = property.name
            val columnIndex = cursor.getColumnIndexOrThrow(columnName)
            val value =
                when (property.returnType.toString()) {
                    "kotlin.String" -> cursor.getString(columnIndex)

                    // ?.let { EncryptionUtil.decrypt(it, iv) }

                    "kotlin.Int" -> cursor.getInt(columnIndex)

                    "kotlin.Long" -> cursor.getLong(columnIndex)

                    "kotlin.Boolean" -> cursor.getString(columnIndex).toBooleanStrictOrNull() ?: false

                    "java.net.URI" -> URI(cursor.getString(columnIndex))

                    else -> null
                }
            fieldMap[columnName] = value
        }

        return Conversation::class.constructors.first().callBy(
            Conversation::class
                .primaryConstructor!!
                .parameters
                .filter { it.name != "isLoading" } // Ignore "isLoading"
                .associateWith { parameter -> fieldMap[parameter.name] },
        )
    }

    private fun encryptConversation(conversation: Conversation): Pair<Map<String, String>, String> {
        val properties = Conversation::class.memberProperties
        val encryptedMap = mutableMapOf<String, String>()
        val iv = EncryptionUtil.generateIV()

        properties.forEach { property ->
            val key = property.name
            val value = property.getter.call(conversation)?.toString()
            if (value != null && key != "isLoading") {
                // encryptedMap[key] = EncryptionUtil.encrypt(value, iv).first
                encryptedMap[key] = value
            }
        }
        return Pair(encryptedMap, iv)
    }
}

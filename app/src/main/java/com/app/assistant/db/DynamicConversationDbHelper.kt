package com.app.assistant.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.app.assistant.model.Conversation
import kotlin.reflect.full.memberProperties

class DynamicConversationDbHelper(
    context: Context,
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        private const val DATABASE_NAME = "conversation.db"
        private const val DATABASE_VERSION = 1

        private const val COLUMN_IV = "iv"

        // Groups Table
        private const val TABLE_GROUPS = "`groups`"
        private const val COLUMN_GROUP_ID = "group_id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_CREATED_AT = "created_at"

        // Messages Table
        private const val TABLE_MESSAGES = "messages"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createGroupsTableQuery =
            """
            CREATE TABLE $TABLE_GROUPS (
                $COLUMN_GROUP_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TITLE TEXT,
                $COLUMN_CREATED_AT INTEGER
            )
            """.trimIndent()
        db?.execSQL(createGroupsTableQuery)

        val createMessageTableQuery = buildCreateTableQuery()
        db?.execSQL(createMessageTableQuery)
    }

    override fun onUpgrade(
        db: SQLiteDatabase?,
        oldVersion: Int,
        newVersion: Int,
    ) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_GROUPS")
        onCreate(db)
    }

    private fun buildCreateTableQuery(): String {
        val fields = Conversation::class.memberProperties
        val columns =
            fields.filterNot { it.name == "isLoading" }.joinToString(", ") { field ->
                val fieldType = mapFieldTypeToSQLiteType(field.returnType.toString())
                if (field.name == "id") {
                    "${field.name} INTEGER PRIMARY KEY"
                } else {
                    "${field.name} $fieldType"
                }
            }

        return """
            CREATE TABLE $TABLE_MESSAGES (
                $columns,
                iv TEXT,
                $COLUMN_GROUP_ID INTEGER,
                FOREIGN KEY ($COLUMN_GROUP_ID) REFERENCES $TABLE_GROUPS($COLUMN_GROUP_ID)
            )
            """.trimIndent()
    }

    private fun mapFieldTypeToSQLiteType(kotlinType: String): String =
        when (kotlinType) {
            "kotlin.String" -> "TEXT"
            "kotlin.Int", "kotlin.Long", "kotlin.Boolean" -> "INTEGER"
            "java.net.URI" -> "TEXT"
            else -> "TEXT"
        }
}

package com.app.assistant.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.net.URI

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["group_id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["group_id"])]
)
data class MessageEntity(
    @PrimaryKey
    val id: Long,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "isMe")
    val isMe: Boolean,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "contentURL")
    val contentURL: String,

    @ColumnInfo(name = "navigationURI")
    val navigationURI: URI,

    @ColumnInfo(name = "iv")
    val iv: String,

    @ColumnInfo(name = "group_id")
    val groupId: Long
)

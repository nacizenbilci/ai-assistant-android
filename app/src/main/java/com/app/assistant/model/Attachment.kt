package com.app.assistant.model

import com.app.assistant.util.IdGenerator
import java.io.Serializable

data class Attachment(
    val id: Long = IdGenerator.nextId(),
    val filePath: String,
    val mimeType: String,
    val fileName: String,
    val iv: String
) : Serializable

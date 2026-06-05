package com.app.assistant.model

import java.io.Serializable

data class Attachment(
    val id: Long = System.currentTimeMillis() * 1_000_000 + (System.nanoTime() % 1_000_000),
    val filePath: String,
    val mimeType: String,
    val fileName: String,
    val iv: String
) : Serializable

package com.app.assistant.model
import com.app.assistant.util.Category
import java.net.URI

data class Conversation(
    var id: Long = System.currentTimeMillis() * 1_000_000 + (System.nanoTime() % 1_000_000),
    var text: String,
    val isMe: Boolean,
    val isLoading: Boolean = false,
    var category: String = Category.OTHER.name,
    val contentURL: String = "",
    val navigationURI: URI = URI(""),
    val isStreaming: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
) {
    fun getThinkingProcess(): String? {
        val start = text.indexOf("<think>")
        val end = text.indexOf("</think>")
        if (start == -1) return null
        return if (end == -1) {
            text.substring(start + 7)
        } else {
            text.substring(start + 7, end)
        }
    }

    fun getActualAnswer(): String {
        val end = text.indexOf("</think>")
        val mainText = if (end == -1) {
            if (text.contains("<think>")) "" else text
        } else {
            text.substring(end + 8)
        }
        if (isMe) {
            val fileIndex = mainText.indexOf("\n\n[Attached File:")
            if (fileIndex != -1) {
                return mainText.substring(0, fileIndex)
            }
        }
        return mainText
    }
}

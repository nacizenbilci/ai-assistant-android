package com.app.assistant.util

object Constants {
    const val MAIN_CONTEXT =
        "You are my smart assistant who answers just like normal human response, not too long. Don't refer yourself as an AI."
    const val HANDS_FREE_MODE_IMAGE_CONTEXT =
        "You are my smart assistant who answers just like normal human response, not too long. Don't refer yourself as an AI and don't mention words like image, photo, picture, screen in your response. "
    val CATEGORY_CONTEXT =
        "You are a assistant who can classify commands based on previous conversation, from these categories. " +
            enumValues<Category>().joinToString(",") { it.name } +
            ". Just say the category name."
}

package com.app.assistant.usecase

interface ResourceProvider {
    fun getString(key: String): String
}

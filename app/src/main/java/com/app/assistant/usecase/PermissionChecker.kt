package com.app.assistant.usecase

interface PermissionChecker {
    fun hasPermission(permission: String): Boolean
}

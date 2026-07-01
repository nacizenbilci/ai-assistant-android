package com.app.assistant.usecase

import com.app.assistant.model.DeviceAction
import com.app.assistant.repository.ContactsRepository
import java.net.URI

class CallContactUseCase(
    private val permissionChecker: PermissionChecker,
    private val contactsRepository: ContactsRepository
) {
    suspend fun execute(
        prompt: String,
        onPermissionRequest: suspend (Array<String>) -> Unit,
        onIntentTriggered: suspend (DeviceAction) -> Unit,
        onSuccess: suspend (name: String, dialUri: URI) -> Unit,
        onFailure: suspend (errorMsg: String) -> Unit
    ) {
        val requiredPermissions = arrayOf(
            "android.permission.READ_CONTACTS",
            "android.permission.CALL_PHONE"
        ).filter {
            !permissionChecker.hasPermission(it)
        }

        if (requiredPermissions.isNotEmpty()) {
            onPermissionRequest(requiredPermissions.toTypedArray())
            return
        }

        val bestMatch = contactsRepository.searchContact(prompt)
        if (bestMatch != null) {
            try {
                val callAction = DeviceAction.MakeCall(bestMatch.phoneNumber)
                onIntentTriggered(callAction)
                onSuccess(bestMatch.name, URI("tel:${bestMatch.phoneNumber.replace(" ", "")}"))
            } catch (e: Exception) {
                System.err.println("Error making call: ${e.message}")
                onFailure("Sorry, failed to make call. Please try again.")
            }
        } else {
            onFailure("I cannot find such contact, please try again.")
        }
    }
}

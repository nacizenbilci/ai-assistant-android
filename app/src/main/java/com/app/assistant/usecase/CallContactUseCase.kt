package com.app.assistant.usecase

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.app.assistant.repository.ContactsRepository
import com.app.assistant.viewmodel.UIEvent
import java.net.URI

class CallContactUseCase(
    private val context: Context,
    private val contactsRepository: ContactsRepository
) {
    suspend fun execute(
        prompt: String,
        onPermissionRequest: suspend (Array<String>) -> Unit,
        onIntentTriggered: suspend (Intent) -> Unit,
        onSuccess: suspend (name: String, dialUri: URI) -> Unit,
        onFailure: suspend (errorMsg: String) -> Unit
    ) {
        val requiredPermissions = arrayOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        ).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (requiredPermissions.isNotEmpty()) {
            onPermissionRequest(requiredPermissions.toTypedArray())
            return
        }

        val bestMatch = contactsRepository.searchContact(prompt)
        if (bestMatch != null) {
            try {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    data = Uri.parse("tel:${bestMatch.phoneNumber}")
                }
                onIntentTriggered(callIntent)
                onSuccess(bestMatch.name, URI("tel:${bestMatch.phoneNumber.replace(" ", "")}"))
            } catch (e: Exception) {
                Log.e("CallContactUseCase", "Error making call:", e)
                onFailure("Sorry, failed to make call. Please try again.")
            }
        } else {
            onFailure("I cannot find such contact, please try again.")
        }
    }
}

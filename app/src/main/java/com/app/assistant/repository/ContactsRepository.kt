package com.app.assistant.repository

import android.annotation.SuppressLint
import android.content.Context
import android.provider.ContactsContract
import com.app.assistant.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

class ContactsRepository(
    private val context: Context,
) {
    @SuppressLint("Range")
    suspend fun searchContact(prompt: String): Contact? {
        val contentResolver = context.contentResolver
        val contactList = mutableListOf<Contact>()

        withContext(Dispatchers.IO) {
            val cursor =
                contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Data.CONTACT_ID,
                        ContactsContract.Data.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ),
                    "${ContactsContract.Data.MIMETYPE} =? AND ${ContactsContract.Data.CONTACT_ID} IS NOT NULL",
                    arrayOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
                    null,
                )

            cursor?.use { contactCursor ->
                while (contactCursor.moveToNext()) {
                    val contactId = contactCursor.getString(contactCursor.getColumnIndex(ContactsContract.Data.CONTACT_ID))
                    val name = contactCursor.getString(contactCursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME))
                    val phoneNumber = contactCursor.getString(contactCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    contactList.add(Contact(contactId, name, phoneNumber))
                }
            }
        }

        val contactName = cleanAndExtractName(prompt)
        return findBestMatch(contactName, contactList)
    }

    private fun cleanAndExtractName(command: String): String {
        val stopWords = setOf("can", "you", "make", "call", "dial", "number", "to", "please", "the", "a", "on")
        val words = command.lowercase().trim().split(" ")
        return words.filter { !stopWords.contains(it) }.joinToString(" ")
    }

    private fun findBestMatch(
        inputString: String,
        nameList: List<Contact>,
        similarityThreshold: Double = 0.7,
    ): Contact? {
        if (inputString.isEmpty()) {
            return null
        }
        val inputLower = inputString.lowercase().trim()
        var exactMatch: Contact? = null
        var bestSubstringMatch: Contact? = null
        var bestFuzzyMatch: Contact? = null
        var bestFuzzyScore = 0.0

        for (contact in nameList) {
            val nameLower = contact.name.lowercase().trim()

            // Check for exact match
            if (inputLower == nameLower) {
                return contact
            }

            // Check for exact match within tokens
            val tokens = nameLower.split(" ")
            for (token in tokens) {
                when {
                    inputLower == token -> {
                        exactMatch = contact
                    }

                    token.contains(inputLower) -> {
                        bestSubstringMatch = contact
                    }

                    else -> {
                        val similarityScore = stringSimilarity(inputLower, token)
                        if (similarityScore > bestFuzzyScore) {
                            bestFuzzyScore = similarityScore
                            bestFuzzyMatch = contact
                        }
                    }
                }
            }
        }

        return when {
            exactMatch != null -> exactMatch
            bestSubstringMatch != null -> bestSubstringMatch
            bestFuzzyScore >= similarityThreshold -> bestFuzzyMatch
            else -> null
        }
    }

    private fun stringSimilarity(
        a: String,
        b: String,
    ): Double {
        val matcher = a.commonPrefixWith(b).length.toDouble() / max(a.length, b.length).toDouble()
        return matcher
    }
}

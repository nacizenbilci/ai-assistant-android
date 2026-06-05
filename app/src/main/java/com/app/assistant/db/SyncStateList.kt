package com.app.assistant.db

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.app.assistant.model.Conversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SyncStateList(
    private val repository: DynamicConversationRepository, // Repository to handle DB operations
    private val scope: CoroutineScope,
    private val stateList: SnapshotStateList<Conversation> = mutableStateListOf(),
) : MutableList<Conversation> by stateList {
    override fun add(element: Conversation): Boolean {
        val result = stateList.add(element)
        if (result && !element.isLoading && !element.isStreaming) {
            scope.launch(Dispatchers.IO) {
                repository.addMessage(element)
            }
        }
        return result
    }

    override fun removeAt(index: Int): Conversation {
        val result = stateList.removeAt(index)
        scope.launch(Dispatchers.IO) {
            repository.deleteMessage(result.id)
        }
        return result
    }

    override fun set(
        index: Int,
        element: Conversation,
    ): Conversation {
        val oldElement = stateList[index]
        stateList[index] = element
        if (!element.isLoading && !element.isStreaming) {
            scope.launch(Dispatchers.IO) {
                if (oldElement.isLoading || oldElement.isStreaming) {
                    repository.addMessage(element)
                } else {
                    repository.updateMessage(oldElement, element)
                }
            }
        }
        return oldElement
    }

    fun clearAll(): Job {
        val oldList = stateList.toList()
        stateList.clear()
        return scope.launch(Dispatchers.IO) {
            repository.clearMessages(oldList) // Suspends until DB operations finish
        }
    }
}

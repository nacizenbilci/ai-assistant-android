package com.app.assistant.util

import java.util.concurrent.atomic.AtomicLong

object IdGenerator {
    private val lastId = AtomicLong(0L)

    fun nextId(): Long {
        while (true) {
            val now = System.currentTimeMillis()
            val last = lastId.get()
            val next = if (now > last) now else last + 1
            if (lastId.compareAndSet(last, next)) {
                return next
            }
        }
    }
}

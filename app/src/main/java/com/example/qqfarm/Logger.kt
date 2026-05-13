package com.example.qqfarm

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private val lines = ArrayDeque<String>()
    private const val MAX_LINES = 50
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    var onUpdate: ((String) -> Unit)? = null

    fun log(message: String) {
        val entry = "[${fmt.format(Date())}] $message"
        lines.addFirst(entry)
        if (lines.size > MAX_LINES) lines.removeLast()
        onUpdate?.invoke(lines.joinToString("\n"))
    }

    fun clear() {
        lines.clear()
        onUpdate?.invoke("")
    }
}

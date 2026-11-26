package com.gizwits.smart.app.core.setup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Bounded, in-memory operation history. Callers pass labels, never credentials or raw QR payloads. */
object DemoEvents {
    data class Event(val time: Long, val title: String, val detail: String, val failed: Boolean)
    private val history = MutableStateFlow<List<Event>>(emptyList())
    val events = history.asStateFlow()
    fun record(title: String, detail: String, failed: Boolean = false) {
        history.update { (listOf(Event(System.currentTimeMillis(), title, detail, failed)) + it).take(100) }
    }
    fun clear() { history.value = emptyList() }
}

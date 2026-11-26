package com.gizwits.smart.app.core.setup

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/** Session-only last readings, separated by device and transport. Never derived from a write request. */
object DeviceSnapshots {
    private val values = linkedMapOf<String, Map<String, JsonElement>>()
    @Synchronized fun read(key: String): Map<String, JsonElement> = values[key]?.toMap() ?: emptyMap()
    @Synchronized fun write(key: String, value: Map<String, JsonElement>) {
        values[key] = value.toMap()
        while (values.size > 100) values.remove(values.keys.first())
    }
    @Synchronized fun clear() = values.clear()
}

fun mergeDeviceValues(previous: Map<String, JsonElement>, report: JsonObject): Map<String, JsonElement> {
    val next = previous.toMutableMap()
    listOf("data", "alerts", "faults").forEach { group ->
        report.get(group)?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { (name, value) ->
            when {
                value.isJsonArray -> {
                    val bytes = value.asJsonArray
                    if (bytes.all { it.isJsonPrimitive && it.asJsonPrimitive.isNumber })
                        next[name] = JsonPrimitive(bytes.joinToString("") { "%02X".format(it.asInt and 0xff) })
                }
                value.isJsonPrimitive -> next[name] = value.deepCopy()
            }
        }
    }
    return next
}

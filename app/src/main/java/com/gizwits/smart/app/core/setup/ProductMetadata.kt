package com.gizwits.smart.app.core.setup

import com.google.gson.JsonParser

/** The public /app/datapoint response contains a product name as well as its schema. */
object ProductMetadata {
    fun name(raw: String?): String? = try {
        val value = raw?.let { JsonParser.parseString(it) }?.takeIf { it.isJsonObject }?.asJsonObject
        val error = value?.get("error_code")
        val name = value?.get("name")
        if ((error != null && error.asInt != 0) || name == null || !name.isJsonPrimitive || !name.asJsonPrimitive.isString) null
        else name.asString.trim().takeIf { it.isNotEmpty() }
    } catch (_: Exception) { null }
}

package com.gizwits.smart.app.feature.control.data

import com.gizwits.smart.sdk.common.DataPointAttribute
import com.gizwits.smart.sdk.common.DataPointStatusType
import com.gizwits.smart.sdk.extend.hexToByteArray
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlin.math.roundToLong

abstract class DataPointState<T>(
    private val attribute: DataPointAttribute,
    val isEnabled: StateFlow<Boolean>,
    private val dataPoint: SharedFlow<Map<String, JsonElement>>,
    private val onDataPointChange: (Pair<String, JsonElement>) -> Unit
) {

    val id: Int = attribute.id

    val name: String = attribute.name

    val displayName: String = attribute.displayName

    val description: String = attribute.description

    val dataStatusType: DataPointStatusType = attribute.dataStatusType

    val value: Flow<T> = dataPoint.mapNotNull {
        runCatching { toValue(it[attribute.name] ?: return@mapNotNull null) }.getOrNull()
    }

    fun onValueChange(value: T) {
        if (!isEnabled.value || dataStatusType != DataPointStatusType.WRITABLE) return
        try {
            onDataPointChange(attribute.name to fromValue(value))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    abstract fun toValue(value: JsonElement): T

    abstract fun fromValue(value: T): JsonElement

}

class BooleanDataPointState(
    attribute: DataPointAttribute,
    isEnabled: StateFlow<Boolean>,
    dataPoint: SharedFlow<Map<String, JsonElement>>,
    onDataPointChange: (Pair<String, JsonElement>) -> Unit
) : DataPointState<Boolean>(attribute, isEnabled, dataPoint, onDataPointChange) {

    override fun toValue(value: JsonElement): Boolean = value.asBoolean

    override fun fromValue(value: Boolean): JsonElement = JsonPrimitive(value)

}

class NumberDataPointState(
    attribute: DataPointAttribute,
    isEnabled: StateFlow<Boolean>,
    dataPoint: SharedFlow<Map<String, JsonElement>>,
    onDataPointChange: (Pair<String, JsonElement>) -> Unit
) : DataPointState<Float>(attribute, isEnabled, dataPoint, onDataPointChange) {

    private val minCount: Double = attribute.intSpecification.minCount

    private val maxCount: Double = attribute.intSpecification.maxCount

    private val ratio: Double = attribute.intSpecification.ratio

    private val addition: Double = attribute.intSpecification.addition

    val minValue: Double = minCount * ratio + addition

    val maxValue: Double = maxCount * ratio + addition

    override fun toValue(value: JsonElement): Float {
        return if (maxValue == minValue) 0f else ((value.asDouble - minValue) / (maxValue - minValue)).toFloat().coerceIn(0f, 1f)
    }

    override fun fromValue(value: Float): JsonElement {
        val newValue = (maxValue - minValue) * value + minValue
        return JsonPrimitive(if (ratio == 0.0) minValue else (((newValue - addition) / ratio).roundToLong() * ratio + addition).coerceIn(minValue, maxValue))
    }

}

class EnumDataPointState(
    attribute: DataPointAttribute,
    isEnabled: StateFlow<Boolean>,
    dataPoint: SharedFlow<Map<String, JsonElement>>,
    onDataPointChange: (Pair<String, JsonElement>) -> Unit
) : DataPointState<Int>(attribute, isEnabled, dataPoint, onDataPointChange) {

    val options: Array<String> = attribute.enums

    override fun toValue(value: JsonElement): Int = value.asInt

    override fun fromValue(value: Int): JsonElement = JsonPrimitive(value)

}

class BinaryDataPointState(
    attribute: DataPointAttribute,
    isEnabled: StateFlow<Boolean>,
    dataPoint: SharedFlow<Map<String, JsonElement>>,
    onDataPointChange: (Pair<String, JsonElement>) -> Unit
) : DataPointState<String>(attribute, isEnabled, dataPoint, onDataPointChange) {

    override fun toValue(value: JsonElement): String {
        return value.asString
    }

    override fun fromValue(value: String): JsonElement {
        val data = JsonArray()

        value.hexToByteArray().map {
            data.add(it)
        }
        return data
    }


    private fun String.encodeHexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

}
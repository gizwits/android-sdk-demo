package com.gizwits.smart.app.feature.control.data

import com.gizwits.smart.sdk.common.DataPointAttribute
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class DataPointStateTest {
    private fun number(spec: String): NumberDataPointState {
        val attr = Gson().fromJson("""{"name":"target","uint_spec":$spec}""", DataPointAttribute::class.java)
        return NumberDataPointState(attr, MutableStateFlow(true), MutableSharedFlow<Map<String, JsonElement>>()) {}
    }
    @Test fun offsetAndStepAreAppliedWithoutDroppingAddition() {
        val state = number("""{"min":0,"max":10,"ratio":2,"addition":3}""")
        assertEquals(3.0, state.fromValue(0f).asDouble, 0.001)
        assertEquals(13.0, state.fromValue(.5f).asDouble, 0.001)
        assertEquals(23.0, state.fromValue(1f).asDouble, 0.001)
    }
    @Test fun fixedRangeAndZeroRatioDoNotProduceNaN() {
        val fixed = number("""{"min":5,"max":5,"ratio":1,"addition":0}""")
        assertEquals(0f, fixed.toValue(JsonPrimitive(5)), 0f)
        val zero = number("""{"min":0,"max":10,"ratio":0,"addition":3}""")
        assertEquals(3.0, zero.fromValue(.5f).asDouble, 0f.toDouble())
    }
    @Test fun readOnlyAndDisconnectedWritesAreBlockedAtTheStateBoundary() {
        var writes = 0
        val enabled = MutableStateFlow(true)
        val readOnly = Gson().fromJson("""{"name":"temperature","type":"READONLY"}""", DataPointAttribute::class.java)
        val writable = Gson().fromJson("""{"name":"power","type":"WRITABLE"}""", DataPointAttribute::class.java)
        BooleanDataPointState(readOnly, enabled, MutableSharedFlow()) { writes++ }.onValueChange(true)
        enabled.value = false
        BooleanDataPointState(writable, enabled, MutableSharedFlow()) { writes++ }.onValueChange(true)
        assertEquals(0, writes)
        enabled.value = true
        BooleanDataPointState(writable, enabled, MutableSharedFlow()) { writes++ }.onValueChange(true)
        assertEquals(1, writes)
    }
}

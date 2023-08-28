package com.segment.analytics.kotlin.destinations.consent

import com.segment.analytics.kotlin.core.utilities.getBoolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsentBlockingPluginTests {


    @Test
    fun `get boolean succeeds`() {
        val jsonObject = buildJsonObject { put("keyed", true) }

        val keyedValue = jsonObject.getBoolean("keyed")

        Assertions.assertEquals(true, keyedValue)
    }
}
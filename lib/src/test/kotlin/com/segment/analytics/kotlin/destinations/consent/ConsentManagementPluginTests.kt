package com.segment.analytics.kotlin.destinations.consent

import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import sovran.kotlin.SynchronousStore

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsentManagementPluginTests {

    @Test
    fun `stamps events`() {
        val store = SynchronousStore()
        store.provide(ConsentState())

        val cp = object : ConsentCategoryProvider {

            override fun setCategoryList(categories: List<String>) {
                // NO OP
            }

            override fun getCategories(): Map<String, Boolean> {
                var categories = HashMap<String, Boolean>()

                categories.put("cat1", true)
                categories.put("cat2", false)

                return categories
            }
        }


        val consentManagementPlugin = ConsentManagementPlugin(store, cp)

        var event = TrackEvent(emptyJsonObject, "MyEvent")
        event.context = emptyJsonObject

        val expectedContext = buildJsonObject {
            put(CONSENT_SETTINGS, buildJsonObject { put(CATEGORY_PREFERENCE, buildJsonObject {
                put("cat1", JsonPrimitive(true))
                put("cat2", JsonPrimitive(false))
            }) })
        }

        var processedEvent = consentManagementPlugin.execute(event)

        assertEquals(expectedContext, processedEvent?.context)
    }
}
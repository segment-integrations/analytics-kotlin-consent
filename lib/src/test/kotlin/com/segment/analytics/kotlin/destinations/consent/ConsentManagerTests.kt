package com.segment.analytics.kotlin.destinations.consent

import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import sovran.kotlin.SynchronousStore

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsentManagerTests {

    @Test
    fun `stamps events`() {
        val store = SynchronousStore()
        store.provide(ConsentState.defaultState)

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

        val consentManager = ConsentManager(store, cp)

        var event = TrackEvent(emptyJsonObject, "MyEvent")
        event.context = emptyJsonObject

        val expectedContext = buildJsonObject {
            put(CONSENT_SETTINGS, buildJsonObject { put(CATEGORY_PREFERENCE, buildJsonObject {
                put("cat1", JsonPrimitive(true))
                put("cat2", JsonPrimitive(false))
            }) })
        }

        consentManager.start()

        var processedEvent = consentManager.execute(event)

        assertEquals(expectedContext, processedEvent?.context)
    }
}
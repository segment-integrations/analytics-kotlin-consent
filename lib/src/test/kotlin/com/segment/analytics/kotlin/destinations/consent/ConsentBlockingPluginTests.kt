package com.segment.analytics.kotlin.destinations.consent

import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import sovran.kotlin.SynchronousStore

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsentBlockingPluginTests {

    @Test
    fun `allow when consent available`() {
        val store = SynchronousStore()
        store.provide(ConsentState())
        var state: MutableMap<String, Array<String>> = HashMap()
        state["foo"] = arrayOf("cat1", "cat2")
        store.dispatch(UpdateConsentStateAction(state), ConsentState::class)
        val blockingPlugin = ConsentBlockingPlugin("foo", store)

        // All categories correct
        var stamppedEvent = TrackEvent(properties = emptyJsonObject, event = "MyEvent")
        stamppedEvent.context = buildJsonObject {
            put(CONSENT_SETTINGS, buildJsonObject {
                put(CATEGORY_PREFERENCE, buildJsonObject {
                    put("cat1", JsonPrimitive(true))
                    put("cat2", JsonPrimitive(true))
                })
            })
        }
        var processedEvent = blockingPlugin.execute(stamppedEvent)
        assertNotNull(processedEvent)

        stamppedEvent = TrackEvent(properties = emptyJsonObject, event = "MyEvent")
        stamppedEvent.context = buildJsonObject {
            put(CONSENT_SETTINGS, buildJsonObject {
                put(CATEGORY_PREFERENCE, buildJsonObject {
                    put("cat1", JsonPrimitive(true))
                    put("cat2", JsonPrimitive(true))
                    put("cat3", JsonPrimitive(true))
                })
            })
        }
        processedEvent = blockingPlugin.execute(stamppedEvent)
        assertNotNull(processedEvent)
    }

    @Test
    fun `blocks when missing consent`() {
        val store = SynchronousStore()
        store.provide(ConsentState())
        var state: MutableMap<String, Array<String>> = HashMap()
        state["foo"] = arrayOf("cat1", "cat2")
        store.dispatch(UpdateConsentStateAction(state), ConsentState::class)
        val blockingPlugin = ConsentBlockingPlugin("foo", store)

        // Empty context
        var unstamppedEvent = TrackEvent(properties = emptyJsonObject, event = "MyEvent")
        unstamppedEvent.context = emptyJsonObject
        var processedEvent = blockingPlugin.execute(unstamppedEvent)
        assertNull(processedEvent)

        // Context with empty consentSettings
        unstamppedEvent.context = buildJsonObject {
            put(CONSENT_SETTINGS, emptyJsonObject)
        }
        processedEvent = blockingPlugin.execute(unstamppedEvent)
        assertNull(processedEvent)

        // Stamped Event with all categories false
        var stamppedEvent = TrackEvent(properties = emptyJsonObject, event = "MyEvent")
        stamppedEvent.context = buildJsonObject {
            put(CONSENT_SETTINGS, buildJsonObject {
                put(CATEGORY_PREFERENCE, buildJsonObject {
                    put("cat1", JsonPrimitive(false))
                    put("cat2", JsonPrimitive(false))
                })
            })
        }
        processedEvent = blockingPlugin.execute(stamppedEvent)
        assertNull(processedEvent)
    }

    @Test
    fun `allow when nothing in store`() {
        val store = SynchronousStore()
        store.provide(ConsentState())
        val blockingPlugin = ConsentBlockingPlugin("foo", store)

        // Empty context
        var unstamppedEvent = TrackEvent(properties = emptyJsonObject, event = "MyEvent")
        unstamppedEvent.context = emptyJsonObject
        var processedEvent = blockingPlugin.execute(unstamppedEvent)
        assertNotNull(processedEvent)

        var stamppedEvent = TrackEvent(properties = emptyJsonObject, event = "MyEvent")
        stamppedEvent.context = buildJsonObject {
            put(CONSENT_SETTINGS, buildJsonObject {
                put(CATEGORY_PREFERENCE, buildJsonObject {
                    put("cat1", JsonPrimitive(false))
                    put("cat2", JsonPrimitive(false))
                })
            })
        }

        processedEvent = blockingPlugin.execute(stamppedEvent)
        assertNotNull(processedEvent)
    }
}
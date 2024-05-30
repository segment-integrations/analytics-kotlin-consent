package com.segment.analytics.kotlin.destinations.consent

import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.emptyJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import sovran.kotlin.SynchronousStore

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsentBlockerTests {

    @Test
    fun `allow when consent available`() {
        val store = SynchronousStore()
        store.provide(ConsentState.defaultState)
        var mappings: MutableMap<String, Array<String>> = HashMap()
        mappings["foo"] = arrayOf("cat1", "cat2")
        val state = ConsentState(mappings, false, mutableListOf<String>(),true)
        store.dispatch(UpdateConsentStateActionFull(state), ConsentState::class)
        val blockingPlugin = ConsentBlocker("foo", store)

        // All categories correct
        var stampedEvent = TrackEvent(properties = emptyJsonObject, event = "MyEvent")
        stampedEvent.context = buildJsonObject {
            put(Constants.CONSENT_KEY, buildJsonObject {
                put(Constants.CATEGORY_PREFERENCE_KEY, buildJsonObject {
                    put("cat1", JsonPrimitive(true))
                    put("cat2", JsonPrimitive(true))
                })
            })
        }
        var processedEvent = blockingPlugin.execute(stampedEvent)
        assertNotNull(processedEvent)

        stampedEvent = TrackEvent(properties = emptyJsonObject, event = "MyEvent")
        stampedEvent.context = buildJsonObject {
            put(Constants.CONSENT_KEY, buildJsonObject {
                put(Constants.CATEGORY_PREFERENCE_KEY, buildJsonObject {
                    put("cat1", JsonPrimitive(true))
                    put("cat2", JsonPrimitive(true))
                    put("cat3", JsonPrimitive(true))
                })
            })
        }
        processedEvent = blockingPlugin.execute(stampedEvent)
        assertNotNull(processedEvent)
    }

    @Test
    fun `blocks when missing consent`() {
        val store = SynchronousStore()
        store.provide(ConsentState.defaultState)
        var mappings: MutableMap<String, Array<String>> = HashMap()
        mappings["foo"] = arrayOf("cat1", "cat2")
        val state = ConsentState(mappings, false, mutableListOf<String>(),true)
        store.dispatch(UpdateConsentStateActionFull(state), ConsentState::class)
        val blockingPlugin = ConsentBlocker("foo", store)

        // Empty context
        var unstamppedEvent = TrackEvent(properties = emptyJsonObject, event = "MyEvent")
        unstamppedEvent.context = emptyJsonObject
        var processedEvent = blockingPlugin.execute(unstamppedEvent)
        assertNull(processedEvent)

        // Context with empty consentSettings
        unstamppedEvent.context = buildJsonObject {
            put(Constants.CONSENT_KEY, emptyJsonObject)
        }
        processedEvent = blockingPlugin.execute(unstamppedEvent)
        assertNull(processedEvent)

        // Stamped Event with all categories false
        var stamppedEvent = TrackEvent(properties = emptyJsonObject, event = "MyEvent")
        stamppedEvent.context = buildJsonObject {
            put(Constants.CONSENT_KEY, buildJsonObject {
                put(Constants.CATEGORY_PREFERENCE_KEY, buildJsonObject {
                    put("cat1", JsonPrimitive(false))
                    put("cat2", JsonPrimitive(false))
                })
            })
        }
        processedEvent = blockingPlugin.execute(stamppedEvent)
        assertNull(processedEvent)
    }

    @Test
    fun `block when nothing in store`() {
        val store = SynchronousStore()
        store.provide(ConsentState.defaultState)
        val blockingPlugin = ConsentBlocker("foo", store)

        // Empty context
        var unstamppedEvent = TrackEvent(properties = emptyJsonObject, event = "MyEvent")
        unstamppedEvent.context = emptyJsonObject
        var processedEvent = blockingPlugin.execute(unstamppedEvent)
        assertNull(processedEvent)

        var stamppedEvent = TrackEvent(properties = emptyJsonObject, event = "MyEvent")
        stamppedEvent.context = buildJsonObject {
            put(Constants.CONSENT_KEY, buildJsonObject {
                put(Constants.CATEGORY_PREFERENCE_KEY, buildJsonObject {
                    put("cat1", JsonPrimitive(false))
                    put("cat2", JsonPrimitive(false))
                })
            })
        }

        processedEvent = blockingPlugin.execute(stamppedEvent)
        assertNull(processedEvent)
    }
}
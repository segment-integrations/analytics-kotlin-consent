package com.segment.analytics.kotlin.destinations.consent

import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.segment.analytics.kotlin.android.AndroidStorageProvider
import com.segment.analytics.kotlin.android.plugins.getUniqueID
import com.segment.analytics.kotlin.core.*
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.LenientJson
import com.segment.analytics.kotlin.core.utilities.toJsonElement
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.spyk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Before
import org.junit.jupiter.api.Assertions.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import sovran.kotlin.SynchronousStore

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ConsentManagerTests {

    lateinit var appContext: Context
    lateinit var analytics: Analytics

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    fun createConsentEvent(name: String, consentMap: Map<String, Boolean>, properties: Properties = emptyJsonObject, context: AnalyticsContext = emptyJsonObject): BaseEvent {
        var event = TrackEvent(properties, name)
        event.context = buildJsonObject {
            // Add all context items
            context.forEach { prop, elem -> put(prop, elem) }

            // Add (potentially overriding from context) the consentMap values
            put(Constants.CONSENT_KEY, buildJsonObject {
                put(Constants.CATEGORY_PREFERENCE_KEY, buildJsonObject {
                    consentMap.forEach { category, isConsented ->
                        put(category, JsonPrimitive(isConsented))
                    }
                })
            })
        }

        return event
    }

    @Before
    fun setUp() {
        appContext = spyk(InstrumentationRegistry.getInstrumentation().targetContext)
        val sharedPreferences: SharedPreferences = MemorySharedPreferences()
        every { appContext.getSharedPreferences(any(), any()) } returns sharedPreferences
        mockkStatic("com.segment.analytics.kotlin.android.plugins.AndroidContextPluginKt")
        every { getUniqueID() } returns "unknown"

        analytics = testAnalytics(
            Configuration(
                writeKey = "123",
                application = appContext,
                storageProvider = AndroidStorageProvider
            ),
            testScope, testDispatcher
        )
    }

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
        consentManager.start()

        // Refactor
        var event = TrackEvent(emptyJsonObject, "MyEvent")
        event.context = emptyJsonObject

        val expectedContext = buildJsonObject {
            put(Constants.CONSENT_KEY, buildJsonObject {
                put(Constants.CATEGORY_PREFERENCE_KEY, buildJsonObject {
                    put("cat1", JsonPrimitive(true))
                    put("cat2", JsonPrimitive(false))
                })
            })
        }

        var processedEvent = consentManager.execute(event)

        assertEquals(expectedContext, processedEvent?.context)
    }

    @Test
    fun `setups up blockers`() {
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

        val KEY_TEST_DESTINATION = "TestDestination"
        analytics.add(object : DestinationPlugin() {
            override val key: String = KEY_TEST_DESTINATION
            override lateinit var analytics: Analytics
        })

        val consentManager = ConsentManager(store, cp)

        analytics.add(consentManager)

        val KEY_SEGMENTIO = "Segment.io"
        val integrations = buildJsonObject {
            put(KEY_SEGMENTIO, buildJsonObject {
                put("apiKey", JsonPrimitive("foo"))
                put(Constants.CONSENT_SETTINGS_KEY, buildJsonObject {
                    put(Constants.CATEGORIES_KEY, buildJsonArray { "foo" })
                })
            })

            put(KEY_TEST_DESTINATION, buildJsonObject {
                put("apiKey", JsonPrimitive("foo"))
                put(Constants.CONSENT_SETTINGS_KEY, buildJsonObject {
                    put(Constants.CATEGORIES_KEY, buildJsonArray { "foo" })
                })
            })
        }

        val settings = Settings(
            integrations = integrations,
            plan = buildJsonObject { put("foo", JsonPrimitive("bar")) },
            middlewareSettings = buildJsonObject { put("foo", JsonPrimitive("bar")) },
            edgeFunction = buildJsonObject { put("foo", JsonPrimitive("bar")) },
            consentSettings = buildJsonObject {
                put(Constants.ALL_CATEGORIES_KEY, buildJsonArray {
                    add(JsonPrimitive("foo"))
                })

                put(Constants.HAS_UNMAPPED_DESTINATIONS_KEY, JsonPrimitive(false))
            }
        )

        consentManager.update(settings, Plugin.UpdateType.Refresh)

        val segmentDestination = analytics.find(KEY_SEGMENTIO)
        val segmentConsentBlockers = segmentDestination?.findAll(SegmentConsentBlocker::class)

        assertNotNull(segmentConsentBlockers)
        assertEquals(1, segmentConsentBlockers?.size)


        val testDestination = analytics.find(KEY_TEST_DESTINATION)
        val testConsentBlockers = testDestination?.findAll(ConsentBlocker::class)

        assertNotNull(testConsentBlockers)
        assertEquals(1, testConsentBlockers?.size)
    }

    @Test
    fun `SegmentConsentBlocker does not block when we have unmapped destinations and no consent rule`() {
        val store = SynchronousStore()

        // We _have_ unmapped destinations and there are no rules for the for the segment destination
        // so we should ALLOW the event to proceed.
        store.provide(ConsentState(mutableMapOf(), true, mutableListOf(),true))
        var event = createConsentEvent("MyConsentEvent", mapOf( "foo" to false))
        var segmentBlocker = SegmentConsentBlocker(store)
        var resultingEvent = segmentBlocker.execute(event)
        assertNotNull(resultingEvent)
    }

    @Test
    fun `SegmentConsentBlocker blocks when we have no unmapped destinations and event has no consent`() {
        val store = SynchronousStore()

        // We have NO unmapped destinations and there are no rules for the for the segment destination
        // so we should BLOCK the event to proceed.
        store.provide(ConsentState(mutableMapOf(), false, mutableListOf(),true))
        var event = createConsentEvent("MyConsentEvent", mapOf( "foo" to false))
        var segmentBlocker = SegmentConsentBlocker(store)
        var resultingEvent = segmentBlocker.execute(event)
        assertNull(resultingEvent)
    }

    @Test
    fun `SegmentConsentBlocker blocks when event missing required consent`() {
        val store = SynchronousStore()

        // We _have_ unmapped destinations but there are required consent categories for the
        // segment destination so we should BLOCK the event to proceed.
        store.provide(ConsentState(mutableMapOf("Segment.io" to arrayOf("foo")), false, mutableListOf(),true))
        var event = createConsentEvent("MyConsentEvent", mapOf( "foo" to false))
        var segmentBlocker = SegmentConsentBlocker(store)
        var resultingEvent = segmentBlocker.execute(event)
        assertNull(resultingEvent)
    }

    @Test
    fun `SegmentConsentBlocker does not block when event has required consent`() {
        val store = SynchronousStore()

        // We _have_ unmapped destinations but there are required consent categories for the
        // segment destination so we should BLOCK the event to proceed.
        store.provide(ConsentState(mutableMapOf("Segment.io" to arrayOf("foo")), false, mutableListOf(),true))
        var event = createConsentEvent("MyConsentEvent", mapOf( "foo" to true))
        var segmentBlocker = SegmentConsentBlocker(store)
        var resultingEvent = segmentBlocker.execute(event)
        assertNotNull(resultingEvent)
    }
}
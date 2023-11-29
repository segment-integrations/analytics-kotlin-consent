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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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

        var event = TrackEvent(emptyJsonObject, "MyEvent")
        event.context = emptyJsonObject

        val expectedContext = buildJsonObject {
            put(CONSENT_SETTINGS, buildJsonObject {
                put(CATEGORY_PREFERENCE, buildJsonObject {
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
                put("consentSettings", buildJsonObject {
                    put("categories", buildJsonArray { "foo" })
                })
            })

            put(KEY_TEST_DESTINATION, buildJsonObject {
                put("apiKey", JsonPrimitive("foo"))
                put("consentSettings", buildJsonObject {
                    put("categories", buildJsonArray { "foo" })
                })
            })
        }

        val settings = Settings(
            integrations = integrations,
            plan = buildJsonObject { put("foo", JsonPrimitive("bar")) },
            middlewareSettings = buildJsonObject { put("foo", JsonPrimitive("bar")) },
            edgeFunction = buildJsonObject { put("foo", JsonPrimitive("bar")) }
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

}
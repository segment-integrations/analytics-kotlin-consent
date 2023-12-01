package com.segment.analytics.kotlin.destinations.consent

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.getBoolean
import com.segment.analytics.kotlin.core.utilities.safeJsonObject
import com.segment.analytics.kotlin.core.utilities.toJsonElement
import com.segment.analytics.kotlin.destinations.consent.Constants.CATEGORIES_KEY
import com.segment.analytics.kotlin.destinations.consent.Constants.CATEGORY_PREFERENCE_KEY
import com.segment.analytics.kotlin.destinations.consent.Constants.CONSENT_KEY
import com.segment.analytics.kotlin.destinations.consent.Constants.CONSENT_SETTINGS_KEY
import com.segment.analytics.kotlin.destinations.consent.Constants.EVENT_SEGMENT_CONSENT_PREFERENCE
import com.segment.analytics.kotlin.destinations.consent.Constants.HAS_UNMAPPED_DESTINATIONS_KEY
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import sovran.kotlin.SynchronousStore
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


class ConsentManager(
    private var store: SynchronousStore,
    private var consentProvider: ConsentCategoryProvider,
    private var consentChange: (() -> Unit)? = null
) : EventPlugin {


    override lateinit var analytics: Analytics
    override val type: Plugin.Type = Plugin.Type.Enrichment

    // Event Queue
    private var queuedEvents: Queue<BaseEvent> = LinkedList()

    // Flag for Event Queue
    private var started = AtomicBoolean(false)

    init {
        // IF no consentChanged function passed in, set a default.
        if (consentChange == null) {
            consentChange = { analytics.track(EVENT_SEGMENT_CONSENT_PREFERENCE) }
        }
    }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)

        // Empty state
        store.provide(ConsentState.defaultState)
    }

    override fun update(settings: Settings, type: Plugin.UpdateType) {

        val state = consentStateFrom(settings)
        // Update the store
        store.dispatch(UpdateConsentStateActionFull(state), ConsentState::class)


        // Add Segment Destination blocker
        analytics.find(Constants.SEGMENT_IO_KEY)?.let { segmentDestination ->
            val existingBlocker = analytics.find(SegmentConsentBlocker::class)
            if (existingBlocker == null) {
                segmentDestination.add(SegmentConsentBlocker(store))
            }
        }

        // Add Blocker to all other destinations
        val destinationKeys = state.destinationCategoryMap.keys
        for (key in destinationKeys) {
            analytics.find(key)?.let { destination ->
                if (destination.key != Constants.SEGMENT_IO_KEY) {
                    val existingBlockers =
                        destination.findAll(ConsentBlocker::class)
                    if (existingBlockers.isEmpty()) {
                        destination.add(ConsentBlocker(key, store))
                    }
                }
            }
        }

    }

    private fun consentStateFrom(settings: Settings): ConsentState {

        val destinationMapping = mutableMapOf<String, Array<String>>()
        var hasUnmappedDestinations = true
        var enabledAtSegment = true

        // Add all mappings
        settings.integrations.forEach { integrationName, integrationJson ->
            // If the integration has the consent key:
            integrationJson.jsonObject[CONSENT_SETTINGS_KEY]?.let {

                // Build list of categories required for this integration
                val categories: MutableList<String> = mutableListOf()
                (it.jsonObject.get(CATEGORIES_KEY) as JsonArray).forEach { categoryJsonElement ->
                    categories.add(categoryJsonElement.toString().replace("\"", "").trim())
                }
                destinationMapping[integrationName] = categories.toTypedArray()
            }
        }

        // Set hasUnmappedDestinations
        try {
            settings.toJsonElement().jsonObject.get(CONSENT_SETTINGS_KEY)?.let {
                it.jsonObject.getBoolean(HAS_UNMAPPED_DESTINATIONS_KEY)
                    ?.let { serverHasUnmappedDestinations ->
                        println("hasUnmappedDestinations jsonElement: $serverHasUnmappedDestinations")
                        hasUnmappedDestinations = serverHasUnmappedDestinations == true
                    }
            }
        } catch (t: Throwable) {
            println("Couldn't parse settings object to check for 'hasUnmappedDestinations'")
        }

        // Set enabledAtSegment
        try {
            settings.toJsonElement().jsonObject.get(CONSENT_SETTINGS_KEY)?.safeJsonObject.let {
                enabledAtSegment = true
            }
        } catch (t: Throwable) {
            println("Couldn't parse settings object to check if 'enabledAtSegment'.")
        }

        return ConsentState(destinationMapping, hasUnmappedDestinations, enabledAtSegment)
    }

    override fun execute(event: BaseEvent): BaseEvent? {

        return if (started.get()) {
            // Stamp consent on the event
            stampEvent(event)
            event
        } else {
            queuedEvents.add(event)
            null
        }
    }

    /**
     * Add the consent status to the event's context object.
     */
    private fun stampEvent(event: BaseEvent) {
        event.context = buildJsonObject {
            event.context.forEach { key, json ->
                put(key, json)
            }
            put(CONSENT_KEY, buildJsonObject {
                put(CATEGORY_PREFERENCE_KEY, buildJsonObject {
                    val categories = consentProvider.getCategories()
                    categories.forEach { (category, status) ->
                        put(category, JsonPrimitive(status))
                    }
                })
            })
        }
    }

    /**
     * Notify the ConsentManagementPlugin that consent has changed. This will
     * trigger the Segment Consent Preference event to be fired.
     */
    fun notifyConsentChanged() {
        consentChange?.invoke()
    }

    fun start() {
        started.set(true)

        while (queuedEvents.isNotEmpty()) {
            queuedEvents.poll()?.let { analytics.process(it) }
        }

        queuedEvents.clear()
    }
}
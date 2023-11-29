package com.segment.analytics.kotlin.destinations.consent

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.core.utilities.toJsonElement
import com.segment.analytics.kotlin.destinations.consent.Constants.Companion.CATEGORIES_KEY
import com.segment.analytics.kotlin.destinations.consent.Constants.Companion.CATEGORY_PREFERENCE_KEY
import com.segment.analytics.kotlin.destinations.consent.Constants.Companion.CONSENT_KEY
import com.segment.analytics.kotlin.destinations.consent.Constants.Companion.CONSENT_SETTINGS_KEY
import com.segment.analytics.kotlin.destinations.consent.Constants.Companion.EVENT_SEGMENT_CONSENT_PREFERENCE
import com.segment.analytics.kotlin.destinations.consent.Constants.Companion.HAS_UNMAPPED_DESTINATIONS_KEY
import kotlinx.serialization.json.*

import sovran.kotlin.SynchronousStore
import java.util.concurrent.atomic.AtomicBoolean


class ConsentManager(
    private var store: SynchronousStore,
    private var consentProvider: ConsentCategoryProvider,
    private var consentChange: (() -> Unit)? = null
) : EventPlugin {


    override lateinit var analytics: Analytics
    override val type: Plugin.Type = Plugin.Type.Enrichment

    // Event Queue
    private var queuedEvents: MutableList<BaseEvent>? = mutableListOf<BaseEvent>()

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


        analytics?.let {

            // Add Segment Destination blocker
            val segmentDestination = it.find(Constants.SEGMENT_IO_KEY)
            segmentDestination.let {
                val existingBlocker =
                    segmentDestination?.analytics?.find(SegmentConsentBlocker::class)
                if (existingBlocker == null) {
                    segmentDestination?.add(SegmentConsentBlocker(store))
                }
            }

            // Add Blocker to all other destinations
            val destinationKeys = state.destinationCategoryMap.keys
            for (key in destinationKeys) {
                val destination = analytics.find(key)
                destination.let {
                    if (it?.key != Constants.SEGMENT_IO_KEY) {
                        val existingBlock =
                            destination?.analytics?.find(ConsentBlocker::class)
                        if (existingBlock == null) {
                            destination?.add(ConsentBlocker(key, store))
                        }
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
                val jsonElement = it.jsonObject.get(HAS_UNMAPPED_DESTINATIONS_KEY)
                println("hasUnmappedDestinations jsonElement: $jsonElement")
                hasUnmappedDestinations = jsonElement.toString() == "true"
            }
        } catch (t: Throwable) {
            println("Couldn't parse settings object to check for 'hasUnmappedDestinations'")
        }

        // Set enabledAtSegment
        try {
            settings.toJsonElement().jsonObject.get(CONSENT_SETTINGS_KEY)?.let {
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
            queuedEvents?.add(event)
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
        consentChange?.let {
            it()
        }
    }

    fun start() {
        started.set(true)

        for (event in queuedEvents!!) {
            analytics?.process(event)
        }

        queuedEvents?.clear()
        queuedEvents = null
    }
}
package com.segment.analytics.kotlin.destinations.consent

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.platform.Plugin
import com.segment.analytics.kotlin.destinations.consent.Constants.EVENT_SEGMENT_CONSENT_PREFERENCE
import com.segment.analytics.kotlin.destinations.consent.Constants.SEGMENT_IO_KEY
import kotlinx.serialization.json.JsonObject
import sovran.kotlin.SynchronousStore
import com.segment.analytics.kotlin.destinations.consent.Constants.CATEGORY_PREFERENCE_KEY
import com.segment.analytics.kotlin.destinations.consent.Constants.CONSENT_KEY



open class ConsentBlocker(
    var destinationKey: String,
    var store: SynchronousStore,
    var allowSegmentPreferenceEvent: Boolean = true
) : Plugin {
    override lateinit var analytics: Analytics
    override val type: Plugin.Type = Plugin.Type.Enrichment
    private val TAG = "ConsentBlockingPlugin"

    override fun execute(event: BaseEvent): BaseEvent? {
        val currentState = store.currentState(ConsentState::class)

        val requiredConsentCategories = currentState?.destinationCategoryMap?.get(destinationKey)

        if (requiredConsentCategories != null && requiredConsentCategories.isNotEmpty()) {

            val consentJsonArray = getConsentedCategoriesFromEvent(event)

            // Look for a missing consent category
            requiredConsentCategories.forEach {
                if (!consentJsonArray.contains(it)) {

                    if (allowSegmentPreferenceEvent && event is TrackEvent && event.event == EVENT_SEGMENT_CONSENT_PREFERENCE) {
                        // IF event is the EVENT_SEGMENT_CONSENT_PREFERENCE event let it through
                        return event
                    } else {
                        return null
                    }
                }
            }
        } else {
            // Given that this Blocking Plugin was put in place in this Destination we are
            // expecting to have required consent categories. For some reason we don't have
            // this information so we must default to BLOCKING the event.
            return null
        }
        return event
    }

    /**
     * Returns the set of consented categories in the event. Only categories with set to 'true'
     * will be returned.
     */
    internal fun getConsentedCategoriesFromEvent(event: BaseEvent): Set<String> {
        val consentJsonArray = HashSet<String>()

        val consentSettingsJson = event.context[CONSENT_KEY]
        if (consentSettingsJson != null) {
            val consentJsonObject = (consentSettingsJson as JsonObject)
            val categoryPreferenceJson = consentJsonObject[CATEGORY_PREFERENCE_KEY]
            if (categoryPreferenceJson != null) {
                val categoryPreferenceJsonObject = categoryPreferenceJson as JsonObject
                categoryPreferenceJsonObject.forEach { category, consentGiven ->
                    if (consentGiven.toString() == "true") {
                        // Add this category to the list of necessary categories
                        consentJsonArray.add(category)
                    }
                }
            }
        }
        return consentJsonArray
    }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
    }

    override fun update(settings: Settings, type: Plugin.UpdateType) {
        super.update(settings, type)
    }
}


class SegmentConsentBlocker(store: SynchronousStore): ConsentBlocker(SEGMENT_IO_KEY, store) {
    override fun execute(event: BaseEvent): BaseEvent? {

        val currentState = store.currentState(ConsentState::class)
        val hasUnmappedDestinations = currentState?.hasUnmappedDestinations

        // IF we have no unmapped destinations and we have not consented to any categories block (drop)
        // the event.
        if (hasUnmappedDestinations == false) {
            val consentedCategoriesSet = getConsentedCategoriesFromEvent(event)
            if (consentedCategoriesSet.isEmpty()) {
                // Drop the event
                return null
            }
        }

        return event
    }
}

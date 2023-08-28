package com.segment.analytics.kotlin.destinations.consent

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.platform.Plugin
import kotlinx.serialization.json.*
import sovran.kotlin.Action
import sovran.kotlin.State
import sovran.kotlin.SynchronousStore


data class ConsentState(var destinationCategoryMap: Map<String, Array<String>> = mapOf()): State {
}

class UpdateConsentStateAction(var value: Map<String, Array<String>>): Action<ConsentState> {
    override fun reduce(state: ConsentState): ConsentState {
        val newState = state.copy()
        newState.destinationCategoryMap = value

        return newState
    }
}

class ConsentManagementPlugin(
    private var store: SynchronousStore,
    private var consentProvider: ConsentCategoryProvider? = null) : Plugin {


    companion object {
        // Note, because this event name starts with "Segment" it won't show up in the Segment
        // debugger. To allow this event to be displayed in the Segment Debugger you can add a
        // prefix like "X-" to the event name.
        const val EVENT_SEGMENT_CONSENT_PREFERENCE = "Segment Consent Preference"
        const val CONSENT_SETTINGS_KEY = "consentSettings"
        const val CONSENT_KEY = "consent"
        const val CATEGORY_PREFERENCE_KEY = "categoryPreference"
        const val CATEGORIES_KEY = "categories"
    }


    override lateinit var analytics: Analytics
    override val type: Plugin.Type = Plugin.Type.Enrichment

    override fun setup(analytics: Analytics) {
        super.setup(analytics)

        // Empty state
        store.provide(ConsentState())
    }

    override fun update(settings: Settings, type: Plugin.UpdateType) {

        val state = consentStateFrom(settings.integrations)

        // Update the store
        store.dispatch(UpdateConsentStateAction(state), ConsentState::class)
    }

    private fun consentStateFrom(integrations: JsonObject): HashMap<String, Array<String>> {
        val state = HashMap<String, Array<String>>()

        integrations.forEach { integrationName, integrationJson ->
            // If the integration has the consent key:
            integrationJson.jsonObject.get(CONSENT_SETTINGS_KEY)?.let {

                // Build list of categories required for this integration
                val categories: MutableList<String> = mutableListOf()
                (it.jsonObject.get(CATEGORIES_KEY) as JsonArray).forEach { categoryJsonElement ->
                    categories.add(categoryJsonElement.toString().replace("\"", "").trim())
                }

                state[integrationName] = categories.toTypedArray()
            }
        }
        return state
    }

    override fun execute(event: BaseEvent): BaseEvent? {

        // Try to stamp consent on the event
        stampEvent(event)

        return event
    }

    /**
     * Add the consent status to the event's context object.
     */
    private fun stampEvent(event: BaseEvent) {
        consentProvider?.let {
            event.context = buildJsonObject {
                event.context.forEach { key, json ->
                    put(key, json)
                }
                put(CONSENT_KEY, buildJsonObject {
                    put(CATEGORY_PREFERENCE_KEY, buildJsonObject {
                        val categories = consentProvider?.getCategories()
                        categories?.forEach { (category, status) ->
                            put(category, JsonPrimitive(status))
                        }
                    })
                })
            }
        }
    }

    /**
     * Notify the ConsentManagementPlugin that consent has changed. This will
     * trigger the Segment Consent Preference event to be fired.
     */
    fun notifyConsentChanged() {
        analytics.track(EVENT_SEGMENT_CONSENT_PREFERENCE)
    }

    /**
     * Sets the Consent Category provider that will be used to get consent category information.
     */
    fun setConsentCategoryProvider(provider: ConsentCategoryProvider) {
        this.consentProvider = provider
    }
}
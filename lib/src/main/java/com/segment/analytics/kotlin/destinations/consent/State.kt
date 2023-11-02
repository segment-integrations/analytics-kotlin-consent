package com.segment.analytics.kotlin.destinations.consent

import sovran.kotlin.Action
import sovran.kotlin.State


class ConsentState(
    var destinationCategoryMap: Map<String, Array<String>>,
    var hasUnmappedDestinations: Boolean,
    var enabledAtSegment: Boolean
) : State {

    companion object {
        val defaultState = ConsentState(mutableMapOf(), false, false)
    }
}

class UpdateConsentStateActionFull(var value: ConsentState) : Action<ConsentState> {
    override fun reduce(state: ConsentState): ConsentState {

        // New state override any old state.
        val newState = ConsentState(
            value.destinationCategoryMap, value.hasUnmappedDestinations, value.enabledAtSegment
        )

        return newState
    }
}

class UpdateConsentStateActionMappings(var mappings: Map<String, Array<String>>) : Action<ConsentState> {
    override fun reduce(state: ConsentState): ConsentState {
        val newState = ConsentState(mappings, state.hasUnmappedDestinations, state.enabledAtSegment)
        return newState
    }
}

class UpdateConsentStateActionHasUnmappedDestinations(var hasUnmappedDestinations: Boolean) : Action<ConsentState> {
    override fun reduce(state: ConsentState): ConsentState {

        // New state override any old state.
        val newState = ConsentState(
            state.destinationCategoryMap, hasUnmappedDestinations, state.enabledAtSegment
        )

        return newState
    }
}

class UpdateConsentStateActionEnabledAtSegment(var enabledAtSegment: Boolean) : Action<ConsentState> {
    override fun reduce(state: ConsentState): ConsentState {

        // New state override any old state.
        val newState = ConsentState(
            state.destinationCategoryMap, state.hasUnmappedDestinations, enabledAtSegment
        )

        return newState
    }
}

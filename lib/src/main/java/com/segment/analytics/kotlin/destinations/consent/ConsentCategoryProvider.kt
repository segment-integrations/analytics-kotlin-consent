package com.segment.analytics.kotlin.destinations.consent

/**
 * This interface fronts a module that would like to provide
 * Consent Category status.
 */
interface ConsentCategoryProvider {

    /**
     * Sets the list of categories that will be used to query the CMP.
     */
    fun setCategoryList(categories: List<String>)

    /**
     * Returns a MAP of String (category name) to Boolean (has consent for the category).
     * Should return all categories know to the CMP.
     */
    fun getCategories(): Map<String, Boolean>
}
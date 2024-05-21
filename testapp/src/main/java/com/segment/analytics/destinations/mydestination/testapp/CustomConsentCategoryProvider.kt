package com.segment.analytics.destinations.mydestination.testapp

import com.segment.analytics.kotlin.destinations.consent.ConsentCategoryProvider

/**
 * A toy implementation of a ConsentCategoryProvider. Normally, an implementation would
 * be tied to a CMP tool like OneTrust and get information regarding consent from it, but
 * this will work for our simple use-case.
 */
class CustomConsentCategoryProvider: ConsentCategoryProvider {
    var categories: List<String>? = null
    override fun setCategoryList(categories: List<String>) {
        this.categories = categories
    }

    override fun getCategories(): Map<String, Boolean> {
        val result = mutableMapOf<String,Boolean>()

        // Allow all categories
        categories?.forEach { cat -> result.put(cat, true) }

        return result
    }
}
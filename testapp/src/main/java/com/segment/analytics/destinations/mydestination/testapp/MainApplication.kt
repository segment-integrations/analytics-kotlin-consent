package com.segment.analytics.destinations.mydestination.testapp

import android.app.Application
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.android.Analytics
import com.segment.analytics.kotlin.core.platform.policies.CountBasedFlushPolicy
import com.segment.analytics.kotlin.core.platform.policies.FrequencyFlushPolicy
import com.segment.analytics.kotlin.consent.ConsentManager
import org.json.JSONException
import org.json.JSONObject
import sovran.kotlin.SynchronousStore


class MainApplication: Application() {

    val SEGMENT_WRITE_KEY = "<writekey>"
    companion object {
        lateinit var analytics: Analytics
        const val TAG = "application"
    }

    private fun getGroupIds(domainGroupData: JSONObject): List<String> {
        val result: MutableList<String> = ArrayList()
        try {
            val groups = domainGroupData.getJSONArray("Groups")
            for (i in 0 until groups.length()) {
                val group = groups.getJSONObject(i)
                val groupId = group.getString("OptanonGroupId")
                result.add(groupId)
            }
        } catch (ex: JSONException) {
            ex.printStackTrace()
        }
        return result
    }

    override fun onCreate() {
        super.onCreate()

        Analytics.debugLogsEnabled = true
        analytics = Analytics(SEGMENT_WRITE_KEY, applicationContext) {
            this.collectDeviceId = true
            this.trackApplicationLifecycleEvents = true
            this.trackDeepLinks = true
            this.flushPolicies = mutableListOf(
                CountBasedFlushPolicy(1), // Flush after each event
                FrequencyFlushPolicy(5000) // Flush after 5 Seconds
            )
        }

        val consentCategoryProvider = CustomConsentCategoryProvider()
        val store = SynchronousStore()

        val consentPlugin = ConsentManager(store, consentCategoryProvider)

        analytics.add(consentPlugin)
        consentPlugin.start()
    }


}
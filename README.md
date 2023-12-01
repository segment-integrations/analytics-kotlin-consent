# Twilio Consent Management

This plugin provides the framework to integration Consent Management Platform (CMP) SDKs like
OneTrust to supply consent status and potential block events going to device mode destinations.

## Background

Consent Management is the management of a user’s consent preferences related to privacy. You might
be familiar with the Privacy Pop-ups that have become mandated recently that ask the user if he or
she consents to the use of certain category of cookies:

![Sample CMP UI](imgs/cmp-sample.png?raw=true "Sample CMP UI")

The Privacy pop-up asks the user if he or she will consent to the use of cookies and allows the user
to customize their consent by turning on/off different categories of cookies.

After the user selects “Allow All” or “Save Preferences” a callback is fired and the owner of the
website is notified as to the consent preferences of a given user. The website owner must then store
that consent preference and abide by it. Any rejected cookies must not be set or read to avoid large
fines that can be handed down by government authorities.

Additionally, besides the initial pop-up the website owner must give users a way to later change any
preferences they originally selected. This is usually accomplished by providing a link to display
the customization screen.

## Segment managed CMP

Segment provides a framework for users to integrate any CMP they choose and use the Segment web app
to map consent categories to device mode destinations. This information is sent down the
analytics-kotlin SDK and stored for later lookup.

Every event that flows through the library will be stamped with the current status according to
whatever configured CMP is used. Event stamping is handled by the ConsentManagementPlugin.

Using consent status stamped on the events and the mappings sent down from the Segment web app each
event is evaluated and action is taken. Currently the supported actions are:

- Blocking - This action is implemented by the ConsentBlockingPlugin

## Event Stamping

Event stamping is the process of adding the consent status information to an existing event. The
information is added to the context object of every event. Below is a before and after example:

Before

```json
{
  "anonymousId": "23adfd82-aa0f-45a7-a756-24f2a7a4c895",
  "type": "track",
  "event": "MyEvent",
  "userId": "u123",
  "timestamp": "2023-01-01T00:00:00.000Z",
  "context": {
    "traits": {
      "email": "peter@example.com",
      "phone": "555-555-5555"
    },
    "device": {
      "advertisingId": "7A3CBBA0-BDF5-11E4-8DFC-AA02A5B093DB"
    }
  }
}
```

After

```json
{
  "anonymousId": "23adfd82-aa0f-45a7-a756-24f2a7a4c895",
  "type": "track",
  "event": "MyEvent",
  "userId": "u123",
  "timestamp": "2023-01-01T00:00:00.000Z",
  "context": {
    "traits": {
      "email": "peter@example.com",
      "phone": "555-555-5555"
    },
    "device": {
      "advertisingId": "7A3CBBA0-BDF5-11E4-8DFC-AA02A5B093DB"
    },
    "consent": {
      "categoryPreferences": {
        "Advertising": true,
        "Analytics": false,
        "Functional": true,
        "DataSharing": false
      }
    }
  }
}
```

## Segment Consent Preference Event

When notified by the CMP SDK that consent has changed, a track event with name “Segment Consent
Preference” will be emitted. Below is example of what that event will look like:

```json
{
  "anonymousId": "23adfd82-aa0f-45a7-a756-24f2a7a4c895",
  "type": "track",
  "event": "Segment Consent Preference",
  "userId": "u123",
  "timestamp": "2023-01-01T00:00:00.000Z",
  "context": {
    "device": {
      "advertisingId": "7A3CBEA0-BDF5-11E4-8DFC-AA07A5B093DB"
    },
    "consent": {
      "categoryPreferences": {
        "Advertising": true,
        "Analytics": false,
        "Functional": true,
        "DataSharing": false
      }
    }
  }
}
```

## Event Flow

![Shows how an event is stamped and later checked for consent](imgs/main-flow-diagram.png?raw=true "Event Flow Diagram")

1. An event is dropped onto the timeline by some tracking call.
2. The ConsentManagementPlugin consumes the event, stamps it, and returns it.
3. The event is now stamped with consent information from this point forward.
4. The event is copied. The copy is consumed by a Destination Plugin and continues down its internal
   timeline. The original event is returned and continues down the main timeline.
   a. The stamped event is now on the timeline of the destination plugin.
   b. The event reaches the ConsentBlockingPlugin which makes a decision as to whether or not to let
   the event continue down the timeline.
   c. If the event has met the consent requirements it continues down the timeline.
5. The event continues down the timeline.

## Getting Started

To get started add the dependency for consent management to your app's build.gradle file:

```groovy
    implementation 'com.segment.analytics.kotlin.destinations:consent:<LATEST_VERSION>'
```

Next pick from one of the prebuilt integration:

- OneTrust: https://github.com/segment-integrations/analytics-kotlin-consent-onetrust

Or build your own integration (see below)

Next you'll need to write some setup/init code where you have your
Analytics setup:

```kotlin
analytics = Analytics(SEGMENT_WRITE_KEY, applicationContext) {
    this.collectDeviceId = true
    this.trackApplicationLifecycleEvents = true
    this.trackDeepLinks = true
    this.flushPolicies = listOf(
        CountBasedFlushPolicy(5), // Flush after 5 events
        FrequencyFlushPolicy(5000) // Flush after 5 Seconds
    )
}

// Add the myDestination plugin into the main timeline
val myDestinationPlugin = myDestinationPlugin()
analytics.add(myDestinationPlugin)

val consentCategoryProvider = MyConsentCategoryProvider(cmpSDK)
val store = SynchronousStore() // Use only a Synchronous store here!

val consentPlugin = ConsentManagementPlugin(store, consentCategoryProvider)

// Add the Consent Plugin directly to analytics
analytics.add(consentPlugin)


// Start the OneTrust SDK
otPublishersHeadlessSDK.startSDK(
    DOMAIN_URL,
    DOMAIN_ID,
    "en",
    null,
    false,
    object : OTCallback {
        override fun onSuccess(p0: OTResponse) {
            Log.d(TAG, "onSuccess: SDK Started")
            // Grab the categories
            val categories = getGroupIds(MainApplication.otPublishersHeadlessSDK.domainGroupData)
            
            // set categories
            consentCategoryProvider.setCategories(categories)

            // Start the consent Plugin so that events are actually processed
            consentPlugin.start()
        }

        override fun onFailure(p0: OTResponse) {
            Log.d(TAG, "onFailure: Failed to start SDK")
        }

    })

// Helper function

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


```

## Building your own integration

In order to integrate a new CMP all you have down is provide to couple of integration points.

### Consent Category Provider

First you'll need to create a `ConsentCategoryProvider` that will provide a mapping of consent
category to whether or not a given category has been consented by the user.

Example:

```Kotlin
class OneTrustConsentCategoryProvider(
    val otPublishersHeadlessSDK: OTPublishersHeadlessSDK,
    val categories: List<String>
) : ConsentCategoryProvider {

    override fun getCategories(): Map<String, Boolean> {
        var categoryConsentMap = HashMap<String, Boolean>()

        categories.forEach { category ->
            val consent = otPublishersHeadlessSDK.getConsentStatusForGroupId(category)
            val consentValue = when (consent) {
                1 -> true
                else -> false
            }

            categoryConsentMap.put(category, consentValue)
        }

        return categoryConsentMap
    }
}
```

Here we show how OneTrust is integrated. As you can see it uses a passed in list of categories to
query the OneTrust SDK and maps the OneTrust response (-1, 0, or 1) to true/false.

### Consent Changed Notifier

The second and last integration point is a way to notify the ConsentManagementPlugin that consent
has changed for the user.

Here is an OneTrust example:

```kotlin
package com.segment.analytics.kotlin.destinations.consent.onetrust

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.onetrust.otpublishers.headless.Public.Keys.OTBroadcastServiceKeys
import com.segment.analytics.kotlin.destinations.consent.ConsentManagementPlugin
import java.lang.ref.WeakReference

class OneTrustConsentChangedNotifier(
    val contextReference: WeakReference<Context>,
    val categories: List<String>,
    val consentPlugin: ConsentManagementPlugin
) {

    private val consentChangedReceiver: BroadcastReceiver? = null

    fun register() {
        if (consentChangedReceiver != null) {
            unregister()
        }

        val context = contextReference.get()
        categories.forEach {

            if (context != null) {
                context.registerReceiver(
                    OneTrustConsentChangedReceiver(consentPlugin),
                    IntentFilter(OTBroadcastServiceKeys.OT_CONSENT_UPDATED)
                )
            }
        }
    }

    fun unregister() {
        val context = contextReference.get()
        if (context != null) {
            context.unregisterReceiver(consentChangedReceiver)
        }
    }
}

class OneTrustConsentChangedReceiver(val consentPlugin: ConsentManagementPlugin) :
    BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        consentPlugin.notifyConsentChanged()
    }
}
```

Here we can see that the OneTrust SDK notifies us of consent change via an Android Intent with the
action `OTBroadcastServiceKeys.OT_CONSENT_UPDATED` so our notifier must create a broadcast receiver
and listen for this event. One the event is broadcast the reciever will then
call `consentPlugin.notifyConsentChanged()` to let the ConsentManagmentPlugin to send
the `Segment Consent Preference` event.

## License

```
MIT License

Copyright (c) 2023 Segment

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

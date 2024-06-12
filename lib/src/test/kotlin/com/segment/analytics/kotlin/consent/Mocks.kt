package com.segment.analytics.kotlin.consent

import android.content.SharedPreferences
import androidx.annotation.Nullable
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.CoroutineConfiguration
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import sovran.kotlin.Store
import kotlin.coroutines.CoroutineContext



fun testAnalytics(configuration: Configuration, testScope: TestScope, testDispatcher: TestDispatcher): Analytics {
    return object : Analytics(configuration, TestCoroutineConfiguration(testScope, testDispatcher)) {}
}




fun spyStore(scope: CoroutineScope, dispatcher: CoroutineDispatcher): Store {
    val store = spyk(Store())
    every { store getProperty "sovranScope" } propertyType CoroutineScope::class returns scope
    every { store getProperty "syncQueue" } propertyType CoroutineContext::class returns dispatcher
    every { store getProperty "updateQueue" } propertyType CoroutineContext::class returns dispatcher
    return store
}




class TestCoroutineConfiguration(
    val testScope: TestScope,
    val testDispatcher: TestDispatcher
) : CoroutineConfiguration {

    override val store: Store =
        spyStore(testScope, testDispatcher)

    override val analyticsScope: CoroutineScope
        get() = testScope

    override val analyticsDispatcher: CoroutineDispatcher
        get() = testDispatcher

    override val networkIODispatcher: CoroutineDispatcher
        get() = testDispatcher

    override val fileIODispatcher: CoroutineDispatcher
        get() = testDispatcher
}




/**
 * Mock implementation of shared preference, which just saves data in memory using map.
 */
class MemorySharedPreferences : SharedPreferences {
    internal val preferenceMap: HashMap<String, Any?> = HashMap()
    private val preferenceEditor: MockSharedPreferenceEditor
    override fun getAll(): Map<String, *> {
        return preferenceMap
    }

    @Nullable
    override fun getString(s: String, @Nullable s1: String?): String? {
        return try {
            preferenceMap[s] as String?
        } catch(ex: Exception) {
            s1
        }
    }

    @Nullable
    override fun getStringSet(s: String, @Nullable set: Set<String>?): Set<String>? {
        return try {
            preferenceMap[s] as Set<String>?
        } catch(ex: Exception) {
            set
        }
    }

    override fun getInt(s: String, i: Int): Int {
        return try {
            preferenceMap[s] as Int
        } catch(ex: Exception) {
            i
        }
    }

    override fun getLong(s: String, l: Long): Long {
        return try {
            preferenceMap[s] as Long
        } catch(ex: Exception) {
            l
        }
    }

    override fun getFloat(s: String, v: Float): Float {
        return try {
            preferenceMap[s] as Float
        } catch(ex: Exception) {
            v
        }
    }

    override fun getBoolean(s: String, b: Boolean): Boolean {
        return try {
            preferenceMap[s] as Boolean
        } catch(ex: Exception) {
            b
        }
    }

    override fun contains(s: String): Boolean {
        return preferenceMap.containsKey(s)
    }

    override fun edit(): SharedPreferences.Editor {
        return preferenceEditor
    }

    override fun registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    class MockSharedPreferenceEditor(private val preferenceMap: HashMap<String, Any?>) :
        SharedPreferences.Editor {
        override fun putString(s: String, @Nullable s1: String?): SharedPreferences.Editor {
            preferenceMap[s] = s1
            return this
        }

        override fun putStringSet(
            s: String,
            @Nullable set: Set<String>?
        ): SharedPreferences.Editor {
            preferenceMap[s] = set
            return this
        }

        override fun putInt(s: String, i: Int): SharedPreferences.Editor {
            preferenceMap[s] = i
            return this
        }

        override fun putLong(s: String, l: Long): SharedPreferences.Editor {
            preferenceMap[s] = l
            return this
        }

        override fun putFloat(s: String, v: Float): SharedPreferences.Editor {
            preferenceMap[s] = v
            return this
        }

        override fun putBoolean(s: String, b: Boolean): SharedPreferences.Editor {
            preferenceMap[s] = b
            return this
        }

        override fun remove(s: String): SharedPreferences.Editor {
            preferenceMap.remove(s)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            preferenceMap.clear()
            return this
        }

        override fun commit(): Boolean {
            return true
        }

        override fun apply() {
            // Nothing to do, everything is saved in memory.
        }
    }

    init {
        preferenceEditor = MockSharedPreferenceEditor(preferenceMap)
    }
}
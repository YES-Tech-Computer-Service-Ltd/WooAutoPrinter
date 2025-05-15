package com.example.wooauto.licensing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

object TrialManager {
    private val KEY_FIRST_LAUNCH_TIME = longPreferencesKey("first_launch_time")
    private const val TRIAL_PERIOD_DAYS = 3

    fun isTrialExpired(context: Context, dataStore: DataStore<Preferences>): Boolean {
        val firstLaunchTime = getOrInitializeFirstLaunchTime(dataStore)
        val currentTime = System.currentTimeMillis()
        val daysUsed = TimeUnit.MILLISECONDS.toDays(currentTime - firstLaunchTime)
        return daysUsed >= TRIAL_PERIOD_DAYS
    }

    private fun getOrInitializeFirstLaunchTime(dataStore: DataStore<Preferences>): Long {
        return runBlocking {
            val preferences = dataStore.data.first()
            preferences[KEY_FIRST_LAUNCH_TIME] ?: initializeFirstLaunchTime(dataStore)
        }
    }

    private suspend fun initializeFirstLaunchTime(dataStore: DataStore<Preferences>): Long {
        val currentTime = System.currentTimeMillis()
        dataStore.edit { prefs ->
            prefs[KEY_FIRST_LAUNCH_TIME] = currentTime
        }
        return currentTime
    }
}

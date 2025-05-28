package com.example.wooauto.licensing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Locale
import android.util.Log
import java.util.TimeZone
import java.util.Calendar
import kotlinx.coroutines.flow.flowOf

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "license_prefs")

object LicenseDataStore {

    private val IS_LICENSED = booleanPreferencesKey("is_licensed")
    private val LICENSE_START_DATE = stringPreferencesKey("license_start_date")
    private val LICENSE_END_DATE = stringPreferencesKey("license_end_date")
    private val LICENSE_KEY = stringPreferencesKey("license_key")
    private val LICENSE_EDITION = stringPreferencesKey("license_edition")
    private val CAPABILITIES = stringPreferencesKey("capabilities")
    private val LICENSED_TO = stringPreferencesKey("licensed_to")

    suspend fun isLicensed(context: Context): Flow<Boolean> = try {
        context.dataStore.data.map { preferences ->
            val licenseValue = preferences[IS_LICENSED]
            if (licenseValue != true) {
                false
            } else {
                // 检查许可证是否过期
                val endDateStr = preferences[LICENSE_END_DATE]
                if (endDateStr.isNullOrEmpty()) {
                    false
                } else {
                    try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        sdf.timeZone = TimeZone.getDefault()
                        val endDateTime = sdf.parse(endDateStr)?.time ?: 0
                        val currentDateTime = Calendar.getInstance(TimeZone.getDefault()).timeInMillis
                        val isValid = endDateTime > currentDateTime
                        isValid
                    } catch (e: Exception) {
                        false
                    }
                }
            }
        }
    } catch (e: Exception) {
        flowOf(false)
    }

    suspend fun setLicensed(context: Context, isLicensed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_LICENSED] = isLicensed
        }
    }

    fun getLicenseStartDate(context: Context): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            val startDate = preferences[LICENSE_START_DATE]
            startDate
        }
    }

    fun getLicenseEndDate(context: Context): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            val endDate = preferences[LICENSE_END_DATE]
            endDate
        }
    }

    fun getLicenseKey(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs ->
            prefs[LICENSE_KEY] ?: ""
        }
    }

    fun getLicenseEdition(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs ->
            prefs[LICENSE_EDITION] ?: "Spire"
        }
    }

    fun getCapabilities(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs ->
            prefs[CAPABILITIES] ?: "cap1, cap2"
        }
    }

    fun getLicensedTo(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs ->
            prefs[LICENSED_TO] ?: "MockCustomer"
        }
    }

    suspend fun saveLicenseStartDate(context: Context, startDate: String) {
        context.dataStore.edit { preferences ->
            preferences[LICENSE_START_DATE] = startDate
        }
    }

    suspend fun saveLicenseEndDate(context: Context, endDate: String) {
        context.dataStore.edit { preferences ->
            preferences[LICENSE_END_DATE] = endDate
        }
    }

    suspend fun saveLicenseInfo(
        context: Context,
        isLicensed: Boolean,
        endDate: String,
        licenseKey: String,
        edition: String = "Spire",
        capabilities: String = "cap1, cap2",
        licensedTo: String = "MockCustomer"
    ) {
        context.dataStore.edit { preferences ->
            preferences[IS_LICENSED] = isLicensed
            preferences[LICENSE_END_DATE] = endDate
            preferences[LICENSE_KEY] = licenseKey
            preferences[LICENSE_EDITION] = edition
            preferences[CAPABILITIES] = capabilities
            preferences[LICENSED_TO] = licensedTo
        }
    }

    suspend fun clearLicenseInfo(context: Context) {
        context.dataStore.edit { preferences ->
            preferences.remove(IS_LICENSED)
            preferences.remove(LICENSE_START_DATE)
            preferences.remove(LICENSE_END_DATE)
            preferences.remove(LICENSE_KEY)
            preferences.remove(LICENSE_EDITION)
            preferences.remove(CAPABILITIES)
            preferences.remove(LICENSED_TO)
        }
    }

    fun calculateEndDate(activationDate: String, validity: Int): String {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.timeZone = TimeZone.getDefault()
            val startDate = sdf.parse(activationDate)
                ?: throw IllegalArgumentException("Invalid activation_date")
            val calendar = Calendar.getInstance(TimeZone.getDefault())
            calendar.time = startDate
            calendar.add(Calendar.DAY_OF_MONTH, validity)
            val endDate = calendar.time
            val result = sdf.format(endDate)
            return result
        } catch (e: Exception) {
            return activationDate
        }
    }

    fun formatDate(dateStr: String?): String {
        if (dateStr.isNullOrEmpty()) return ""
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.timeZone = TimeZone.getDefault()
            val date = sdf.parse(dateStr) ?: return ""
            return sdf.format(date)
        } catch (e: Exception) {
            return ""
        }
    }

    /**
     * 解析日期字符串为Date对象
     */
    fun parseDateString(dateStr: String): java.util.Date {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        val date = sdf.parse(dateStr) ?: throw IllegalArgumentException("Invalid date format: $dateStr")
        
        // 设置为当天的23:59:59，确保许可证在到期日当天仍然有效
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        
        return calendar.time
    }
}
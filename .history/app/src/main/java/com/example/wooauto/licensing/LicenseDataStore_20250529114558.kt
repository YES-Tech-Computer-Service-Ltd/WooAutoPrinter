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

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "license_prefs")

object LicenseDataStore {

    private val IS_LICENSED = booleanPreferencesKey("is_licensed")
    private val LICENSE_START_DATE = stringPreferencesKey("license_start_date")
    private val LICENSE_END_DATE = stringPreferencesKey("license_end_date")
    private val LICENSE_KEY = stringPreferencesKey("license_key")
    private val LICENSE_EDITION = stringPreferencesKey("license_edition")
    private val CAPABILITIES = stringPreferencesKey("capabilities")
    private val LICENSED_TO = stringPreferencesKey("licensed_to")
    private val USER_EMAIL = stringPreferencesKey("user_email")

    fun isLicensed(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            val isLicensed = preferences[IS_LICENSED] ?: false
            if (!isLicensed) {
                println("isLicensed: Not licensed")
                return@map false
            }
            val endDateStr = preferences[LICENSE_END_DATE] ?: return@map false
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.timeZone = TimeZone.getDefault()
                val endDate = sdf.parse(endDateStr) ?: return@map false
                val calendar = Calendar.getInstance(TimeZone.getDefault())
                calendar.time = endDate
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endDateTime = calendar.time
                val currentCalendar = Calendar.getInstance(TimeZone.getDefault())
                val currentDateTime = currentCalendar.time
                val isValid = endDateTime.after(currentDateTime)
                println("isLicensed: endDate=$endDateStr, endDateTime=$endDateTime, currentDateTime=$currentDateTime, isValid=$isValid")
                isValid
            } catch (e: Exception) {
                println("isLicensed: Error parsing endDate=$endDateStr, ${e.message}")
                false
            }
        }
    }

    suspend fun setLicensed(context: Context, isLicensed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_LICENSED] = isLicensed
            Log.d("LicenseDataStore", "Setting isLicensed: $isLicensed")
        }
    }

    fun getLicenseStartDate(context: Context): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            val startDate = preferences[LICENSE_START_DATE]
            println("getLicenseStartDate: $startDate")
            startDate
        }
    }

    fun getLicenseEndDate(context: Context): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            val endDate = preferences[LICENSE_END_DATE]
            println("getLicenseEndDate: $endDate")
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

    fun getUserEmail(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs ->
            prefs[USER_EMAIL] ?: "user@example.com"
        }
    }

    suspend fun saveLicenseStartDate(context: Context, startDate: String) {
        context.dataStore.edit { preferences ->
            println("Saving startDate: $startDate")
            preferences[LICENSE_START_DATE] = startDate
        }
    }

    suspend fun saveLicenseEndDate(context: Context, endDate: String) {
        context.dataStore.edit { preferences ->
            println("Saving endDate: $endDate")
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
        licensedTo: String = "MockCustomer",
        email: String = "user@example.com"
    ) {
        context.dataStore.edit { preferences ->
            println("Saving licenseInfo: isLicensed=$isLicensed, endDate=$endDate, edition=$edition, capabilities=$capabilities, licensedTo=$licensedTo, email=$email")
            preferences[IS_LICENSED] = isLicensed
            preferences[LICENSE_END_DATE] = endDate
            preferences[LICENSE_KEY] = licenseKey
            preferences[LICENSE_EDITION] = edition
            preferences[CAPABILITIES] = capabilities
            preferences[LICENSED_TO] = licensedTo
            preferences[USER_EMAIL] = email
        }
    }

    suspend fun clearLicenseInfo(context: Context) {
        context.dataStore.edit { preferences ->
            println("Clearing licenseInfo")
            preferences.remove(IS_LICENSED)
            preferences.remove(LICENSE_START_DATE)
            preferences.remove(LICENSE_END_DATE)
            preferences.remove(LICENSE_KEY)
            preferences.remove(LICENSE_EDITION)
            preferences.remove(CAPABILITIES)
            preferences.remove(LICENSED_TO)
            preferences.remove(USER_EMAIL)
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
            println("calculateEndDate: activationDate=$activationDate, validity=$validity, endDate=$result")
            return result
        } catch (e: Exception) {
            println("calculateEndDate: Error processing $activationDate, validity=$validity, ${e.message}")
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
            println("formatDate: Error processing $dateStr, ${e.message}")
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
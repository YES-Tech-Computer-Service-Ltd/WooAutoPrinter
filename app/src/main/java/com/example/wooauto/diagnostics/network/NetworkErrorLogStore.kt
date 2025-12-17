package com.example.wooauto.diagnostics.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络报错日志持久化（DataStore）。
 * - 使用同一个全局 DataStore（woo_auto_preferences），避免新增 DataStore 文件与兼容风险。
 * - 仅保留“去重后的唯一问题”列表，避免无限增长。
 */
@Singleton
class NetworkErrorLogStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson
) {
    companion object {
        private val KEY_LOGS_JSON = stringPreferencesKey("network_error_logs_json_v1")

        const val DEFAULT_MAX_UNIQUE = 50
        const val DEFAULT_MAX_SAMPLES_PER_ENTRY = 5
        const val DEFAULT_MAX_DETAILS_CHARS = 8_000
    }

    fun logsFlow(): Flow<List<NetworkErrorLogEntry>> {
        return dataStore.data.map { prefs ->
            val raw = prefs[KEY_LOGS_JSON].orEmpty()
            if (raw.isBlank()) return@map emptyList()
            try {
                val type = object : TypeToken<List<NetworkErrorLogEntry>>() {}.type
                gson.fromJson<List<NetworkErrorLogEntry>>(raw, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_LOGS_JSON)
        }
    }

    suspend fun record(
        event: NetworkErrorEvent,
        maxUnique: Int = DEFAULT_MAX_UNIQUE,
        maxSamplesPerEntry: Int = DEFAULT_MAX_SAMPLES_PER_ENTRY
    ) {
        val safeMaxUnique = maxUnique.coerceIn(10, 200)
        val safeMaxSamples = maxSamplesPerEntry.coerceIn(1, 20)

        dataStore.edit { prefs ->
            val current = runCatching {
                val raw = prefs[KEY_LOGS_JSON].orEmpty()
                if (raw.isBlank()) emptyList()
                else {
                    val type = object : TypeToken<List<NetworkErrorLogEntry>>() {}.type
                    gson.fromJson<List<NetworkErrorLogEntry>>(raw, type) ?: emptyList()
                }
            }.getOrDefault(emptyList())

            val sampleLine = buildSampleLine(event)
            val updated = upsertAndTrim(
                current = current,
                event = event,
                sampleLine = sampleLine,
                maxUnique = safeMaxUnique,
                maxSamplesPerEntry = safeMaxSamples
            )

            val json = gson.toJson(updated).orEmpty()
            prefs[KEY_LOGS_JSON] = if (json.length <= DEFAULT_MAX_DETAILS_CHARS) {
                json
            } else {
                // 极端情况下（细节太长）进行截断保护，避免 DataStore 写入过大导致问题。
                // 这里不做“部分截断 JSON”（会破坏格式），直接只保留前 N 条。
                val reduced = updated.take((safeMaxUnique / 2).coerceAtLeast(10))
                gson.toJson(reduced).orEmpty()
            }
        }
    }

    private fun buildSampleLine(event: NetworkErrorEvent): String {
        val host = event.siteHost ?: "unknown-host"
        val codePart = event.httpCode?.let { "HTTP $it" } ?: event.issueType.name
        val endpoint = event.endpoint?.takeIf { it.isNotBlank() } ?: "-"
        return "${event.now} | $codePart | host=$host | endpoint=$endpoint"
    }

    private fun upsertAndTrim(
        current: List<NetworkErrorLogEntry>,
        event: NetworkErrorEvent,
        sampleLine: String,
        maxUnique: Int,
        maxSamplesPerEntry: Int
    ): List<NetworkErrorLogEntry> {
        val idx = current.indexOfFirst { it.fingerprint == event.fingerprint }

        val newDetails = event.details.take(DEFAULT_MAX_DETAILS_CHARS)

        val nextList = if (idx >= 0) {
            val old = current[idx]
            val samples = run {
                val base = old.recentSamples
                val appended = if (base.lastOrNull() == sampleLine) base else base + sampleLine
                if (appended.size > maxSamplesPerEntry) appended.takeLast(maxSamplesPerEntry) else appended
            }

            val updated = old.copy(
                issueType = event.issueType,
                title = event.title,
                summary = event.summary,
                lastSeenAt = event.now,
                count = old.count + 1,
                lastDetails = newDetails,
                lastNetworkSnapshot = event.networkSnapshot,
                recentSamples = samples
            )

            // Move to top
            buildList {
                add(updated)
                current.forEachIndexed { i, e ->
                    if (i != idx) add(e)
                }
            }
        } else {
            val created = NetworkErrorLogEntry(
                fingerprint = event.fingerprint,
                issueType = event.issueType,
                title = event.title,
                summary = event.summary,
                firstSeenAt = event.now,
                lastSeenAt = event.now,
                count = 1,
                lastDetails = newDetails,
                lastNetworkSnapshot = event.networkSnapshot,
                recentSamples = listOf(sampleLine).takeLast(maxSamplesPerEntry)
            )
            listOf(created) + current
        }

        // Trim by most-recent first
        return nextList
            .sortedByDescending { it.lastSeenAt }
            .take(maxUnique)
    }
}



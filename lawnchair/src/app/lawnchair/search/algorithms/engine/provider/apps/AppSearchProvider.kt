package app.lawnchair.search.algorithms.engine.provider.apps

import android.content.Context
import android.content.ComponentName
import android.content.SharedPreferences
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.algorithms.engine.SearchResult
import app.lawnchair.search.algorithms.filterHiddenApps
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.search.StringMatcherUtility
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln


/** Lightweight, offline preference ranking used by Nexus Launcher.
 * It deliberately uses a handful of signals instead of shipping a neural model.
 */


/**
 * Offline AI-style ranking layer. It combines lexical relevance with launch frequency
 * and recency without shipping a heavyweight model.
 */
private object NexusAiRanker {
    fun rank(
        context: Context,
        query: String,
        apps: List<AppInfo>,
    ): List<AppInfo> {
        if (apps.size < 2) return apps
        val prefs = context.getSharedPreferences("nexus_app_preferences", Context.MODE_PRIVATE)
        val normalized = query.trim().lowercase(Locale.getDefault())
        return apps.withIndex()
            .map { indexed ->
                val app = indexed.value
                val key = app.componentName.flattenToString()
                val launches = prefs.getLong("launch:$key", 0L).coerceAtMost(10_000L)
                val last = prefs.getLong("last:$key", 0L)
                val ageHours = if (last == 0L) Double.POSITIVE_INFINITY else
                    ((System.currentTimeMillis() - last).coerceAtLeast(0L)).toDouble() / 3_600_000.0
                val recency = if (ageHours.isFinite()) 1.0 / (1.0 + ageHours) else 0.0
                val title = stripForRanking(app.title.toString())
                val lexical = when {
                    normalized.isBlank() -> 0.0
                    title == normalized -> 1.0
                    title.startsWith(normalized) -> 0.75
                    title.contains(normalized) -> 0.5
                    else -> 0.0
                }
                val behavior = ln(1.0 + launches.toDouble()) / 10.0 + recency
                IndexedValueScore(indexed.index, app, lexical * 2.0 + behavior)
            }
            .sortedWith(compareByDescending<IndexedValueScore> { it.score }.thenBy { it.index })
            .map { it.app }
    }

    private fun stripForRanking(input: String): String =
        Normalizer.normalize(input, Normalizer.Form.NFKD)
            .replace(DIACRITICS_REMOVE_PATTERN, "")
            .lowercase(Locale.getDefault())

    private data class IndexedValueScore(
        val index: Int,
        val app: AppInfo,
        val score: Double,
    )
}

private object NexusPreferenceBrain {
    private const val PREFS_NAME = "nexus_app_preferences"
    private const val KEY_PREFIX = "launch:"
    private const val KEY_LAST_PREFIX = "last:"

    fun rank(context: Context, apps: List<AppInfo>): List<AppInfo> {
        if (apps.size < 2) return apps
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return apps.withIndex()
            .sortedWith(compareByDescending<IndexedValue<AppInfo>> { (_, app) ->
                val key = app.componentName.flattenToString()
                val launches = prefs.getLong(KEY_PREFIX + key, 0L).coerceAtMost(10_000L)
                val last = prefs.getLong(KEY_LAST_PREFIX + key, 0L)
                val recencyHours = ((System.currentTimeMillis() - last).coerceAtLeast(0L) / 3_600_000L).coerceAtMost(24L * 30L)
                val recencyScore = if (last == 0L) 0.0 else 1.0 / (1.0 + recencyHours)
                ln(1.0 + launches.toDouble()) * 0.65 + recencyScore * 0.35
            }.thenBy { it.index })
            .map { it.value }
    }

    fun recordLaunch(context: Context, componentName: ComponentName) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = componentName.flattenToString()
        prefs.edit()
            .putLong(KEY_PREFIX + key, prefs.getLong(KEY_PREFIX + key, 0L) + 1L)
            .putLong(KEY_LAST_PREFIX + key, System.currentTimeMillis())
            .apply()
    }
}

object AppSearchProvider {

    private val DIACRITICS_REMOVE_PATTERN = "\\p{M}+".toRegex()

    fun search(context: Context, query: String, allApps: AllAppsList): List<SearchResult.App> {
        val prefs = PreferenceManager2.getInstance(context)
        val hiddenApps = prefs.hiddenApps.firstCached()
        val hiddenAppsInSearch = prefs.hiddenAppsInSearch.firstCached()
        val maxAppResults = prefs.maxAppSearchResultCount.firstCached()
        val enableFuzzySearch = prefs.enableFuzzySearch.firstCached()

        val queryNormalized = stripDiacritics(query).lowercase(Locale.getDefault())

        val appResults = if (enableFuzzySearch) {
            fuzzySearch(allApps.data, queryNormalized, maxAppResults, hiddenApps, hiddenAppsInSearch)
        } else {
            normalSearch(allApps.data, queryNormalized, maxAppResults, hiddenApps, hiddenAppsInSearch)
        }

        val rankedResults = NexusAiRanker.rank(context, query, appResults)
        return rankedResults.map { SearchResult.App(data = it) }
    }

    private fun normalSearch(apps: List<AppInfo>, query: String, maxResultsCount: Int, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<AppInfo> {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        val matcher = StringMatcherUtility.StringMatcher.getInstance()
        return apps.asSequence()
            .filter { StringMatcherUtility.matches(query, stripDiacritics(it.title.toString()), matcher) }
            .filterHiddenApps(query, hiddenApps, hiddenAppsInSearch)
            .take(maxResultsCount)
            .toList()
    }

    private fun fuzzySearch(apps: List<AppInfo>, query: String, maxResultsCount: Int, hiddenApps: Set<String>, hiddenAppsInSearch: String): List<AppInfo> {
        val filteredApps = apps.asSequence()
            .filterHiddenApps(query, hiddenApps, hiddenAppsInSearch)
            .toList()

        return filteredApps
            .mapNotNull { app ->
                val matchResult = AppMatcher.match(stripDiacritics(app.title.toString()), query)
                if (matchResult.type == MatchType.NO_MATCH) null else Pair(app, matchResult)
            }
            .sortedWith(
                compareBy(
                    { it.second.type.priority },
                    { -it.second.score },
                ),
            )
            .map { it.first }
            .take(maxResultsCount)
    }

    private fun stripDiacritics(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFKD)
            .replace(DIACRITICS_REMOVE_PATTERN, "")
    }
}

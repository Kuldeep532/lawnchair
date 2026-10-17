package app.lawnchair.search.algorithms

import android.content.Context
import android.net.Uri
import app.lawnchair.preferences.PreferenceManager
import android.os.Handler
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.adapter.SPACE
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.SearchTargetFactory
import app.lawnchair.util.isDefaultLauncher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.allapps.BaseAllAppsAdapter
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.util.Executors
import com.patrykmichalik.opto.core.onEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

class LawnchairAppSearchAlgorithm(context: Context) : LawnchairSearchAlgorithm(context) {

    private val appState = LauncherAppState.getInstance(context)
    private val resultHandler = Handler(Executors.MAIN_EXECUTOR.looper)

    // todo maybe use D.I.?
    private val searchTargetFactory = SearchTargetFactory(context)

    private var hiddenApps: Set<String> = setOf()

    private var hiddenAppsInSearch = ""
    private var enableFuzzySearch = false
    private var maxResultsCount = 5

    private val prefs2 = PreferenceManager2.getInstance(context)

    val coroutineScope = CoroutineScope(context = Dispatchers.IO)

    private var modelRetryJob: Job? = null
    private var currentJob: Job? = null

    init {
        prefs2.enableFuzzySearch.onEach(launchIn = coroutineScope) {
            enableFuzzySearch = it
        }
        prefs2.hiddenApps.onEach(launchIn = coroutineScope) {
            hiddenApps = it
        }
        prefs2.hiddenAppsInSearch.onEach(launchIn = coroutineScope) {
            hiddenAppsInSearch = it
        }
        prefs2.maxAppSearchResultCount.onEach(launchIn = coroutineScope) {
            maxResultsCount = it
        }
    }

    override fun doSearch(query: String, callback: SearchCallback<BaseAllAppsAdapter.AdapterItem>) {
        modelRetryJob?.cancel()
        modelRetryJob = appState.model.runWhenModelLoaded(coroutineScope) { apps ->
            // Track the delivery job so cancel(true) (e.g. from a drawer close) can stop a
            // late-arriving result from being delivered after reset. See issue #7325.
            currentJob?.cancel()
            currentJob = coroutineScope.launch(Dispatchers.Main) {
                val results = getResult(apps.data, query)
                callback.onSearchResult(query, results)
            }
        }
    }

    override fun cancel(interruptActiveRequests: Boolean) {
        if (interruptActiveRequests) {
            modelRetryJob?.cancel()
            currentJob?.cancel()
            resultHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun getResult(
        apps: MutableList<AppInfo>,
        query: String,
    ): ArrayList<BaseAllAppsAdapter.AdapterItem> {
        val appQuery = query.trim().let { value ->
            if (value.startsWith("app:", ignoreCase = true)) value.substringAfter(':').trim() else value
        }
        val appResults = if (enableFuzzySearch) {
            SearchUtils.fuzzySearch(apps, appQuery, maxResultsCount, hiddenApps, hiddenAppsInSearch)
        } else {
            SearchUtils.normalSearch(apps, appQuery, maxResultsCount, hiddenApps, hiddenAppsInSearch)
        }

        val searchTargets = mutableListOf<SearchTargetCompat>()

        if (appResults.isNotEmpty()) {
            if (appResults.size == 1 && context.isDefaultLauncher()) {
                val singleAppResult = appResults.firstOrNull()
                val shortcuts = singleAppResult?.let { SearchUtils.getShortcuts(it, context) }
                if (shortcuts != null && shortcuts.isNotEmpty()) {
                    // Show app as a row alongside its shortcuts (no duplicate icon)
                    singleAppResult.let { searchTargets.add(searchTargetFactory.createAppSearchTarget(it, true)) }
                    searchTargets.addAll(shortcuts.map(searchTargetFactory::createShortcutTarget))
                } else {
                    // No shortcuts: show as icon
                    appResults.mapTo(searchTargets, searchTargetFactory::createAppSearchTarget)
                }
            } else {
                // Multiple results: show as icons
                appResults.mapTo(searchTargets, searchTargetFactory::createAppSearchTarget)
            }
            searchTargets.add(searchTargetFactory.createHeaderTarget(SPACE))
        }

        // Keep the standard app-search mode useful as a universal launcher search.
        // The existing "Web search suggestions" switch remains the user's opt-out.
        if (PreferenceManager.getInstance(context).searchResultStartPageSuggestion.get()) {
            val normalized = query.trim()
            val lower = normalized.lowercase(Locale.ROOT)
            val musicQuery = when {
                lower.startsWith("music:") -> normalized.substringAfter(':').trim()
                lower.startsWith("music ") -> normalized.substringAfter(' ').trim()
                lower.startsWith("म्यूजिक ") -> normalized.substringAfter(' ').trim()
                else -> null
            }
            val videoQuery = when {
                lower.startsWith("video:") -> normalized.substringAfter(':').trim()
                lower.startsWith("video ") -> normalized.substringAfter(' ').trim()
                lower.startsWith("videos ") -> normalized.substringAfter(' ').trim()
                lower.startsWith("वीडियो ") -> normalized.substringAfter(' ').trim()
                else -> null
            }
            val appQuery = when {
                lower.startsWith("app:") -> normalized.substringAfter(':').trim()
                lower.startsWith("app ") -> normalized.substringAfter(' ').trim()
                else -> null
            }

            when {
                !musicQuery.isNullOrBlank() -> searchTargets.add(
                    searchTargetFactory.createWebSearchActionTarget(
                        query = musicQuery,
                        providerName = "Music apps",
                        searchUrl = "https://music.youtube.com/search?q=" + Uri.encode(musicQuery),
                        providerIconRes = com.android.launcher3.R.drawable.ic_search,
                        tintIcon = true,
                    ),
                )
                !videoQuery.isNullOrBlank() -> searchTargets.add(
                    searchTargetFactory.createWebSearchActionTarget(
                        query = videoQuery,
                        providerName = "YouTube Videos",
                        searchUrl = "https://www.youtube.com/results?search_query=" + Uri.encode(videoQuery),
                        providerIconRes = com.android.launcher3.R.drawable.ic_search,
                        tintIcon = true,
                    ),
                )
                !appQuery.isNullOrBlank() -> searchTargets.add(
                    searchTargetFactory.createWebSearchActionTarget(
                        query = "$appQuery app",
                        providerName = "Google",
                        searchUrl = "https://www.google.com/search?q=" + Uri.encode("$appQuery app"),
                        providerIconRes = com.android.launcher3.R.drawable.ic_search,
                        tintIcon = true,
                    ),
                )
                else -> searchTargets.add(
                    searchTargetFactory.createWebSearchActionTarget(
                        query = normalized,
                        providerName = "Google",
                        searchUrl = "https://www.google.com/search?q=" + Uri.encode(normalized),
                        providerIconRes = com.android.launcher3.R.drawable.ic_search,
                        tintIcon = true,
                    ),
                )
            }
        }

        searchTargetFactory.createMarketSearchTarget(query)?.let { searchTargets.add(it) }

        setFirstItemQuickLaunch(searchTargets)
        val adapterItems = transformSearchResults(searchTargets)
        return ArrayList(adapterItems)
    }
}

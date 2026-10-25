package com.example.searcher

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Xml
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.ScriptHandler
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    enum class TaskMode { IDLE, SEARCHING, READING, DAILY_SET, MORE_ACTIVITIES }

    private var currentMode = TaskMode.IDLE
    private var isAutoAllActive = false

    private val fallbackTerms = listOf(
        "Elden Ring walkthrough", "Mario Kart 8 tracks", "Gran Turismo 7 updates",
        "Street Fighter 6 tiers", "Hades 2 release", "Toyota Corolla hybrid review",
        "Honda Civic type r specs", "Ford Mustang mach e", "Mazda MX-5 miata tuning",
        "Subaru Impreza wrx", "Bohemian Rhapsody lyrics meaning",
        "Hotel California acoustic chords", "Purple Rain live solo",
        "Imagine John Lennon piano", "Hey Jude vocal tracks",
        "Inception ending explained", "Interstellar soundtrack vinyl",
        "The Matrix 4k remaster", "Spirited Away analysis",
        "Parasite movie symbolism", "NASA James Webb discoveries",
        "Quantum computing explained", "CRISPR gene therapy news",
        "Deep sea bioluminescence", "Mars Rover sample update",
        "Best mechanical keyboards 2026", "Types of espresso beans",
        "How do solar panels work", "Electric vehicle battery life",
        "Artificial intelligence future", "Healthy mediterranean recipes",
        "Best budget travel destinations", "History of ancient Rome",
        "Origami folding techniques", "Nordic minimalist interior",
        "Top rated science fiction books", "Beginner strength training guide",
        "Photography composition rules", "Mount Everest climbing routes",
        "Sourdough bread starter tips"
    )
    private val searchQueue = mutableListOf<String>()
    private val dailyTrendingTerms = mutableListOf<String>()
    private var loadingTrendingTerms = false
    private var searchStartPending = false
    private var dailyTrendingDate: String? = null

    private val limits = listOf(10, 20, 35, 50)
    private val intervals = listOf(10, 20, 30, 60)
    private val readLimits = listOf(5, 10, 15, 20, 30)
    private val readIntervals = listOf(5, 8, 10, 12, 15, 20)
    private var selectedReadLimitIndex = 1
    private var selectedReadIntervalIndex = 2

    private val fallbackMsnArticles = listOf(
        "https://www.msn.com/en-us/news/world",
        "https://www.msn.com/en-us/news/technology",
        "https://www.msn.com/en-us/news/national",
        "https://www.msn.com/en-us/news/science",
        "https://www.msn.com/en-us/sports",
        "https://www.msn.com/en-us/money",
        "https://www.msn.com/en-us/entertainment",
        "https://www.msn.com/en-us/health",
        "https://www.msn.com/en-us/travel",
        "https://www.msn.com/en-us/autos",
        "https://www.msn.com/en-us/weather",
        "https://www.msn.com/en-us/lifestyle",
        "https://www.msn.com/en-us/food-and-drink",
        "https://www.msn.com/en-us/news/good-news",
        "https://www.msn.com/en-us/news/crime"
    )

    private val MSN_LOCATIONS_API =
        "https://api.msn.com/v1/news/users/me/locations?ocid=superapp-bridge-bing&apikey=f4HpgEaGwL3xg1Q3UHvHAUwyAomQnDYsIiYQKqn1sH&activityId=0903EE9BF4C04A2898794DA81B2BBC1C&cm=en-ph&it=app&scn=APP_ANON&timeout=1000&autodetect=true"
    private val BING_SAPPHIRE_UA =
        "Mozilla/5.0 (Linux; Android 13; 21061119AG Build/TQ3C.230901.001.B1; ) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/155.0.8059.43 Mobile Safari/537.36 BingSapphire/34.3.441014006"

    private val handler = Handler(Looper.getMainLooper())
    private var currentArticleIndex = 0
    private var activeArticles = mutableListOf<String>()

    data class RewardActivity(val title: String, val url: String, val isDaily: Boolean)

    private var pendingActivities = mutableListOf<RewardActivity>()
    private var totalActivitiesCount = 0
    private var done = 0
    private var signedIn = false
    private var isFullscreen = false
    private var desktopScript: ScriptHandler? = null
    private var pointsWebView: WebView? = null
    private var refreshTimeoutRunnable: Runnable? = null
    private lateinit var prefs: SharedPreferences

    private val DESKTOP_START_JS = """
        Object.defineProperty(navigator,'maxTouchPoints',{get:function(){return 0}});
        Object.defineProperty(navigator,'platform',{get:function(){return 'Win32'}});
    """
    private val DESKTOP_JS = """(function(){var m=document.querySelector('meta[name=viewport]');
        if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}
        m.setAttribute('content','width=1280');})();"""

    private val backCb = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (isFullscreen) exitFullscreen()
            else if (::web.isInitialized && web.canGoBack()) web.goBack()
            else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = backCheckbox.isChecked
            }
        }
    }

    // ALL declared as View — no ImageButton anywhere
    private lateinit var web: WebView
    private lateinit var progress: TextView
    private lateinit var toggle: MaterialButton
    private lateinit var bar: LinearProgressIndicator
    private lateinit var total: TextView
    private lateinit var lifetime: TextView
    private lateinit var claim: TextView
    private lateinit var accountStatus: TextView
    private lateinit var accountEmail: TextView
    private lateinit var signin: MaterialButton
    private lateinit var refresh: MaterialButton
    private lateinit var openFull: MaterialButton
    private lateinit var webCard: MaterialCardView
    private lateinit var fullOverlay: View
    private lateinit var fullContainer: FrameLayout
    private lateinit var fullTitle: TextView
    private lateinit var closeFull: View          // NOT ImageButton
    private lateinit var moreFull: View            // NOT ImageButton
    private lateinit var backCheckbox: CheckBox
    private lateinit var limitDropdown: AutoCompleteTextView
    private lateinit var intervalDropdown: AutoCompleteTextView
    private lateinit var readLimitDropdown: AutoCompleteTextView
    private lateinit var readIntervalDropdown: AutoCompleteTextView
    private lateinit var readEarnBtn: MaterialButton
    private lateinit var dailySetBtn: MaterialButton
    private lateinit var activitiesBtn: MaterialButton
    private lateinit var autoAllBtn: MaterialButton
    private var desktopSwitch: CompoundButton? = null
    private var selectedLimitIndex = 2
    private var selectedIntervalIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("searcher_prefs", Context.MODE_PRIVATE)
        selectedReadLimitIndex =
            prefs.getInt("READ_LIMIT_IDX", 1).coerceIn(0, readLimits.size - 1)
        selectedReadIntervalIndex =
            prefs.getInt("READ_INTERVAL_IDX", 2).coerceIn(0, readIntervals.size - 1)

        onBackPressedDispatcher.addCallback(this, backCb)

        // Bind views — all use their real types; closeFull/moreFull are View
        web = findViewById(R.id.web)
        progress = findViewById(R.id.progress)
        toggle = findViewById(R.id.toggle)
        bar = findViewById(R.id.bar)
        total = findViewById(R.id.total)
        lifetime = findViewById(R.id.lifetime)
        claim = findViewById(R.id.claim)
        accountStatus = findViewById(R.id.accountStatus)
        accountEmail = findViewById(R.id.accountEmail)
        signin = findViewById(R.id.signin)
        refresh = findViewById(R.id.refresh)
        limitDropdown = findViewById(R.id.limitDropdown)
        intervalDropdown = findViewById(R.id.intervalDropdown)
        readLimitDropdown = findViewById(R.id.readLimitDropdown)
        readIntervalDropdown = findViewById(R.id.readIntervalDropdown)
        readEarnBtn = findViewById(R.id.readEarnBtn)
        dailySetBtn = findViewById(R.id.dailySetBtn)
        activitiesBtn = findViewById(R.id.activitiesBtn)
        autoAllBtn = findViewById(R.id.autoAllBtn)
        openFull = findViewById(R.id.openFull)
        webCard = findViewById(R.id.webCard)
        fullOverlay = findViewById(R.id.fullOverlay)
        fullContainer = findViewById(R.id.fullContainer)
        fullTitle = findViewById(R.id.fullTitle)
        closeFull = findViewById(R.id.closeFull)
        moreFull = findViewById(R.id.moreFull)
        backCheckbox = findViewById(R.id.fullscreenBackCb)
        desktopSwitch = findViewById(R.id.desktopSwitch)

        backCheckbox.setOnCheckedChangeListener { _, checked ->
            backCb.isEnabled = checked || isFullscreen
        }

        // Smooth press on all interactive buttons
        listOf<View>(
            signin, refresh, readEarnBtn, openFull, toggle,
            closeFull, moreFull, dailySetBtn, activitiesBtn, autoAllBtn
        ).forEach { it.addSmoothPressAnimation() }

        // Click listeners — every feature is one visible tap
        toggle.setOnClickListener {
            if (currentMode != TaskMode.IDLE) stop() else startSearch()
        }
        autoAllBtn.setOnClickListener {
            if (isAutoAllActive) stop() else startAutoAll()
        }
        readEarnBtn.setOnClickListener {
            if (currentMode == TaskMode.READING) stop() else startReadToEarn()
        }
        dailySetBtn.setOnClickListener {
            if (currentMode == TaskMode.DAILY_SET) stop()
            else startRewardsActivities(onlyDaily = true)
        }
        activitiesBtn.setOnClickListener {
            if (currentMode == TaskMode.MORE_ACTIVITIES) stop()
            else startRewardsActivities(onlyDaily = false)
        }

        // --- SEARCH DROPDOWNS ---
        val limitAdapter = ArrayAdapter(
            this, android.R.layout.simple_dropdown_item_1line,
            limits.map { "$it searches" })
        limitDropdown.setAdapter(limitAdapter)
        limitDropdown.setText(
            "${limits.getOrElse(selectedLimitIndex) { 35 }} searches", false
        )
        limitDropdown.setOnItemClickListener { _, _, pos, _ ->
            val t = limitAdapter.getItem(pos) ?: ""
            val c = t.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 35
            selectedLimitIndex = limits.indexOf(c).takeIf { it >= 0 } ?: 2
            bar.max = c
            limitDropdown.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        val intervalAdapter = ArrayAdapter(
            this, android.R.layout.simple_dropdown_item_1line,
            intervals.map { "$it seconds" })
        intervalDropdown.setAdapter(intervalAdapter)
        intervalDropdown.setText(
            "${intervals.getOrElse(selectedIntervalIndex) { 10 }} seconds", false
        )
        intervalDropdown.setOnItemClickListener { _, _, pos, _ ->
            val t = intervalAdapter.getItem(pos) ?: ""
            val s = t.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 10
            selectedIntervalIndex = intervals.indexOf(s).takeIf { it >= 0 } ?: 0
            intervalDropdown.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        // --- READ TO EARN DROPDOWNS ---
        val readLimitAdapter = ArrayAdapter(
            this, android.R.layout.simple_dropdown_item_1line,
            readLimits.map { "$it articles" })
        readLimitDropdown.setAdapter(readLimitAdapter)
        readLimitDropdown.setText(
            "${readLimits.getOrElse(selectedReadLimitIndex) { 10 }} articles", false
        )
        readLimitDropdown.setOnItemClickListener { _, _, pos, _ ->
            val t = readLimitAdapter.getItem(pos) ?: ""
            val c = t.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 10
            selectedReadLimitIndex = readLimits.indexOf(c).takeIf { it >= 0 } ?: 1
            prefs.edit().putInt("READ_LIMIT_IDX", selectedReadLimitIndex).apply()
            readLimitDropdown.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        val readIntervalAdapter = ArrayAdapter(
            this, android.R.layout.simple_dropdown_item_1line,
            readIntervals.map { "$it seconds" })
        readIntervalDropdown.setAdapter(readIntervalAdapter)
        readIntervalDropdown.setText(
            "${readIntervals.getOrElse(selectedReadIntervalIndex) { 10 }} seconds", false
        )
        readIntervalDropdown.setOnItemClickListener { _, _, pos, _ ->
            val t = readIntervalAdapter.getItem(pos) ?: ""
            val s = t.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 10
            selectedReadIntervalIndex = readIntervals.indexOf(s).takeIf { it >= 0 } ?: 2
            prefs.edit().putInt("READ_INTERVAL_IDX", selectedReadIntervalIndex).apply()
            readIntervalDropdown.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        // --- WEBVIEW SETUP ---
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.setSupportZoom(true)
        web.settings.builtInZoomControls = true
        web.settings.displayZoomControls = false
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        val initialDesktop = savedInstanceState?.getBoolean("DESKTOP", true) ?: true
        desktopSwitch?.isChecked = initialDesktop
        applyDesktop(initialDesktop)

        desktopSwitch?.setOnCheckedChangeListener { v, on ->
            applyDesktop(on)
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.setOnTouchListener { v, e ->
            v.parent.requestDisallowInterceptTouchEvent(
                e.action != MotionEvent.ACTION_UP && e.action != MotionEvent.ACTION_CANCEL
            )
            false
        }

        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String) {
                if (desktopSwitch?.isChecked == true && currentMode != TaskMode.READING)
                    v.evaluateJavascript(DESKTOP_JS, null)
                if (url.contains("rewards.bing.com/dashboard") && !signedIn)
                    handler.postDelayed({ refreshPoints() }, 1500)
            }
        }

        signin.setOnClickListener {
            if (signedIn) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Log out?")
                    .setMessage("This removes the Microsoft account from this app.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Log out") { _, _ -> logout() }
                    .show()
            } else {
                web.loadUrl("https://rewards.bing.com")
                accountStatus.text = "Opening Bing Rewards sign-in..."
            }
        }

        openFull.setOnClickListener { enterFullscreen() }
        closeFull.setOnClickListener { exitFullscreen() }
        moreFull.setOnClickListener { showMenu(it) }
        refresh.setOnClickListener { refreshPoints() }

        bar.max = limits.getOrElse(selectedLimitIndex) { 35 }
        bar.progress = 0
        refreshPoints()

        if (savedInstanceState != null) {
            val wasRunning = savedInstanceState.getBoolean("RUNNING", false)
            done = savedInstanceState.getInt("DONE", 0)
            selectedLimitIndex =
                savedInstanceState.getInt("LIMIT_IDX", 2).coerceIn(0, limits.size - 1)
            selectedIntervalIndex =
                savedInstanceState.getInt("INT_IDX", 0).coerceIn(0, intervals.size - 1)
            limitDropdown.setText("${limits[selectedLimitIndex]} searches", false)
            intervalDropdown.setText("${intervals[selectedIntervalIndex]} seconds", false)
            bar.max = limits[selectedLimitIndex]
            bar.progress = done
            if (wasRunning) startSearch() else updateUIForMode(TaskMode.IDLE)
        }
    }

    private fun View.addSmoothPressAnimation() {
        setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(100).start()
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("RUNNING", currentMode != TaskMode.IDLE)
        outState.putInt("DONE", done)
        outState.putInt("LIMIT_IDX", selectedLimitIndex)
        outState.putInt("INT_IDX", selectedIntervalIndex)
        outState.putBoolean("DESKTOP", desktopSwitch?.isChecked ?: true)
    }

    private fun tintButton(btn: MaterialButton, isStop: Boolean) {
        btn.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (isStop) R.color.stop_btn else R.color.start_btn)
        )
    }

    private fun updateUIForMode(mode: TaskMode) {
        toggle.text =
            if (mode == TaskMode.SEARCHING) "Stop Searches" else "Start Searches"
        tintButton(toggle, mode == TaskMode.SEARCHING)
        readEarnBtn.text =
            if (mode == TaskMode.READING) "Stop Reading" else "Start Read to Earn"
        dailySetBtn.text =
            if (mode == TaskMode.DAILY_SET) "Stop Daily Set" else "Daily Set"
        activitiesBtn.text =
            if (mode == TaskMode.MORE_ACTIVITIES) "Stop Activities" else "More Activities"
        autoAllBtn.text =
            if (isAutoAllActive) "Stop Auto Pilot" else "Run All"
        if (mode == TaskMode.IDLE)
            progress.text = if (done > 0) "Stopped at $done" else "Ready"
    }

    private fun nextSearchTerm(): String {
        if (searchQueue.isEmpty()) {
            val terms = dailyTrendingTerms.ifEmpty { fallbackTerms }
            searchQueue.addAll(terms.shuffled())
        }
        return searchQueue.removeAt(0)
    }

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Manila")
    }.format(Date())

    private fun startSearchWhenTrendsReady() {
        val today = todayKey()
        if (dailyTrendingDate == today && dailyTrendingTerms.isNotEmpty()) {
            stepSearch()
            return
        }
        dailyTrendingTerms.clear()

        val savedDate = prefs.getString("TREND_DATE", null)
        val savedTerms = prefs.getString("TREND_TERMS", null)
        if (savedDate == today && !savedTerms.isNullOrBlank()) {
            try {
                val saved = JSONArray(savedTerms)
                for (i in 0 until saved.length()) {
                    saved.optString(i).takeIf { it.isNotBlank() }?.let(dailyTrendingTerms::add)
                }
            } catch (_: Exception) {}
            if (dailyTrendingTerms.isNotEmpty()) {
                dailyTrendingDate = today
                stepSearch()
                return
            }
        }

        if (loadingTrendingTerms) {
            searchStartPending = true
            progress.text = "Loading today's trending searches..."
            return
        }

        loadingTrendingTerms = true
        searchStartPending = true
        progress.text = "Loading today's trending searches..."
        Thread {
            val terms = fetchDailyTrendingTerms()
            runOnUiThread {
                loadingTrendingTerms = false
                if (terms.isNotEmpty()) {
                    dailyTrendingTerms.clear()
                    dailyTrendingTerms.addAll(terms)
                    dailyTrendingDate = today
                    prefs.edit()
                        .putString("TREND_DATE", today)
                        .putString("TREND_TERMS", JSONArray(terms).toString())
                        .apply()
                }
                if (searchStartPending && currentMode == TaskMode.SEARCHING &&
                    !isFinishing && !isDestroyed
                ) {
                    searchStartPending = false
                    stepSearch()
                }
            }
        }.start()
    }

    private fun fetchDailyTrendingTerms(): List<String> {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("https://trends.google.com/trending/rss?geo=PH")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            }
            if (connection.responseCode !in 200..299) return emptyList()

            val parser = Xml.newPullParser()
            parser.setInput(connection.inputStream, "UTF-8")
            val terms = LinkedHashSet<String>()
            var inItem = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && terms.size < 50) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "item" -> inItem = true
                        "title" -> if (inItem) {
                            val title = parser.nextText().trim()
                            if (title.isNotEmpty()) terms.add(title)
                        }
                    }
                } else if (event == XmlPullParser.END_TAG && parser.name == "item") {
                    inItem = false
                }
                event = parser.next()
            }
            terms.toList()
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    // ==========================================
    // DESKTOP / MOBILE UA
    // ==========================================
    private fun applyDesktop(on: Boolean) {
        try {
            if (desktopScript != null &&
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            ) desktopScript?.remove()
        } catch (_: Exception) {}
        desktopScript = null
        try {
            if (on) {
                web.settings.userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"
                web.settings.useWideViewPort = true
                web.settings.loadWithOverviewMode = true
                if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA))
                    try {
                        WebSettingsCompat.setUserAgentMetadata(
                            web.settings, desktopMetadata()
                        )
                    } catch (_: Exception) {}
                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
                    try {
                        desktopScript = WebViewCompat.addDocumentStartJavaScript(
                            web, DESKTOP_START_JS, setOf("*")
                        )
                    } catch (_: Exception) {}
            } else {
                web.settings.userAgentString = null
                web.settings.useWideViewPort = false
                web.settings.loadWithOverviewMode = false
                if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA))
                    try {
                        WebSettingsCompat.setUserAgentMetadata(
                            web.settings, UserAgentMetadata.Builder().build()
                        )
                    } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        val u = web.url
        if (!u.isNullOrEmpty() && u != "about:blank")
            try { web.reload() } catch (_: Exception) {}
    }

    private fun desktopMetadata() = UserAgentMetadata.Builder()
        .setPlatform("Windows").setPlatformVersion("10.0")
        .setArchitecture("x86").setBitness(64)
        .setModel("").setMobile(false).build()

    // ==========================================
    // 1. AUTO PILOT
    // ==========================================
    private fun startAutoAll() {
        if (currentMode != TaskMode.IDLE) stop()
        isAutoAllActive = true
        autoAllBtn.text = "Stop Auto Pilot"
        startSearch()
    }

    // ==========================================
    // 2. BING SEARCHES
    // ==========================================
    private fun startSearch() {
        if (currentMode != TaskMode.IDLE) stop()
        currentMode = TaskMode.SEARCHING
        done = 0
        bar.max = limits.getOrNull(selectedLimitIndex) ?: 35
        bar.setProgressCompat(0, false)
        updateUIForMode(TaskMode.SEARCHING)
        searchQueue.clear()
        searchStartPending = false
        startSearchWhenTrendsReady()
    }

    private fun stepSearch() {
        val max = limits.getOrNull(selectedLimitIndex) ?: 35
        bar.max = max
        if (currentMode != TaskMode.SEARCHING || done >= max || isFinishing || isDestroyed) {
            if (isAutoAllActive) { currentMode = TaskMode.IDLE; startReadToEarn() }
            else stop()
            return
        }
        val term = nextSearchTerm()
        val searchUrl = "https://www.bing.com/search?q=${
            URLEncoder.encode(term.lowercase(), "UTF-8")
        }&form=NPII01&ssp=1&darkschemeovr=1&safesearch=moderate&setlang=en&cc=ph&PC=SAADAND"
        web.loadUrl(searchUrl)
        done++
        bar.setProgressCompat(done, true)
        progress.text = "Searching ($done/$max): $term"

        handler.postDelayed({
            if (currentMode == TaskMode.SEARCHING && !isFinishing && !isDestroyed) {
                try {
                    web.evaluateJavascript(
                        """(function(){
                        try {
                            fetch('https://prod.rewardsplatform.microsoft.com/dapi/me?channel=SAAndroid&options=580', {credentials:'include'}).catch(function(){});
                            fetch('https://www.bing.com/rewardsapp/reportActivity', {credentials:'include'}).catch(function(){});
                            fetch('https://dove.sapphire.microsoftapp.net/api/v1/query/update?market=en-ph', {credentials:'include'}).catch(function(){});
                        } catch(e){}
                        })()""", null
                    )
                } catch (_: Exception) {}
            }
        }, 1200L)

        handler.postDelayed(
            { stepSearch() },
            (intervals.getOrNull(selectedIntervalIndex) ?: 10) * 1000L
        )
    }

    // ==========================================
    // 3. READ TO EARN
    // ==========================================
    private fun startReadToEarn() {
        if (currentMode != TaskMode.IDLE && currentMode != TaskMode.READING) stop()
        currentMode = TaskMode.READING
        currentArticleIndex = 0
        activeArticles.clear()
        handler.removeCallbacksAndMessages(null)
        updateUIForMode(TaskMode.READING)
        progress.text = "Initializing Read to Earn..."

        web.settings.userAgentString = BING_SAPPHIRE_UA
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true

        val total = readLimits.getOrElse(selectedReadLimitIndex) { 10 }
        bar.max = total
        bar.setProgressCompat(0, false)
        progress.text = "Discovering articles..."
        web.loadUrl("https://www.bing.com/news")

        handler.postDelayed({
            if (currentMode != TaskMode.READING || isFinishing || isDestroyed) return@postDelayed
            web.evaluateJavascript(
                """(function(){var l=Array.from(document.querySelectorAll('a[href]'))
                .map(a=>a.href).filter(h=>{if(!h||!h.startsWith('http'))return false;
                return h.includes('msn.com')&&(h.includes('/ar-')||h.includes('/news/'))||
                h.includes('bing.com/news/')&&!h.includes('/search?')||
                h.match(/\/(article|story|news)\//i)});
                return JSON.stringify(Array.from(new Set(l)))})();"""
            ) { raw ->
                if (currentMode != TaskMode.READING || isFinishing || isDestroyed) return@evaluateJavascript
                val parsed = parseJsonUrlArray(raw)
                val combined = (parsed + fallbackMsnArticles.shuffled()).distinct()
                activeArticles.clear()
                activeArticles.addAll(combined.take(total))
                if (activeArticles.isEmpty()) { progress.text = "No articles"; stop(); return@evaluateJavascript }
                currentArticleIndex = 0
                readNextArticle()
            }
        }, 3500)
    }

    private fun parseJsonUrlArray(raw: String?): List<String> {
        val list = mutableListOf<String>()
        try {
            val c = raw?.trim() ?: "[]"
            val u = if (c.startsWith("\"") && c.endsWith("\"") && c.length >= 2)
                try { JSONTokener(c).nextValue() as? String ?: c } catch (_: Exception) { c }
            else c
            val a = if (u == "null" || u.isBlank()) JSONArray() else JSONArray(u)
            for (i in 0 until a.length()) {
                val s = a.optString(i)
                if (s.isNotEmpty() && s.startsWith("http")) list.add(s)
            }
        } catch (_: Exception) {}
        return list
    }

    private fun readNextArticle() {
        val total = readLimits.getOrElse(selectedReadLimitIndex) { 10 }
        val sec = readIntervals.getOrElse(selectedReadIntervalIndex) { 10 }
        if (currentMode != TaskMode.READING || currentArticleIndex >= total ||
            currentArticleIndex >= activeArticles.size || isFinishing || isDestroyed
        ) {
            progress.text = "Read to earn done ($currentArticleIndex articles)!"
            if (isAutoAllActive) { currentMode = TaskMode.IDLE; startRewardsActivities(true) }
            else stop()
            return
        }
        val url = activeArticles[currentArticleIndex]
        val idx = currentArticleIndex + 1
        progress.text = "Reading $idx/$total (${sec}s)"
        bar.setProgressCompat(idx, true)
        web.loadUrl(url)

        handler.postDelayed({
            if (currentMode != TaskMode.READING || isFinishing || isDestroyed) return@postDelayed
            try {
                web.evaluateJavascript(
                    """(function(){
                        window.focus();
                        window.scrollBy({top:450,behavior:'smooth'});
                        document.dispatchEvent(new Event('scroll'));
                        try {
                            fetch('https://prod.rewardsplatform.microsoft.com/dapi/me/activities', {credentials:'include'}).catch(function(){});
                            fetch('https://www.bing.com/rewardsapp/reportActivity', {credentials:'include'}).catch(function(){});
                        } catch(e){}
                    })()""",
                    null
                )
            } catch (_: Exception) {}
        }, sec * 500L)

        handler.postDelayed({
            if (currentMode == TaskMode.READING && !isFinishing && !isDestroyed) {
                currentArticleIndex++; readNextArticle()
            }
        }, sec * 1000L)
    }

    // ==========================================
    // 4. DAILY SET & MORE ACTIVITIES
    // ==========================================
    private fun startRewardsActivities(onlyDaily: Boolean) {
        if (currentMode != TaskMode.IDLE) stop()
        currentMode = if (onlyDaily) TaskMode.DAILY_SET else TaskMode.MORE_ACTIVITIES
        pendingActivities.clear()
        handler.removeCallbacksAndMessages(null)
        updateUIForMode(currentMode)
        val title = if (onlyDaily) "Daily Set" else "More Activities"
        progress.text = "Loading $title..."
        bar.setProgressCompat(0, false)
        web.loadUrl("https://rewards.bing.com")

        handler.postDelayed({
            if (currentMode != TaskMode.DAILY_SET && currentMode != TaskMode.MORE_ACTIVITIES ||
                isFinishing || isDestroyed
            ) return@postDelayed

            web.evaluateJavascript(
                """(function(){var r=[];try{var ss=document.querySelectorAll('script');
                for(var i=0;i<ss.length;i++){var t=ss[i].textContent||'';
                if(t.indexOf('dailySetPromotions')>-1||t.indexOf('morePromotions')>-1){
                var m=t.match(/var\s+dashboard\s*=\s*(\{[\s\S]*?\});/);if(m){var d=JSON.parse(m[1]);
                if($onlyDaily&&d.dailySetPromotions){Object.values(d.dailySetPromotions).forEach(function(day){
                if(Array.isArray(day)){day.forEach(function(item){
                if(item&&!item.complete&&item.destinationUrl)
                r.push({title:item.title||'Daily',url:item.destinationUrl,isDaily:true})})}})}
                if(!$onlyDaily&&d.morePromotions&&Array.isArray(d.morePromotions)){
                d.morePromotions.forEach(function(item){
                if(item&&!item.complete&&item.destinationUrl)
                r.push({title:item.title||'Activity',url:item.destinationUrl,isDaily:false})})}}}}}catch(e){}
                if(r.length===0){var cards=document.querySelectorAll('mee-card,.rewards-card,.c-card');
                cards.forEach(function(c){var done=c.querySelector('.mee-icon-CheckMark,[data-icon-name="CheckMark"],.complete');
                var link=c.querySelector('a[href^="http"]');if(!done&&link){
                var t2=(c.querySelector('h3,.c-heading')||{}).textContent||'Activity';var h=link.href;
                var isD=!!c.closest('#daily-set,[id*="daily"]')||h.indexOf('dailyset')>-1;
                if(($onlyDaily&&isD)||(!$onlyDaily&&!isD))r.push({title:t2.trim(),url:h,isDaily:isD})}})}
                return JSON.stringify(r)})();"""
            ) { raw ->
                if (currentMode != TaskMode.DAILY_SET && currentMode != TaskMode.MORE_ACTIVITIES ||
                    isFinishing || isDestroyed
                ) return@evaluateJavascript
                val list = mutableListOf<RewardActivity>()
                try {
                    val c = raw?.trim() ?: "[]"
                    val u = if (c.startsWith("\"") && c.endsWith("\"") && c.length >= 2)
                        try { JSONTokener(c).nextValue() as? String ?: c } catch (_: Exception) { c }
                    else c
                    val a = if (u == "null" || u.isBlank()) JSONArray() else JSONArray(u)
                    for (i in 0 until a.length()) {
                        val o = a.optJSONObject(i)
                        if (o != null) {
                            val url = o.optString("url")
                            if (url.isNotEmpty() && url.startsWith("http"))
                                list.add(
                                    RewardActivity(
                                        o.optString("title", "Activity"),
                                        url, o.optBoolean("isDaily", onlyDaily)
                                    )
                                )
                        }
                    }
                } catch (_: Exception) {}

                pendingActivities = list.distinctBy { it.url }.toMutableList()
                totalActivitiesCount = pendingActivities.size
                bar.max = if (totalActivitiesCount > 0) totalActivitiesCount else 1

                if (pendingActivities.isEmpty()) {
                    progress.text =
                        if (onlyDaily) "Daily Set complete! 🎉" else "Activities complete! 🎉"
                    if (isAutoAllActive && onlyDaily) {
                        currentMode = TaskMode.IDLE; startRewardsActivities(false)
                    } else stop()
                    return@evaluateJavascript
                }
                executeNextRewardActivity()
            }
        }, 3500)
    }

    private fun executeNextRewardActivity() {
        if (currentMode != TaskMode.DAILY_SET && currentMode != TaskMode.MORE_ACTIVITIES ||
            isFinishing || isDestroyed
        ) return
        if (pendingActivities.isEmpty()) {
            val t = if (currentMode == TaskMode.DAILY_SET) "Daily Set" else "Activities"
            progress.text = "$t completed! 🎉"
            if (isAutoAllActive && currentMode == TaskMode.DAILY_SET) {
                currentMode = TaskMode.IDLE; startRewardsActivities(false)
            } else stop()
            return
        }
        val act = pendingActivities.removeAt(0)
        val idx = totalActivitiesCount - pendingActivities.size
        bar.setProgressCompat(idx, true)
        progress.text = "Doing ($idx/$totalActivitiesCount): ${act.title}"
        web.loadUrl(act.url)

        handler.postDelayed({
            if (currentMode != TaskMode.DAILY_SET && currentMode != TaskMode.MORE_ACTIVITIES ||
                isFinishing || isDestroyed
            ) return@postDelayed
            try {
                web.evaluateJavascript(
                    """(function(){function s(){
                    var p=document.querySelector('#btoption0,.btOption,input[name="btoption"]');if(p)p.click();
                    var q=document.querySelector('#rqStartQuiz,.rqStartBtn,input[value="Start playing"]');if(q)q.click();
                    var a=document.querySelector('.rqOption:not([disabled]),[id^="rqAnswerOption"],.wk_ansOption');if(a)a.click();
                    window.scrollBy({top:350,behavior:'smooth'})}
                    s();setTimeout(s,1500);setTimeout(s,3000)})();""", null
                )
            } catch (_: Exception) {}
        }, 2500)

        handler.postDelayed({
            if (currentMode == TaskMode.DAILY_SET || currentMode == TaskMode.MORE_ACTIVITIES)
                executeNextRewardActivity()
        }, 6500)
    }

    // ==========================================
    // STOP
    // ==========================================
    private fun stop() {
        val wasReading = currentMode == TaskMode.READING
        currentMode = TaskMode.IDLE
        isAutoAllActive = false
        handler.removeCallbacksAndMessages(null)
        searchStartPending = false
        if (wasReading) applyDesktop(desktopSwitch?.isChecked ?: true)
        updateUIForMode(TaskMode.IDLE)
        refreshPoints()
    }

    private fun setSignedIn(on: Boolean) {
        signedIn = on
        val bg = ContextCompat.getColor(
            this, if (on) R.color.stop_btn else R.color.secondary
        )
        val fg = ColorStateList.valueOf(
            ContextCompat.getColor(
                this, if (on) android.R.color.white else R.color.on_secondary
            )
        )
        signin.text = if (on) "Log out" else "Sign in"
        signin.setIconResource(if (on) R.drawable.ic_logout else R.drawable.ic_login)
        signin.backgroundTintList = ColorStateList.valueOf(bg)
        signin.setTextColor(fg)
        signin.iconTint = fg
    }

    private fun logout() {
        currentMode = TaskMode.IDLE; isAutoAllActive = false
        handler.removeCallbacksAndMessages(null); done = 0
        updateUIForMode(TaskMode.IDLE)
        bar.setProgressCompat(0, false); progress.text = "Ready"
        try {
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
            }
            WebStorage.getInstance().deleteAllData()
            web.clearCache(true); web.loadUrl("about:blank")
        } catch (_: Exception) {}
        total.text = "-"; lifetime.text = "-"; claim.text = "-"
        accountEmail.text = "Not signed in"; accountStatus.text = "Logged out"
        setSignedIn(false)
    }

    // ==========================================
    // FULLSCREEN
    // ==========================================
    private fun enterFullscreen() {
        if (isFullscreen) return; isFullscreen = true
        (web.parent as? ViewGroup)?.removeView(web)
        fullContainer.addView(
            web, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        fullTitle.text = web.url ?: "bing.com"
        fullOverlay.visibility = View.VISIBLE; backCb.isEnabled = true
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun exitFullscreen() {
        if (!isFullscreen) return; isFullscreen = false
        (web.parent as? ViewGroup)?.removeView(web)
        webCard.addView(
            web, 0, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        fullOverlay.visibility = View.GONE
        backCb.isEnabled = backCheckbox.isChecked
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Reload Page")
            menu.add(0, 2, 1, "Exit Fullscreen")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> web.reload()
                    2 -> exitFullscreen()
                }; true
            }
        }.show()
    }

    // ==========================================
    // POINTS REFRESH
    // ==========================================
    private class PointsBridge(private val cb: (String) -> Unit) {
        @JavascriptInterface
        fun result(data: String) = cb(data)
    }

    private fun cleanupPointsWebView() {
        refreshTimeoutRunnable?.let { handler.removeCallbacks(it) }
        refreshTimeoutRunnable = null
        pointsWebView?.let { pw ->
            try {
                pw.stopLoading(); pw.webViewClient = WebViewClient()
                pw.removeJavascriptInterface("Native"); pw.destroy()
            } catch (_: Exception) {}
        }
        pointsWebView = null
    }

    private fun refreshPoints() {
        cleanupPointsWebView()
        total.text = "..."; lifetime.text = "..."; claim.text = "..."
        accountStatus.text = "Refreshing..."; accountEmail.text = "..."
        CookieManager.getInstance().flush()

        var finished = false
        val w = WebView(this).also { pointsWebView = it }
        w.settings.javaScriptEnabled = true; w.settings.domStorageEnabled = true
        w.settings.userAgentString = BING_SAPPHIRE_UA
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)

        w.addJavascriptInterface(PointsBridge { body ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    showPoints(body); cleanupPointsWebView()
                }
            }
        }, "Native")

        refreshTimeoutRunnable = Runnable {
            if (!finished && !isFinishing && !isDestroyed) {
                finished = true; accountStatus.text = "Refresh timed out"
                cleanupPointsWebView()
            }
        }
        handler.postDelayed(refreshTimeoutRunnable!!, 15000L)

        w.webViewClient = object : WebViewClient() {
            var stepCount = 0
            override fun onPageFinished(v: WebView, url: String) {
                if (finished || isFinishing || isDestroyed) return
                if (url.contains("login.live.com") || url.contains("login.microsoftonline.com") ||
                    url.contains("rewards.bing.com/welcome")
                ) {
                    finished = true
                    runOnUiThread {
                        accountStatus.text = "Not signed in"
                        accountEmail.text = "Not signed in"
                        setSignedIn(false)
                        total.text = "-"; lifetime.text = "-"; claim.text = "-"
                        cleanupPointsWebView()
                    }; return
                }
                if (url.contains("api.msn.com/v1/news/users/me/locations")) {
                    stepCount++
                    if (stepCount == 1) {
                        try {
                            v.evaluateJavascript(
                                """(function(){
                                try {
                                    fetch('https://prod.rewardsplatform.microsoft.com/dapi/me?channel=SAAndroid&options=105', {credentials:'include'}).catch(function(){});
                                    fetch('https://prod.rewardsplatform.microsoft.com/dapi/me?channel=SAAndroid&options=1', {credentials:'include'}).catch(function(){});
                                    fetch('https://prod.rewardsplatform.microsoft.com/dapi/me?channel=SAAndroid&options=2', {credentials:'include'}).catch(function(){});
                                    fetch('https://prod.rewardsplatform.microsoft.com/dapi/me?channel=SAAndroid&options=612', {credentials:'include'}).catch(function(){});
                                    fetch('https://dove.sapphire.microsoftapp.net/api/v1/query/list?market=en-ph', {credentials:'include'}).catch(function(){});
                                } catch(e){}
                                })()""", null
                            )
                        } catch (_: Exception) {}
                        v.loadUrl("https://rewards.bing.com/dashboard"); return
                    }
                }
                if (url.contains("rewards.bing.com")) {
                    finished = true
                    v.evaluateJavascript(
                        """(function(){fetch('/dashboard',{credentials:'include'}).then(function(r){return r.text()}).then(function(t){
                        var g=function(re){var m=t.match(re);return m?m[1]:''};
                        var e=g(/(?:email|signInName|userEmail|upn)\\?":\\?"([^"\\]+@[^"\\]+)/)||
                        ((t.match(/[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+\.[A-Za-z0-9.-]+/)||[''])[0]);
                        if(window.Native)window.Native.result(JSON.stringify({
                        e:e,b:g(/balance\\?":(\d+)/),l:g(/lifetimeEarn\\?":(\d+)/),
                        c:g(/pointClaim\\?":\{\\?"points\\?":(\d+)/)}))}).catch(function(err){
                        if(window.Native)window.Native.result(JSON.stringify({err:String(err)}))})})();""",
                        null
                    )
                }
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true && !finished) {
                    finished = true
                    runOnUiThread {
                        accountStatus.text = "Connection error"; cleanupPointsWebView()
                    }
                }
            }
        }
        w.loadUrl(MSN_LOCATIONS_API)
    }

    private fun showPoints(body: String) {
        if (isFinishing || isDestroyed) return
        try {
            val o = JSONObject(body)
            val b = o.optString("b")
            if (b.isEmpty()) {
                accountStatus.text = "Status: ${o.optString("err", "Not found")}"
                accountEmail.text = "-"; setSignedIn(false)
                total.text = "-"; lifetime.text = "-"; claim.text = "-"; return
            }
            total.text = b
            lifetime.text = o.optString("l", "-").ifEmpty { "-" }
            claim.text = o.optString("c", "0").ifEmpty { "0" }
            val e = o.optString("e")
            accountEmail.text = if (e.isNotEmpty()) e else "Signed in"
            setSignedIn(true); accountStatus.text = "Connected"
        } catch (_: Exception) {
            accountStatus.text = "Parse error"
            total.text = "-"; lifetime.text = "-"; claim.text = "-"
        }
    }

    override fun onDestroy() {
        currentMode = TaskMode.IDLE; isAutoAllActive = false
        handler.removeCallbacksAndMessages(null); cleanupPointsWebView()
        try {
            if (desktopScript != null &&
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
            ) desktopScript?.remove()
        } catch (_: Exception) {}
        desktopScript = null
        try {
            (web.parent as? ViewGroup)?.removeView(web)
            web.stopLoading(); web.webViewClient = WebViewClient()
            web.webChromeClient = null; web.destroy()
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
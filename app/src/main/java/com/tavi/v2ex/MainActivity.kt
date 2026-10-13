package com.tavi.v2ex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.tavi.v2ex.ui.*

class MainActivity : ComponentActivity() {
    private var nav: Navigator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()
        val app = application as App
        val navigator = Navigator(app.api, app.prefs, app.dao, applicationContext).also { nav = it }
        handleIntent(intent)
        setContent {
            AppTheme(app.prefs.themeMode, app.prefs.dynamicColor) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // 预测性返回：边缘滑动可预览上一页；对话框由系统优先处理
                    PredictiveNavHost(navigator) { e ->
                        when (val r = e.route) {
                            Route.Home -> HomeScreen(navigator, e)
                            is Route.TopicR -> TopicScreen(navigator, e, r.id, r.preview, r.jumpFloor)
                            is Route.NodeR -> NodeScreen(navigator, e, r.name, r.title)
                            is Route.MemberR -> MemberScreen(navigator, e, r.name)
                            Route.Notices -> NoticesScreen(navigator, e)
                            Route.Settings -> SettingsScreen(navigator, e)
                            Route.Search -> SearchScreen(navigator, e)
                            Route.Library -> LibraryScreen(navigator, e)
                            is Route.Image -> ImageScreen(navigator, r.url)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val url = i?.dataString ?: return
        Navigator.routeFor(url)?.let { nav?.push(it) }
    }
}

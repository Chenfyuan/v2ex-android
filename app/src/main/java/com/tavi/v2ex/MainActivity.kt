package com.tavi.v2ex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.tavi.v2ex.ui.*

class MainActivity : ComponentActivity() {
    private var nav: Navigator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        val navigator = Navigator(app.api, app.prefs, app.dao, applicationContext).also { nav = it }
        handleIntent(intent)
        setContent {
            AppTheme(app.prefs.themeMode, app.prefs.dynamicColor) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    BackHandler(navigator.stack.size > 1) { navigator.pop() }
                    AnimatedContent(
                        targetState = navigator.current,
                        transitionSpec = {
                            val dir = if (navigator.forward) 1 else -1
                            (slideInHorizontally(tween(260)) { it / 4 * dir } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(260)) { -it / 6 * dir } + fadeOut(tween(160)))
                        },
                        label = "nav",
                    ) { e ->
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

package com.tavi.v2ex

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.tavi.v2ex.data.*
import com.tavi.v2ex.ui.*
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val OUT = System.getProperty("shots.dir") ?: "build/screenshots"
private fun res(n: String) = ShotsTest::class.java.classLoader!!.getResource(n).readText()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class ShotsTest {
    @get:Rule val rule = createComposeRule()
    private val topics = JSONArray(res("hot.json")).objects().map(::parseTopic)
    private val tid = 1248103L

    @OptIn(ExperimentalMaterial3Api::class)
    private fun list(dark: Boolean, name: String) {
        rule.setContent {
            AppTheme(if (dark) 2 else 1, dynamic = false) {
                Box(Modifier.fillMaxSize()) {
                    val haze = remember { dev.chrisbanes.haze.HazeState() }
                    Scaffold(topBar = { TopAppBar(title = { Text("最热") }) }) { p ->
                        Box(Modifier.padding(p).fillMaxSize().hazeSource(haze)) {
                            TopicList(topics, LazyListState(), {}, {})
                        }
                    }
                    FloatingBottomBar(0, {}, Modifier.align(androidx.compose.ui.Alignment.BottomCenter), haze)
                }
            }
        }
        rule.onRoot().captureRoboImage("$OUT/$name.png")
    }

    @Test fun homeLight() = list(false, "1-home-light")
    @Test fun homeDark() = list(true, "2-home-dark")

    @Test fun topic() {
        val t = topics.first { it.id == tid }
        val replies = JSONArray(res("replies.json")).objects().mapIndexed { i, o ->
            Reply(o.optLong("id"), o.optString("content_rendered"), parseMember(o.optJSONObject("member")), o.optLong("created"), i + 1)
        }
        rule.setContent {
            AppTheme(1, dynamic = false) {
                Surface { TopicDetail(t, replies, false, null, 50, LazyListState(), false, {}, {}, {}, {}, {}) }
            }
        }
        rule.onRoot().captureRoboImage("$OUT/3-topic.png")
    }

    @Test fun routes() {
        assertEquals(Route.TopicR(123), Navigator.routeFor("https://www.v2ex.com/t/123#reply4"))
        assertEquals(Route.MemberR("Livid"), Navigator.routeFor("https://v2ex.com/member/Livid"))
        assertEquals(Route.NodeR("python"), Navigator.routeFor("https://www.v2ex.com/go/python"))
        assertEquals(null, Navigator.routeFor("https://github.com/x"))
    }

    @Test fun html() {
        val b = parseHtml("<p>hi <a href=\"/t/1\">t</a></p><pre><code>x=1</code></pre><img src=\"//i.imgur.com/a.png\">",
            androidx.compose.ui.graphics.Color.Blue, androidx.compose.ui.graphics.Color.Gray) {}
        assertEquals(3, b.size)
        assertTrue(b[2] is Block.Img && (b[2] as Block.Img).url == "https://i.imgur.com/a.png")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h860dp-xxhdpi")
class SettingsTest {
    @get:Rule val rule = createComposeRule()
    @Test fun settingsOpens() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(ctx)
        val nav = Navigator(V2exApi(ctx.cacheDir) { null }, prefs, ctx)
        rule.setContent { AppTheme(1, false) { SettingsScreen(nav, Entry(Route.Settings)) } }
        rule.onRoot().captureRoboImage("$OUT/4-settings.png")
    }
}

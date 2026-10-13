package com.tavi.v2ex

import com.tavi.v2ex.data.Node
import com.tavi.v2ex.data.WebParser
import org.junit.Assert.*
import org.junit.Test

class WebParserTest {
    private fun res(n: String) = javaClass.classLoader!!.getResource(n).readText()

    @Test fun recent() {
        val l = WebParser.parseTopicList(res("recent.html"))
        assertTrue(l.size >= 20)
        assertEquals(l.size, l.map { it.id }.toSet().size)
        val t = l.first()
        assertEquals(1248095L, t.id)
        assertEquals("xiaoz", t.member?.username)
        assertEquals("create", t.node?.name)
        assertEquals(33, t.replies)
        assertTrue(t.member!!.avatar.startsWith("https://"))
    }

    @Test fun nodePage() {
        val l = WebParser.parseTopicList(res("go_python.html"), Node("python", "Python"))
        assertTrue(l.size >= 15)
        assertEquals("python", l.first().node?.name)
        assertEquals("mitwj", l.first().member?.username)
    }

    @Test fun garbage() {
        assertTrue(WebParser.parseTopicList("<html><body>维护中</body></html>").isEmpty())
    }
}

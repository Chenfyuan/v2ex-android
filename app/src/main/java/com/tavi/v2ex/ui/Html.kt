package com.tavi.v2ex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tavi.v2ex.data.fixUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

sealed interface Block {
    data class Text(val text: AnnotatedString, val quote: Boolean) : Block
    data class Img(val url: String) : Block
    data class Code(val text: String) : Block
}

/** 把 V2EX 的 content_rendered（HTML）转成原生 Compose 段落 */
fun parseHtml(html: String, linkColor: Color, codeBg: Color, onLink: (String) -> Unit): List<Block> {
    val w = Walker(linkColor, codeBg, onLink)
    w.walkChildren(Jsoup.parseBodyFragment(html, "https://www.v2ex.com/").body())
    w.flush()
    return w.blocks
}

private class Walker(val linkColor: Color, val codeBg: Color, val onLink: (String) -> Unit) {
    val blocks = mutableListOf<Block>()
    var sb = AnnotatedString.Builder()
    var quote = false

    fun flush() {
        val t = sb.toAnnotatedString()
        val s = t.text.indexOfFirst { !it.isWhitespace() }
        if (s >= 0) {
            val e = t.text.indexOfLast { !it.isWhitespace() } + 1
            blocks += Block.Text(t.subSequence(s, e), quote)
        }
        sb = AnnotatedString.Builder()
    }

    private fun lastChar(): Char? = sb.length.takeIf { it > 0 }?.let { sb.toAnnotatedString().text[it - 1] }

    fun walkChildren(el: Element) = el.childNodes().forEach { n ->
        when (n) {
            is TextNode -> {
                var t = n.wholeText.replace(Regex("\\s+"), " ")
                val lc = lastChar()
                if (t.startsWith(" ") && (lc == null || lc == ' ' || lc == '\n')) t = t.trimStart()
                if (t.isNotEmpty()) sb.append(t)
            }
            is Element -> walk(n)
        }
    }

    fun walk(el: Element) {
        when (val tag = el.tagName().lowercase()) {
            "br" -> sb.append("\n")
            "img" -> {
                val src = el.absUrl("src").ifBlank { fixUrl(el.attr("data-src")) }
                if (src.isNotBlank()) { flush(); blocks += Block.Img(src) }
            }
            "pre" -> { flush(); blocks += Block.Code(el.wholeText().trimEnd()) }
            "blockquote" -> { flush(); val old = quote; quote = true; walkChildren(el); flush(); quote = old }
            "li" -> { flush(); sb.append("•  "); walkChildren(el); flush() }
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                flush(); sb.pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); walkChildren(el); sb.pop(); flush()
            }
            "p", "div", "ul", "ol", "table", "tr", "hr", "section" -> { flush(); walkChildren(el); flush() }
            "a" -> {
                val href = el.absUrl("href")
                if (href.isBlank() || el.select("img").isNotEmpty() && el.text().isBlank()) { walkChildren(el); return }
                sb.pushLink(LinkAnnotation.Clickable(href, TextLinkStyles(SpanStyle(color = linkColor))) { onLink(href) })
                walkChildren(el); sb.pop()
            }
            "strong", "b" -> { sb.pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold)); walkChildren(el); sb.pop() }
            "em", "i" -> { sb.pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); walkChildren(el); sb.pop() }
            "del", "s" -> { sb.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)); walkChildren(el); sb.pop() }
            "code" -> { sb.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)); walkChildren(el); sb.pop() }
            "td", "th" -> { walkChildren(el); sb.append("  ") }
            "script", "style" -> Unit
            else -> { if (tag.isNotEmpty()) walkChildren(el) }
        }
    }
}

@Composable
fun HtmlContent(
    html: String,
    onLink: (String) -> Unit,
    onImage: (String) -> Unit,
    loadImages: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val cs = MaterialTheme.colorScheme
    val link by rememberUpdatedState(onLink)
    val blocks = remember(html, cs.primary) { parseHtml(html, cs.primary, cs.surfaceContainerHighest) { link(it) } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Block.Text -> if (b.quote) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(cs.outlineVariant, RoundedCornerShape(2.dp)))
                        SelectionContainer { Text(b.text, Modifier.padding(start = 10.dp), style = style, color = cs.onSurfaceVariant) }
                    }
                } else SelectionContainer { Text(b.text, style = style, color = cs.onSurface) }
                is Block.Code -> Surface(color = cs.surfaceContainerHigh, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(b.text, Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
                is Block.Img -> if (loadImages) {
                    AsyncImage(
                        model = b.url, contentDescription = "图片",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(8.dp))
                            .clickable { onImage(b.url) },
                    )
                } else AssistChip(onClick = { onImage(b.url) }, label = { Text("查看图片") },
                    leadingIcon = { Icon(Icons.Outlined.Image, null, Modifier.size(18.dp)) })
            }
        }
    }
}

package com.tavi.v2ex.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

/**
 * 自定义栈的预测性返回：
 * - 手势进行中：底层露出上一页（略放大），当前页随进度横向滑出并略缩小
 * - 手势确认：无动画 pop，避免和预览动画叠两次
 * - 手势取消：当前页弹回
 * - 非手势的前进 / 系统返回键仍走 AnimatedContent
 *
 * 对话框（AlertDialog）在独立窗口，系统会优先交给对话框关闭。
 * 看图、搜索作为普通栈页，滑动返回会预览并回到上一页。
 */
@Composable
fun PredictiveNavHost(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    content: @Composable (Entry) -> Unit,
) {
    val stack = navigator.stack
    val current = stack.last()
    val previous = stack.getOrNull(stack.lastIndex - 1)
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var predictive by remember { mutableStateOf(false) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val progress = remember { Animatable(0f) }
    var skipNextTransition by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = previous != null) { events ->
        predictive = true
        swipeEdge = BackEventCompat.EDGE_LEFT
        try {
            events.collect { ev ->
                swipeEdge = ev.swipeEdge
                progress.snapTo(ev.progress.coerceIn(0f, 1f))
            }
            // 手势完成 → 直接 pop，跳过 AnimatedContent，预览本身已是终点
            skipNextTransition = true
            progress.snapTo(0f)
            predictive = false
            navigator.pop()
        } catch (e: CancellationException) {
            // 取消：弹回
            progress.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
            predictive = false
            throw e
        }
    }

    Box(modifier.fillMaxSize()) {
        // 预测性返回时画出上一页作「偷看」
        if (predictive && previous != null) {
            val p = progress.value
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val startScale = 0.92f
                        scaleX = lerp(startScale, 1f, p)
                        scaleY = lerp(startScale, 1f, p)
                        alpha = lerp(0.8f, 1f, p)
                    },
            ) { content(previous) }
        }

        // 常规导航动画；预测手势进行中关掉进出动画，改用手动位移
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                if (skipNextTransition || predictive) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val dir = if (navigator.forward) 1 else -1
                    (slideInHorizontally(tween(260)) { it / 4 * dir } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it / 6 * dir } + fadeOut(tween(160)))
                }
            },
            label = "nav",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!predictive) return@graphicsLayer
                    val p = progress.value
                    val widthPx = size.width
                    // 从左缘滑：页面向右移出；从右缘滑：向左移出（RTL 对调）
                    val fromLeft = swipeEdge == BackEventCompat.EDGE_LEFT
                    val sign = when {
                        rtl && fromLeft -> -1f
                        rtl && !fromLeft -> 1f
                        fromLeft -> 1f
                        else -> -1f
                    }
                    translationX = sign * widthPx * 0.25f * p + sign * widthPx * 0.75f * p * p
                    scaleX = lerp(1f, 0.96f, p)
                    scaleY = lerp(1f, 0.96f, p)
                    alpha = lerp(1f, 0.7f, p)
                    shadowElevation = with(density) { (8.dp.toPx()) * (1f - p) }
                },
        ) { e ->
            LaunchedEffect(e) { skipNextTransition = false }
            key(e) { content(e) }
        }
    }
}

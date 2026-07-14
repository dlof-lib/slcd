package org.dlof.slcd

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * ── إعادة ترتيب بالسحب لقوائم SLCD (أغلفة/مواسم/فصول) ─────────────────
 *
 * تطبيق مصغّر لا يحتاج مكتبة خارجية: ضغط مطوّل على عنصر ثم سحبه فوق
 * العناصر المجاورة يبدّل موضعيهما مباشرة (المصدر الوحيد للحقيقة هو ترتيب
 * القائمة المعروضة نفسها)، و[onMove] يُطبَّق فوراً على القائمة في الذاكرة
 * بينما `animateItemPlacement()` في LazyColumn/LazyRow يتكفّل بالانتقال
 * البصري السلس بين المواضع. عند انتهاء السحب [onDragFinished] هو من
 * يحفظ الترتيب الجديد فعلياً (بإعادة تسمية مجلدات coverN/seasonN/chapterM).
 */
enum class SlcdDragAxis { Vertical, Horizontal }

class SlcdDragState {
    var draggedIndex by mutableStateOf<Int?>(null)
        private set
    var dragOffset by mutableStateOf(0f)
        private set

    fun start(index: Int) {
        draggedIndex = index
        dragOffset = 0f
    }

    fun drag(delta: Float, itemCount: Int, itemSizePx: Float, onMove: (from: Int, to: Int) -> Unit) {
        val from = draggedIndex ?: return
        dragOffset += delta
        if (itemSizePx <= 0f) return
        val shift = (dragOffset / itemSizePx).roundToInt()
        if (shift != 0) {
            val to = (from + shift).coerceIn(0, itemCount - 1)
            if (to != from) {
                onMove(from, to)
                draggedIndex = to
            }
            dragOffset -= shift * itemSizePx
        }
    }

    fun end() {
        draggedIndex = null
        dragOffset = 0f
    }
}

@Composable
fun rememberSlcdDragState(): SlcdDragState = remember { SlcdDragState() }

/** يُضاف لكل عنصر داخل قائمة قابلة لإعادة الترتيب بالسحب (ضغط مطوّل ثم سحب). */
fun Modifier.slcdDragReorder(
    state: SlcdDragState,
    axis: SlcdDragAxis,
    index: Int,
    itemCount: Int,
    itemSizePx: Float,
    onMove: (from: Int, to: Int) -> Unit,
    onDragFinished: () -> Unit
): Modifier = this
    .zIndex(if (state.draggedIndex == index) 1f else 0f)
    .graphicsLayer {
        if (state.draggedIndex == index) {
            if (axis == SlcdDragAxis.Vertical) translationY = state.dragOffset else translationX = state.dragOffset
        }
    }
    .pointerInput(index, itemCount, itemSizePx) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.start(index) },
            onDragEnd = { onDragFinished(); state.end() },
            onDragCancel = { state.end() },
            onDrag = { change, dragAmount ->
                change.consume()
                val delta = if (axis == SlcdDragAxis.Vertical) dragAmount.y else dragAmount.x
                state.drag(delta, itemCount, itemSizePx, onMove)
            }
        )
    }

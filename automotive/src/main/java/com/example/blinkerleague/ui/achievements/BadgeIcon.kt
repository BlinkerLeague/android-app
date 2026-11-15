package com.example.blinkerleague.ui.achievements

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.blinkerleague.ui.theme.Accent
import com.example.blinkerleague.ui.theme.Flame
import com.example.blinkerleague.ui.theme.Gold
import com.example.blinkerleague.ui.theme.OnWarningContainer
import com.example.blinkerleague.ui.theme.TextPrimary

/**
 * 배지 코드별 아이콘 (이모지 대신 Canvas). 모르는 코드는 기본 트로피.
 * [locked]면 한 가지 회색 실루엣으로 그림 (잠긴 배지)
 */
@Composable
fun BadgeIcon(code: String, locked: Boolean, modifier: Modifier = Modifier) {
    val shape = BadgeShape.of(code)
    Canvas(modifier.aspectRatio(1f)) {
        val tint = if (locked) LockedTint else null
        when (shape) {
            BadgeShape.FLAG -> drawFlag(tint)
            BadgeShape.FLAME -> drawFlame(tint)
            BadgeShape.SIGNAL -> drawSignal(tint)
            BadgeShape.CHAMPION -> drawTrophy(tint ?: Gold, tint ?: GoldDark)
            BadgeShape.TROPHY -> drawTrophy(tint ?: Accent, tint ?: AccentDark)
        }
    }
}

/** 서버 배지 코드 → 아이콘 모양 (서버 plan 4.3 배지 카탈로그) */
internal enum class BadgeShape {
    FLAG, FLAME, SIGNAL, CHAMPION, TROPHY;

    companion object {
        fun of(code: String): BadgeShape = when (code) {
            "FIRST_TRIP" -> FLAG
            "STREAK_7" -> FLAME
            "SIGNAL_100" -> SIGNAL
            "SEASON_CHAMPION" -> CHAMPION
            else -> TROPHY
        }
    }
}

private val LockedTint = Color(0xFF59616C)
private val GoldDark = Color(0xFFB88A1C)
private val AccentDark = Color(0xFF2F6DB8)

/** 첫 경기: 체크무늬 깃발 */
private fun DrawScope.drawFlag(tint: Color?) {
    val w = size.width
    val h = size.height
    val pole = tint ?: TextPrimary
    drawRoundRect(pole, Offset(w * 0.18f, h * 0.1f), Size(w * 0.07f, h * 0.82f), CornerRadius(w * 0.03f))
    val left = w * 0.25f
    val top = h * 0.12f
    val cell = w * 0.14f
    repeat(3) { row ->
        repeat(4) { col ->
            val dark = (row + col) % 2 == 1
            val color = tint?.copy(alpha = if (dark) 0.55f else 1f) ?: if (dark) Color(0xFF1B2027) else TextPrimary
            drawRect(color, Offset(left + col * cell, top + row * cell), Size(cell, cell))
        }
    }
    if (tint == null) {
        drawRect(TextPrimary, Offset(left, top), Size(cell * 4, cell * 3), style = Stroke(w * 0.02f))
    }
}

/** 연승: 불꽃 (StreakFlame과 같은 모양, 멈춘 상태) */
private fun DrawScope.drawFlame(tint: Color?) {
    val w = size.width
    val h = size.height
    drawPath(flame(w * 0.7f, h * 0.84f, w * 0.15f, h * 0.08f), tint ?: Flame)
    drawPath(flame(w * 0.35f, h * 0.46f, w * 0.325f, h * 0.46f), tint?.copy(alpha = 0.6f) ?: Gold)
}

private fun flame(w: Float, h: Float, x: Float, y: Float) = Path().apply {
    moveTo(x + w / 2, y)
    cubicTo(x + w * 0.95f, y + h * 0.4f, x + w, y + h * 0.75f, x + w / 2, y + h)
    cubicTo(x, y + h * 0.75f, x + w * 0.05f, y + h * 0.4f, x + w / 2, y)
    close()
}

/** 깜빡이: 좌우 방향지시등 화살표 한 쌍 */
private fun DrawScope.drawSignal(tint: Color?) {
    val w = size.width
    val h = size.height
    val color = tint ?: OnWarningContainer
    val left = Path().apply {
        moveTo(w * 0.06f, h * 0.5f)
        lineTo(w * 0.42f, h * 0.22f)
        lineTo(w * 0.42f, h * 0.78f)
        close()
    }
    val right = Path().apply {
        moveTo(w * 0.94f, h * 0.5f)
        lineTo(w * 0.58f, h * 0.22f)
        lineTo(w * 0.58f, h * 0.78f)
        close()
    }
    drawPath(left, color)
    drawPath(right, color)
}

/** 트로피: 컵 · 손잡이 · 받침 */
private fun DrawScope.drawTrophy(body: Color, shade: Color) {
    val w = size.width
    val h = size.height
    val handle = Stroke(w * 0.06f)
    drawArc(shade, 90f, 180f, false, Offset(w * 0.12f, h * 0.16f), Size(w * 0.3f, h * 0.3f), style = handle)
    drawArc(shade, -90f, 180f, false, Offset(w * 0.58f, h * 0.16f), Size(w * 0.3f, h * 0.3f), style = handle)
    val cup = Path().apply {
        moveTo(w * 0.24f, h * 0.1f)
        lineTo(w * 0.76f, h * 0.1f)
        cubicTo(w * 0.76f, h * 0.5f, w * 0.62f, h * 0.6f, w * 0.5f, h * 0.6f)
        cubicTo(w * 0.38f, h * 0.6f, w * 0.24f, h * 0.5f, w * 0.24f, h * 0.1f)
        close()
    }
    drawPath(cup, body)
    drawRect(shade, Offset(w * 0.45f, h * 0.58f), Size(w * 0.1f, h * 0.16f))
    drawRoundRect(body, Offset(w * 0.3f, h * 0.74f), Size(w * 0.4f, h * 0.14f), CornerRadius(w * 0.03f))
}

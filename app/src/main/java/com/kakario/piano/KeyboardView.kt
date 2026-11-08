package com.kakario.piano

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

/** 멀티터치 건반. 손가락을 미끄러뜨리면 옆 건반으로 넘어간다. */
class KeyboardView(context: Context) : View(context) {

    companion object {
        private val BLACK = booleanArrayOf(false, true, false, true, false, false, true, false, true, false, true, false)
        private val DOREMI = arrayOf("도", "도#", "레", "레#", "미", "파", "파#", "솔", "솔#", "라", "라#", "시")
        private val CDE = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        fun isBlack(midi: Int) = BLACK[midi % 12]
    }

    /** 0 = 끄기, 1 = 도레미, 2 = CDE */
    var labelMode = 1
        set(v) { field = v; invalidate() }

    var visibleWhites = 14
        set(v) { field = v.coerceIn(7, whites.size); firstWhite = firstWhite; invalidate() }

    /** 화면 왼쪽 첫 흰 건반의 순번 */
    var firstWhite = 14
        set(v) { field = v.coerceIn(0, (whites.size - visibleWhites).coerceAtLeast(0)); invalidate() }

    var onNoteOn: ((Int) -> Unit)? = null
    var onNoteOff: ((Int) -> Unit)? = null

    private val whites: List<Int> = (SoundEngine.LOW..SoundEngine.HIGH).filter { !isBlack(it) }
    val whiteCount get() = whites.size

    private val held = IntArray(128)           // 건반별 누르고 있는 손가락 수
    private val pointerKey = HashMap<Int, Int>() // 손가락 → 건반

    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F2C66D") }
    private val pressedBlackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C9922E") }
    private val linePaint = Paint().apply { color = Color.parseColor("#55555E"); strokeWidth = 2f }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5A5A66"); textAlign = Paint.Align.CENTER }
    private val octavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B0802A"); textAlign = Paint.Align.CENTER }
    private val blackTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D8D8E0"); textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    fun lowestVisible(): Int = whites[firstWhite]
    fun highestVisible(): Int = whites[(firstWhite + visibleWhites - 1).coerceAtMost(whites.size - 1)]

    fun noteName(midi: Int): String {
        val names = if (labelMode == 2) CDE else DOREMI
        return names[midi % 12] + (midi / 12 - 1)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        whitePaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            Color.parseColor("#F4F4F0"), Color.parseColor("#FFFFFF"), Shader.TileMode.CLAMP)
        blackPaint.shader = LinearGradient(0f, 0f, 0f, h * 0.62f,
            Color.parseColor("#2A2A30"), Color.parseColor("#111114"), Shader.TileMode.CLAMP)
    }

    private fun whiteWidth() = width.toFloat() / visibleWhites
    private fun blackHeight() = height * 0.62f

    /** 흰 건반 순번 i 의 화면 왼쪽 x */
    private fun whiteX(i: Int) = (i - firstWhite) * whiteWidth()

    private fun blackRect(midi: Int, out: RectF): Boolean {
        val leftWhite = whites.indexOf(midi - 1)
        if (leftWhite < 0) return false
        val ww = whiteWidth()
        val bw = ww * 0.6f
        val cx = whiteX(leftWhite + 1)
        out.set(cx - bw / 2, 0f, cx + bw / 2, blackHeight())
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val ww = whiteWidth()
        val h = height.toFloat()
        canvas.drawColor(Color.parseColor("#1E1E24"))
        textPaint.textSize = (ww * 0.32f).coerceAtMost(h * 0.07f)
        octavePaint.textSize = textPaint.textSize * 0.7f
        blackTextPaint.textSize = textPaint.textSize * 0.62f

        val last = (firstWhite + visibleWhites).coerceAtMost(whites.size)
        for (i in firstWhite until last) {
            val midi = whites[i]
            val x = whiteX(i)
            rect.set(x + 1.5f, 0f, x + ww - 1.5f, h - 4f)
            canvas.drawRoundRect(rect, 10f, 10f, if (held[midi] > 0) pressedPaint else whitePaint)
            if (labelMode != 0) {
                val names = if (labelMode == 2) CDE else DOREMI
                canvas.drawText(names[midi % 12], x + ww / 2, h - h * 0.07f, textPaint)
                if (midi % 12 == 0) {
                    canvas.drawText((midi / 12 - 1).toString(), x + ww / 2, h - h * 0.07f - textPaint.textSize * 1.15f, octavePaint)
                }
            }
        }
        // 흰 건반 사이 경계선
        for (i in firstWhite..last) {
            val x = whiteX(i)
            canvas.drawLine(x, 0f, x, h - 4f, linePaint)
        }
        // 검은 건반
        for (i in firstWhite until last) {
            val midi = whites[i] + 1
            if (midi > SoundEngine.HIGH || !isBlack(midi)) continue
            if (!blackRect(midi, rect)) continue
            canvas.drawRoundRect(rect, 6f, 6f, if (held[midi] > 0) pressedBlackPaint else blackPaint)
            if (labelMode != 0) {
                val names = if (labelMode == 2) CDE else DOREMI
                canvas.drawText(names[midi % 12], rect.centerX(), rect.bottom - rect.height() * 0.08f, blackTextPaint)
            }
        }
        // 화면 왼쪽 끝에 걸친 검은 건반
        if (firstWhite > 0) {
            val midi = whites[firstWhite] - 1
            if (isBlack(midi) && blackRect(midi, rect)) {
                canvas.drawRoundRect(rect, 6f, 6f, if (held[midi] > 0) pressedBlackPaint else blackPaint)
            }
        }
    }

    private fun keyAt(x: Float, y: Float): Int {
        val ww = whiteWidth()
        val idx = (firstWhite + (x / ww).toInt()).coerceIn(0, whites.size - 1)
        if (y < blackHeight()) {
            // 터치 위치 양옆의 검은 건반부터 확인
            for (cand in intArrayOf(whites[idx] + 1, whites[idx] - 1)) {
                if (cand in SoundEngine.LOW..SoundEngine.HIGH && isBlack(cand) && blackRect(cand, rect) &&
                    x >= rect.left && x <= rect.right) return cand
            }
        }
        return whites[idx]
    }

    private fun press(pointer: Int, midi: Int) {
        pointerKey[pointer] = midi
        held[midi]++
        onNoteOn?.invoke(midi)
    }

    private fun lift(pointer: Int) {
        val midi = pointerKey.remove(pointer) ?: return
        held[midi] = (held[midi] - 1).coerceAtLeast(0)
        if (held[midi] == 0) onNoteOff?.invoke(midi)
    }

    fun releaseAll() {
        for (p in pointerKey.keys.toList()) lift(p)
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                press(e.getPointerId(i), keyAt(e.getX(i), e.getY(i)))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val now = keyAt(e.getX(i), e.getY(i))
                    val before = pointerKey[id]
                    if (before != null && before != now) {
                        lift(id)
                        press(id, now)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> lift(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        invalidate()
        return true
    }
}

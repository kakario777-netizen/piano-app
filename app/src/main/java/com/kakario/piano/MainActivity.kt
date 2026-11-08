package com.kakario.piano

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private data class Instrument(val folder: String, val label: String)

    private val instruments = listOf(
        Instrument("piano", "그랜드 피아노"),
        Instrument("epiano", "전자 피아노"),
        Instrument("organ", "파이프 오르간"),
        Instrument("strings", "현악 합주"),
        Instrument("harpsichord", "하프시코드"),
        Instrument("musicbox", "오르골"),
    )

    private lateinit var engine: SoundEngine
    private lateinit var keyboard: KeyboardView
    private lateinit var instrumentBtn: Button
    private lateinit var rangeText: TextView
    private lateinit var labelBtn: Button
    private lateinit var sustainBtn: Button
    private lateinit var statusText: TextView

    private var instrumentIndex = 0
    private val prefs by lazy { getSharedPreferences("piano", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream = AudioManager.STREAM_MUSIC

        engine = SoundEngine(this)
        keyboard = KeyboardView(this)
        keyboard.onNoteOn = { engine.noteOn(it) }
        keyboard.onNoteOff = { engine.noteOff(it) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E24"))
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }

        instrumentBtn = chip("") { pickInstrument() }
        bar.addView(instrumentBtn)
        bar.addView(space())

        bar.addView(chip("◀ 옥타브") { shift(-7) })
        bar.addView(chip("◀") { shift(-1) })
        rangeText = TextView(this).apply {
            setTextColor(Color.parseColor("#E8E8EE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            minWidth = dp(96)
        }
        bar.addView(rangeText)
        bar.addView(chip("▶") { shift(1) })
        bar.addView(chip("옥타브 ▶") { shift(7) })
        bar.addView(space())

        bar.addView(chip("건반 −") { zoom(1) })
        bar.addView(chip("건반 +") { zoom(-1) })
        bar.addView(space())

        labelBtn = chip("") {
            keyboard.labelMode = (keyboard.labelMode + 1) % 3
            save(); refresh()
        }
        bar.addView(labelBtn)
        sustainBtn = chip("") {
            engine.sustain = !engine.sustain
            if (!engine.sustain) engine.stopAll()
            save(); refresh()
        }
        bar.addView(sustainBtn)

        statusText = TextView(this).apply {
            setTextColor(Color.parseColor("#F2C66D"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(10), 0, 0, 0)
        }
        bar.addView(statusText)

        // 화면이 좁으면(폴드 바깥 화면 등) 위 버튼줄을 옆으로 밀어 볼 수 있게
        val scroller = HorizontalScrollView(this).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            addView(bar)
        }
        root.addView(scroller, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(keyboard, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        // 저장된 설정 불러오기
        instrumentIndex = prefs.getInt("instrument", 0).coerceIn(0, instruments.size - 1)
        keyboard.labelMode = prefs.getInt("label", 1)
        keyboard.visibleWhites = prefs.getInt("whites", 14)
        keyboard.firstWhite = prefs.getInt("first", 14) // 기본 C4 근처
        engine.sustain = prefs.getBoolean("sustain", false)

        engine.onProgress = { loaded, total ->
            statusText.text = if (loaded >= total) "" else "소리 불러오는 중 ${loaded * 100 / total}%"
        }
        loadInstrument()
        refresh()
    }

    private fun loadInstrument() {
        statusText.text = "소리 불러오는 중"
        engine.loadInstrument(instruments[instrumentIndex].folder)
    }

    private fun pickInstrument() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("악기 선택")
            .setSingleChoiceItems(instruments.map { it.label }.toTypedArray(), instrumentIndex) { d, which ->
                d.dismiss()
                if (which != instrumentIndex) {
                    instrumentIndex = which
                    keyboard.releaseAll()
                    loadInstrument()
                    save(); refresh()
                }
            }
            .show()
    }

    private fun shift(by: Int) {
        keyboard.firstWhite = keyboard.firstWhite + by
        save(); refresh()
    }

    private fun zoom(by: Int) {
        keyboard.visibleWhites = keyboard.visibleWhites + by
        save(); refresh()
    }

    private fun refresh() {
        instrumentBtn.text = "♪ " + instruments[instrumentIndex].label
        // 범위 표시: 도레미 모드면 도4, 아니면 C4 식
        rangeText.text = if (keyboard.labelMode == 1) {
            keyboard.noteName(keyboard.lowestVisible()) + " ~ " + keyboard.noteName(keyboard.highestVisible())
        } else {
            cdeName(keyboard.lowestVisible()) + " ~ " + cdeName(keyboard.highestVisible())
        }
        labelBtn.text = when (keyboard.labelMode) {
            1 -> "계이름: 도레미"
            2 -> "계이름: CDE"
            else -> "계이름: 끔"
        }
        sustainBtn.text = if (engine.sustain) "울림 유지: 켬" else "울림 유지: 끔"
        sustainBtn.alpha = if (engine.sustain) 1f else 0.75f
    }

    private fun cdeName(midi: Int): String {
        val n = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return n[midi % 12] + (midi / 12 - 1)
    }

    private fun save() {
        prefs.edit()
            .putInt("instrument", instrumentIndex)
            .putInt("label", keyboard.labelMode)
            .putInt("whites", keyboard.visibleWhites)
            .putInt("first", keyboard.firstWhite)
            .putBoolean("sustain", engine.sustain)
            .apply()
    }

    private fun chip(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.parseColor("#E8E8EE"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(12), dp(6), dp(12), dp(6))
        stateListAnimator = null
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#2E2E36"))
            cornerRadius = dp(16).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply {
            marginEnd = dp(6)
        }
        setOnClickListener { onClick() }
    }

    private fun space(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onPause() {
        super.onPause()
        keyboard.releaseAll()
        engine.stopAll()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.release()
    }
}

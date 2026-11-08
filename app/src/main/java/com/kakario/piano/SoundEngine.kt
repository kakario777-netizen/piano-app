package com.kakario.piano

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper

/** 악기 소리 파일을 불러와 건반마다 재생/정지한다. */
class SoundEngine(private val context: Context) {

    companion object {
        const val LOW = 36   // C2
        const val HIGH = 96  // C7
    }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(24)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private val soundIds = IntArray(128)
    private val streams = IntArray(128)
    private val fadeTokens = IntArray(128)
    private val pending = HashSet<Int>()
    private var loadGeneration = 0

    var sustain = false
    var volume = 1f

    var onProgress: ((loaded: Int, total: Int) -> Unit)? = null

    init {
        pool.setOnLoadCompleteListener { _, sampleId, _ ->
            handler.post {
                if (pending.remove(sampleId)) {
                    val total = HIGH - LOW + 1
                    onProgress?.invoke(total - pending.size, total)
                }
            }
        }
    }

    val isReady: Boolean get() = pending.isEmpty()

    fun loadInstrument(folder: String) {
        loadGeneration++
        stopAll()
        for (m in LOW..HIGH) {
            if (soundIds[m] != 0) {
                pool.unload(soundIds[m])
                soundIds[m] = 0
            }
        }
        pending.clear()
        for (m in LOW..HIGH) {
            try {
                context.assets.openFd("sounds/$folder/$m.mp3").use { fd ->
                    val id = pool.load(fd, 1)
                    soundIds[m] = id
                    pending.add(id)
                }
            } catch (e: Exception) {
                // 파일이 없으면 그 건반은 소리 없음
            }
        }
        if (pending.isEmpty()) {
            val total = HIGH - LOW + 1
            onProgress?.invoke(total, total)
        }
    }

    fun noteOn(midi: Int) {
        if (midi !in LOW..HIGH) return
        val id = soundIds[midi]
        if (id == 0) return
        // 같은 건반이 아직 울리고 있으면 짧게 끊고 다시 친다
        if (streams[midi] != 0) {
            pool.stop(streams[midi])
            streams[midi] = 0
        }
        fadeTokens[midi]++
        streams[midi] = pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun noteOff(midi: Int) {
        if (midi !in LOW..HIGH) return
        if (sustain) return
        val stream = streams[midi]
        if (stream == 0) return
        val token = ++fadeTokens[midi]
        val steps = 8
        for (i in 1..steps) {
            handler.postDelayed({
                if (fadeTokens[midi] != token) return@postDelayed
                if (i == steps) {
                    pool.stop(stream)
                    if (streams[midi] == stream) streams[midi] = 0
                } else {
                    val v = volume * (1f - i.toFloat() / steps)
                    pool.setVolume(stream, v, v)
                }
            }, (i * 30).toLong())
        }
    }

    fun stopAll() {
        for (m in 0 until 128) {
            fadeTokens[m]++
            if (streams[m] != 0) {
                pool.stop(streams[m])
                streams[m] = 0
            }
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        pool.release()
    }
}

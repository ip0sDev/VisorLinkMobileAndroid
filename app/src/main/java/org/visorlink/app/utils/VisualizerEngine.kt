package org.visorlink.app.utils

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Кадр FFT системного Visualizer: сырые знаковые байты (`Visualizer.getFft`) и частота
 * дискретизации в миллигерцах. Аналог Flutter EventChannel `org.visorlink.app/visualizer`.
 */
class MusicFftFrame(val fft: ByteArray, val rateMilliHz: Int)

/**
 * Нативный FFT плеера (порт Flutter `AudioVisualizer.kt`): Visualizer на аудиосессии ExoPlayer.
 * К собственной сессии разрешение RECORD_AUDIO не нужно; на устройствах, где attach не удался,
 * UI откатывается на процедурный спектр.
 */
class VisualizerEngine {
    private var visualizer: Visualizer? = null

    private val _fft = MutableSharedFlow<MusicFftFrame>(
        replay = 0, extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val fft: SharedFlow<MusicFftFrame> = _fft

    /** Подключить к аудиосессии. Повторный вызов переподключается. */
    fun attach(audioSessionId: Int) {
        detach()
        try {
            val v = Visualizer(audioSessionId)
            v.captureSize = Visualizer.getCaptureSizeRange()[1]
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft != null) _fft.tryEmit(MusicFftFrame(fft.copyOf(), samplingRate))
                    }
                },
                Visualizer.getMaxCaptureRate(),
                false, // waveform не нужен
                true,  // fft
            )
            v.enabled = true
            visualizer = v
        } catch (e: Exception) {
            Log.w(TAG, "Visualizer unavailable: ${e.message}")
            visualizer = null
        }
    }

    fun detach() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null
    }

    companion object {
        private const val TAG = "VisualizerEngine"
    }
}
